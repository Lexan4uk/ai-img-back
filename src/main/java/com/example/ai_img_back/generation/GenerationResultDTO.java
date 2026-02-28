package com.example.ai_img_back.generation;

import java.util.UUID;

import com.example.ai_img_back.generation.enums.RequestStatus;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа генерации — один результат для пары (тип × стиль).
 *
 * Клиент получает массив таких объектов.
 */
@Data
@NoArgsConstructor
public class GenerationResultDTO {

    private UUID requestId;
    private UUID imageTypeId;
    private UUID styleId;
    private RequestStatus status;
    private UUID createdAssetId;
    private String fileUri;
    private String errorMessage;

    public GenerationResultDTO(GenerationRequest req) {
        this.requestId = req.getId();
        this.imageTypeId = req.getImageTypeId();
        this.styleId = req.getStyleId();
        this.status = req.getStatus();
        this.createdAssetId = req.getCreatedAssetId();
        this.errorMessage = req.getErrorMessage();
        // fileUri заполним позже через asset, пока null
    }
}
