package com.example.ai_img_back.user;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai_img_back.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Сервис пользователей — бизнес-логика и валидация.
 *
 * Паттерн из эталона:
 * - @Service + @RequiredArgsConstructor + @Transactional
 * - Валидация в начале метода (if/throw)
 * - EntityNotFoundException для "не найдено"
 *
 * @Transactional на классе — КАЖДЫЙ публичный метод = транзакция.
 * Если метод выбросит исключение (unchecked) → все SQL в нём откатятся.
 * В NestJS это как обернуть каждый метод в queryRunner.startTransaction().
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    /**
     * Создать пользователя.
     * email и displayName оба nullable по схеме, но хотя бы что-то должно быть.
     */
    public User create(String email, String displayName) {
        return userRepository.create(email, displayName);
    }

    /**
     * Получить по id или выбросить 404.
     *
     * .orElseThrow() — если Optional пустой, выполняет лямбду и бросает исключение.
     * Лямбда () -> new EntityNotFoundException(...) — создаёт исключение "лениво",
     * только если пользователь не найден (не тратим ресурсы на создание объекта зря).
     *
     * Этот метод используется и для получения, и для проверки существования
     * (например, перед удалением).
     */
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Пользователь с id " + id + " не найден"
                ));
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public void delete(UUID id) {
        getById(id); // проверяем существование → 404 если нет
        userRepository.delete(id);
    }
}
