package com.example.ai_img_back.asset;

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
public class AssetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Asset> ASSET_MAPPER = (rs, rowNum) -> new Asset()
            .setId(UUID.fromString(rs.getString("id")))
            .setOwnerUserId(parseNullableUuid(rs.getString("owner_user_id")))
            .setImageTypeId(UUID.fromString(rs.getString("image_type_id")))
            .setStyleId(UUID.fromString(rs.getString("style_id")))
            .setPromptId(UUID.fromString(rs.getString("prompt_id")))
            .setUserPromptSnapshot(rs.getString("user_prompt_snapshot"))
            .setTypePromptSnapshot(rs.getString("type_prompt_snapshot"))
            .setStylePromptSnapshot(rs.getString("style_prompt_snapshot"))
            .setFinalPromptSnapshot(rs.getString("final_prompt_snapshot"))
            .setFinalPromptHash(rs.getString("final_prompt_hash"))
            .setFileUri(rs.getString("file_uri"))
            .setProvider(rs.getString("provider"))
            .setModel(rs.getString("model"))
            .setMeta(rs.getString("meta"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    /**
     * Создать asset. Вызывается из GenerationService после успешной генерации.
     * Много параметров — это нормально для INSERT с кучей колонок.
     */
    public Asset create(Asset asset) {
        String sql = """
                INSERT INTO assets (
                    owner_user_id, image_type_id, style_id, prompt_id,
                    user_prompt_snapshot, type_prompt_snapshot, style_prompt_snapshot,
                    final_prompt_snapshot, final_prompt_hash,
                    file_uri, provider, model, meta
                ) VALUES (
                    :ownerUserId, :imageTypeId, :styleId, :promptId,
                    :userPromptSnapshot, :typePromptSnapshot, :stylePromptSnapshot,
                    :finalPromptSnapshot, :finalPromptHash,
                    :fileUri, :provider, :model, :meta
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", asset.getOwnerUserId())
                .addValue("imageTypeId", asset.getImageTypeId())
                .addValue("styleId", asset.getStyleId())
                .addValue("promptId", asset.getPromptId())
                .addValue("userPromptSnapshot", asset.getUserPromptSnapshot())
                .addValue("typePromptSnapshot", asset.getTypePromptSnapshot())
                .addValue("stylePromptSnapshot", asset.getStylePromptSnapshot())
                .addValue("finalPromptSnapshot", asset.getFinalPromptSnapshot())
                .addValue("finalPromptHash", asset.getFinalPromptHash())
                .addValue("fileUri", asset.getFileUri())
                .addValue("provider", asset.getProvider())
                .addValue("model", asset.getModel())
                .addValue("meta", asset.getMeta());

        return jdbcTemplate.queryForObject(sql, params, ASSET_MAPPER);
    }

    /**
     * Галерея: все картинки по (тип + стиль), от всех пользователей.
     * Использует индекс ix_assets_type_style_created.
     */
    public List<Asset> findByTypeAndStyle(UUID imageTypeId, UUID styleId) {
        String sql = """
                SELECT *
                FROM assets
                WHERE image_type_id = :imageTypeId AND style_id = :styleId
                ORDER BY created_at DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("imageTypeId", imageTypeId)
                .addValue("styleId", styleId);

        return jdbcTemplate.query(sql, params, ASSET_MAPPER);
    }
    /**
     * Все ассеты по типу (все стили). Для экрана галереи конкретного типа.
     * Сортировка: сначала по style_id (группировка), потом по дате убывания.
     */
    public List<Asset> findByImageTypeId(UUID imageTypeId) {
        String sql = """
                SELECT *
                FROM assets
                WHERE image_type_id = :imageTypeId
                ORDER BY style_id, created_at DESC
                """;

        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("imageTypeId", imageTypeId), ASSET_MAPPER);
    }
    /**
     * Поиск дубликата: по пользователю + хешу.
     * Возвращает первый найденный (для проверки "существует ли").
     */
    public Optional<Asset> findByOwnerAndHash(UUID ownerUserId, String finalPromptHash) {
        String sql = """
                SELECT *
                FROM assets
                WHERE owner_user_id = :ownerUserId AND final_prompt_hash = :hash
                LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("hash", finalPromptHash);

        List<Asset> results = jdbcTemplate.query(sql, params, ASSET_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<Asset> findById(UUID id) {
        String sql = """
                SELECT *
                FROM assets
                WHERE id = :id
                """;

        List<Asset> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), ASSET_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
