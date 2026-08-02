package com.selfsync.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfsync.api.dto.ApiDtos.*;
import com.selfsync.api.model.*;
import com.selfsync.api.repository.InterviewSessionRepository;
import com.selfsync.api.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterviewService {
    private final InterviewSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public InterviewService(
            InterviewSessionRepository sessionRepository,
            UserRepository userRepository,
            AiService aiService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionDto create(UUID userId, CreateInterviewRequest request) {
        validate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        String type = InterviewCatalog.canonicalType(request.interviewType());
        String domain = InterviewCatalog.canonicalDomain(type, request.domain());
        String difficulty = InterviewCatalog.canonicalDifficulty(request.difficulty());

        List<String> questions = aiService.generateQuestions(type, domain, difficulty, 5);

        InterviewSession session = new InterviewSession();
        session.setUser(user);
        session.setInterviewType(type);
        session.setDomain(domain);
        session.setDifficulty(difficulty);
        session.setStatus("InProgress");

        int index = 0;
        for (String text : questions) {
            Question q = new Question();
            q.setInterviewSession(session);
            q.setOrderIndex(index++);
            q.setText(text);
            session.getQuestions().add(q);
        }

        sessionRepository.save(session);
        return mapSession(session);
    }

    @Transactional
    public InterviewSessionDto get(UUID userId, UUID sessionId) {
        return mapSession(load(userId, sessionId));
    }

    @Transactional
    public List<InterviewSummaryDto> list(UUID userId, String domain, String status) {
        return sessionRepository.findFiltered(userId, emptyToNull(domain), emptyToNull(status))
                .stream()
                .map(this::mapSummary)
                .toList();
    }

    @Transactional
    public AnswerDto submitTextAnswer(UUID userId, UUID sessionId, SubmitAnswerRequest request) {
        InterviewSession session = load(userId, sessionId);
        if (!"InProgress".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview is already completed.");
        }
        Question question = session.getQuestions().stream()
                .filter(q -> q.getId().equals(request.questionId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question not found in this session."));
        if (!StringUtils.hasText(request.textContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text content is required for text answers.");
        }

        Answer answer = question.getAnswer();
        if (answer == null) {
            answer = new Answer();
            answer.setQuestion(question);
            question.setAnswer(answer);
        }
        answer.setInputType("Text");
        answer.setTextContent(request.textContent().trim());
        answer.setSubmittedAt(Instant.now());
        sessionRepository.save(session);
        return mapAnswer(answer);
    }

    @Transactional
    public AnswerDto submitMediaAnswer(
            UUID userId,
            UUID sessionId,
            UUID questionId,
            String inputType,
            String mediaPath,
            String transcript,
            String textContent) {
        InterviewSession session = load(userId, sessionId);
        if (!"InProgress".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview is already completed.");
        }
        Question question = session.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question not found in this session."));

        Answer answer = question.getAnswer();
        if (answer == null) {
            answer = new Answer();
            answer.setQuestion(question);
            question.setAnswer(answer);
        }
        answer.setInputType(inputType);
        answer.setMediaPath(mediaPath);
        answer.setTranscript(transcript);
        if (StringUtils.hasText(textContent)) {
            answer.setTextContent(textContent);
        }
        answer.setSubmittedAt(Instant.now());
        sessionRepository.save(session);
        return mapAnswer(answer);
    }

    @Transactional
    public InterviewSessionDto complete(UUID userId, UUID sessionId) {
        InterviewSession session = load(userId, sessionId);
        if ("Completed".equals(session.getStatus()) && session.getScoreReport() != null) {
            return mapSession(session);
        }

        List<QaPair> pairs = session.getQuestions().stream()
                .sorted(Comparator.comparingInt(Question::getOrderIndex))
                .map(q -> {
                    String answerText = "";
                    String inputType = "Text";
                    if (q.getAnswer() != null) {
                        inputType = StringUtils.hasText(q.getAnswer().getInputType())
                                ? q.getAnswer().getInputType()
                                : "Text";
                        if (StringUtils.hasText(q.getAnswer().getTextContent())) {
                            answerText = q.getAnswer().getTextContent();
                        } else if (StringUtils.hasText(q.getAnswer().getTranscript())) {
                            answerText = q.getAnswer().getTranscript();
                        }
                    }
                    return new QaPair(q.getText(), answerText, inputType);
                })
                .toList();

        EvaluationResult evaluation = aiService.evaluateSession(
                session.getInterviewType(), session.getDomain(), session.getDifficulty(), pairs);

        session.setStatus("Completed");
        session.setCompletedAt(Instant.now());

        ScoreReport report = session.getScoreReport();
        if (report == null) {
            report = new ScoreReport();
            report.setInterviewSession(session);
            session.setScoreReport(report);
        }
        report.setTechnicalScore(evaluation.technicalScore());
        report.setCommunicationScore(evaluation.communicationScore());
        report.setConfidenceScore(evaluation.confidenceScore());
        report.setProblemSolvingScore(evaluation.problemSolvingScore());
        report.setOverallScore(evaluation.overallScore());
        report.setFeedback(evaluation.feedback());
        report.setStrengths(evaluation.strengths());
        report.setWeaknesses(evaluation.weaknesses());
        report.setImprovements(evaluation.improvements());
        try {
            report.setQuestionReviewsJson(objectMapper.writeValueAsString(
                    evaluation.questionReviews() == null ? List.of() : evaluation.questionReviews()));
        } catch (Exception ex) {
            report.setQuestionReviewsJson("[]");
        }
        report.setCreatedAt(Instant.now());

        sessionRepository.save(session);
        return mapSession(session);
    }

    @Transactional
    public AnalyticsSummaryDto analytics(UUID userId) {
        List<InterviewSession> sessions = sessionRepository.findAllByUserIdWithScores(userId);
        List<InterviewSession> completed = sessions.stream()
                .filter(s -> s.getScoreReport() != null)
                .toList();
        List<ScoreReport> reports = completed.stream().map(InterviewSession::getScoreReport).toList();

        List<DomainTrendDto> trends = completed.stream()
                .collect(Collectors.groupingBy(InterviewSession::getDomain))
                .entrySet().stream()
                .map(e -> new DomainTrendDto(
                        e.getKey(),
                        e.getValue().size(),
                        round(e.getValue().stream().mapToDouble(s -> s.getScoreReport().getOverallScore()).average().orElse(0))))
                .sorted(Comparator.comparingInt(DomainTrendDto::sessions).reversed())
                .toList();

        int streak = computeStreakDays(completed);
        long weeklyCompleted = completed.stream()
                .filter(s -> s.getCompletedAt() != null
                        && s.getCompletedAt().isAfter(java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)))
                .count();

        return new AnalyticsSummaryDto(
                sessions.size(),
                completed.size(),
                reports.isEmpty() ? 0 : round(reports.stream().mapToDouble(ScoreReport::getOverallScore).average().orElse(0)),
                reports.isEmpty() ? 0 : round(reports.stream().mapToDouble(ScoreReport::getTechnicalScore).average().orElse(0)),
                reports.isEmpty() ? 0 : round(reports.stream().mapToDouble(ScoreReport::getCommunicationScore).average().orElse(0)),
                reports.isEmpty() ? 0 : round(reports.stream().mapToDouble(ScoreReport::getConfidenceScore).average().orElse(0)),
                reports.isEmpty() ? 0 : round(reports.stream().mapToDouble(ScoreReport::getProblemSolvingScore).average().orElse(0)),
                streak,
                3,
                (int) weeklyCompleted,
                sessions.stream().limit(10).map(this::mapSummary).toList(),
                trends);
    }

    private int computeStreakDays(List<InterviewSession> completed) {
        java.util.TreeSet<java.time.LocalDate> days = new java.util.TreeSet<>();
        for (InterviewSession s : completed) {
            if (s.getCompletedAt() != null) {
                days.add(java.time.LocalDate.ofInstant(s.getCompletedAt(), java.time.ZoneOffset.UTC));
            }
        }
        if (days.isEmpty()) return 0;
        java.time.LocalDate cursor = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        // Allow streak to continue if last activity was today or yesterday
        if (!days.contains(cursor) && !days.contains(cursor.minusDays(1))) {
            return 0;
        }
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    @Transactional
    public String htmlReport(UUID userId, UUID sessionId) {
        InterviewSession session = load(userId, sessionId);
        ScoreReport report = session.getScoreReport();
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete the interview before generating a report.");
        }

        StringBuilder qa = new StringBuilder();
        session.getQuestions().stream()
                .sorted(Comparator.comparingInt(Question::getOrderIndex))
                .forEach(q -> {
                    String answer = "(no answer)";
                    String inputType = "N/A";
                    if (q.getAnswer() != null) {
                        inputType = q.getAnswer().getInputType();
                        if (StringUtils.hasText(q.getAnswer().getTextContent())) {
                            answer = q.getAnswer().getTextContent();
                        } else if (StringUtils.hasText(q.getAnswer().getTranscript())) {
                            answer = q.getAnswer().getTranscript();
                        }
                    }
                    qa.append("<div class='qa'><h3>Q").append(q.getOrderIndex() + 1).append(". ")
                            .append(escape(q.getText())).append("</h3><p><strong>Answer (")
                            .append(escape(inputType)).append("):</strong> ")
                            .append(escape(answer)).append("</p>");
                    QuestionReviewDto review = findReview(report, q.getOrderIndex() + 1);
                    if (review != null) {
                        qa.append("<p><strong>Verdict:</strong> ").append(escape(review.verdict()))
                                .append(" (").append(String.format("%.0f", review.score())).append("/100)</p>");
                        if (StringUtils.hasText(review.explanation())) {
                            qa.append("<p><strong>Why:</strong> ").append(escape(review.explanation())).append("</p>");
                        }
                        if (review.showCorrectAnswer() && StringUtils.hasText(review.correctAnswer())) {
                            qa.append("<p class='correct'><strong>Correct / model answer:</strong> ")
                                    .append(escape(review.correctAnswer())).append("</p>");
                        }
                    }
                    qa.append("</div>");
                });

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8" />
                  <title>SelfSync Report - %s</title>
                  <style>
                    body { font-family: Georgia, serif; max-width: 860px; margin: 40px auto; color: #1a1a1a; line-height: 1.5; }
                    h1 { color: #0b3d2e; }
                    .scores { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin: 24px 0; }
                    .score { background: #f3f7f5; padding: 12px 16px; border-left: 4px solid #0b3d2e; }
                    .qa { margin: 18px 0; padding-bottom: 12px; border-bottom: 1px solid #ddd; }
                    .correct { background: #eef8f4; padding: 10px 12px; border-left: 4px solid #0b3d2e; }
                  </style>
                </head>
                <body>
                  <h1>SelfSync Interview Report</h1>
                  <p>%s · %s · %s</p>
                  <p>Completed: %s</p>
                  <div class="scores">
                    <div class="score"><strong>Overall</strong><div>%.1f</div></div>
                    <div class="score"><strong>Technical</strong><div>%.1f</div></div>
                    <div class="score"><strong>Communication</strong><div>%.1f</div></div>
                    <div class="score"><strong>Confidence</strong><div>%.1f</div></div>
                    <div class="score"><strong>Problem Solving</strong><div>%.1f</div></div>
                  </div>
                  <h2>Feedback</h2><p>%s</p>
                  <h2>Strengths</h2><p>%s</p>
                  <h2>Weaknesses</h2><p>%s</p>
                  <h2>Improvements</h2><p>%s</p>
                  <h2>Q&amp;A</h2>
                  %s
                </body>
                </html>
                """.formatted(
                escape(session.getDomain()),
                escape(session.getInterviewType()),
                escape(session.getDomain()),
                escape(session.getDifficulty()),
                session.getCompletedAt() == null ? "N/A" : session.getCompletedAt().toString(),
                report.getOverallScore(),
                report.getTechnicalScore(),
                report.getCommunicationScore(),
                report.getConfidenceScore(),
                report.getProblemSolvingScore(),
                escape(report.getFeedback()),
                escape(report.getStrengths()),
                escape(report.getWeaknesses()),
                escape(report.getImprovements()),
                qa);
    }

    private InterviewSession load(UUID userId, UUID sessionId) {
        return sessionRepository.findDetailedByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session not found."));
    }

    private void validate(CreateInterviewRequest request) {
        if (!InterviewCatalog.isValidType(request.interviewType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Interview type must be one of: " + String.join(", ", InterviewCatalog.TYPES));
        }
        if (!InterviewCatalog.isValidPair(request.interviewType(), request.domain())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Domain \"" + request.domain() + "\" is not valid for interview type \""
                            + request.interviewType() + "\". Choose a matching domain.");
        }
        if (!InterviewCatalog.isValidDifficulty(request.difficulty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Difficulty must be one of: " + String.join(", ", InterviewCatalog.DIFFICULTIES));
        }
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private InterviewSessionDto mapSession(InterviewSession session) {
        List<QuestionDto> questions = session.getQuestions().stream()
                .sorted(Comparator.comparingInt(Question::getOrderIndex))
                .map(q -> new QuestionDto(
                        q.getId(),
                        q.getOrderIndex(),
                        q.getText(),
                        q.getAnswer() == null ? null : mapAnswer(q.getAnswer())))
                .toList();
        ScoreReportDto score = session.getScoreReport() == null ? null : mapScore(session.getScoreReport());
        return new InterviewSessionDto(
                session.getId(),
                session.getInterviewType(),
                session.getDomain(),
                session.getDifficulty(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                questions,
                score);
    }

    private InterviewSummaryDto mapSummary(InterviewSession session) {
        Double overall = session.getScoreReport() == null ? null : session.getScoreReport().getOverallScore();
        return new InterviewSummaryDto(
                session.getId(),
                session.getInterviewType(),
                session.getDomain(),
                session.getDifficulty(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getCompletedAt(),
                overall);
    }

    private AnswerDto mapAnswer(Answer answer) {
        return new AnswerDto(
                answer.getId(),
                answer.getInputType(),
                answer.getTextContent(),
                answer.getTranscript(),
                answer.getMediaPath(),
                answer.getSubmittedAt());
    }

    private ScoreReportDto mapScore(ScoreReport report) {
        return new ScoreReportDto(
                report.getId(),
                report.getTechnicalScore(),
                report.getCommunicationScore(),
                report.getConfidenceScore(),
                report.getProblemSolvingScore(),
                report.getOverallScore(),
                report.getFeedback(),
                report.getStrengths(),
                report.getWeaknesses(),
                report.getImprovements(),
                report.getCreatedAt(),
                readReviews(report));
    }

    private List<QuestionReviewDto> readReviews(ScoreReport report) {
        if (report == null || !StringUtils.hasText(report.getQuestionReviewsJson())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(report.getQuestionReviewsJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private QuestionReviewDto findReview(ScoreReport report, int questionIndex) {
        return readReviews(report).stream()
                .filter(r -> r.questionIndex() == questionIndex)
                .findFirst()
                .orElse(null);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
