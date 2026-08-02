package com.selfsync.api.controller;

import com.selfsync.api.dto.ApiDtos.*;
import com.selfsync.api.service.AiService;
import com.selfsync.api.service.InterviewCatalog;
import com.selfsync.api.service.InterviewService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/interviews")
public class InterviewsController {
    private final InterviewService interviewService;
    private final AiService aiService;
    private final Path uploadsDir = Path.of("Uploads");

    public InterviewsController(InterviewService interviewService, AiService aiService) {
        this.interviewService = interviewService;
        this.aiService = aiService;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return InterviewCatalog.asPublicCatalog();
    }

    @PostMapping
    public InterviewSessionDto create(Authentication authentication, @Valid @RequestBody CreateInterviewRequest request) {
        return interviewService.create((UUID) authentication.getPrincipal(), request);
    }

    @GetMapping
    public List<InterviewSummaryDto> list(
            Authentication authentication,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        return interviewService.list((UUID) authentication.getPrincipal(), domain, status);
    }

    @GetMapping("/{id}")
    public InterviewSessionDto get(Authentication authentication, @PathVariable UUID id) {
        return interviewService.get((UUID) authentication.getPrincipal(), id);
    }

    @PostMapping("/{id}/answers")
    public AnswerDto submitAnswer(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody SubmitAnswerRequest request) {
        return interviewService.submitTextAnswer((UUID) authentication.getPrincipal(), id, request);
    }

    @PostMapping(value = "/{id}/answers/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnswerDto submitMedia(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam UUID questionId,
            @RequestParam String inputType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String textContent) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media file is required.");
        }
        if (!"Voice".equalsIgnoreCase(inputType) && !"Video".equalsIgnoreCase(inputType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputType must be Voice or Video.");
        }

        Files.createDirectories(uploadsDir);
        String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload.bin";
        String safeName = UUID.randomUUID() + "_" + Path.of(original).getFileName();
        Path fullPath = uploadsDir.resolve(safeName);
        Files.write(fullPath, file.getBytes());

        String transcript = aiService.transcribeAudio(
                file.getBytes(),
                safeName,
                file.getContentType());

        return interviewService.submitMediaAnswer(
                (UUID) authentication.getPrincipal(),
                id,
                questionId,
                inputType,
                uploadsDir.resolve(safeName).toString(),
                transcript,
                textContent);
    }

    @PostMapping("/{id}/complete")
    public InterviewSessionDto complete(Authentication authentication, @PathVariable UUID id) {
        return interviewService.complete((UUID) authentication.getPrincipal(), id);
    }

    @GetMapping(value = "/{id}/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report(Authentication authentication, @PathVariable UUID id) {
        String html = interviewService.htmlReport((UUID) authentication.getPrincipal(), id);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
