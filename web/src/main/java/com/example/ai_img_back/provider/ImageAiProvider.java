package com.example.ai_img_back.provider;

/**
 * Интерфейс AI-провайдера для генерации изображений.
 * Каждая реализация (OpenAI, Stability AI) имплементирует этот интерфейс.
 */
public interface ImageAiProvider {

    /**
     * Генерирует изображение по промпту.
     *
     * @param prompt      финальный промпт (type + style + user)
     * @param aspectRatio соотношение сторон ("1:1", "1:1.5", "1.5:1" и т.д.),
     *                    null или пустая строка = авто/дефолт провайдера
     * @param model       имя модели из запроса клиента (null = дефолт провайдера)
     * @return байты сгенерированного изображения (PNG/WebP)
     * @throws AiProviderException при ошибке API
     */
    byte[] generate(String prompt, String aspectRatio, String model) throws AiProviderException;

    /** Имя провайдера ("openai", "stability") */
    String providerName();

    /** Доступен ли провайдер (есть API-ключ) */
    boolean isAvailable();
}
