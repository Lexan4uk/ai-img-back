# API Reference — ai-img-back

> **Версия:** v2 (BIGSERIAL PK, Long IDs)

Base URL: `http://localhost:8080`
Авторизация: header `UserId: <Long>` (временная заглушка, в продакшене → JWT)
Формат ошибок: `{ "error": "текст" }`

---

## Краткое описание экранов и эндпоинтов к ним

### Вне приложения

Фактически, для работы приложения, от пользователя нужен только id.

POST users - добавить пользователя (для разработки)

GET users - получить всех пользователей (для разработки)

GET users/{id} - получить одного пользователя (для разработки)

### Главный экран — галерея (S-01)

3 блока.

Сначала только 1 блок — выбор типа. После выбора типа подгружается галерея по этому типу — 2 блок, а сбоку появляется список возможных стилей — 3 блок. При выборе стиля галерея (2 блок) меняется на галерею по типу+стилю.

_Типы_

GET image-types — получить все типы

GET image-types/favorites — получить id избранных типов (List\<Long\>)

POST image-types/{id}/favorite — добавить id типа в избранные

DELETE image-types/{id}/favorite — удалить id типа из избранных

_Стили_

GET styles — получить все стили

GET styles/favorites — получить id избранных стилей (List\<Long\>)

POST styles/{id}/favorite — добавить id стиля в избранные

DELETE styles/{id}/favorite — удалить id стиля из избранных

_Ассеты_

GET assets?imageTypeId={Long} — получить все ассеты заданного типа

GET assets?imageTypeId={Long}&styleId={Long} — получить все ассеты заданного типа и стиля

### Второй экран — генерация (S-02)

3 блока — список типов (чекбоксы), список стилей (чекбоксы), поле ввода пользовательского промпта + параметры.

У пользовательского промпта есть `generationParams` — параметры для нейронок (JSON-строка). Пока реализован aspect ratio (соотношение сторон).

На этом же экране — создание и удаление типов/стилей.

_Типы_

GET image-types — получить все типы

GET image-types/favorites — получить id избранных типов

POST image-types — создать тип

POST image-types/{id}/favorite — добавить id типа в избранные

DELETE image-types/{id}/favorite — удалить id типа из избранных

DELETE image-types/{id} — удалить тип (только создатель)

_Стили_

GET styles — получить все стили

GET styles/favorites — получить id избранных стилей

POST styles — создать стиль

POST styles/{id}/favorite — добавить id стиля в избранные

DELETE styles/{id}/favorite — удалить id стиля из избранных

DELETE styles/{id} — удалить стиль (только создатель)

_Генерация_

POST generations — запустить генерацию

---

## Users

| Метод  | URL           | Headers | Body                                           |
| ------ | ------------- | ------- | ---------------------------------------------- |
| POST   | `/users`      | —       | `{ "email": "a@b.ru", "displayName": "Ivan" }` |
| GET    | `/users`      | —       | —                                              |
| GET    | `/users/{id}` | —       | —                                              |
| DELETE | `/users/{id}` | —       | —                                              |

При удалении пользователя все его данные остаются (`created_by_user_id` → NULL), удаляется только избранное (CASCADE).

---

## Image Types

Общий справочник. Удалить может только создатель.
Системный тип «Неопределённый»: `id = 1` — неудаляемый.

| Метод  | URL                          | UserId | Body                                             |
| ------ | ---------------------------- | ------ | ------------------------------------------------ |
| POST   | `/image-types`               | ✅     | `{ "name": "Фото", "typePrompt": "a photo of" }` |
| GET    | `/image-types`               | —      | —                                                |
| DELETE | `/image-types/{id}`          | ✅     | —                                                |
| POST   | `/image-types/{id}/favorite` | ✅     | —                                                |
| DELETE | `/image-types/{id}/favorite` | ✅     | —                                                |
| GET    | `/image-types/favorites`     | ✅     | → `List<Long>` (массив ID)                      |

При удалении типа: assets и generation_requests переназначаются на `id = 1`, избранное удаляется (CASCADE).

---

## Styles

Зеркальная копия Image Types.
Системный стиль «Неопределённый»: `id = 1` — неудаляемый.

| Метод  | URL                     | UserId | Body                                                              |
| ------ | ----------------------- | ------ | ----------------------------------------------------------------- |
| POST   | `/styles`               | ✅     | `{ "name": "Айвазовский", "stylePrompt": "style of Aivazovsky" }` |
| GET    | `/styles`               | —      | —                                                                 |
| DELETE | `/styles/{id}`          | ✅     | —                                                                 |
| POST   | `/styles/{id}/favorite` | ✅     | —                                                                 |
| DELETE | `/styles/{id}/favorite` | ✅     | —                                                                 |
| GET    | `/styles/favorites`     | ✅     | → `List<Long>` (массив ID)                                       |

