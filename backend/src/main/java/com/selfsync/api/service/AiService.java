package com.selfsync.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfsync.api.config.SelfSyncProperties;
import com.selfsync.api.dto.ApiDtos.AiStatusDto;
import com.selfsync.api.dto.ApiDtos.EvaluationResult;
import com.selfsync.api.dto.ApiDtos.QaPair;
import com.selfsync.api.dto.ApiDtos.QuestionReviewDto;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestClient restClient;
    private final SelfSyncProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public AiService(RestClient.Builder builder, SelfSyncProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = properties.getGroq().getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "https://api.groq.com/openai/v1";
        }
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private boolean hasApiKey() {
        return StringUtils.hasText(resolveApiKey());
    }

    private String resolveApiKey() {
        String key = properties.getGroq().getApiKey();
        if (!StringUtils.hasText(key) || "YOUR_GROQ_API_KEY".equals(key)) {
            key = System.getenv("GROQ_API_KEY");
        }
        if (!StringUtils.hasText(key) || "YOUR_GROQ_API_KEY".equals(key)) {
            key = System.getenv("SELFSYNC_GROQ_API_KEY");
        }
        if (!StringUtils.hasText(key) || "YOUR_GROQ_API_KEY".equals(key)) {
            return null;
        }
        return key.trim();
    }

    private String baseUrl() {
        String baseUrl = properties.getGroq().getBaseUrl();
        return StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/$", "") : "https://api.groq.com/openai/v1";
    }

    public List<String> generateQuestions(String interviewType, String domain, String difficulty, int count) {
        if (!hasApiKey()) {
            return fallbackQuestions(interviewType, domain, count);
        }
        String varietySeed = UUID.randomUUID().toString().substring(0, 8);
        String focus = InterviewCatalog.typeHint(interviewType);
        String prompt = """
                You are a senior hiring manager creating a realistic mock interview for SelfSync.
                Generate exactly %d brand-new %s-level "%s" interview questions focused on domain "%s".

                Session variety token: %s
                Interview focus: %s

                Rules:
                - EVERY question must be different from typical textbook repeats; invent fresh angles, scenarios, and constraints.
                - Do NOT reuse classic stock questions (e.g. generic "tell me about yourself", "what is OOP", "reverse a linked list" unless reframed with a novel scenario).
                - Mix conceptual, practical, debugging, and scenario-based prompts.
                - Stay strictly relevant to interview type "%s" and domain "%s" (do not drift into unrelated domains).
                - Match difficulty: Easy = fundamentals, Medium = applied reasoning, Hard = depth/tradeoffs, Expert = ambiguous production-scale judgment.
                - For Aptitude: include numbers/logic with a clear expected approach.
                - For Coding: ask problem-solving questions (describe approach/complexity), not full code dumps unless needed.
                - For System Design: ask architecture/tradeoff questions.
                - For HR: behavioral/STAR with specific workplace scenarios.
                - Do not number the questions.
                - Return ONLY JSON object: {"questions":["q1","q2",...]}
                """.formatted(
                count, difficulty, interviewType, domain, varietySeed, focus, interviewType, domain);
        try {
            String content = chatCompletion(prompt, true, 0.95);
            JsonNode root = objectMapper.readTree(stripMarkdown(content));
            List<String> questions;
            if (root.isArray()) {
                questions = objectMapper.convertValue(root, new TypeReference<>() {});
            } else if (root.has("questions") && root.get("questions").isArray()) {
                questions = objectMapper.convertValue(root.get("questions"), new TypeReference<>() {});
            } else {
                questions = List.of();
            }
            if (questions != null && !questions.isEmpty()) {
                return questions.stream()
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .limit(count)
                        .toList();
            }
        } catch (Exception ex) {
            log.warn("Groq question generation failed; using fallback", ex);
        }
        return fallbackQuestions(interviewType, domain, count);
    }

    public EvaluationResult evaluateSession(String interviewType, String domain, String difficulty, List<QaPair> pairs) {
        if (!hasApiKey()) {
            return fallbackEvaluation(pairs);
        }
        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            QaPair p = pairs.get(i);
            String modality = StringUtils.hasText(p.inputType()) ? p.inputType() : "Text";
            String answer = StringUtils.hasText(p.answer()) ? p.answer() : "(no answer provided)";
            qaText.append("Q").append(i + 1).append(": ").append(p.question())
                    .append("\nResponse mode: ").append(modality)
                    .append("\nA").append(i + 1).append(": ").append(answer).append("\n\n");
        }
        String prompt = """
                You are SelfSync, an expert interview coach writing personalized feedback.
                Evaluate this %s %s interview in the "%s" domain.

                Candidate responses (may be typed text, or transcripts from voice/video):
                %s

                Scoring instructions (0-100 each):
                - technicalScore: correctness/domain knowledge (for HR use judgment/role fit quality)
                - communicationScore: clarity, structure, vocabulary
                - confidenceScore: assertiveness and completeness of response
                - problemSolvingScore: reasoning, examples, tradeoff thinking
                - overallScore: weighted overall impression

                Feedback must be personalized:
                - Reference specific answers (e.g. "In Q2 you mentioned...")
                - Call out response mode when relevant (voice/video clarity vs typed depth)
                - Give concrete next-practice actions
                - Avoid generic praise

                Also grade EACH question individually in questionReviews:
                - questionIndex: 1-based index matching Q1, Q2, ...
                - verdict: "correct" | "partial" | "incorrect" | "unanswered"
                - score: 0-100 for that answer alone
                - correctAnswer: a clear model/ideal answer (2-6 sentences). REQUIRED when verdict is incorrect or partial or unanswered. For HR/behavioral, give a strong sample STAR-style answer.
                - explanation: short why the candidate was wrong/incomplete and what to improve

                Return ONLY valid JSON:
                {
                  "technicalScore": number,
                  "communicationScore": number,
                  "confidenceScore": number,
                  "problemSolvingScore": number,
                  "overallScore": number,
                  "feedback": "2-4 sentence personalized summary",
                  "strengths": "specific strengths tied to answers",
                  "weaknesses": "specific gaps tied to answers",
                  "improvements": "actionable practice plan",
                  "questionReviews": [
                    {
                      "questionIndex": 1,
                      "verdict": "incorrect",
                      "score": 30,
                      "correctAnswer": "ideal answer text",
                      "explanation": "why this was wrong"
                    }
                  ]
                }
                """.formatted(difficulty, interviewType, domain, qaText);
        try {
            String content = chatCompletion(prompt, true, 0.35);
            JsonNode node = objectMapper.readTree(stripMarkdown(content));
            double technical = clamp(node.path("technicalScore").asDouble());
            double communication = clamp(node.path("communicationScore").asDouble());
            double confidence = clamp(node.path("confidenceScore").asDouble());
            double problemSolving = clamp(node.path("problemSolvingScore").asDouble());
            double overall = node.path("overallScore").asDouble(0);
            if (overall <= 0) {
                overall = (technical + communication + confidence + problemSolving) / 4.0;
            }
            return new EvaluationResult(
                    technical,
                    communication,
                    confidence,
                    problemSolving,
                    clamp(overall),
                    textOr(node, "feedback", "No feedback provided."),
                    textOr(node, "strengths", ""),
                    textOr(node, "weaknesses", ""),
                    textOr(node, "improvements", ""),
                    parseQuestionReviews(node.path("questionReviews"), pairs));
        } catch (Exception ex) {
            log.warn("Groq evaluation failed; using fallback", ex);
            return fallbackEvaluation(pairs);
        }
    }

    public AiStatusDto checkStatus() {
        String model = StringUtils.hasText(properties.getGroq().getModel())
                ? properties.getGroq().getModel()
                : "llama-3.3-70b-versatile";
        if (!hasApiKey()) {
            return new AiStatusDto(false, false, "Groq", model,
                    "Groq API key is not configured. AI questions and scoring will use offline fallbacks.");
        }
        try {
            String content = chatCompletion(
                    "Reply with JSON only: {\"ok\":true,\"service\":\"SelfSync\"}",
                    true,
                    0.1);
            JsonNode node = objectMapper.readTree(stripMarkdown(content));
            boolean ok = node.path("ok").asBoolean(true) || content.toLowerCase().contains("ok");
            return new AiStatusDto(true, ok, "Groq", model,
                    ok ? "Groq is connected and responding. AI interviews are live for everyone."
                            : "Groq key is set but the probe response was unexpected.");
        } catch (Exception ex) {
            log.warn("Groq health check failed", ex);
            String detail = ex.getMessage() == null ? "unknown error" : ex.getMessage();
            if (detail.length() > 160) detail = detail.substring(0, 160) + "…";
            return new AiStatusDto(true, false, "Groq", model, "Groq check failed: " + detail);
        }
    }

    public String transcribeAudio(byte[] bytes, String fileName, String contentType) {
        if (!hasApiKey()) {
            return "[Transcription unavailable: configure selfsync.groq.api-key. Candidate should also provide text if possible.]";
        }
        try {
            String boundary = "----SelfSyncBoundary" + UUID.randomUUID().toString().replace("-", "");
            String safeName = StringUtils.hasText(fileName) ? fileName : "audio.webm";
            String partContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
            String whisperModel = StringUtils.hasText(properties.getGroq().getWhisperModel())
                    ? properties.getGroq().getWhisperModel()
                    : "whisper-large-v3";

            byte[] preamble = (
                    "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                    + "Content-Type: " + partContentType + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8);

            byte[] mid = (
                    "\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + whisperModel + "\r\n"
                    + "--" + boundary + "--\r\n"
            ).getBytes(StandardCharsets.UTF_8);

            byte[] body = new byte[preamble.length + bytes.length + mid.length];
            System.arraycopy(preamble, 0, body, 0, preamble.length);
            System.arraycopy(bytes, 0, body, preamble.length, bytes.length);
            System.arraycopy(mid, 0, body, preamble.length + bytes.length, mid.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/audio/transcriptions"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + resolveApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                log.warn("Groq Whisper rate limited: {}", response.body());
                return "[Transcription failed: Groq rate limit hit. Wait a bit or use the text notes box.]";
            }
            if (response.statusCode() >= 400) {
                log.warn("Groq Whisper transcription failed: {} {}", response.statusCode(), response.body());
                return "[Transcription failed]";
            }
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("text").asText("");
        } catch (Exception ex) {
            log.warn("Groq Whisper transcription failed", ex);
            return "[Transcription failed]";
        }
    }

    private String chatCompletion(String userPrompt, boolean jsonMode, double temperature) throws Exception {
        String model = StringUtils.hasText(properties.getGroq().getModel())
                ? properties.getGroq().getModel()
                : "llama-3.3-70b-versatile";
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are SelfSync, an AI interview coach. Always respond with strict JSON when asked."),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        String body;
        try {
            body = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + resolveApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new IllegalStateException("Groq rate limit exceeded. Wait a moment and try again.", ex);
            }
            throw ex;
        }
        JsonNode root = objectMapper.readTree(body);
        return root.path("choices").path(0).path("message").path("content").asText("[]");
    }

    private static String stripMarkdown(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl >= 0) trimmed = trimmed.substring(nl + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String v = node.path(field).asText(null);
        return v == null ? fallback : v;
    }

    private static List<String> fallbackQuestions(String interviewType, String domain, int count) {
        List<String> pool = new ArrayList<>();
        String d = StringUtils.hasText(domain) ? domain : "general";
        String type = interviewType == null ? "Technical" : interviewType;

        if ("HR".equalsIgnoreCase(type)) {
            pool.addAll(List.of(
                    "Describe a time in a " + d + " context when priorities conflicted. How did you decide?",
                    "Tell me about feedback you received that changed how you work with others.",
                    "Walk through a STAR story where you influenced a decision without authority.",
                    "How would you handle a teammate missing deadlines repeatedly on a shared goal?",
                    "What motivates you long-term, and how does this role fit that path?",
                    "Share a mistake you owned publicly and what process you improved afterward.",
                    "How do you build trust quickly with a new manager or cross-functional partner?",
                    "Describe a cultural value you need on a team to do your best work."));
        } else if ("Aptitude".equalsIgnoreCase(type)) {
            pool.addAll(List.of(
                    "In " + d + ": a shop offers 15% off then an extra 10% off. Is that the same as 25% off? Prove it.",
                    "A train covers 150 km in 2.5 hours. How long for 210 km at the same speed?",
                    "Sequence in " + d + " style: 3, 8, 15, 24, 35, ?. Explain the rule.",
                    "If 6 people finish a job in 10 days, how many days for 15 people (same rate)?",
                    "A bag has 4 red and 6 blue balls. Probability of drawing 2 red without replacement?",
                    "Interpret: sales rose 20% then fell 20%. Net change vs original?",
                    "Find the odd one out and justify: 16, 25, 36, 48, 64.",
                    "Pipe A fills a tank in 6h, B in 8h. Time together?"));
        } else if ("Coding".equalsIgnoreCase(type)) {
            pool.addAll(List.of(
                    "For " + d + ": outline an approach and complexity for finding duplicates in a stream of integers.",
                    "Design a " + d + " solution that handles empty input and overflow edge cases.",
                    "Compare two strategies for " + d + " and when you'd pick each.",
                    "How would you test a " + d + " implementation for worst-case inputs?",
                    "Explain space/time tradeoffs for a " + d + " problem you've solved.",
                    "Walk through debugging a wrong output on a " + d + " interview problem.",
                    "Given constraints that scale to millions of items, how does your " + d + " approach change?",
                    "Describe invariants you'd maintain while mutating a " + d + " structure."));
        } else if ("System Design".equalsIgnoreCase(type)) {
            pool.addAll(List.of(
                    "Design a high-level architecture focused on " + d + " for a URL shortener.",
                    "How would " + d + " choices change if traffic spikes 50x overnight?",
                    "What failure modes worry you most around " + d + ", and how do you mitigate them?",
                    "Compare consistency vs availability in a system where " + d + " is critical.",
                    "Sketch data flow and bottlenecks for a feed/service emphasizing " + d + ".",
                    "How would you observe and alert on " + d + " health in production?",
                    "Propose an evolution path: MVP → scale for " + d + ".",
                    "Security/privacy concerns tied to " + d + " — what controls would you add?"));
        } else {
            pool.addAll(List.of(
                    "Explain a " + d + " concept juniors often misuse, with a correct mental model.",
                    "Design a small feature using " + d + " best practices and call out tradeoffs.",
                    "How do you diagnose a production issue involving " + d + "?",
                    "Compare two common " + d + " approaches and when each wins.",
                    "How would you improve performance in a " + d + " subsystem under load?",
                    "What testing strategy would you use for a risky " + d + " change?",
                    "Describe a " + d + " design smell you've fixed and the refactor.",
                    "Walk through securing or hardening a " + d + " component."));
        }

        Collections.shuffle(pool, new java.util.Random());
        List<String> picked = new ArrayList<>();
        for (String q : pool) {
            if (picked.size() >= count) break;
            picked.add(q);
        }
        while (picked.size() < count) {
            picked.add("Give a structured answer for a realistic " + type + " scenario in " + d
                    + " (variety #" + (picked.size() + 1) + ").");
        }
        return picked;
    }

    private static EvaluationResult fallbackEvaluation(List<QaPair> pairs) {
        long answered = pairs.stream()
                .filter(p -> StringUtils.hasText(p.answer()) && !p.answer().startsWith("["))
                .count();
        double avgLen = pairs.stream()
                .filter(p -> StringUtils.hasText(p.answer()))
                .mapToInt(p -> p.answer().length())
                .average()
                .orElse(0);
        double base = Math.min(90, 40 + answered * 8 + Math.min(20, avgLen / 40.0));
        List<QuestionReviewDto> reviews = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            QaPair p = pairs.get(i);
            boolean has = StringUtils.hasText(p.answer()) && !p.answer().startsWith("[");
            String verdict = !has ? "unanswered" : (p.answer().length() < 40 ? "partial" : "correct");
            boolean show = !"correct".equals(verdict);
            reviews.add(new QuestionReviewDto(
                    i + 1,
                    verdict,
                    !has ? 0 : (p.answer().length() < 40 ? 45 : 75),
                    show,
                    show ? "Ideal answer unavailable offline. Configure Groq for full model answers. Key points: address the question directly, give a concrete example, and state tradeoffs or outcome." : "",
                    show ? "Offline review: answer was missing or too brief for full credit." : "Offline review: answer length looked adequate."));
        }
        return new EvaluationResult(
                round(base),
                round(Math.min(90, base - 3)),
                round(Math.min(90, base - 5)),
                round(Math.min(90, base + 2)),
                round(base),
                "Offline scoring used (Groq key not configured). Scores estimate completeness and answer length. Configure selfsync.groq.api-key for AI evaluation.",
                answered > 0 ? "Attempted multiple questions with substantive text." : "Limited responses provided.",
                answered < pairs.size() ? "Some questions unanswered or too brief." : "Could deepen technical detail and structure.",
                "Practice structured answers (STAR for HR; problem→approach→tradeoffs for technical). Add concrete examples.",
                reviews);
    }

    private List<QuestionReviewDto> parseQuestionReviews(JsonNode arrayNode, List<QaPair> pairs) {
        List<QuestionReviewDto> reviews = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                int index = item.path("questionIndex").asInt(reviews.size() + 1);
                String verdict = item.path("verdict").asText("partial").trim().toLowerCase();
                if (!List.of("correct", "partial", "incorrect", "unanswered").contains(verdict)) {
                    verdict = "partial";
                }
                double score = clamp(item.path("score").asDouble(verdict.equals("correct") ? 85 : 40));
                String correctAnswer = textOr(item, "correctAnswer", "");
                String explanation = textOr(item, "explanation", "");
                boolean show = !"correct".equals(verdict);
                if (show && !StringUtils.hasText(correctAnswer)) {
                    correctAnswer = "A strong answer should directly address the question with clear reasoning and a concrete example.";
                }
                reviews.add(new QuestionReviewDto(index, verdict, score, show, show ? correctAnswer : "", explanation));
            }
        }
        if (reviews.size() < pairs.size()) {
            for (int i = reviews.size(); i < pairs.size(); i++) {
                QaPair p = pairs.get(i);
                boolean has = StringUtils.hasText(p.answer());
                String verdict = has ? "partial" : "unanswered";
                reviews.add(new QuestionReviewDto(
                        i + 1,
                        verdict,
                        has ? 50 : 0,
                        true,
                        "Review the question and provide a complete, structured answer with examples.",
                        has ? "Could not fully grade this answer; treat as incomplete." : "No answer was provided."));
            }
        }
        return reviews;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
