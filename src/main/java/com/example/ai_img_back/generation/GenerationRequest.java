package com.example.ai_img_back.generation;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.ai_img_back.generation.enums.DedupeMode;
import com.example.ai_img_back.generation.enums.DedupeResult;
import com.example.ai_img_back.generation.enums.RequestStatus;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Request — одна пара (тип + стиль) внутри batch.
 *
 * Хранит снапшоты промптов, результат дедупликации,
 * статус выполнения и ссылку на созданный asset (если DONE).
 */
@Getter
@Setter
@Accessors(chain = true)
public class GenerationRequest {

    private UUID id;
    private UUID batchId;
    private UUID ownerUserId;

    private UUID imageTypeId;
    private UUID styleId;
    private UUID promptId;

    private String userPromptSnapshot;
    private String finalPromptSnapshot;
    private String finalPromptHash;

    private DedupeResult dedupeResult;
    private DedupeMode dedupeMode;
    private RequestStatus status;

    /** Ссылка на созданный asset (заполняется при status=DONE) */
    private UUID createdAssetId;
    /** Текст ошибки (заполняется при status=FAILED) */
    private String errorMessage;

    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
