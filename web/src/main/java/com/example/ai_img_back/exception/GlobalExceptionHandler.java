package com.example.ai_img_back.exception;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик ошибок.
 * Перехватывает исключения из контроллеров и возвращает красивый JSON-ответ.
 *
 * В NestJS это @Catch() + ExceptionFilter.
 * Тут — @RestControllerAdvice: Spring автоматически оборачивает все контроллеры.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Сущность не найдена → 404.
     * Срабатывает когда сервис бросает new EntityNotFoundException("...").
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)                    // 404
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Ошибка валидации (null/пустые поля) → 400.
     * IllegalArgumentException — стандартный Java-класс для "неверный аргумент".
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)                  // 400
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Нарушение ограничений БД → 400.
     * Например: дубликат email (UNIQUE constraint), несуществующий FK.
     *
     * DataIntegrityViolationException — Spring оборачивает SQLException в это.
     * В NestJS аналог — ловить QueryFailedError от TypeORM.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDbConstraint(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)                  // 400
                .body(Map.of("error", "Нарушение ограничений базы данных"));
    }
    /**
     * Операция запрещена → 403.
     * Например: попытка удалить чужой тип или неопределённый тип.
     *
     * IllegalStateException — стандартный Java-класс для
     * "объект в неправильном состоянии для этой операции".
     * Используем его для бизнес-запретов.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)                    // 403
                .body(Map.of("error", ex.getMessage()));
    }


    /**
     * Всё остальное → 500.
     * Ловит любые необработанные исключения (NPE, баги и т.д.).
     * Не показываем детали клиенту — это может быть утечкой информации.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)        // 500
                .body(Map.of("error", ex.getMessage()));
    }
}