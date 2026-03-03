package com.example.ai_img_back.imagetype;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ImageTypeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<ImageType> IMAGE_TYPE_MAPPER = (rs, rowNum) -> new ImageType()
            .setId(rs.getLong("id"))
            .setCreatedByUserId(rs.getObject("created_by_user_id", Long.class))
            .setName(rs.getString("name"))
            .setTypePrompt(rs.getString("type_prompt"));

    public ImageType create(Long createdByUserId, String name, String typePrompt) {
        String sql = """
                INSERT INTO image_types (created_by_user_id, name, type_prompt)
                VALUES (:createdByUserId, :name, :typePrompt)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("createdByUserId", createdByUserId)
                .addValue("name", name)
                .addValue("typePrompt", typePrompt);

        return jdbcTemplate.queryForObject(sql, params, IMAGE_TYPE_MAPPER);
    }

    public Optional<ImageType> findById(Long id) {
        String sql = """
                SELECT id, created_by_user_id, name, type_prompt
                FROM image_types
                WHERE id = :id
                """;

        List<ImageType> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), IMAGE_TYPE_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<ImageType> findAll() {
        String sql = """
                SELECT id, created_by_user_id, name, type_prompt
                FROM image_types
                ORDER BY name
                """;

        return jdbcTemplate.query(sql, IMAGE_TYPE_MAPPER);
    }

    public void delete(Long id) {
        String sql = """
                DELETE FROM image_types
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public void reassignAssets(Long fromTypeId, Long toTypeId) {
        String sql = """
                UPDATE assets
                SET image_type_id = :toTypeId
                WHERE image_type_id = :fromTypeId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromTypeId", fromTypeId)
                .addValue("toTypeId", toTypeId));
    }

    public void reassignGenerationRequests(Long fromTypeId, Long toTypeId) {
        String sql = """
                UPDATE generation_requests
                SET image_type_id = :toTypeId
                WHERE image_type_id = :fromTypeId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fromTypeId", fromTypeId)
                .addValue("toTypeId", toTypeId));
    }

    public void addFavorite(Long userId, Long imageTypeId) {
        String sql = """
                INSERT INTO user_favorite_types (user_id, image_type_id)
                VALUES (:userId, :imageTypeId)
                ON CONFLICT DO NOTHING
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("imageTypeId", imageTypeId));
    }

    public void removeFavorite(Long userId, Long imageTypeId) {
        String sql = """
                DELETE FROM user_favorite_types
                WHERE user_id = :userId AND image_type_id = :imageTypeId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("imageTypeId", imageTypeId));
    }

    public List<Long> findFavoriteIdsByUserId(Long userId) {
        String sql = """
                SELECT image_type_id
                FROM user_favorite_types
                WHERE user_id = :userId
                """;

        return jdbcTemplate.queryForList(sql,
                new MapSqlParameterSource("userId", userId), Long.class);
    }
}
