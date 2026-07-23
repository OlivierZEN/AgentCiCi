package com.codehouse.ciciassistant.semattice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional AgentCiCi-originated provisioning path. Semattice remains responsible
 * for calling back to AgentCiCi to reserve and validate the company identity.
 */
@Component
public class SematticeProvisioningClient {

    private static final String PROVISION_PATH = "/internal/v1/company-provisionings";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SematticeProvisioningClient(RestClient.Builder restClientBuilder,
                                       ObjectMapper objectMapper,
                                       @Value("${app.semattice.base-url:}") String baseUrl,
                                       @Value("${app.semattice.internal-hmac-key:}") String hmacKey) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.hmacKey = hmacKey == null ? "" : hmacKey.trim();
    }

    public ProvisioningView provision(String companyId, String idempotencyKey, String displayName,
                                      String serviceTier, JsonNode entitlements) {
        if (baseUrl.isBlank() || hmacKey.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Semattice provisioning is not configured");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("company_id", companyId);
            payload.put("idempotency_key", idempotencyKey);
            payload.put("display_name", displayName);
            payload.put("service_tier", serviceTier);
            if (entitlements != null) {
                payload.put("entitlements", entitlements);
            }
            String body = objectMapper.writeValueAsString(payload);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = nonce();
            String signature = signature("agentcici", "POST", PROVISION_PATH, timestamp, nonce, body);
            JsonNode response = restClient.post()
                    .uri(baseUrl + PROVISION_PATH)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Service", "agentcici")
                    .header("X-Internal-Timestamp", timestamp)
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signature)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode result = response == null ? null : response.path("result");
            if (response == null || !"succeeded".equals(response.path("status").asText())
                    || result == null || result.path("company_id").asText().isBlank()
                    || result.path("tenant_id").asText().isBlank()) {
                throw unavailable();
            }
            return new ProvisioningView(result.path("company_id").asText(), result.path("tenant_id").asText(),
                    result.path("native_status").asText(), result.path("operation_status").asText());
        } catch (RestClientException | java.io.IOException exception) {
            throw unavailable();
        }
    }

    String signature(String serviceId, String method, String path, String timestamp, String nonce, String body) {
        try {
            String bodyHash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            String canonical = String.join("\n", serviceId, method, path, timestamp, nonce, bodyHash);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private String nonce() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return java.util.HexFormat.of().formatHex(value);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Semattice provisioning request failed");
    }

    public record ProvisioningView(String companyId, String tenantId, String nativeStatus, String operationStatus) {
    }
}
