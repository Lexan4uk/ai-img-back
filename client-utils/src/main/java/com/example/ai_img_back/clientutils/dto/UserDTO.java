package com.example.ai_img_back.clientutils.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDTO {
    private UUID id;
    private String email;
    private String displayName;
    private OffsetDateTime createdAt;
}
