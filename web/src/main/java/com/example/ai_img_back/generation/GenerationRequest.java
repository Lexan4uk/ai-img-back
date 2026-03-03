package com.example.ai_img_back.generation;

import java.time.OffsetDateTime;

import com.example.ai_img_back.clientutils.enums.RequestStatus;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class GenerationRequest {

    private Long id;
    private Long batchId;
    private Long imageTypeId;
    private Long styleId;

    private String finalPromptHash;
    private RequestStatus status;

    private Long createdAssetId;
    private String errorMessage;

    private OffsetDateTime createdAt;
}
