package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Projects AgentCiCi-authoritative HUMAN/SERVICE identities through Semattice's public capability boundary. */
@Component
public class SematticePrincipalProjectionClient {
    private static final String CAPABILITY = "identity.principal.sync";
    private final RestClient restClient;
    private final OfficialAccessTokenService tokens;
    private final String baseUrl;

    public SematticePrincipalProjectionClient(RestClient.Builder builder,
                                               OfficialAccessTokenService tokens,
                                               @Value("${app.semattice.base-url:}") String baseUrl) {
        this.restClient = builder.build();
        this.tokens = tokens;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public ProjectionView syncService(UserEntity owner, String principalId, String displayName, String publicId,
                                      String clientId, String lifecycleStatus) {
        OfficialAccessTokenService.IssuedToken ownerToken = syncHuman(owner);
        OfficialAccessTokenService.IssuedToken serviceToken = tokens.issueForSematticeServiceProjection(
                principalId, owner.getAccountId(), clientId, ownerToken.tenantId(),
                owner.getCompany().getId(), lifecycleStatus);
        return invoke(serviceToken, displayName, publicId);
    }

    public OfficialAccessTokenService.IssuedToken syncHuman(UserEntity member) {
        OfficialAccessTokenService.IssuedToken token = tokens.issueForSematticePrincipalSync(member);
        invoke(token, member.getAccount().getDisplayName(), member.getAccount().getPublicId());
        return token;
    }

    private ProjectionView invoke(OfficialAccessTokenService.IssuedToken token, String displayName, String publicId) {
        if (baseUrl.isBlank()) {
            throw unavailable(null);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("capability_id", CAPABILITY);
        request.put("request_id", "agentcici-principal-sync-" + UUID.randomUUID());
        request.put("idempotency_key", "agentcici-principal-sync-" + UUID.randomUUID());
        Map<String, Object> input = new LinkedHashMap<>();
        if (displayName != null && !displayName.isBlank()) input.put("display_name", displayName);
        if (publicId != null && !publicId.isBlank()) input.put("public_id", publicId);
        request.put("input", input);
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/v1/capabilities/" + CAPABILITY + "/invoke")
                    .header("Authorization", "Bearer " + token.token())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode result = response == null ? null : response.path("result");
            if (response == null || !"succeeded".equals(response.path("status").asText())
                    || result == null || result.path("principal_id").asText().isBlank()) {
                throw unavailable(null);
            }
            return new ProjectionView(result.path("principal_id").asText(), result.path("status").asText(),
                    result.path("identity_version").asLong());
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Semattice principal projection failed", cause);
    }

    public record ProjectionView(String principalId, String status, long identityVersion) { }
}
