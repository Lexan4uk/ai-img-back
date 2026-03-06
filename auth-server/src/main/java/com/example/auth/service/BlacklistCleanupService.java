package com.example.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.auth.repository.TokenBlacklistRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BlacklistCleanupService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistCleanupService.class);

    private final TokenBlacklistRepository blacklistRepository;

    /**
     * Удаляет просроченные записи из token_blacklist раз в час.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanup() {
        int deleted = blacklistRepository.deleteExpired();
        if (deleted > 0) {
            log.info("Очищено {} просроченных записей из token_blacklist", deleted);
        }
    }
}
