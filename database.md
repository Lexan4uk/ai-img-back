-- =========================
-- СХЕМА БД ai-img-back v2
-- =========================
-- PK: BIGSERIAL (auto-increment), FK: BIGINT
-- UUID не используется в ключах
-- INDEX на каждый FK
-- Таблица prompts удалена (данные на batch)
-- Статус batch считается по COUNT реквестов
-- created_at только у generation_requests

-- =========================
-- Well-known IDs (constants in application code)
-- =========================
-- UNDEFINED_TYPE_ID = 1 (первая запись image_types)
-- UNDEFINED_STYLE_ID = 1 (первая запись styles)

-- =========================
-- USERS
-- =========================
CREATE TABLE users (
id BIGSERIAL PRIMARY KEY,
email TEXT NOT NULL,
display_name TEXT NOT NULL,
CONSTRAINT uq_users_email UNIQUE (email)
);

-- =========================
-- IMAGE TYPES
-- =========================
-- created_by_user_id — кто создал. Удалять может только создатель.
-- ON DELETE SET NULL — если пользователь удалён, тип остаётся (автор = NULL).
-- type_prompt — текст для AI (например "a photograph of").
-- name — человекочитаемое название для UI.
CREATE TABLE image_types (
id BIGSERIAL PRIMARY KEY,
created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
name TEXT NOT NULL,
type_prompt TEXT
);
CREATE UNIQUE INDEX uq_image_types_name ON image_types(LOWER(name));
CREATE INDEX ix_image_types_created_by ON image_types(created_by_user_id);

-- Seed: «Неопределённый» (id=1, неудаляемый, fallback при удалении типа)
INSERT INTO image_types (name, type_prompt) VALUES ('Неопределённый', NULL);

-- =========================
-- STYLES
-- =========================
-- Зеркальная копия image_types.
-- style_prompt — текст для AI (например "style of Aivazovsky").
CREATE TABLE styles (
id BIGSERIAL PRIMARY KEY,
created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
name TEXT NOT NULL,
style_prompt TEXT
);
CREATE UNIQUE INDEX uq_styles_name ON styles(LOWER(name));
CREATE INDEX ix_styles_created_by ON styles(created_by_user_id);

-- Seed: «Неопределённый» (id=1, неудаляемый, fallback при удалении стиля)
INSERT INTO styles (name, style_prompt) VALUES ('Неопределённый', NULL);

-- =========================
-- FAVORITES (per-user bookmarks)
-- =========================
-- CASCADE с обеих сторон:
-- пользователь удалён → его избранное удаляется
-- тип/стиль удалён → записи избранного удаляются
CREATE TABLE user_favorite_types (
user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
image_type_id BIGINT NOT NULL REFERENCES image_types(id) ON DELETE CASCADE,
PRIMARY KEY (user_id, image_type_id)
);
CREATE INDEX ix_fav_types_type ON user_favorite_types(image_type_id);

CREATE TABLE user_favorite_styles (
user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
style_id BIGINT NOT NULL REFERENCES styles(id) ON DELETE CASCADE,
PRIMARY KEY (user_id, style_id)
);
CREATE INDEX ix_fav_styles_style ON user_favorite_styles(style_id);

-- =========================
-- ASSETS (результат генерации — файл + привязка к типу/стилю)
-- =========================
-- Минимум полей: файл + кто + к чему.
-- Детали (промпт, provider, model) — через JOIN на generation_requests → batch.
-- Сортировка галереи: ORDER BY id DESC (BIGSERIAL = порядок вставки).
-- ON DELETE SET NULL для owner — картинки остаются при удалении пользователя.
CREATE TABLE assets (
id BIGSERIAL PRIMARY KEY,
image_type_id BIGINT NOT NULL REFERENCES image_types(id),
style_id BIGINT NOT NULL REFERENCES styles(id),
file_uri TEXT NOT NULL,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_assets_type ON assets(image_type_id);
CREATE INDEX ix_assets_style ON assets(style_id);
CREATE INDEX ix_assets_type_style ON assets(image_type_id, style_id, id DESC);

-- =========================
-- GENERATION BATCHES (один запуск генерации от пользователя)
-- =========================
-- Хранит ВСЕ параметры запуска: промпт, params, provider, mode.
-- Статуса НЕТ — считается по COUNT реквестов (DONE/FAILED/SKIPPED).
-- ON DELETE SET NULL для owner — история генераций остаётся.
CREATE TABLE generation_batches (
id BIGSERIAL PRIMARY KEY,
owner_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
user_prompt TEXT NOT NULL,
generation_params JSONB NOT NULL DEFAULT '{}',
dedupe_mode TEXT NOT NULL DEFAULT 'SKIP',
provider TEXT NOT NULL,
model TEXT,
routing_mode TEXT NOT NULL,
CONSTRAINT ck_batches_dedupe CHECK (dedupe_mode IN ('SKIP', 'OVERWRITE')),
CONSTRAINT ck_batches_routing CHECK (routing_mode IN ('DIRECT', 'PROXY'))
);
CREATE INDEX ix_batches_owner ON generation_batches(owner_user_id);

-- =========================
-- GENERATION REQUESTS (одна пара тип×стиль внутри batch)
-- =========================
-- Минимум: какая пара, хеш для дедупа, статус, результат.
-- Промпт пересобирается из batch.user_prompt + type.type_prompt + style.style_prompt.
-- Единственная таблица с created_at.
-- Статусы: PENDING → RUNNING → DONE/FAILED/SKIPPED
CREATE TABLE generation_requests (
id BIGSERIAL PRIMARY KEY,
batch_id BIGINT NOT NULL REFERENCES generation_batches(id) ON DELETE CASCADE,
image_type_id BIGINT NOT NULL REFERENCES image_types(id),
style_id BIGINT NOT NULL REFERENCES styles(id),
final_prompt_hash TEXT NOT NULL,
status TEXT NOT NULL DEFAULT 'PENDING',
created_asset_id BIGINT REFERENCES assets(id),
error_message TEXT,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT ck_requests_status CHECK (status IN ('PENDING','RUNNING','DONE','FAILED','SKIPPED'))
);
CREATE INDEX ix_requests_batch ON generation_requests(batch_id);
CREATE INDEX ix_requests_type ON generation_requests(image_type_id);
CREATE INDEX ix_requests_style ON generation_requests(style_id);
CREATE INDEX ix_requests_asset ON generation_requests(created_asset_id);
CREATE INDEX ix_requests_hash ON generation_requests(final_prompt_hash);

-- =========================
-- СВЯЗИ (справка)
-- =========================
-- users
-- ├─→ image_types.created_by_user_id (SET NULL)
-- ├─→ styles.created_by_user_id (SET NULL)
-- ├─→ assets.owner_user_id (SET NULL)
-- ├─→ generation_batches.owner_user_id (SET NULL)
-- ├─→ user_favorite_types (CASCADE)
-- └─→ user_favorite_styles (CASCADE)
--
-- image_types
-- ├─→ assets.image_type_id (переназначение на id=1 через код)
-- ├─→ generation_requests.image_type_id
-- └─→ user_favorite_types (CASCADE)
--
-- styles — аналогично image_types
--
-- generation_batches
-- └─→ generation_requests.batch_id (CASCADE)
--
-- generation_requests
-- └─→ assets.id через created_asset_id
