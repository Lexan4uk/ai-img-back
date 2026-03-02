package com.example.ai_img_back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.ai_img_back.clientutils.dto.AssetDTO;
import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;
import com.example.ai_img_back.clientutils.dto.StyleDTO;
import com.example.ai_img_back.clientutils.dto.StyleRequest;
import com.example.ai_img_back.clientutils.dto.UserDTO;
import com.example.ai_img_back.clientutils.dto.UserRequest;

/**
 * Интеграционные тесты AssetController (галерея).
 *
 * Галерея — read-only эндпоинт. Чтобы его протестировать,
 * нужно сначала сгенерировать картинки через POST /generations,
 * а потом проверить что они появились в GET /assets.
 *
 * Паттерн: setup (create user + type + style + generate) → verify (GET /assets).
 */
public class AssetControllerTest extends BaseTest {

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private UserDTO createUser() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail(UUID.randomUUID() + "@test.ru");
        req.setDisplayName("GalleryUser");

        MockHttpServletResponse resp = mvc.perform(
                post("/users")
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), UserDTO.class);
    }

    private ImageTypeDTO createType(UUID userId, String name) throws Exception {
        ImageTypeRequest req = new ImageTypeRequest();
        req.setName(name);
        req.setTypePrompt("tp-" + name);

        MockHttpServletResponse resp = mvc.perform(
                post("/image-types")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), ImageTypeDTO.class);
    }

    private StyleDTO createStyle(UUID userId, String name) throws Exception {
        StyleRequest req = new StyleRequest();
        req.setName(name);
        req.setStylePrompt("sp-" + name);

        MockHttpServletResponse resp = mvc.perform(
                post("/styles")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), StyleDTO.class);
    }

    /** Запустить генерацию 1 type × 1 style */
    private void generateOne(UUID userId, UUID typeId, UUID styleId, String prompt) throws Exception {
        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt(prompt);
        req.setImageTypeIds(List.of(typeId));
        req.setStyleIds(List.of(styleId));

        mvc.perform(post("/generations")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /** Запросить галерею */
    private AssetDTO[] getGallery(UUID typeId, UUID styleId) throws Exception {
        MockHttpServletResponse resp = mvc.perform(
                get("/assets")
                        .param("imageTypeId", typeId.toString())
                        .param("styleId", styleId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), AssetDTO[].class);
    }
    /** Запросить галерею типа (без стиля) */
    private AssetDTO[] getGalleryByType(UUID typeId) throws Exception {
        MockHttpServletResponse resp = mvc.perform(
                get("/assets")
                        .param("imageTypeId", typeId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), AssetDTO[].class);
    }

    // ═══════════════════════════════════════════
    // Tests
    // ═══════════════════════════════════════════

    @Test
    void gallery_afterGeneration_shouldContainAsset() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "GalT-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "GalS-" + UUID.randomUUID());

        generateOne(user.getId(), type.getId(), style.getId(), "картинка для галереи");

        AssetDTO[] assets = getGallery(type.getId(), style.getId());

        assertEquals(1, assets.length);
        assertEquals(type.getId(), assets[0].getImageTypeId());
        assertEquals(style.getId(), assets[0].getStyleId());
        assertEquals(user.getId(), assets[0].getOwnerUserId());
        assertTrue(assets[0].getFinalPromptSnapshot().contains("картинка для галереи"));
        assertNotNull(assets[0].getFileUri());
        assertNotNull(assets[0].getCreatedAt());
    }

    @Test
    void gallery_multipleGenerations_shouldReturnAll() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Multi-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Multi-" + UUID.randomUUID());

        generateOne(user.getId(), type.getId(), style.getId(), "первая-" + UUID.randomUUID());
        generateOne(user.getId(), type.getId(), style.getId(), "вторая-" + UUID.randomUUID());
        generateOne(user.getId(), type.getId(), style.getId(), "третья-" + UUID.randomUUID());

        AssetDTO[] assets = getGallery(type.getId(), style.getId());

        assertEquals(3, assets.length);
    }

    @Test
    void gallery_differentTypeStyle_shouldNotMix() throws Exception {
        /*
         * Генерируем для пары (typeA, styleA).
         * Запрашиваем галерею для (typeA, styleB) → пусто.
         * Фильтрация работает.
         */
        UserDTO user = createUser();
        ImageTypeDTO typeA = createType(user.getId(), "MixA-" + UUID.randomUUID());
        StyleDTO styleA = createStyle(user.getId(), "MixA-" + UUID.randomUUID());
        StyleDTO styleB = createStyle(user.getId(), "MixB-" + UUID.randomUUID());

        generateOne(user.getId(), typeA.getId(), styleA.getId(), "только для A+A");

        // Для (typeA, styleA) — есть
        AssetDTO[] assetsAA = getGallery(typeA.getId(), styleA.getId());
        assertEquals(1, assetsAA.length);

        // Для (typeA, styleB) — пусто
        AssetDTO[] assetsAB = getGallery(typeA.getId(), styleB.getId());
        assertEquals(0, assetsAB.length);
    }

    @Test
    void gallery_showsAllUsersAssets() throws Exception {
        /*
         * Галерея — cross-user: показывает картинки ВСЕХ пользователей.
         * User1 и User2 генерируют для одной пары (type, style).
         * Галерея показывает обе картинки.
         */
        UserDTO user1 = createUser();
        UserDTO user2 = createUser();
        ImageTypeDTO type = createType(user1.getId(), "Cross-" + UUID.randomUUID());
        StyleDTO style = createStyle(user1.getId(), "Cross-" + UUID.randomUUID());

        generateOne(user1.getId(), type.getId(), style.getId(), "от первого");
        generateOne(user2.getId(), type.getId(), style.getId(), "от второго");

        AssetDTO[] assets = getGallery(type.getId(), style.getId());

        assertEquals(2, assets.length);

        // Проверяем что оба пользователя представлены
        boolean hasUser1 = false;
        boolean hasUser2 = false;
        for (AssetDTO a : assets) {
            if (user1.getId().equals(a.getOwnerUserId())) hasUser1 = true;
            if (user2.getId().equals(a.getOwnerUserId())) hasUser2 = true;
        }
        assertTrue(hasUser1, "Должна быть картинка от user1");
        assertTrue(hasUser2, "Должна быть картинка от user2");
    }

    @Test
    void gallery_emptyForUnusedPair_shouldReturnEmptyArray() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Empty-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Empty-" + UUID.randomUUID());

        // Не генерируем ничего — галерея пуста
        AssetDTO[] assets = getGallery(type.getId(), style.getId());

        assertEquals(0, assets.length);
    }
    // ═══════════════════════════════════════════
    // Gallery by type (без styleId)
    // ═══════════════════════════════════════════

    @Test
    void galleryByType_multipleStyles_shouldReturnAll() throws Exception {
        /*
         * 1 тип × 3 стиля = 3 ассета.
         * GET /assets?imageTypeId=... (без styleId) → все 3.
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "ByType-" + UUID.randomUUID());
        StyleDTO s1 = createStyle(user.getId(), "BTS1-" + UUID.randomUUID());
        StyleDTO s2 = createStyle(user.getId(), "BTS2-" + UUID.randomUUID());
        StyleDTO s3 = createStyle(user.getId(), "BTS3-" + UUID.randomUUID());

        generateOne(user.getId(), type.getId(), s1.getId(), "картинка-1");
        generateOne(user.getId(), type.getId(), s2.getId(), "картинка-2");
        generateOne(user.getId(), type.getId(), s3.getId(), "картинка-3");

        AssetDTO[] assets = getGalleryByType(type.getId());

        assertEquals(3, assets.length);
        for (AssetDTO a : assets) {
            assertEquals(type.getId(), a.getImageTypeId());
        }
    }

    @Test
    void galleryByType_shouldNotIncludeOtherTypes() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO typeA = createType(user.getId(), "BTA-" + UUID.randomUUID());
        ImageTypeDTO typeB = createType(user.getId(), "BTB-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "BTS-" + UUID.randomUUID());

        generateOne(user.getId(), typeA.getId(), style.getId(), "для A");
        generateOne(user.getId(), typeB.getId(), style.getId(), "для B");

        AssetDTO[] assetsA = getGalleryByType(typeA.getId());
        assertEquals(1, assetsA.length);
        assertEquals(typeA.getId(), assetsA[0].getImageTypeId());
    }

    @Test
    void galleryByType_emptyForNewType_shouldReturnEmptyArray() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "BTEmpty-" + UUID.randomUUID());

        AssetDTO[] assets = getGalleryByType(type.getId());
        assertEquals(0, assets.length);
    }
}
