package com.example.ai_img_back.prompt;

import static com.example.ai_img_back.util.RowMapperUtils.parseNullableUuid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Репозиторий промптов.
 *
 * Новый паттерн: работа с JSONB.
 *
 * PostgreSQL JSONB ↔ Java String:
 * - При чтении: rs.getString("generation_params") возвращает JSON-строку
 * - При записи: нужен PGobject с type="jsonb", иначе PostgreSQL
 *   не поймёт что это JSON и бросит ошибку типов.
 *
 * В NestJS/TypeORM @Column({ type: 'jsonb' }) делает это автоматически.
 * Тут — руками через createJsonbParam().
 */
@Repository
@RequiredArgsConstructor
public class PromptRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Prompt> PROMPT_MAPPER = (rs, rowNum) -> new Prompt()
            .setId(UUID.fromString(rs.getString("id")))
            .setOwnerUserId(parseNullableUuid(rs.getString("owner_user_id")))
            .setText(rs.getString("text"))
            .setGenerationParams(rs.getString("generation_params"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    public Prompt create(UUID ownerUserId, String text, String generationParams) {
        // CAST(:generationParams AS jsonb) — PostgreSQL сам преобразует строку в JSONB.
        String sql = """
                INSERT INTO prompts (owner_user_id, text, generation_params)
                VALUES (:ownerUserId, :text, CAST(:generationParams AS jsonb))
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("text", text)
                .addValue("generationParams", generationParams == null ? "{}" : generationParams);

        return jdbcTemplate.queryForObject(sql, params, PROMPT_MAPPER);
    }


    public Optional<Prompt> findById(UUID id) {
        String sql = """
                SELECT id, owner_user_id, text, generation_params, created_at
                FROM prompts
                WHERE id = :id
                """;

        List<Prompt> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), PROMPT_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
