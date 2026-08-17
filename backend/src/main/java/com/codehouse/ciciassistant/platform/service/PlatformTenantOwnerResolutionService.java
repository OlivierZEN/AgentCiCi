package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.KeycloakIdentityProvisioningService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantOwnerResolutionService {

    private final UserAccountRepository accountRepository;
    private final AccountLoginIdentifierRepository identifierRepository;
    private final AccountExternalIdentityRepository externalIdentityRepository;
    private final UserRepository userRepository;
    private final KeycloakIdentityProvisioningService identityProvisioningService;

    public PlatformTenantOwnerResolutionService(UserAccountRepository accountRepository,
                                                AccountLoginIdentifierRepository identifierRepository,
                                                AccountExternalIdentityRepository externalIdentityRepository,
                                                UserRepository userRepository,
                                                KeycloakIdentityProvisioningService identityProvisioningService) {
        this.accountRepository = accountRepository;
        this.identifierRepository = identifierRepository;
        this.externalIdentityRepository = externalIdentityRepository;
        this.userRepository = userRepository;
        this.identityProvisioningService = identityProvisioningService;
    }

    @Transactional(readOnly = true)
    public OwnerResolutionView resolve(OwnerResolutionCommand command) {
        return toView(resolveOwner(command));
    }

    @Transactional(readOnly = true)
    public ResolvedOwner resolveOwner(OwnerResolutionCommand command) {
        String mobile = normalizeMobile(command.ownerMobile());
        String email = normalizeEmail(command.ownerEmail());
        String publicId = normalizePublicId(command.ownerPublicId());
        if (mobile == null && email == null && publicId == null) {
            throw new IllegalArgumentException("请填写手机号、邮箱或用户公共编号");
        }

        Map<String, Match> matches = new LinkedHashMap<>();
        findMobile(mobile).ifPresent(account -> matches.put(account.getId(), new Match(account, "MOBILE")));
        findEmail(email).ifPresent(account -> mergeMatch(matches, account, "EMAIL"));
        findPublicId(publicId).ifPresent(account -> mergeMatch(matches, account, "PUBLIC_ID"));

        if (matches.size() > 1) {
            return new ResolvedOwner(
                    Resolution.IDENTIFIER_CONFLICT,
                    null,
                    matches.values().stream().map(Match::basis).distinct().toList(),
                    "手机号、邮箱或公共编号属于不同账号，请修正输入或重新选择已有用户。"
            );
        }
        if (matches.isEmpty()) {
            if (publicId != null) {
                return new ResolvedOwner(Resolution.NOT_FOUND, null, List.of(), "未找到该公共编号对应的用户。");
            }
            return new ResolvedOwner(Resolution.NEW_ACCOUNT, null, List.of(), "未发现已注册用户，可以创建新的统一账号。");
        }

        Match match = matches.values().iterator().next();
        UserAccountEntity account = match.account();
        if (!UserAccountEntity.STATUS_ACTIVE.equals(account.getStatus())) {
            return new ResolvedOwner(Resolution.ACCOUNT_BLOCKED, account, match.bases(), "该全局账号当前不可用，请先完成账号恢复。");
        }
        return new ResolvedOwner(
                Resolution.EXISTING_ACCOUNT,
                account,
                match.bases(),
                "检测到已注册用户，可以直接复用为新租户 Owner。"
        );
    }

    private OwnerResolutionView toView(ResolvedOwner resolved) {
        UserAccountEntity account = resolved.account();
        if (account == null) {
            return new OwnerResolutionView(
                    resolved.resolution().name(),
                    resolved.resolution() == Resolution.NEW_ACCOUNT,
                    null,
                    null,
                    null,
                    null,
                    "NOT_APPLICABLE",
                    0,
                    resolved.matchBasis(),
                    identityProvisioningService.isEnabled(),
                    resolved.message()
            );
        }
        long activeTenantCount = userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_ACTIVE)
                .size();
        String identityStatus = identityStatus(account, activeTenantCount);
        return new OwnerResolutionView(
                resolved.resolution().name(),
                resolved.resolution() == Resolution.EXISTING_ACCOUNT,
                account.getPublicId(),
                displayName(account),
                maskMobile(account.getPrimaryMobile()),
                maskEmail(account.getEmail()),
                identityStatus,
                activeTenantCount,
                resolved.matchBasis(),
                identityProvisioningService.isEnabled(),
                resolved.message()
        );
    }

    private String identityStatus(UserAccountEntity account, long activeTenantCount) {
        boolean bound = externalIdentityRepository.findByAccount_Id(account.getId()).isPresent();
        if (!bound) {
            return "NEEDS_RECONCILIATION";
        }
        if (activeTenantCount > 0) {
            return "ACTIVE";
        }
        boolean pending = !userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_PENDING_ACTIVATION)
                .isEmpty();
        return pending ? "PENDING_ACTIVATION" : "BOUND";
    }

    private Optional<UserAccountEntity> findMobile(String mobile) {
        if (mobile == null) {
            return Optional.empty();
        }
        return identifierRepository.findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobile,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> accountRepository.findByPrimaryMobile(mobile));
    }

    private Optional<UserAccountEntity> findEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return identifierRepository.findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_EMAIL,
                        email,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> accountRepository.findByEmailIgnoreCase(email));
    }

    private Optional<UserAccountEntity> findPublicId(String publicId) {
        return publicId == null ? Optional.empty() : accountRepository.findByPublicIdIgnoreCase(publicId);
    }

    private void mergeMatch(Map<String, Match> matches, UserAccountEntity account, String basis) {
        matches.compute(account.getId(), (ignored, existing) -> {
            if (existing == null) {
                return new Match(account, basis);
            }
            return existing.withBasis(basis);
        });
    }

    private String normalizeMobile(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号必须是 11 位大陆手机号");
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePublicId(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("^U[0-9A-Z]{12}$")) {
            throw new IllegalArgumentException("用户公共编号格式不正确");
        }
        return normalized;
    }

    private String displayName(UserAccountEntity account) {
        String display = trim(account.getDisplayName());
        return display == null ? "未设置显示名称" : display;
    }

    private String maskMobile(String value) {
        String mobile = trim(value);
        if (mobile == null || mobile.length() < 7) {
            return "未设置";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String maskEmail(String value) {
        String email = trim(value);
        if (email == null || !email.contains("@")) {
            return "未设置";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.length() <= 1 ? "*" : local.substring(0, 1) + "***";
        return maskedLocal + "@" + domain;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public enum Resolution {
        NEW_ACCOUNT,
        EXISTING_ACCOUNT,
        IDENTIFIER_CONFLICT,
        ACCOUNT_BLOCKED,
        NOT_FOUND
    }

    public record OwnerResolutionCommand(String ownerMobile, String ownerEmail, String ownerPublicId) {
    }

    public record ResolvedOwner(Resolution resolution, UserAccountEntity account, List<String> matchBasis, String message) {
    }

    public record OwnerResolutionView(
            String resolution,
            boolean canProceed,
            String accountPublicId,
            String displayName,
            String maskedMobile,
            String maskedEmail,
            String identityStatus,
            long activeTenantCount,
            List<String> matchBasis,
            boolean unifiedIdentityEnabled,
            String message
    ) {
    }

    private record Match(UserAccountEntity account, List<String> bases) {
        private Match(UserAccountEntity account, String basis) {
            this(account, new ArrayList<>(List.of(basis)));
        }

        private Match withBasis(String basis) {
            List<String> next = new ArrayList<>(bases);
            if (!next.contains(basis)) {
                next.add(basis);
            }
            return new Match(account, next);
        }

        private String basis() {
            return String.join("+", bases);
        }
    }
}
