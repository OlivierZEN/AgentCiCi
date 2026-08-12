package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountExternalIdentityRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final String IDENTITY_RECONCILIATION_EVENT = "company_member.identity_reconciled";
    private static final String IDENTITY_ACTIVATION_SYNC_EVENT = "company_member.identity_activation_synced";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountLoginIdentifierRepository accountLoginIdentifierRepository;
    private final AccountExternalIdentityRepository accountExternalIdentityRepository;
    private final KeycloakIdentityProvisioningService keycloakIdentityProvisioningService;
    private final PlatformAuditService auditService;
    private final EntityManager entityManager;

    public AdminUserService(UserRepository userRepository,
                            CompanyRepository companyRepository,
                            UserAccountRepository userAccountRepository,
                            AccountLoginIdentifierRepository accountLoginIdentifierRepository,
                            AccountExternalIdentityRepository accountExternalIdentityRepository,
                            KeycloakIdentityProvisioningService keycloakIdentityProvisioningService,
                            PlatformAuditService auditService,
                            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountLoginIdentifierRepository = accountLoginIdentifierRepository;
        this.accountExternalIdentityRepository = accountExternalIdentityRepository;
        this.keycloakIdentityProvisioningService = keycloakIdentityProvisioningService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    public List<Map<String, Object>> listUsers(String companyId) {
        return userRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> inviteMember(
            String companyId,
            String mobile,
            String email,
            String nickname,
            String roleCode) {
        String mobileValue = normalizeMobile(mobile);
        if (!mobileValue.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        String normalizedRole = normalizeMemberRole(roleCode);
        UserAccountEntity account = findOrCreateMobileAccount(mobileValue, email, nickname);
        UserEntity target = userRepository.findByCompany_IdAndAccount_Id(companyId, account.getId()).orElse(null);
        // A suspension is an explicit governance decision. Re-inviting must not
        // silently restore it or send a credential-reset message; the existing
        // restore endpoint remains the deliberate recovery path.
        if (target != null && UserEntity.STATUS_SUSPENDED.equals(target.getMemberStatus())) {
            return toRow(target);
        }
        KeycloakIdentityProvisioningService.ProvisionResult identity = keycloakIdentityProvisioningService
                .ensureHumanIdentity(account);
        if (target == null) {
            var org = companyRepository.findById(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            target = new UserEntity(org, account, normalizedRole);
        }
        if (!RoleCodes.OWNER.equals(target.getRoleCode())) {
            target.setRoleCode(normalizedRole);
        }
        target.setMemberStatus(identity.activationRequired()
                ? UserEntity.STATUS_PENDING_ACTIVATION
                : UserEntity.STATUS_ACTIVE);
        target.setNickname(trimOrNull(nickname));
        userRepository.save(target);
        return toRow(target);
    }

    /**
     * Sends a fresh Keycloak verification/password-setup message for a member
     * that is still pending activation.  This intentionally does not reuse
     * {@link #inviteMember(String, String, String, String, String)} because an
     * invitation edit may otherwise change the member's role or profile.
     */
    @Transactional
    public Map<String, Object> resendActivationEmail(
            String companyId,
            String userId,
            String actorUserId,
            String actorRole) {
        UserEntity target = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在或不属于当前组织"));
        if (!UserEntity.STATUS_PENDING_ACTIVATION.equals(target.getMemberStatus())) {
            throw new IllegalStateException("仅可向待激活成员重发初始化邮件");
        }
        KeycloakIdentityProvisioningService.ProvisionResult identity = keycloakIdentityProvisioningService
                .resendHumanActivation(target.getAccount());
        if (!identity.activationRequired()) {
            target.setMemberStatus(UserEntity.STATUS_ACTIVE);
            userRepository.saveAndFlush(target);
            auditService.log(
                    companyId,
                    actorUserId,
                    actorRole,
                    IDENTITY_ACTIVATION_SYNC_EVENT,
                    "company_member_identity",
                    target.getId(),
                    "source=keycloak_status_check");
        }
        return toRow(target);
    }

    /**
     * Repairs an ACTIVE company member whose local unified-identity binding is
     * missing. The operation deliberately preserves role and profile data and
     * moves the membership to PENDING_ACTIVATION only when Keycloak still
     * requires the member to verify email or establish a password.
     */
    @Transactional
    public Map<String, Object> reconcileIdentity(
            String companyId,
            String userId,
            String confirmMobile,
            String idempotencyKey,
            String actorUserId,
            String actorRole) {
        UserEntity target = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在或不属于当前组织"));
        if (!normalizeMobile(confirmMobile).equals(normalizeMobile(target.getMobile()))) {
            throw new IllegalArgumentException("手机号确认不匹配");
        }
        String normalizedIdempotencyKey = trimOrNull(idempotencyKey);
        if (normalizedIdempotencyKey == null) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        String idempotencyFragment = "idempotencyKey=" + normalizedIdempotencyKey + ";";
        if (auditService.hasEventDetail(companyId, IDENTITY_RECONCILIATION_EVENT, target.getId(), idempotencyFragment)) {
            return toRow(target);
        }
        if (!UserEntity.STATUS_ACTIVE.equals(target.getMemberStatus())) {
            throw new IllegalStateException("仅可修复状态为有效且统一身份缺失的成员");
        }
        if (accountExternalIdentityRepository.findByAccount_Id(target.getAccountId()).isPresent()) {
            throw new IllegalStateException("该成员已绑定统一身份，无需修复");
        }

        KeycloakIdentityProvisioningService.ProvisionResult identity = keycloakIdentityProvisioningService
                .ensureHumanIdentity(target.getAccount());
        if (identity.activationRequired()) {
            target.setMemberStatus(UserEntity.STATUS_PENDING_ACTIVATION);
        }
        userRepository.saveAndFlush(target);
        auditService.log(
                companyId,
                actorUserId,
                actorRole,
                IDENTITY_RECONCILIATION_EVENT,
                "company_member_identity",
                target.getId(),
                idempotencyFragment + "activationRequired=" + identity.activationRequired());
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> updateRole(String companyId, String actorUserId, String targetUserId, String newRoleCode) {
        String role = normalizeMemberRole(newRoleCode);
        UserEntity target = userRepository.findByIdAndCompany_Id(targetUserId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (RoleCodes.OWNER.equals(target.getRoleCode())) {
            throw new ForbiddenException("Owner 角色请通过所有权转让处理");
        }
        if (targetUserId.equals(actorUserId)
                && RoleCodes.ORG_USER.equals(role)
                && RoleCodes.ORG_ADMIN.equals(target.getRoleCode())) {
            long adminCount = userRepository.findByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                    .filter(u -> RoleCodes.ORG_ADMIN.equals(u.getRoleCode()))
                    .filter(u -> UserEntity.STATUS_ACTIVE.equals(u.getMemberStatus()))
                    .count();
            if (adminCount <= 1) {
                throw new ForbiddenException("不能移除唯一的管理员");
            }
        }
        target.setRoleCode(role);
        userRepository.save(target);
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> suspendMember(String companyId, String actorUserId, String targetUserId) {
        UserEntity target = userRepository.findByIdAndCompany_Id(targetUserId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.getId().equals(actorUserId)) {
            throw new ForbiddenException("不能停用当前登录成员");
        }
        assertNotLastActiveOwner(companyId, target);
        target.setMemberStatus(UserEntity.STATUS_SUSPENDED);
        userRepository.save(target);
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> restoreMember(String companyId, String targetUserId) {
        UserEntity target = userRepository.findByIdAndCompany_Id(targetUserId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setMemberStatus(UserEntity.STATUS_ACTIVE);
        userRepository.save(target);
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> transferOwner(String companyId, String actorUserId, String targetUserId) {
        UserEntity actor = userRepository.findByIdAndCompany_Id(actorUserId, companyId)
                .orElseThrow(() -> new ForbiddenException("需要 Owner 权限"));
        if (!RoleCodes.OWNER.equals(actor.getRoleCode()) || !UserEntity.STATUS_ACTIVE.equals(actor.getMemberStatus())) {
            throw new ForbiddenException("需要 Owner 权限");
        }
        UserEntity target = userRepository.findByIdAndCompany_Id(targetUserId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!UserEntity.STATUS_ACTIVE.equals(target.getMemberStatus())) {
            throw new ForbiddenException("只能转让给有效成员");
        }
        if (actor.getId().equals(target.getId())) {
            return toRow(actor);
        }
        target.setRoleCode(RoleCodes.OWNER);
        actor.setRoleCode(RoleCodes.ORG_ADMIN);
        userRepository.save(actor);
        userRepository.save(target);
        return toRow(target);
    }

    @Transactional
    public Map<String, Object> updateProfile(
            String companyId,
            String userId,
            String mobile,
            String nickname,
            String ccUsername,
            String ccSafetymark,
            String avatarBase64) {
        UserEntity target = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String mobileValue = trimOrNull(mobile);
        if (mobileValue != null && !mobileValue.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (mobileValue != null) {
            updateAccountMobile(target, mobileValue);
        }
        String ccUsernameValue = trimOrNull(ccUsername);
        if (ccUsernameValue != null) {
            UserEntity existing = userRepository.findByCcUsername(ccUsernameValue).orElse(null);
            if (existing != null && !existing.getId().equals(target.getId())) {
                throw new IllegalArgumentException("CloudCC用户名已被其他用户绑定");
            }
        }
        target.setNickname(trimOrNull(nickname));
        target.setCcUsername(ccUsernameValue);
        target.setCcSafetymark(trimOrNull(ccSafetymark));
        target.setAvatarBase64(AvatarDataUrlValidator.normalizeNullableDataUrl(avatarBase64, "avatarBase64"));
        userRepository.save(target);
        return toRow(target);
    }

    private void updateAccountMobile(UserEntity target, String mobileValue) {
        userAccountRepository.findByPrimaryMobile(mobileValue)
                .filter(account -> !account.getId().equals(target.getAccountId()))
                .ifPresent(account -> {
                    throw new IllegalArgumentException("该手机号已被其他账号使用");
                });
        accountLoginIdentifierRepository
                .findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileValue,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .filter(identifier -> !identifier.getAccount().getId().equals(target.getAccountId()))
                .ifPresent(identifier -> {
                    throw new IllegalArgumentException("该手机号已被其他账号使用");
                });
        target.getAccount().setPrimaryMobile(mobileValue);
        userAccountRepository.save(target.getAccount());
        AccountLoginIdentifierEntity identifier = accountLoginIdentifierRepository
                .findByAccount_IdAndIdentifierTypeAndStatus(
                        target.getAccountId(),
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .orElseGet(() -> new AccountLoginIdentifierEntity(
                        target.getAccount(),
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileValue,
                        mobileValue));
        identifier.updateMobileValue(mobileValue, mobileValue);
        accountLoginIdentifierRepository.save(identifier);
    }

    private Map<String, Object> toRow(UserEntity u) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("memberId", u.getId());
        row.put("accountId", u.getAccountId());
        row.put("mobile", u.getMobile());
        row.put("email", u.getAccount() == null || u.getAccount().getEmail() == null ? "" : u.getAccount().getEmail());
        row.put("roleCode", u.getRoleCode());
        row.put("memberStatus", u.getMemberStatus());
        row.put("identityState", identityState(u));
        row.put("nickname", u.getNickname() == null ? "" : u.getNickname());
        row.put("ccUsername", u.getCcUsername() == null ? "" : u.getCcUsername());
        row.put("ccSafetymark", u.getCcSafetymark() == null ? "" : u.getCcSafetymark());
        row.put("avatarBase64", u.getAvatarBase64() == null ? "" : u.getAvatarBase64());
        row.put("createdAt", u.getCreatedAt().toString());
        return row;
    }

    private String identityState(UserEntity user) {
        if (UserEntity.STATUS_SUSPENDED.equals(user.getMemberStatus())) {
            return "BLOCKED";
        }
        if (accountExternalIdentityRepository.findByAccount_Id(user.getAccountId()).isEmpty()) {
            return "MISSING";
        }
        if (UserEntity.STATUS_PENDING_ACTIVATION.equals(user.getMemberStatus())) {
            return "PENDING_ACTIVATION";
        }
        return "ACTIVE";
    }

    private UserAccountEntity findOrCreateMobileAccount(String mobileValue, String email, String nickname) {
        UserAccountEntity account = accountLoginIdentifierRepository
                .findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileValue,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> userAccountRepository.findByPrimaryMobile(mobileValue))
                .orElseGet(() -> userAccountRepository.saveAndFlush(new UserAccountEntity(mobileValue)));
        String emailValue = trimOrNull(email);
        if (emailValue != null && !emailValue.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (emailValue != null) {
            String accountId = account.getId();
            userAccountRepository.findByEmailIgnoreCase(emailValue)
                    .filter(existing -> !existing.getId().equals(accountId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("该邮箱已被其他全局账号使用");
                    });
            account.setEmail(emailValue);
        }
        if (account.getDisplayName() == null && trimOrNull(nickname) != null) {
            account.setDisplayName(trimOrNull(nickname));
        }
        UserAccountEntity persistedAccount = userAccountRepository.saveAndFlush(account);
        // public_id is assigned by a PostgreSQL trigger. A repository lookup in this
        // transaction would return the same stale first-level-cache instance, so refresh
        // the managed entity before Keycloak provisioning consumes the generated value.
        entityManager.refresh(persistedAccount);
        accountLoginIdentifierRepository
                .findByAccount_IdAndIdentifierTypeAndStatus(
                        persistedAccount.getId(),
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .orElseGet(() -> accountLoginIdentifierRepository.save(new AccountLoginIdentifierEntity(
                        persistedAccount,
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileValue,
                        mobileValue)));
        return persistedAccount;
    }

    private void assertNotLastActiveOwner(String companyId, UserEntity target) {
        if (!RoleCodes.OWNER.equals(target.getRoleCode()) || !UserEntity.STATUS_ACTIVE.equals(target.getMemberStatus())) {
            return;
        }
        long ownerCount = userRepository.countByCompany_IdAndRoleCodeAndMemberStatus(
                companyId,
                RoleCodes.OWNER,
                UserEntity.STATUS_ACTIVE);
        if (ownerCount <= 1) {
            throw new ForbiddenException("不能停用唯一的 Owner");
        }
    }

    private String normalizeMemberRole(String roleCode) {
        String role = roleCode == null ? "" : roleCode.trim();
        if (!RoleCodes.ORG_ADMIN.equals(role) && !RoleCodes.ORG_USER.equals(role)) {
            throw new IllegalArgumentException("Invalid role code");
        }
        return role;
    }

    private String normalizeMobile(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
