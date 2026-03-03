package com.example.ai_img_back.generation;

import com.example.ai_img_back.clientutils.enums.DedupeMode;
import com.example.ai_img_back.clientutils.enums.RoutingMode;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class GenerationBatch {

    private Long id;
    private Long ownerUserId;

    private String userPrompt;
    private String generationParams;
    private DedupeMode dedupeMode;

    private String provider;
    private String model;
    private RoutingMode routingMode;
}
