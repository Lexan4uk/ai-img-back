package com.example.ai_img_back.asset;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.AssetDTO;

import lombok.RequiredArgsConstructor;

/**
 * Контроллер галереи — просмотр сгенерированных изображений.
 *
 * Галерея: выбираешь тип + стиль → видишь все картинки
 * от всех пользователей.
 *
 * @RequestParam — параметры из query string.
 * GET /assets?imageTypeId=...&styleId=...
 *
 * В NestJS: @Query('imageTypeId') imageTypeId: string
 */
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    /**
     * GET /assets?imageTypeId=...           — все картинки типа (все стили)
     * GET /assets?imageTypeId=...&styleId=...  — картинки по типу + стилю
     */
    @GetMapping
    public List<AssetDTO> getAssets(
            @RequestParam UUID imageTypeId,
            @RequestParam(required = false) UUID styleId) {
        List<Asset> assets = styleId != null
                ? assetRepository.findByTypeAndStyle(imageTypeId, styleId)
                : assetRepository.findByImageTypeId(imageTypeId);
        return assets.stream().map(this::toDTO).toList();
    }



    /** Entity → DTO маппинг */
    private AssetDTO toDTO(Asset entity) {
        AssetDTO dto = new AssetDTO();
        dto.setId(entity.getId());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setImageTypeId(entity.getImageTypeId());
        dto.setStyleId(entity.getStyleId());
        dto.setPromptId(entity.getPromptId());
        dto.setFinalPromptSnapshot(entity.getFinalPromptSnapshot());
        dto.setFileUri(entity.getFileUri());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
