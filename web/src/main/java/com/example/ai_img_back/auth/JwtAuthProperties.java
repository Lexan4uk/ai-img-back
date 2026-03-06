package com.example.ai_img_back.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtAuthProperties {

    private String signingKey;
}
