package com.example.ai_img_back.clientutils.controller;

import java.util.List;

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
    UserDTO getById(@PathVariable("id") Long id);

    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id);
}
