package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantOwnerIdentityService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformTenantOwnerIdentityServiceTest {

    private static final String COMPANY_ID = "org3989ajn55ev8eqj51";
    private static final String PUBLIC_ID = "U2026WVBJQGYU";

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AccountExternalIdentityRepository identities = mock(AccountExternalIdentityRepository.class);
    private final KeycloakIdentityProvisioningService provisioning = mock(KeycloakIdentityProvisioningService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformTenantOwnerIdentityService service = new PlatformTenantOwnerIdentityService(
            companies, users, identities, provisioning, audit);

    private CompanyEntity company;
    private UserAccountEntity account;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        company = new CompanyEntity(COMPANY_ID, "Insurance industry", "ACTIVE");
        account = new UserAccountEntity("13900000002");
        account.setDisplayName("UAT Owner");
        account.setEmail("owner@example.test");
        ReflectionTestUtils.setField(account, "publicId", PUBLIC_ID);
        owner = new UserEntity(company, account, RoleCodes.OWNER);
        owner.setNickname("UAT Owner");
        when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(users.findByCompany_IdAndRoleCodeOrderByCreatedAtAsc(COMPANY_ID, RoleCodes.OWNER))
                .thenReturn(List.of(owner));
        when(users.lockByCompanyIdAndRoleCode(COMPANY_ID, RoleCodes.OWNER)).thenReturn(List.of(owner));
        when(identities.findByAccount_Id(account.getId())).thenReturn(Optional.empty());
    }

    @Test
    void exposesMissingIdentityWithMaskedContactDetails() {
        PlatformTenantOwnerIdentityService.OwnerIdentityView result = service.get(COMPANY_ID);

        assertThat(result.displayName()).isEqualTo("UAT Owner");
        assertThat(result.maskedEmail()).isEqualTo("o***@example.test");
        assertThat(result.maskedMobile()).isEqualTo("139****0002");
        assertThat(result.publicId()).isEqualTo(PUBLIC_ID);
        assertThat(result.identityState()).isEqualTo("MISSING");
        assertThat(result.recoverable()).isTrue();
    }

    @Test
    void reconcilesTheSameOwnerAndLeavesActivationPending() {
        when(provisioning.ensureHumanIdentity(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(false, true, "subject-1"));

        PlatformTenantOwnerIdentityService.OwnerIdentityView result = service.reconcile(
                COMPANY_ID, PUBLIC_ID, "owner-reconcile-1", "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.memberStatus()).isEqualTo(UserEntity.STATUS_PENDING_ACTIVATION);
        verify(users).saveAndFlush(owner);
        verify(audit).log(
                eq(COMPANY_ID),
                eq("platform-admin"),
                eq("PLATFORM_ADMIN"),
                eq(PlatformTenantOwnerIdentityService.AUDIT_EVENT),
                eq("tenant_owner_identity"),
                eq(owner.getId()),
                contains("idempotencyKey=owner-reconcile-1"));
    }

    @Test
    void rejectsAConfirmationForAnotherPublicId() {
        assertThatThrownBy(() -> service.reconcile(
                COMPANY_ID, "U2026WVBJQGYX", "owner-reconcile-2", "platform-admin", "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Owner 公共编号不匹配");

        verify(provisioning, never()).ensureHumanIdentity(account);
    }

    @Test
    void returnsCurrentStateForAnAlreadyCompletedIdempotencyKey() {
        when(audit.hasEventDetail(
                COMPANY_ID,
                PlatformTenantOwnerIdentityService.AUDIT_EVENT,
                owner.getId(),
                "idempotencyKey=owner-reconcile-3;"))
                .thenReturn(true);

        PlatformTenantOwnerIdentityService.OwnerIdentityView result = service.reconcile(
                COMPANY_ID, PUBLIC_ID, "owner-reconcile-3", "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.identityState()).isEqualTo("MISSING");
        verify(provisioning, never()).ensureHumanIdentity(account);
        verify(audit, never()).log(eq(COMPANY_ID), eq("platform-admin"), eq("PLATFORM_ADMIN"),
                eq(PlatformTenantOwnerIdentityService.AUDIT_EVENT), eq("tenant_owner_identity"),
                eq(owner.getId()), contains("owner-reconcile-3"));
    }

    @Test
    void failsClosedWhenTenantHasMoreThanOneOwner() {
        UserEntity duplicate = new UserEntity(company, new UserAccountEntity("17772207085"), RoleCodes.OWNER);
        when(users.findByCompany_IdAndRoleCodeOrderByCreatedAtAsc(COMPANY_ID, RoleCodes.OWNER))
                .thenReturn(List.of(owner, duplicate));

        assertThatThrownBy(() -> service.get(COMPANY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户 Owner 数据异常，请先完成所有权治理");
    }

    @Test
    void reportsPendingWhenLocalBindingExists() {
        owner.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        when(identities.findByAccount_Id(account.getId()))
                .thenReturn(Optional.of(mock(AccountExternalIdentityEntity.class)));

        PlatformTenantOwnerIdentityService.OwnerIdentityView result = service.get(COMPANY_ID);

        assertThat(result.identityState()).isEqualTo("PENDING_ACTIVATION");
        assertThat(result.recoverable()).isTrue();
    }
}
