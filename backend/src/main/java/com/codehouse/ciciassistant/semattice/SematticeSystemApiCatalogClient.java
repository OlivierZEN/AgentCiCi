package com.codehouse.ciciassistant.semattice;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads the provider-owned Semattice system API projection without copying its contract into AgentCiCi. */
@Component
public class SematticeSystemApiCatalogClient {

    static final String PATH = "/internal/v1/system-api-catalog";

    private final RestClient restClient;
    private final SematticeProvisioningClient signer;
    private final String baseUrl;

    public SematticeSystemApiCatalogClient(RestClient.Builder restClientBuilder,
                                           SematticeProvisioningClient signer,
                                           @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = restClientBuilder.build();
        this.signer = signer;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public Optional<JsonNode> fetch() {
        if (baseUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            JsonNode response = restClient.get()
                    .uri(baseUrl + PATH)
                    .header("X-Internal-Service", "agentcici")
                    .header("X-Internal-Timestamp", timestamp)
                    .header("X-Internal-Nonce", nonce)
                    .header("X-Internal-Signature", signer.signature("agentcici", "GET", PATH, timestamp, nonce, ""))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"succeeded".equals(response.path("status").asText())
                    || !response.path("result").isObject()) {
                return Optional.empty();
            }
            return Optional.of(response.path("result"));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }
}
