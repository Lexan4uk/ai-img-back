package com.example.ai_img_back.style;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.StyleDTO;
import com.example.ai_img_back.clientutils.dto.StyleRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/styles")
@RequiredArgsConstructor
public class StyleController {

    private final StyleService styleService;

    @PostMapping
    public StyleDTO create(
            @RequestHeader("UserId") Long currentUserId,
            @RequestBody StyleRequest request) {
        Style entity = styleService.create(
                currentUserId, request.getName(), request.getStylePrompt());
        return toDTO(entity);
    }

    @GetMapping
    public List<StyleDTO> getAll() {
        return styleService.getAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @RequestHeader("UserId") Long currentUserId) {
        styleService.delete(id, currentUserId);
    }

    @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable Long id,
            @RequestHeader("UserId") Long currentUserId) {
        styleService.addFavorite(currentUserId, id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable Long id,
            @RequestHeader("UserId") Long currentUserId) {
        styleService.removeFavorite(currentUserId, id);
    }

    @GetMapping("/favorites")
    public List<Long> getFavoriteIds(
            @RequestHeader("UserId") Long currentUserId) {
        return styleService.getFavoriteIds(currentUserId);
    }

    private StyleDTO toDTO(Style entity) {
        StyleDTO dto = new StyleDTO();
        dto.setId(entity.getId());
        dto.setCreatedByUserId(entity.getCreatedByUserId());
        dto.setName(entity.getName());
        dto.setStylePrompt(entity.getStylePrompt());
        return dto;
    }
}
