package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.AdminUserService;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
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
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        UserAccountEntity account = new UserAccountEntity("13900000002");
        UserEntity pending = new UserEntity(Mockito.mock(CompanyEntity.class), account, RoleCodes.ORG_ADMIN);
        pending.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        when(users.findByIdAndCompany_Id(pending.getId(), "company-1")).thenReturn(Optional.of(pending));
        when(provisioning.resendHumanActivation(account)).thenReturn(
                new KeycloakIdentityProvisioningService.ProvisionResult(true, true, "subject-1"));

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, provisioning);

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
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
        UserEntity active = new UserEntity(Mockito.mock(CompanyEntity.class), new UserAccountEntity("13900000003"), RoleCodes.ORG_USER);
        when(users.findByIdAndCompany_Id(active.getId(), "company-1")).thenReturn(Optional.of(active));

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, provisioning);

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
        KeycloakIdentityProvisioningService provisioning = Mockito.mock(KeycloakIdentityProvisioningService.class);
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

        AdminUserService service = new AdminUserService(users, companies, accounts, identifiers, provisioning);

        assertThat(service.inviteMember("company-1", "13900000001", "member@example.com", "成员", RoleCodes.ORG_ADMIN))
                .containsEntry("memberStatus", UserEntity.STATUS_SUSPENDED)
                .containsEntry("roleCode", RoleCodes.ORG_USER);
        verifyNoInteractions(provisioning);
        verify(users, never()).save(any());
    }
}
