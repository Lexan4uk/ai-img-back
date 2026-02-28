package com.example.ai_img_back.generation.enums;

/**
 * Результат проверки дедупликации.
 *
 * NEW — дубликат не найден, картинка новая.
 * DUPLICATE — такая картинка уже есть у пользователя.
 */
public enum DedupeResult {
    NEW,
    DUPLICATE
}
