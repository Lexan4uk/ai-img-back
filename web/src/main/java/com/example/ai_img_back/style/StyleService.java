package com.example.ai_img_back.style;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class StyleService {

    /**
     * Фиксированный UUID неопределённого стиля.
     * Совпадает с seed в changeset-2.xml.
     */
    public static final UUID UNDEFINED_STYLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final StyleRepository styleRepository;

    public Style create(UUID currentUserId, String name, String stylePrompt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название стиля обязательно");
        }
        return styleRepository.create(currentUserId, name.trim(), stylePrompt);
    }

    public Style getById(UUID id) {
        return styleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Стиль с id " + id + " не найден"
                ));
    }

    public List<Style> getAll() {
        return styleRepository.findAll();
    }

    public void delete(UUID id, UUID currentUserId) {
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
		public void addFavorite(UUID userId, UUID styleId) {
        getById(styleId);
        styleRepository.addFavorite(userId, styleId);
    }

    public void removeFavorite(UUID userId, UUID styleId) {
        styleRepository.removeFavorite(userId, styleId);
    }

    public List<UUID> getFavoriteIds(UUID userId) {
        return styleRepository.findFavoriteIdsByUserId(userId);
    }


}
