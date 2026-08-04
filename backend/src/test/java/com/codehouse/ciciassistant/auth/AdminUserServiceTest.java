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
