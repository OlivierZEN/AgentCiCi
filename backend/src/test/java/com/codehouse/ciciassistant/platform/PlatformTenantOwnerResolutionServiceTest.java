package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantOwnerResolutionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformTenantOwnerResolutionServiceTest {

    private static final String PUBLIC_ID = "U123456789ABC";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AccountLoginIdentifierRepository identifiers = mock(AccountLoginIdentifierRepository.class);
    private final AccountExternalIdentityRepository externalIdentities = mock(AccountExternalIdentityRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final KeycloakIdentityProvisioningService identities = mock(KeycloakIdentityProvisioningService.class);
    private final PlatformTenantOwnerResolutionService service = new PlatformTenantOwnerResolutionService(
            accounts, identifiers, externalIdentities, users, identities);

    private UserAccountEntity account;

    @BeforeEach
    void setUp() {
        account = account("13800138000", "zhangsan@example.com", "张三", PUBLIC_ID);
        when(identities.isEnabled()).thenReturn(true);
        when(users.findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_ACTIVE))
                .thenReturn(List.of(mock(UserEntity.class), mock(UserEntity.class)));
        when(externalIdentities.findByAccount_Id(account.getId()))
                .thenReturn(Optional.of(mock(AccountExternalIdentityEntity.class)));
    }

    @Test
    void resolvesTheSameAccountFromMobileAndEmailWithoutCreatingAnotherIdentity() {
        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(
                AccountLoginIdentifierEntity.TYPE_MOBILE, "13800138000", AccountLoginIdentifierEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(identifier(account, AccountLoginIdentifierEntity.TYPE_MOBILE, "13800138000")));
        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(
                AccountLoginIdentifierEntity.TYPE_EMAIL, "zhangsan@example.com", AccountLoginIdentifierEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(identifier(account, AccountLoginIdentifierEntity.TYPE_EMAIL, "zhangsan@example.com")));

        PlatformTenantOwnerResolutionService.OwnerResolutionView result = service.resolve(
                new PlatformTenantOwnerResolutionService.OwnerResolutionCommand(
                        "13800138000", "ZHANGSAN@example.com", null));

        assertThat(result.resolution()).isEqualTo("EXISTING_ACCOUNT");
        assertThat(result.accountPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(result.maskedMobile()).isEqualTo("138****8000");
        assertThat(result.maskedEmail()).isEqualTo("z***@example.com");
        assertThat(result.identityStatus()).isEqualTo("ACTIVE");
        assertThat(result.activeTenantCount()).isEqualTo(2);
        assertThat(result.matchBasis()).containsExactly("MOBILE", "EMAIL");
    }

    @Test
    void blocksWhenMobileAndEmailBelongToDifferentAccounts() {
        UserAccountEntity another = account("13900139000", "lisi@example.com", "李四", "U987654321ABC");
        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(
                AccountLoginIdentifierEntity.TYPE_MOBILE, "13800138000", AccountLoginIdentifierEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(identifier(account, AccountLoginIdentifierEntity.TYPE_MOBILE, "13800138000")));
        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(
                AccountLoginIdentifierEntity.TYPE_EMAIL, "lisi@example.com", AccountLoginIdentifierEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(identifier(another, AccountLoginIdentifierEntity.TYPE_EMAIL, "lisi@example.com")));

        PlatformTenantOwnerResolutionService.OwnerResolutionView result = service.resolve(
                new PlatformTenantOwnerResolutionService.OwnerResolutionCommand(
                        "13800138000", "lisi@example.com", null));

        assertThat(result.resolution()).isEqualTo("IDENTIFIER_CONFLICT");
        assertThat(result.canProceed()).isFalse();
        assertThat(result.accountPublicId()).isNull();
    }

    @Test
    void marksUnusedIdentifiersAsEligibleForANewAccount() {
        PlatformTenantOwnerResolutionService.OwnerResolutionView result = service.resolve(
                new PlatformTenantOwnerResolutionService.OwnerResolutionCommand(
                        "13700137000", "new@example.com", null));

        assertThat(result.resolution()).isEqualTo("NEW_ACCOUNT");
        assertThat(result.canProceed()).isTrue();
        assertThat(result.accountPublicId()).isNull();
    }

    @Test
    void resolvesAnExistingAccountByImmutablePublicId() {
        when(accounts.findByPublicIdIgnoreCase(PUBLIC_ID)).thenReturn(Optional.of(account));

        PlatformTenantOwnerResolutionService.OwnerResolutionView result = service.resolve(
                new PlatformTenantOwnerResolutionService.OwnerResolutionCommand(null, null, PUBLIC_ID));

        assertThat(result.resolution()).isEqualTo("EXISTING_ACCOUNT");
        assertThat(result.matchBasis()).containsExactly("PUBLIC_ID");
    }

    private AccountLoginIdentifierEntity identifier(UserAccountEntity owner, String type, String value) {
        return new AccountLoginIdentifierEntity(owner, type, value, value);
    }

    private UserAccountEntity account(String mobile, String email, String displayName, String publicId) {
        UserAccountEntity value = new UserAccountEntity(mobile);
        value.setEmail(email);
        value.setDisplayName(displayName);
        ReflectionTestUtils.setField(value, "publicId", publicId);
        return value;
    }
}
