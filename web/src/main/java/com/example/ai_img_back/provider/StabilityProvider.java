package com.example.ai_img_back.provider;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Stability AI провайдер — Stable Image Core.
 *
 * API: POST multipart/form-data → https://api.stability.ai/v2beta/stable-image/generate/core
 * Accept: image/* → получаем байты напрямую (без base64).
 *
 * Поддерживаемые aspect_ratio: 16:9, 1:1, 21:9, 2:3, 3:2, 4:5, 5:4, 9:16, 9:21
 * Стоимость: 3 кредита за генерацию.
 * Разрешение: 1.5 мегапикселя.
 */
@Component
public class StabilityProvider implements ImageAiProvider {

    private static final Logger log = LoggerFactory.getLogger(StabilityProvider.class);

    /** Допустимые aspect_ratio для Stable Image Core */
    private static final Set<String> VALID_RATIOS = Set.of(
            "16:9", "1:1", "21:9", "2:3", "3:2", "4:5", "5:4", "9:16", "9:21"
    );

    private final AiProperties props;
    private RestTemplate restTemplate;

    public StabilityProvider(AiProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        var builder = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(props.getStability().getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(props.getStability().getTimeoutSeconds()));

        if (props.getProxy().isEnabled() && !props.getProxy().getHost().isBlank()) {
            builder = builder.customizers(rt -> {
                var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setProxy(new Proxy(
                        Proxy.Type.HTTP,
                        new InetSocketAddress(props.getProxy().getHost(), props.getProxy().getPort())
                ));
                rt.setRequestFactory(factory);
            });
        }

        this.restTemplate = builder.build();
    }

    @Override
    public String providerName() {
        return "stability";
    }

    @Override
    public boolean isAvailable() {
        return props.getStability().getApiKey() != null
                && !props.getStability().getApiKey().isBlank();
    }

    @Override
    public byte[] generate(String prompt, String aspectRatio, String model) throws AiProviderException {
        if (!isAvailable()) {
            throw new AiProviderException("Stability API key не настроен", 0, false);
        }

        int maxRetries = props.getStability().getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doGenerate(prompt, aspectRatio);
            } catch (AiProviderException e) {
                if (!e.isRetryable() || attempt == maxRetries) {
                    throw e;
                }
                long delay = (long) Math.pow(2, attempt) * 1000L;
                log.warn("Stability: retry {}/{} через {} мс — {}", attempt, maxRetries, delay, e.getMessage());
                sleep(delay);
            }
        }

        throw new AiProviderException("Stability: все попытки исчерпаны", 0, false);
    }

    private byte[] doGenerate(String prompt, String aspectRatio) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + props.getStability().getApiKey());
        headers.set("Accept", "image/*");

        // multipart/form-data body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("prompt", prompt);
        body.add("output_format", props.getStability().getOutputFormat());

        // Маппинг aspect ratio
        String ratio = mapAspectRatio(aspectRatio);
        body.add("aspect_ratio", ratio);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    props.getStability().getUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class
            );

            byte[] imageBytes = response.getBody();
            if (imageBytes == null || imageBytes.length == 0) {
                throw new AiProviderException("Stability: пустой ответ", 0, true);
            }

            return imageBytes;

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status >= 500 || status == 429;
            throw new AiProviderException(
                    "Stability HTTP " + status + ": " + e.getResponseBodyAsString(),
                    status, retryable
            );
        } catch (ResourceAccessException e) {
            throw new AiProviderException("Stability: ошибка подключения — " + e.getMessage(), e);
        }
    }

    /**
     * Маппинг aspect ratio.
     * Если входной ratio совпадает с допустимым — используем его.
     * Иначе пробуем конвертировать из формата "1:1.5" → ближайший валидный.
     * Fallback → "1:1".
     */
    private String mapAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return "1:1";
        }

        String normalized = aspectRatio.trim();

        // Прямое совпадение
        if (VALID_RATIOS.contains(normalized)) {
            return normalized;
        }

        // Попробуем маппинг из формата "1.5:1" → "3:2" и т.д.
        return convertToNearestValid(normalized);
    }

    /**
     * Конвертирует произвольное соотношение (1:1.5, 1.5:1) в ближайшее допустимое.
     */
    private String convertToNearestValid(String ratio) {
        try {
            String[] parts = ratio.split(":");
            if (parts.length != 2) return "1:1";

            double w = Double.parseDouble(parts[0].trim());
            double h = Double.parseDouble(parts[1].trim());

            if (w <= 0 || h <= 0) return "1:1";

            double target = w / h;
            String best = "1:1";
            double bestDiff = Double.MAX_VALUE;

            for (String valid : VALID_RATIOS) {
                String[] vParts = valid.split(":");
                double vRatio = Double.parseDouble(vParts[0]) / Double.parseDouble(vParts[1]);
                double diff = Math.abs(target - vRatio);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = valid;
                }
            }

            if (!best.equals(ratio)) {
                log.debug("Stability: aspect ratio '{}' → '{}' (ближайший допустимый)", ratio, best);
            }
            return best;
        } catch (NumberFormatException e) {
            return "1:1";
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
