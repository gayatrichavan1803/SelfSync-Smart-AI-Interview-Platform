package com.selfsync.api.controller;

import com.selfsync.api.dto.ApiDtos.AnalyticsSummaryDto;
import com.selfsync.api.service.InterviewService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final InterviewService interviewService;

    public AnalyticsController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryDto summary(Authentication authentication) {
        return interviewService.analytics((UUID) authentication.getPrincipal());
    }
}
