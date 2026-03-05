# Изменения архитектуры — журнал решений

Этот документ — референс для обновления остальной документации в `result/`.

---

## 1. Возобновление генерации после перезагрузки сервера

### Как было (старая логика)

При падении/перезагрузке сервера класс `GenerationStartupCleaner` помечал **все RUNNING запросы как FAILED**:

```java
@EventListener(ApplicationReadyEvent.class)
public void cleanupStaleRequests() {
    UPDATE generation_requests
    SET status = 'FAILED', error_message = 'Прервано при перезагрузке сервера'
    WHERE status = 'RUNNING'
}
```

**Проблема:** работа полностью терялась. Если из 20 запросов 15 были ещё PENDING, а 1 был RUNNING — все 16 становились FAILED. Пользователь должен был перегенерировать вручную.

### Как стало (новая логика)

#### Принцип
RUNNING и PENDING запросы **не убиваются**, а **продолжают выполнение** в фоне после рестарта сервера.

#### Затронутые файлы

| Файл | Что изменилось |
|------|---------------|
| `AiImgBackApplication.java` | Добавлен `@EnableAsync` |
| `GenerationStartupCleaner.java` | Полностью переписан: вместо UPDATE→FAILED теперь находит незавершённые batch и вызывает `resumeBatchAsync` |
| `GenerationService.java` | Убран `@Transactional` с класса. Добавлен метод `resumeBatchAsync(Long batchId)` с аннотацией `@Async` |
| `GenerationRequestRepository.java` | Добавлены методы `findByStatus(RequestStatus)` и `findUnfinishedBatchIds()` |

#### GenerationStartupCleaner — новая логика

```
@EventListener(ApplicationReadyEvent.class)
resumeStaleRequests():
  1. Запросить список уникальных batch_id из generation_requests
     WHERE status IN ('RUNNING', 'PENDING')
  2. Если пусто — ничего не делать
  3. Для каждого batchId → вызвать generationService.resumeBatchAsync(batchId)
```

Не блокирует старт приложения — каждый batch обрабатывается в отдельном потоке.

#### GenerationService.resumeBatchAsync(batchId) — полный алгоритм

```
@Async
resumeBatchAsync(batchId):
  1. Загрузить batch из БД (получить provider, userPrompt, generationParams, model, dedupeMode)
  2. Загрузить все requests этого batch, отфильтровать только RUNNING и PENDING
  3. Разрешить AI-провайдер через ProviderRouter
     — если провайдер недоступен → пометить все как FAILED, выйти
  4. Извлечь aspectRatio из generationParams
  5. Для каждого request:
     a. Если authFailed (предыдущий запрос вернул 401/403/402) → markFailed, пропустить
     b. Проверить дедупликацию по hash:
        — если дубликат и dedupeMode=SKIP → markSkipped, пропустить
     c. markRunning
     d. Восстановить finalPrompt:
        — загрузить ImageType и Style по ID из request
        — собрать промпт через composeFinalPrompt(typePrompt, stylePrompt, batch.userPrompt)
     e. Вызвать provider.generate(finalPrompt, aspectRatio, model)
     f. Сохранить файл на диск через imageStorageService.save()
     g. Создать asset в БД
     h. markDone(requestId, assetId)
     i. При ошибке:
        — AiProviderException → markFailed, если 401/403/402 → authFailed=true
        — IOException → markFailed (ошибка сохранения файла)
        — Exception → markFailed (неожиданная ошибка)
```

#### Убран @Transactional с GenerationService

**Причина:** метод `generate()` и `resumeBatchAsync()` выполняются минуты (вызовы AI по 8-30 сек каждый). Классовый `@Transactional` держал бы транзакцию открытой всё это время, что:
- блокирует соединение из пула
- при падении откатывает ВСЕ изменения (даже успешно завершённые запросы)

Без `@Transactional` каждый вызов `markRunning`/`markDone`/`markFailed` коммитится сразу через `jdbcTemplate` (auto-commit), и уже завершённые запросы не пропадают при сбое.

#### Поток данных (новый)

```
Нормальная генерация (без сбоев):
  Клиент → POST /generations → GenerationService.generate()
    → создать batch → создать requests (PENDING)
    → цикл: PENDING → RUNNING → AI → DONE/FAILED/SKIPPED
    ← вернуть результаты клиенту
  (без изменений)

Падение сервера во время генерации:
  Запросы в БД: часть DONE, один RUNNING, остальные PENDING
  Сервер падает — состояние сохранено в БД как есть

Рестарт сервера:
  ApplicationReadyEvent
    → GenerationStartupCleaner.resumeStaleRequests()
    → SELECT DISTINCT batch_id FROM generation_requests WHERE status IN ('RUNNING','PENDING')
    → для каждого batch: generationService.resumeBatchAsync(batchId) [в отдельном потоке]
      → загрузить контекст из batch (provider, prompt, params)
      → продолжить обработку RUNNING + PENDING запросов
      → каждый результат сразу коммитится в БД
    → ничего не теряется
```

#### Граничные случаи

| Ситуация | Поведение |
|----------|-----------|
| Запрос был RUNNING, AI уже получил промпт, но ответ не дошёл | Повторная отправка. AI может сгенерировать дубль — это допустимая потеря (один лишний asset) |
| Тип или стиль удалён пока сервер лежал | `getById()` бросит `EntityNotFoundException` → запрос станет FAILED |
| Провайдер стал недоступен (ключ протух) | Все RUNNING/PENDING в batch → FAILED с сообщением |
| Сервер падает повторно во время resume | При следующем старте те же запросы подхватятся снова |
| Batch полностью завершён до падения | `findUnfinishedBatchIds()` не вернёт его — ничего не произойдёт |

#### Документация, которую нужно обновить

- `01_Модель_данных/07_Генерация.md` — описание жизненного цикла, алгоритм, поведение при сбое
- `03_Архитектура/02_Серверная_архитектура.md` — упоминание StartupCleaner, @Async, убран @Transactional
- `03_Архитектура/04_Взаимодействие_клиент-сервер.md` — сценарий отключения сервера
- `03_Архитектура/05_Интеграция_с_AI.md` — recovery on restart

---

## 2. Детали цикла вызова AI-провайдеров

Описывает что происходит «под капотом» на шаге `provider.generate(finalPrompt, aspectRatio, model)` — для каждого провайдера отдельно.

### Общий интерфейс

