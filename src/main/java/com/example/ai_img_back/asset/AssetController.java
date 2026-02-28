package com.example.ai_img_back.asset;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * GET /assets?imageTypeId=...&styleId=...
     * Галерея: все картинки по типу + стилю, от всех пользователей.
     */
    @GetMapping
    public List<AssetDTO> getByTypeAndStyle(
            @RequestParam UUID imageTypeId,
            @RequestParam UUID styleId) {

        return assetRepository.findByTypeAndStyle(imageTypeId, styleId).stream()
                .map(AssetDTO::new)
                .toList();
    }
}
