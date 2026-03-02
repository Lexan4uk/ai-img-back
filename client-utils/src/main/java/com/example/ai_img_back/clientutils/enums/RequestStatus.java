package com.example.ai_img_back.clientutils.enums;

/**
 * Статус отдельного запроса генерации.
 *
 * PENDING → RUNNING → DONE/FAILED/SKIPPED
 * SKIPPED — если дубликат и dedupe_mode=SKIP.
 */
public enum RequestStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED
}
