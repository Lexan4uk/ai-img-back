package com.example.ai_img_back.style;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StyleDTO {

    private UUID id;
    private UUID createdByUserId;
    private String name;
    private String stylePrompt;
    private OffsetDateTime createdAt;

    public StyleDTO(Style entity) {
        this.id = entity.getId();
        this.createdByUserId = entity.getCreatedByUserId();
        this.name = entity.getName();
        this.stylePrompt = entity.getStylePrompt();
        this.createdAt = entity.getCreatedAt();
    }
}
