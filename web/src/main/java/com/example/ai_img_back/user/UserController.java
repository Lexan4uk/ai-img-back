package com.example.ai_img_back.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.UserDTO;
import com.example.ai_img_back.clientutils.dto.UserRequest;

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
        return toDTO(user);
    }

    /** GET /users — список всех */
    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** GET /users/{id} — получить по id */
    @GetMapping("/{id}")
    public UserDTO getById(@PathVariable UUID id) {
        return toDTO(userService.getById(id));
    }

    /** DELETE /users/{id} — удалить */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }

    /** Entity → DTO маппинг */
    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
