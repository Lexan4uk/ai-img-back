package com.example.ai_img_back.user;

import lombok.Data;

/**
 * Тело запроса на создание/обновление пользователя.
 * Клиент отправляет JSON: {"email": "ivan@mail.ru", "displayName": "Ivan"}
 *
 * Аналог CreateUserDto в NestJS.
 *
 * Почему displayName, а не display_name?
 * - В Java конвенция — camelCase для полей
 * - Spring (Jackson) автоматически маппит JSON "displayName" → поле displayName
 * - Если клиент шлёт snake_case, можно настроить Jackson глобально
 */
@Data
public class UserRequest {
    private String email;
    private String displayName;
}
