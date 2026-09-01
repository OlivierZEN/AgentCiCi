package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.api.AuthController;
import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.CloudccSsoService;
import com.codehouse.ciciassistant.auth.service.KeycloakOidcLoginService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.platform.service.DevAutopilotHandoffService;
import com.codehouse.ciciassistant.semattice.SematticeConsoleHandoffService;
import com.codehouse.ciciassistant.semattice.SematticeConsoleLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCanonicalCloudccCompanyId() throws Exception {
        AuthController.CloudccSsoTicketRequest request = objectMapper.readValue("""
                {
                  "agentCompanyId": "company-canonical",
                  "cloudccAccessToken": "runtime-token",
                  "cloudccUser": {"username": "member@example.invalid"}
                }
                """, AuthController.CloudccSsoTicketRequest.class);

        assertThat(request.agentCompanyId()).isEqualTo("company-canonical");
    }

    @Test
    void deserializesLegacyCloudccOrgIdAsCompanyId() throws Exception {
        AuthController.CloudccSsoTicketRequest request = objectMapper.readValue("""
                {
                  "agentOrgId": "company-legacy",
                  "cloudccAccessToken": "runtime-token",
                  "cloudccUser": {"username": "member@example.invalid"}
                }
                """, AuthController.CloudccSsoTicketRequest.class);

        assertThat(request.agentCompanyId()).isEqualTo("company-legacy");
    }

    @Test
    void rejectsLegacyLocalPasswordWritesWhenOidcIsEnabled() {
        AuthService authService = mock(AuthService.class);
        KeycloakOidcLoginService oidc = mock(KeycloakOidcLoginService.class);
        when(oidc.isEnabled()).thenReturn(true);
        AuthController controller = new AuthController(
                authService,
                mock(CloudccSsoService.class),
                oidc,
                mock(DevAutopilotHandoffService.class),
                mock(SematticeConsoleHandoffService.class),
                new SematticeConsoleLocation(""));

        assertThatThrownBy(() -> controller.changeMyPassword(
                new AuthController.ChangeMyPasswordRequest("old-password", "new-password")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("统一认证已启用，请在统一账号中心修改密码");

        verifyNoInteractions(authService);
    }

    @Test
    void callbackSetsAnHttpOnlyServerSideOidcSessionCookie() {
        KeycloakOidcLoginService oidc = mock(KeycloakOidcLoginService.class);
        when(oidc.complete("code", "state", "state-cookie"))
                .thenReturn(new KeycloakOidcLoginService.LoginCompletionRedirect(
                        URI.create("/app?oidc_ticket=completion"), "login-session", 1800));
        AuthController controller = controller(oidc);

        var response = controller.oidcCallback("code", "state", "state-cookie");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/app?oidc_ticket=completion"));
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("CICI_OIDC_SESSION=login-session")
                        .contains("Max-Age=1800")
                        .contains("Path=/auth/oidc")
                        .contains("Secure")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
    }

    @Test
    void logoutClearsTheSessionCookieAndRedirectsThroughKeycloak() {
        KeycloakOidcLoginService oidc = mock(KeycloakOidcLoginService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(oidc.isEnabled()).thenReturn(true);
        when(request.getHeader(HttpHeaders.HOST)).thenReturn("agentcici.example.test");
        when(oidc.isCanonicalStartHost("agentcici.example.test")).thenReturn(true);
        when(oidc.logout("login-session"))
                .thenReturn(URI.create("https://sso.example.test/realms/agentcici/protocol/openid-connect/logout"));
        AuthController controller = controller(oidc);

        var response = controller.oidcLogout("login-session", request);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(
                URI.create("https://sso.example.test/realms/agentcici/protocol/openid-connect/logout"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("CICI_OIDC_SESSION=")
                .contains("Max-Age=0")
                .contains("Path=/auth/oidc");
        org.mockito.Mockito.verify(oidc).logout("login-session");
    }

    private AuthController controller(KeycloakOidcLoginService oidc) {
        return new AuthController(
                mock(AuthService.class),
                mock(CloudccSsoService.class),
                oidc,
                mock(DevAutopilotHandoffService.class),
                mock(SematticeConsoleHandoffService.class),
                new SematticeConsoleLocation(""));
    }
}
