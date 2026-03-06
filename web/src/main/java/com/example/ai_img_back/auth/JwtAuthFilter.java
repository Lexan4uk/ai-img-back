package com.example.ai_img_back.auth;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements HandlerInterceptor {

    private final JwtAuthProperties jwtProperties;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        signingKey = Keys.hmacShaKeyFor(jwtProperties.getSigningKey().getBytes());
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Отсутствует токен авторизации");
            return false;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            request.setAttribute("userId", userId);
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            sendUnauthorized(response, "Невалидный токен");
            return false;
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
