package com.selfsync.api.controller;

import com.selfsync.api.dto.ApiDtos.LearningRecommendationsDto;
import com.selfsync.api.service.LearningService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/recommendations")
    public LearningRecommendationsDto recommendations(Authentication authentication) {
        return learningService.recommendations((UUID) authentication.getPrincipal());
    }
}
