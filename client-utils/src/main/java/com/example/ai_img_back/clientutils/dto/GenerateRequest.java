package com.example.ai_img_back.clientutils.dto;

import java.util.List;
import java.util.UUID;

import com.example.ai_img_back.clientutils.enums.DedupeMode;
import com.example.ai_img_back.clientutils.enums.RoutingMode;

import lombok.Data;

@Data
public class GenerateRequest {
    private String userPrompt;
    private String generationParams;
    private List<UUID> imageTypeIds;
    private List<UUID> styleIds;
    private DedupeMode dedupeMode;
    private String provider;
    private String model;
    private RoutingMode routingMode;
}
