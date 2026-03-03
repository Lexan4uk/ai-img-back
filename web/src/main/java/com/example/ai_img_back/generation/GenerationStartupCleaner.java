package com.example.ai_img_back.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * При старте сервера переводит все «зависшие» запросы (RUNNING) в FAILED.
 * Это обрабатывает случай, когда сервер был остановлен во время генерации
 * (отключение электричества, перезагрузка и т.д.).
 */
@Component
@RequiredArgsConstructor
public class GenerationStartupCleaner {

    private static final Logger log = LoggerFactory.getLogger(GenerationStartupCleaner.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupStaleRequests() {
        String sql = """
                UPDATE generation_requests
                SET status = 'FAILED', error_message = 'Прервано при перезагрузке сервера'
                WHERE status = 'RUNNING'
                """;

        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource());

        if (updated > 0) {
            log.info("Startup: {} запросов переведены из RUNNING в FAILED", updated);
        }
    }
}
