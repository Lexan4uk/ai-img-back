package com.example.ai_img_back.style;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class StyleService {

    public static final Long UNDEFINED_STYLE_ID = 1L;

    private final StyleRepository styleRepository;

    public Style create(Long currentUserId, String name, String stylePrompt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название стиля обязательно");
        }
        return styleRepository.create(currentUserId, name.trim(), stylePrompt);
    }

    public Style getById(Long id) {
        return styleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Стиль с id " + id + " не найден"
                ));
    }

    public List<Style> getAll() {
        return styleRepository.findAll();
    }

    public void delete(Long id, Long currentUserId) {
        Style style = getById(id);

        if (UNDEFINED_STYLE_ID.equals(id)) {
            throw new IllegalStateException("Нельзя удалить неопределённый стиль");
        }

        if (!currentUserId.equals(style.getCreatedByUserId())) {
            throw new IllegalStateException("Удалить стиль может только его создатель");
        }

        styleRepository.reassignAssets(id, UNDEFINED_STYLE_ID);
        styleRepository.reassignGenerationRequests(id, UNDEFINED_STYLE_ID);
        styleRepository.delete(id);
    }

    public void addFavorite(Long userId, Long styleId) {
        getById(styleId);
        styleRepository.addFavorite(userId, styleId);
    }

    public void removeFavorite(Long userId, Long styleId) {
        styleRepository.removeFavorite(userId, styleId);
    }

    public List<Long> getFavoriteIds(Long userId) {
        return styleRepository.findFavoriteIdsByUserId(userId);
    }
}
