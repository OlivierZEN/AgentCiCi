package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformRegisteredUserService {

    private final UserAccountRepository userAccountRepository;
    private final UserRepository userRepository;

    public PlatformRegisteredUserService(
            UserAccountRepository userAccountRepository,
            UserRepository userRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public RegisteredUserPage listRegisteredUsers(String query, int page, int pageSize) {
        String keyword = query == null ? "" : query.trim();
        Page<UserAccountEntity> result = userAccountRepository.searchRegisteredAccounts(
                keyword,
                PageRequest.of(page, pageSize));
        Map<String, List<RegisteredUserOrganizationView>> organizationsByAccount = findOrganizations(result.getContent());
        List<RegisteredUserView> users = result.getContent().stream()
                .map(account -> toView(account, organizationsByAccount.getOrDefault(account.getId(), List.of())))
                .toList();
        return new RegisteredUserPage(users, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private Map<String, List<RegisteredUserOrganizationView>> findOrganizations(List<UserAccountEntity> accounts) {
        List<String> accountIds = accounts.stream().map(UserAccountEntity::getId).toList();
        if (accountIds.isEmpty()) {
            return Map.of();
        }

        Map<String, LinkedHashMap<String, RegisteredUserOrganizationView>> organizationsByAccount = new LinkedHashMap<>();
        userRepository.findByAccount_IdInAndMemberStatusOrderByCreatedAtDesc(accountIds, UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus()))
                .forEach(member -> addOrganization(organizationsByAccount, member));

        return organizationsByAccount.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue().values()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private void addOrganization(
            Map<String, LinkedHashMap<String, RegisteredUserOrganizationView>> organizationsByAccount,
            UserEntity member) {
        String accountId = member.getAccountId();
        CompanyEntity company = member.getCompany();
        if (accountId == null || accountId.isBlank() || company == null || company.getId() == null || company.getId().isBlank()) {
            return;
        }
        organizationsByAccount
                .computeIfAbsent(accountId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(company.getId(), new RegisteredUserOrganizationView(
                        company.getId(),
                        company.getName() == null ? "" : company.getName()));
    }

    private RegisteredUserView toView(
            UserAccountEntity account,
            List<RegisteredUserOrganizationView> organizations) {
        return new RegisteredUserView(
                account.getId(),
                account.getDisplayName() == null ? "" : account.getDisplayName(),
                account.getPrimaryMobile(),
                account.getEmail() == null ? "" : account.getEmail(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                organizations);
    }

    public record RegisteredUserPage(List<RegisteredUserView> items, long total, int page, int pageSize) {
    }

    public record RegisteredUserView(
            String id,
            String displayName,
            String mobile,
            String email,
            String status,
            Instant createdAt,
            Instant updatedAt,
            List<RegisteredUserOrganizationView> organizations
    ) {
    }

    public record RegisteredUserOrganizationView(String id, String name) {
    }
}
