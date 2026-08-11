package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityEntity;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.AdminUserService;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminUserServiceTest {

    @Test
    void resendsActivationOnlyForThePendingMemberWithoutChangingMembershipData() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserAccountEntity account = new UserAccountEntity("13900000002");
        UserEntity pending = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_ADMIN);
        pending.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        when(users.findByIdAndCompany_Id(pending.getId(), "company-1")).thenReturn(Optional.of(pending));
        when(provisioning.resendHumanActivation(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(true, true, "subject-1"));

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        assertThat(service.resendActivationEmail("company-1", pending.getId()))
                .containsEntry("id", pending.getId())
                .containsEntry("memberStatus", UserEntity.STATUS_PENDING_ACTIVATION)
                .containsEntry("roleCode", RoleCodes.ORG_ADMIN);
        verify(provisioning).resendHumanActivation(account);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsActivationResendForAnAlreadyActiveMember() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserEntity active = new UserEntity(Mockito.mock(CompanyEntity.class), new UserAccountEntity("13900000003"), RoleCodes.ORG_USER);
        when(users.findByIdAndCompany_Id(active.getId(), "company-1")).thenReturn(Optional.of(active));

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.resendActivationEmail("company-1", active.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("仅可向待激活成员重发初始化邮件");
        verifyNoInteractions(provisioning);
    }

    @Test
    void doesNotReactivateASuspendedMemberWhenAnAdministratorInvitesAgain() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserAccountEntity account = new UserAccountEntity("13900000001");
        UserEntity suspended = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_USER);
        suspended.setMemberStatus(UserEntity.STATUS_SUSPENDED);

        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accounts.findByPrimaryMobile("13900000001")).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        when(identifiers.findByAccount_IdAndIdentifierTypeAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(users.findByCompany_IdAndAccount_Id("company-1", account.getId())).thenReturn(Optional.of(suspended));

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        assertThat(service.inviteMember("company-1", "13900000001", "member@example.com", "成员", RoleCodes.ORG_ADMIN))
                .containsEntry("memberStatus", UserEntity.STATUS_SUSPENDED)
                .containsEntry("roleCode", RoleCodes.ORG_USER);
        verifyNoInteractions(provisioning);
        verify(users, never()).save(any());
    }

    @Test
    void reconcilesMissingIdentityWithoutChangingRoleOrProfile() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserAccountEntity account = new UserAccountEntity("13900000004");
        UserEntity active = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_ADMIN);
        active.setNickname("原昵称");
        when(users.findByIdAndCompany_Id(active.getId(), "company-1")).thenReturn(Optional.of(active));
        when(externalIdentities.findByAccount_Id(account.getId())).thenReturn(Optional.empty());
        when(provisioning.ensureHumanIdentity(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(false, true, "subject-1"));

        AdminUserService service = new AdminUserService(
                users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        assertThat(service.reconcileIdentity(
                "company-1", active.getId(), "13900000004", "request-1", "actor-1", RoleCodes.ORG_ADMIN))
                .containsEntry("memberStatus", UserEntity.STATUS_PENDING_ACTIVATION)
                .containsEntry("roleCode", RoleCodes.ORG_ADMIN)
                .containsEntry("nickname", "原昵称");
        verify(provisioning).ensureHumanIdentity(account);
        verify(users).saveAndFlush(active);
        verify(audit).log(
                "company-1", "actor-1", RoleCodes.ORG_ADMIN,
                "company_member.identity_reconciled", "company_member_identity", active.getId(),
                "idempotencyKey=request-1;activationRequired=true");
    }

    @Test
    void rejectsIdentityReconciliationWhenConfirmationDoesNotMatch() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserEntity active = new UserEntity(
                Mockito.mock(CompanyEntity.class), new UserAccountEntity("13900000005"), RoleCodes.ORG_USER);
        when(users.findByIdAndCompany_Id(active.getId(), "company-1")).thenReturn(Optional.of(active));

        AdminUserService service = new AdminUserService(
                users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.reconcileIdentity(
                        "company-1", active.getId(), "13900000006", "request-2", "actor-1", RoleCodes.ORG_ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号确认不匹配");
        verifyNoInteractions(provisioning);
    }

    @Test
    void rejectsIdentityReconciliationWhenBindingAlreadyExists() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserAccountEntity account = new UserAccountEntity("13900000007");
        UserEntity active = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_USER);
        when(users.findByIdAndCompany_Id(active.getId(), "company-1")).thenReturn(Optional.of(active));
        when(externalIdentities.findByAccount_Id(account.getId())).thenReturn(
                Optional.of(Mockito.mock(AccountExternalIdentityEntity.class)));

        AdminUserService service = new AdminUserService(
                users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.reconcileIdentity(
                        "company-1", active.getId(), "13900000007", "request-3", "actor-1", RoleCodes.ORG_ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("该成员已绑定统一身份，无需修复");
        verifyNoInteractions(provisioning);
    }

    @Test
    void returnsTheCurrentMemberForAnAlreadyCompletedIdempotentRequest() {
        UserRepository users = Mockito.mock(UserRepository.class);
        CompanyRepository companies = Mockito.mock(CompanyRepository.class);
        UserAccountRepository accounts = Mockito.mock(UserAccountRepository.class);
        AccountLoginIdentifierRepository identifiers = Mockito.mock(AccountLoginIdentifierRepository.class);
        AccountExternalIdentityRepository externalIdentities = Mockito.mock(AccountExternalIdentityRepository.class);
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        PlatformAuditService audit = Mockito.mock(PlatformAuditService.class);
        UserAccountEntity account = new UserAccountEntity("13900000008");
        UserEntity pending = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_USER);
        pending.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        when(users.findByIdAndCompany_Id(pending.getId(), "company-1")).thenReturn(Optional.of(pending));
        when(audit.hasEventDetail(
                "company-1", "company_member.identity_reconciled", pending.getId(), "idempotencyKey=request-4;"))
                .thenReturn(true);

        AdminUserService service = new AdminUserService(
                users, companies, accounts, identifiers, externalIdentities, provisioning, audit);

        assertThat(service.reconcileIdentity(
                "company-1", pending.getId(), "13900000008", "request-4", "actor-1", RoleCodes.ORG_ADMIN))
                .containsEntry("memberStatus", UserEntity.STATUS_PENDING_ACTIVATION);
        verifyNoInteractions(provisioning);
    }
}