```java
public interface ImageAiProvider {
    byte[] generate(String prompt, String aspectRatio, String model) throws AiProviderException;
    String providerName();
    boolean isAvailable();
}
```

Все провайдеры возвращают `byte[]` — сырые байты изображения. Разница в протоколе, формате запроса/ответа, ошибках и retry.

### AiProviderException

```java
public class AiProviderException extends RuntimeException {
    int httpStatus;     // HTTP-код ответа (0 если не HTTP-ошибка)
    boolean retryable;  // можно ли повторить
}
```

- `retryable=true`: 5xx ошибки сервера, 429 rate limit, сетевые ошибки (таймаут, отказ соединения)
- `retryable=false`: 400 bad request, 401 unauthorized, 402 payment required, 403 forbidden

---

### OpenAI GPT Image

#### Инициализация (один раз при старте)

```
@PostConstruct init():
  RestTemplate с настройками:
    - connectTimeout = ai.openai.timeout-seconds (default 90)
    - readTimeout = ai.openai.timeout-seconds (default 90)
    - если ai.proxy.enabled=true → SimpleClientHttpRequestFactory с HTTP-прокси
```

#### isAvailable()

Возвращает `true` если `ai.openai.api-key` не пустой.

#### generate() — внешний цикл с retry

```
generate(prompt, aspectRatio, model):
  effectiveModel = model ?? ai.openai.model (default "gpt-image-1.5")
  maxRetries = ai.openai.max-retries (default 3)

  for attempt = 1..maxRetries:
    try:
      return doGenerate(prompt, aspectRatio, effectiveModel)
    catch AiProviderException:
      if !retryable OR attempt == maxRetries → throw (прокидываем наверх)
      delay = 2^attempt * 1000 мс (attempt 1 → 2с, attempt 2 → 4с, attempt 3 → 8с)
      Thread.sleep(delay)

  throw "все попытки исчерпаны" (сюда не должно дойти, но на всякий случай)
```

#### doGenerate() — один HTTP-запрос к OpenAI

**Запрос:**
```
POST https://api.openai.com/v1/images/generations
Content-Type: application/json
Authorization: Bearer <api-key>

{
  "model": "gpt-image-1.5",     // или gpt-image-1, gpt-image-1-mini
  "prompt": "<finalPrompt>",
  "n": 1,
  "size": "<mapped-size>",      // маппинг ниже
  "output_format": "png",
  "quality": "high"
}
```

**Маппинг aspectRatio → size:**

| aspectRatio (вход) | size (отправляется) |
|--------------------|---------------------|
| `"1:1"` | `"1024x1024"` |
| `"1:1.5"` | `"1024x1536"` (портрет) |
| `"1.5:1"` | `"1536x1024"` (альбом) |
| `null`, `""`, любой другой | `"auto"` |

**Успешный ответ (HTTP 200):**
```json
{
  "data": [
    {
      "b64_json": "<base64-encoded-png-image>"
    }
  ]
}
```

Обработка:
1. Проверить `response.body != null`, иначе → AiProviderException("пустой ответ", retryable=true)
2. Извлечь `data[0].b64_json`, проверить что не null/blank, иначе → AiProviderException("пустой base64", retryable=true)
3. `Base64.getDecoder().decode(b64)` → `byte[]`
4. Вернуть байты

**Ошибки:**

| Ситуация | Исключение | retryable | Что происходит |
|----------|-----------|-----------|----------------|
| HTTP 400 Bad Request | `HttpClientErrorException` → `AiProviderException(400, false)` | нет | Невалидный промпт, размер или модель. Retry не поможет. Запрос → FAILED |
| HTTP 401 Unauthorized | `HttpClientErrorException` → `AiProviderException(401, false)` | нет | Невалидный API-ключ. Запрос → FAILED + `authFailed=true` → все остальные в batch тоже FAILED |
| HTTP 402 Payment Required | `HttpClientErrorException` → `AiProviderException(402, false)` | нет | Закончился баланс. Аналогично 401 |
| HTTP 403 Forbidden | `HttpClientErrorException` → `AiProviderException(403, false)` | нет | Доступ запрещён. Аналогично 401 |
| HTTP 429 Too Many Requests | `HttpClientErrorException` → `AiProviderException(429, true)` | да | Rate limit. Retry через 2^attempt секунд |
| HTTP 500/502/503/504 | `HttpClientErrorException` → `AiProviderException(5xx, true)` | да | Сервер OpenAI сломался. Retry |
| Таймаут (connectTimeout / readTimeout) | `ResourceAccessException` → `AiProviderException(0, true)` | да | Сеть или прокси не отвечает. Retry |
| Прокси недоступен | `ResourceAccessException` → `AiProviderException(0, true)` | да | Прокси-сервер упал. Retry |
| DNS не разрешился | `ResourceAccessException` → `AiProviderException(0, true)` | да | Нет интернета / DNS. Retry |
| Тело ответа null | `AiProviderException("пустой ответ", 0, true)` | да | OpenAI вернул пустое тело. Retry |
| data[] пуст или null | `AiProviderException("ответ без данных", 0, true)` | да | Нет изображения в ответе. Retry |
| b64_json null/blank | `AiProviderException("пустой base64", 0, true)` | да | Изображение не закодировано. Retry |

**Особенности OpenAI:**
- Требует прокси из России (`ai.proxy.enabled=true`)
- Возвращает base64 — декодирование в памяти (~2-5 MB на изображение)
- Поддерживает выбор модели через параметр `model`
- `quality: "high"` всегда, hardcoded

#### Асинхронные механизмы OpenAI (мы не используем, но API поддерживает)

Наш код вызывает `POST /v1/images/generations` **синхронно** — отправил запрос, ждёшь ответ с картинкой. Но OpenAI предоставляет три альтернативных способа:

**1. Batch API** (`/v1/batches`)
- Загружаешь JSONL-файл с запросами (до 50 000 штук), каждый с `custom_id`
- Получаешь `batch_id`, поллишь статус: `validating → in_progress → completed`
- Результат гарантирован в течение **24 часов**
- **На 50% дешевле** синхронных вызовов
- Поддерживает вебхук `batch.completed`
- Поддерживает `/v1/images/generations` и `/v1/images/edits`
- *Плюсы:* дешевле вдвое, можно загрузить тысячи запросов разом, не нужно держать соединение
- *Минусы:* latency до 24 часов — не подходит для интерактивной генерации, нужно хранить/загружать JSONL файлы

