package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.EcosystemHumanApiService;
import com.codehouse.ciciassistant.auth.service.KeycloakOidcLoginService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.EcosystemApplicationTrustService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EcosystemHumanApiServiceTest {

    private final KeycloakOidcLoginService keycloak = mock(KeycloakOidcLoginService.class);
    private final EcosystemApplicationTrustService trust = mock(EcosystemApplicationTrustService.class);
    private final AccountExternalIdentityRepository identities = mock(AccountExternalIdentityRepository.class);
    private final UserRepository members = mock(UserRepository.class);
    private final EcosystemHumanApiService service = new EcosystemHumanApiService(keycloak, trust, identities, members);

    private UserAccountEntity account;
    private UserEntity member;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        account = new UserAccountEntity("13902401001");
        member = new UserEntity(new CompanyEntity("orgaaaaaaaaaaaaaaaaa", "测试公司", "ACTIVE"), account, "ORG_USER");
        expiresAt = Instant.now().plusSeconds(300);
        when(keycloak.issuer()).thenReturn("https://sso.example.test/realms/agentcici");
        when(keycloak.verifyHumanAccessToken("keycloak-token", OfficialAccessTokenService.AGENTCICI_AUDIENCE))
                .thenReturn(new KeycloakOidcLoginService.HumanAccessToken(
                        "human-subject", "internal-workbench", List.of("openid"), "session-1", expiresAt));
        when(identities.findByIssuerAndSubject("https://sso.example.test/realms/agentcici", "human-subject"))
                .thenReturn(Optional.of(new AccountExternalIdentityEntity(
                        account, "https://sso.example.test/realms/agentcici", "human-subject")));
        when(trust.requireActiveClient("internal-workbench", EcosystemApplicationTrustService.ORGANIZATION_READ_SCOPE))
                .thenReturn(application(EcosystemApplicationTrustService.ORGANIZATION_READ_SCOPE));
        when(trust.requireActiveClient("internal-workbench", EcosystemApplicationTrustService.ORGANIZATION_CONTEXT_SCOPE))
                .thenReturn(application(EcosystemApplicationTrustService.ORGANIZATION_CONTEXT_SCOPE));
    }

    @Test
    void listsCompaniesFromTheMappedHumanAccountWithoutCallerSuppliedIdentity() {
        when(members.findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_ACTIVE))
                .thenReturn(List.of(member));

        var result = service.companies("keycloak-token");

        assertThat(result.appCode()).isEqualTo("internal-workbench");
        assertThat(result.accountId()).isEqualTo(account.getId());
        assertThat(result.companies()).singleElement().satisfies(company -> {
            assertThat(company.companyId()).isEqualTo("orgaaaaaaaaaaaaaaaaa");
            assertThat(company.memberId()).isEqualTo(member.getId());
            assertThat(company.roleCode()).isEqualTo("ORG_USER");
        });
        verify(trust).requireActiveClient("internal-workbench", EcosystemApplicationTrustService.ORGANIZATION_READ_SCOPE);
    }

    @Test
    void authorizesAStatelessCompanyContextAndKeepsTheKeycloakTokenPrimary() {
        when(members.findByCompany_IdAndAccount_IdAndMemberStatus(
                "orgaaaaaaaaaaaaaaaaa", account.getId(), UserEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(member));

        var result = service.companyContext("keycloak-token", "orgaaaaaaaaaaaaaaaaa");

        assertThat(result.appCode()).isEqualTo("internal-workbench");
        assertThat(result.companyHeader()).isEqualTo("X-Company-Id");
        assertThat(result.companyId()).isEqualTo("orgaaaaaaaaaaaaaaaaa");
        assertThat(result.roles()).containsExactly("ORG_USER");
    }

    @Test
    void failsClosedWhenTheHumanHasNoActiveMembershipInTheRequestedCompany() {
        when(members.findByCompany_IdAndAccount_IdAndMemberStatus(
                "orgbbbbbbbbbbbbbbbbb", account.getId(), UserEntity.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.companyContext("keycloak-token", "orgbbbbbbbbbbbbbbbbb"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no active membership");
    }

    private EcosystemApplicationTrustService.TrustedApplicationView application(String scope) {
        return new EcosystemApplicationTrustService.TrustedApplicationView(
                "internal-workbench", "内部工作台", "internal-workbench", Set.of(scope),
                EcosystemApplicationTrustService.STATUS_ACTIVE, "platform-admin", Instant.now(), Instant.now());
    }
}
