package com.example.ai_img_back.asset;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.AssetDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    @GetMapping
    public List<AssetDTO> getAssets(
            @RequestParam Long imageTypeId,
            @RequestParam(required = false) Long styleId) {
        List<Asset> assets = styleId != null
                ? assetRepository.findByTypeAndStyle(imageTypeId, styleId)
                : assetRepository.findByImageTypeId(imageTypeId);
        return assets.stream().map(this::toDTO).toList();
    }

    private AssetDTO toDTO(Asset entity) {
        AssetDTO dto = new AssetDTO();
        dto.setId(entity.getId());
        dto.setImageTypeId(entity.getImageTypeId());
        dto.setStyleId(entity.getStyleId());
        dto.setFileUri(entity.getFileUri());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