**2. Background Mode** (Responses API)
- Отправляешь с `background: true` → сразу получаешь `{ id, status: "queued" }`
- Поллишь `GET /v1/responses/{id}`: `queued → in_progress → completed`
- Есть вебхуки: `response.completed`, `response.failed`
- Данные хранятся ~10 минут для polling
- *Плюсы:* не блокирует HTTP-соединение, можно подписаться на webhook
- *Минусы:* работает через Responses API (не через Images API напрямую), не доступен в EU-регионе, нет гарантии что работает с image generation tool

**3. Streaming / Partial Images**
- SSE-события `image_generation.partial_image` с промежуточными превью (base64)
- Параметр `partial_images: 0–3` — сколько промежуточных кадров получить
- Каждый partial — +100 output tokens к стоимости
- *Плюсы:* пользователь видит прогресс генерации в реальном времени
- *Минусы:* дороже (доп. токены), нужна SSE-инфраструктура на сервере и клиенте, не уменьшает общее время генерации

**Вывод для нашего проекта:**
- Batch API интересен для массовой генерации (например, перегенерировать все картинки) — дешевле в 2 раза, но latency до 24ч
- Streaming мог бы улучшить UX (показать пользователю прогресс), но требует переделки на SSE/WebSocket на всех уровнях (провайдер → сервер → клиент)
- Сейчас синхронный подход оптимален для нашего масштаба (десятки картинок за раз)

---

### Stability AI (Stable Image Core)

#### Инициализация (один раз при старте)

```
@PostConstruct init():
  RestTemplate с настройками:
    - connectTimeout = ai.stability.timeout-seconds (default 90)
    - readTimeout = ai.stability.timeout-seconds (default 90)
    - если ai.proxy.enabled=true → SimpleClientHttpRequestFactory с HTTP-прокси
```

#### isAvailable()

Возвращает `true` если `ai.stability.api-key` не пустой.

#### generate() — внешний цикл с retry

```
generate(prompt, aspectRatio, model):
  // параметр model ИГНОРИРУЕТСЯ — Stability имеет одну модель (Stable Image Core)
  maxRetries = ai.stability.max-retries (default 2)

  for attempt = 1..maxRetries:
    try:
      return doGenerate(prompt, aspectRatio)
    catch AiProviderException:
      if !retryable OR attempt == maxRetries → throw
      delay = 2^attempt * 1000 мс (attempt 1 → 2с, attempt 2 → 4с)
      Thread.sleep(delay)

  throw "все попытки исчерпаны"
```

#### doGenerate() — один HTTP-запрос к Stability

**Запрос:**
```
POST https://api.stability.ai/v2beta/stable-image/generate/core
Content-Type: multipart/form-data
Authorization: Bearer <api-key>
Accept: image/*

Parts:
  prompt = "<finalPrompt>"
  output_format = "png"           // из ai.stability.output-format
  aspect_ratio = "<mapped-ratio>" // маппинг ниже
```

**Ключевое отличие:** формат `multipart/form-data` (не JSON), и ответ приходит как **сырые байты** (не base64).

**Маппинг aspectRatio:**

Допустимые значения Stability: `1:1, 16:9, 9:16, 21:9, 9:21, 2:3, 3:2, 4:5, 5:4`

| aspectRatio (вход) | Поведение |
|--------------------|-----------|
| Совпадает с допустимым (например `"16:9"`) | Используется как есть |
| Не совпадает (например `"1:1.5"`) | Конвертируется в ближайший допустимый по числовому соотношению сторон |
| `null`, `""` | `"1:1"` |

Алгоритм конвертации `convertToNearestValid`:
```
1. Разбить по ":" → w, h (как double)
2. target = w / h
3. Для каждого допустимого ratio:
   vRatio = vW / vH
   diff = |target - vRatio|
4. Вернуть ratio с минимальным diff
```

Примеры:
- `"1:1.5"` → target=0.667 → ближайший `"2:3"` (0.667) — точное совпадение
- `"1.5:1"` → target=1.5 → ближайший `"3:2"` (1.5) — точное совпадение
- `"1:2"` → target=0.5 → ближайший `"9:21"` (0.429) или `"2:3"` (0.667) — зависит от diff

**Успешный ответ (HTTP 200):**

Тело ответа — сырые байты изображения (PNG/WebP/JPEG в зависимости от output_format). Не JSON, не base64.

Обработка:
1. `response.getBody()` → `byte[]`
2. Проверить `!= null && length > 0`, иначе → AiProviderException("пустой ответ", retryable=true)
3. Вернуть байты

**Ошибки:**

| Ситуация | Исключение | retryable | Что происходит |
|----------|-----------|-----------|----------------|
| HTTP 400 Bad Request | `HttpClientErrorException` → `AiProviderException(400, false)` | нет | Невалидный промпт или aspect_ratio. FAILED |
| HTTP 401 Unauthorized | `HttpClientErrorException` → `AiProviderException(401, false)` | нет | Невалидный API-ключ. FAILED + authFailed |
| HTTP 402 Payment Required | `HttpClientErrorException` → `AiProviderException(402, false)` | нет | Закончились кредиты (3 кредита/генерацию). FAILED + authFailed |
| HTTP 403 Forbidden | `HttpClientErrorException` → `AiProviderException(403, false)` | нет | Доступ запрещён. FAILED + authFailed |
| HTTP 429 Too Many Requests | `HttpClientErrorException` → `AiProviderException(429, true)` | да | Rate limit. Retry |
| HTTP 500/502/503 | `HttpClientErrorException` → `AiProviderException(5xx, true)` | да | Сервер Stability упал. Retry |
| Таймаут | `ResourceAccessException` → `AiProviderException(0, true)` | да | Retry |
| Сеть/DNS | `ResourceAccessException` → `AiProviderException(0, true)` | да | Retry |
| Пустое тело ответа | `AiProviderException("пустой ответ", 0, true)` | да | Retry |

**Особенности Stability:**
- Работает напрямую из России (прокси не обязателен, но можно включить)
- Возвращает **сырые байты** — нет декодирования base64, меньше нагрузки на память
- Модель фиксированная (Stable Image Core), параметр `model` игнорируется
- 3 кредита за каждую генерацию
- Только английский язык в промптах

#### Асинхронные механизмы Stability (отсутствуют для генерации)

