package com.example.ai_img_back.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Сервис сохранения сгенерированных изображений на диск.
 * Путь к папке настраивается через ai.images.output-dir.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    /** Минимальный размер валидного изображения (1 KB) */
    private static final int MIN_IMAGE_SIZE = 1024;

    /** Magic bytes PNG: 89 50 4E 47 */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    /** Magic bytes JPEG: FF D8 FF */
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** Magic bytes WebP: RIFF....WEBP (байты 0-3 = "RIFF", байты 8-11 = "WEBP") */
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    private final AiProperties props;
    private Path outputDir;

    public ImageStorageService(AiProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() throws IOException {
        this.outputDir = Paths.get(props.getImages().getOutputDir()).toAbsolutePath();
        Files.createDirectories(outputDir);
        log.info("Папка для изображений: {}", outputDir);
    }

    /**
     * Сохранить изображение на диск.
     *
     * @param imageBytes байты изображения
     * @return относительный путь к файлу (для записи в БД)
     * @throws IOException если не удалось записать файл
     */
    public String save(byte[] imageBytes) throws IOException {
        // Валидация
        validate(imageBytes);

        // Определяем расширение по magic bytes
        String ext = detectExtension(imageBytes);

        // Генерируем имя файла
        String fileName = UUID.randomUUID() + ext;
        Path filePath = outputDir.resolve(fileName);

        // Записываем
        Files.write(filePath, imageBytes);
        log.debug("Файл сохранён: {} ({} KB)", filePath, imageBytes.length / 1024);

        // Возвращаем относительный путь (output-dir/filename)
        return props.getImages().getOutputDir() + "/" + fileName;
    }

    private void validate(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < MIN_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Провайдер вернул повреждённый файл (размер: "
                            + (imageBytes == null ? 0 : imageBytes.length) + " байт)"
            );
        }

        if (!startsWith(imageBytes, PNG_MAGIC) && !startsWith(imageBytes, JPEG_MAGIC) && !isWebP(imageBytes)) {
            throw new IllegalArgumentException(
                    "Провайдер вернул файл неизвестного формата (не PNG, не JPEG и не WebP)"
            );
        }
    }

    private String detectExtension(byte[] imageBytes) {
        if (startsWith(imageBytes, PNG_MAGIC)) return ".png";
        if (startsWith(imageBytes, JPEG_MAGIC)) return ".jpg";
        if (isWebP(imageBytes)) return ".webp";
        return ".png"; // fallback
    }

    /** WebP: starts with "RIFF", bytes 8-11 = "WEBP" */
    private boolean isWebP(byte[] data) {
        if (data.length < 12) return false;
        if (!startsWith(data, RIFF_MAGIC)) return false;
        return data[8] == WEBP_MAGIC[0] && data[9] == WEBP_MAGIC[1]
                && data[10] == WEBP_MAGIC[2] && data[11] == WEBP_MAGIC[3];
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
