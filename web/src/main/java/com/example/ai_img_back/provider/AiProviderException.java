package com.example.ai_img_back.provider;

/**
 * Исключение при вызове AI-провайдера.
 * Содержит HTTP-код ответа (если есть) для определения retry-стратегии.
 */
public class AiProviderException extends RuntimeException {

    private final int httpStatus;
    private final boolean retryable;

    public AiProviderException(String message, int httpStatus, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.retryable = true; // сетевые ошибки — retryable
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** Можно ли повторить запрос (true для 5xx, таймаутов; false для 400, 401, 402) */
    public boolean isRetryable() {
        return retryable;
    }
}