API генерации картинок у Stability **полностью синхронный**. Нет:
- Job ID / polling
- Вебхуков
- Batch API
- Streaming / partial images
- Background mode

Единственные async-эндпоинты Stability — **Creative Upscale** и **Image-to-Video**:
- `POST /v2beta/stable-image/upscale/creative` → возвращает `generation_id`
- `GET /v2beta/results/{generation_id}` → поллинг до готовности

Но к генерации картинок (`/v2beta/stable-image/generate/core`) это не относится — там всегда синхронный ответ.

**Вывод:** для Stability единственный способ отслеживать прогресс — на уровне нашего сервера (считать завершённые requests из batch и отдавать клиенту).

---

### Stub-провайдер (для тестов)

Активируется через `ai.stub.enabled=true` в application.properties.

- `providerName()` → `"stub"`
- `isAvailable()` → всегда `true`
- `generate()` → возвращает захардкоженный 1x1 красный PNG, дополненный до 1100 байт (чтобы пройти валидацию MIN_IMAGE_SIZE=1024)
- Не делает HTTP-запросов, не кидает ошибок, мгновенный ответ

---

### После получения byte[] — сохранение на диск

Выполняется `ImageStorageService.save(byte[])`:

```
save(imageBytes):
  1. validate(imageBytes):
     — if null или < 1024 байт → throw IllegalArgumentException("повреждённый файл")
     — проверить magic bytes:
       PNG:  89 50 4E 47
       JPEG: FF D8 FF
       WebP: RIFF....WEBP (байты 0-3 = "RIFF", 8-11 = "WEBP")
     — если ни один не совпал → throw IllegalArgumentException("неизвестный формат")

  2. detectExtension(imageBytes):
     PNG  → ".png"
     JPEG → ".jpg"
     WebP → ".webp"

  3. fileName = UUID.randomUUID() + extension
     filePath = <ai.images.output-dir>/<fileName>

  4. Files.write(filePath, imageBytes)

  5. return "<output-dir>/<fileName>"   // относительный путь для записи в БД
```

**Ошибки на этапе сохранения:**

| Ситуация | Исключение | Что происходит |
|----------|-----------|----------------|
| Файл < 1 KB | `IllegalArgumentException` | Попадает в общий catch → markFailed |
| Неизвестный формат (не PNG/JPEG/WebP) | `IllegalArgumentException` | markFailed |
| Нет места на диске | `IOException` | Ловится в catch(IOException) в GenerationService → markFailed |
| Нет прав на запись в папку | `IOException` | markFailed |
| Папка не существует | Не произойдёт — создаётся при @PostConstruct | — |

---

### Полный цикл одного запроса (сводка)

```
Дедупликация — ДО создания requests (§3):
  POST /generations/check → клиент показывает дубликаты → пользователь решает
  POST /generations с overwriteDuplicates=true/false
  Сервер создаёт requests только для нужных пар (дубликаты не попадают в БД)

Для одного GenerationRequest в цикле:

1. Проверка authFailed → если да, сразу FAILED
2. markRunning(id) → статус RUNNING в БД
3. Собрать finalPrompt из type.typePrompt + style.stylePrompt + batch.userPrompt
4. provider.generate(finalPrompt, aspectRatio, model):
   ┌─ OpenAI:
   │  POST JSON → https://api.openai.com/v1/images/generations
   │  ← JSON с base64 → decode → byte[]
   │  retry до 3 раз (2с, 4с, 8с) при 5xx/429/сетевых
   │
   └─ Stability:
      POST multipart → https://api.stability.ai/v2beta/stable-image/generate/core
      ← raw bytes (PNG/WebP)
      retry до 2 раз (2с, 4с) при 5xx/429/сетевых

5. ImageStorageService.save(byte[]):
   validate magic bytes + size ≥ 1KB
   → UUID.ext → Files.write → return path

6. AssetRepository.create(imageTypeId, styleId, fileUri) → asset

7. markDone(requestId, assetId) → статус DONE

Ошибки на любом шаге 4-6:
  AiProviderException(retryable=false) → markFailed
  AiProviderException(401/402/403) → markFailed + authFailed=true (остальные в batch тоже FAILED)
  IOException → markFailed
  Exception → markFailed
```

---

### Генерация нескольких картинок за один API-запрос (параметр `n` / `samples`)

Сейчас наш код всегда отправляет `"n": 1` (OpenAI) и не передаёт `samples` (Stability). Один request = один HTTP-вызов = одна картинка. Ниже — что изменится, если отправлять `n > 1`.

#### OpenAI: параметр `n`

| Модель | `n` поддерживается? | Диапазон |
|--------|---------------------|----------|
| gpt-image-1.5 | Да | 1–10 |
| gpt-image-1 | Да | 1–10 |
| gpt-image-1-mini | Да | 1–10 |
| dall-e-3 | **Нет** (только `n=1`) | — |
| dall-e-2 | Да | 1–10 |

Формат ответа не меняется — массив `data[]` просто содержит больше элементов:

```json
{
  "data": [
    { "b64_json": "<image-1>" },
    { "b64_json": "<image-2>" },
    { "b64_json": "<image-3>" }
  ]
}
```

Цена линейная: 3 картинки стоят ×3. Скидки за батч нет.

#### Stability AI: параметр `samples`

| API | Параметр | Поддержка |
|-----|----------|-----------|
| v1 (SDXL, SD 1.6) — **deprecated** | `samples: 1..10` | Да, но `Accept` обязан быть `application/json` |
| v2beta (SD3, Ultra, Core) — **текущий** | — | **Нет**. Всегда 1 картинка за запрос |

Мы используем v2beta (`/v2beta/stable-image/generate/core`) → **батч невозможен** на уровне API.

#### Плюсы batch-генерации (n > 1)

1. **Меньше HTTP-запросов** — вместо 10 запросов один. Меньше overhead на TLS handshake, заголовки, round-trip
2. **Проще код** — не нужен цикл на стороне сервера, один вызов → массив результатов
3. **Атомарность** — все картинки одного промпта приходят в одном ответе

#### Минусы batch-генерации (n > 1)

