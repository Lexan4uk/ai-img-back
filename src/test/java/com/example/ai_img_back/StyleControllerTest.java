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

import com.example.ai_img_back.style.StyleDTO;
import com.example.ai_img_back.style.StyleRequest;
import com.example.ai_img_back.user.UserDTO;
import com.example.ai_img_back.user.UserRequest;

/**
 * Интеграционные тесты StyleController.
 * Зеркало ImageTypeControllerTest — те же бизнес-правила.
 */
public class StyleControllerTest extends BaseTest {

    private static final UUID UNDEFINED_STYLE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

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

    private StyleDTO createStyle(UUID userId, String name, String stylePrompt) throws Exception {
        StyleRequest req = new StyleRequest();
        req.setName(name);
        req.setStylePrompt(stylePrompt);

        MockHttpServletResponse resp = mvc.perform(
                post("/styles")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), StyleDTO.class);
    }

    // ═══════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════

    @Test
    void createStyle_withAllFields_shouldReturnCreatedStyle() throws Exception {
        UserDTO user = createUser();
        String name = "Айвазовский-" + UUID.randomUUID();

        StyleDTO dto = createStyle(user.getId(), name, "in the style of Aivazovsky");

        assertNotNull(dto.getId());
        assertEquals(name, dto.getName());
        assertEquals("in the style of Aivazovsky", dto.getStylePrompt());
        assertEquals(user.getId(), dto.getCreatedByUserId());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void createStyle_withoutStylePrompt_shouldCreateWithNull() throws Exception {
        UserDTO user = createUser();

        StyleDTO dto = createStyle(user.getId(), "БезПромпта-" + UUID.randomUUID(), null);

        assertNull(dto.getStylePrompt());
    }

    @Test
    void createStyle_emptyName_shouldReturn400() throws Exception {
        UserDTO user = createUser();

        StyleRequest req = new StyleRequest();
        req.setName("");

        mvc.perform(post("/styles")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createStyle_duplicateName_shouldReturn400() throws Exception {
        UserDTO user = createUser();
        String name = "Дубль-" + UUID.randomUUID();

        createStyle(user.getId(), name, null);

        StyleRequest req = new StyleRequest();
        req.setName(name);

        mvc.perform(post("/styles")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════
    // GET
    // ═══════════════════════════════════════════

    @Test
    void getAll_shouldContainUndefinedStyle() throws Exception {
        MockHttpServletResponse resp = mvc.perform(get("/styles"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StyleDTO[] styles = objectMapper.readValue(
                resp.getContentAsString(), StyleDTO[].class);

        assertTrue(styles.length >= 1);

        boolean hasUndefined = false;
        for (StyleDTO s : styles) {
            if (UNDEFINED_STYLE_ID.equals(s.getId())) {
                hasUndefined = true;
                break;
            }
        }
        assertTrue(hasUndefined, "Должен быть 'Неопределённый' стиль из seed");
    }

    @Test
    void getById_existingStyle_shouldReturnStyle() throws Exception {
        UserDTO user = createUser();
        StyleDTO created = createStyle(user.getId(), "GetById-" + UUID.randomUUID(), null);

        MockHttpServletResponse resp = mvc.perform(
                get("/styles/{id}", created.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StyleDTO dto = objectMapper.readValue(resp.getContentAsString(), StyleDTO.class);
        assertEquals(created.getId(), dto.getId());
    }

    @Test
    void getById_nonExistent_shouldReturn404() throws Exception {
        mvc.perform(get("/styles/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════

    @Test
    void delete_ownStyle_shouldReturn200() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "Удалить-" + UUID.randomUUID(), null);

        mvc.perform(delete("/styles/{id}", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        mvc.perform(get("/styles/{id}", style.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_otherUsersStyle_shouldReturn403() throws Exception {
        UserDTO creator = createUser();
        UserDTO stranger = createUser();
        StyleDTO style = createStyle(creator.getId(), "Чужой-" + UUID.randomUUID(), null);

        mvc.perform(delete("/styles/{id}", style.getId())
                        .header("UserId", stranger.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_undefinedStyle_shouldReturn403() throws Exception {
        UserDTO user = createUser();

        mvc.perform(delete("/styles/{id}", UNDEFINED_STYLE_ID)
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_nonExistent_shouldReturn404() throws Exception {
        UserDTO user = createUser();

        mvc.perform(delete("/styles/{id}", UUID.randomUUID())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // FAVORITES
    // ═══════════════════════════════════════════

    @Test
    void addFavorite_shouldAppearInFavoritesList() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "Избранное-" + UUID.randomUUID(), null);

        mvc.perform(post("/styles/{id}/favorite", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        MockHttpServletResponse resp = mvc.perform(
                get("/styles/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StyleDTO[] favorites = objectMapper.readValue(
                resp.getContentAsString(), StyleDTO[].class);

        boolean found = false;
        for (StyleDTO f : favorites) {
            if (f.getId().equals(style.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Стиль должен быть в избранном");
    }

    @Test
    void addFavorite_twice_shouldBeIdempotent() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "Дважды-" + UUID.randomUUID(), null);

        mvc.perform(post("/styles/{id}/favorite", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        mvc.perform(post("/styles/{id}/favorite", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void removeFavorite_shouldDisappearFromList() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "Убрать-" + UUID.randomUUID(), null);

        mvc.perform(post("/styles/{id}/favorite", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        mvc.perform(delete("/styles/{id}/favorite", style.getId())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk());

        MockHttpServletResponse resp = mvc.perform(
                get("/styles/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StyleDTO[] favorites = objectMapper.readValue(
                resp.getContentAsString(), StyleDTO[].class);

        for (StyleDTO f : favorites) {
            assertNotEquals(style.getId(), f.getId());
        }
    }

    @Test
    void addFavorite_nonExistentStyle_shouldReturn404() throws Exception {
        UserDTO user = createUser();

        mvc.perform(post("/styles/{id}/favorite", UUID.randomUUID())
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFavorites_emptyByDefault() throws Exception {
        UserDTO user = createUser();

        MockHttpServletResponse resp = mvc.perform(
                get("/styles/favorites")
                        .header("UserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        StyleDTO[] favorites = objectMapper.readValue(
                resp.getContentAsString(), StyleDTO[].class);

        assertEquals(0, favorites.length);
    }
}
