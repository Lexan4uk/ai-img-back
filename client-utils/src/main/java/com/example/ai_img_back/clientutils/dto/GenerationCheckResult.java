package com.example.ai_img_back.clientutils.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат проверки дубликатов перед генерацией.
 * Клиент получает эту информацию и спрашивает пользователя,
 * нужно ли пересоздавать уже существующие изображения.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationCheckResult {
    /** Общее количество пар тип×стиль */
    private int totalCount;
    /** Сколько из них уже существуют (дубликаты) */
    private int duplicateCount;
    /** Сколько новых (ещё не сгенерированы) */
    private int newCount;
}
