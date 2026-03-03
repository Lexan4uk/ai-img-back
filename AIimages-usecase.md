# Спецификация: генерация изображений, справочники, избранное, галерея

> **Версия:** v2 (BIGSERIAL PK, упрощённая схема)

## 0) Термины и инварианты

- **Тип (`image_type`)**: общий справочник (виден всем пользователям), имеет `type_prompt` (фрагмент для промпта). Нередактируемый — только создание и удаление. Удалить может только создатель.
- **Стиль (`style`)**: общий справочник (виден всем пользователям), имеет `style_prompt`. Нередактируемый — только создание и удаление. Удалить может только создатель.
- **Неопределённый тип / стиль**: системные записи с фиксированным `id = 1` (BIGSERIAL). При удалении типа/стиля все связанные `assets` и `generation_requests` переназначаются на неопределённый. Не могут быть удалены. `created_by_user_id = NULL`.
- **Избранное**: пользователь может добавлять типы и стили в избранное. Используется `user_id` из таблицы users.
- **User prompt**: текст, который вводит пользователь.
- **Final prompt**: то, что реально уходит в AI = композиция `type_prompt` + `style_prompt` + `user_prompt`.
- **Final prompt hash**: `SHA-256(final_prompt + "|" + generation_params)` — учитывает и текст, и параметры генерации (одинаковый текст с разным размером = разные картинки).
- **Дубликат**: **глобально** существует `generation_request` со статусом `DONE` и тем же `final_prompt_hash`.
- **Overwrite**: если дубль и режим `OVERWRITE` — создаём новый `asset`, старые не трогаем.
- **Галерея**: выбор типа → все `assets` этого типа от всех пользователей (все стили). Опционально: фильтрация по стилю → `assets` с конкретным сочетанием тип + стиль. Сортировка по ID DESC.
- **Удаление пользователя**: все данные (assets, типы, стили, история генераций) остаются. Поля `created_by_user_id` / `owner_user_id` становятся NULL. Избранное удаляется (CASCADE).

### Изменения относительно v1

| Что изменилось | v1 | v2 |
|---------------|----|----|
| PK/FK | UUID | BIGSERIAL / BIGINT |
| Таблица `prompts` | Отдельная таблица | Удалена. `user_prompt` и `generation_params` хранятся в `generation_batches` |
| Снапшоты в assets | `user_prompt_snapshot`, `type_prompt_snapshot`, `style_prompt_snapshot`, `final_prompt_snapshot`, `final_prompt_hash` | Удалены. Asset хранит только `id`, `image_type_id`, `style_id`, `file_uri`, `created_at` |
| Owner в assets | `owner_user_id` | Удалён (можно восстановить через `generation_requests → batch → owner`) |
| Статус batch | Хранился в БД (`status`, `started_at`, `finished_at`) | Вычисляется по COUNT реквестов |
| Дедупликация | В рамках пользователя | Глобальная (по `generation_requests` с `status = 'DONE'`) |
| `dedupe_mode` | В каждом `generation_request` | В `generation_batches` (один на batch) |
| Снапшоты в requests | `user_prompt_snapshot`, `final_prompt_snapshot` | Удалены |
| UserId в заголовке | UUID | Long |

---

## 1) Справочники: создание/удаление

### 1.1 Создать тип

**Ввод:** `name`, `type_prompt` _(опц)_

**Проверки:**

- `name` не пустой
- уникальность имени (case-insensitive) глобально

**Действия:**

- `INSERT image_types (created_by_user_id = текущий userId)`

**Результат:** новый тип с автоинкрементным `id`

---

### 1.2 Удалить тип

**Ввод:** `type_id` (Long)

**Проверки:**

- нельзя удалить неопределённый тип (`id = 1`)
- `created_by_user_id` = текущий пользователь (удалять может только создатель)

**Действия (в одной транзакции):**

1. `UPDATE assets SET image_type_id = 1 WHERE image_type_id = :type_id`
2. `UPDATE generation_requests SET image_type_id = 1 WHERE image_type_id = :type_id`
3. `DELETE FROM image_types WHERE id = :type_id`

_(избранное `user_favorite_types` удалится автоматически через CASCADE)_

**Результат:** тип удалён, все связанные assets переведены на неопределённый тип.

---

Аналогично для `styles` (с `UNDEFINED_STYLE_ID = 1`).

---

## 2) Избранное

### 2.1 Добавить тип в избранное

**Ввод:** `image_type_id` (Long)

**Проверки:** тип существует

**Действия:** `INSERT INTO user_favorite_types (user_id, image_type_id) ON CONFLICT DO NOTHING`

### 2.2 Убрать тип из избранного

**Действия:** `DELETE FROM user_favorite_types WHERE user_id = :userId AND image_type_id = :typeId`

### 2.3 Получить ID избранных типов

**Действия:** `SELECT image_type_id FROM user_favorite_types WHERE user_id = :userId`

**Результат:** массив Long

Аналогично для `styles` → массив Long.

---

## 3) Компоновка final prompt

### 3.1 Входные данные

- `user_prompt` (текст от пользователя)
- `generation_params` (width, height и др. — JSON-строка)
- `type_prompt` из `image_types`
- `style_prompt` из `styles`

