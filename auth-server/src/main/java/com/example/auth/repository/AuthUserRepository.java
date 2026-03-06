package com.example.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.auth.entity.AuthUser;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AuthUserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<AuthUser> USER_MAPPER = (rs, rowNum) -> new AuthUser()
            .setId(rs.getLong("id"))
            .setEmail(rs.getString("email"))
            .setDisplayName(rs.getString("display_name"))
            .setPasswordHash(rs.getString("password_hash"))
            .setRole(rs.getString("role"));

    public AuthUser create(String email, String displayName, String passwordHash) {
        String sql = """
                INSERT INTO auth_users (email, display_name, password_hash)
                VALUES (:email, :displayName, :passwordHash)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("displayName", displayName)
                .addValue("passwordHash", passwordHash);

        return jdbcTemplate.queryForObject(sql, params, USER_MAPPER);
    }

    public Optional<AuthUser> findByEmail(String email) {
        String sql = """
                SELECT id, email, display_name, password_hash, role
                FROM auth_users
                WHERE email = :email
                """;

        List<AuthUser> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("email", email), USER_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<AuthUser> findById(Long id) {
        String sql = """
                SELECT id, email, display_name, password_hash, role
                FROM auth_users
                WHERE id = :id
                """;

        List<AuthUser> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), USER_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void updatePasswordHash(Long id, String passwordHash) {
        String sql = """
                UPDATE auth_users
                SET password_hash = :passwordHash
                WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("passwordHash", passwordHash);

        jdbcTemplate.update(sql, params);
    }
}
