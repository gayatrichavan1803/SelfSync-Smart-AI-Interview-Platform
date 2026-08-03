package com.selfsync.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Allowed interview type ↔ domain combinations. */
public final class InterviewCatalog {
    private InterviewCatalog() {}

    public static final List<String> TYPES = List.of("Technical", "HR", "Aptitude", "Coding", "System Design");
    public static final List<String> DIFFICULTIES = List.of("Easy", "Medium", "Hard", "Expert");

    public static final Map<String, List<String>> DOMAINS_BY_TYPE = Map.of(
            "Technical", List.of(
                    "Java", "Python", "JavaScript", "TypeScript", "SQL", "React", "Node.js",
                    "Spring Boot", "C++", "Go", "DevOps", "Cloud AWS", "Machine Learning", "Android"),
            "HR", List.of(
                    "Behavioral", "Leadership", "Culture Fit", "Situational", "Career Goals",
                    "Teamwork", "Conflict Resolution", "Communication"),
            "Aptitude", List.of(
                    "Quantitative", "Logical Reasoning", "Verbal Ability", "Data Interpretation",
                    "Puzzles", "Probability", "Number Series"),
            "Coding", List.of(
                    "Arrays & Strings", "Linked Lists", "Trees & Graphs", "Dynamic Programming",
                    "Recursion", "Sorting & Searching", "Hashing", "System Design Lite"),
            "System Design", List.of(
                    "Scalability", "Databases", "Caching", "Messaging Queues", "API Design",
                    "Microservices", "Reliability", "Security Architecture")
    );

    public static Set<String> allDomains() {
        return DOMAINS_BY_TYPE.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static boolean isValidType(String type) {
        return TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(type));
    }

    public static boolean isValidDifficulty(String difficulty) {
        return DIFFICULTIES.stream().anyMatch(d -> d.equalsIgnoreCase(difficulty));
    }

    public static boolean isValidPair(String type, String domain) {
        if (type == null || domain == null) return false;
        List<String> domains = domainsFor(type);
        return domains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    public static List<String> domainsFor(String type) {
        if (type == null) return List.of();
        for (Map.Entry<String, List<String>> e : DOMAINS_BY_TYPE.entrySet()) {
            if (e.getKey().equalsIgnoreCase(type)) return e.getValue();
        }
        return List.of();
    }

    public static String canonicalType(String type) {
        return TYPES.stream().filter(t -> t.equalsIgnoreCase(type)).findFirst().orElse(type);
    }

    public static String canonicalDomain(String type, String domain) {
        return domainsFor(type).stream()
                .filter(d -> d.equalsIgnoreCase(domain))
                .findFirst()
                .orElse(domain);
    }

    public static String canonicalDifficulty(String difficulty) {
        return DIFFICULTIES.stream().filter(d -> d.equalsIgnoreCase(difficulty)).findFirst().orElse(difficulty);
    }

    public static Map<String, Object> asPublicCatalog() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("types", TYPES);
        map.put("difficulties", DIFFICULTIES);
        map.put("domainsByType", DOMAINS_BY_TYPE);
        return map;
    }

    public static String typeHint(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "hr" -> "behavioral/STAR, soft skills, workplace judgment";
            case "aptitude" -> "quantitative, logical, verbal problem-solving with clear expected reasoning";
            case "coding" -> "algorithmic problem-solving, complexity analysis, edge cases";
            case "system design" -> "architecture, tradeoffs, scalability, reliability";
            default -> "technical knowledge, applied reasoning, and practical scenarios";
        };
    }
}
