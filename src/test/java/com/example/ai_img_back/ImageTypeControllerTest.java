package com.example.ai_img_back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.ai_img_back.imagetype.ImageTypeDTO;
import com.example.ai_img_back.imagetype.ImageTypeRequest;
import com.example.ai_img_back.user.UserDTO;
import com.example.ai_img_back.user.UserRequest;

/**
 * Интеграционные тесты ImageTypeController.
 *
 * Особенности по сравнению с UserControllerTest:
 *
 * 1. Header "UserId" — нужен для create, delete, favorites.
 *    mvc.perform(post(...).header("UserId", userId.toString()))
 *
 * 2. Seed-данные — "Неопределённый" тип уже есть в БД
 *    (создаётся миграцией changeset-2). На него можно полагаться.
 *
 * 3. Бизнес-правила удаления:
 *    - Нельзя удалить "Неопределённый" → 403
 *    - Нельзя удалить чужой тип → 403
 *    - Только создатель может удалить → 200
 */
public class ImageTypeControllerTest extends BaseTest {

    /** UUID "Неопределённого" типа из changeset-2 */
    private static final UUID UNDEFINED_TYPE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    /** Создать пользователя (нужен для UserId header) */
    private UserDTO createUser() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail(UUID.randomUUID() + "@test.ru");
        req.setDisplayName("TestUser");

