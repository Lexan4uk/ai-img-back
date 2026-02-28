package com.example.ai_img_back.generation;

import java.util.List;
import java.util.UUID;

import com.example.ai_img_back.generation.enums.DedupeMode;
import com.example.ai_img_back.generation.enums.RoutingMode;

import lombok.Data;

/**
 * Тело запроса POST /generations.
 *
 * Это то, что пришлёт клиент:
 * {
 *   "userPrompt": "человек кующий лопату",
 *   "generationParams": "{\"width\": 1024, \"height\": 1024}",
 *   "imageTypeIds": ["uuid1", "uuid2"],
 *   "styleIds": ["uuid3", "uuid4"],
 *   "dedupeMode": "SKIP",
 *   "provider": "openai",
 *   "model": "dall-e-3",
 *   "routingMode": "DIRECT"
 * }
 *
 * 2 типа × 2 стиля = 4 запроса к AI.
 *
 */
@Data
public class GenerateRequest {

    private String userPrompt;

    /** JSON-строка: {"width": 1024, "height": 1024} */
    private String generationParams;

    private List<UUID> imageTypeIds;
    private List<UUID> styleIds;

    /** SKIP или OVERWRITE */
    private DedupeMode dedupeMode;

    /** AI-провайдер (openai, stability и т.д.) */
    private String provider;
    private String model;
    private RoutingMode routingMode;
}
