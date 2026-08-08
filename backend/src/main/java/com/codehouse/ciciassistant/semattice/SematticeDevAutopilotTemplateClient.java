package com.codehouse.ciciassistant.semattice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** AgentCiCi-owned client for Semattice's HMAC-protected DevAutopilot metadata baseline. */
@Component
public class SematticeDevAutopilotTemplateClient {
    public static final String TEMPLATE_VERSION = "devautopilot.standard.v1";
    private static final String PATH = "/internal/v1/devautopilot-template-applications";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SematticeProvisioningClient signer;
    private final String baseUrl;

    public SematticeDevAutopilotTemplateClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                                SematticeProvisioningClient signer,
                                                @org.springframework.beans.factory.annotation.Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public TemplateView apply(String companyId, String idempotencyKey) {
        if (baseUrl.isBlank()) throw unavailable();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("company_id", companyId);
            payload.put("template_version", TEMPLATE_VERSION);
            payload.put("idempotency_key", idempotencyKey);
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
                    || result.path("metadata_version_id").asText().isBlank()) throw unavailable();
            return new TemplateView(result.path("company_id").asText(), result.path("tenant_id").asText(),
                    result.path("metadata_version_id").asText(), result.path("snapshot_digest").asText(),
                    result.path("object_count").asInt(), result.path("field_count").asInt(), result.path("state").asText());
        } catch (RestClientException | java.io.IOException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice DevAutopilot template request failed");
    }

    public record TemplateView(String companyId, String tenantId, String metadataVersionId, String snapshotDigest,
                               int objectCount, int fieldCount, String state) { }
}
