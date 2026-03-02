package com.example.ai_img_back.style;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.StyleDTO;
import com.example.ai_img_back.clientutils.dto.StyleRequest;

import lombok.RequiredArgsConstructor;

/**
 * REST-контроллер стилей.
 *
 * Зеркало ImageTypeController, но с полем stylePrompt вместо typePrompt.
 * Оба — общие справочники, нередактируемые, только create/delete.
 */
@RestController
@RequestMapping("/styles")
@RequiredArgsConstructor
public class StyleController {

    private final StyleService styleService;

    /** POST /styles — создать стиль */
    @PostMapping
    public StyleDTO create(
            @RequestHeader("UserId") UUID currentUserId,
            @RequestBody StyleRequest request) {
        Style entity = styleService.create(
                currentUserId, request.getName(), request.getStylePrompt());
        return toDTO(entity);
    }

    /** GET /styles — все стили */
    @GetMapping
    public List<StyleDTO> getAll() {
        return styleService.getAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** DELETE /styles/{id} — удалить стиль (только создатель) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.delete(id, currentUserId);
    }

    /** POST /styles/{id}/favorite — добавить в избранное */
    @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.addFavorite(currentUserId, id);
    }

    /** DELETE /styles/{id}/favorite — убрать из избранного */
    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.removeFavorite(currentUserId, id);
    }

    /** GET /styles/favorites — ID избранных стилей */
    @GetMapping("/favorites")
    public List<UUID> getFavoriteIds(
            @RequestHeader("UserId") UUID currentUserId) {
        return styleService.getFavoriteIds(currentUserId);
    }

    /** Entity → DTO маппинг */
    private StyleDTO toDTO(Style entity) {
        StyleDTO dto = new StyleDTO();
        dto.setId(entity.getId());
        dto.setCreatedByUserId(entity.getCreatedByUserId());
        dto.setName(entity.getName());
        dto.setStylePrompt(entity.getStylePrompt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
