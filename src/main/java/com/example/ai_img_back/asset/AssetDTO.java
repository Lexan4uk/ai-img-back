package com.example.ai_img_back.asset;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для галереи — то, что видит клиент.
 */
@Data
@NoArgsConstructor
public class AssetDTO {

    private UUID id;
    private UUID ownerUserId;
    private UUID imageTypeId;
    private UUID styleId;
    private UUID promptId;
    private String userPromptSnapshot;
    private String fileUri;
    private String provider;
    private String model;
    private OffsetDateTime createdAt;

    public AssetDTO(Asset entity) {
        this.id = entity.getId();
        this.ownerUserId = entity.getOwnerUserId();
        this.imageTypeId = entity.getImageTypeId();
        this.styleId = entity.getStyleId();
        this.promptId = entity.getPromptId();
        this.userPromptSnapshot = entity.getUserPromptSnapshot();
        this.fileUri = entity.getFileUri();
        this.provider = entity.getProvider();
        this.model = entity.getModel();
        this.createdAt = entity.getCreatedAt();
    }
}
