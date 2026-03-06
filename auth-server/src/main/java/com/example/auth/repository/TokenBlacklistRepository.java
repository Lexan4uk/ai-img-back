package com.example.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insert(String jti, Instant expiresAt) {
        String sql = """
                INSERT INTO token_blacklist (jti, expires_at)
                VALUES (:jti, :expiresAt)
                ON CONFLICT (jti) DO NOTHING
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("jti", jti)
                .addValue("expiresAt", Timestamp.from(expiresAt));

        jdbcTemplate.update(sql, params);
    }

    public boolean existsByJti(String jti) {
        String sql = """
                SELECT COUNT(*) FROM token_blacklist
                WHERE jti = :jti
                """;

        Integer count = jdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource("jti", jti), Integer.class);
        return count != null && count > 0;
    }

    public int deleteExpired() {
        String sql = """
                DELETE FROM token_blacklist
                WHERE expires_at < :now
                """;

        return jdbcTemplate.update(sql,
                new MapSqlParameterSource("now", Timestamp.from(Instant.now())));
    }
}
