# TODO — ai-img-back

## Заглушки (заменить для продакшена)

### AI-клиент

- **Файл:** `GenerationService.java` → `callAiProvider()`
- Сейчас возвращает fake URI и provider="stub"
- Нужно: HTTP-клиент к OpenAI / Stability AI / другому провайдеру
- Связано: выбрать `RestTemplate` или `WebClient`

### Хранение файлов

- **Нет файла/класса** — нужно создать
- `file_uri` в assets — просто строка, файла на диске нет
- Нужно: сохранять байты от AI на диск или в S3/MinIO
- Нужно: эндпоинт `GET /assets/{id}/image` для отдачи файла клиенту

### fileUri в ответе генерации

- **Файл:** `GenerationResultDTO.java`
- `fileUri` всегда null (стоит комментарий `// заполним позже`)
- Заполнить после реализации хранилища

### Аутентификация

- **Все контроллеры** → `@RequestHeader("UserId")`
- Любой может подставить любой UUID — нет проверки
- Нужно: JWT / OAuth2 / Spring Security
- Или оставить если auth делает другой модуль (по спеке users управляется отдельно)

---

## Ограничения (работает, но может потребоваться доработка)

### Пагинация

- `GET /users`, `GET /image-types`, `GET /styles`, `GET /assets` — возвращают всё без LIMIT
- При большом количестве данных будет проблема
- Нужно: `LIMIT/OFFSET` или cursor-based pagination

### Сборка промпта

- **Файл:** `GenerationService.java` → `composeFinalPrompt()`
- Сейчас простая конкатенация: `type_prompt. style_prompt. user_prompt`
- Для реального AI может потребоваться шаблон, quality-блок, negative prompt

### Batch status логика

- **Файл:** `GenerationService.java`
- Если хоть один request DONE — batch = DONE
- Может потребоваться PARTIAL_DONE или более гранулярная логика

### GlobalExceptionHandler — 500 ответ

- **Файл:** `GlobalExceptionHandler.java` → `handleGeneric()`
- Сейчас показывает `ex.getMessage()` (оставлено для дебага)
- На проде заменить на `"Внутренняя ошибка сервера"` — утечка информации

---

## Не реализовано из спеки

### Группировка стилей

- Эпохи/страны из `mod1-raw.md`
- Справочник эпох, привязка стилей к эпохам

### Поиск и фильтрация

- Поиск типов/стилей по имени
- Фильтрация в галерее по промпту/дате/автору

---

## Порядок реализации (рекомендуемый)

1. Хранение файлов + эндпоинт отдачи картинок
2. AI API интеграция (OpenAI / Stability)
3. fileUri в GenerationResultDTO
4. Пагинация
5. Аутентификация (если не внешний модуль)
6. composeFinalPrompt — шаблон с quality-блоком
7. GlobalExceptionHandler — скрыть детали на проде
8. Группировка стилей, поиск/фильтрация