        MockHttpServletResponse resp = mvc.perform(
                post("/users")
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), UserDTO.class);
    }

    /** Создать тип от имени userId */
    private ImageTypeDTO createType(UUID userId, String name, String typePrompt) throws Exception {
        ImageTypeRequest req = new ImageTypeRequest();
        req.setName(name);
        req.setTypePrompt(typePrompt);

        MockHttpServletResponse resp = mvc.perform(
                post("/image-types")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), ImageTypeDTO.class);
    }

    // ═══════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════

    @Test
    void createType_withAllFields_shouldReturnCreatedType() throws Exception {
        /*
         * .header("UserId", userId.toString()) — добавляет HTTP-заголовок.
         *
         * В NestJS:
         *   request(app).post('/image-types')
         *     .set('UserId', userId)
         *     .send({ name: 'Фото', typePrompt: '...' })
         *
         * В Spring:
         *   mvc.perform(post("/image-types")
         *     .header("UserId", userId.toString())
         *     .content(json).contentType(JSON))
         */
        UserDTO user = createUser();
        String uniqueName = "Фото-" + UUID.randomUUID();

        ImageTypeDTO dto = createType(user.getId(), uniqueName, "a photograph of");

        assertNotNull(dto.getId());
        assertEquals(uniqueName, dto.getName());
        assertEquals("a photograph of", dto.getTypePrompt());
        assertEquals(user.getId(), dto.getCreatedByUserId());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void createType_withoutTypePrompt_shouldCreateWithNullPrompt() throws Exception {
        UserDTO user = createUser();
        String uniqueName = "БезПромпта-" + UUID.randomUUID();

        ImageTypeDTO dto = createType(user.getId(), uniqueName, null);

        assertEquals(uniqueName, dto.getName());
        assertNull(dto.getTypePrompt());
    }

    @Test
    void createType_emptyName_shouldReturn400() throws Exception {
        UserDTO user = createUser();

        ImageTypeRequest req = new ImageTypeRequest();
        req.setName("");
        req.setTypePrompt("whatever");

        mvc.perform(post("/image-types")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createType_duplicateName_shouldReturn400() throws Exception {
        /*
         * UNIQUE constraint на lower(name) — case-insensitive.
         * "Фото" и "фото" считаются дубликатами.
         * DataIntegrityViolationException → 400.
         */
        UserDTO user = createUser();
        String name = "Дубль-" + UUID.randomUUID();

        createType(user.getId(), name, null);

        // Повторное создание с тем же именем
        ImageTypeRequest req = new ImageTypeRequest();
        req.setName(name);

        mvc.perform(post("/image-types")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════
    // GET
    // ═══════════════════════════════════════════

    @Test
    void getAll_shouldContainUndefinedType() throws Exception {
        /*
         * "Неопределённый" тип создаётся seed-данными в миграции.
         * Он ВСЕГДА есть в БД — это инвариант системы.
         */
        MockHttpServletResponse resp = mvc.perform(get("/image-types"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        ImageTypeDTO[] types = objectMapper.readValue(
                resp.getContentAsString(), ImageTypeDTO[].class);

        assertTrue(types.length >= 1);

        boolean hasUndefined = false;
        for (ImageTypeDTO t : types) {
            if (UNDEFINED_TYPE_ID.equals(t.getId())) {
                hasUndefined = true;
                break;
            }
        }
        assertTrue(hasUndefined, "Должен быть 'Неопределённый' тип из seed");
    }

    @Test
    void getById_existingType_shouldReturnType() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO created = createType(user.getId(), "GetById-" + UUID.randomUUID(), null);

        MockHttpServletResponse resp = mvc.perform(
                get("/image-types/{id}", created.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        ImageTypeDTO dto = objectMapper.readValue(resp.getContentAsString(), ImageTypeDTO.class);
        assertEquals(created.getId(), dto.getId());
        assertEquals(created.getName(), dto.getName());
    }

    @Test
    void getById_nonExistent_shouldReturn404() throws Exception {
        mvc.perform(get("/image-types/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════

    @Test
    void delete_ownType_shouldReturn200() throws Exception {
        /*
         * Создатель удаляет свой тип → успех.
         * Проверяем что после удаления GET возвращает 404.
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Удалить-" + UUID.randomUUID(), null);

        mvc.perform(delete("/image-types/{id}", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Проверяем что удалён
        mvc.perform(get("/image-types/{id}", type.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_otherUsersType_shouldReturn403() throws Exception {
        /*
         * Пользователь A создал тип.
         * Пользователь B пытается удалить → 403.
         *
         * IllegalStateException → GlobalExceptionHandler → 403.
         */
        UserDTO creator = createUser();
        UserDTO stranger = createUser();
        ImageTypeDTO type = createType(creator.getId(), "Чужой-" + UUID.randomUUID(), null);

        mvc.perform(delete("/image-types/{id}", type.getId())
                        .header("UserId", stranger.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_undefinedType_shouldReturn403() throws Exception {
        UserDTO user = createUser();

        mvc.perform(delete("/image-types/{id}", UNDEFINED_TYPE_ID)
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_nonExistent_shouldReturn404() throws Exception {
        UserDTO user = createUser();

        mvc.perform(delete("/image-types/{id}", UUID.randomUUID())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // FAVORITES
    // ═══════════════════════════════════════════

    @Test
    void addFavorite_shouldAppearInFavoritesList() throws Exception {
        /*
         * Полный цикл: добавить в избранное → получить список → проверить что есть.
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Избранное-" + UUID.randomUUID(), null);

        // Добавить в избранное
        mvc.perform(post("/image-types/{id}/favorite", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Получить список избранных
        MockHttpServletResponse resp = mvc.perform(
                get("/image-types/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UUID[] favorites = objectMapper.readValue(
                resp.getContentAsString(), UUID[].class);

        boolean found = false;
        for (UUID f : favorites) {
            if (f.equals(type.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Тип должен быть в избранном");
    }

    @Test
    void addFavorite_twice_shouldBeIdempotent() throws Exception {
        /*
         * ON CONFLICT DO NOTHING — повторное добавление не ошибка.
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Дважды-" + UUID.randomUUID(), null);

        mvc.perform(post("/image-types/{id}/favorite", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Второй раз — тоже ок
        mvc.perform(post("/image-types/{id}/favorite", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void removeFavorite_shouldDisappearFromList() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Убрать-" + UUID.randomUUID(), null);

        // Добавить
        mvc.perform(post("/image-types/{id}/favorite", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Убрать
        mvc.perform(delete("/image-types/{id}/favorite", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Проверить что нет в списке
        MockHttpServletResponse resp = mvc.perform(
                get("/image-types/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UUID[] favorites = objectMapper.readValue(
                resp.getContentAsString(), UUID[].class);

        for (UUID f : favorites) {
            assertNotEquals(type.getId(), f, "Тип не должен быть в избранном");
        }
    }

    @Test
    void addFavorite_nonExistentType_shouldReturn404() throws Exception {
        UserDTO user = createUser();

        mvc.perform(post("/image-types/{id}/favorite", UUID.randomUUID())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFavorites_emptyByDefault() throws Exception {
        /*
         * Новый пользователь — избранное пустое.
         */
        UserDTO user = createUser();

        MockHttpServletResponse resp = mvc.perform(
                get("/image-types/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UUID[] favorites = objectMapper.readValue(
                resp.getContentAsString(), UUID[].class);

        assertEquals(0, favorites.length);
    }
}
