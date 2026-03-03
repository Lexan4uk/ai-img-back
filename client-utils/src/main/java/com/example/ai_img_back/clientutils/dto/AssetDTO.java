package com.example.ai_img_back.clientutils.dto;

import java.time.OffsetDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssetDTO {
    private Long id;
    private Long imageTypeId;
    private Long styleId;
    private String fileUri;
    private OffsetDateTime createdAt;
}
