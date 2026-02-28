package com.example.ai_img_back.generation;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.ai_img_back.generation.enums.BatchStatus;
import com.example.ai_img_back.generation.enums.RoutingMode;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Batch — один запуск генерации от пользователя.
 *
 * Пользователь выбрал 2 типа + 2 стиля → batch содержит 4 request-а.
 * Batch отслеживает общий прогресс и результат.
 */
@Getter
@Setter
@Accessors(chain = true)
public class GenerationBatch {

    private UUID id;
    private UUID ownerUserId;

    private String provider;
    private String model;
    private RoutingMode routingMode;
    private BatchStatus status;

    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
