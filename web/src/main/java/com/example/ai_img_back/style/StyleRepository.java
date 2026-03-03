package com.example.ai_img_back.style;

import java.util.List;
import java.util.Optional;

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
            .setId(rs.getLong("id"))
            .setCreatedByUserId(rs.getObject("created_by_user_id", Long.class))
            .setName(rs.getString("name"))
            .setStylePrompt(rs.getString("style_prompt"));

    public Style create(Long createdByUserId, String name, String stylePrompt) {
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

    public Optional<Style> findById(Long id) {
        String sql = """
                SELECT id, created_by_user_id, name, style_prompt
                FROM styles
                WHERE id = :id
                """;

        List<Style> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), STYLE_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Style> findAll() {
        String sql = """
                SELECT id, created_by_user_id, name, style_prompt
                FROM styles
                ORDER BY name
                """;

        return jdbcTemplate.query(sql, STYLE_MAPPER);
    }

    public void delete(Long id) {
        String sql = """
                DELETE FROM styles
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public void reassignAssets(Long fromStyleId, Long toStyleId) {
        String sql = """
                UPDATE assets
                SET style_id = :toStyleId
                WHERE style_id = :fromStyleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromStyleId", fromStyleId)
                .addValue("toStyleId", toStyleId));
    }

    public void reassignGenerationRequests(Long fromStyleId, Long toStyleId) {
        String sql = """
                UPDATE generation_requests
                SET style_id = :toStyleId
                WHERE style_id = :fromStyleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromStyleId", fromStyleId)
                .addValue("toStyleId", toStyleId));
    }

    public void addFavorite(Long userId, Long styleId) {
        String sql = """
                INSERT INTO user_favorite_styles (user_id, style_id)
                VALUES (:userId, :styleId)
                ON CONFLICT DO NOTHING
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("styleId", styleId));
    }

    public void removeFavorite(Long userId, Long styleId) {
        String sql = """
                DELETE FROM user_favorite_styles
                WHERE user_id = :userId AND style_id = :styleId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("styleId", styleId));
    }

    public List<Long> findFavoriteIdsByUserId(Long userId) {
        String sql = """
                SELECT style_id
                FROM user_favorite_styles
                WHERE user_id = :userId
                """;

        return jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("userId", userId), Long.class);
    }
}
