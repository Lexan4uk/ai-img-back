package com.example.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth.dto.AccessTokenResponse;
import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LogoutRequest;
import com.example.auth.dto.RefreshRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.TokenResponse;
import com.example.auth.dto.ValidateRequest;
import com.example.auth.dto.ValidateResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.service.AuthService;
import com.example.auth.service.TokenService;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthUserRepository userRepository;

    /**
     * POST /register — регистрация нового пользователя.
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        TokenResponse tokens = authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getDisplayName());
        return ResponseEntity.ok(tokens);
    }

    /**
     * POST /login — вход по email + пароль.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        TokenResponse tokens = authService.login(
                request.getEmail(),
                request.getPassword());
        return ResponseEntity.ok(tokens);
    }

    /**
     * POST /validate — проверка валидности токена.
     * Возвращает { "valid": true/false }.
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestBody ValidateRequest request) {
        boolean valid = tokenService.validate(request.getToken());
        return ResponseEntity.ok(new ValidateResponse(valid));
    }

    /**
     * POST /refresh — обновление access-токена по refresh-токену.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@RequestBody RefreshRequest request) {
        Claims claims = tokenService.parseClaimsOrThrow(request.getRefreshToken());

        Long userId = Long.valueOf(claims.getSubject());
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new com.example.auth.exception.AuthException("Пользователь не найден"));

        String accessToken = tokenService.generateAccessToken(user);
        return ResponseEntity.ok(new AccessTokenResponse(accessToken));
    }

    /**
     * POST /logout — отзыв обоих токенов (помещение в чёрный список).
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody LogoutRequest request) {
        tokenService.blacklist(request.getAccessToken());
        tokenService.blacklist(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Выход выполнен"));
    }

    /**
     * POST /change-password — смена пароля.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                request.getUserId(),
                request.getCurrentPassword(),
                request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Пароль изменён"));
    }
}
