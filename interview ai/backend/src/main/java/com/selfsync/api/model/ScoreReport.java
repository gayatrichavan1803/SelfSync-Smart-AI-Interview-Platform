package com.selfsync.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "score_reports")
public class ScoreReport {
    public ScoreReport() {
        this.id = UUID.randomUUID();
    }

    @Id
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_session_id", nullable = false, unique = true)
    private InterviewSession interviewSession;

    private double technicalScore;
    private double communicationScore;
    private double confidenceScore;
    private double problemSolvingScore;
    private double overallScore;

    @Lob
    @Column(nullable = false)
    private String feedback = "";

    @Lob
    @Column(nullable = false)
    private String strengths = "";

    @Lob
    @Column(nullable = false)
    private String weaknesses = "";

    @Lob
    @Column(nullable = false)
    private String improvements = "";

    /** JSON array of per-question reviews (verdict + correct answer when wrong). */
    @Lob
    @Column
    private String questionReviewsJson = "[]";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public InterviewSession getInterviewSession() { return interviewSession; }
    public void setInterviewSession(InterviewSession interviewSession) { this.interviewSession = interviewSession; }
    public double getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(double technicalScore) { this.technicalScore = technicalScore; }
    public double getCommunicationScore() { return communicationScore; }
    public void setCommunicationScore(double communicationScore) { this.communicationScore = communicationScore; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    public double getProblemSolvingScore() { return problemSolvingScore; }
    public void setProblemSolvingScore(double problemSolvingScore) { this.problemSolvingScore = problemSolvingScore; }
    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public String getQuestionReviewsJson() { return questionReviewsJson; }
    public void setQuestionReviewsJson(String questionReviewsJson) { this.questionReviewsJson = questionReviewsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
