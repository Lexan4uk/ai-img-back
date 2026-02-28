package com.example.ai_img_back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.ai_img_back.generation.GenerateRequest;
import com.example.ai_img_back.generation.GenerationResultDTO;
import com.example.ai_img_back.imagetype.ImageTypeDTO;
import com.example.ai_img_back.imagetype.ImageTypeRequest;
import com.example.ai_img_back.style.StyleDTO;
import com.example.ai_img_back.style.StyleRequest;
import com.example.ai_img_back.user.UserDTO;
import com.example.ai_img_back.user.UserRequest;

/**
 * Интеграционные тесты GenerationController.
 *
 * Это самый "тяжёлый" тест-класс — каждый тест создаёт
 * пользователя + тип(ы) + стиль(и) → запускает генерацию.
 *
 * Новый паттерн: парсинг массива DTO из ответа.
 * objectMapper.readValue(json, GenerationResultDTO[].class)
 *
 * В NestJS:
 *   const results: GenerationResultDTO[] = res.body;
 *   expect(results).toHaveLength(4);
 *
 * В Java массив, потому что Jackson не может вывести
 * generic тип List<T> без TypeReference. Массив проще.
 */
public class GenerationControllerTest extends BaseTest {

    // ═══════════════════════════════════════════
    // Helpers — каждый тест собирает данные с нуля
    // ═══════════════════════════════════════════

    private UserDTO createUser() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail(UUID.randomUUID() + "@test.ru");
        req.setDisplayName("GenTestUser");

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
        req.setTypePrompt("prompt-for-" + name);

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
        req.setStylePrompt("prompt-for-" + name);

        MockHttpServletResponse resp = mvc.perform(
                post("/styles")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(resp.getContentAsString(), StyleDTO.class);
    }

    /**
     * Отправить POST /generations и вернуть массив результатов.
     *
     * GenerateRequest собирается вручную в каждом тесте,
     * а этот helper только отправляет и парсит ответ.
     */
    private GenerationResultDTO[] generate(UUID userId, GenerateRequest request) throws Exception {
        MockHttpServletResponse resp = mvc.perform(
                post("/generations")
                        .header("UserId", userId.toString())
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return objectMapper.readValue(
                resp.getContentAsString(), GenerationResultDTO[].class);
    }

    // ═══════════════════════════════════════════
    // Успешная генерация
    // ═══════════════════════════════════════════

    @Test
    void generate_1type_1style_shouldReturn1Result() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Тип-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Стиль-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("тестовый промпт");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] results = generate(user.getId(), req);

