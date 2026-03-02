package com.example.ai_img_back.generation;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.GenerationResultDTO;

import lombok.RequiredArgsConstructor;

/**
 * REST-контроллер генерации.
 *
 * Один эндпоинт: POST /generations — запустить генерацию.
 * Ответ — список результатов по каждой паре (тип × стиль).
 */
@RestController
@RequestMapping("/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    /**
     * POST /generations — запустить генерацию.
     *
     * Тело: GenerateRequest (prompt + types[] + styles[] + params)
     * Ответ: список результатов по каждому request
     */
    @PostMapping
    public List<GenerationResultDTO> generate(
            @RequestHeader("UserId") UUID currentUserId,
            @RequestBody GenerateRequest request) {

        List<GenerationRequest> results =
                generationService.generate(currentUserId, request);

        return results.stream()
                .map(this::toDTO)
                .toList();
    }

    /** GenerationRequest entity → GenerationResultDTO маппинг */
    private GenerationResultDTO toDTO(GenerationRequest req) {
        GenerationResultDTO dto = new GenerationResultDTO();
        dto.setRequestId(req.getId());
        dto.setImageTypeId(req.getImageTypeId());
        dto.setStyleId(req.getStyleId());
        dto.setStatus(req.getStatus());
        dto.setCreatedAssetId(req.getCreatedAssetId());
        dto.setErrorMessage(req.getErrorMessage());
        // fileUri заполним позже через asset, пока null
        return dto;
    }
}
