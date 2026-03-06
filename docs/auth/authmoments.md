# Auth — влияние на другие модули

Краткие пояснения к изменениям в существующих модулях при внедрении JWT-авторизации.

---

## web (основной бэкенд)

### Таблица `users` → `auth_users` (общая БД, вариант B)
- Миграция Liquibase: RENAME TABLE `users` → `auth_users`, добавить колонки auth-сервера
- Auth-сервер и основной бэкенд подключаются к одной PostgreSQL
- userId берётся из JWT-токена, не из БД
- FK `*_user_id → auth_users(id)` сохраняются
- Каскады ON DELETE SET NULL / CASCADE продолжают работать

### Заголовок `UserId` → `Authorization: Bearer`
- Все контроллеры: `@RequestHeader("UserId") Long userId` → `@AuthUserId Long userId`
- userId извлекается из JWT через `JwtAuthFilter` → `SecurityContext`
- `HandlerMethodArgumentResolver` для `@AuthUserId`

### Новый фильтр `JwtAuthFilter`
- `OncePerRequestFilter`, перехватывает все запросы кроме публичных
- Публичные пути: `/auth/login`, `/auth/register`, `/auth/refresh`, `/assets/**`
- Валидирует токен через auth-сервер (`POST /validate`) с кэшированием (Caffeine, TTL 10 мин)

### Новый контроллер `AuthController`
- Проксирует `/auth/login`, `/auth/register`, `/auth/refresh` на auth-сервер
- `/auth/logout` — проксирует на auth-сервер (blacklist jti обоих токенов)

### Новые исключения в `GlobalExceptionHandler`
- `AuthenticationException` → 401
- `TokenExpiredException` → 401
- `InvalidTokenException` → 401
- `AuthServerUnavailableException` → 503
- `EmailAlreadyExistsException` → 409

---

## client-utils

### Изменения в интерфейсах контроллеров
- Интерфейсы (`IImageTypeController`, `IStyleController`, etc.) остаются
- Аннотация `@RequestHeader("UserId")` убирается из интерфейсов
- Добавляется `IAuthController` с эндпоинтами login/register/refresh/logout

---

## auth-client-utils (новый модуль)

### Обёртка над client-utils
- Хранит accessToken в памяти, refreshToken в Preferences
- OkHttp Interceptor: подставляет `Authorization: Bearer <token>` в каждый запрос
- При 401 — автоматический refresh + повтор запроса
- При неудачном refresh (401) — сигнал клиенту о необходимости повторного логина
