package com.codehouse.ciciassistant.semattice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Trusted control-plane client for Semattice's immutable DevAutopilot authorization template. */
@Component
public class SematticeDevAutopilotAuthorizationClient {
    public static final String TEMPLATE_VERSION = "devautopilot.authorization.v1";
    private static final String PATH = "/internal/v1/devautopilot-authorization-templates";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SematticeProvisioningClient signer;
    private final String baseUrl;

    public SematticeDevAutopilotAuthorizationClient(RestClient.Builder restClientBuilder,
                                                     ObjectMapper objectMapper,
                                                     SematticeProvisioningClient signer,
                                                     @org.springframework.beans.factory.annotation.Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public AuthorizationView apply(String companyId, String activationId, String idempotencyKey,
                                   List<Assignment> assignments) {
        if (baseUrl.isBlank()) {
            throw unavailable();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("company_id", companyId);
            payload.put("template_version", TEMPLATE_VERSION);
            payload.put("activation_id", activationId);
            payload.put("idempotency_key", idempotencyKey);
            payload.put("assignments", assignments.stream().map(assignment -> Map.of(
                    "principal_id", assignment.principalId(),
                    "logical_role", assignment.logicalRole())).toList());
            String body = objectMapper.writeValueAsString(payload);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
            JsonNode response = restClient.post().uri(baseUrl + PATH)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Service", "agentcici")
                    .header("X-Internal-Timestamp", timestamp)
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signer.signature("agentcici", "POST", PATH, timestamp, nonce, body))
                    .body(body).retrieve().body(JsonNode.class);
            JsonNode result = response == null ? null : response.path("result");
            if (response == null || !"succeeded".equals(response.path("status").asText())
                    || result == null || result.path("tenant_id").asText().isBlank()
                    || result.path("authorization_digest").asText().isBlank()) {
                throw unavailable();
            }
            return new AuthorizationView(
                    result.path("company_id").asText(), result.path("tenant_id").asText(),
                    result.path("template_version").asText(), result.path("authorization_digest").asText(),
                    result.path("role_count").asInt(), result.path("permission_set_count").asInt(),
                    result.path("object_count").asInt(), result.path("assignment_count").asInt(),
                    result.path("verified").asBoolean(), result.path("state").asText());
        } catch (RestClientException | java.io.IOException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Semattice DevAutopilot authorization template request failed");
    }

    public record Assignment(String principalId, String logicalRole) { }

    public record AuthorizationView(String companyId, String tenantId, String templateVersion,
                                    String authorizationDigest, int roleCount, int permissionSetCount,
                                    int objectCount, int assignmentCount, boolean verified, String state) { }
}
