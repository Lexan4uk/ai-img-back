package com.example.ai_img_back.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthUser {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String role;
}
