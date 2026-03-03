package com.example.ai_img_back.clientutils.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.AssetDTO;

@RequestMapping("/assets")
public interface IAssetController {

    @GetMapping
    List<AssetDTO> getAssets(
            @RequestParam("imageTypeId") Long imageTypeId,
            @RequestParam(value = "styleId", required = false) Long styleId);
}
