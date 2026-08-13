package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.platform.service.EcosystemApplicationTrustService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves an internal application's Keycloak HUMAN token into current AgentCiCi memberships. */
@Service
public class EcosystemHumanApiService {

    private final KeycloakOidcLoginService keycloak;
    private final EcosystemApplicationTrustService applicationTrust;
    private final AccountExternalIdentityRepository identityRepository;
    private final UserRepository userRepository;

    public EcosystemHumanApiService(KeycloakOidcLoginService keycloak,
                                    EcosystemApplicationTrustService applicationTrust,
                                    AccountExternalIdentityRepository identityRepository,
                                    UserRepository userRepository) {
        this.keycloak = keycloak;
        this.applicationTrust = applicationTrust;
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CompanyDirectoryView companies(String keycloakAccessToken) {
        AuthorizedCaller caller = authorize(keycloakAccessToken,
                EcosystemApplicationTrustService.ORGANIZATION_READ_SCOPE);
        List<CompanyMembershipView> memberships = activeMemberships(caller.account().getId());
        return new CompanyDirectoryView(
                caller.application().appCode(),
                caller.application().displayName(),
                caller.account().getId(),
                caller.account().getPublicId(),
                caller.token().expiresAt(),
                memberships);
    }

    @Transactional(readOnly = true)
    public CompanyContextView companyContext(String keycloakAccessToken, String companyId) {
        AuthorizedCaller caller = authorize(keycloakAccessToken,
                EcosystemApplicationTrustService.ORGANIZATION_CONTEXT_SCOPE);
        String targetCompanyId = requireText(companyId, "companyId");
        UserEntity member = userRepository.findByCompany_IdAndAccount_IdAndMemberStatus(
                        targetCompanyId, caller.account().getId(), UserEntity.STATUS_ACTIVE)
                .filter(value -> "ACTIVE".equalsIgnoreCase(value.getCompany().getStatus()))
                .orElseThrow(() -> new ForbiddenException("Current user has no active membership in the requested company"));
        return new CompanyContextView(
                caller.application().appCode(),
                caller.account().getId(),
                member.getCompany().getId(),
                member.getCompany().getName(),
                member.getId(),
                List.of(member.getRoleCode()),
                "X-Company-Id",
                caller.token().expiresAt());
    }

    private AuthorizedCaller authorize(String keycloakAccessToken, String requiredScope) {
        if (keycloakAccessToken == null || keycloakAccessToken.isBlank()) {
            throw new UnauthorizedException("Keycloak HUMAN access token is required");
        }
        KeycloakOidcLoginService.HumanAccessToken token = keycloak.verifyHumanAccessToken(
                keycloakAccessToken, OfficialAccessTokenService.AGENTCICI_AUDIENCE);
        EcosystemApplicationTrustService.TrustedApplicationView application =
                applicationTrust.requireActiveClient(token.clientId(), requiredScope);
        AccountExternalIdentityEntity identity = identityRepository
                .findByIssuerAndSubject(keycloak.issuer(), token.subject())
                .orElseThrow(() -> new UnauthorizedException("Unified identity is not bound to an AgentCiCi HUMAN account"));
        UserAccountEntity account = identity.getAccount();
        if (!UserAccountEntity.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new ForbiddenException("AgentCiCi account is not active");
        }
        return new AuthorizedCaller(token, application, account);
    }

    private List<CompanyMembershipView> activeMemberships(String accountId) {
        return userRepository.findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(
                        accountId, UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getCompany().getStatus()))
                .map(member -> new CompanyMembershipView(
                        member.getCompany().getId(),
                        member.getCompany().getName(),
                        member.getId(),
                        member.getRoleCode()))
                .toList();
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private record AuthorizedCaller(
            KeycloakOidcLoginService.HumanAccessToken token,
            EcosystemApplicationTrustService.TrustedApplicationView application,
            UserAccountEntity account) {
    }

    public record CompanyMembershipView(
            String companyId,
            String companyName,
            String memberId,
            String roleCode) {
    }

    public record CompanyDirectoryView(
            String appCode,
            String applicationName,
            String accountId,
            String accountPublicId,
            Instant tokenExpiresAt,
            List<CompanyMembershipView> companies) {
    }

    public record CompanyContextView(
            String appCode,
            String accountId,
            String companyId,
            String companyName,
            String memberId,
            List<String> roles,
            String companyHeader,
            Instant tokenExpiresAt) {
    }
}
