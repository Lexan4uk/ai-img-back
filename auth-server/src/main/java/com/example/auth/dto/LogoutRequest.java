package com.example.auth.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LogoutRequest {

    private String accessToken;
    private String refreshToken;
}
