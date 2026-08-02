package com.selfsync.api.service;

import com.selfsync.api.dto.ApiDtos.LearningRecommendationsDto;
import com.selfsync.api.dto.ApiDtos.LearningResourceDto;
import com.selfsync.api.model.InterviewSession;
import com.selfsync.api.model.ScoreReport;
import com.selfsync.api.repository.InterviewSessionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LearningService {
    private final InterviewSessionRepository sessionRepository;

    public LearningService(InterviewSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public LearningRecommendationsDto recommendations(UUID userId) {
        List<InterviewSession> sessions = sessionRepository.findAllByUserIdWithScores(userId);
        Set<String> focus = new LinkedHashSet<>();
        String lastDomain = "General";

        for (InterviewSession s : sessions) {
            ScoreReport r = s.getScoreReport();
            if (r == null) continue;
            lastDomain = s.getDomain();
            if (r.getTechnicalScore() < 70) focus.add("Technical depth");
            if (r.getCommunicationScore() < 70) focus.add("Communication");
            if (r.getConfidenceScore() < 70) focus.add("Confidence");
            if (r.getProblemSolvingScore() < 70) focus.add("Problem solving");
            if (StringUtils.hasText(r.getWeaknesses())) {
                String w = r.getWeaknesses().toLowerCase(Locale.ROOT);
                if (w.contains("system design")) focus.add("System design");
                if (w.contains("sql") || w.contains("database")) focus.add("SQL");
                if (w.contains("algorithm") || w.contains("dsa")) focus.add("Algorithms");
                if (w.contains("star") || w.contains("behavioral")) focus.add("Behavioral / STAR");
            }
        }
        if (focus.isEmpty()) {
            focus.add("Interview fundamentals");
            focus.add("Communication");
        }

        List<LearningResourceDto> resources = new ArrayList<>();
        for (String skill : focus) {
            resources.addAll(resourcesFor(skill, lastDomain));
        }
        return new LearningRecommendationsDto(new ArrayList<>(focus), resources.stream().limit(8).toList());
    }

    private List<LearningResourceDto> resourcesFor(String skill, String domain) {
        String d = domain == null ? "General" : domain;
        return switch (skill) {
            case "Technical depth" -> List.of(
                    new LearningResourceDto(
                            d + " deep dive",
                            "Practice core " + d + " concepts with examples and tradeoffs.",
                            "https://www.geeksforgeeks.org/",
                            skill,
                            "Medium"),
                    new LearningResourceDto(
                            "Official docs",
                            "Read primary documentation for " + d + ".",
                            "https://developer.mozilla.org/",
                            skill,
                            "Easy"));
            case "Communication" -> List.of(
                    new LearningResourceDto(
                            "Explain like an interviewer",
                            "Practice clear structure: answer → reason → example.",
                            "https://www.toastmasters.org/",
                            skill,
                            "Easy"));
            case "Confidence" -> List.of(
                    new LearningResourceDto(
                            "Mock interview cadence",
                            "Do short daily drills to reduce hesitation.",
                            "https://www.pramp.com/",
                            skill,
                            "Easy"));
            case "Problem solving" -> List.of(
                    new LearningResourceDto(
                            "Pattern practice",
                            "Solve 3 problems focusing on approach first, then code.",
                            "https://leetcode.com/problemset/",
                            skill,
                            "Medium"));
            case "System design" -> List.of(
                    new LearningResourceDto(
                            "System Design Primer",
                            "Learn scalability, storage, and tradeoff frameworks.",
                            "https://github.com/donnemartin/system-design-primer",
                            skill,
                            "Hard"));
            case "SQL" -> List.of(
                    new LearningResourceDto(
                            "SQLBolt",
                            "Interactive SQL lessons for joins and aggregation.",
                            "https://sqlbolt.com/",
                            skill,
                            "Easy"));
            case "Algorithms" -> List.of(
                    new LearningResourceDto(
                            "DSA roadmap",
                            "Arrays → hashing → trees → graphs with spaced practice.",
                            "https://neetcode.io/",
                            skill,
                            "Medium"));
            case "Behavioral / STAR" -> List.of(
                    new LearningResourceDto(
                            "STAR stories workbook",
                            "Write 5 STAR stories for leadership, conflict, failure, ownership.",
                            "https://www.themuse.com/advice/star-interview-method",
                            skill,
                            "Easy"));
            default -> List.of(
                    new LearningResourceDto(
                            "Interview prep checklist",
                            "Warm-up questions, score review, and weekly goals.",
                            "https://www.indeed.com/career-advice/interviewing",
                            skill,
                            "Easy"));
        };
    }
}
