package com.codehouse.ciciassistant.mcp.service;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Resolves short-lived MCP transport credentials; secrets remain encrypted at rest. */
@Service
public class McpAuthenticationService {
    private final SecretCipherService secrets;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ConcurrentHashMap<Long, CachedToken> tokens = new ConcurrentHashMap<>();

    public McpAuthenticationService(SecretCipherService secrets, ObjectMapper objectMapper) {
        this.secrets = secrets;
        this.objectMapper = objectMapper;
    }

    public Map<String, String> headers(McpServerEntity server) {
        if (!"KEYCLOAK_CLIENT_CREDENTIALS".equals(server.getAuthType())) return Map.of();
        CachedToken cached = tokens.get(server.getId());
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return Map.of("Authorization", "Bearer " + cached.value());
        }
        CachedToken refreshed = requestToken(server);
        tokens.put(server.getId(), refreshed);
        return Map.of("Authorization", "Bearer " + refreshed.value());
    }

    public void invalidate(Long serverId) {
        if (serverId != null) tokens.remove(serverId);
    }

    private CachedToken requestToken(McpServerEntity server) {
        try {
            String secret = secrets.decryptUtf8(server.getClientSecretCipher(), server.getClientSecretIv());
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "client_credentials");
            form.put("client_id", server.getClientId());
            form.put("client_secret", secret);
            if (server.getTokenAudience() != null && !server.getTokenAudience().isBlank()) form.put("audience", server.getTokenAudience());
            if (server.getTokenScopes() != null && !server.getTokenScopes().isBlank()) form.put("scope", server.getTokenScopes());
            String body = form.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(server.getTokenUrl()))
                    .timeout(Duration.ofSeconds(Math.min(server.getTimeoutSeconds(), 30)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode value = objectMapper.readTree(response.body());
            if (response.statusCode() >= 400 || value.path("access_token").asText().isBlank()) {
                throw new IllegalStateException("Keycloak client_credentials failed: HTTP " + response.statusCode());
            }
            long expiresIn = Math.max(60, value.path("expires_in").asLong(300));
            return new CachedToken(value.path("access_token").asText(), Instant.now().plusSeconds(expiresIn));
        } catch (Exception exception) {
            throw new IllegalStateException("MCP Keycloak authentication failed", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant expiresAt) {}
}