1. **Все-или-ничего при ошибке** — если запрос упал (таймаут, 5xx), теряются ВСЕ картинки разом, а не одна. При n=1 остальные уже сохранены
2. **Таймауты** — генерация 10 картинок ≈ 10× дольше. При нашем timeout-seconds=90 это почти гарантированный таймаут. Придётся ставить 300–600с
3. **Память** — 10 base64-картинок в одном JSON ≈ 30–50 MB в памяти. Для Stability (v1, `samples`) — аналогично в `artifacts[]`
4. **Один промпт на все** — `n` повторяет один и тот же промпт. У нас каждый request = уникальная пара (type × style) = **уникальный finalPrompt**. Параметр `n` нам не подходит для основного сценария
5. **Несовместимость со Stability v2beta** — наш второй провайдер вообще не поддерживает батч. Код придётся раздваивать
6. **Retry усложняется** — при n=1 retry одной картинки прозрачен. При n>1 надо разбираться, какие из batch прошли, а какие нет (особенно если content filter сработал на 1 из 10)
7. **Resume при рестарте** — сейчас один request = один HTTP-вызов. Если batch-запрос с n=5 упал на середине — нет возможности узнать, сколько картинок успело сгенериться на стороне API

#### Вывод для нашего проекта

**Не применимо.** У нас каждый `GenerationRequest` — это уникальная пара `(imageType, style)` со своим уникальным `finalPrompt`. Параметр `n` отправляет один промпт несколько раз, что не соответствует нашей модели. Единственный сценарий, где `n > 1` имел бы смысл — если пользователь хочет несколько вариантов одной и той же комбинации (type + style + prompt), но такой функции сейчас нет.

Если в будущем появится функция "несколько вариантов одного промпта" — можно использовать `n` для OpenAI (gpt-image-1/1.5), но для Stability придётся отправлять параллельные запросы.

---

## 3. Переделка дедупликации: клиентская проверка вместо серверного SKIP

### Как было (старая логика)

Клиент отправлял `dedupeMode: "SKIP" | "OVERWRITE"` в `POST /generations`. Сервер:
1. Создавал request для КАЖДОЙ пары тип×стиль (включая дубликаты)
2. В цикле обработки проверял hash → если дубликат и SKIP → `markSkipped(id)`, статус `SKIPPED`
3. Дубликаты занимали место в БД (строка в generation_requests со статусом SKIPPED)

**Проблемы:**
- Пользователь не знал заранее, сколько дубликатов — узнавал только после генерации
- Создавались лишние записи в БД для SKIPPED запросов
- Не было возможности осознанно выбрать "пересоздать именно эти 8 из 20"

### Как стало (новая логика)

#### Принцип
**Двухшаговый процесс:**
1. Клиент сначала вызывает `POST /generations/check` — получает `{ totalCount, duplicateCount, newCount }`
2. Клиент показывает пользователю: "Генерация 20 картинок. 8 уже существуют. Пересоздать?"
3. Пользователь выбирает → клиент отправляет `POST /generations` с `overwriteDuplicates: true/false`
4. Если `false` — сервер просто НЕ создаёт requests для дубликатов (не SKIPPED, а вообще не существуют)

#### Удалено

| Что | Где было |
|-----|----------|
| `DedupeMode` enum (SKIP, OVERWRITE) | `client-utils/enums/DedupeMode.java` — **удалён** |
| `SKIPPED` статус | `RequestStatus` enum — **удалён** |
| `markSkipped()` метод | `GenerationRequestRepository` — **удалён** |
| `dedupe_mode` колонка в БД | `generation_batches` — **заменена** на `overwrite_duplicates BOOLEAN` |
| `ck_batches_dedupe` constraint | `generation_batches` — **удалён** |
| `SKIPPED` в `ck_requests_status` | `generation_requests` — **убран** из CHECK |

#### Добавлено

| Что | Где |
|-----|-----|
| `GenerationCheckResult` DTO | `client-utils/dto/` — `{ totalCount, duplicateCount, newCount }` |
| `overwriteDuplicates` поле | `GenerateRequest` DTO — `boolean`, default `false` |
| `overwrite_duplicates` колонка | `generation_batches` — `BOOLEAN NOT NULL DEFAULT false` |
| `POST /generations/check` эндпоинт | `GenerationController` + `IGenerationController` |
| `check()` метод | `GenerationService` — считает дубликаты без создания записей |
| Liquibase `changeset-v3.xml` | Миграция: переименование колонки, обновление constraints |

#### Затронутые файлы

| Файл | Что изменилось |
|------|---------------|
| `DedupeMode.java` | **Удалён** |
| `RequestStatus.java` | Убран `SKIPPED` |
| `GenerateRequest.java` | `dedupeMode: DedupeMode` → `overwriteDuplicates: boolean` |
| `GenerationCheckResult.java` | **Создан** |
| `IGenerationController.java` | Добавлен `check()` |
| `GenerationController.java` | Добавлен `POST /generations/check` |
| `GenerationService.java` | Добавлен `check()`, переписан `generate()`, убрана дедуп-логика из цикла, обновлён `resumeBatchAsync()` |
| `GenerationRequestRepository.java` | Удалён `markSkipped()` |
| `GenerationBatch.java` | `dedupeMode: DedupeMode` → `overwriteDuplicates: boolean` |
| `GenerationBatchRepository.java` | Обновлён маппер и `create()` |
| `changeset-v3.xml` | **Создан** — миграция БД |
| `changelog-master.xml` | Добавлен include v3 |
| `GenerationControllerTest.java` | Тесты переписаны под новый API |

#### Новый API

**POST /generations/check**
```
Request (тот же GenerateRequest):
{
  "userPrompt": "горный пейзаж",
  "imageTypeIds": [2, 3, 4],
  "styleIds": [2, 5],
  "generationParams": "{\"aspectRatio\": \"1:1\"}"
}

Response:
{
  "totalCount": 6,       // 3 типа × 2 стиля
  "duplicateCount": 2,   // 2 пары уже сгенерированы
  "newCount": 4          // 4 новых
}
```

**POST /generations** (обновлён)
```
Request:
{
  "userPrompt": "горный пейзаж",
  "imageTypeIds": [2, 3, 4],
  "styleIds": [2, 5],
  "overwriteDuplicates": false,   // ← было dedupeMode: "SKIP"/"OVERWRITE"
  "provider": "openai",
  "model": "gpt-image-1.5",
  "routingMode": "PROXY"
}

Response (если overwrite=false и 2 дубликата):
[
  // только 4 результата — дубликаты не создали requests
  { "requestId": 1, "status": "DONE", ... },
  { "requestId": 2, "status": "DONE", ... },
  { "requestId": 3, "status": "FAILED", ... },
  { "requestId": 4, "status": "DONE", ... }
]

Response (если overwrite=true):
[
  // все 6 результатов
  ...
]
```

