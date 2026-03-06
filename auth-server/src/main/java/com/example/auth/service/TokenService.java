package com.example.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.example.auth.config.JwtProperties;
import com.example.auth.dto.TokenResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.AuthException;
import com.example.auth.repository.TokenBlacklistRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProperties jwtProperties;
    private final TokenBlacklistRepository blacklistRepository;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        signingKey = Keys.hmacShaKeyFor(jwtProperties.getSigningKey().getBytes());
    }

    /**
     * Генерирует пару access + refresh токенов.
     */
    public TokenResponse generatePair(AuthUser user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * Генерирует только access-токен (для refresh-сценария).
     */
    public String generateAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(jwtProperties.getAccessTokenTtlMinutes()));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .claim("roles", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    private String generateRefreshToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofDays(jwtProperties.getRefreshTokenTtlDays()));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Валидирует токен: подпись, срок, не в чёрном списке.
     * Возвращает true/false (для /validate эндпоинта).
     */
    public boolean validate(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.getId();
            return jti == null || !blacklistRepository.existsByJti(jti);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Парсит claims из токена. Бросает AuthException при невалидном токене.
     */
    public Claims parseClaimsOrThrow(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.getId();
            if (jti != null && blacklistRepository.existsByJti(jti)) {
                throw new AuthException("Токен отозван");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("Невалидный токен");
        }
    }

    /**
     * Помещает токен в чёрный список по jti.
     */
    public void blacklist(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.getId();
            if (jti != null) {
                blacklistRepository.insert(jti, claims.getExpiration().toInstant());
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Токен уже невалиден — ничего не делаем
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
