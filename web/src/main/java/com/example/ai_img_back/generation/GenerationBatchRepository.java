package com.example.ai_img_back.generation;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.ai_img_back.clientutils.enums.RoutingMode;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GenerationBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<GenerationBatch> BATCH_MAPPER = (rs, rowNum) -> new GenerationBatch()
            .setId(rs.getLong("id"))
            .setOwnerUserId(rs.getObject("owner_user_id", Long.class))
            .setUserPrompt(rs.getString("user_prompt"))
            .setGenerationParams(rs.getString("generation_params"))
            .setOverwriteDuplicates(rs.getBoolean("overwrite_duplicates"))
            .setProvider(rs.getString("provider"))
            .setModel(rs.getString("model"))
            .setRoutingMode(RoutingMode.valueOf(rs.getString("routing_mode")));

    public GenerationBatch create(Long ownerUserId, String userPrompt, String generationParams,
                                   boolean overwriteDuplicates, String provider, String model,
                                   RoutingMode routingMode) {
        String sql = """
                INSERT INTO generation_batches (
                    owner_user_id, user_prompt, generation_params,
                    overwrite_duplicates, provider, model, routing_mode
                ) VALUES (
                    :ownerUserId, :userPrompt, CAST(:generationParams AS jsonb),
                    :overwriteDuplicates, :provider, :model, :routingMode
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerUserId", ownerUserId)
                .addValue("userPrompt", userPrompt)
                .addValue("generationParams", generationParams)
                .addValue("overwriteDuplicates", overwriteDuplicates)
                .addValue("provider", provider)
                .addValue("model", model)
                .addValue("routingMode", routingMode.name());

        return jdbcTemplate.queryForObject(sql, params, BATCH_MAPPER);
    }

    public Optional<GenerationBatch> findById(Long id) {
        String sql = "SELECT * FROM generation_batches WHERE id = :id";
        List<GenerationBatch> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), BATCH_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
