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
import com.example.ai_img_back.clientutils.enums.BatchStatus;
import com.example.ai_img_back.clientutils.enums.DedupeMode;
import com.example.ai_img_back.clientutils.enums.DedupeResult;
import com.example.ai_img_back.clientutils.enums.RequestStatus;
import com.example.ai_img_back.clientutils.enums.RoutingMode;
import com.example.ai_img_back.imagetype.ImageType;
import com.example.ai_img_back.imagetype.ImageTypeService;
import com.example.ai_img_back.prompt.Prompt;
import com.example.ai_img_back.prompt.PromptRepository;
import com.example.ai_img_back.style.Style;
import com.example.ai_img_back.style.StyleService;

import lombok.RequiredArgsConstructor;

/**
 * Главный сервис генерации — оркестратор.
 *
 * Координирует весь процесс:
 * 1. Валидация входных данных
 * 2. Создание промпта (одноразовый)
 * 3. Создание batch
 * 4. Создание request-ов (декартово произведение type × style)
 * 5. Последовательное выполнение каждого request
 * 6. Завершение batch
 *
 * В NestJS это был бы GenerationService с конструктором:
 *   constructor(
 *     private readonly imageTypeService: ImageTypeService,
 *     private readonly styleService: StyleService,
 *     private readonly promptRepo: PromptRepository,
 *     private readonly assetRepo: AssetRepository,
 *     private readonly batchRepo: GenerationBatchRepository,
 *     private readonly requestRepo: GenerationRequestRepository,
 *   ) {}
 *
 * @RequiredArgsConstructor генерирует точно такой же конструктор.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GenerationService {

    private final ImageTypeService imageTypeService;
    private final StyleService styleService;
    private final PromptRepository promptRepository;
    private final AssetRepository assetRepository;
    private final GenerationBatchRepository batchRepository;
    private final GenerationRequestRepository requestRepository;

    /**
     * Главный метод — запуск генерации.
     *
     * Возвращает список GenerationRequest с итоговыми статусами.
     * Контроллер преобразует их в DTO для ответа клиенту.
     *
     * @param currentUserId — кто запускает
     * @param request — тело запроса от клиента
     */
    public List<GenerationRequest> generate(UUID currentUserId, GenerateRequest request) {

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
        

        // Загрузить и проверить существование типов и стилей.
        // getById бросит EntityNotFoundException если не найден.
        List<ImageType> types = request.getImageTypeIds().stream()
                .map(imageTypeService::getById)
                .toList();

        List<Style> styles = request.getStyleIds().stream()
                .map(styleService::getById)
                .toList();

        // === Создание промпта (одноразовый снимок) ===

        Prompt prompt = promptRepository.create(
                currentUserId,
                request.getUserPrompt().trim(),
                request.getGenerationParams() == null ? "{}" : request.getGenerationParams()
        );

        // === Шаг 1: Создать batch ===

        GenerationBatch batch = batchRepository.create(
                currentUserId,
                request.getProvider() != null ? request.getProvider() : "openai",
                request.getModel(),
                request.getRoutingMode() != null ? request.getRoutingMode() : RoutingMode.DIRECT,
                BatchStatus.RUNNING
        );

        // === Шаг 2: Сформировать request-ы (декартово произведение) ===

        /*
         * Декартово произведение:
         * 2 типа × 3 стиля = 6 request-ов.
         *
         * В TypeScript это:
         *   types.flatMap(type => styles.map(style => ({ type, style })))
         *
         * В Java — вложенный for. Можно и через Stream,
         * но for нагляднее для начинающих.
         */
        List<GenerationRequest> requests = new ArrayList<>();

        for (ImageType type : types) {
            for (Style style : styles) {
                // Собрать промпт
                String typePromptSnapshot = type.getTypePrompt() != null ? type.getTypePrompt() : "";
                String stylePromptSnapshot = style.getStylePrompt() != null ? style.getStylePrompt() : "";
                String userPromptSnapshot = prompt.getText();

                String finalPromptSnapshot = composeFinalPrompt(
                        typePromptSnapshot, stylePromptSnapshot, userPromptSnapshot);

                String finalPromptHash = computeHash(finalPromptSnapshot, prompt.getGenerationParams());

                // Проверка дедупликации
                DedupeMode dedupeMode = request.getDedupeMode() != null
                        ? request.getDedupeMode() : DedupeMode.SKIP;

                boolean isDuplicate = assetRepository
                        .findByOwnerAndHash(currentUserId, finalPromptHash)
                        .isPresent();

                DedupeResult dedupeResult = isDuplicate ? DedupeResult.DUPLICATE : DedupeResult.NEW;

                // Создать request
                GenerationRequest genRequest = requestRepository.create(
                        batch.getId(), currentUserId,
                        type.getId(), style.getId(), prompt.getId(),
                        userPromptSnapshot, finalPromptSnapshot, finalPromptHash,
                        dedupeResult, dedupeMode
                );

                requests.add(genRequest);
            }
        }

        // === Шаг 3: Последовательное выполнение ===

        int doneCount = 0;
        int failedCount = 0;

        for (GenerationRequest genReq : requests) {

            // Пропуск дубликатов
            if (genReq.getDedupeResult() == DedupeResult.DUPLICATE
                    && genReq.getDedupeMode() == DedupeMode.SKIP) {
                requestRepository.markSkipped(genReq.getId());
                genReq.setStatus(RequestStatus.SKIPPED);
                doneCount++;
                continue;
            }

            // Пометить как RUNNING
            requestRepository.markRunning(genReq.getId());

            try {
                // Вызов AI (заглушка)
                AiResult aiResult = callAiProvider(
                        genReq.getFinalPromptSnapshot(),
                        genReq.getOwnerUserId()
                );

                // Сохранить asset
                Asset asset = assetRepository.create(new Asset()
                        .setOwnerUserId(genReq.getOwnerUserId())
                        .setImageTypeId(genReq.getImageTypeId())
                        .setStyleId(genReq.getStyleId())
                        .setPromptId(genReq.getPromptId())
                        .setUserPromptSnapshot(genReq.getUserPromptSnapshot())
                        .setTypePromptSnapshot(findTypePromptSnapshot(genReq.getImageTypeId()))
                        .setStylePromptSnapshot(findStylePromptSnapshot(genReq.getStyleId()))
                        .setFinalPromptSnapshot(genReq.getFinalPromptSnapshot())
                        .setFinalPromptHash(genReq.getFinalPromptHash())
                        .setFileUri(aiResult.fileUri())
                        .setProvider(aiResult.provider())
                        .setModel(aiResult.model())
                        .setMeta(aiResult.meta())
                );

                requestRepository.markDone(genReq.getId(), asset.getId());
                genReq.setStatus(RequestStatus.DONE);
                genReq.setCreatedAssetId(asset.getId());
                doneCount++;

            } catch (Exception e) {
                requestRepository.markFailed(genReq.getId(), e.getMessage());
                genReq.setStatus(RequestStatus.FAILED);
                genReq.setErrorMessage(e.getMessage());
                failedCount++;
            }
        }

        // === Шаг 4: Завершить batch ===

        BatchStatus finalStatus = failedCount > 0 && doneCount == 0
                ? BatchStatus.FAILED : BatchStatus.DONE;
        batchRepository.updateStatus(batch.getId(), finalStatus);

        return requests;
    }

    // ===== Вспомогательные методы =====

    /**
     * Сборка финального промпта.
     * Пока — простая конкатенация. Позже можно добавить
     * шаблон, quality-блок, нормализацию и т.д.
     */
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

    /**
     * SHA-256 хеш для дедупликации.
     *
     * Включает и текст промпта, и generation_params —
     * одинаковый текст с разным размером = разные картинки.
     *
     * MessageDigest — стандартный Java API для хеширования.
     * HexFormat — перевод байтов в hex-строку (Java 17+).
     */
    private String computeHash(String finalPrompt, String generationParams) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = finalPrompt + "|" + (generationParams != null ? generationParams : "{}");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 всегда доступен в JVM — сюда не попадём
            throw new RuntimeException("SHA-256 не доступен", e);
        }
    }

    private String findTypePromptSnapshot(UUID imageTypeId) {
        String tp = imageTypeService.getById(imageTypeId).getTypePrompt();
        return tp != null ? tp : "";
    }

    private String findStylePromptSnapshot(UUID styleId) {
        String sp = styleService.getById(styleId).getStylePrompt();
        return sp != null ? sp : "";
    }

    /**
     * Заглушка AI-клиента.
     */
    record AiResult(String fileUri, String provider, String model, String meta) {}

    private AiResult callAiProvider(String finalPrompt, UUID ownerUserId) {
        // TODO: заменить на реальный вызов AI API
        String fakeUri = "generated/" + ownerUserId + "/" + UUID.randomUUID() + ".png";
        return new AiResult(fakeUri, "stub", "stub-model", null);
    }
}
