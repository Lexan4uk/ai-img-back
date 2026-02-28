# Спецификация: генерация изображений, справочники, избранное, галерея

## 0) Термины и инварианты

- **Тип (`image_type`)**: общий справочник (виден всем пользователям), имеет `type_prompt` (фрагмент для промпта). Нередактируемый — только создание и удаление. Удалить может только создатель.
- **Стиль (`style`)**: общий справочник (виден всем пользователям), имеет `style_prompt`. Нередактируемый — только создание и удаление. Удалить может только создатель.
- **Неопределённый тип / стиль**: системные записи-заглушки с фиксированными UUID. При удалении типа/стиля все связанные `assets` и `generation_requests` переназначаются на неопределённый. Не могут быть удалены. `created_by_user_id = NULL`.
- **Избранное**: пользователь может добавлять типы и стили в избранное. Используется только `user_id` из таблицы users (таблица users не модифицируется).
- **Промпт (`prompt`)**: одноразовый снимок, создаётся автоматически при запуске генерации. Содержит текст пользователя + параметры генерации (`generation_params`: width, height и др. через JSONB — масштабируемо). Не переиспользуется, не редактируется.
- **User prompt**: текст, который вводит пользователь.
- **Final prompt**: то, что реально уходит в AI = композиция `type_prompt` + `style_prompt` + `user_prompt` + _(общие ограничения/quality блок)_.
- **Final prompt hash**: `sha256(canonical(final_prompt_snapshot) + canonical(generation_params))` — учитывает и текст, и параметры генерации (одинаковый текст с разным размером = разные картинки).
- **Дубликат**: в рамках пользователя существует `asset` с тем же `final_prompt_hash`.
- **Overwrite**: если дубль и режим `OVERWRITE` — создаём новый `asset`, старые не трогаем.
- **Галерея**: выбор типа + стиля → все `assets` с этим сочетанием от всех пользователей, сортировка по времени.
- **Удаление пользователя**: все данные (assets, типы, стили, промпты, история генераций) остаются. Поля `owner_user_id` / `created_by_user_id` становятся NULL.

---

## 1) Справочники: создание/удаление

### 1.1 Создать тип

**Ввод:** `name`, `type_prompt` _(опц)_

**Проверки:**

- `name` не пустой
- уникальность имени (case-insensitive) глобально

**Действия:**

- `INSERT image_types (created_by_user_id = текущий userId)`

**Результат:** новый `type_id`

---

### 1.2 Удалить тип

**Ввод:** `type_id`

**Проверки:**

- нельзя удалить неопределённый тип (`UNDEFINED_TYPE_ID`)
- `created_by_user_id` = текущий пользователь (удалять может только создатель)

**Действия (в одной транзакции):**

1. `UPDATE assets SET image_type_id = UNDEFINED_TYPE_ID WHERE image_type_id = :type_id`
2. `UPDATE generation_requests SET image_type_id = UNDEFINED_TYPE_ID WHERE image_type_id = :type_id`
3. `DELETE FROM image_types WHERE id = :type_id`

_(избранное user_favorite_types удалится автоматически через CASCADE)_

**Результат:** тип удалён, все связанные assets переведены на неопределённый тип. Снапшоты (`type_prompt_snapshot`) в assets сохраняют оригинальный промпт типа.

---

Аналогично для `styles` (с `UNDEFINED_STYLE_ID`).

---

## 2) Избранное

### 2.1 Добавить тип в избранное

**Ввод:** `image_type_id`

**Проверки:** тип существует

**Действия:** `INSERT INTO user_favorite_types (user_id, image_type_id)`

### 2.2 Убрать тип из избранного

**Действия:** `DELETE FROM user_favorite_types WHERE user_id = :userId AND image_type_id = :typeId`

### 2.3 Получить избранные типы

**Действия:** `SELECT ... FROM image_types JOIN user_favorite_types ON ... WHERE user_id = :userId`

Аналогично для `styles` (`user_favorite_styles`).

---

## 3) Компоновка final prompt (PromptComposer)

### 3.1 Входные данные

- `user_prompt` (текст от пользователя)
- `generation_params` (width, height и др.)
- `type_prompt` из `image_types`
- `style_prompt` из `styles`

### 3.2 Итог

- `final_prompt_snapshot` = строка, которая уйдёт в AI
- `final_prompt_hash` = `sha256(canonical(final_prompt_snapshot) + canonical(generation_params))`

---

## 4) Генерация v1 (последовательно)

### 4.1 Вход API

`POST /generations`

- `user_prompt` (текст)
- `generation_params`: `{ "width": 1024, "height": 1024 }` _(масштабируемо)_
- `image_type_ids[]`
- `style_ids[]`
- `dedupe_mode: SKIP|OVERWRITE`
- _(optional)_ `provider/model/routing_mode`

### 4.2 Общий алгоритм

#### Шаг 0. Валидация и загрузка

