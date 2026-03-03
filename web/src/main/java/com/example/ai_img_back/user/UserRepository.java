package com.example.ai_img_back.user;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User()
            .setId(rs.getLong("id"))
            .setEmail(rs.getString("email"))
            .setDisplayName(rs.getString("display_name"));

    public User create(String email, String displayName) {
        String sql = """
                INSERT INTO users (email, display_name)
                VALUES (:email, :displayName)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("displayName", displayName);

        return jdbcTemplate.queryForObject(sql, params, USER_MAPPER);
    }

    public Optional<User> findById(Long id) {
        String sql = """
                SELECT id, email, display_name
                FROM users
                WHERE id = :id
                """;

        List<User> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), USER_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<User> findAll() {
        String sql = """
                SELECT id, email, display_name
                FROM users
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, USER_MAPPER);
    }

    public void delete(Long id) {
        String sql = """
                DELETE FROM users
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }
}
