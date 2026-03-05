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

import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.GenerationResultDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;
import com.example.ai_img_back.clientutils.dto.StyleDTO;
import com.example.ai_img_back.clientutils.dto.StyleRequest;
import com.example.ai_img_back.clientutils.dto.UserDTO;
import com.example.ai_img_back.clientutils.dto.UserRequest;

/**
 * Интеграционные тесты GenerationController.
 *
 * Это самый "тяжёлый" тест-класс — каждый тест создаёт
 * пользователя + тип(ы) + стиль(и) → запускает генерацию.
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

    private ImageTypeDTO createType(Long userId, String name) throws Exception {
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

    private StyleDTO createStyle(Long userId, String name) throws Exception {
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

    private GenerationResultDTO[] generate(Long userId, GenerateRequest request) throws Exception {
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
        req.setGenerationParams("{\"aspectRatio\": \"16:9\"}");
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
        req.setImageTypeIds(List.of(999999L));
        req.setStyleIds(List.of(999999L));

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
        req.setStyleIds(List.of(999999L));

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
        req.setImageTypeIds(List.of(999999L));
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
        req.setImageTypeIds(List.of(999999L));
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
        req.setStyleIds(List.of(999999L));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void generate_withUndefinedType_shouldReturn400() throws Exception {
        UserDTO user = createUser();
        StyleDTO style = createStyle(user.getId(), "S-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("попытка с неопределённым типом");
        req.setImageTypeIds(List.of(1L)); // UNDEFINED_TYPE_ID
        req.setStyleIds(List.of(style.getId()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_withUndefinedStyle_shouldReturn400() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "T-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("попытка с неопределённым стилем");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(1L)); // UNDEFINED_STYLE_ID

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generate_undefinedTypeAmongValid_shouldReturn400() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO validType = createType(user.getId(), "Valid-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "S-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("микс валидного и неопределённого");
        req.setImageTypeIds(List.of(
                validType.getId(),
                1L // UNDEFINED подмешан
        ));
        req.setStyleIds(List.of(style.getId()));

        mvc.perform(post("/generations")
                        .header("UserId", user.getId().toString())
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════
    // Дедупликация
    // ═══════════════════════════════════════════

    @Test
    void generate_duplicateWithoutOverwrite_shouldReturnEmpty() throws Exception {
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

        // Второй раз без overwrite — дубликат не создаёт request, пустой массив
        req.setOverwriteDuplicates(false);
        GenerationResultDTO[] second = generate(user.getId(), req);
        assertEquals(0, second.length);
    }

    @Test
    void generate_duplicateWithOverwrite_shouldReturnDone() throws Exception {
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
        Long firstAssetId = first[0].getCreatedAssetId();

        // Второй раз с overwrite=true — тоже DONE, но новый asset
        req.setOverwriteDuplicates(true);
        GenerationResultDTO[] second = generate(user.getId(), req);
        assertEquals("DONE", second[0].getStatus().name());
        assertNotNull(second[0].getCreatedAssetId());
        assertNotEquals(firstAssetId, second[0].getCreatedAssetId());
    }

    @Test
    void generate_samePromptDifferentParams_shouldNotBeDuplicate() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "DiffP-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "DiffP-" + UUID.randomUUID());

        GenerateRequest req1 = new GenerateRequest();
        req1.setUserPrompt("одинаковый текст");
        req1.setGenerationParams("{\"aspectRatio\": \"1:1\"}");
        req1.setImageTypeIds(List.of(type.getId()));
        req1.setStyleIds(List.of(style.getId()));

        GenerateRequest req2 = new GenerateRequest();
        req2.setUserPrompt("одинаковый текст");
        req2.setGenerationParams("{\"aspectRatio\": \"16:9\"}");
        req2.setImageTypeIds(List.of(type.getId()));
        req2.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] first = generate(user.getId(), req1);
        assertEquals("DONE", first[0].getStatus().name());

        GenerationResultDTO[] second = generate(user.getId(), req2);
        assertEquals("DONE", second[0].getStatus().name());
    }

    @Test
    void generate_samePromptDifferentUsers_globalDedup_shouldSkipSecond() throws Exception {
        /*
         * Глобальная дедупликация: по умолчанию overwriteDuplicates=false.
         * User1 генерирует → DONE. User2 с тем же промптом → пустой массив,
         * т.к. asset с таким hash уже существует (global dedup).
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
        assertEquals(0, r2.length);
    }

    @Test
    void generate_samePromptDifferentUsers_overwrite_shouldBothDone() throws Exception {
        /*
         * С overwriteDuplicates=true дедупликация игнорируется —
         * оба пользователя получают DONE.
         */
        UserDTO user1 = createUser();
        UserDTO user2 = createUser();
        ImageTypeDTO type = createType(user1.getId(), "OverU-" + UUID.randomUUID());
        StyleDTO style = createStyle(user1.getId(), "OverU-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("один и тот же промпт overwrite");
        req.setOverwriteDuplicates(true);
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        GenerationResultDTO[] r1 = generate(user1.getId(), req);
        assertEquals("DONE", r1[0].getStatus().name());

        GenerationResultDTO[] r2 = generate(user2.getId(), req);
        assertEquals("DONE", r2[0].getStatus().name());
    }

    // ═══════════════════════════════════════════
    // Проверка дубликатов (check endpoint)
    // ═══════════════════════════════════════════

    @Test
    void check_noDuplicates_shouldReturnZeroDuplicates() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "Check-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "Check-" + UUID.randomUUID());

        GenerateRequest req = new GenerateRequest();
        req.setUserPrompt("новый промпт для проверки");
        req.setImageTypeIds(List.of(type.getId()));
        req.setStyleIds(List.of(style.getId()));

        MockHttpServletResponse resp = mvc.perform(
                post("/generations/check")
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        com.example.ai_img_back.clientutils.dto.GenerationCheckResult result =
                objectMapper.readValue(resp.getContentAsString(),
                        com.example.ai_img_back.clientutils.dto.GenerationCheckResult.class);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(1, result.getNewCount());
    }

    @Test
    void check_withDuplicates_shouldReturnCorrectCounts() throws Exception {
        UserDTO user = createUser();
        ImageTypeDTO type = createType(user.getId(), "CheckD-" + UUID.randomUUID());
        StyleDTO style = createStyle(user.getId(), "CheckD-" + UUID.randomUUID());

        // Сначала сгенерируем
        GenerateRequest genReq = new GenerateRequest();
        genReq.setUserPrompt("промпт для check дубликатов");
        genReq.setImageTypeIds(List.of(type.getId()));
        genReq.setStyleIds(List.of(style.getId()));
        generate(user.getId(), genReq);

        // Теперь check — должен показать 1 дубликат
        MockHttpServletResponse resp = mvc.perform(
                post("/generations/check")
                        .content(objectMapper.writeValueAsString(genReq))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        com.example.ai_img_back.clientutils.dto.GenerationCheckResult result =
                objectMapper.readValue(resp.getContentAsString(),
                        com.example.ai_img_back.clientutils.dto.GenerationCheckResult.class);

        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(0, result.getNewCount());
    }
}
