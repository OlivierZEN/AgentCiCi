package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.service.CompanyProvisioningService;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import com.codehouse.ciciassistant.platform.domain.CompanyExportJobRepository;
import com.codehouse.ciciassistant.platform.domain.CompanyPurgeJobRepository;
import com.codehouse.ciciassistant.platform.domain.CompanyRetentionPolicyEntity;
import com.codehouse.ciciassistant.platform.domain.CompanyRetentionPolicyRepository;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantLifecycleService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantOwnerResolutionService;
import com.codehouse.ciciassistant.platform.service.PlatformTenantProvisioningIdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class PlatformTenantOwnerProvisioningTest {

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyProvisioningService companyProvisioning = mock(CompanyProvisioningService.class);
    private final KeycloakIdentityProvisioningService identities = mock(KeycloakIdentityProvisioningService.class);
    private final PlatformTenantOwnerResolutionService ownerResolution = mock(PlatformTenantOwnerResolutionService.class);
    private final PlatformTenantProvisioningIdempotencyService idempotency = mock(PlatformTenantProvisioningIdempotencyService.class);
    private final CompanyRetentionPolicyRepository retention = mock(CompanyRetentionPolicyRepository.class);
    private final CompanyPurgeJobRepository purgeJobs = mock(CompanyPurgeJobRepository.class);
    private final CompanyExportJobRepository exportJobs = mock(CompanyExportJobRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformTenantLifecycleService service = new PlatformTenantLifecycleService(
            companies,
            companyProvisioning,
            identities,
            ownerResolution,
            idempotency,
            retention,
            purgeJobs,
            exportJobs,
            audit,
            mock(JdbcTemplate.class),
            new ObjectMapper(),
            mock(VectorStoreClient.class),
            mock(PlatformTransactionManager.class),
            "./target/test-kb",
            "./target/test-exports",
            "test-worker",
            60);

    private CompanyEntity company;
    private UserAccountEntity account;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        company = new CompanyEntity("company-owner-oidc", "统一身份租户", "ACTIVE");
        account = new UserAccountEntity("17772207084");
        account.setEmail("owner@example.com");
        ReflectionTestUtils.setField(account, "publicId", "U2026EXISTING");
        owner = new UserEntity(company, account, RoleCodes.OWNER);
        when(ownerResolution.resolveOwner(any())).thenReturn(new PlatformTenantOwnerResolutionService.ResolvedOwner(
                PlatformTenantOwnerResolutionService.Resolution.NEW_ACCOUNT,
                null,
                java.util.List.of(),
                "new"));
        when(companyProvisioning.createCompany("统一身份租户")).thenReturn(company);
        when(companyProvisioning.createMobileAccount("17772207084", "Owner", "owner@example.com")).thenReturn(account);
        when(companyProvisioning.createMobileAccount("17772207084", "Owner", null)).thenReturn(account);
        when(companyProvisioning.createOwnerMembership(company, account, "Owner")).thenReturn(owner);
        when(companyProvisioning.createOwnerMembership(eq(company), eq(account), isNull())).thenReturn(owner);
        when(retention.findById(company.getId())).thenReturn(Optional.empty());
        when(retention.save(any(CompanyRetentionPolicyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void provisionsKeycloakAndLeavesNewOwnerPendingWithoutLocalPassword() {
        when(identities.isEnabled()).thenReturn(true);
        when(identities.ensureHumanIdentity(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(false, true, "subject-1"));

        PlatformTenantLifecycleService.TenantProvisionView result = service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "AUTO", null, "17772207084", "Owner", "owner@example.com", null, "uat", "test-new-pending"),
                "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.ownerActivationRequired()).isTrue();
        assertThat(owner.getMemberStatus()).isEqualTo(UserEntity.STATUS_PENDING_ACTIVATION);
        verify(identities).ensureHumanIdentity(account);
        verify(companyProvisioning, never()).assignPasswordCredential(any(), any());
    }

    @Test
    void requiresOwnerEmailWhenUnifiedIdentityIsEnabled() {
        when(identities.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "AUTO", null, "17772207084", "Owner", null, null, "uat", "test-email-required"),
                "platform-admin", "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("统一认证启用时新 Owner 邮箱不能为空");

        verify(companyProvisioning, never()).createCompany(any());
        verify(identities, never()).ensureHumanIdentity(any());
    }

    @Test
    void preservesLocalPasswordCompatibilityWhenUnifiedIdentityIsDisabled() {
        when(identities.isEnabled()).thenReturn(false);

        PlatformTenantLifecycleService.TenantProvisionView result = service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "AUTO", null, "17772207084", "Owner", null, "local-password", "local", "test-local-password"),
                "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.ownerActivationRequired()).isFalse();
        assertThat(owner.getMemberStatus()).isEqualTo(UserEntity.STATUS_ACTIVE);
        verify(companyProvisioning).assignPasswordCredential(account, "local-password");
        verify(identities, never()).ensureHumanIdentity(any());
    }

    @Test
    void reusesExistingUnifiedAccountWithoutResettingCredentials() {
        when(identities.isEnabled()).thenReturn(true);
        when(ownerResolution.resolveOwner(any())).thenReturn(new PlatformTenantOwnerResolutionService.ResolvedOwner(
                PlatformTenantOwnerResolutionService.Resolution.EXISTING_ACCOUNT,
                account,
                java.util.List.of("MOBILE", "EMAIL"),
                "existing"));
        when(ownerResolution.resolve(any())).thenReturn(new PlatformTenantOwnerResolutionService.OwnerResolutionView(
                "EXISTING_ACCOUNT", true, "U2026EXISTING", "Owner", "177****7084", "o***@example.com",
                "BOUND", 0, java.util.List.of("MOBILE", "EMAIL"), true, "existing"));
        when(identities.ensureHumanIdentity(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(true, false, "subject-existing"));

        PlatformTenantLifecycleService.TenantProvisionView result = service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "AUTO", null, "17772207084", "Owner", "owner@example.com", null, "uat", "test-existing"),
                "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.reusedExistingAccount()).isTrue();
        assertThat(result.ownerActivationRequired()).isFalse();
        assertThat(owner.getMemberStatus()).isEqualTo(UserEntity.STATUS_ACTIVE);
        verify(companyProvisioning, never()).createMobileAccount(any(), any(), any());
        verify(companyProvisioning, never()).assignPasswordCredential(any(), any());
    }

    @Test
    void reusesAnActiveUnifiedAccountWithoutProvisioningIdentityAgain() {
        when(identities.isEnabled()).thenReturn(true);
        when(ownerResolution.resolveOwner(any())).thenReturn(new PlatformTenantOwnerResolutionService.ResolvedOwner(
                PlatformTenantOwnerResolutionService.Resolution.EXISTING_ACCOUNT,
                account,
                java.util.List.of("PUBLIC_ID"),
                "existing"));
        when(ownerResolution.resolve(any())).thenReturn(new PlatformTenantOwnerResolutionService.OwnerResolutionView(
                "EXISTING_ACCOUNT", true, "U2026EXISTING", "Owner", "177****7084", "o***@example.com",
                "ACTIVE", 2, java.util.List.of("PUBLIC_ID"), true, "existing"));

        PlatformTenantLifecycleService.TenantProvisionView result = service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "EXISTING", "U2026EXISTING", null, null, null, null, "uat", "test-existing-active"),
                "platform-admin", "PLATFORM_ADMIN");

        assertThat(result.reusedExistingAccount()).isTrue();
        assertThat(result.ownerActivationRequired()).isFalse();
        assertThat(owner.getMemberStatus()).isEqualTo(UserEntity.STATUS_ACTIVE);
        verify(identities, never()).ensureHumanIdentity(any());
        verify(companyProvisioning, never()).createMobileAccount(any(), any(), any());
    }

    @Test
    void replaysTheCompletedIdempotentProvisioningResult() {
        PlatformTenantLifecycleService.TenantProvisionView previous = new PlatformTenantLifecycleService.TenantProvisionView(
                "org00000000000000001", "统一身份租户", "ACTIVE", "member-1", "account-1", true, false, "EXISTING");
        when(idempotency.findReplay(any(), any())).thenReturn(previous);

        PlatformTenantLifecycleService.TenantProvisionView result = service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "EXISTING", "U2026EXISTING", null, null, null, null, "uat", "test-replay"),
                "platform-admin", "PLATFORM_ADMIN");

        assertThat(result).isSameAs(previous);
        verify(companyProvisioning, never()).createCompany(any());
        verify(ownerResolution, never()).resolveOwner(any());
    }

    @Test
    void failsClosedBeforeCreatingOwnerMembershipWhenKeycloakProvisioningFails() {
        when(identities.isEnabled()).thenReturn(true);
        when(identities.ensureHumanIdentity(account)).thenThrow(new IllegalStateException("Keycloak 用户创建或邀请发送失败"));

        assertThatThrownBy(() -> service.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        "统一身份租户", "AUTO", null, "17772207084", "Owner", "owner@example.com", null, "uat", "test-keycloak-failure"),
                "platform-admin", "PLATFORM_ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak 用户创建或邀请发送失败");

        verify(companyProvisioning, never()).createOwnerMembership(any(), any(), any());
        verifyNoPlatformAudit();
    }

    private void verifyNoPlatformAudit() {
        verify(audit, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
