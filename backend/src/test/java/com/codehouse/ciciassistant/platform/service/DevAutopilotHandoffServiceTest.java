package com.codehouse.ciciassistant.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.service.OidcLoginStateStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DevAutopilotHandoffServiceTest {

    private final OidcLoginStateStore stateStore = mock(OidcLoginStateStore.class);
    private final DevAutopilotTenantApplicationService applications = mock(DevAutopilotTenantApplicationService.class);
    private final AuthService authService = mock(AuthService.class);
    private final OfficialAccessTokenService tokens = mock(OfficialAccessTokenService.class);
    private final DevAutopilotHandoffService service = new DevAutopilotHandoffService(
            stateStore, applications, authService, tokens);

    @Test
    void storesOnlyTenantAndMemberForOneTimeTicketThenMintsFreshRuntimeAccess() {
        active("org00000000000000001");
        DevAutopilotHandoffService.HandoffTicket handoff = service.issue("org00000000000000001", "member-1");

        assertThat(handoff.ticket()).isNotBlank();
        assertThat(handoff.expiresInSeconds()).isEqualTo(60);
        verify(stateStore).saveDevAutopilotHandoff(eq(handoff.ticket()),
                eq(new OidcLoginStateStore.DevAutopilotHandoff("org00000000000000001", "member-1")), any());

        when(stateStore.consumeDevAutopilotHandoff("single-use"))
                .thenReturn(new OidcLoginStateStore.DevAutopilotHandoff("org00000000000000001", "member-1"));
        OfficialAccessTokenService.IssuedToken issued = new OfficialAccessTokenService.IssuedToken(
                "fresh-runtime-oact", Instant.now().plusSeconds(300), "tenant-1", "org00000000000000001", List.of("runtime.record.read"));
        when(authService.issueEcosystemAccessForDevAutopilot("org00000000000000001", "member-1", tokens)).thenReturn(issued);

        DevAutopilotHandoffService.ExchangedAccess access = service.exchange("single-use");

        assertThat(access.companyId()).isEqualTo("org00000000000000001");
        assertThat(access.tenantId()).isEqualTo("tenant-1");
        assertThat(access.accessToken()).isEqualTo("fresh-runtime-oact");
    }

    private void active(String companyId) {
        when(applications.get(companyId)).thenReturn(new DevAutopilotTenantApplicationService.View(
                companyId, true, "devautopilot.standard.v1", "ACTIVE", "ACTIVE", "tenant-1",
                "metadata-1", "digest", true, null, List.of()));
    }
}
