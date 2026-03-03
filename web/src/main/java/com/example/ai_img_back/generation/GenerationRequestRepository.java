package com.example.ai_img_back.generation;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.ai_img_back.clientutils.enums.RequestStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GenerationRequestRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<GenerationRequest> REQUEST_MAPPER = (rs, rowNum) -> new GenerationRequest()
            .setId(rs.getLong("id"))
            .setBatchId(rs.getLong("batch_id"))
            .setImageTypeId(rs.getLong("image_type_id"))
            .setStyleId(rs.getLong("style_id"))
            .setFinalPromptHash(rs.getString("final_prompt_hash"))
            .setStatus(RequestStatus.valueOf(rs.getString("status")))
            .setCreatedAssetId(rs.getObject("created_asset_id", Long.class))
            .setErrorMessage(rs.getString("error_message"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    public GenerationRequest create(Long batchId, Long imageTypeId, Long styleId,
                                     String finalPromptHash) {
        String sql = """
                INSERT INTO generation_requests (batch_id, image_type_id, style_id, final_prompt_hash)
                VALUES (:batchId, :imageTypeId, :styleId, :finalPromptHash)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("imageTypeId", imageTypeId)
                .addValue("styleId", styleId)
                .addValue("finalPromptHash", finalPromptHash);

        return jdbcTemplate.queryForObject(sql, params, REQUEST_MAPPER);
    }

    public List<GenerationRequest> findByBatchId(Long batchId) {
        String sql = "SELECT * FROM generation_requests WHERE batch_id = :batchId ORDER BY created_at";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("batchId", batchId), REQUEST_MAPPER);
    }

    public void markRunning(Long id) {
        String sql = "UPDATE generation_requests SET status = 'RUNNING' WHERE id = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public void markDone(Long id, Long createdAssetId) {
        String sql = """
                UPDATE generation_requests
                SET status = 'DONE', created_asset_id = :assetId
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("assetId", createdAssetId));
    }

    public void markFailed(Long id, String errorMessage) {
        String sql = """
                UPDATE generation_requests
                SET status = 'FAILED', error_message = :errorMessage
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("errorMessage", errorMessage));
    }

    public void markSkipped(Long id) {
        String sql = "UPDATE generation_requests SET status = 'SKIPPED' WHERE id = :id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }
}
