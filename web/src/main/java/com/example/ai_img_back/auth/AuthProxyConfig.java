package com.example.ai_img_back.auth;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.OkHttpClient;

@Configuration
public class AuthProxyConfig {

    @Bean("authHttpClient")
    public OkHttpClient authHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
