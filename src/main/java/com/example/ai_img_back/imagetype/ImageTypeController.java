package com.example.ai_img_back.imagetype;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-контроллер типов изображений.
 *
 * Пока нет авторизации — userId передаётся через заголовок X-User-Id.
 * Это ВРЕМЕННОЕ решение для разработки. Потом заменим на
 * SecurityContext / JWT / etc.
 *
 * @RequestHeader — читает HTTP-заголовок из запроса.
 * В NestJS аналог: @Headers('X-User-Id') userId: string
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
        ImageType imageType = imageTypeService.create(
                currentUserId,
                request.getName(),
                request.getTypePrompt());
        return new ImageTypeDTO(imageType);
    }

    /** GET /image-types — все типы */
    @GetMapping
    public List<ImageTypeDTO> getAll() {
        return imageTypeService.getAll().stream()
                .map(ImageTypeDTO::new)
                .toList();
    }

    /** GET /image-types/{id} — один тип */
    @GetMapping("/{id}")
    public ImageTypeDTO getById(@PathVariable UUID id) {
        return new ImageTypeDTO(imageTypeService.getById(id));
    }

    /** DELETE /image-types/{id} — удалить тип (только создатель) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        imageTypeService.delete(id, currentUserId);
    }

    /**
     * POST /image-types/{id}/favorite — добавить в избранное.
     */
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

    /** GET /image-types/favorites — мои избранные типы */
    @GetMapping("/favorites")
    public List<ImageTypeDTO> getFavorites(@RequestHeader("UserId") UUID currentUserId) {
        return imageTypeService.getFavorites(currentUserId).stream()
                .map(ImageTypeDTO::new)
                .toList();
    }

}
