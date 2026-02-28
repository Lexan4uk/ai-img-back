package com.example.ai_img_back.generation;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        List<GenerationRequest> results = generationService.generate(currentUserId, request);

        return results.stream()
                .map(GenerationResultDTO::new)
                .toList();
    }
}
