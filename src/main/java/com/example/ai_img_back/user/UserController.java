package com.example.ai_img_back.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-контроллер пользователей.
 *
 * Маппинг Spring → NestJS:
 *   @RestController          → @Controller()
 *   @RequestMapping("/users") → @Controller('users')
 *   @GetMapping("/{id}")     → @Get(':id')
 *   @PathVariable UUID id    → @Param('id', ParseUUIDPipe) id: string
 *   @RequestBody             → @Body()
 *
 * Spring автоматически парсит UUID из строки в пути.
 * Если формат невалидный — Spring вернёт 400 сам (MethodArgumentTypeMismatchException).
 * В NestJS для этого нужен ParseUUIDPipe.
 *
 * @RequiredArgsConstructor — Lombok генерирует конструктор с userService.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** POST /users — создать пользователя */
    @PostMapping
    public UserDTO create(@RequestBody UserRequest request) {
        User user = userService.create(request.getEmail(), request.getDisplayName());
        return new UserDTO(user);
    }

    /** GET /users — список всех */
    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAll().stream()
                .map(UserDTO::new)
                .toList();
    }

    /** GET /users/{id} — получить по id */
    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable UUID id) {
        return new UserDTO(userService.getById(id));
    }

    /** DELETE /users/{id} — удалить */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
