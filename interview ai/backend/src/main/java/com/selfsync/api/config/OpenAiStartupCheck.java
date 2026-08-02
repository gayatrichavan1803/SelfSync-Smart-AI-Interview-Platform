package com.selfsync.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiStartupCheck {
    private static final Logger log = LoggerFactory.getLogger(OpenAiStartupCheck.class);
    private final SelfSyncProperties properties;

    public OpenAiStartupCheck(SelfSyncProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logKeyStatus() {
        String key = properties.getGroq().getApiKey();
        boolean configured = StringUtils.hasText(key) && !"YOUR_GROQ_API_KEY".equals(key);
        if (configured) {
            log.info("Groq API key is configured (length={}). Model={} Whisper={}",
                    key.trim().length(),
                    properties.getGroq().getModel(),
                    properties.getGroq().getWhisperModel());
        } else {
            log.warn("Groq API key is NOT configured. Set selfsync.groq.api-key (or GROQ_API_KEY) and restart.");
        }

        String firebaseKey = properties.getFirebase().getApiKey();
        boolean firebaseOk = StringUtils.hasText(firebaseKey) && !"YOUR_FIREBASE_WEB_API_KEY".equals(firebaseKey);
        if (firebaseOk) {
            log.info("Firebase is configured (api-key length={}, projectId={})",
                    firebaseKey.trim().length(),
                    properties.getFirebase().getProjectId());
        } else {
            log.warn("Firebase is NOT configured. Set selfsync.firebase.api-key and project-id, then restart.");
        }
    }
}
