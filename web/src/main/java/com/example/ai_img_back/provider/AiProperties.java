package com.example.ai_img_back.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Конфигурация AI-провайдеров, прокси и хранения изображений.
 * Читается из application.properties (префикс "ai").
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Getter
@Setter
public class AiProperties {

    private String defaultProvider = "openai";

    /** Шаблон промпта. Переменные: {type}, {style}, {prompt} */
    private String promptTemplate = "Create an image of type: {type}. Style: {style}. Description: {prompt}. Avoid unnecessary text in the image.";

    private final OpenAi openai = new OpenAi();
    private final Stability stability = new Stability();
    private final Proxy proxy = new Proxy();
    private final Images images = new Images();

    @Getter @Setter
    public static class OpenAi {
        private String apiKey = "";
        private String model = "gpt-image-1.5";
        private String url = "https://api.openai.com/v1/images/generations";
        private int timeoutSeconds = 90;
        private int maxRetries = 3;
    }

    @Getter @Setter
    public static class Stability {
        private String apiKey = "";
        private String url = "https://api.stability.ai/v2beta/stable-image/generate/core";
        private int timeoutSeconds = 90;
        private int maxRetries = 2;
        private String outputFormat = "png";
    }

    @Getter @Setter
    public static class Proxy {
        private boolean enabled = false;
        private String host = "";
        private int port = 10000;
        private String username = "";
        private String password = "";
        private String type = "HTTP"; // HTTP or SOCKS5
    }

    @Getter @Setter
    public static class Images {
        private String outputDir = "generated-imgs";
    }
}
