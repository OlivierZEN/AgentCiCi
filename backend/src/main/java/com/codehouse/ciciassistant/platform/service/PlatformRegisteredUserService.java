package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformRegisteredUserService {

    private final UserAccountRepository userAccountRepository;

    public PlatformRegisteredUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public RegisteredUserPage listRegisteredUsers(String query, int page, int pageSize) {
        String keyword = query == null ? "" : query.trim();
        Page<UserAccountEntity> result = userAccountRepository.searchRegisteredAccounts(
                keyword,
                PageRequest.of(page, pageSize));
        List<RegisteredUserView> users = result.getContent().stream()
                .map(this::toView)
                .toList();
        return new RegisteredUserPage(users, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private RegisteredUserView toView(UserAccountEntity account) {
        return new RegisteredUserView(
                account.getId(),
                account.getDisplayName() == null ? "" : account.getDisplayName(),
                account.getPrimaryMobile(),
                account.getEmail() == null ? "" : account.getEmail(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
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
            Instant updatedAt
    ) {
    }
}
