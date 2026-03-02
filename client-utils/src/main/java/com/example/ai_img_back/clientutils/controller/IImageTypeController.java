package com.example.ai_img_back.clientutils.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.ImageTypeDTO;
import com.example.ai_img_back.clientutils.dto.ImageTypeRequest;

@RequestMapping("/image-types")
public interface IImageTypeController {

    @PostMapping
    ImageTypeDTO create(@RequestBody ImageTypeRequest request);

    @GetMapping
    List<ImageTypeDTO> getAll();

    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") UUID id);

    @PostMapping("/{id}/favorite")
    void addFavorite(@PathVariable("id") UUID id);

    @DeleteMapping("/{id}/favorite")
    void removeFavorite(@PathVariable("id") UUID id);

    @GetMapping("/favorites")
    List<UUID> getFavoriteIds();
}
