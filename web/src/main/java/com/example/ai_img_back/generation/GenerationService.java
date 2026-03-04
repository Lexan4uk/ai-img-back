package com.example.ai_img_back.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.asset.Asset;
import com.example.ai_img_back.asset.AssetRepository;
import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.enums.DedupeMode;
import com.example.ai_img_back.clientutils.enums.RequestStatus;
import com.example.ai_img_back.clientutils.enums.RoutingMode;
import com.example.ai_img_back.imagetype.ImageType;
import com.example.ai_img_back.imagetype.ImageTypeService;
import com.example.ai_img_back.provider.AiProperties;
import com.example.ai_img_back.provider.AiProviderException;
import com.example.ai_img_back.provider.ImageAiProvider;
import com.example.ai_img_back.provider.ImageStorageService;
import com.example.ai_img_back.provider.ProviderRouter;
import com.example.ai_img_back.style.Style;
import com.example.ai_img_back.style.StyleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ImageTypeService imageTypeService;
    private final StyleService styleService;
    private final AssetRepository assetRepository;
    private final GenerationBatchRepository batchRepository;
    private final GenerationRequestRepository requestRepository;
    private final ProviderRouter providerRouter;
    private final ImageStorageService imageStorageService;
    private final AiProperties aiProperties;

    /** Aspect ratio по умолчанию: 1:1 (квадратное изображение) */
    private static final String DEFAULT_ASPECT_RATIO = "1:1";

    public List<GenerationRequest> generate(Long currentUserId, GenerateRequest request) {

        // === Шаг 0: Валидация ===

        if (request.getUserPrompt() == null || request.getUserPrompt().isBlank()) {
            throw new IllegalArgumentException("Промпт не может быть пустым");
        }
        if (request.getImageTypeIds() == null || request.getImageTypeIds().isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один тип");
        }
        if (request.getStyleIds() == null || request.getStyleIds().isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один стиль");
        }

        if (request.getImageTypeIds().contains(ImageTypeService.UNDEFINED_TYPE_ID)) {
            throw new IllegalArgumentException(
                    "Нельзя генерировать с неопределённым типом изображения");
        }
        if (request.getStyleIds().contains(StyleService.UNDEFINED_STYLE_ID)) {
            throw new IllegalArgumentException(
                    "Нельзя генерировать с неопределённым стилем");
        }

        List<ImageType> types = request.getImageTypeIds().stream()
                .map(imageTypeService::getById)
                .toList();

        List<Style> styles = request.getStyleIds().stream()
                .map(styleService::getById)
                .toList();

        // === Шаг 1: Создать batch (хранит prompt и params) ===

        DedupeMode dedupeMode = request.getDedupeMode() != null
                ? request.getDedupeMode() : DedupeMode.SKIP;

        String generationParams = request.getGenerationParams() != null
                ? request.getGenerationParams() : "{}";

        String providerName = request.getProvider() != null
                ? request.getProvider() : aiProperties.getDefaultProvider();

        GenerationBatch batch = batchRepository.create(
                currentUserId,
                request.getUserPrompt().trim(),
                generationParams,
                dedupeMode,
                providerName,
                request.getModel(),
                request.getRoutingMode() != null ? request.getRoutingMode() : RoutingMode.DIRECT
        );

        log.info("Batch #{}: начата генерация, {} типов × {} стилей = {} запросов, provider={}",
                batch.getId(), types.size(), styles.size(), types.size() * styles.size(), providerName);

        // === Шаг 2: Сформировать request-ы (декартово произведение) ===

        List<GenerationRequest> requests = new ArrayList<>();

        for (ImageType type : types) {
            for (Style style : styles) {
                String typePrompt = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePrompt = style.getStylePrompt() != null ? style.getStylePrompt() : "";
                String userPrompt = batch.getUserPrompt();

                String finalPrompt = composeFinalPrompt(typePrompt, stylePrompt, userPrompt);
                String finalPromptHash = computeHash(finalPrompt, generationParams);

                GenerationRequest genRequest = requestRepository.create(
                        batch.getId(), type.getId(), style.getId(), finalPromptHash
                );

                requests.add(genRequest);
            }
        }

        // === Шаг 3: Выбрать провайдер ===

        ImageAiProvider provider;
        try {
            provider = providerRouter.resolve(providerName);
        } catch (AiProviderException e) {
            // Если провайдер недоступен — помечаем все как FAILED
            for (GenerationRequest genReq : requests) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
            }
            return requests;
        }

        // Парсим aspect ratio из generationParams (если указан)
        String aspectRatio = parseStringParam(generationParams, "aspectRatio", DEFAULT_ASPECT_RATIO);

        // Модель из запроса клиента (null → провайдер использует свой дефолт)
        String requestedModel = request.getModel();

        // === Шаг 4: Последовательное выполнение ===

        boolean authFailed = false;

        for (GenerationRequest genReq : requests) {

            // При ошибке авторизации — остальные тоже не пройдут
            if (authFailed) {
                requestRepository.markFailed(genReq.getId(),
                        "Генерация остановлена: ошибка авторизации провайдера");
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage("Генерация остановлена: ошибка авторизации провайдера");
                continue;
            }

            // Проверка дедупликации
            boolean isDuplicate = assetRepository.existsByHash(genReq.getFinalPromptHash());

            if (isDuplicate && dedupeMode == DedupeMode.SKIP) {
                requestRepository.markSkipped(genReq.getId());
                genReq.setStatus(RequestStatus.SKIPPED);
                log.info("Request #{}: SKIPPED (дубликат, hash: {}...)",
                        genReq.getId(), genReq.getFinalPromptHash().substring(0, 8));
                continue;
            }

            requestRepository.markRunning(genReq.getId());

            try {
                // Собираем finalPrompt для отправки в AI
                ImageType type = types.stream()
                        .filter(t -> t.getId().equals(genReq.getImageTypeId()))
                        .findFirst().orElseThrow();
                Style style = styles.stream()
                        .filter(s -> s.getId().equals(genReq.getStyleId()))
                        .findFirst().orElseThrow();

                String typePrompt = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePrompt = style.getStylePrompt() != null ? style.getStylePrompt() : "";
                String finalPrompt = composeFinalPrompt(typePrompt, stylePrompt, batch.getUserPrompt());

                log.debug("Request #{}: отправлен к {} (hash: {}..., ratio: {})",
                        genReq.getId(), provider.providerName(),
                        genReq.getFinalPromptHash().substring(0, 8), aspectRatio);

                // Вызов AI-провайдера
                byte[] imageBytes = provider.generate(finalPrompt, aspectRatio, requestedModel);

                // Сохранение на диск
                String fileUri = imageStorageService.save(imageBytes);

                // Сохранить asset в БД
                Asset asset = assetRepository.create(new Asset()
                        .setImageTypeId(genReq.getImageTypeId())
                        .setStyleId(genReq.getStyleId())
                        .setFileUri(fileUri)
                );

                requestRepository.markDone(genReq.getId(), asset.getId());
                genReq.setStatus(RequestStatus.DONE);
                genReq.setCreatedAssetId(asset.getId());

                log.info("Request #{}: DONE, asset #{}, файл {}",
                        genReq.getId(), asset.getId(), fileUri);

            } catch (AiProviderException e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
                log.warn("Request #{}: FAILED — {}", genReq.getId(), e.getMessage());

                // Если 401/403/402 — нет смысла продолжать
                if (e.getHttpStatus() == 401 || e.getHttpStatus() == 403 || e.getHttpStatus() == 402) {
                    authFailed = true;
                    log.error("Ошибка авторизации ({}) — остальные запросы batch будут отменены",
                            e.getHttpStatus());
                }

            } catch (IOException e) {
                String msg = "Ошибка сохранения файла: " + e.getMessage();
                requestRepository.markFailed(genReq.getId(), msg);
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(msg);
                log.error("Request #{}: {}", genReq.getId(), msg, e);

            } catch (Exception e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
                log.error("Request #{}: неожиданная ошибка", genReq.getId(), e);
            }
        }

        // Статус batch не хранится — вычисляется по COUNT реквестов

        return requests;
    }

    // ===== Вспомогательные методы =====

    /**
     * Собирает финальный промпт по шаблону из ai.prompt-template.
     * Переменные: {type}, {style}, {prompt}
     */
    private String composeFinalPrompt(String typePrompt, String stylePrompt, String userPrompt) {
        return aiProperties.getPromptTemplate()
                .replace("{type}", typePrompt.isBlank() ? "любой" : typePrompt)
                .replace("{style}", stylePrompt.isBlank() ? "любой" : stylePrompt)
                .replace("{prompt}", userPrompt)
                .trim();
    }

    private String computeHash(String finalPrompt, String generationParams) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = finalPrompt + "|" + (generationParams != null ? generationParams : "{}");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 не доступен", e);
        }
    }

    /**
     * Простой парсер строкового параметра из JSON-строки.
     * Ищет "key": "value" и возвращает value.
     */
    private String parseStringParam(String json, String key, String defaultValue) {
        try {
            String searchKey = "\"" + key + "\"";
            int idx = json.indexOf(searchKey);
            if (idx < 0) return defaultValue;

            int colonIdx = json.indexOf(':', idx + searchKey.length());
            if (colonIdx < 0) return defaultValue;

            // Ищем открывающую кавычку
            int quoteStart = json.indexOf('"', colonIdx + 1);
            if (quoteStart < 0) return defaultValue;

            // Ищем закрывающую кавычку
            int quoteEnd = json.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) return defaultValue;

            String value = json.substring(quoteStart + 1, quoteEnd).trim();
            return value.isEmpty() ? defaultValue : value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
