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
 * <p>The URL contains only an opaque, short-lived ticket. The target backend redeems it for the
 * same ecosystem HUMAN token type used by AgentCiCi login and stores it in an HttpOnly cookie.
 * No bearer token is persisted in the ticket store or exposed to frontend JavaScript.</p>
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
        OfficialAccessTokenService.IssuedToken issued = authService.issueEcosystemAccessForDevAutopilot(
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
