package com.example.ai_img_back.generation;

import static com.example.ai_img_back.util.RowMapperUtils.parseNullableUuid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.ai_img_back.clientutils.enums.BatchStatus;
import com.example.ai_img_back.clientutils.enums.RoutingMode;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GenerationBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * enum из строки:
     *   BatchStatus.valueOf("RUNNING") → BatchStatus.RUNNING
     *   RoutingMode.valueOf("DIRECT") → RoutingMode.DIRECT
     *
     * Если в БД лежит невалидная строка → IllegalArgumentException.
     */
    private static final RowMapper<GenerationBatch> BATCH_MAPPER = (rs, rowNum) -> new GenerationBatch()
            .setId(UUID.fromString(rs.getString("id")))
            .setOwnerUserId(parseNullableUuid(rs.getString("owner_user_id")))
            .setProvider(rs.getString("provider"))
            .setModel(rs.getString("model"))
            .setRoutingMode(RoutingMode.valueOf(rs.getString("routing_mode")))
            .setStatus(BatchStatus.valueOf(rs.getString("status")))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
            .setStartedAt(rs.getObject("started_at", java.time.OffsetDateTime.class))
            .setFinishedAt(rs.getObject("finished_at", java.time.OffsetDateTime.class));

    /**
     * Создать batch. Enum сохраняется как строка через .name():
     *   BatchStatus.RUNNING.name() → "RUNNING"
     */
    public GenerationBatch create(UUID ownerUserId, String provider, String model,
                                   RoutingMode routingMode, BatchStatus status) {
        String sql = """
                INSERT INTO generation_batches (owner_user_id, provider, model, routing_mode, status, started_at)
                VALUES (:ownerUserId, :provider, :model, :routingMode, :status, NOW())
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("provider", provider)
                .addValue("model", model)
                .addValue("routingMode", routingMode.name())
                .addValue("status", status.name());

        return jdbcTemplate.queryForObject(sql, params, BATCH_MAPPER);
    }

    public Optional<GenerationBatch> findById(UUID id) {
        String sql = "SELECT * FROM generation_batches WHERE id = :id";
        List<GenerationBatch> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), BATCH_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /** Обновить статус и время завершения */
    public void updateStatus(UUID id, BatchStatus status) {
        String sql = """
                UPDATE generation_batches
                SET status = :status, finished_at = NOW()
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name()));
    }
}
