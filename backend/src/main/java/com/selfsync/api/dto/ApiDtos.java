package com.selfsync.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token, UserDto user) {}

    public record UserDto(
            UUID id,
            String fullName,
            String email,
            Instant createdAt,
            String provider,
            String avatarUrl,
            String phoneNumber,
            boolean emailVerified,
            String firebaseUid
    ) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 30) String phoneNumber,
            @Size(max = 1000) String avatarUrl
    ) {}

    public record FirebaseLoginRequest(@NotBlank String idToken) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6) String password
    ) {}

    public record MessageResponse(String message) {}

    public record LearningResourceDto(
            String title,
            String description,
            String url,
            String skill,
            String level
    ) {}

    public record LearningRecommendationsDto(
            List<String> focusSkills,
            List<LearningResourceDto> resources
    ) {}

    public record CreateInterviewRequest(
            @NotBlank String interviewType,
            @NotBlank String domain,
            @NotBlank String difficulty
    ) {}

    public record SubmitAnswerRequest(
            UUID questionId,
            String textContent,
            String inputType
    ) {}

    public record AnswerDto(
            UUID id,
            String inputType,
            String textContent,
            String transcript,
            String mediaPath,
            Instant submittedAt
    ) {}

    public record QuestionDto(UUID id, int orderIndex, String text, AnswerDto answer) {}

    public record QuestionReviewDto(
            int questionIndex,
            String verdict,
            double score,
            boolean showCorrectAnswer,
            String correctAnswer,
            String explanation
    ) {}

    public record ScoreReportDto(
            UUID id,
            double technicalScore,
            double communicationScore,
            double confidenceScore,
            double problemSolvingScore,
            double overallScore,
            String feedback,
            String strengths,
            String weaknesses,
            String improvements,
            Instant createdAt,
            List<QuestionReviewDto> questionReviews
    ) {}

    public record AiStatusDto(
            boolean configured,
            boolean ok,
            String provider,
            String model,
            String message
    ) {}

    public record InterviewSessionDto(
            UUID id,
            String interviewType,
            String domain,
            String difficulty,
            String status,
            Instant createdAt,
            Instant completedAt,
            List<QuestionDto> questions,
            ScoreReportDto scoreReport
    ) {}

    public record InterviewSummaryDto(
            UUID id,
            String interviewType,
            String domain,
            String difficulty,
            String status,
            Instant createdAt,
            Instant completedAt,
            Double overallScore
    ) {}

    public record DomainTrendDto(String domain, int sessions, double averageScore) {}

    public record AnalyticsSummaryDto(
            int totalSessions,
            int completedSessions,
            double averageOverallScore,
            double averageTechnical,
            double averageCommunication,
            double averageConfidence,
            double averageProblemSolving,
            int currentStreakDays,
            int weeklyGoalTarget,
            int weeklyCompleted,
            List<InterviewSummaryDto> recentSessions,
            List<DomainTrendDto> domainTrends
    ) {}

    public record EvaluationResult(
            double technicalScore,
            double communicationScore,
            double confidenceScore,
            double problemSolvingScore,
            double overallScore,
            String feedback,
            String strengths,
            String weaknesses,
            String improvements,
            List<QuestionReviewDto> questionReviews
    ) {}

    public record QaPair(String question, String answer, String inputType) {}
}
