package com.example.ai_img_back.generation;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.GenerationResultDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    public List<GenerationResultDTO> generate(
            @RequestHeader("UserId") Long currentUserId,
            @RequestBody GenerateRequest request) {

        List<GenerationRequest> results =
                generationService.generate(currentUserId, request);

        return results.stream()
                .map(this::toDTO)
                .toList();
    }

    private GenerationResultDTO toDTO(GenerationRequest req) {
        GenerationResultDTO dto = new GenerationResultDTO();
        dto.setRequestId(req.getId());
        dto.setImageTypeId(req.getImageTypeId());
        dto.setStyleId(req.getStyleId());
        dto.setStatus(req.getStatus());
        dto.setCreatedAssetId(req.getCreatedAssetId());
        dto.setErrorMessage(req.getErrorMessage());
        return dto;
    }
}
