package com.example.ai_img_back.imagetype;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Сервис типов изображений.
 *
 * Бизнес-правила:
 * - Типы общие для всех пользователей
 * - Нередактируемые (только create/delete)
 * - Удалять может только создатель (created_by_user_id)
 * - "Неопределённый" тип нельзя удалить
 * - При удалении — assets переназначаются на "Неопределённый"
 *
 * Константы с фиксированными UUID:
 * В Java нет enum с UUID "из коробки", поэтому используем
 * public static final — это аналог:
 *   export const UNDEFINED_TYPE_ID = '00000000-...'
 * в TypeScript.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImageTypeService {

    /**
     * Фиксированный UUID неопределённого типа.
     * Совпадает с seed в changeset-2.xml.
     * Используется как fallback при удалении типа.
     */
    public static final UUID UNDEFINED_TYPE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ImageTypeRepository imageTypeRepository;

    /**
     * Создать тип.
     * @param currentUserId — кто создаёт (из контекста авторизации)
     */
    public ImageType create(UUID currentUserId, String name, String typePrompt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название типа обязательно");
        }
        return imageTypeRepository.create(currentUserId, name.trim(), typePrompt);
    }

    public ImageType getById(UUID id) {
        return imageTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Тип изображения с id " + id + " не найден"
                ));
    }

    public List<ImageType> getAll() {
        return imageTypeRepository.findAll();
    }

    /**
     * Удалить тип.
     *
     * Порядок операций (всё в одной транзакции благодаря @Transactional):
     * 1. Проверка: существует, не undefined, текущий юзер = создатель
     * 2. Переназначить assets → undefined
     * 3. Переназначить generation_requests → undefined
     * 4. DELETE тип
     *
     * IllegalStateException — для "операция запрещена".
     * Отличие от других исключений:
     * - IllegalArgumentException → "ты послал неправильные данные" (400)
     * - EntityNotFoundException → "такого нет" (404)
     * - IllegalStateException → "объект есть, данные ОК, но операция запрещена" (403/409)
     *
     * Пока обработчика для IllegalStateException в GlobalExceptionHandler нет —
     * оно упадёт в generic Exception → 500. Мы добавим обработчик позже.
     *
     * @param currentUserId — кто удаляет (из контекста авторизации)
     */
    public void delete(UUID id, UUID currentUserId) {
        ImageType imageType = getById(id);

        // Нельзя удалить "Неопределённый"
        if (UNDEFINED_TYPE_ID.equals(id)) {
            throw new IllegalStateException("Нельзя удалить неопределённый тип");
        }

        // Удалять может только создатель
        if (!currentUserId.equals(imageType.getCreatedByUserId())) {
            throw new IllegalStateException("Удалить тип может только его создатель");
        }

        // Переназначить связанные записи на "Неопределённый"
        imageTypeRepository.reassignAssets(id, UNDEFINED_TYPE_ID);
        imageTypeRepository.reassignGenerationRequests(id, UNDEFINED_TYPE_ID);

        // Удалить сам тип (избранное удалится по CASCADE)
        imageTypeRepository.delete(id);
    }
    
    public void addFavorite(UUID userId, UUID imageTypeId) {
        getById(imageTypeId); // проверяем что тип существует → 404 если нет
        imageTypeRepository.addFavorite(userId, imageTypeId);
    }

    public void removeFavorite(UUID userId, UUID imageTypeId) {
        imageTypeRepository.removeFavorite(userId, imageTypeId);
    }

    public List<ImageType> getFavorites(UUID userId) {
        return imageTypeRepository.findFavoritesByUserId(userId);
    }

}
