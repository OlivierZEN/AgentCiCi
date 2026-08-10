package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.CompanyIdGenerator;
import com.codehouse.ciciassistant.auth.service.CompanyProvisioningService;
import com.codehouse.ciciassistant.auth.service.PasswordHashService;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CompanyProvisioningServiceTest {

    @Test
    void refreshesDatabaseGeneratedPublicIdBeforeReturningNewAccount() {
        CompanyRepository companies = mock(CompanyRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AccountAuthCredentialRepository credentials = mock(AccountAuthCredentialRepository.class);
        AccountLoginIdentifierRepository identifiers = mock(AccountLoginIdentifierRepository.class);
        UserRepository members = mock(UserRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        UserAccountEntity account = new UserAccountEntity("17772207084");
        when(accounts.saveAndFlush(any(UserAccountEntity.class))).thenReturn(account);
        when(identifiers.findByAccount_IdAndIdentifierTypeAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(identifiers.findByIdentifierTypeAndNormalizedValueAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());

        CompanyProvisioningService service = new CompanyProvisioningService(
                companies,
                accounts,
                credentials,
                identifiers,
                members,
                entityManager,
                mock(CompanyIdGenerator.class),
                mock(PasswordHashService.class));

        UserAccountEntity created = service.createMobileAccount(
                "17772207084", "Pengtiro", "pengtiro@gmail.com");

        assertThat(created).isSameAs(account);
        InOrder persistenceOrder = inOrder(accounts, entityManager, identifiers);
        persistenceOrder.verify(accounts).saveAndFlush(any(UserAccountEntity.class));
        persistenceOrder.verify(entityManager).refresh(account);
        persistenceOrder.verify(identifiers).findByAccount_IdAndIdentifierTypeAndStatus(any(), any(), any());
    }
}