#### Алгоритм check()

```
check(request):
  1. Валидация (prompt, typeIds, styleIds не пустые, нет UNDEFINED)
  2. Загрузить types и styles из БД
  3. Для каждой пары (type, style):
     — собрать finalPrompt
     — вычислить hash = SHA-256(finalPrompt + "|" + generationParams)
     — проверить existsByHash(hash) в generation_requests (DONE)
     — если есть → duplicateCount++
  4. Вернуть { totalCount, duplicateCount, newCount = total - duplicate }
```

#### Алгоритм generate() — изменения

```
generate(userId, request):
  ...
  Шаг 2 (формирование requests) — ИЗМЕНЁН:
    for (type, style):
      hash = computeHash(...)
      if !overwriteDuplicates AND existsByHash(hash):
        log "пропуск дубликата" → continue   // НЕ создаём request
      requestRepository.create(...)           // создаём только для не-дубликатов

  Шаг 4 (обработка) — УПРОЩЁН:
    Нет проверки дедупликации в цикле — все requests точно нужно генерировать
  ...
```

#### Жизненный цикл статусов (обновлённый)

```
Было:   PENDING → RUNNING → DONE / FAILED / SKIPPED
Стало:  PENDING → RUNNING → DONE / FAILED
```

`SKIPPED` больше не существует. Дубликаты просто не создают запись в БД.

#### UX-сценарий на клиенте

```
1. Пользователь выбрал 3 типа × 2 стиля, написал промпт
2. Нажал "Генерировать"
3. Клиент вызывает POST /generations/check
4. Ответ: { totalCount: 6, duplicateCount: 2, newCount: 4 }
5. Если duplicateCount > 0:
   Клиент показывает диалог:
   "Генерация 6 картинок. 2 уже существуют. Пересоздать?"
   [Пропустить дубликаты] [Пересоздать все]
6a. "Пропустить" → POST /generations { overwriteDuplicates: false } → 4 запроса
6b. "Пересоздать" → POST /generations { overwriteDuplicates: true } → 6 запросов
7. Если duplicateCount == 0:
   Сразу POST /generations без диалога
```

#### Документация, которую нужно обновить

- `01_Модель_данных/07_Генерация.md` — убрать SKIPPED, убрать dedupe_mode, описать overwrite_duplicates, описать check endpoint
- `01_Модель_данных/01_Обзор_модели.md` — убрать упоминание DedupeMode
- `02_Экраны/02_Генерация.md` — обновить UI: добавить диалог дубликатов, убрать radio SKIP/OVERWRITE
- `03_Архитектура/02_Серверная_архитектура.md` — убрать DedupeMode из описания
- `03_Архитектура/04_Взаимодействие_клиент-сервер.md` — обновить API, добавить check endpoint

---

## 4. Проксирование запросов к AI-провайдерам

### Как сейчас реализовано

Прокси настраивается **глобально** в `application.properties`:

```
ai.proxy.enabled=false
ai.proxy.host=
ai.proxy.port=10000
ai.proxy.username=
ai.proxy.password=
ai.proxy.type=HTTP          # HTTP или SOCKS5
```

Оба провайдера (OpenAI, Stability) при старте в `@PostConstruct init()` создают **один `RestTemplate`** на весь жизненный цикл:

```
if (props.getProxy().isEnabled() && !props.getProxy().getHost().isBlank()) {
    factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
}
```

- Если `proxy.enabled=true` → **оба** провайдера идут через прокси
- Если `proxy.enabled=false` → **оба** напрямую
- Переключить без перезагрузки сервера **невозможно** (RestTemplate создаётся один раз)

### RoutingMode — мёртвый код

В `GenerateRequest` есть поле `routingMode: RoutingMode` (enum: `DIRECT`, `PROXY`). Клиент его отправляет, сервер **сохраняет** в `generation_batches.routing_mode`. Но:

- `ProviderRouter.resolve()` — не читает `routingMode`
- `OpenAiProvider.generate()` — не принимает `routingMode`
- `StabilityProvider.generate()` — не принимает `routingMode`
- `RestTemplate` создан при старте — его нельзя переключить per-request

**Итог:** `RoutingMode` enum, поле в DTO, колонка в БД — всё это не влияет ни на что. Мёртвый код.

### Реальная потребность в прокси

| Провайдер | Из России | Из EU/US |
|-----------|-----------|----------|
| **OpenAI** | Нужен прокси (заблокирован) | Напрямую |
| **Stability** | Напрямую (не заблокирован) | Напрямую |

Сейчас `ai.proxy.enabled=true` гонит через прокси **оба** провайдера, хотя Stability в этом не нуждается. Это лишний hop и потенциальная точка отказа.

### Нужен ли переключатель прокси на фронте?

**Нет.** Причины:

1. **Это инфраструктурная настройка, не пользовательская.** Нужен прокси или нет — зависит от того, где развёрнут сервер (Россия → нужен для OpenAI, Европа → не нужен). Пользователь этим не управляет.

2. **Безопасность.** Прокси-креды (host, port, username, password) — серверный секрет. На клиент они попадать не должны.

3. **Пользователь и так выбирает провайдера.** Если OpenAI заблокирован без прокси — сервер вернёт ошибку подключения. Пользователь переключит на Stability. Это уже работает.

4. **Никакого UX-выигрыша.** Пользователь не знает и не должен знать, через какой прокси идёт трафик. Для него важен результат: картинка пришла или ошибка.

### Как должно быть (рекомендация)

**Per-provider прокси** вместо глобального:

```properties
# Вариант: per-provider proxy
ai.openai.proxy.enabled=true
ai.openai.proxy.host=proxy.example.com
ai.openai.proxy.port=10000

ai.stability.proxy.enabled=false
# Stability идёт напрямую
```

Каждый провайдер создаёт свой `RestTemplate` с собственными прокси-настройками. OpenAI через прокси, Stability напрямую.

**RoutingMode — удалить:**

| Что | Действие |
|-----|----------|
| `RoutingMode` enum | Удалить |
| `routingMode` поле в `GenerateRequest` | Удалить |
| `routingMode` поле в `GenerationBatch` | Удалить |
| `routing_mode` колонка в `generation_batches` | Liquibase миграция: `DROP COLUMN` |
| Маппер в `GenerationBatchRepository` | Убрать `routing_mode` |
| `GenerationService.generate()` | Убрать передачу `routingMode` в `batchRepository.create()` |

