package com.example.ai_img_back.imagetype;

import java.util.List;

import org.springframework.web.bind.annotation.*;

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
            @RequestAttribute("userId") Long currentUserId,
            @RequestBody ImageTypeRequest request) {
        ImageType entity = imageTypeService.create(
                currentUserId, request.getName(), request.getTypePrompt());
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
            @RequestAttribute("userId") Long currentUserId) {
        imageTypeService.delete(id, currentUserId);
    }

    @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable Long id,
            @RequestAttribute("userId") Long currentUserId) {
        imageTypeService.addFavorite(currentUserId, id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable Long id,
            @RequestAttribute("userId") Long currentUserId) {
        imageTypeService.removeFavorite(currentUserId, id);
    }

    @GetMapping("/favorites")
    public List<Long> getFavoriteIds(
            @RequestAttribute("userId") Long currentUserId) {
        return imageTypeService.getFavoriteIds(currentUserId);
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