---

## Generations

Произведение: N типов × M стилей = N×M запросов к AI.

```
POST /generations
UserId: <Long>
```

```json
{
    "userPrompt": "кузнец куёт меч",
    "generationParams": "{\"aspectRatio\": \"1:1\"}",
    "imageTypeIds": [2, 4],
    "styleIds": [3, 5, 7],
    "dedupeMode": "SKIP",
    "provider": "openai",
    "model": "gpt-image-1.5",
    "routingMode": "DIRECT"
}
```

| Поле             | Обязательно | По умолчанию | Варианты             |
| ---------------- | ----------- | ------------ | -------------------- |
| userPrompt       | ✅          | —            |                      |
| generationParams | —           | `"{}"`       | JSON-строка          |
| imageTypeIds     | ✅          | —            | List\<Long\>         |
| styleIds         | ✅          | —            | List\<Long\>         |
| dedupeMode       | —           | `SKIP`       | `SKIP` / `OVERWRITE` |
| provider         | —           | `openai`     |                      |
| model            | —           | —            |                      |
| routingMode      | —           | `DIRECT`     | `DIRECT` / ...       |

**Валидация:** `imageTypeIds` и `styleIds` не могут содержать `1` (UNDEFINED) — вернёт 400.

**Дедупликация:** глобальная. Проверяется наличие `generation_request` со `status = 'DONE'` и тем же `final_prompt_hash`. Если найден и `dedupeMode = SKIP` → статус `SKIPPED`.

**Ответ** — массив по каждой паре (тип × стиль):

```json
[
    {
        "requestId": 1,
        "imageTypeId": 2,
        "styleId": 3,
        "status": "DONE",
        "createdAssetId": 142,
        "errorMessage": null
    },
    {
        "requestId": 2,
        "imageTypeId": 2,
        "styleId": 5,
        "status": "SKIPPED",
        "createdAssetId": null,
        "errorMessage": null
    }
]
```

Статусы: `DONE` — создан asset, `SKIPPED` — дубликат + SKIP, `FAILED` — ошибка AI.

---

## Assets (Галерея)

Картинки от **всех** пользователей, отсортированы по `id DESC` (новые первые).

`styleId` — опциональный: без него возвращаются все ассеты типа (все стили).

```
GET /assets?imageTypeId=2

GET /assets?imageTypeId=2&styleId=3
```

Header `UserId` не нужен.

```json
[
    {
        "id": 142,
        "imageTypeId": 2,
        "styleId": 3,
        "fileUri": "generated/.../xxx.png",
        "createdAt": "2026-02-28T12:15:00+03:00"
    }
]
```

> **Примечание:** В v2 asset не содержит `ownerUserId`, `promptId`, `finalPromptSnapshot`, `provider`, `model`.
> Эти данные доступны через цепочку `asset → generation_request → batch`.

---

## Сборка prompt

```
final_prompt = [type_prompt]. [style_prompt]. [user_prompt]
hash = SHA-256(final_prompt + "|" + generation_params)
```

Одинаковый текст + разные params (соотношение сторон) = разные hash = разные картинки.

`user_prompt` и `generation_params` хранятся в `generation_batches` (таблица `prompts` удалена в v2).

---

## Ошибки

| Код | Когда                                                                      |
| --- | -------------------------------------------------------------------------- |
| 400 | Пустое имя/промпт, дубликат имени, генерация с UNDEFINED типом/стилем      |
| 403 | Удаление чужого или неопределённого типа/стиля                             |
| 404 | Сущность не найдена                                                        |
| 500 | Внутренняя ошибка                                                          |

---

## Связи (v2)

```
users
  ├─→ image_types.created_by_user_id  (SET NULL)
  ├─→ styles.created_by_user_id       (SET NULL)
  ├─→ generation_batches.owner_user_id(SET NULL)
  ├─→ user_favorite_types             (CASCADE)
  └─→ user_favorite_styles            (CASCADE)

image_types
  ├─→ assets.image_type_id            (переназначение на id=1 через код)
  ├─→ generation_requests.image_type_id(переназначение на id=1 через код)
  └─→ user_favorite_types             (CASCADE)

styles
  ├─→ assets.style_id                 (переназначение на id=1 через код)
  ├─→ generation_requests.style_id    (переназначение на id=1 через код)
  └─→ user_favorite_styles            (CASCADE)

generation_batches
  └─→ generation_requests.batch_id    (CASCADE)
```
