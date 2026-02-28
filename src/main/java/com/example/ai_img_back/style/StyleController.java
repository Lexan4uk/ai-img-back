package com.example.ai_img_back.style;

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

@RestController
@RequestMapping("/styles")
@RequiredArgsConstructor
public class StyleController {

    private final StyleService styleService;

    @PostMapping
    public StyleDTO create(
            @RequestHeader("UserId") UUID currentUserId,
            @RequestBody StyleRequest request) {
        Style style = styleService.create(
                currentUserId,
                request.getName(),
                request.getStylePrompt());
        return new StyleDTO(style);
    }

    @GetMapping
    public List<StyleDTO> getAll() {
        return styleService.getAll().stream()
                .map(StyleDTO::new)
                .toList();
    }

    @GetMapping("/{id}")
    public StyleDTO getById(@PathVariable UUID id) {
        return new StyleDTO(styleService.getById(id));
    }

    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.delete(id, currentUserId);
    }
		/*favorites */
	  @PostMapping("/{id}/favorite")
    public void addFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.addFavorite(currentUserId, id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(
            @PathVariable UUID id,
            @RequestHeader("UserId") UUID currentUserId) {
        styleService.removeFavorite(currentUserId, id);
    }

    @GetMapping("/favorites")
    public List<StyleDTO> getFavorites(@RequestHeader("UserId") UUID currentUserId) {
        return styleService.getFavorites(currentUserId).stream()
                .map(StyleDTO::new)
                .toList();
    }

}
