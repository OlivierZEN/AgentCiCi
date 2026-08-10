package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.CompanyProvisioningService;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantOwnerRecoveryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlatformTenantOwnerRecoveryServiceTest {

    private final CompanyRepository companies = Mockito.mock(CompanyRepository.class);
    private final CompanyProvisioningService provisioning = Mockito.mock(CompanyProvisioningService.class);
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final KeycloakIdentityProvisioningService identities = Mockito.mock(KeycloakIdentityProvisioningService.class);
    private final PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
    private final PlatformTenantOwnerRecoveryService service = new PlatformTenantOwnerRecoveryService(
            companies, provisioning, users, identities, audit);
    private final CompanyEntity company = new CompanyEntity("org00000000000000002", "Recovery Tenant", "ACTIVE");

    @BeforeEach
    void setUp() {
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
    }

    @Test
    void atomicallyReplacesAPendingOwnerWithAnAlreadyActiveHumanAccount() throws Exception {
        UserAccountEntity pendingAccount = account("pending-account", "U2026PENDING1", "17772207084", "Pending Owner");
        UserEntity pendingOwner = new UserEntity(company, pendingAccount, RoleCodes.OWNER);
        pendingOwner.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        UserAccountEntity replacement = account("active-account", "U2026ACTIVE01", "13800000001", "Active Owner");
        UserEntity replacementMember = new UserEntity(company, replacement, RoleCodes.OWNER);

        when(provisioning.findMobileAccount("13800000001")).thenReturn(Optional.of(replacement));
        when(users.lockByCompanyIdAndRoleCode(company.getId(), RoleCodes.OWNER)).thenReturn(List.of(pendingOwner));
        when(users.findByCompany_IdOrderByCreatedAtDesc(company.getId())).thenReturn(List.of(pendingOwner));
        when(identities.requireActiveHumanIdentity(replacement))
                .thenReturn(new KeycloakIdentityProvisioningService.ActiveHumanIdentity("active-subject"));
        when(provisioning.createOwnerMembership(company, replacement, "Active Owner"))
                .thenReturn(replacementMember);

        PlatformTenantOwnerRecoveryService.OwnerRecoveryView result = service.recover(
                company.getId(), "13800000001", "platform-admin", RoleCodes.PLATFORM_ADMIN);

        assertThat(result.ownerAccountId()).isEqualTo("active-account");
        assertThat(result.ownerPublicId()).isEqualTo("U2026ACTIVE01");
        assertThat(result.reusedMembership()).isFalse();
        assertThat(pendingOwner.getRoleCode()).isEqualTo(RoleCodes.ORG_ADMIN);
        assertThat(pendingOwner.getMemberStatus()).isEqualTo(UserEntity.STATUS_PENDING_ACTIVATION);
        assertThat(replacementMember.getRoleCode()).isEqualTo(RoleCodes.OWNER);
        assertThat(replacementMember.getMemberStatus()).isEqualTo(UserEntity.STATUS_ACTIVE);
        verify(users).saveAll(List.of(pendingOwner));
        verify(users).save(replacementMember);
        verify(audit).log(company.getId(), "platform-admin", RoleCodes.PLATFORM_ADMIN,
                "platform.tenant.owner.recover", "tenant", company.getId(),
                "Recovered tenant Owner with an already-active HUMAN account; ownerMemberId=" + replacementMember.getId());
    }

    @Test
    void repeatsTheSameRecoveryIdempotentlyWithoutRemoteOrDatabaseMutation() throws Exception {
        UserAccountEntity replacement = account("active-account", "U2026ACTIVE01", "13800000001", "Active Owner");
        UserEntity activeOwner = new UserEntity(company, replacement, RoleCodes.OWNER);
        when(provisioning.findMobileAccount("13800000001")).thenReturn(Optional.of(replacement));
        when(users.lockByCompanyIdAndRoleCode(company.getId(), RoleCodes.OWNER)).thenReturn(List.of(activeOwner));

        PlatformTenantOwnerRecoveryService.OwnerRecoveryView result = service.recover(
                company.getId(), "13800000001", "platform-admin", RoleCodes.PLATFORM_ADMIN);

        assertThat(result.alreadyRecovered()).isTrue();
        verify(identities, never()).requireActiveHumanIdentity(any());
        verify(users, never()).save(any());
        verify(users, never()).saveAll(any());
        verify(audit, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refusesToOverrideAnotherActiveOwner() throws Exception {
        UserAccountEntity current = account("current-account", "U2026CURRENT1", "13900000001", "Current Owner");
        UserEntity activeOwner = new UserEntity(company, current, RoleCodes.OWNER);
        UserAccountEntity replacement = account("active-account", "U2026ACTIVE01", "13800000001", "Active Owner");
        when(provisioning.findMobileAccount("13800000001")).thenReturn(Optional.of(replacement));
        when(users.lockByCompanyIdAndRoleCode(company.getId(), RoleCodes.OWNER)).thenReturn(List.of(activeOwner));

        assertThatThrownBy(() -> service.recover(
                company.getId(), "13800000001", "platform-admin", RoleCodes.PLATFORM_ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("已有有效 Owner");
        verify(identities, never()).requireActiveHumanIdentity(any());
        verify(users, never()).save(any());
    }

    @Test
    void refusesToPromoteAnIdentityThatHasNotCompletedActivation() throws Exception {
        UserAccountEntity pendingAccount = account("pending-account", "U2026PENDING1", "17772207084", "Pending Owner");
        UserEntity pendingOwner = new UserEntity(company, pendingAccount, RoleCodes.OWNER);
        pendingOwner.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        UserAccountEntity replacement = account("pending-replacement", "U2026PENDALT", "13800000001", "Pending Replacement");
        when(provisioning.findMobileAccount("13800000001")).thenReturn(Optional.of(replacement));
        when(users.lockByCompanyIdAndRoleCode(company.getId(), RoleCodes.OWNER)).thenReturn(List.of(pendingOwner));
        when(users.findByCompany_IdOrderByCreatedAtDesc(company.getId())).thenReturn(List.of(pendingOwner));
        when(identities.requireActiveHumanIdentity(replacement))
                .thenThrow(new IllegalStateException("目标账号尚未完成统一身份激活"));

        assertThatThrownBy(() -> service.recover(
                company.getId(), "13800000001", "platform-admin", RoleCodes.PLATFORM_ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("目标账号尚未完成统一身份激活");
        assertThat(pendingOwner.getRoleCode()).isEqualTo(RoleCodes.OWNER);
        verify(users, never()).save(any());
        verify(users, never()).saveAll(any());
        verify(audit, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    private static UserAccountEntity account(
            String id, String publicId, String mobile, String displayName) throws Exception {
        UserAccountEntity account = new UserAccountEntity(mobile);
        setField(account, "id", id);
        setField(account, "publicId", publicId);
        account.setDisplayName(displayName);
        return account;
    }

    private static void setField(Object target, String field, String value) throws Exception {
        java.lang.reflect.Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }
}
