package com.example.ai_img_back.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Entity пользователя — отражение строки из таблицы users.
 *
 * В NestJS entity — это класс с декораторами @Entity(), @Column().
 * В Spring без JPA — просто POJO (Plain Old Java Object), обычный класс.
 * Маппинг из БД делается вручную в Repository через RowMapper.
 *
 * UUID — стандартный класс Java (java.util.UUID).
 * В отличие от Long, UUID — это 128-битный идентификатор вида
 * "550e8400-e29b-41d4-a716-446655440000".
 *
 * OffsetDateTime — дата+время+таймзона (соответствует TIMESTAMPTZ в PostgreSQL).
 * LocalDateTime НЕ хранит таймзону — это частая ошибка.
 */
@Getter
@Setter
@Accessors(chain = true)
public class User {

    private UUID id;
    private String email;
    private String displayName;
    private OffsetDateTime createdAt;
}