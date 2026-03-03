package com.example.ai_img_back.clientutils.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImageTypeDTO {
    private Long id;
    private Long createdByUserId;
    private String name;
    private String typePrompt;
}
