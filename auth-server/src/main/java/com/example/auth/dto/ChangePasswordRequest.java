package com.example.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChangePasswordRequest {

    private Long userId;
    private String currentPassword;
    private String newPassword;
}
