package com.example.ai_img_back.auth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RestController
@RequestMapping("/auth")
public class AuthProxyController {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String SERVICE_UNAVAILABLE_BODY = "{\"error\":\"Сервер авторизации недоступен\"}";

    private final OkHttpClient httpClient;
    private final AuthServerProperties properties;

    public AuthProxyController(@Qualifier("authHttpClient") OkHttpClient httpClient,
                               AuthServerProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/register", body);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/login", body);
    }

    @PostMapping("/validate")
    public ResponseEntity<String> validate(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/validate", body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/refresh", body);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/logout", body);
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@org.springframework.web.bind.annotation.RequestBody String body) {
        return proxy("/change-password", body);
    }

    /**
     * Проксирует POST-запрос к auth-серверу.
     * Передаёт raw JSON, пробрасывает HTTP-статус как есть.
     * При недоступности auth-сервера → 503.
     */
    private ResponseEntity<String> proxy(String path, String jsonBody) {
        Request request = new Request.Builder()
                .url(properties.getUrl() + path)
                .post(okhttp3.RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            return ResponseEntity
                    .status(response.code())
                    .header("Content-Type", "application/json")
                    .body(responseBody);
        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json")
                    .body(SERVICE_UNAVAILABLE_BODY);
        }
    }
}
