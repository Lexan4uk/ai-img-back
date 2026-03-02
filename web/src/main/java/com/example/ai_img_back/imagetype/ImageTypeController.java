package com.example.ai_img_back.imagetype;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;

import lombok.RequiredArgsConstructor;

/**
 * REST-контроллер типов изображений.
 *
 * Маппинг Spring → NestJS:
 *   @RestController          → @Controller()
 *   @RequestMapping("/...") → @Controller('...')
 *   @GetMapping("/{id}")     → @Get(':id')
 *   @PathVariable UUID id    → @Param('id', ParseUUIDPipe) id: string
 *   @RequestBody             → @Body()
 *   @RequestHeader           → @Headers('...')
 *
 * DTO и Request импортируются из client-utils (единый источник правды).
 */
@RestController
@RequestMapping("/image-types")
@RequiredArgsConstructor
public class ImageTypeController {

    private final ImageTypeService imageTypeService;

    /** POST /image-types — создать тип */
    @PostMapping
    public ImageTypeDTO create(
            @RequestHeader("UserId") UUID currentUserId,
            @RequestBody ImageTypeRequest request) {
        ImageType entity = imageTypeService.create(
                currentUserId, request.getName(), request.getTypePrompt());
        return toDTO(entity);
    }

    /** GET /image-types — все типы */
    @GetMapping
    public List<ImageTypeDTO> getAll() {
        return imageTypeService.getAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** DELETE /image-types/{id} — удалить тип (только создатель) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        imageTypeService.delete(id, currentUserId);
    }

    /** POST /image-types/{id}/favorite — добавить в избранное */
    @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        imageTypeService.addFavorite(currentUserId, id);
    }

    /** DELETE /image-types/{id}/favorite — убрать из избранного */
    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        imageTypeService.removeFavorite(currentUserId, id);
    }

    /** GET /image-types/favorites — ID избранных типов */
    @GetMapping("/favorites")
    public List<UUID> getFavoriteIds(
            @RequestHeader("UserId") UUID currentUserId) {
        return imageTypeService.getFavoriteIds(currentUserId);
    }

    /**
     * Entity → DTO маппинг.
     */
    private ImageTypeDTO toDTO(ImageType entity) {
        ImageTypeDTO dto = new ImageTypeDTO();
        dto.setId(entity.getId());
        dto.setCreatedByUserId(entity.getCreatedByUserId());
        dto.setName(entity.getName());
        dto.setTypePrompt(entity.getTypePrompt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
