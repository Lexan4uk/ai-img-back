package com.example.ai_img_back.clientutils.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssetDTO {
    private UUID id;
    private UUID ownerUserId;
    private UUID imageTypeId;
    private UUID styleId;
    private UUID promptId;
    private String finalPromptSnapshot;
    private String fileUri;
    private String provider;
    private String model;
    private OffsetDateTime createdAt;
}
