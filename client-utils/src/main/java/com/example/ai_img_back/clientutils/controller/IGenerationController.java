package com.example.ai_img_back.clientutils.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.ai_img_back.clientutils.dto.GenerateRequest;
import com.example.ai_img_back.clientutils.dto.GenerationCheckResult;
import com.example.ai_img_back.clientutils.dto.GenerationResultDTO;

@RequestMapping("/generations")
public interface IGenerationController {

    @PostMapping("/check")
    GenerationCheckResult check(@RequestBody GenerateRequest request);

    @PostMapping
    List<GenerationResultDTO> generate(@RequestBody GenerateRequest request);
}
