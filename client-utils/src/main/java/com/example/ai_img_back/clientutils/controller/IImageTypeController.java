package com.example.ai_img_back.clientutils.controller;

import java.util.List;

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
    void delete(@PathVariable("id") Long id);

    @PostMapping("/{id}/favorite")
    void addFavorite(@PathVariable("id") Long id);

    @DeleteMapping("/{id}/favorite")
    void removeFavorite(@PathVariable("id") Long id);

    @GetMapping("/favorites")
    List<Long> getFavoriteIds();
}