### Управление прокси при эксплуатации

| Сценарий | Что делать |
|----------|------------|
| Сервер в России, нужен OpenAI | `ai.proxy.enabled=true`, указать host/port/creds |
| Сервер за рубежом | `ai.proxy.enabled=false` |
| Прокси сменился | Поменять в application.properties → перезагрузить сервер |
| Прокси упал | OpenAI вернёт `ResourceAccessException` → retry. Если все retry исчерпаны → `FAILED`. Пользователь увидит ошибку и может выбрать Stability |
| Нужен SOCKS5 вместо HTTP | `ai.proxy.type=SOCKS5` (но код сейчас hardcoded `Proxy.Type.HTTP` — надо фиксить, чтобы читал `props.getProxy().getType()`) |

**Баг:** в `OpenAiProvider` и `StabilityProvider` тип прокси **захардкожен** как `Proxy.Type.HTTP`, хотя в `AiProperties` есть поле `type = "HTTP"` (может быть SOCKS5). Нужно заменить:
```java
// Было:
Proxy.Type.HTTP
// Должно быть:
Proxy.Type.valueOf(props.getProxy().getType())  // "HTTP" → Proxy.Type.HTTP, "SOCKS5" → Proxy.Type.SOCKS5
```

Также: при `proxy.type=HTTP` с аутентификацией (username/password) — `SimpleClientHttpRequestFactory` **не передаёт** креды. Нужен `Authenticator` или переход на Apache HttpClient / OkHttp. Сейчас username/password в конфиге есть, но **не используются**.

### Сравнение: глобальный прокси vs per-provider

**Суть:** у нас один оплаченный прокси. Вопрос не в "два прокси vs один", а в том, **все ли провайдеры через него ходят, или только те, кому он реально нужен**.

- **Глобальный (сейчас):** `ai.proxy.enabled=true` → все провайдеры идут через прокси. Один переключатель на весь сервер.
- **Per-provider:** тот же самый один прокси, но указывается только у OpenAI. Stability идёт напрямую, потому что ему прокси не нужен.

---

#### Глобальный прокси (текущая реализация)

**Плюсы:**

1. **Простота настройки.** 5 строк в конфиге — указал один раз, все провайдеры ходят через него. Не нужно думать, кому прокси нужен, а кому нет.
2. **Минимум кода.** Оба провайдера читают один и тот же `props.getProxy()`. Нет дублирования логики.
3. **Быстрое подключение нового провайдера.** Добавляешь провайдер → он автоматически через прокси. Ничего дополнительно не настраиваешь.

**Минусы:**

1. **Лишний hop для Stability.** Stability не заблокирован из РФ, но всё равно идёт через прокси. Лишние +50-200 мс latency на каждый запрос.
2. **Единая точка отказа.** Прокси упал → **оба** провайдера не работают, хотя Stability мог бы работать напрямую.
3. **Расход трафика на прокси.** Stability возвращает raw bytes (~1-3 MB на картинку). Этот трафик идёт через прокси зря, расходуя трафик/bandwidth оплаченного прокси.
4. **Нет возможности отключить прокси для одного провайдера.** Если Stability начнёт глючить через прокси — нельзя отключить прокси только для него, не отключив OpenAI.

**Подводные камни:**

1. **Proxy auth не работает.** `SimpleClientHttpRequestFactory` не передаёт username/password. Если прокси требует аутентификацию — запросы идут без неё. Работает только если прокси авторизует по IP.
2. **Proxy type hardcoded.** Код использует `Proxy.Type.HTTP` всегда, хотя в конфиге есть `ai.proxy.type` который может быть `SOCKS5`. Значение из конфига игнорируется.
3. **Некоторые прокси фильтруют домены.** Если прокси настроен на whitelist доменов (только `api.openai.com`), запросы к `api.stability.ai` через него провалятся с 403.

---

#### Per-provider прокси (рекомендация)

**Конфиг:**
```properties
# Тот же один прокси, но только для OpenAI
ai.openai.proxy.enabled=true
ai.openai.proxy.host=gate.proxyseller.io
ai.openai.proxy.port=10000
ai.openai.proxy.username=user123
ai.openai.proxy.password=pass456

# Stability — напрямую
ai.stability.proxy.enabled=false
```

**Плюсы:**

1. **Корректная маршрутизация.** Каждый провайдер идёт оптимальным путём. OpenAI через прокси (заблокирован), Stability напрямую (не заблокирован). Нет лишних hop-ов.
2. **Изоляция отказов.** Прокси упал → OpenAI не работает, но Stability продолжает генерировать. Пользователь может переключиться.
3. **Экономия прокси-трафика.** Stability-трафик (raw bytes, 1-3 MB на картинку) не гоняется через прокси. Если прокси оплачивается по трафику — прямая экономия.
4. **Явность конфига.** Видно кто через прокси, кто напрямую. Не нужно помнить "Stability тоже через прокси, но ему это не нужно".
5. **Гибкость на будущее.** Если добавится третий провайдер (Midjourney, Flux) — можно указать ему другой прокси или без прокси.

**Минусы:**

1. **Больше строк конфигурации.** Было 5 строк, станет 5–10. Если оба провайдера через один прокси — дублирование host/port/creds.
2. **Изменение `AiProperties`.** Нужно переместить `Proxy` внутрь `OpenAi` и `Stability` классов, либо сделать два отдельных блока. Ломается формат `application.properties`.
3. **Чуть больше кода.** Каждый провайдер читает свой блок прокси вместо общего. Хотя это пара строк.

**Подводные камни:**

1. **Breaking change конфига.** `ai.proxy.enabled=true` перестанет работать. Нужно либо поддержать fallback (если есть `ai.openai.proxy.*` — использовать, иначе использовать глобальный `ai.proxy.*`), либо документировать как breaking change.
2. **Proxy auth всё так же не работает** (баг `SimpleClientHttpRequestFactory`). При переходе на per-provider это надо фиксить сразу, иначе переход бессмысленный. Варианты:
   - Перейти на `OkHttp` (уже есть в `client-utils`) — поддерживает proxy auth из коробки
   - Перейти на `Apache HttpClient` — стандарт Spring, поддерживает proxy auth
   - `java.net.Authenticator.setDefault()` — глобальный хак, работает для обоих, но не per-provider
