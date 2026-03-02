package com.example.ai_img_back;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Базовый класс для всех интеграционных тестов.
 *
 * Используем паттерн "Singleton Container":
 * - Контейнер стартует ОДИН раз в static-блоке
 * - Живёт до конца JVM (всех тестов)
 * - НЕ используем @Testcontainers и @Container
 *
 * Почему не @Testcontainers + @Container?
 * При нескольких тест-классах JUnit останавливает контейнер
 * после первого класса → второй класс ловит таймаут.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Один контейнер на ВСЕ тесты.
     *
     * static { postgres.start(); } — запускается при загрузке класса.
     * JVM shutdown hook (внутри Testcontainers) убьёт контейнер
     * когда все тесты завершатся.
     *
     * Без @Container — JUnit не управляет lifecycle.
     * Мы управляем сами: start() здесь, stop() — автоматически.
     */
    static PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
