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
}
