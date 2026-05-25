package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialEntity;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationProvisioningService {

    private final OrgRepository orgRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountAuthCredentialRepository accountAuthCredentialRepository;
    private final AccountLoginIdentifierRepository accountLoginIdentifierRepository;
    private final UserRepository userRepository;
    private final OrganizationIdGenerator organizationIdGenerator;
    private final PasswordHashService passwordHashService;

    public OrganizationProvisioningService(OrgRepository orgRepository,
                                           UserAccountRepository userAccountRepository,
                                           AccountAuthCredentialRepository accountAuthCredentialRepository,
                                           AccountLoginIdentifierRepository accountLoginIdentifierRepository,
                                           UserRepository userRepository,
                                           OrganizationIdGenerator organizationIdGenerator,
                                           PasswordHashService passwordHashService) {
        this.orgRepository = orgRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountAuthCredentialRepository = accountAuthCredentialRepository;
        this.accountLoginIdentifierRepository = accountLoginIdentifierRepository;
        this.userRepository = userRepository;
        this.organizationIdGenerator = organizationIdGenerator;
        this.passwordHashService = passwordHashService;
    }

    public Optional<UserAccountEntity> findMobileAccount(String mobileNorm) {
        return accountLoginIdentifierRepository
                .findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileNorm,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> userAccountRepository.findByPrimaryMobile(mobileNorm));
    }

    @Transactional
    public OrgEntity createOrganization(String organizationName) {
        String name = trimToNull(organizationName);
        if (name == null) {
            throw new IllegalArgumentException("组织名称不能为空");
        }
        String id;
        do {
            id = organizationIdGenerator.nextId();
        } while (orgRepository.existsById(id));
        return orgRepository.save(new OrgEntity(id, name, "ACTIVE"));
    }

    @Transactional
    public UserAccountEntity createMobileAccount(String mobileNorm, String displayName, String email) {
        UserAccountEntity account = new UserAccountEntity(mobileNorm);
        account.setDisplayName(trimToNull(displayName));
        account.setEmail(trimToNull(email));
        UserAccountEntity saved = userAccountRepository.save(account);
        ensureMobileIdentifier(saved, mobileNorm);
        syncEmailIdentifier(saved, trimToNull(email));
        return userAccountRepository.save(saved);
    }

    @Transactional
    public void assignPasswordCredential(UserAccountEntity account, String password) {
        PasswordHashService.PasswordHash hash = passwordHashService.hash(password);
        AccountAuthCredentialEntity credential = accountAuthCredentialRepository
                .findByAccount_IdAndCredentialTypeAndStatus(
                        account.getId(),
                        AccountAuthCredentialEntity.TYPE_PASSWORD,
                        AccountAuthCredentialEntity.STATUS_ACTIVE)
                .orElseGet(() -> new AccountAuthCredentialEntity(account));
        credential.replacePassword(hash.passwordHash(), hash.salt(), hash.iterations(), hash.algorithm());
        accountAuthCredentialRepository.save(credential);
    }

    @Transactional
    public UserEntity createOwnerMembership(OrgEntity org, UserAccountEntity account, String ownerDisplayName) {
        UserEntity member = new UserEntity(org, account, RoleCodes.OWNER);
        member.setNickname(resolveMemberDisplayName(ownerDisplayName, account));
        return userRepository.save(member);
    }

    private UserAccountEntity ensureMobileIdentifier(UserAccountEntity account, String mobileNorm) {
        accountLoginIdentifierRepository
                .findByAccount_IdAndIdentifierTypeAndStatus(
                        account.getId(),
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .orElseGet(() -> accountLoginIdentifierRepository.save(new AccountLoginIdentifierEntity(
                        account,
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileNorm,
                        mobileNorm)));
        return account;
    }

    private void syncEmailIdentifier(UserAccountEntity account, String emailValue) {
        accountLoginIdentifierRepository
                .findByAccount_IdAndIdentifierTypeAndStatus(
                        account.getId(),
                        AccountLoginIdentifierEntity.TYPE_EMAIL,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .ifPresentOrElse(existing -> {
                    if (emailValue == null) {
                        accountLoginIdentifierRepository.delete(existing);
                        return;
                    }
                    String normalizedEmail = normalizeEmail(emailValue);
                    accountLoginIdentifierRepository
                            .findByIdentifierTypeAndNormalizedValueAndStatus(
                                    AccountLoginIdentifierEntity.TYPE_EMAIL,
                                    normalizedEmail,
                                    AccountLoginIdentifierEntity.STATUS_ACTIVE)
                            .filter(conflict -> !conflict.getAccount().getId().equals(account.getId()))
                            .ifPresent(conflict -> {
                                throw new IllegalArgumentException("该邮箱已被其他账号使用");
                            });
                    existing.updateValue(normalizedEmail, emailValue);
                    accountLoginIdentifierRepository.save(existing);
                }, () -> {
                    if (emailValue == null) {
                        return;
                    }
                    String normalizedEmail = normalizeEmail(emailValue);
                    accountLoginIdentifierRepository
                            .findByIdentifierTypeAndNormalizedValueAndStatus(
                                    AccountLoginIdentifierEntity.TYPE_EMAIL,
                                    normalizedEmail,
                                    AccountLoginIdentifierEntity.STATUS_ACTIVE)
                            .ifPresent(conflict -> {
                                throw new IllegalArgumentException("该邮箱已被其他账号使用");
                            });
                    accountLoginIdentifierRepository.save(new AccountLoginIdentifierEntity(
                            account,
                            AccountLoginIdentifierEntity.TYPE_EMAIL,
                            normalizedEmail,
                            emailValue));
                });
    }

    private String resolveMemberDisplayName(String ownerDisplayName, UserAccountEntity account) {
        String explicit = trimToNull(ownerDisplayName);
        if (explicit != null) {
            return explicit;
        }
        String accountDisplay = trimToNull(account.getDisplayName());
        if (accountDisplay != null) {
            return accountDisplay;
        }
        return account.getPrimaryMobile();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
