package com.example.ai_img_back.clientutils.dto;

import com.example.ai_img_back.clientutils.enums.RequestStatus;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerationResultDTO {
    private Long requestId;
    private Long imageTypeId;
    private Long styleId;
    private RequestStatus status;
    private Long createdAssetId;
    private String errorMessage;
}
