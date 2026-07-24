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
        OfficialAccessTokenService service = new OfficialAccessTokenService(
                identityRepository,
                bindingRepository,
                true,
                "https://x.agentcici.com",
                "oact-test-1",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                List.of("metadata.version.read", "record.read"),
                600);

        OfficialAccessTokenService.IssuedToken issued = service.issueForSemattice(member);
        Claims claims = Jwts.parser().verifyWith(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        ((RSAPrivateCrtKey) keys.getPrivate()).getModulus(),
                        ((RSAPrivateCrtKey) keys.getPrivate()).getPublicExponent())))
                .build()
                .parseSignedClaims(issued.token())
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("https://x.agentcici.com");
        assertThat(claims.getSubject()).isEqualTo("keycloak-user-subject");
        assertThat(claims.getAudience()).containsExactly(OfficialAccessTokenService.SEMATTICE_AUDIENCE);
        assertThat(claims.get("tenant_id", String.class)).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(claims.get("company_id", String.class)).isEqualTo(company.getId());
        assertThat(claims.get("scope", String.class)).isEqualTo("metadata.version.read record.read");
        assertThat(claims.get("membership_version", String.class)).isNotBlank();
        assertThat(service.jwks().get("keys")).isNotNull();
        assertThat(issued.expiresAt()).isAfter(claims.getIssuedAt().toInstant());
    }

    @Test
    void rejectsIssuanceWhenAccountHasNoKeycloakIdentityBinding() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AccountExternalIdentityRepository identityRepository = org.mockito.Mockito.mock(AccountExternalIdentityRepository.class);
        SematticeProvisioningBindingRepository bindingRepository = org.mockito.Mockito.mock(SematticeProvisioningBindingRepository.class);
        UserAccountEntity account = new UserAccountEntity("13902400998");
        UserEntity member = new UserEntity(new CompanyEntity("orgbbbbbbbbbbbbbbbbb", "测试公司", "ACTIVE"), account, "OWNER");
        when(identityRepository.findByAccount_Id(account.getId())).thenReturn(Optional.empty());
        OfficialAccessTokenService service = new OfficialAccessTokenService(
                identityRepository,
                bindingRepository,
                true,
                "https://x.agentcici.com",
                "oact-test-1",
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                List.of("metadata.version.read"),
                600);

        assertThatThrownBy(() -> service.issueForSemattice(member))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("尚未绑定统一身份");
    }
}
