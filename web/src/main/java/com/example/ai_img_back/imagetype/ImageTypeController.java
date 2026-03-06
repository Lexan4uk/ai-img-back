package com.example.ai_img_back.imagetype;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.auth.AuthUser;
import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/image-types")
@RequiredArgsConstructor
public class ImageTypeController {

    private final ImageTypeService imageTypeService;

    @PostMapping
    public ImageTypeDTO create(
            @AuthenticationPrincipal AuthUser currentUser,
            @RequestBody ImageTypeRequest request) {
        ImageType entity = imageTypeService.create(
                currentUser.getId(), request.getName(), request.getTypePrompt());
        return toDTO(entity);
    }

    @GetMapping
    public List<ImageTypeDTO> getAll() {
        return imageTypeService.getAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser currentUser) {
        imageTypeService.delete(id, currentUser.getId());
    }

    @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser currentUser) {
        imageTypeService.addFavorite(currentUser.getId(), id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser currentUser) {
        imageTypeService.removeFavorite(currentUser.getId(), id);
    }

    @GetMapping("/favorites")
    public List<Long> getFavoriteIds(
            @AuthenticationPrincipal AuthUser currentUser) {
        return imageTypeService.getFavoriteIds(currentUser.getId());
    }

    private ImageTypeDTO toDTO(ImageType entity) {
        ImageTypeDTO dto = new ImageTypeDTO();
        dto.setId(entity.getId());
        dto.setCreatedByUserId(entity.getCreatedByUserId());
        dto.setName(entity.getName());
        dto.setTypePrompt(entity.getTypePrompt());
        return dto;
    }
}
