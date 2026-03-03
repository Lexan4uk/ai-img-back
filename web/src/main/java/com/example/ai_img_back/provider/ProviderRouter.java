package com.example.ai_img_back.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Маршрутизатор AI-провайдеров.
 * Выбирает провайдер по имени или автоматически (fallback).
 */
@Service
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<String, ImageAiProvider> providers;
    private final AiProperties props;

    public ProviderRouter(List<ImageAiProvider> providerList, AiProperties props) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(ImageAiProvider::providerName, Function.identity()));
        this.props = props;

        log.info("Зарегистрированы AI-провайдеры: {}", providers.keySet());
        providers.forEach((name, p) ->
                log.info("  {} — {}", name, p.isAvailable() ? "доступен" : "НЕ настроен (нет API-ключа)"));
    }

    /**
     * Выбрать провайдер.
     *
     * @param providerName имя провайдера из запроса (может быть null)
     * @return подходящий провайдер
     * @throws AiProviderException если ни один провайдер не доступен
     */
    public ImageAiProvider resolve(String providerName) {
        // Если пользователь указал конкретного провайдера
        if (providerName != null && !providerName.isBlank() && !"auto".equalsIgnoreCase(providerName)) {
            ImageAiProvider provider = providers.get(providerName.toLowerCase());
            if (provider == null) {
                throw new AiProviderException(
                        "Неизвестный провайдер: " + providerName
                                + ". Доступные: " + providers.keySet(),
                        0, false
                );
            }
            if (!provider.isAvailable()) {
                throw new AiProviderException(
                        "Провайдер " + providerName + " не настроен (нет API-ключа)",
                        0, false
                );
            }
            return provider;
        }

        // Автоматический выбор
        String defaultName = props.getDefaultProvider();
        if (defaultName != null && !"auto".equalsIgnoreCase(defaultName)) {
            ImageAiProvider defaultProvider = providers.get(defaultName.toLowerCase());
            if (defaultProvider != null && defaultProvider.isAvailable()) {
                return defaultProvider;
            }
        }

        // Fallback: любой доступный
        return providers.values().stream()
                .filter(ImageAiProvider::isAvailable)
                .findFirst()
                .orElseThrow(() -> new AiProviderException(
                        "Нет доступных AI-провайдеров. Настройте OPENAI_API_KEY или STABILITY_API_KEY",
                        0, false
                ));
    }
}