- Проверить `user_prompt` не пустой, списки ids не пустые.
- Загрузить `image_types` по ids, убедиться что существуют.
- Загрузить `styles` по ids, убедиться что существуют.
- **Создать prompt** (одноразовый): `INSERT prompts (owner_user_id, text, generation_params)`.

#### Шаг 1. Создать batch

- `INSERT generation_batches`:
  - `owner_user_id`
  - `provider/model/routing_mode`
  - `status=RUNNING`, timestamps: `created_at/started_at`

#### Шаг 2. Сформировать список задач (requests)

Для каждой пары `(typeId, styleId)` (декартово произведение):

- взять `type_prompt_snapshot = image_types.type_prompt` _(или '')_
- взять `style_prompt_snapshot = styles.style_prompt` _(или '')_
- взять `user_prompt_snapshot` = `prompts.text`
- собрать `final_prompt_snapshot`
- посчитать `final_prompt_hash` (с учётом `generation_params`)
- `INSERT generation_requests` со статусом `PENDING`, полями snapshot/hash, `dedupe_mode`, `prompt_id`

В `generation_batches.total_requests` поставить количество.

#### Шаг 3. Последовательное выполнение (главный цикл v1)

Для каждого `generation_request` по порядку:

1. `UPDATE request`: `status=RUNNING`, `started_at=now()`

2. **Проверка дубля:**

- ищем существующий `asset` в рамках пользователя по `final_prompt_hash`
- если найдено:
  - `UPDATE request`: `dedupe_result='DUPLICATE'`
  - если `dedupe_mode=SKIP`:
    - `UPDATE request`: `status=SKIPPED`, `finished_at=now()`
    - увеличить счётчики batch (`done_requests++`)
    - перейти к следующему request
  - если `dedupe_mode=OVERWRITE`: продолжаем _(просто создадим новый asset)_
- если не найдено:
  - `UPDATE request`: `dedupe_result='NEW'`

3. **Вызов AI (один запрос → один ответ):**

- `AiClient.generateImage(final_prompt_snapshot, generation_params, ...)`
- получить `bytes/uri` и `meta`

Если ошибка:

- `UPDATE request`: `status=FAILED`, `error_message`, `finished_at=now()`
- `batch.failed_requests++`
- продолжить со следующим _(или валить batch целиком — это отдельное решение)_

4. **Сохранение asset:**

- сохранить файл на диск → `file_uri`
- `INSERT assets`:
  - `owner_user_id`
  - `image_type_id`, `style_id`, `prompt_id`
  - `user_prompt_snapshot/type_prompt_snapshot/style_prompt_snapshot`
  - `final_prompt_snapshot/final_prompt_hash`
  - `file_uri/provider/model/meta`
- `UPDATE request`:
  - `status=DONE`, `created_asset_id=assets.id`, `finished_at=now()`
- `batch.done_requests++`

#### Шаг 4. Завершить batch

`UPDATE generation_batches`:

- `status`:
  - `DONE`, если нет `failed` _(или если failed допустимы — всё равно DONE)_
  - `FAILED`, если решили "любая ошибка = failed batch"
- `finished_at=now()`

#### Шаг 5. Ответ API

Вернуть массив результатов по каждой паре _(type, style)_:

- `status: DONE/SKIPPED/FAILED`
- `asset_id + file_uri` _(если DONE)_
- `error_message` _(если FAILED)_

---

## 5) Галерея

### 5.1 Навигация

Пользователь выбирает:

- **Тип** из списка всех типов
- **Стиль** из списка всех стилей

### 5.2 Результат

После выбора типа **T** и стиля **S**:

- все `assets` `WHERE image_type_id = T AND style_id = S`
- от **всех пользователей**
- сортировка по `created_at DESC`

Показывается:

- превью изображения
- промпт пользователя (`user_prompt_snapshot`)
- дата создания
- автор (`owner_user_id`)

---

## 6) Поведение при удалении типа/стиля

- Удалили тип/стиль:
  - все связанные `assets` переключаются на неопределённый тип/стиль
  - `type_prompt_snapshot`/`style_prompt_snapshot` в assets сохраняют оригинальные промпты
  - в галерее эти assets появятся в секции «Неопределённый»
  - записи в избранном удаляются автоматически (CASCADE)
- Снапшоты гарантируют, что `final_prompt_hash` и `final_prompt_snapshot` остаются валидными — они не зависят от текущего состояния справочника.

---

## 7) Поведение при удалении пользователя

- Все данные пользователя **остаются**:
  - `assets.owner_user_id` → NULL
  - `image_types.created_by_user_id` → NULL (тип остаётся общим, но удалить его никто не сможет)
  - `styles.created_by_user_id` → NULL (аналогично)
  - `prompts.owner_user_id` → NULL
  - `generation_batches.owner_user_id` → NULL
  - `generation_requests.owner_user_id` → NULL
- Избранное пользователя **удаляется** (CASCADE).

---

## 8) Запрет одинаковых типов/стилей

Запрещаем одинаковые `name` (case-insensitive) глобально.

Это предотвращает дубликаты в UI.
