package com.example.ai_img_back.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.server")
public class AuthServerProperties {

    private String url = "http://localhost:8090";
}
