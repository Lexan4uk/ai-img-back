package com.example.ai_img_back.imagetype;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO типа для ответа клиенту.
 *
 * Содержит все поля entity — пока скрывать нечего.
 * В будущем можно добавить вычисляемые поля (например, кол-во assets).
 */
@Data
@NoArgsConstructor
public class ImageTypeDTO {

    private UUID id;
    private UUID createdByUserId;
    private String name;
    private String typePrompt;
    private OffsetDateTime createdAt;

    /** Конвертация Entity → DTO */
    public ImageTypeDTO(ImageType entity) {
        this.id = entity.getId();
        this.createdByUserId = entity.getCreatedByUserId();
        this.name = entity.getName();
        this.typePrompt = entity.getTypePrompt();
        this.createdAt = entity.getCreatedAt();
    }
}
