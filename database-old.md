-- Enable UUID generation CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ========================= -- Well-known UUIDs (used as constants in application code) -- ========================= -- UNDEFINED_TYPE_ID = '00000000-0000-0000-0000-000000000001' -- UNDEFINED_STYLE_ID = '00000000-0000-0000-0000-000000000002'

-- ========================= -- USERS -- ========================= -- Таблица users управляется другим модулем. -- Здесь описана только структура для FK-ссылок. -- НЕ МОДИФИЦИРОВАТЬ эту таблицу в нашем модуле. CREATE TABLE IF NOT EXISTS users ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text UNIQUE, display_name text, created_at timestamptz NOT NULL DEFAULT now() );

-- ========================= -- IMAGE TYPES (shared, immutable — only create/delete) -- ========================= -- created_by_user_id — кто создал. Удалять может только создатель. -- ON DELETE SET NULL — если пользователь удалён, тип остаётся (автор = NULL). CREATE TABLE IF NOT EXISTS image_types ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), created_by_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL, name text NOT NULL, type_prompt text NULL, created_at timestamptz NOT NULL DEFAULT now() );

-- Uniqueness: global case-insensitive name CREATE UNIQUE INDEX IF NOT EXISTS ux_image_types_name ON image_types (lower(name));

-- Seed: "Неопределённый" type (cannot be deleted, used as fallback) INSERT INTO image_types (id, created_by_user_id, name, type_prompt) VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'Неопределённый', NULL);

-- ========================= -- STYLES (shared, immutable — only create/delete) -- ========================= -- created_by_user_id — кто создал. Удалять может только создатель. -- ON DELETE SET NULL — если пользователь удалён, стиль остаётся (автор = NULL). CREATE TABLE IF NOT EXISTS styles ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), created_by_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL, name text NOT NULL, style_prompt text NULL, created_at timestamptz NOT NULL DEFAULT now() );

-- Uniqueness: global case-insensitive name CREATE UNIQUE INDEX IF NOT EXISTS ux_styles_name ON styles (lower(name));

-- Seed: "Неопределённый" style (cannot be deleted, used as fallback) INSERT INTO styles (id, created_by_user_id, name, style_prompt) VALUES ('00000000-0000-0000-0000-000000000002', NULL, 'Неопределённый', NULL);

-- ========================= -- FAVORITES (per-user bookmarks for types and styles) -- ========================= -- Только user_id из таблицы users. Таблицу users не трогаем. -- CASCADE с обеих сторон: -- пользователь удалён → его избранное удаляется -- тип/стиль удалён → записи избранного удаляются

CREATE TABLE IF NOT EXISTS user_favorite_types ( user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, image_type_id uuid NOT NULL REFERENCES image_types(id) ON DELETE CASCADE, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (user_id, image_type_id) );

CREATE TABLE IF NOT EXISTS user_favorite_styles ( user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, style_id uuid NOT NULL REFERENCES styles(id) ON DELETE CASCADE, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (user_id, style_id) );

-- ========================= -- PROMPTS (one-time, created automatically during generation) -- ========================= -- Промпт — одноразовый снимок того, что пользователь ввёл при генерации. -- Не переиспользуется, не редактируется. -- generation_params — JSONB for scalability. -- Currently: {"width": 1024, "height": 1024} -- Future: any new params added without schema changes. -- ON DELETE SET NULL — если пользователь удалён, промпт остаётся (нужен assets). CREATE TABLE IF NOT EXISTS prompts ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL, text text NOT NULL, generation_params jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now() );

CREATE INDEX IF NOT EXISTS ix_prompts_owner_created ON prompts (owner_user_id, created_at DESC);

-- ========================= -- ASSETS (generated images + snapshots) -- ========================= -- ON DELETE SET NULL для owner_user_id — картинки остаются при удалении пользователя. CREATE TABLE IF NOT EXISTS assets ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL,

