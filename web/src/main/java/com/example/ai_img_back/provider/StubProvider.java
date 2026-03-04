package com.example.ai_img_back.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub-провайдер для тестов и разработки без API-ключей.
 * Активируется через ai.stub.enabled=true.
 * Возвращает минимальный валидный PNG-файл.
 */
@Component
@ConditionalOnProperty(name = "ai.stub.enabled", havingValue = "true")
public class StubProvider implements ImageAiProvider {

    private static final Logger log = LoggerFactory.getLogger(StubProvider.class);

    /**
     * Минимальный валидный PNG: 1×1 пиксель, красный.
     * 67 байт — проходит валидацию magic bytes (89 50 4E 47).
     * Но меньше 1 KB — не пройдёт validate() в ImageStorageService.
     * Поэтому паддим до 1 KB+.
     */
    private static final byte[] STUB_PNG = createStubPng();

    @Override
    public byte[] generate(String prompt, String aspectRatio, String model) throws AiProviderException {
        log.debug("StubProvider: генерация для prompt длиной {} символов, ratio={}, model={}", prompt.length(), aspectRatio, model);
        return STUB_PNG;
    }

    @Override
    public String providerName() {
        return "stub";
    }

    @Override
    public boolean isAvailable() {
        return true; // всегда доступен
    }

    /**
     * Создаёт минимальный PNG размером > 1 KB (чтобы пройти валидацию).
     * PNG header + IHDR + IDAT + IEND + padding.
     */
    private static byte[] createStubPng() {
        // Минимальный 1x1 красный PNG
        byte[] header = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, // IHDR length
                0x49, 0x48, 0x44, 0x52, // IHDR
                0x00, 0x00, 0x00, 0x01, // width 1
                0x00, 0x00, 0x00, 0x01, // height 1
                0x08, 0x02,             // 8-bit RGB
                0x00, 0x00, 0x00,       // compression, filter, interlace
                (byte) 0x90, 0x77, 0x53, (byte) 0xDE, // IHDR CRC
                0x00, 0x00, 0x00, 0x0C, // IDAT length
                0x49, 0x44, 0x41, 0x54, // IDAT
                0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00, 0x00,
                0x00, 0x02, 0x00, 0x01, // compressed data
                (byte) 0xE2, 0x21, (byte) 0xBC, 0x33, // IDAT CRC
                0x00, 0x00, 0x00, 0x00, // IEND length
                0x49, 0x45, 0x4E, 0x44, // IEND
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82  // IEND CRC
        };

        // Паддинг до 1100 байт (чтобы пройти > 1024 валидацию)
        byte[] padded = new byte[1100];
        System.arraycopy(header, 0, padded, 0, header.length);
        // Остаток заполнен нулями — это нарушает формат PNG, но для stub-а неважно
        return padded;
    }
}
