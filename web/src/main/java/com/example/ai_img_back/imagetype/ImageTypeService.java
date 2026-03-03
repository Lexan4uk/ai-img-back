package com.example.ai_img_back.imagetype;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ImageTypeService {

    public static final Long UNDEFINED_TYPE_ID = 1L;

    private final ImageTypeRepository imageTypeRepository;

    public ImageType create(Long currentUserId, String name, String typePrompt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название типа обязательно");
        }
        return imageTypeRepository.create(currentUserId, name.trim(), typePrompt);
    }

    public ImageType getById(Long id) {
        return imageTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Тип изображения с id " + id + " не найден"
                ));
    }

    public List<ImageType> getAll() {
        return imageTypeRepository.findAll();
    }

    public void delete(Long id, Long currentUserId) {
        ImageType imageType = getById(id);

        if (UNDEFINED_TYPE_ID.equals(id)) {
            throw new IllegalStateException("Нельзя удалить неопределённый тип");
        }

        if (!currentUserId.equals(imageType.getCreatedByUserId())) {
            throw new IllegalStateException("Удалить тип может только его создатель");
        }

        imageTypeRepository.reassignAssets(id, UNDEFINED_TYPE_ID);
        imageTypeRepository.reassignGenerationRequests(id, UNDEFINED_TYPE_ID);
        imageTypeRepository.delete(id);
    }

    public void addFavorite(Long userId, Long imageTypeId) {
        getById(imageTypeId);
        imageTypeRepository.addFavorite(userId, imageTypeId);
    }

    public void removeFavorite(Long userId, Long imageTypeId) {
        imageTypeRepository.removeFavorite(userId, imageTypeId);
    }

    public List<Long> getFavoriteIds(Long userId) {
        return imageTypeRepository.findFavoriteIdsByUserId(userId);
    }
}
