package com.codehouse.ciciassistant.ontology.semattice;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SematticeOntologyHttpGateway implements SematticeOntologyGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final OfficialAccessTokenService officialAccessTokens;
    private final String baseUrl;

    public SematticeOntologyHttpGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AuthService authService,
            OfficialAccessTokenService officialAccessTokens,
            @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.officialAccessTokens = officialAccessTokens;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    @Override
    public JsonNode invoke(
            String companyId,
            String userId,
            String capabilityId,
            Map<String, Object> input,
            String idempotencyKey) {
        requireConfigured();
        requireCapabilityId(capabilityId);
        OfficialAccessTokenService.IssuedToken token =
                authService.issueSematticeOfficialAccess(companyId, userId, officialAccessTokens);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", capabilityId);
        request.put("request_id", "ontology-" + UUID.randomUUID());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.put("idempotency_key", idempotencyKey.trim());
        }
        request.put("input", input == null ? Map.of() : input);
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/v1/capabilities/" + capabilityId + "/invoke")
                    .header("Authorization", "Bearer " + token.token())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return requireSucceeded(response);
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception);
        } catch (SematticeCapabilityException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new SematticeCapabilityException(
                    "DATA_SOURCE_UNAVAILABLE",
                    "Semattice capability endpoint is unavailable",
                    exception);
        }
    }

    private JsonNode requireSucceeded(JsonNode response) {
        if (response != null
                && "succeeded".equals(response.path("status").asText())
                && !response.path("result").isMissingNode()
                && !response.path("result").isNull()) {
            return response.path("result");
        }
        String code = response == null ? "INTERNAL" : response.path("error").path("code").asText("INTERNAL");
        String message = response == null
                ? "Semattice returned an empty response"
                : response.path("error").path("message").asText("Semattice capability failed");
        throw new SematticeCapabilityException(code, message);
    }

    private SematticeCapabilityException responseFailure(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsString());
            return new SematticeCapabilityException(
                    response.path("error").path("code").asText("INTERNAL"),
                    response.path("error").path("message").asText("Semattice capability failed"),
                    exception);
        } catch (Exception parseFailure) {
            return new SematticeCapabilityException(
                    "DATA_SOURCE_UNAVAILABLE",
                    "Semattice capability endpoint returned an invalid response",
                    exception);
        }
    }

    private void requireConfigured() {
        if (baseUrl.isBlank()) {
            throw new SematticeCapabilityException(
                    "DATA_SOURCE_UNAVAILABLE", "Semattice base URL is not configured");
        }
    }

    private void requireCapabilityId(String capabilityId) {
        if (capabilityId == null || !capabilityId.matches("^[a-z][a-z0-9.-]{2,127}$")) {
            throw new IllegalArgumentException("SEMATTICE_CAPABILITY_INVALID");
        }
    }
}
