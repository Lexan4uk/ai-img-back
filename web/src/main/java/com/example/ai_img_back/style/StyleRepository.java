package com.example.ai_img_back.style;

import static com.example.ai_img_back.util.RowMapperUtils.parseNullableUuid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StyleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Style> STYLE_MAPPER = (rs, rowNum) -> new Style()
            .setId(UUID.fromString(rs.getString("id")))
            .setCreatedByUserId(parseNullableUuid(rs.getString("created_by_user_id")))
            .setName(rs.getString("name"))
            .setStylePrompt(rs.getString("style_prompt"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    public Style create(UUID createdByUserId, String name, String stylePrompt) {
        String sql = """
                INSERT INTO styles (created_by_user_id, name, style_prompt)
                VALUES (:createdByUserId, :name, :stylePrompt)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("createdByUserId", createdByUserId)
                .addValue("name", name)
                .addValue("stylePrompt", stylePrompt);

        return jdbcTemplate.queryForObject(sql, params, STYLE_MAPPER);
    }

    public Optional<Style> findById(UUID id) {
        String sql = """
                SELECT id, created_by_user_id, name, style_prompt, created_at
                FROM styles
                WHERE id = :id
                """;

        List<Style> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), STYLE_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Style> findAll() {
        String sql = """
                SELECT id, created_by_user_id, name, style_prompt, created_at
                FROM styles
                ORDER BY name
                """;

        return jdbcTemplate.query(sql, STYLE_MAPPER);
    }

    public void delete(UUID id) {
        String sql = """
                DELETE FROM styles
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    /** TODO: раскомментить вызов в сервисе когда появится таблица assets */
    public void reassignAssets(UUID fromStyleId, UUID toStyleId) {
        String sql = """
                UPDATE assets
                SET style_id = :toStyleId
                WHERE style_id = :fromStyleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromStyleId", fromStyleId)
                .addValue("toStyleId", toStyleId));
    }

    /** TODO: раскомментить вызов в сервисе когда появится таблица generation_requests */
    public void reassignGenerationRequests(UUID fromStyleId, UUID toStyleId) {
        String sql = """
                UPDATE generation_requests
                SET style_id = :toStyleId
                WHERE style_id = :fromStyleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromStyleId", fromStyleId)
                .addValue("toStyleId", toStyleId));
    }
		public void addFavorite(UUID userId, UUID styleId) {
        String sql = """
                INSERT INTO user_favorite_styles (user_id, style_id)
                VALUES (:userId, :styleId)
                ON CONFLICT DO NOTHING
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("styleId", styleId));
    }

    public void removeFavorite(UUID userId, UUID styleId) {
        String sql = """
                DELETE FROM user_favorite_styles
                WHERE user_id = :userId AND style_id = :styleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("styleId", styleId));
    }

    public List<UUID> findFavoriteIdsByUserId(UUID userId) {
        String sql = """
                SELECT style_id
                FROM user_favorite_styles
                WHERE user_id = :userId
                """;
        return jdbcTemplate.queryForList(sql,
            new MapSqlParameterSource("userId", userId), UUID.class);
   }


}
