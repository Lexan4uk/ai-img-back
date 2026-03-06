package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    private String signingKey;
    private int accessTokenTtlMinutes = 60;
    private int refreshTokenTtlDays = 30;
}
