package com.example.ai_img_back.generation.enums;

/**
 * Статус пакета генерации.
 *
 * Жизненный цикл: NEW → RUNNING → DONE/FAILED
 */
public enum BatchStatus {
    NEW,
    RUNNING,
    DONE,
    FAILED
}
