package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.semattice.SematticeMetadataApprovalService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OfficialAccessTokenServiceTest {

    @Test
    void signsShortLivedSematticeContextWithMappedIdentityAndLocalBinding() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AccountExternalIdentityRepository identityRepository = org.mockito.Mockito.mock(AccountExternalIdentityRepository.class);
        SematticeProvisioningBindingRepository bindingRepository = org.mockito.Mockito.mock(SematticeProvisioningBindingRepository.class);
        SematticeMetadataApprovalService approvalService = org.mockito.Mockito.mock(SematticeMetadataApprovalService.class);
        UserAccountEntity account = new UserAccountEntity("13902400999");
        CompanyEntity company = new CompanyEntity("orgaaaaaaaaaaaaaaaaa", "测试公司", "ACTIVE");
        UserEntity member = new UserEntity(company, account, "OWNER");
        AccountExternalIdentityEntity identity = new AccountExternalIdentityEntity(
                account, "https://sso.agentcici.com/realms/agentcici", "keycloak-user-subject");
        SematticeProvisioningBindingEntity binding = new SematticeProvisioningBindingEntity(
                "reservation-1", company.getId(), "idempotency-1");
        binding.complete("11111111-1111-4111-8111-111111111111", "operation-1", true, null);

        when(identityRepository.findByAccount_Id(account.getId())).thenReturn(Optional.of(identity));
        when(bindingRepository.findByCompanyId(company.getId())).thenReturn(Optional.of(binding));
        when(approvalService.approvedIdsForRequester(company.getId(), member.getId())).thenReturn(List.of("approval-1"));
        OfficialAccessTokenService service = new OfficialAccessTokenService(
                identityRepository,
                bindingRepository,
                approvalService,
                true,
                "https://x.agentcici.com",
                "oact-test-1",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                List.of("metadata.version.read", "record.read"),
                List.of("metadata.version.read", "record.read", "runtime.record.delete"),
                600);

        OfficialAccessTokenService.IssuedToken issued = service.issueForSemattice(member);
        Claims claims = Jwts.parser().verifyWith(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        ((RSAPrivateCrtKey) keys.getPrivate()).getModulus(),
                        ((RSAPrivateCrtKey) keys.getPrivate()).getPublicExponent())))
                .build()
                .parseSignedClaims(issued.token())
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("https://x.agentcici.com");
        assertThat(claims.getSubject()).isEqualTo(account.getId());
        assertThat(claims.getAudience()).containsExactly(OfficialAccessTokenService.SEMATTICE_AUDIENCE);
        assertThat(claims.get("tenant_id", String.class)).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(claims.get("company_id", String.class)).isEqualTo(company.getId());
        assertThat(claims.get("principal_id", String.class)).isEqualTo(account.getId());
        assertThat(claims.get("principal_type", String.class)).isEqualTo("HUMAN");
        assertThat(claims.get("keycloak_subject", String.class)).isEqualTo("keycloak-user-subject");
        assertThat(claims.get("scope", String.class)).isEqualTo("metadata.version.read record.read");
        assertThat(issued.scopes()).containsExactly("metadata.version.read", "record.read");
        OfficialAccessTokenService.IssuedToken consoleToken = service.issueForSematticeConsole(member);
        assertThat(consoleToken.scopes())
                .containsExactly("metadata.version.read", "record.read", "audit.read");
        Claims consoleClaims = Jwts.parser().verifyWith(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        ((RSAPrivateCrtKey) keys.getPrivate()).getModulus(),
                        ((RSAPrivateCrtKey) keys.getPrivate()).getPublicExponent())))
                .build().parseSignedClaims(consoleToken.token()).getPayload();
        assertThat(consoleClaims.get("approvals", List.class)).containsExactly("approval-1");
        assertThat(claims.get("membership_version", String.class)).isNotBlank();
        assertThat(service.jwks().get("keys")).isNotNull();
        assertThat(issued.expiresAt()).isAfter(claims.getIssuedAt().toInstant());
        OfficialAccessTokenService.VerifiedContext verified = service.verifyDevAutopilotContext(issued.token());
        assertThat(verified.companyId()).isEqualTo(company.getId());
        assertThat(verified.tenantId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(verified.principalId()).isEqualTo(account.getId());
        assertThat(verified.principalType()).isEqualTo("HUMAN");
    }

    @Test
    void rejectsIssuanceWhenAccountHasNoKeycloakIdentityBinding() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AccountExternalIdentityRepository identityRepository = org.mockito.Mockito.mock(AccountExternalIdentityRepository.class);
        SematticeProvisioningBindingRepository bindingRepository = org.mockito.Mockito.mock(SematticeProvisioningBindingRepository.class);
        SematticeMetadataApprovalService approvalService = org.mockito.Mockito.mock(SematticeMetadataApprovalService.class);
        UserAccountEntity account = new UserAccountEntity("13902400998");
        UserEntity member = new UserEntity(new CompanyEntity("orgbbbbbbbbbbbbbbbbb", "测试公司", "ACTIVE"), account, "OWNER");
        when(identityRepository.findByAccount_Id(account.getId())).thenReturn(Optional.empty());
        OfficialAccessTokenService service = new OfficialAccessTokenService(
                identityRepository,
                bindingRepository,
                approvalService,
                true,
                "https://x.agentcici.com",
                "oact-test-1",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                List.of("metadata.version.read"),
                List.of("metadata.version.read"),
                600);

        assertThatThrownBy(() -> service.issueForSemattice(member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("尚未绑定统一身份");
    }

    @Test
    void signsServiceTokenWithBoundedPrincipalAndAccountableOwner() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        OfficialAccessTokenService service = new OfficialAccessTokenService(
                org.mockito.Mockito.mock(AccountExternalIdentityRepository.class),
                org.mockito.Mockito.mock(SematticeProvisioningBindingRepository.class),
                org.mockito.Mockito.mock(SematticeMetadataApprovalService.class),
                true,
                "https://x.agentcici.com",
                "oact-test-1",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                List.of("metadata.version.read", "record.read"),
                List.of("metadata.version.read", "record.read", "runtime.record.delete"),
                600);

        String principalId = "11111111-1111-4111-8111-111111111111";
        String ownerPrincipalId = "22222222-2222-4222-8222-222222222222";
        OfficialAccessTokenService.IssuedToken issued = service.issueForSematticeService(
                principalId, ownerPrincipalId, "agentcici-data-sync",
                "33333333-3333-4333-8333-333333333333", "orgaaaaaaaaaaaaaaaaa",
                List.of("record.read"), ownerPrincipalId, "PRIMARY_OWNER");
        Claims claims = Jwts.parser().verifyWith(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        ((RSAPrivateCrtKey) keys.getPrivate()).getModulus(),
                        ((RSAPrivateCrtKey) keys.getPrivate()).getPublicExponent())))
                .build().parseSignedClaims(issued.token()).getPayload();

        assertThat(claims.getSubject()).isEqualTo(principalId);
        assertThat(claims.get("principal_type", String.class)).isEqualTo("SERVICE");
        assertThat(claims.get("owner_principal_id", String.class)).isEqualTo(ownerPrincipalId);
        assertThat(claims.get("client_id", String.class)).isEqualTo("agentcici-data-sync");
        assertThat(claims.get("lifecycle_status", String.class)).isEqualTo("ACTIVE");
        assertThat(claims.get("actor_type", String.class)).isEqualTo("service");
        assertThat(claims.get("delegated_by_principal_id", String.class)).isEqualTo(ownerPrincipalId);
        assertThat(claims.get("delegation_policy", String.class)).isEqualTo("PRIMARY_OWNER");
        assertThat(claims.get("scope", String.class)).isEqualTo("record.read");
        OfficialAccessTokenService.IssuedToken deleteToken = service.issueForSematticeService(
                principalId, ownerPrincipalId, "agentcici-data-sync",
                "33333333-3333-4333-8333-333333333333", "orgaaaaaaaaaaaaaaaaa",
                List.of("runtime.record.delete"));
        assertThat(deleteToken.scopes()).containsExactly("runtime.record.delete");
        assertThatThrownBy(() -> service.issueForSematticeService(
                principalId, ownerPrincipalId, "agentcici-data-sync", "tenant", "company", List.of("audit.read")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("scope");
    }
}
