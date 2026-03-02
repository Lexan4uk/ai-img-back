# API Reference — ai-img-back

Base URL: `http://localhost:8080`
Авторизация: header `UserId: <uuid>` (временная заглушка)
Формат ошибок: `{ "error": "текст" }`

---

## Краткое описание экранов и эндпоинтов к ним

### Вне приложения

Фактически, для работы приложения, от пользователя нужен только id.

POST users - добавить пользователя (для разработки)

GET users - получить всех пользователей (для разработки)

GET users/{id} - получить одного пользователя (для разработки)

### Главный экран - галерея

3 блока.

Сначала только 1 блок - выбор типа. После выбора типа подгружается галерея по этому типу - 2 блок, а сбоку/где то ещё появляется список возможных стилей - 3 блок. При выборе стиля галлерея (2 блок) меняется на галлерею по типу+стилю.

_Типы_

GET image-types - получить все типы

GET image-types/favorites - получить id избранных типов

POST image-types/{id}/favorite - добавить id типа в избранные

DELETE image-types/{id}/favorite - удалить id типа из избранных

_Стили_

GET styles - получить все стили

GET styles/favorites - получить id избранных стилей

POST styles/{id}/favorite - добавить id стиля в избранные

DELETE styles/{id}/favorite - удалить id стиля из избранных

_Ассеты_

GET assets?imageTypeId={id} - получить все ассеты (сгенерированные картинки) заданного типа

GET assets?imageTypeId={id}&styleId={id} - получить все ассеты (сгенерированные картинки) заданного типа и стиля

### Второй экран - генерация

3 блока - список типов, список стилей, поле ввода пользовательского промпта.
У пользовательского промпта есть неограниченное количество generationParams - параметры для нейронок независимые от основного промпта пользователя. Пока что я реализовываю только Width + Height. Они будут, например, сбоку от основного поля ввода - 2 дополнительных обязательных поля.

_Типы_

GET image-types - получить все типы

GET image-types/favorites - получить id избранных типов

POST image-types - создать тип

POST image-types/{id}/favorite - добавить id типа в избранные

DELETE image-types/{id}/favorite - удалить id типа из избранных

DELETE image-types/{id} - удалить тип (только создатель)

_Стили_

GET styles - получить все стили

GET styles/favorites - получить id избранных стилей

POST styles - создать стиль

POST styles/{id}/favorite - добавить id стиля в избранные

DELETE styles/{id}/favorite - удалить id стиля из избранных

DELETE styles/{id} - удалить стиль (только создатель)

_Генерация_

POST generations - закинуть запрос в генерацию

## Users

| Метод  | URL           | Headers | Body                                           |
| ------ | ------------- | ------- | ---------------------------------------------- |
| POST   | `/users`      | —       | `{ "email": "a@b.ru", "displayName": "Ivan" }` |
| GET    | `/users`      | —       | —                                              |
| GET    | `/users/{id}` | —       | —                                              |
| DELETE | `/users/{id}` | —       | —                                              |

При удалении пользователя все его данные остаются (owner → NULL), удаляется только избранное.

---

## Image Types

Общий справочник. Удалить может только создатель.
Системный тип «Неопределённый»: `00000000-0000-0000-0000-000000000001` — неудаляемый.

| Метод  | URL                          | UserId | Body                                             |
| ------ | ---------------------------- | ------ | ------------------------------------------------ |
| POST   | `/image-types`               | ✅     | `{ "name": "Фото", "typePrompt": "a photo of" }` |
| GET    | `/image-types`               | —      | —                                                |
| DELETE | `/image-types/{id}`          | ✅     | —                                                |
| POST   | `/image-types/{id}/favorite` | ✅     | —                                                |
| DELETE | `/image-types/{id}/favorite` | ✅     | —                                                |
| GET    | `/image-types/favorites`     | ✅     | —                                                |

При удалении типа: assets и generation_requests переназначаются на «Неопределённый», избранное удаляется (CASCADE).

---

## Styles

Зеркальная копия Image Types.
Системный стиль «Неопределённый»: `00000000-0000-0000-0000-000000000002` — неудаляемый.

| Метод  | URL                     | UserId | Body                                                              |
| ------ | ----------------------- | ------ | ----------------------------------------------------------------- |
| POST   | `/styles`               | ✅     | `{ "name": "Айвазовский", "stylePrompt": "style of Aivazovsky" }` |
| GET    | `/styles`               | —      | —                                                                 |
| DELETE | `/styles/{id}`          | ✅     | —                                                                 |
| POST   | `/styles/{id}/favorite` | ✅     | —                                                                 |
| DELETE | `/styles/{id}/favorite` | ✅     | —                                                                 |
| GET    | `/styles/favorites`     | ✅     | —                                                                 |

