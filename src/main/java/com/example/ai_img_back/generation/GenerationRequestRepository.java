package com.example.ai_img_back.generation;

import static com.example.ai_img_back.util.RowMapperUtils.parseNullableUuid;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.ai_img_back.generation.enums.DedupeMode;
import com.example.ai_img_back.generation.enums.DedupeResult;
import com.example.ai_img_back.generation.enums.RequestStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GenerationRequestRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Nullable enum: dedupeResult может быть заполнен позже.
     * Но по схеме он NOT NULL и заполняется при создании.
     *
     * Nullable UUID: createdAssetId — заполняется только при DONE.
     */
    private static final RowMapper<GenerationRequest> REQUEST_MAPPER = (rs, rowNum) -> new GenerationRequest()
            .setId(UUID.fromString(rs.getString("id")))
            .setBatchId(UUID.fromString(rs.getString("batch_id")))
            .setOwnerUserId(parseNullableUuid(rs.getString("owner_user_id")))
            .setImageTypeId(UUID.fromString(rs.getString("image_type_id")))
            .setStyleId(UUID.fromString(rs.getString("style_id")))
            .setPromptId(UUID.fromString(rs.getString("prompt_id")))
            .setUserPromptSnapshot(rs.getString("user_prompt_snapshot"))
            .setFinalPromptSnapshot(rs.getString("final_prompt_snapshot"))
            .setFinalPromptHash(rs.getString("final_prompt_hash"))
            .setDedupeResult(DedupeResult.valueOf(rs.getString("dedupe_result")))
            .setDedupeMode(DedupeMode.valueOf(rs.getString("dedupe_mode")))
            .setStatus(RequestStatus.valueOf(rs.getString("status")))
            .setCreatedAssetId(parseNullableUuid(rs.getString("created_asset_id")))
            .setErrorMessage(rs.getString("error_message"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .setStartedAt(rs.getObject("started_at", java.time.OffsetDateTime.class))
            .setFinishedAt(rs.getObject("finished_at", java.time.OffsetDateTime.class));

    /** Создать request (статус PENDING, dedupeResult заполняется сразу) */
    public GenerationRequest create(UUID batchId, UUID ownerUserId, UUID imageTypeId,
                                     UUID styleId, UUID promptId,
                                     String userPromptSnapshot, String finalPromptSnapshot,
                                     String finalPromptHash, DedupeResult dedupeResult,
                                     DedupeMode dedupeMode) {
        String sql = """
                INSERT INTO generation_requests (
                    batch_id, owner_user_id, image_type_id, style_id, prompt_id,
                    user_prompt_snapshot, final_prompt_snapshot, final_prompt_hash,
                    dedupe_result, dedupe_mode, status
                ) VALUES (
                    :batchId, :ownerUserId, :imageTypeId, :styleId, :promptId,
                    :userPromptSnapshot, :finalPromptSnapshot, :finalPromptHash,
                    :dedupeResult, :dedupeMode, :status
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("ownerUserId", ownerUserId)
                .addValue("imageTypeId", imageTypeId)
                .addValue("styleId", styleId)
                .addValue("promptId", promptId)
                .addValue("userPromptSnapshot", userPromptSnapshot)
                .addValue("finalPromptSnapshot", finalPromptSnapshot)
                .addValue("finalPromptHash", finalPromptHash)
                .addValue("dedupeResult", dedupeResult.name())
                .addValue("dedupeMode", dedupeMode.name())
                .addValue("status", RequestStatus.PENDING.name());

        return jdbcTemplate.queryForObject(sql, params, REQUEST_MAPPER);
    }

    /** Все request-ы в batch (для последовательной обработки) */
    public List<GenerationRequest> findByBatchId(UUID batchId) {
        String sql = "SELECT * FROM generation_requests WHERE batch_id = :batchId ORDER BY created_at";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("batchId", batchId), REQUEST_MAPPER);
    }

    /** Пометить как RUNNING */
    public void markRunning(UUID id) {
        String sql = "UPDATE generation_requests SET status = 'RUNNING', started_at = NOW() WHERE id = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    /** Пометить как DONE + привязать asset */
    public void markDone(UUID id, UUID createdAssetId) {
        String sql = """
                UPDATE generation_requests
                SET status = 'DONE', created_asset_id = :assetId, finished_at = NOW()
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("assetId", createdAssetId));
    }

    /** Пометить как FAILED + записать ошибку */
    public void markFailed(UUID id, String errorMessage) {
        String sql = """
                UPDATE generation_requests
                SET status = 'FAILED', error_message = :errorMessage, finished_at = NOW()
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("errorMessage", errorMessage));
    }

    /** Пометить как SKIPPED (дубликат + SKIP mode) */
    public void markSkipped(UUID id) {
        String sql = "UPDATE generation_requests SET status = 'SKIPPED', finished_at = NOW() WHERE id = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }
}
