package com.example.ai_img_back.clientutils.enums;

/**
 * Статус отдельного запроса генерации.
 *
 * PENDING → RUNNING → DONE/FAILED
 */
public enum RequestStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}
