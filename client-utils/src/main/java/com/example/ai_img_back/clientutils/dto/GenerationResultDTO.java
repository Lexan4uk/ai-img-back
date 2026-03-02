package com.example.ai_img_back.clientutils.dto;

import java.util.UUID;

import com.example.ai_img_back.clientutils.enums.RequestStatus;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerationResultDTO {
    private UUID requestId;
    private UUID imageTypeId;
    private UUID styleId;
    private RequestStatus status;
    private UUID createdAssetId;
    private String fileUri;
    private String errorMessage;
}
