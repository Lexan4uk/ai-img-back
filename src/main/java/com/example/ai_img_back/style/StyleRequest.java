package com.example.ai_img_back.style;

import lombok.Data;

/**
 * JSON: {"name": "Айвазовский", "stylePrompt": "in the style of Aivazovsky"}
 */
@Data
public class StyleRequest {
    private String name;
    private String stylePrompt;
}
