package com.example.ai_img_back.imagetype;

import static com.example.ai_img_back.util.RowMapperUtils.parseNullableUuid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;


/**
 * Репозиторий для таблицы image_types.
 *
 * Новый паттерн: nullable UUID в RowMapper.
 * В UserRepository все поля были non-null (кроме email, но он String).
 * Здесь created_by_user_id может быть NULL (если автор удалён).
 *
 * rs.getString("col") для NULL-колонки вернёт Java null.
 * UUID.fromString(null) бросит NullPointerException.
 * Поэтому нужна обёртка: parseNullableUuid().
 *
 * В NestJS/TypeORM ты бы просто написал:
 *   @Column({ type: 'uuid', nullable: true })
 *   createdByUserId: string | null;
 * и ORM сделал бы маппинг сам. Тут — руками.
 */
@Repository
@RequiredArgsConstructor
public class ImageTypeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Хелпер для парсинга nullable UUID из ResultSet.
     * Если значение NULL → вернёт null, а не бросит NPE.
     *
     * Вынесен в статический метод — будет переиспользоваться
     * в других Repository (Style, Asset и т.д.).
     * Позже можно вынести в общий утилитный класс.
     */


    private static final RowMapper<ImageType> IMAGE_TYPE_MAPPER = (rs, rowNum) -> new ImageType()
            .setId(UUID.fromString(rs.getString("id")))
            .setCreatedByUserId(parseNullableUuid(rs.getString("created_by_user_id")))
            .setName(rs.getString("name"))
            .setTypePrompt(rs.getString("type_prompt"))
            .setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));

    /**
     * Создать тип. БД генерирует UUID.
     * created_by_user_id — UUID текущего пользователя.
     */
    public ImageType create(UUID createdByUserId, String name, String typePrompt) {
        String sql = """
                INSERT INTO image_types (created_by_user_id, name, type_prompt)
                VALUES (:createdByUserId, :name, :typePrompt)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("createdByUserId", createdByUserId)
                .addValue("name", name)
                .addValue("typePrompt", typePrompt);

        return jdbcTemplate.queryForObject(sql, params, IMAGE_TYPE_MAPPER);
    }

    public Optional<ImageType> findById(UUID id) {
        String sql = """
                SELECT id, created_by_user_id, name, type_prompt, created_at
                FROM image_types
                WHERE id = :id
                """;

        List<ImageType> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", id), IMAGE_TYPE_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /** Все типы — для галереи и выбора при генерации */
    public List<ImageType> findAll() {
        String sql = """
                SELECT id, created_by_user_id, name, type_prompt, created_at
                FROM image_types
                ORDER BY name
                """;

        return jdbcTemplate.query(sql, IMAGE_TYPE_MAPPER);
    }

    /**
     * Жёсткое удаление.
     * ПЕРЕД вызовом этого метода сервис должен переназначить
     * все связанные assets и generation_requests на UNDEFINED_TYPE_ID.
     */
    public void delete(UUID id) {
        String sql = """
                DELETE FROM image_types
                WHERE id = :id
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    /**
     * Переназначить все assets с данным типом на "Неопределённый".
     * Вызывается перед удалением типа.
     *
     * Пока таблицы assets нет — метод подготовлен заранее,
     * при запуске не упадёт (UPDATE 0 rows — это ОК).
     */
    public void reassignAssets(UUID fromTypeId, UUID toTypeId) {
        String sql = """
                UPDATE assets
                SET image_type_id = :toTypeId
                WHERE image_type_id = :fromTypeId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTypeId", fromTypeId)
                .addValue("toTypeId", toTypeId);

        jdbcTemplate.update(sql, params);
    }

    /**
     * Переназначить generation_requests — аналогично assets.
     */
    public void reassignGenerationRequests(UUID fromTypeId, UUID toTypeId) {
        String sql = """
                UPDATE generation_requests
                SET image_type_id = :toTypeId
                WHERE image_type_id = :fromTypeId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTypeId", fromTypeId)
                .addValue("toTypeId", toTypeId);

        jdbcTemplate.update(sql, params);
    }

        /**
     * Добавить тип в избранное пользователя.
     *
     * ON CONFLICT DO NOTHING — если уже в избранном, не падаем с ошибкой.
     * Это идемпотентная операция: вызвал 1 раз или 5 — результат одинаковый.
     * В REST это правильное поведение для PUT/POST избранного.
     */
    public void addFavorite(UUID userId, UUID imageTypeId) {
        String sql = """
                INSERT INTO user_favorite_types (user_id, image_type_id)
                VALUES (:userId, :imageTypeId)
                ON CONFLICT DO NOTHING
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("imageTypeId", imageTypeId));
    }

    /** Убрать из избранного */
    public void removeFavorite(UUID userId, UUID imageTypeId) {
        String sql = """
                DELETE FROM user_favorite_types
                WHERE user_id = :userId AND image_type_id = :imageTypeId
                """;

        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("imageTypeId", imageTypeId));
    }

    /**
     * Все избранные типы пользователя.
     *
     * JOIN — объединяет две таблицы по условию.
     * Тут: берём id из user_favorite_types и подтягиваем
     * полную запись из image_types.
     *
     * В TypeORM это было бы:
     *   user.favoriteTypes (через @ManyToMany relation)
     * Тут — ручной JOIN.
     */
    public List<ImageType> findFavoritesByUserId(UUID userId) {
        String sql = """
                SELECT it.id, it.created_by_user_id, it.name, it.type_prompt, it.created_at
                FROM image_types it
                JOIN user_favorite_types uft ON uft.image_type_id = it.id
                WHERE uft.user_id = :userId
                ORDER BY it.name
                """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("userId", userId),
                IMAGE_TYPE_MAPPER);
    }

}
