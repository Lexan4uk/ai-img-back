package com.example.ai_img_back.prompt;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Промпт — одноразовый снимок, создаётся автоматически при генерации.
 *
 * Хранит:
 * - text: то что пользователь написал
 * - generationParams: JSON-строка с параметрами (width, height, ...)
 *
 * Не переиспользуется, не редактируется. Каждый запуск генерации
 * создаёт свой промпт — даже если текст тот же.
 *
 * generationParams хранится как String (не Map, не JsonNode),
 * потому что на уровне entity мы просто передаём JSON туда-сюда.
 * Парсинг будет в сервисе, если нужен.
 */
@Getter
@Setter
@Accessors(chain = true)
public class Prompt {

    private UUID id;
    private UUID ownerUserId;
    private String text;

    /**
     * JSON-строка: {"width": 1024, "height": 1024}
     * Хранится как String — JDBC отдаёт JSONB как String.
     */
    private String generationParams;

    private OffsetDateTime createdAt;
}
