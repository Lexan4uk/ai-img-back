package com.example.ai_img_back.provider;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * OpenAI GPT Image провайдер (платный, основной).
 *
 * Модели: gpt-image-1, gpt-image-1.5, gpt-image-1-mini
 * Допустимые размеры: 1024x1024, 1024x1536, 1536x1024, auto
 *
 * Маппинг aspect ratio → size:
 *   "1:1"   → "1024x1024"
 *   "1:1.5" → "1024x1536"  (портретная)
 *   "1.5:1" → "1536x1024"  (альбомная)
 *   другое  → "auto"
 */
@Component
public class OpenAiProvider implements ImageAiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    /**
     * Маппинг aspect ratio → OpenAI size.
     */
    private static final Map<String, String> RATIO_TO_SIZE = Map.of(
            "1:1",   "1024x1024",
            "1:1.5", "1024x1536",
            "1.5:1", "1536x1024"
    );

    private final AiProperties props;
    private RestTemplate restTemplate;

    public OpenAiProvider(AiProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        var builder = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(props.getOpenai().getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(props.getOpenai().getTimeoutSeconds()));

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
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return props.getOpenai().getApiKey() != null
                && !props.getOpenai().getApiKey().isBlank();
    }

    @Override
    public byte[] generate(String prompt, String aspectRatio) throws AiProviderException {
        if (!isAvailable()) {
            throw new AiProviderException("OpenAI API key не настроен", 0, false);
        }

        int maxRetries = props.getOpenai().getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return doGenerate(prompt, aspectRatio);
            } catch (AiProviderException e) {
                if (!e.isRetryable() || attempt == maxRetries) {
                    throw e;
                }
                long delay = (long) Math.pow(2, attempt) * 1000L;
                log.warn("OpenAI: retry {}/{} через {} мс — {}", attempt, maxRetries, delay, e.getMessage());
                sleep(delay);
            }
        }

        throw new AiProviderException("OpenAI: все попытки исчерпаны", 0, false);
    }

    @SuppressWarnings("unchecked")
    private byte[] doGenerate(String prompt, String aspectRatio) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getOpenai().getApiKey());

        String size = mapAspectRatioToSize(aspectRatio);

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getOpenai().getModel());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        body.put("output_format", "png");
        body.put("quality", "high");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    props.getOpenai().getUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map responseBody = response.getBody();
            if (responseBody == null) {
                throw new AiProviderException("OpenAI: пустой ответ", 0, true);
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new AiProviderException("OpenAI: ответ без данных", 0, true);
            }

            String b64 = (String) data.getFirst().get("b64_json");
            if (b64 == null || b64.isBlank()) {
                throw new AiProviderException("OpenAI: пустой base64", 0, true);
            }

            return Base64.getDecoder().decode(b64);

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status >= 500 || status == 429;
            throw new AiProviderException(
                    "OpenAI HTTP " + status + ": " + e.getResponseBodyAsString(),
                    status, retryable
            );
        } catch (ResourceAccessException e) {
            throw new AiProviderException("OpenAI: ошибка подключения — " + e.getMessage(), e);
        }
    }

    /**
     * Маппинг aspect ratio → OpenAI size.
     * "1:1" → "1024x1024", "1:1.5" → "1024x1536", "1.5:1" → "1536x1024".
     * Если ratio не из списка или null → "auto".
     */
    private String mapAspectRatioToSize(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return "auto";
        }
        return RATIO_TO_SIZE.getOrDefault(aspectRatio.trim(), "auto");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
