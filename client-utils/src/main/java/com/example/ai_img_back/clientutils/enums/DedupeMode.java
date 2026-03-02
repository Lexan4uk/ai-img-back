package com.example.ai_img_back.clientutils.enums;

/**
 * Режим дедупликации: что делать если такая картинка уже есть.
 *
 * SKIP — пропустить, не генерировать заново.
 * OVERWRITE — сгенерировать новую (старая остаётся, просто добавится ещё одна).
 */
public enum DedupeMode {
    SKIP,
    OVERWRITE
}
