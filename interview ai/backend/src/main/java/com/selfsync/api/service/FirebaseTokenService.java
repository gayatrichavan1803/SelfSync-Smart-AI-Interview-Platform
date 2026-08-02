package com.selfsync.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfsync.api.config.SelfSyncProperties;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FirebaseTokenService {
    private final RestClient restClient;
    private final SelfSyncProperties properties;
    private final ObjectMapper objectMapper;

    public FirebaseTokenService(RestClient.Builder builder, SelfSyncProperties properties, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://identitytoolkit.googleapis.com").build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record FirebaseIdentity(
            String uid,
            String email,
            String displayName,
            String photoUrl,
            boolean emailVerified,
            String signInProvider
    ) {}

    public FirebaseIdentity verifyIdToken(String idToken) {
        String apiKey = resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase is not configured. Set selfsync.firebase.api-key in application.yml.");
        }
        try {
            String body = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/accounts:lookup")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("idToken", idToken))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode users = root.path("users");
            if (!users.isArray() || users.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Firebase token.");
            }
            JsonNode user = users.get(0);
            String provider = "firebase";
            JsonNode providerInfo = user.path("providerUserInfo");
            if (providerInfo.isArray() && !providerInfo.isEmpty()) {
                provider = providerInfo.get(0).path("providerId").asText("firebase");
            }
            String email = user.path("email").asText(null);
            if (!StringUtils.hasText(email)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firebase account has no email.");
            }
            return new FirebaseIdentity(
                    user.path("localId").asText(),
                    email.toLowerCase(),
                    user.path("displayName").asText(""),
                    user.path("photoUrl").asText(null),
                    user.path("emailVerified").asBoolean(false),
                    provider);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Could not verify Firebase token.", ex);
        }
    }

    private String resolveApiKey() {
        String key = properties.getFirebase().getApiKey();
        if (!StringUtils.hasText(key) || "YOUR_FIREBASE_WEB_API_KEY".equals(key)) {
            key = System.getenv("FIREBASE_WEB_API_KEY");
        }
        if (!StringUtils.hasText(key) || "YOUR_FIREBASE_WEB_API_KEY".equals(key)) {
            key = System.getenv("SELFSYNC_FIREBASE_API_KEY");
        }
        if (!StringUtils.hasText(key) || "YOUR_FIREBASE_WEB_API_KEY".equals(key)) {
            return null;
        }
        return key.trim();
    }
}
