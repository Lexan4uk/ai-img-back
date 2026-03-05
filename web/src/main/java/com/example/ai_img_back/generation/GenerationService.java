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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.ai_img_back.asset.Asset;
import com.example.ai_img_back.asset.AssetRepository;
import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.GenerationCheckResult;
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

    // ===== Проверка дубликатов (перед генерацией) =====

    /**
     * Проверяет сколько из запрашиваемых пар тип×стиль уже сгенерированы.
     * Клиент вызывает этот метод ДО генерации, чтобы показать пользователю:
     * "Генерация 20 картинок. 8 уже существуют. Пересоздать?"
     */
    public GenerationCheckResult check(GenerateRequest request) {

        validateRequest(request);

        List<ImageType> types = request.getImageTypeIds().stream()
                .map(imageTypeService::getById)
                .toList();

        List<Style> styles = request.getStyleIds().stream()
                .map(styleService::getById)
                .toList();

        String generationParams = request.getGenerationParams() != null
                ? request.getGenerationParams() : "{}";

        int totalCount = types.size() * styles.size();
        int duplicateCount = 0;

        for (ImageType type : types) {
            for (Style style : styles) {
                String typePrompt = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePrompt = style.getStylePrompt() != null ? style.getStylePrompt() : "";

                String finalPrompt = composeFinalPrompt(typePrompt, stylePrompt, request.getUserPrompt().trim());
                String hash = computeHash(finalPrompt, generationParams);

                if (assetRepository.existsByHash(hash)) {
                    duplicateCount++;
                }
            }
        }

        return new GenerationCheckResult(totalCount, duplicateCount, totalCount - duplicateCount);
    }

    // ===== Генерация =====

    public List<GenerationRequest> generate(Long currentUserId, GenerateRequest request) {

        // === Шаг 0: Валидация ===

        validateRequest(request);

        List<ImageType> types = request.getImageTypeIds().stream()
                .map(imageTypeService::getById)
                .toList();

        List<Style> styles = request.getStyleIds().stream()
                .map(styleService::getById)
                .toList();

        // === Шаг 1: Создать batch ===

        String generationParams = request.getGenerationParams() != null
                ? request.getGenerationParams() : "{}";

        String providerName = request.getProvider() != null
                ? request.getProvider() : aiProperties.getDefaultProvider();

        GenerationBatch batch = batchRepository.create(
                currentUserId,
                request.getUserPrompt().trim(),
                generationParams,
                request.isOverwriteDuplicates(),
                providerName,
                request.getModel(),
                request.getRoutingMode() != null ? request.getRoutingMode() : RoutingMode.DIRECT
        );

        // === Шаг 2: Сформировать request-ы (только не-дубликаты, или все если overwrite) ===

        List<GenerationRequest> requests = new ArrayList<>();

        for (ImageType type : types) {
            for (Style style : styles) {
                String typePrompt = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePrompt = style.getStylePrompt() != null ? style.getStylePrompt() : "";
                String userPrompt = batch.getUserPrompt();

                String finalPrompt = composeFinalPrompt(typePrompt, stylePrompt, userPrompt);
                String finalPromptHash = computeHash(finalPrompt, generationParams);

                // Если не overwrite — пропустить дубликаты (НЕ создавать request)
                if (!request.isOverwriteDuplicates()) {
                    boolean isDuplicate = assetRepository.existsByHash(finalPromptHash);
                    if (isDuplicate) {
                        log.info("Batch #{}: пропуск дубликата type={} style={} (hash: {}...)",
                                batch.getId(), type.getId(), style.getId(),
                                finalPromptHash.substring(0, 8));
                        continue;
                    }
                }

                GenerationRequest genRequest = requestRepository.create(
                        batch.getId(), type.getId(), style.getId(), finalPromptHash
                );
                requests.add(genRequest);
            }
        }

        if (requests.isEmpty()) {
            log.info("Batch #{}: все пары — дубликаты, нечего генерировать", batch.getId());
            return requests;
        }

        log.info("Batch #{}: {} запросов к генерации (overwrite={}), provider={}",
                batch.getId(), requests.size(), request.isOverwriteDuplicates(), providerName);

        // === Шаг 3: Выбрать провайдер ===

        ImageAiProvider provider;
        try {
            provider = providerRouter.resolve(providerName);
        } catch (AiProviderException e) {
            for (GenerationRequest genReq : requests) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
            }
            return requests;
        }

        String aspectRatio = parseStringParam(generationParams, "aspectRatio", DEFAULT_ASPECT_RATIO);
        String requestedModel = request.getModel();

        // === Шаг 4: Последовательное выполнение ===

        processRequests(requests, types, styles, batch, provider, aspectRatio, requestedModel);

        return requests;
    }

    // ===== Возобновление после перезагрузки =====

    /**
     * Возобновляет незавершённые запросы из указанного batch.
     * Вызывается при старте сервера для RUNNING и PENDING запросов,
     * которые остались после падения/перезагрузки.
     * Выполняется асинхронно, чтобы не блокировать старт приложения.
     */
    @Async
    public void resumeBatchAsync(Long batchId) {
        GenerationBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            log.warn("Resume: batch #{} не найден, пропускаю", batchId);
            return;
        }

        List<GenerationRequest> allRequests = requestRepository.findByBatchId(batchId);
        List<GenerationRequest> toProcess = allRequests.stream()
                .filter(r -> r.getStatus() == RequestStatus.RUNNING
                          || r.getStatus() == RequestStatus.PENDING)
                .toList();

        if (toProcess.isEmpty()) {
            return;
        }

        log.info("Resume batch #{}: {} запросов к обработке (provider={})",
                batchId, toProcess.size(), batch.getProvider());

        ImageAiProvider provider;
        try {
            provider = providerRouter.resolve(batch.getProvider());
        } catch (AiProviderException e) {
            log.error("Resume batch #{}: провайдер недоступен — {}", batchId, e.getMessage());
            for (GenerationRequest genReq : toProcess) {
                requestRepository.markFailed(genReq.getId(), "Resume failed: " + e.getMessage());
            }
            return;
        }

        String generationParams = batch.getGenerationParams() != null
                ? batch.getGenerationParams() : "{}";
        String aspectRatio = parseStringParam(generationParams, "aspectRatio", DEFAULT_ASPECT_RATIO);
        String requestedModel = batch.getModel();

        boolean authFailed = false;

        for (GenerationRequest genReq : toProcess) {

            if (authFailed) {
                requestRepository.markFailed(genReq.getId(),
                        "Генерация остановлена: ошибка авторизации провайдера");
                continue;
            }

            requestRepository.markRunning(genReq.getId());

            try {
                ImageType type = imageTypeService.getById(genReq.getImageTypeId());
                Style style = styleService.getById(genReq.getStyleId());

                String typePrompt = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePrompt = style.getStylePrompt() != null ? style.getStylePrompt() : "";
                String finalPrompt = composeFinalPrompt(typePrompt, stylePrompt, batch.getUserPrompt());

                log.debug("Resume request #{}: отправлен к {}", genReq.getId(), provider.providerName());

                byte[] imageBytes = provider.generate(finalPrompt, aspectRatio, requestedModel);
                String fileUri = imageStorageService.save(imageBytes);

                Asset asset = assetRepository.create(new Asset()
                        .setImageTypeId(genReq.getImageTypeId())
                        .setStyleId(genReq.getStyleId())
                        .setFileUri(fileUri)
                );

                requestRepository.markDone(genReq.getId(), asset.getId());
                log.info("Resume request #{}: DONE, asset #{}, файл {}",
                        genReq.getId(), asset.getId(), fileUri);

            } catch (AiProviderException e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                log.warn("Resume request #{}: FAILED — {}", genReq.getId(), e.getMessage());

                if (e.getHttpStatus() == 401 || e.getHttpStatus() == 403 || e.getHttpStatus() == 402) {
                    authFailed = true;
                    log.error("Resume: ошибка авторизации ({}) — остальные запросы batch #{} будут отменены",
                            e.getHttpStatus(), batchId);
                }

            } catch (IOException e) {
                String msg = "Ошибка сохранения файла: " + e.getMessage();
                requestRepository.markFailed(genReq.getId(), msg);
                log.error("Resume request #{}: {}", genReq.getId(), msg, e);

            } catch (Exception e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                log.error("Resume request #{}: неожиданная ошибка", genReq.getId(), e);
            }
        }

        log.info("Resume batch #{}: завершён", batchId);
    }

    // ===== Вспомогательные методы =====

    private void validateRequest(GenerateRequest request) {
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
    }

    /**
     * Общий цикл обработки запросов — используется и в generate(), и мог бы в resume.
     */
    private void processRequests(List<GenerationRequest> requests,
                                  List<ImageType> types, List<Style> styles,
                                  GenerationBatch batch, ImageAiProvider provider,
                                  String aspectRatio, String requestedModel) {
        boolean authFailed = false;

        for (GenerationRequest genReq : requests) {

            if (authFailed) {
                requestRepository.markFailed(genReq.getId(),
                        "Генерация остановлена: ошибка авторизации провайдера");
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage("Генерация остановлена: ошибка авторизации провайдера");
                continue;
            }

            requestRepository.markRunning(genReq.getId());

            try {
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

                byte[] imageBytes = provider.generate(finalPrompt, aspectRatio, requestedModel);
                String fileUri = imageStorageService.save(imageBytes);

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
    }

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

            int quoteStart = json.indexOf('"', colonIdx + 1);
            if (quoteStart < 0) return defaultValue;

            int quoteEnd = json.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) return defaultValue;

            String value = json.substring(quoteStart + 1, quoteEnd).trim();
            return value.isEmpty() ? defaultValue : value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
