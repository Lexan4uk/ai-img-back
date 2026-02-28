package com.example.ai_img_back.util;

import java.util.UUID;

/**
 * Утилиты для RowMapper-ов.
 *
 * Статический класс с хелперами для маппинга ResultSet → Entity.
 * Используется во всех Repository.
 *
 * Приватный конструктор — запрещает создание экземпляров.
 * Это паттерн "utility class" в Java. В TypeScript
 * такой проблемы нет — ты просто экспортируешь функции.
 * В Java функция не может существовать без класса,
 * поэтому оборачиваем в класс со static-методами.
 */
public final class RowMapperUtils {

    private RowMapperUtils() {
        // Запретить создание экземпляров — все методы static
    }

    /**
     * Парсинг nullable UUID из строки ResultSet.
     * rs.getString() для NULL-колонки возвращает Java null.
     * UUID.fromString(null) бросит NullPointerException.
     * Этот метод безопасно возвращает null.
     */
    public static UUID parseNullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