3. **SOCKS5 + auth.** `java.net.Proxy(Type.SOCKS, ...)` не передаёт username/password. Если прокси SOCKS5 с аутентификацией — нужен `Authenticator` или библиотечный SOCKS-клиент. Не работает ни в текущей, ни в per-provider реализации.
4. **Тестирование.** Сейчас тесты не проверяют прокси. При per-provider нужно покрыть: с прокси / без прокси / с auth / без auth / невалидный host.
5. **Hot reload невозможен** ни в глобальном, ни в per-provider варианте. `RestTemplate` создаётся в `@PostConstruct` один раз. Смена прокси = рестарт сервера. Для нашего масштаба это ок.

---

#### Вывод

**Стоит менять, но не срочно.** Текущий глобальный прокси работает — Stability через лишний hop, но функционально корректно.

Оптимальный момент — когда будем фиксить баги с proxy auth и SOCKS5 type. Тогда заодно разнести по провайдерам. Одно без другого не имеет смысла — если auth не работает, то и per-provider конфиг с username/password бесполезен.

**Минимальные фиксы прямо сейчас** (без разнесения per-provider):
1. Фикс `Proxy.Type.HTTP` → `Proxy.Type.valueOf(props.getProxy().getType())` — 2 строки в двух файлах
2. Эти фиксы не ломают обратную совместимость конфига

---

## 5. Пометки: клиентская валидация (что бэк не проверяет)

Факты из `04_Жизненный_Цикл/02_Фронтенд.md` — не забыть при переделке:

- email: бэк не валидирует формат, пустая строка `""` проходит NOT NULL
- displayName: пустая строка проходит
- name (тип/стиль): длина не ограничена (TEXT), бэк только blank проверяет
- typePrompt/stylePrompt: длина не ограничена
- userPrompt: длина не ограничена
- imageTypeIds/styleIds: нет лимита размера списка
- provider/model/aspectRatio/generationParams: фиксированы в AiModelRegistry, клиент формирует программно
- UserId заголовок: бэк не проверяет существование пользователя
- 400 от DataIntegrity: невозможно понять из ответа, какое поле нарушено
- 500 catch-all: сырое сообщение исключения в ответе (утечка деталей)
- ~~AiProviderException: нет отдельного обработчика → 500~~ — **решено.** Добавлен `@ExceptionHandler(AiProviderException.class)` → 502 Bad Gateway с сообщением `"Ошибка AI-провайдера: ..."`. В штатном потоке AiProviderException перехватывается внутри GenerationService и не пробрасывается до контроллера, но обработчик добавлен как защитная сетка
- GET-эндпоинты: добавить серверную пагинацию (`page` + `size`) на `/assets` (растёт с каждой генерацией). `/image-types` и `/styles` — без пагинации (десятки записей максимум, создаются вручную)
- Галерея: загрузка реальных картинок не реализована (placeholder-иконки), нет удаления/скачивания assets
- ~~routingMode: мёртвый код~~ — **удалено.** `RoutingMode` enum, поле в DTO, колонка `routing_mode` в `generation_batches`, `needsProxy` в `AiModelRegistry` — всё убираем. Прокси глобальный (`ai.proxy.enabled`), per-request переключение не нужно. Документация: `03_Архитектура/06_Прокси.md`

---

## TODO: что ещё надо будет изменить в коде при обновлении документации

Краткие пометки — привязаны к конкретным файлам/местам, которые придётся трогать.

### Клиент (aiimageclient)

- **`repository/GenerationRepository.kt`** — добавить метод `suspend fun check(params): GenerationCheckResult`
- **`repository/mock/MockGenerationRepository.kt`** — мок для `check()`, убрать `DedupeMode` из `GenerateParams`
- **`GenerateParams` data class** — убрать `dedupeMode`, добавить `overwriteDuplicates: Boolean`
- **`GenerationResult` data class** — убрать `SKIPPED` из `RequestStatus` enum
- **`ui/generation/PromptPanel.kt`** — убрать radio-кнопки SKIP/OVERWRITE, добавить логику вызова check + диалог дубликатов
- **`ui/generation/GenerationContent.kt`** — изменить flow кнопки "Генерировать": сначала check → если дубликаты → диалог → потом generate
- **`ui/generation/ResultsView.kt`** — убрать иконку/обработку SKIPPED статуса
- **`viewmodel/GenerationScreenModel.kt`** — добавить состояние для диалога дубликатов (`showDuplicateDialog`, `checkResult`), обновить `generate()` flow
- **`core/AiModelConfig.kt`** / **`AiModelRegistry.kt`** — если DedupeMode использовался в маппинге, убрать

### Сервер (уже сделано, но при обновлении доков проверить)

- **Тесты** — `GenerationControllerTest.java` уже обновлён, но при добавлении новых фич могут потребоваться доп. тесты для check endpoint с множественными типами/стилями
- **`ClientUtil.java`** — проверить, нет ли хардкода DedupeMode в прокси-генераторе (client-utils)

### Документация result/

| Файл | Что менять |
|------|-----------|
| `01_Обзор_модели.md` | Убрать DedupeMode из списка enums, убрать SKIPPED из диаграммы статусов |
| `07_Генерация.md` | Переписать: dedupe_mode → overwrite_duplicates, убрать SKIPPED из lifecycle, добавить check endpoint, обновить таблицу generation_batches |
| `02_Генерация.md` (экран) | Убрать блок "дедупликация radio SKIP/OVERWRITE" из Block 3 (PromptPanel), добавить описание диалога дубликатов, обновить flow кнопки "Генерировать" |
| `02_Серверная_архитектура.md` | Убрать DedupeMode из списка enums в client-utils, обновить описание GenerationService (check + generate), убрать @Transactional упоминание |
| `04_Взаимодействие_клиент-сервер.md` | Добавить POST /generations/check в API, обновить POST /generations (убрать dedupeMode, добавить overwriteDuplicates), убрать SKIPPED из примеров ответа |
| `05_Интеграция_с_AI.md` | Убрать упоминание SKIP/OVERWRITE режимов в разделе дедупликации, обновить описание recovery on restart |
| `01_Галерея.md` | Не затронута |
| `03_Навигация_и_общие_паттерны.md` | Не затронута |
| `01_Стек_технологий.md` | Не затронута |
| `03_Клиентская_архитектура.md` | Обновить описание repository interfaces (добавить check), обновить GenerationScreenModel state |
