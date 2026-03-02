package com.example.ai_img_back.clientutils.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImageTypeDTO {
    private UUID id;
    private UUID createdByUserId;
    private String name;
    private String typePrompt;
    private OffsetDateTime createdAt;
}
