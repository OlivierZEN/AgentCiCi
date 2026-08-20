package com.codehouse.ciciassistant.wecom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.service.WecomKfClient;
import com.codehouse.ciciassistant.wecom.service.WecomKfConfigService;
import com.codehouse.ciciassistant.wecom.service.WecomKfMobileService;
import com.codehouse.ciciassistant.wecom.service.WecomKfMobileSessionStore;
import com.codehouse.ciciassistant.wecom.service.WecomKfProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

class WecomKfMobileControllerTest {

    @Test
    void shouldCreateSingleUseOAuthStateAndSecureCookie() {
        Fixture fixture = fixture();
        UUID entry = fixture.resolved.account().getMobileEntryId();
        when(fixture.config.findMobileEntry(entry)).thenReturn(Optional.of(fixture.resolved));

        var response = fixture.controller.start(entry);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=ww-demo", "redirect_uri=https%3A%2F%2Fcici.example.test%2Fwecom%2Fkf%2Fmobile%2Fcallback");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains(WecomKfMobileController.STATE_COOKIE, "HttpOnly", "Secure", "SameSite=Lax", "Path=/wecom/kf/mobile");
        verify(fixture.sessions).saveOAuthState(any(), org.mockito.ArgumentMatchers.eq(entry), any());
    }

    @Test
    void shouldRejectMismatchedOAuthStateBeforeUsingCode() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.controller.callback("code", "state-a", "state-b"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("OAuth state");
        org.mockito.Mockito.verifyNoInteractions(fixture.client);
    }

    @Test
    void shouldBindVerifiedAcceptingServicerToServerSession() {
        Fixture fixture = fixture();
        UUID entry = fixture.resolved.account().getMobileEntryId();
        when(fixture.sessions.consumeOAuthState("same-state")).thenReturn(new WecomKfMobileSessionStore.OAuthState(entry));
        when(fixture.config.findMobileEntry(entry)).thenReturn(Optional.of(fixture.resolved));
        when(fixture.client.resolveCurrentMember(fixture.resolved, "oauth-code")).thenReturn(Optional.of("agent-1"));
        when(fixture.client.listServicers(fixture.resolved)).thenReturn(List.of(new WecomKfClient.Servicer("agent-1", 0)));

        var response = fixture.controller.callback("oauth-code", "same-state", "same-state");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).anySatisfy(cookie ->
                assertThat(cookie).contains(WecomKfMobileController.SESSION_COOKIE, "HttpOnly", "Secure", "SameSite=Lax"));
        ArgumentCaptor<WecomKfMobileSessionStore.MobileSession> session = ArgumentCaptor.forClass(WecomKfMobileSessionStore.MobileSession.class);
        verify(fixture.sessions).saveSession(any(), session.capture(), any());
        assertThat(session.getValue().operatorUserId()).isEqualTo("agent-1");
        assertThat(session.getValue().companyId()).isEqualTo("org-1");
    }

    private Fixture fixture() {
        WecomKfConfigService config = mock(WecomKfConfigService.class);
        WecomKfMobileSessionStore sessions = mock(WecomKfMobileSessionStore.class);
        WecomKfMobileService mobile = mock(WecomKfMobileService.class);
        WecomKfClient client = mock(WecomKfClient.class);
        WecomKfProperties properties = new WecomKfProperties();
        properties.setPublicBaseUrl("https://cici.example.test");
        WecomKfConfigService.ResolvedAccount resolved = resolved();
        return new Fixture(new WecomKfMobileController(config, sessions, mobile, client, properties),
                config, sessions, client, resolved);
    }

    private WecomKfConfigService.ResolvedAccount resolved() {
        SecretCipherService cipher = new SecretCipherService("");
        var secret = cipher.encryptUtf8("kf-secret");
        var aes = cipher.encryptUtf8("abcdefghijklmnopqrstuvwxyzABCDEFG1234567890");
        WecomKfAccountEntity account = new WecomKfAccountEntity("org-1", "ww-demo", "wk-demo", "售后客服",
                secret.cipherBase64(), secret.ivBase64(), "callback-token", aes.cipherBase64(), aes.ivBase64(),
                "after-sales-agent", "user-1");
        account.updateProfile("ww-demo", "wk-demo", "售后客服", "callback-token", "after-sales-agent",
                "user-1", "1000002", true, true);
        var appSecret = cipher.encryptUtf8("app-secret");
        account.updateWecomAppSecret(appSecret.cipherBase64(), appSecret.ivBase64());
        return new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "aes", "app-secret");
    }

    private record Fixture(WecomKfMobileController controller,
                           WecomKfConfigService config,
                           WecomKfMobileSessionStore sessions,
                           WecomKfClient client,
                           WecomKfConfigService.ResolvedAccount resolved) {
    }
}
