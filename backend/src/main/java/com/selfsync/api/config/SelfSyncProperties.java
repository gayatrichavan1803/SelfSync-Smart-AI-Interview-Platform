package com.selfsync.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "selfsync")
public class SelfSyncProperties {
    private final Jwt jwt = new Jwt();
    private final Groq groq = new Groq();
    private final Firebase firebase = new Firebase();
    private final Cors cors = new Cors();

    public Jwt getJwt() { return jwt; }
    public Groq getGroq() { return groq; }
    public Firebase getFirebase() { return firebase; }
    public Cors getCors() { return cors; }

    public static class Jwt {
        private String secret;
        private String issuer;
        private String audience;
        private long expiresMinutes = 10080;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public long getExpiresMinutes() { return expiresMinutes; }
        public void setExpiresMinutes(long expiresMinutes) { this.expiresMinutes = expiresMinutes; }
    }

    public static class Groq {
        private String apiKey;
        private String model = "llama-3.3-70b-versatile";
        private String whisperModel = "whisper-large-v3";
        private String baseUrl = "https://api.groq.com/openai/v1";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getWhisperModel() { return whisperModel; }
        public void setWhisperModel(String whisperModel) { this.whisperModel = whisperModel; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Firebase {
        /** Firebase Web API key used to verify ID tokens via Identity Toolkit. */
        private String apiKey = "";
        private String projectId = "";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
