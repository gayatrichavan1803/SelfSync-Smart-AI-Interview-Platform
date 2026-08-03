package com.selfsync.api.controller;

import com.selfsync.api.dto.ApiDtos.AiStatusDto;
import com.selfsync.api.service.AiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final AiService aiService;

    public HealthController(AiService aiService) {
        this.aiService = aiService;
    }

    /** Public — anyone can see whether Groq AI is live (never exposes the API key). */
    @GetMapping("/ai")
    public AiStatusDto aiStatus() {
        return aiService.checkStatus();
    }
}
