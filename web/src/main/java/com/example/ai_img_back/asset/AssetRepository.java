package com.example.ai_img_back.asset;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AssetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Asset> ASSET_MAPPER = (rs, rowNum) -> new Asset()
            .setId(rs.getLong("id"))
            .setImageTypeId(rs.getLong("image_type_id"))
            .setStyleId(rs.getLong("style_id"))
            .setFileUri(rs.getString("file_uri"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    public Asset create(Asset asset) {
        String sql = """
                INSERT INTO assets (image_type_id, style_id, file_uri)
                VALUES (:imageTypeId, :styleId, :fileUri)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("imageTypeId", asset.getImageTypeId())
                .addValue("styleId", asset.getStyleId())
                .addValue("fileUri", asset.getFileUri());

        return jdbcTemplate.queryForObject(sql, params, ASSET_MAPPER);
    }

    public List<Asset> findByTypeAndStyle(Long imageTypeId, Long styleId) {
        String sql = """
                SELECT * FROM assets
                WHERE image_type_id = :imageTypeId AND style_id = :styleId
                ORDER BY id DESC
                """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("imageTypeId", imageTypeId)
                .addValue("styleId", styleId), ASSET_MAPPER);
    }

    public List<Asset> findByImageTypeId(Long imageTypeId) {
        String sql = """
                SELECT * FROM assets
                WHERE image_type_id = :imageTypeId
                ORDER BY style_id, id DESC
                """;

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("imageTypeId", imageTypeId), ASSET_MAPPER);
    }

    /** Поиск дубликата по хешу (через generation_requests) */
    public boolean existsByHash(String finalPromptHash) {
        String sql = """
                SELECT COUNT(*) FROM generation_requests
                WHERE final_prompt_hash = :hash AND status = 'DONE'
                """;

        Integer count = jdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource("hash", finalPromptHash), Integer.class);
        return count != null && count > 0;
    }

    public Optional<Asset> findById(Long id) {
        String sql = "SELECT * FROM assets WHERE id = :id";

        List<Asset> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), ASSET_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