---

## Generations

Произведение: N типов × M стилей = N×M запросов к AI.

```
POST /generations
UserId: <uuid>
```

```json
{
	"userPrompt": "кузнец куёт меч",
	"generationParams": "{\"width\": 1024, \"height\": 1024}",
	"imageTypeIds": ["<uuid>", "<uuid>"],
	"styleIds": ["<uuid>"],
	"dedupeMode": "SKIP",
	"provider": "openai",
	"model": "dall-e-3",
	"routingMode": "DIRECT"
}
```

| Поле             | Обязательно | По умолчанию | Варианты             |
| ---------------- | ----------- | ------------ | -------------------- |
| userPrompt       | ✅          | —            |                      |
| generationParams | —           | `"{}"`       | JSON-строка          |
| imageTypeIds     | ✅          | —            |                      |
| styleIds         | ✅          | —            |                      |
| dedupeMode       | —           | `SKIP`       | `SKIP` / `OVERWRITE` |
| provider         | —           | `openai`     |                      |
| model            | —           | —            |                      |
| routingMode      | —           | `DIRECT`     | `DIRECT` / `PROXY`   |

**Валидация:** `imageTypeIds` и `styleIds` не могут содержать неопределённый тип (`00000000-0000-0000-0000-000000000001`) или стиль (`00000000-0000-0000-0000-000000000002`) — вернёт 400.

**Ответ** — массив по каждой паре (тип × стиль):

```json
[
	{
		"requestId": "<uuid>",
		"imageTypeId": "<uuid>",
		"styleId": "<uuid>",
		"status": "DONE",
		"createdAssetId": "<uuid>",
		"fileUri": null,
		"errorMessage": null
	}
]
```

Статусы: `DONE` — создан asset, `SKIPPED` — дубликат + SKIP, `FAILED` — ошибка AI.

---

## Assets (Галерея)

Картинки от **всех** пользователей, отсортированы по дате (новые первые).

`styleId` — опциональный: без него возвращаются все ассеты типа (все стили).

```
GET /assets?imageTypeId=<uuid>

GET /assets?imageTypeId=<uuid>&styleId=<uuid>
```

Header `UserId` не нужен.

```json
[
	{
		"id": "<uuid>",
		"ownerUserId": "<uuid | null>",
		"imageTypeId": "<uuid>",
		"styleId": "<uuid>",
		"promptId": "<uuid>",
		"finalPromptSnapshot": "a photo of. style of Aivazovsky. кузнец куёт меч",
		"fileUri": "generated/.../xxx.png",
		"provider": "stub",
		"model": "stub-model",
		"createdAt": "2026-02-28T12:15:00+03:00"
	}
]
```

---

## Сборка prompt

```
final_prompt = [type_prompt]. [style_prompt]. [user_prompt]
hash = SHA-256(final_prompt + "|" + generation_params)
```

Одинаковый текст + разные params (размер) = разные hash = разные картинки.

---

## Ошибки

| Код | Когда                                                                      |
| --- | -------------------------------------------------------------------------- |
| 400 | Пустое имя/промпт, дубликат имени, генерация с неопределённым типом/стилем |
| 403 | Удаление чужого или неопределённого типа/стиля                             |
| 404 | Сущность не найдена                                                        |
| 500 | Внутренняя ошибка                                                          |

---

## Связи

```
users
  ├─→ image_types.created_by_user_id  (SET NULL)
  ├─→ styles.created_by_user_id       (SET NULL)
  ├─→ assets.owner_user_id            (SET NULL)
  ├─→ prompts.owner_user_id           (SET NULL)
  ├─→ generation_batches.owner_user_id(SET NULL)
  ├─→ generation_requests.owner_user_id(SET NULL)
  ├─→ user_favorite_types             (CASCADE)
  └─→ user_favorite_styles            (CASCADE)

image_types
  ├─→ assets.image_type_id            (переназначение на UNDEFINED через код)
  ├─→ generation_requests.image_type_id(переназначение на UNDEFINED через код)
  └─→ user_favorite_types             (CASCADE)

styles — аналогично image_types

generation_batches
  └─→ generation_requests.batch_id    (CASCADE)

prompts
  └─→ assets.prompt_id
```
