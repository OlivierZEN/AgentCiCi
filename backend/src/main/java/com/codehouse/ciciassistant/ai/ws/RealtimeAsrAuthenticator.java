package com.codehouse.ciciassistant.ai.ws;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RealtimeAsrAuthenticator {

    private final JwtService jwtService;
    private final OfficialAccessTokenService officialAccessTokenService;

    public RealtimeAsrAuthenticator(JwtService jwtService,
                                    OfficialAccessTokenService officialAccessTokenService) {
        this.jwtService = jwtService;
        this.officialAccessTokenService = officialAccessTokenService;
    }

    public AuthenticatedUser authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing token");
        }
        try {
            OfficialAccessTokenService.EcosystemUserContext ecosystem =
                    officialAccessTokenService.verifyEcosystemUserContext(
                            token, OfficialAccessTokenService.AGENTCICI_AUDIENCE);
            return new AuthenticatedUser(ecosystem.companyId(), ecosystem.memberId());
        } catch (Exception ignored) {
            return authenticateEmbedToken(token);
        }
    }

    private AuthenticatedUser authenticateEmbedToken(String token) {
        Claims claims = jwtService.parse(token);
        if (!"embed_app".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("unsupported token type");
        }
        List<String> permissions = claimStrings(claims.get("permissions"));
        if (!permissions.contains("voice:input") && !permissions.contains("meeting:start")) {
            throw new IllegalArgumentException("voice permission denied");
        }
        String companyId = claims.get("company_id", String.class);
        String memberId = claims.get("member_id", String.class);
        String userId = hasText(memberId) ? memberId : claims.getSubject();
        if (!hasText(companyId) || !hasText(userId)) {
            throw new IllegalArgumentException("incomplete voice identity");
        }
        return new AuthenticatedUser(companyId.trim(), userId.trim());
    }

    private static List<String> claimStrings(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AuthenticatedUser(String companyId, String userId) {
    }
}
