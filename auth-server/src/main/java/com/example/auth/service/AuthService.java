package com.example.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.auth.dto.TokenResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.AuthException;
import com.example.auth.exception.EmailAlreadyExistsException;
import com.example.auth.repository.AuthUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepository;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Регистрация нового пользователя.
     * Проверяет уникальность email, хеширует пароль, создаёт запись,
     * возвращает пару JWT-токенов.
     */
    public TokenResponse register(String email, String password, String displayName) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new EmailAlreadyExistsException("Email уже зарегистрирован");
        });

        String hash = passwordEncoder.encode(password);
        AuthUser user = userRepository.create(email, displayName, hash);

        return tokenService.generatePair(user);
    }

    /**
     * Логин по email + пароль.
     * Возвращает пару JWT-токенов.
     */
    public TokenResponse login(String email, String password) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Неверный email или пароль"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Неверный email или пароль");
        }

        return tokenService.generatePair(user);
    }

    /**
     * Смена пароля. Проверяет текущий пароль, обновляет хеш.
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthException("Неверный текущий пароль");
        }

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePasswordHash(userId, newHash);
    }
}
