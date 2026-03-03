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

import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;
import com.example.ai_img_back.clientutils.dto.UserDTO;
import com.example.ai_img_back.clientutils.dto.UserRequest;

/**
 * Интеграционные тесты ImageTypeController.
 *
 * Особенности по сравнению с UserControllerTest:
 *
 * 1. Header "UserId" — нужен для create, delete, favorites.
 *    mvc.perform(post(...).header("UserId", userId.toString()))
 *
 * 2. Seed-данные — "Неопределённый" тип уже есть в БД
 *    (создаётся миграцией). На него можно полагаться.
 *
 * 3. Бизнес-правила удаления:
 *    - Нельзя удалить "Неопределённый" → 403
 *    - Нельзя удалить чужой тип → 403
 *    - Только создатель может удалить → 200
 */
public class ImageTypeControllerTest extends BaseTest {

    /** ID "Неопределённого" типа из seed-данных миграции */
    private static final Long UNDEFINED_TYPE_ID = 1L;

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
    private ImageTypeDTO createType(Long userId, String name, String typePrompt) throws Exception {
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
        UserDTO user = createUser();
        String uniqueName = "Фото-" + UUID.randomUUID();

        ImageTypeDTO dto = createType(user.getId(), uniqueName, "a photograph of");

        assertNotNull(dto.getId());
        assertEquals(uniqueName, dto.getName());
        assertEquals("a photograph of", dto.getTypePrompt());
        assertEquals(user.getId(), dto.getCreatedByUserId());
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

    // ═══════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════

    @Test
    void delete_ownType_shouldReturn200() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Удалить-" + UUID.randomUUID(), null);

        mvc.perform(delete("/image-types/{id}", type.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        // Проверяем что удалён — нет в общем списке
        MockHttpServletResponse resp = mvc.perform(get("/image-types"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        ImageTypeDTO[] types = objectMapper.readValue(
                resp.getContentAsString(), ImageTypeDTO[].class);

        for (ImageTypeDTO t : types) {
            assertNotEquals(type.getId(), t.getId(), "Удалённый тип не должен быть в списке");
        }
    }

    @Test
    void delete_otherUsersType_shouldReturn403() throws Exception {
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

        mvc.perform(delete("/image-types/{id}", 999999L)
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // FAVORITES
    // ═══════════════════════════════════════════

    @Test
    void addFavorite_shouldAppearInFavoritesList() throws Exception {
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

        Long[] favorites = objectMapper.readValue(
                resp.getContentAsString(), Long[].class);

        boolean found = false;
        for (Long f : favorites) {
            if (f.equals(type.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Тип должен быть в избранном");
    }

    @Test
    void addFavorite_twice_shouldBeIdempotent() throws Exception {
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

        Long[] favorites = objectMapper.readValue(
                resp.getContentAsString(), Long[].class);

        for (Long f : favorites) {
            assertNotEquals(type.getId(), f, "Тип не должен быть в избранном");
        }
    }

    @Test
    void addFavorite_nonExistentType_shouldReturn404() throws Exception {
        UserDTO user = createUser();

        mvc.perform(post("/image-types/{id}/favorite", 999999L)
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFavorites_emptyByDefault() throws Exception {
        UserDTO user = createUser();

        MockHttpServletResponse resp = mvc.perform(
                get("/image-types/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Long[] favorites = objectMapper.readValue(
                resp.getContentAsString(), Long[].class);

        assertEquals(0, favorites.length);
    }
}
