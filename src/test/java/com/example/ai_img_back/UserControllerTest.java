package com.example.ai_img_back;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.ai_img_back.user.UserDTO;
import com.example.ai_img_back.user.UserRequest;

/**
 * Интеграционные тесты UserController.
 *
 * Наследуем BaseTest → получаем MockMvc, ObjectMapper, реальный Postgres.
 *
 * Паттерн тестов у тимлида:
 *   1. Собрать request-объект
 *   2. Отправить через mvc.perform(...)
 *   3. Проверить HTTP-статус (.andExpect(status().isOk()))
 *   4. Распарсить ответ (objectMapper.readValue)
 *   5. Проверить поля (assertEquals, assertNotNull)
 * 
 * ─── Независимость тестов ───
 * Каждый тест создаёт свои данные и не зависит от других.
 * Используем рандомные email чтобы не конфликтовать.
 */
public class UserControllerTest extends BaseTest {

    // ═══════════════════════════════════════════
    // Helper — создать пользователя и вернуть DTO
    // ═══════════════════════════════════════════

    /**
     * Вспомогательный метод — создаёт пользователя через API.
     * Используется в тестах, которым нужен существующий пользователь.
     *
     * У тимлида такой же паттерн: createTemporaryDevice(), createProfile().
     *
     * Зачем рандомный email? Чтобы тесты не ломали друг друга.
     * UUID.randomUUID() гарантирует уникальность каждый раз.
     */
    private UserDTO createUser(String displayName) throws Exception {
        UserRequest request = new UserRequest();
        request.setEmail(UUID.randomUUID() + "@test.ru");
        request.setDisplayName(displayName);

        MockHttpServletResponse response = mvc.perform(
                post("/users")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        return objectMapper.readValue(response.getContentAsString(), UserDTO.class);
    }

    // ═══════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════

    @Test
    void createUser_withAllFields_shouldReturnCreatedUser() throws Exception {
        /*
         * Что тестируем: POST /users с полным набором полей.
         *
         * Что проверяем:
         * - HTTP 200
         * - В ответе есть id (UUID, сгенерированный БД)
         * - Поля совпадают с тем, что отправили
         * - createdAt заполнен (БД поставила DEFAULT now())
         */
        UserRequest request = new UserRequest();
        request.setEmail("create-all-fields-" + UUID.randomUUID() + "@test.ru");
        request.setDisplayName("Тест Полный");

        MockHttpServletResponse response = mvc.perform(
                post("/users")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        UserDTO dto = objectMapper.readValue(response.getContentAsString(), UserDTO.class);

        assertNotNull(dto.getId());
        assertEquals(request.getEmail(), dto.getEmail());
        assertEquals("Тест Полный", dto.getDisplayName());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void createUser_duplicateEmail_shouldReturn400() throws Exception {
        /*
         * Что тестируем: UNIQUE constraint на email.
         *
         * Паттерн: создаём пользователя → пытаемся создать второго с тем же email → 400.
         * DataIntegrityViolationException → GlobalExceptionHandler → 400.
         */
        String sameEmail = "duplicate-" + UUID.randomUUID() + "@test.ru";

        UserRequest request = new UserRequest();
        request.setEmail(sameEmail);
        request.setDisplayName("Первый");

        // Первый — успешно
        mvc.perform(post("/users")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Второй с тем же email — 400
        request.setDisplayName("Второй");
        mvc.perform(post("/users")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════
    // GET BY ID
    // ═══════════════════════════════════════════

    @Test
    void getById_existingUser_shouldReturnUser() throws Exception {
        /*
         * Что тестируем: GET /users/{id} — получить пользователя.
         *
         * Паттерн: создаём → получаем → проверяем что это тот же.
         * Тест независим — создаёт данные сам.
         */
        UserDTO created = createUser("Для Получения");

        MockHttpServletResponse response = mvc.perform(
                get("/users/{id}", created.getId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        UserDTO dto = objectMapper.readValue(response.getContentAsString(), UserDTO.class);

        assertEquals(created.getId(), dto.getId());
        assertEquals(created.getDisplayName(), dto.getDisplayName());
    }

    @Test
    void getById_nonExistent_shouldReturn404() throws Exception {
        /*
         * Что тестируем: несуществующий UUID → 404.
         *
         * UUID.randomUUID() гарантированно не существует в БД.
         * EntityNotFoundException → GlobalExceptionHandler → 404.
         */
        mvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════
    // GET ALL
    // ═══════════════════════════════════════════

    @Test
    void getAll_shouldReturnNonEmptyList() throws Exception {
        /*
         * Что тестируем: GET /users — список.
         *
         * Создаём пользователя, потом запрашиваем список
         * и проверяем что он непустой и содержит нашего.
         */
        UserDTO created = createUser("Для Списка");

        MockHttpServletResponse response = mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        /*
         * Парсим массив JSON → массив DTO.
         *
         * objectMapper.readValue(json, UserDTO[].class) — парсит JSON-массив.
         * В NestJS: JSON.parse(res.text) и всё. В Java нужно указать тип явно.
         */
        UserDTO[] users = objectMapper.readValue(
                response.getContentAsString(), UserDTO[].class);

        assertTrue(users.length >= 1);

        // Проверяем что наш созданный пользователь есть в списке
        boolean found = false;
        for (UserDTO u : users) {
            if (u.getId().equals(created.getId())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Созданный пользователь должен быть в списке");
    }

    // ═══════════════════════════════════════════
    // DELETE
    // ═══════════════════════════════════════════

    @Test
    void delete_existingUser_shouldReturn200() throws Exception {
        /*
         * Что тестируем: DELETE /users/{id}.
         *
         * Паттерн: создаём → удаляем → проверяем что GET на него даёт 404.
         * Полный цикл life-time сущности.
         */
        UserDTO created = createUser("Для Удаления");

        // Удаляем
        mvc.perform(delete("/users/{id}", created.getId()))
                .andExpect(status().isOk());

        // Проверяем что больше не существует
        mvc.perform(get("/users/{id}", created.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_nonExistent_shouldReturn404() throws Exception {
        /*
         * Что тестируем: удаление несуществующего → 404.
         */
        mvc.perform(delete("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
