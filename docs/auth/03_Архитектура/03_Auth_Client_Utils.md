# 03. Auth Client Utils

## Назначение

`auth-client-utils` — новый модуль (Maven-артефакт), подключаемый к десктоп-клиенту. Инкапсулирует всю клиентскую логику работы с авторизацией: хранение токенов, подстановка заголовка `Authorization`, автоматический refresh при 401, декодирование claims из accessToken.

Клиентский код (Compose UI, ScreenModel-ы) не знает о деталях JWT, refresh-логике и HTTP-заголовках — всё скрыто внутри модуля.

---

## Место в архитектуре

```
┌───────────────────────────────────────────────────────────┐
│                  Десктоп-клиент (Compose)                  │
│                                                           │
│  ScreenModel-ы → репозитории → OkHttpClient               │
│                                      │                    │
│                              ┌───────┴────────┐           │
│                              │ auth-client-    │           │
│                              │   utils         │           │
│                              │                 │           │
│                              │ • AuthInterceptor│          │
│                              │ • TokenManager  │           │
│                              │ • TokenDecoder  │           │
│                              └───────┬────────┘           │
│                                      │                    │
└──────────────────────────────────────┼────────────────────┘
                                       │ HTTP
                                       ▼
                              Основной бэкенд (:8080)
```

---

## Структура модуля

```
auth-client-utils/src/main/java/
└── com.example.auth.client/
    ├── AuthInterceptor.java      ← OkHttp Interceptor (Bearer + auto-refresh)
    ├── TokenManager.java         ← Хранение accessToken (память) и refreshToken (Preferences)
    ├── TokenDecoder.java         ← Base64-decode payload → UserClaims
    ├── UserClaims.java           ← DTO: userId, email, displayName, roles, exp
    └── AuthEventListener.java    ← Интерфейс: callback при необходимости логина
```

---

## Компоненты

### TokenManager

Хранит токены и предоставляет API для их чтения/записи.

| Метод                         | Что делает                                              |
| ----------------------------- | ------------------------------------------------------- |
| `getAccessToken()`            | возвращает accessToken из памяти (nullable)             |
| `getRefreshToken()`           | возвращает refreshToken из Preferences (nullable)       |
| `saveTokens(access, refresh)` | access → память, refresh → Preferences                 |
| `saveAccessToken(access)`     | обновляет только accessToken в памяти (после refresh)   |
| `clearTokens()`               | удаляет оба токена (logout / expired refresh)           |
| `hasRefreshToken()`           | есть ли сохранённый refreshToken (для startup-проверки) |

| Факт               | Детали                                                       |
| ------------------- | ------------------------------------------------------------ |
| accessToken         | в памяти (переменная) — теряется при закрытии приложения     |
| refreshToken        | в Preferences / файле — сохраняется между запусками          |
| Потокобезопасность  | доступ синхронизирован (Interceptor вызывается из IO-потока) |

### AuthInterceptor

OkHttp `Interceptor`, встраиваемый в `OkHttpClient`. Перехватывает каждый HTTP-запрос.

**Логика:**

```
Запрос уходит
│
├── Путь в списке исключений (/auth/login, /auth/register, /auth/refresh)?
│   └── Да → пропустить без заголовка
│
├── Нет → добавить заголовок Authorization: Bearer <accessToken>
│
├── Ответ 401?
│   ├── refreshToken есть?
│   │   ├── Да → POST /auth/refresh { refreshToken }
│   │   │   ├── Успех → сохранить новый accessToken → повторить оригинальный запрос
│   │   │   └── 401 → clearTokens() → AuthEventListener.onLoginRequired()
│   │   └── Нет → AuthEventListener.onLoginRequired()
│
├── Другой ответ → вернуть как есть
```

| Факт                  | Детали                                                          |
| --------------------- | --------------------------------------------------------------- |
| Исключения            | `/auth/login`, `/auth/register`, `/auth/refresh` — без Bearer  |
| Auto-refresh          | при 401 — одна попытка refresh, потом повтор запроса            |
| Защита от цикла       | если refresh сам вернул 401 — не рефрешить повторно             |
| Повторная попытка     | одна (не бесконечный цикл)                                      |
| Потокобезопасность    | refresh-вызов синхронизирован (один refresh за раз)              |

### TokenDecoder

Декодирует payload accessToken (base64, без криптографии) в `UserClaims`.

```java
public class TokenDecoder {
    public static UserClaims decode(String accessToken) {
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        // Jackson parse → UserClaims
    }
}
```

Не проверяет подпись — это делает auth-сервер. Клиент просто читает claims для отображения в UI.

### UserClaims

```java
public class UserClaims {
    Long userId;
    String email;
    String displayName;
    String roles;
    Long exp;
    Long iat;
}
```

Используется клиентом для:
- Отображение имени пользователя в UI
- Определение роли (USER / ADMIN)
- Проверка exp (опционально — для превентивного refresh)

### AuthEventListener

```java
public interface AuthEventListener {
    void onLoginRequired();
}
```

Callback, вызываемый Interceptor-ом когда refresh не удался (401 на refresh или отсутствие refreshToken). Клиент реализует этот интерфейс для перехода на экран логина.

---

## Подключение к клиенту

### Maven-зависимость

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>auth-client-utils</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Инициализация (Koin)

```kotlin
val authModule = module {
    single { TokenManager(get<AppPreferences>()) }
    single { TokenDecoder() }
    single<AuthEventListener> { /* навигация на экран логина */ }
    single { AuthInterceptor(get(), get(), get()) }
}
```

### Встраивание в OkHttpClient

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(get<AuthInterceptor>())
    .build()
```

Все HTTP-запросы клиента (и auth, и бизнес-логика) проходят через один `OkHttpClient` с `AuthInterceptor`.

---

## Зависимости модуля

| Зависимость | Причина                                          |
| ----------- | ------------------------------------------------ |
| OkHttp 4.12 | Interceptor, HTTP-вызовы для refresh            |
| Jackson     | Парсинг JSON (ответы auth-сервера, JWT payload) |

Модуль **не зависит** от Spring, Compose, Voyager или Koin — чистая Java-библиотека. Интеграция с DI-фреймворком — на стороне клиента.

---

## Жизненный цикл

```
Запуск приложения
    → TokenManager.hasRefreshToken()?
        → Да → POST /auth/refresh → успех → Галерея
        → Да → POST /auth/refresh → 401 → экран Логина
        → Нет → экран Логина

Логин / Регистрация
    → POST /auth/login (или /register)
    → TokenManager.saveTokens(access, refresh)
    → TokenDecoder.decode(access) → UserClaims → UI

Работа с приложением
    → AuthInterceptor добавляет Bearer к каждому запросу
    → При 401 → auto-refresh → повтор запроса

Logout
    → POST /auth/logout
    → TokenManager.clearTokens()
    → экран Логина

Смена пароля
    → POST /auth/change-password
    → токены остаются — перелогин не нужен
```
