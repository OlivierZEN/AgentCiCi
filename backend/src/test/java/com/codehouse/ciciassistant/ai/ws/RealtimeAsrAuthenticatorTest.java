package com.codehouse.ciciassistant.ai.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeAsrAuthenticatorTest {

    @Test
    void acceptsTheSameEcosystemUserOactAsProtectedApis() {
        JwtService legacy = mock(JwtService.class);
        OfficialAccessTokenService official = mock(OfficialAccessTokenService.class);
        when(official.verifyEcosystemUserContext(
                "oact-token", OfficialAccessTokenService.AGENTCICI_AUDIENCE))
                .thenReturn(new OfficialAccessTokenService.EcosystemUserContext(
                        "company-1", "member-1", "account-1", List.of("MEMBER")));

        RealtimeAsrAuthenticator.AuthenticatedUser user =
                new RealtimeAsrAuthenticator(legacy, official).authenticate("oact-token");

        assertThat(user.companyId()).isEqualTo("company-1");
        assertThat(user.userId()).isEqualTo("member-1");
    }

    @Test
    void keepsVoiceEnabledEmbedTokensWorking() {
        JwtService legacy = mock(JwtService.class);
        OfficialAccessTokenService official = mock(OfficialAccessTokenService.class);
        Claims claims = mock(Claims.class);
        when(official.verifyEcosystemUserContext(
                "embed-token", OfficialAccessTokenService.AGENTCICI_AUDIENCE))
                .thenThrow(new IllegalArgumentException("not oact"));
        when(legacy.parse("embed-token")).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("embed_app");
        when(claims.get("permissions")).thenReturn(List.of("voice:input"));
        when(claims.get("company_id", String.class)).thenReturn("company-2");
        when(claims.get("member_id", String.class)).thenReturn("member-2");

        RealtimeAsrAuthenticator.AuthenticatedUser user =
                new RealtimeAsrAuthenticator(legacy, official).authenticate("embed-token");

        assertThat(user).isEqualTo(new RealtimeAsrAuthenticator.AuthenticatedUser("company-2", "member-2"));
    }

    @Test
    void rejectsNonOactApplicationTokens() {
        JwtService legacy = mock(JwtService.class);
        OfficialAccessTokenService official = mock(OfficialAccessTokenService.class);
        Claims claims = mock(Claims.class);
        when(official.verifyEcosystemUserContext(
                "platform-token", OfficialAccessTokenService.AGENTCICI_AUDIENCE))
                .thenThrow(new IllegalArgumentException("not oact"));
        when(legacy.parse("platform-token")).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("platform");

        assertThatThrownBy(() -> new RealtimeAsrAuthenticator(legacy, official).authenticate("platform-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported token type");
    }
}
