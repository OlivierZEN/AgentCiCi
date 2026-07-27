package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountLoginIdentifierRepository accountLoginIdentifierRepository;
    private final KeycloakIdentityProvisioningService keycloakIdentityProvisioningService;

    public AdminUserService(UserRepository userRepository,
                            CompanyRepository companyRepository,
                            UserAccountRepository userAccountRepository,
                            AccountLoginIdentifierRepository accountLoginIdentifierRepository,
                            KeycloakIdentityProvisioningService keycloakIdentityProvisioningService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountLoginIdentifierRepository = accountLoginIdentifierRepository;
        this.keycloakIdentityProvisioningService = keycloakIdentityProvisioningService;
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
        KeycloakIdentityProvisioningService.ProvisionResult identity = keycloakIdentityProvisioningService
                .ensureHumanIdentity(account);
        UserEntity target = userRepository.findByCompany_IdAndAccount_Id(companyId, account.getId())
                .orElseGet(() -> {
                    var org = companyRepository.findById(companyId)
                            .orElseThrow(() -> new IllegalArgumentException("Company not found"));
                    return new UserEntity(org, account, normalizedRole);
                });
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
        row.put("nickname", u.getNickname() == null ? "" : u.getNickname());
        row.put("ccUsername", u.getCcUsername() == null ? "" : u.getCcUsername());
        row.put("ccSafetymark", u.getCcSafetymark() == null ? "" : u.getCcSafetymark());
        row.put("avatarBase64", u.getAvatarBase64() == null ? "" : u.getAvatarBase64());
        row.put("createdAt", u.getCreatedAt().toString());
        return row;
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
        // public_id is generated by PostgreSQL, so reload after flush before it is used as
        // the Keycloak username. This makes the unique human identifier immutable end-to-end.
        return userAccountRepository.findById(persistedAccount.getId()).orElseThrow();
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
