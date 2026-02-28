package com.example.ai_img_back.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа клиенту.
 *
 * Паттерн конвертации Entity → DTO:
 *   new UserDTO(user)
 *
 * Зачем DTO, если он выглядит как entity?
 * - Entity = внутренняя модель, может меняться с таблицей
 * - DTO = контракт API, стабильный для клиента
 * - Можно скрыть поля, добавить вычисляемые, изменить имена
 *
 * В NestJS то же самое: entity ≠ response DTO.
 */
@Data
@NoArgsConstructor
public class UserDTO {

    private UUID id;
    private String email;
    private String displayName;
    private OffsetDateTime createdAt;

    /** Конструктор из entity — основной способ конвертации */
    public UserDTO(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.createdAt = user.getCreatedAt();
    }
}