        assertEquals(1, results.length);
        assertEquals("DONE", results[0].getStatus().name());
        assertEquals(type.getId(), results[0].getImageTypeId());
        assertEquals(style.getId(), results[0].getStyleId());
        assertNotNull(results[0].getCreatedAssetId());
        assertNotNull(results[0].getRequestId());
        assertNull(results[0].getErrorMessage());
    }

    @Test
    void generate_2types_2styles_shouldReturn4Results() throws Exception {
        /*
         * Декартово произведение: 2 × 2 = 4 request-а.
         * Все должны быть DONE (первая генерация, дубликатов нет).
         */
        UserDTO user = createUser();
        ImageTypeDTO type1 = createType(user.getId(), "T1-" + UUID.randomUUID());
        ImageTypeDTO type2 = createType(user.getId(), "T2-" + UUID.randomUUID());
        StyleDTO style1 = createStyle(user.getId(), "S1-" + UUID.randomUUID());
        StyleDTO style2 = createStyle(user.getId(), "S2-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("четыре картинки");
        req.setImageTypeIds(List.of(type1.getId(), type2.getId()));
        req.setStyleIds(List.of(style1.getId(), style2.getId()));

        GenerationResultDTO[] results = generate(user.getId(), req);

        assertEquals(4, results.length);

        for (GenerationResultDTO r : results) {
            assertEquals("DONE", r.getStatus().name());
            assertNotNull(r.getCreatedAssetId());
        }
    }

    @Test
    void generate_withGenerationParams_shouldSucceed() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Params-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Params-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("с параметрами");
        req.setGenerationParams("{\"width\": 512, \"height\": 512}");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] results = generate(user.getId(), req);

        assertEquals(1, results.length);
        assertEquals("DONE", results[0].getStatus().name());
    }

    // ═══════════════════════════════════════════
    // Валидация
    // ═══════════════════════════════════════════

    @Test
    void generate_emptyPrompt_shouldReturn400() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "V1-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "V1-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_nullPrompt_shouldReturn400() throws Exception {
        UserDTO user = createUser();

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt(null);
        req.setImageTypeIds(List.of(UUID.randomUUID()));
        req.setStyleIds(List.of(UUID.randomUUID()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_emptyTypeIds_shouldReturn400() throws Exception {
        UserDTO user = createUser();

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("что-то");
        req.setImageTypeIds(List.of());
        req.setStyleIds(List.of(UUID.randomUUID()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_emptyStyleIds_shouldReturn400() throws Exception {
        UserDTO user = createUser();

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("что-то");
        req.setImageTypeIds(List.of(UUID.randomUUID()));
        req.setStyleIds(List.of());

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_nonExistentType_shouldReturn404() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "NF-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("с несуществующим типом");
        req.setImageTypeIds(List.of(UUID.randomUUID()));
        req.setStyleIds(List.of(style.getId()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void generate_nonExistentStyle_shouldReturn404() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "NF-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("с несуществующим стилем");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(UUID.randomUUID()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // Дедупликация
    // ═══════════════════════════════════════════

    @Test
    void generate_duplicateWithSkip_shouldReturnSkipped() throws Exception {
        /*
         * Первый запрос → DONE (asset создан).
         * Второй запрос с тем же промптом + SKIP → SKIPPED.
         *
         * Дедупликация работает по SHA-256 hash от:
         *   final_prompt + "|" + generation_params
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Dedup-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Dedup-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("одинаковый промпт для дедупликации");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        // Первый раз — DONE
        GenerationResultDTO[] first = generate(user.getId(), req);
        assertEquals("DONE", first[0].getStatus().name());

        // Второй раз с SKIP — SKIPPED
        req.setDedupeMode(com.example.ai_img_back.generation.enums.DedupeMode.SKIP);
        GenerationResultDTO[] second = generate(user.getId(), req);
        assertEquals("SKIPPED", second[0].getStatus().name());
        assertNull(second[0].getCreatedAssetId());
    }

    @Test
    void generate_duplicateWithOverwrite_shouldReturnDone() throws Exception {
        /*
         * OVERWRITE — генерирует заново даже если дубликат.
         * Создаётся новый asset (старый не трогается).
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Over-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Over-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("промпт для перезаписи");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        // Первый раз
        GenerationResultDTO[] first = generate(user.getId(), req);
        assertEquals("DONE", first[0].getStatus().name());
        UUID firstAssetId = first[0].getCreatedAssetId();

        // Второй раз с OVERWRITE — тоже DONE, но новый asset
        req.setDedupeMode(com.example.ai_img_back.generation.enums.DedupeMode.OVERWRITE);
        GenerationResultDTO[] second = generate(user.getId(), req);
        assertEquals("DONE", second[0].getStatus().name());
        assertNotNull(second[0].getCreatedAssetId());
        assertNotEquals(firstAssetId, second[0].getCreatedAssetId());
    }

    @Test
    void generate_samePromptDifferentParams_shouldNotBeDuplicate() throws Exception {
        /*
         * Одинаковый текст, но разные generation_params →
         * разный hash → НЕ дубликат → оба DONE.
         */
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "DiffP-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "DiffP-" + UUID.randomUUID());

        GenerateRequest req1 = new GenerateRequest();
        req1.setUserPrompt("одинаковый текст");
        req1.setGenerationParams("{\"width\": 1024, \"height\": 1024}");
        req1.setImageTypeIds(List.of(type.getId()));
        req1.setStyleIds(List.of(style.getId()));

        GenerateRequest req2 = new GenerateRequest();
        req2.setUserPrompt("одинаковый текст");
        req2.setGenerationParams("{\"width\": 512, \"height\": 512}");
        req2.setImageTypeIds(List.of(type.getId()));
        req2.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] first = generate(user.getId(), req1);
        assertEquals("DONE", first[0].getStatus().name());

        GenerationResultDTO[] second = generate(user.getId(), req2);
        assertEquals("DONE", second[0].getStatus().name());
    }

    @Test
    void generate_samePromptDifferentUsers_shouldNotBeDuplicate() throws Exception {
        /*
         * Дедупликация в рамках ОДНОГО пользователя.
         * Разные пользователи с тем же промптом → оба DONE.
         */
        UserDTO user1 = createUser();
        UserDTO user2 = createUser();
        ImageTypeDTO type = createType(user1.getId(), "DiffU-" + UUID.randomUUID());
        StyleDTO style = createStyle(user1.getId(), "DiffU-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("один и тот же промпт");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] r1 = generate(user1.getId(), req);
        assertEquals("DONE", r1[0].getStatus().name());

        GenerationResultDTO[] r2 = generate(user2.getId(), req);
        assertEquals("DONE", r2[0].getStatus().name());
    }
}
