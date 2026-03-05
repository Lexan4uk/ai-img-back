package com.example.ai_img_back.generation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * При старте сервера находит все «зависшие» запросы (RUNNING / PENDING)
 * и возобновляет их выполнение через GenerationService.
 *
 * Обрабатывает случай, когда сервер был остановлен во время генерации
 * (отключение электричества, перезагрузка и т.д.).
 */
@Component
@RequiredArgsConstructor
public class GenerationStartupCleaner {

    private static final Logger log = LoggerFactory.getLogger(GenerationStartupCleaner.class);

    private final GenerationRequestRepository requestRepository;
    private final GenerationService generationService;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeStaleRequests() {
        List<Long> unfinishedBatchIds = requestRepository.findUnfinishedBatchIds();

        if (unfinishedBatchIds.isEmpty()) {
            return;
        }

        log.info("Startup: найдено {} незавершённых batch-ей, возобновляю генерацию...",
                unfinishedBatchIds.size());

        for (Long batchId : unfinishedBatchIds) {
            generationService.resumeBatchAsync(batchId);
        }
    }
}