### 3.2 Итог

```
final_prompt = [type_prompt]. [style_prompt]. [user_prompt]
hash = SHA-256(final_prompt + "|" + generation_params)
```

---

## 4) Генерация v1 (последовательно)

### 4.1 Вход API

`POST /generations`

- `user_prompt` (текст)
- `generation_params`: `{ "width": 1024, "height": 1024 }` _(JSON-строка, масштабируемо)_
- `image_type_ids[]` (List\<Long\>)
- `style_ids[]` (List\<Long\>)
- `dedupe_mode: SKIP|OVERWRITE`
- _(optional)_ `provider/model/routing_mode`

### 4.2 Общий алгоритм

#### Шаг 0. Валидация и загрузка

- Проверить `user_prompt` не пустой, списки ids не пустые.
- Проверить что `image_type_ids` не содержит `1` (UNDEFINED), `style_ids` не содержит `1` (UNDEFINED) — генерация с неопределёнными запрещена.
- Загрузить `image_types` по ids, убедиться что существуют.
- Загрузить `styles` по ids, убедиться что существуют.

#### Шаг 1. Создать batch

- `INSERT generation_batches`:
  - `owner_user_id`
  - `user_prompt`, `generation_params`, `dedupe_mode`
  - `provider/model/routing_mode`

> **Примечание:** В v2 batch не хранит `status` — он вычисляется по COUNT реквестов.
> Также `user_prompt` и `generation_params` хранятся непосредственно в batch
> (таблица `prompts` удалена).

#### Шаг 2. Сформировать список задач (requests)

Для каждой пары `(typeId, styleId)` (декартово произведение):

- взять `type_prompt` из `image_types` _(или '')_
- взять `style_prompt` из `styles` _(или '')_
- собрать `final_prompt`
- посчитать `final_prompt_hash` (с учётом `generation_params`)
- `INSERT generation_requests (batch_id, image_type_id, style_id, final_prompt_hash)` со статусом `PENDING`

#### Шаг 3. Последовательное выполнение (главный цикл v1)

Для каждого `generation_request` по порядку:

1. **Проверка дубля (глобальная):**

   - ищем существующий `generation_request` с `status = 'DONE'` и тем же `final_prompt_hash`
   - если найдено и `dedupe_mode = SKIP`:
     - `UPDATE request`: `status = SKIPPED`
     - перейти к следующему request
   - если `dedupe_mode = OVERWRITE`: продолжаем (создадим новый asset)

2. `UPDATE request`: `status = RUNNING`

3. **Вызов AI (один запрос → один ответ):**

   - `AiClient.generateImage(final_prompt, generation_params, ...)`
   - получить `file_uri`

   Если ошибка:
   - `UPDATE request`: `status = FAILED`, `error_message`
   - продолжить со следующим

4. **Сохранение asset:**

   - `INSERT assets (image_type_id, style_id, file_uri)`
   - `UPDATE request`: `status = DONE`, `created_asset_id = assets.id`

#### Шаг 4. Ответ API

Вернуть массив результатов по каждой паре _(type, style)_:

- `status: DONE/SKIPPED/FAILED`
- `createdAssetId` (Long, если DONE)
- `errorMessage` (если FAILED)

---

## 5) Галерея

### 5.1 Навигация

3 блока. Сначала только блок выбора типа. После выбора типа:

- подгружается галерея по этому типу (все стили)
- появляется список стилей для фильтрации

При выборе стиля галерея перезагружается — показываются только ассеты выбранного типа + стиля.

### 5.2 Эндпоинт

- `GET /assets?imageTypeId=T` — все ассеты типа **T** (все стили)
- `GET /assets?imageTypeId=T&styleId=S` — ассеты типа **T** и стиля **S**

От **всех пользователей**, сортировка по `id DESC`.

### 5.3 Отображение

Показывается:

- превью изображения (`file_uri`)
- название стиля (через `style_id` → `styles.name`)
- дата создания

> **Примечание:** В v2 asset не хранит `owner_user_id` и `final_prompt_snapshot`.
> При необходимости автора можно восстановить через цепочку
> `asset.id → generation_requests.created_asset_id → batch → owner_user_id`.

---

## 6) Поведение при удалении типа/стиля

- Удалили тип/стиль:
  - все связанные `assets` переключаются на неопределённый тип/стиль (`id = 1`)
  - все связанные `generation_requests` переключаются на `id = 1`
  - в галерее эти assets появятся в секции «Неопределённый»
  - записи в избранном удаляются автоматически (CASCADE)

---

## 7) Поведение при удалении пользователя

- Все данные пользователя **остаются**:
  - `image_types.created_by_user_id` → NULL (тип остаётся общим, но удалить его никто не сможет)
  - `styles.created_by_user_id` → NULL (аналогично)
  - `generation_batches.owner_user_id` → NULL
- Избранное пользователя **удаляется** (CASCADE).

---

## 8) Запрет одинаковых типов/стилей

Запрещаем одинаковые `name` (case-insensitive) глобально через индекс `LOWER(name)`.

Это предотвращает дубликаты в UI.
