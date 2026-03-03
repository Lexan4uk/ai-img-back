package com.example.ai_img_back.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

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
import com.example.ai_img_back.style.Style;
import com.example.ai_img_back.style.StyleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GenerationService {

    private final ImageTypeService imageTypeService;
    private final StyleService styleService;
    private final AssetRepository assetRepository;
    private final GenerationBatchRepository batchRepository;
    private final GenerationRequestRepository requestRepository;

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

        GenerationBatch batch = batchRepository.create(
                currentUserId,
                request.getUserPrompt().trim(),
                generationParams,
                dedupeMode,
                request.getProvider() != null ? request.getProvider() : "openai",
                request.getModel(),
                request.getRoutingMode() != null ? request.getRoutingMode() : RoutingMode.DIRECT
        );

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

        // === Шаг 3: Последовательное выполнение ===

        for (GenerationRequest genReq : requests) {

            // Проверка дедупликации
            boolean isDuplicate = assetRepository.existsByHash(genReq.getFinalPromptHash());

            if (isDuplicate && dedupeMode == DedupeMode.SKIP) {
                requestRepository.markSkipped(genReq.getId());
                genReq.setStatus(RequestStatus.SKIPPED);
                continue;
            }

            requestRepository.markRunning(genReq.getId());

            try {
                // Вызов AI (заглушка)
                AiResult aiResult = callAiProvider(genReq.getFinalPromptHash(), currentUserId);

                // Сохранить asset
                Asset asset = assetRepository.create(new Asset()
                        .setImageTypeId(genReq.getImageTypeId())
                        .setStyleId(genReq.getStyleId())
                        .setFileUri(aiResult.fileUri())
                );

                requestRepository.markDone(genReq.getId(), asset.getId());
                genReq.setStatus(RequestStatus.DONE);
                genReq.setCreatedAssetId(asset.getId());

            } catch (Exception e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
            }
        }

        // Статус batch не хранится — вычисляется по COUNT реквестов

        return requests;
    }

    // ===== Вспомогательные методы =====

    private String composeFinalPrompt(String typePrompt, String stylePrompt, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        if (!typePrompt.isBlank()) {
            sb.append(typePrompt).append(". ");
        }
        if (!stylePrompt.isBlank()) {
            sb.append(stylePrompt).append(". ");
        }
        sb.append(userPrompt);
        return sb.toString().trim();
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

    record AiResult(String fileUri, String provider, String model) {}

    private AiResult callAiProvider(String finalPromptHash, Long ownerUserId) {
        // TODO: заменить на реальный вызов AI API
        String fakeUri = "generated/" + ownerUserId + "/" + UUID.randomUUID() + ".png";
        return new AiResult(fakeUri, "stub", "stub-model");
    }
}
