package com.example.ai_img_back.imagetype;

import lombok.Data;

/**
 * Тело запроса на создание типа.
 *
 * JSON: {"name": "Фотография", "typePrompt": "a photograph of"}
 *
 * typePrompt — опциональный: если не указан, при генерации
 * к промпту ничего не добавится от типа.
 */
@Data
public class ImageTypeRequest {
    private String name;
    private String typePrompt;
}
