package com.example.ai_img_back.clientutils.dto;

import java.util.List;

import com.example.ai_img_back.clientutils.enums.RoutingMode;

import lombok.Data;

@Data
public class GenerateRequest {
    private String userPrompt;
    private String generationParams;
    private List<Long> imageTypeIds;
    private List<Long> styleIds;
    /** Пересоздать дубликаты (true) или пропустить их (false, default) */
    private boolean overwriteDuplicates;
    private String provider;
    private String model;
    private RoutingMode routingMode;
}
