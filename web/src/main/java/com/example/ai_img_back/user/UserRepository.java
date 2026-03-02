package com.example.ai_img_back.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Репозиторий для таблицы users.
 *
 * Паттерн из эталона тимлида:
 * - static final RowMapper для маппинга строк
 * - NamedParameterJdbcTemplate для запросов с именованными параметрами
 * - Optional для методов поиска (вместо null)
 *
 * RequiredArgsConstructor (Lombok) — генерирует конструктор для ВСЕХ final-полей.
 * Заменяет ручной:
 *   public UserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
 *       this.jdbcTemplate = jdbcTemplate;
 *   }
 *
 * В NestJS аналог: constructor(private readonly repo: Repository<User>)
 * — DI тоже через конструктор, просто TypeScript-синтаксис короче.
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * RowMapper — функция (ResultSet, rowNum) → User.
     *
     * Вызывается для КАЖДОЙ строки результата SQL-запроса.
     * rs — это курсор по строке, из него читаем колонки по имени.
     *
     * static final — создаётся один раз на класс, переиспользуется.
     * Это паттерн тимлида: каждый Repository имеет свой *_MAPPER.
     *
     * UUID.fromString() — парсит строку "550e8400-..." в объект UUID.
     * rs.getObject("id", UUID.class) тоже работает с PostgreSQL JDBC,
     * но getString + fromString — надёжнее и очевиднее.
     *
     * rs.getObject("created_at", OffsetDateTime.class) —
     * PostgreSQL JDBC драйвер умеет маппить TIMESTAMPTZ напрямую в OffsetDateTime.
     * Это лучше, чем rs.getTimestamp().toLocalDateTime(), который теряет таймзону.
     */
    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User()
            .setId(UUID.fromString(rs.getString("id")))
            .setEmail(rs.getString("email"))
            .setDisplayName(rs.getString("display_name"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    /**
     * Создать пользователя. БД сама генерирует UUID.
     *
     * RETURNING * — PostgreSQL-фича: после INSERT возвращает вставленную строку.
     * Это удобнее, чем KeyHolder (который мы использовали раньше для BIGSERIAL).
     * С UUID нет auto-increment, поэтому KeyHolder не так удобен.
     *
     * jdbcTemplate.query() вместо .update() — потому что RETURNING возвращает строки.
     */
    public User create(String email, String displayName) {
        String sql = """
                INSERT INTO users (email, display_name)
                VALUES (:email, :displayName)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("displayName", displayName);

        // queryForObject — ожидает ровно 1 строку. Если 0 или 2+ → исключение.
        return jdbcTemplate.queryForObject(sql, params, USER_MAPPER);
    }

    /**
     * Найти по id.
     *
     * Возвращает Optional<User>:
     * - Optional.of(user) — если найден
     * - Optional.empty() — если нет
     *
     * Это безопаснее, чем возвращать null.
     * В TypeScript аналог: User | undefined, но Optional заставляет
     * вызывающий код ЯВНО обработать случай "не найдено".
     */
    public Optional<User> findById(UUID id) {
        String sql = """
                SELECT id, email, display_name, created_at
                FROM users
                WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<User> results = jdbcTemplate.query(sql, params, USER_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Получить всех пользователей.
     */
    public List<User> findAll() {
        String sql = """
                SELECT id, email, display_name, created_at
                FROM users
                ORDER BY created_at
                """;

        return jdbcTemplate.query(sql, USER_MAPPER);
    }

    /**
     * Удалить по id.
     */
    public void delete(UUID id) {
        String sql = """
                DELETE FROM users
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }
}
