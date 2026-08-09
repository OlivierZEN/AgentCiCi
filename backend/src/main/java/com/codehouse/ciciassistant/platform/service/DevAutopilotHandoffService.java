package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.service.AuthService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.auth.service.OidcLoginStateStore;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time browser handoff for the tenant DevAutopilot application.
 *
 * <p>The browser never receives an OACT.  It receives only an opaque, short-lived ticket; the
 * target application's backend redeems that ticket and keeps the newly issued OACT server-side.
 * No bearer token is persisted in the ticket store.</p>
 */
@Service
public class DevAutopilotHandoffService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(1);

    private final OidcLoginStateStore stateStore;
    private final DevAutopilotTenantApplicationService applications;
    private final AuthService authService;
    private final OfficialAccessTokenService tokenService;

    public DevAutopilotHandoffService(OidcLoginStateStore stateStore,
                                      DevAutopilotTenantApplicationService applications,
                                      AuthService authService,
                                      OfficialAccessTokenService tokenService) {
        this.stateStore = stateStore;
        this.applications = applications;
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public HandoffTicket issue(String companyId, String memberId) {
        requireActive(companyId);
        String ticket = UUID.randomUUID().toString();
        stateStore.saveDevAutopilotHandoff(ticket,
                new OidcLoginStateStore.DevAutopilotHandoff(companyId, memberId), TICKET_TTL);
        return new HandoffTicket(ticket, TICKET_TTL.toSeconds());
    }

    @Transactional(readOnly = true)
    public ExchangedAccess exchange(String ticket) {
        OidcLoginStateStore.DevAutopilotHandoff handoff = stateStore.consumeDevAutopilotHandoff(ticket);
        if (handoff == null) {
            throw new UnauthorizedException("DevAutopilot 登录票据已过期或已使用");
        }
        requireActive(handoff.companyId());
        OfficialAccessTokenService.IssuedToken issued = authService.issueSematticeOfficialAccessForRuntime(
                handoff.companyId(), handoff.memberId(), tokenService);
        return new ExchangedAccess(issued.token(), issued.expiresAt(), issued.tenantId(), issued.companyId());
    }

    private void requireActive(String companyId) {
        DevAutopilotTenantApplicationService.View activation = applications.get(companyId);
        if (!activation.enabled() || !"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("当前租户尚未开通或已暂停 DevAutopilot");
        }
    }

    public record HandoffTicket(String ticket, long expiresInSeconds) {
    }

    public record ExchangedAccess(String accessToken, java.time.Instant expiresAt, String tenantId, String companyId) {
    }
}
