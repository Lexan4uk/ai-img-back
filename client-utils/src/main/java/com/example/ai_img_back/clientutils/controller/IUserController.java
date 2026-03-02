package com.example.ai_img_back.clientutils.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.UserDTO;
import com.example.ai_img_back.clientutils.dto.UserRequest;

@RequestMapping("/users")
public interface IUserController {

    @PostMapping
    UserDTO create(@RequestBody UserRequest request);

    @GetMapping
    List<UserDTO> getAll();

    @GetMapping("/{id}")
    UserDTO getById(@PathVariable("id") UUID id);

    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") UUID id);
}