image_type_id uuid NOT NULL REFERENCES image_types(id), style_id uuid NOT NULL REFERENCES styles(id), prompt_id uuid NOT NULL REFERENCES prompts(id),

user_prompt_snapshot text NOT NULL, type_prompt_snapshot text NOT NULL DEFAULT '', style_prompt_snapshot text NOT NULL DEFAULT '', final_prompt_snapshot text NOT NULL, final_prompt_hash text NOT NULL,

file_uri text NOT NULL, provider text NULL, model text NULL, meta jsonb NULL,

created_at timestamptz NOT NULL DEFAULT now() );

-- Dedupe lookup per user (NOT unique — OVERWRITE creates new asset with same hash) CREATE INDEX IF NOT EXISTS ix_assets_owner_final_hash ON assets (owner_user_id, final_prompt_hash);

-- Gallery: listing by (type, style) across ALL users CREATE INDEX IF NOT EXISTS ix_assets_type_style_created ON assets (image_type_id, style_id, created_at DESC);

-- Lookup by prompt CREATE INDEX IF NOT EXISTS ix_assets_prompt ON assets (prompt_id);

-- ========================= -- GENERATION BATCHES -- ========================= -- ON DELETE SET NULL — история генераций остаётся при удалении пользователя. CREATE TABLE IF NOT EXISTS generation_batches ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL,

provider text NOT NULL, model text NULL, routing_mode text NOT NULL, -- DIRECT | PROXY

status text NOT NULL, -- NEW | RUNNING | DONE | FAILED

created_at timestamptz NOT NULL DEFAULT now(), started_at timestamptz NULL, finished_at timestamptz NULL,

CONSTRAINT ck_generation_batches_routing CHECK (routing_mode IN ('DIRECT','PROXY')), CONSTRAINT ck_generation_batches_status CHECK (status IN ('NEW','RUNNING','DONE','FAILED')) );

CREATE INDEX IF NOT EXISTS ix_generation_batches_owner_created ON generation_batches (owner_user_id, created_at DESC);

-- ========================= -- GENERATION REQUESTS -- ========================= -- ON DELETE SET NULL для owner_user_id — история остаётся. CREATE TABLE IF NOT EXISTS generation_requests ( id uuid PRIMARY KEY DEFAULT gen_random_uuid(), batch_id uuid NOT NULL REFERENCES generation_batches(id) ON DELETE CASCADE, owner_user_id uuid NULL REFERENCES users(id) ON DELETE SET NULL,

image_type_id uuid NOT NULL REFERENCES image_types(id), style_id uuid NOT NULL REFERENCES styles(id), prompt_id uuid NOT NULL REFERENCES prompts(id),

user_prompt_snapshot text NOT NULL, final_prompt_snapshot text NOT NULL, final_prompt_hash text NOT NULL,

dedupe_result text NOT NULL, -- NEW | DUPLICATE dedupe_mode text NOT NULL, -- SKIP | OVERWRITE

status text NOT NULL, -- PENDING | RUNNING | SKIPPED | DONE | FAILED

created_asset_id uuid NULL REFERENCES assets(id),

error_message text NULL,

created_at timestamptz NOT NULL DEFAULT now(), started_at timestamptz NULL, finished_at timestamptz NULL,

CONSTRAINT ck_generation_requests_dedupe_result CHECK (dedupe_result IN ('NEW','DUPLICATE')), CONSTRAINT ck_generation_requests_dedupe_mode CHECK (dedupe_mode IN ('SKIP','OVERWRITE')), CONSTRAINT ck_generation_requests_status CHECK (status IN ('PENDING','RUNNING','SKIPPED','DONE','FAILED')) );

CREATE INDEX IF NOT EXISTS ix_generation_requests_batch_status ON generation_requests (batch_id, status);

CREATE INDEX IF NOT EXISTS ix_generation_requests_owner_created ON generation_requests (owner_user_id, created_at DESC);
