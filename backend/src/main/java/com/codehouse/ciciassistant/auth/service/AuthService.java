package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialEntity;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.AuthPasswordEntity;
import com.codehouse.ciciassistant.auth.domain.AuthPasswordRepository;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrgRepository orgRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountAuthCredentialRepository accountAuthCredentialRepository;
    private final AccountLoginIdentifierRepository accountLoginIdentifierRepository;
    private final UserRepository userRepository;
    private final AuthPasswordRepository authPasswordRepository;
    private final JwtService jwtService;
    private final Set<String> bootstrapAdminMobiles;
    private final Set<String> platformAdminMobiles;
    private final Set<String> platformOperatorMobiles;
    private final Set<String> platformSupportMobiles;
    private final Set<String> platformBillingMobiles;
    private final Set<String> platformAuditorMobiles;

    public AuthService(OrgRepository orgRepository,
                       UserAccountRepository userAccountRepository,
                       AccountAuthCredentialRepository accountAuthCredentialRepository,
                       AccountLoginIdentifierRepository accountLoginIdentifierRepository,
                       UserRepository userRepository,
                       AuthPasswordRepository authPasswordRepository,
                       JwtService jwtService,
                       @Value("${app.auth.bootstrap-admin-mobiles:}") String bootstrapAdminMobilesRaw,
                       @Value("${app.auth.platform-admin-mobiles:}") String platformAdminMobilesRaw,
                       @Value("${app.auth.platform-operator-mobiles:}") String platformOperatorMobilesRaw,
                       @Value("${app.auth.platform-support-mobiles:}") String platformSupportMobilesRaw,
                       @Value("${app.auth.platform-billing-mobiles:}") String platformBillingMobilesRaw,
                       @Value("${app.auth.platform-auditor-mobiles:}") String platformAuditorMobilesRaw) {
        this.orgRepository = orgRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountAuthCredentialRepository = accountAuthCredentialRepository;
        this.accountLoginIdentifierRepository = accountLoginIdentifierRepository;
        this.userRepository = userRepository;
        this.authPasswordRepository = authPasswordRepository;
        this.jwtService = jwtService;
        this.bootstrapAdminMobiles = parseMobileSet(bootstrapAdminMobilesRaw);
        this.platformAdminMobiles = parseMobileSet(platformAdminMobilesRaw);
        this.platformOperatorMobiles = parseMobileSet(platformOperatorMobilesRaw);
        this.platformSupportMobiles = parseMobileSet(platformSupportMobilesRaw);
        this.platformBillingMobiles = parseMobileSet(platformBillingMobilesRaw);
        this.platformAuditorMobiles = parseMobileSet(platformAuditorMobilesRaw);
    }

    private static Set<String> parseMobileSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public Map<String, Object> sendSmsCode(String orgId, String mobile) {
        throw new IllegalArgumentException("SMS verification login is disabled");
    }

    @Transactional
    public Map<String, Object> loginBySms(String orgId, String mobile, String code) {
        throw new IllegalArgumentException("SMS verification login is disabled");
    }

    @Transactional
    public Map<String, Object> loginByPassword(String orgId, String identifier, String password) {
        LoginIdentifier loginIdentifier = normalizeLoginIdentifier(identifier);
        if (loginIdentifier.value().isBlank()) {
            throw new UnauthorizedException("Invalid mobile or password");
        }
        UserAccountEntity existingAccount = findAccountByIdentifier(loginIdentifier).orElse(null);
        if (existingAccount != null) {
            verifyAccountPassword(existingAccount, password);
        } else {
            if (loginIdentifier.isEmail()) {
                throw new UnauthorizedException("Invalid account or password");
            }
            verifyFixedPassword(password);
        }
        if (orgId == null || orgId.isBlank()) {
            if (existingAccount == null) {
                throw new UnauthorizedException("Invalid mobile or password");
            }
            return loginWithoutOrganization(existingAccount);
        }
        OrgEntity org = requireOrg(orgId);
        return issueLogin(org, loginIdentifier, existingAccount);
    }

    @Transactional
    public Map<String, Object> register(String mobile, String password, String organizationName) {
        verifyFixedPassword(password);
        String mobileNorm = normalizeMobile(mobile);
        if (mobileNorm.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (userAccountRepository.findByPrimaryMobile(mobileNorm).isPresent()
                || accountLoginIdentifierRepository.findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileNorm,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE).isPresent()) {
            throw new IllegalArgumentException("该手机号已注册，请登录后创建或切换组织");
        }
        UserAccountEntity account = userAccountRepository.save(new UserAccountEntity(mobileNorm));
        accountLoginIdentifierRepository.save(new AccountLoginIdentifierEntity(
                account,
                AccountLoginIdentifierEntity.TYPE_MOBILE,
                mobileNorm,
                mobileNorm));
        OrgEntity org = createOrg(organizationName);
        UserEntity owner = userRepository.save(new UserEntity(org, account, RoleCodes.OWNER));
        return issueLoginForMember(owner);
    }

    public Map<String, Object> organizations(String currentOrgId, String currentUserId) {
        UserEntity current = userRepository.findByIdAndOrg_Id(currentUserId, currentOrgId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        List<Map<String, Object>> organizations = organizationRows(current.getAccountId(), currentOrgId);
        return Map.of(
                "accountId", current.getAccountId(),
                "currentOrgId", currentOrgId,
                "organizations", organizations
        );
    }

    @Transactional
    public Map<String, Object> switchOrganization(String currentUserId, String targetOrgId) {
        UserEntity current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        OrgEntity org = requireOrg(targetOrgId);
        UserEntity target = userRepository
                .findByOrg_IdAndAccount_IdAndMemberStatus(org.getId(), current.getAccountId(), UserEntity.STATUS_ACTIVE)
                .orElseThrow(() -> new ForbiddenException("当前账号不属于该组织"));
        return issueLoginForMember(target);
    }

    @Transactional
    public Map<String, Object> createOrganization(String currentUserId, String organizationName) {
        UserEntity current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        OrgEntity org = createOrg(organizationName);
        UserEntity owner = userRepository.save(new UserEntity(org, current.getAccount(), RoleCodes.OWNER));
        return issueLoginForMember(owner);
    }

    private Map<String, Object> loginWithoutOrganization(UserAccountEntity account) {
        List<UserEntity> members = userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getOrg().getStatus()))
                .toList();
        if (members.isEmpty()) {
            throw new UnauthorizedException("No active organization membership");
        }
        if (members.size() == 1) {
            return issueLoginForMember(members.get(0));
        }
        return Map.of(
                "requiresOrganizationSelection", true,
                "accountId", account.getId(),
                "organizations", members.stream().map(member -> organizationRow(member, "")).toList()
        );
    }

    private Map<String, Object> issueLogin(OrgEntity org, LoginIdentifier loginIdentifier, UserAccountEntity existingAccount) {
        String mobileNorm = loginIdentifier.isMobile() ? loginIdentifier.value() : "";
        if (loginIdentifier.isEmail()) {
            if (existingAccount == null) {
                throw new UnauthorizedException("Invalid account or password");
            }
            UserEntity user = userRepository.findByOrg_IdAndAccount_Id(org.getId(), existingAccount.getId())
                    .orElseThrow(() -> new UnauthorizedException("No active organization membership"));
            if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
                throw new UnauthorizedException("No active organization membership");
            }
            return issueLoginForMember(user);
        }
        boolean bootstrapAdmin = bootstrapAdminMobiles.contains(mobileNorm);
        String initialRole = bootstrapAdmin ? RoleCodes.ORG_ADMIN : RoleCodes.ORG_USER;
        UserAccountEntity account = existingAccount == null ? findOrCreateMobileAccount(mobileNorm) : ensureMobileIdentifier(existingAccount, mobileNorm);
        UserEntity user = userRepository.findByOrg_IdAndAccount_Id(org.getId(), account.getId())
                .orElseGet(() -> userRepository.save(new UserEntity(org, account, initialRole)));
        if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
            throw new UnauthorizedException("No active organization membership");
        }
        if (bootstrapAdmin && RoleCodes.ORG_USER.equals(user.getRoleCode())) {
            user.setRoleCode(RoleCodes.ORG_ADMIN);
            user = userRepository.save(user);
        }
        return issueLoginForMember(user);
    }

    private Map<String, Object> issueLoginForMember(UserEntity user) {
        List<String> roles = resolveRoles(user);
        String token = jwtService.issueToken(user, roles);
        return Map.of(
                "token", token,
                "orgId", user.getOrg().getId(),
                "orgName", user.getOrg().getName(),
                "userId", user.getId(),
                "memberId", user.getId(),
                "accountId", user.getAccountId(),
                "roles", roles,
                "issuedAt", Instant.now().toString()
        );
    }

    private UserAccountEntity findOrCreateMobileAccount(String mobileNorm) {
        UserAccountEntity account = findMobileAccount(mobileNorm)
                .orElseGet(() -> userAccountRepository.save(new UserAccountEntity(mobileNorm)));
        return ensureMobileIdentifier(account, mobileNorm);
    }

    private java.util.Optional<UserAccountEntity> findMobileAccount(String mobileNorm) {
        return accountLoginIdentifierRepository
                .findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_MOBILE,
                        mobileNorm,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> userAccountRepository.findByPrimaryMobile(mobileNorm));
    }

    private java.util.Optional<UserAccountEntity> findEmailAccount(String emailNorm) {
        return accountLoginIdentifierRepository
                .findByIdentifierTypeAndNormalizedValueAndStatus(
                        AccountLoginIdentifierEntity.TYPE_EMAIL,
                        emailNorm,
                        AccountLoginIdentifierEntity.STATUS_ACTIVE)
                .map(AccountLoginIdentifierEntity::getAccount)
                .or(() -> userAccountRepository.findByEmailIgnoreCase(emailNorm)
                        .map(account -> {
                            syncEmailIdentifier(account, account.getEmail());
                            return account;
                        }));
    }

    private java.util.Optional<UserAccountEntity> findAccountByIdentifier(LoginIdentifier identifier) {
        if (identifier.isEmail()) {
            return findEmailAccount(identifier.value());
        }
        return findMobileAccount(identifier.value());
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

    private void verifyFixedPassword(String password) {
        AuthPasswordEntity credential = authPasswordRepository.findById("default")
                .orElseThrow(() -> new UnauthorizedException("Password login is not initialized"));
        if (!"PBKDF2WithHmacSHA256".equals(credential.getAlgorithm())) {
            throw new UnauthorizedException("Unsupported password credential");
        }
        try {
            byte[] expected = Base64.getDecoder().decode(credential.getPasswordHash());
            KeySpec spec = new PBEKeySpec(
                    password == null ? new char[0] : password.toCharArray(),
                    credential.getSalt().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    credential.getIterations(),
                    expected.length * 8
            );
            byte[] actual = SecretKeyFactory.getInstance(credential.getAlgorithm()).generateSecret(spec).getEncoded();
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new UnauthorizedException("Invalid mobile or password");
            }
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Password verification failed");
        }
    }

    public Map<String, Object> currentUser(String orgId, String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.getOrg().getId().equals(orgId)) {
            throw new UnauthorizedException("Tenant mismatch");
        }
        if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
            throw new UnauthorizedException("No active organization membership");
        }
        UserAccountEntity account = user.getAccount();
        String displayName = account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? (user.getNickname() == null ? "" : user.getNickname())
                : account.getDisplayName();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orgId", orgId);
        row.put("orgName", user.getOrg().getName());
        row.put("userId", user.getId());
        row.put("memberId", user.getId());
        row.put("accountId", user.getAccountId());
        row.put("mobile", user.getMobile());
        row.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        row.put("firstName", account.getFirstName() == null ? "" : account.getFirstName());
        row.put("lastName", account.getLastName() == null ? "" : account.getLastName());
        row.put("displayName", displayName);
        row.put("email", account.getEmail() == null ? "" : account.getEmail());
        row.put("avatarBase64", user.getAvatarBase64() == null ? "" : user.getAvatarBase64());
        row.put("roles", resolveRoles(user));
        return row;
    }

    @Transactional
    public Map<String, Object> updateCurrentUserAvatar(String orgId, String userId, String avatarBase64) {
        UserEntity user = userRepository.findByIdAndOrg_Id(userId, orgId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        String normalizedAvatar = AvatarDataUrlValidator.normalizeNullableDataUrl(avatarBase64, "avatarBase64");
        user.setAvatarBase64(normalizedAvatar);
        userRepository.save(user);
        return currentUser(orgId, userId);
    }

    @Transactional
    public Map<String, Object> updateCurrentUserProfile(String orgId,
                                                        String userId,
                                                        String firstName,
                                                        String lastName,
                                                        String displayName,
                                                        String mobile,
                                                        String email) {
        UserEntity user = userRepository.findByIdAndOrg_Id(userId, orgId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        UserAccountEntity account = user.getAccount();
        String mobileValue = normalizeMobile(mobile);
        if (mobileValue.isBlank() || !mobileValue.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号必须是 11 位大陆手机号");
        }
        if (!mobileValue.equals(account.getPrimaryMobile())) {
            userAccountRepository.findByPrimaryMobile(mobileValue)
                    .filter(existing -> !existing.getId().equals(account.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("该手机号已被其他账号使用");
                    });
            account.setPrimaryMobile(mobileValue);
            ensureMobileIdentifier(account, mobileValue);
            accountLoginIdentifierRepository
                    .findByAccount_IdAndIdentifierTypeAndStatus(
                            account.getId(),
                            AccountLoginIdentifierEntity.TYPE_MOBILE,
                            AccountLoginIdentifierEntity.STATUS_ACTIVE)
                    .ifPresent(identifier -> identifier.updateMobileValue(mobileValue, mobileValue));
        }

        String emailValue = trimOrNull(email);
        if (emailValue != null && !emailValue.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        syncEmailIdentifier(account, emailValue);
        String displayValue = trimOrNull(displayName);
        if (displayValue == null) {
            displayValue = deriveDisplayName(firstName, lastName, mobileValue);
        }
        account.setFirstName(trimOrNull(firstName));
        account.setLastName(trimOrNull(lastName));
        account.setDisplayName(displayValue);
        account.setEmail(emailValue);
        user.setNickname(displayValue);
        userRepository.save(user);
        userAccountRepository.save(account);
        return currentUser(orgId, userId);
    }

    @Transactional
    public Map<String, Object> changeCurrentUserPassword(String orgId, String userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findByIdAndOrg_Id(userId, orgId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        verifyAccountPassword(user.getAccount(), currentPassword);
        String nextPassword = newPassword == null ? "" : newPassword.trim();
        if (nextPassword.length() < 8) {
            throw new IllegalArgumentException("新密码至少需要 8 位");
        }
        AccountAuthCredentialEntity credential = accountAuthCredentialRepository
                .findByAccount_IdAndCredentialTypeAndStatus(
                        user.getAccountId(),
                        AccountAuthCredentialEntity.TYPE_PASSWORD,
                        AccountAuthCredentialEntity.STATUS_ACTIVE)
                .orElseGet(() -> new AccountAuthCredentialEntity(user.getAccount()));
        PasswordHash hash = hashPassword(nextPassword);
        credential.replacePassword(hash.passwordHash(), hash.salt(), hash.iterations(), hash.algorithm());
        accountAuthCredentialRepository.save(credential);
        return Map.of("updated", true);
    }

    private List<String> resolveRoles(UserEntity user) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.add(user.getRoleCode());
        String mobile = user.getMobile() == null ? "" : user.getMobile().trim();
        if (platformAdminMobiles.contains(mobile)) {
            roles.add(RoleCodes.PLATFORM_ADMIN);
        }
        if (platformOperatorMobiles.contains(mobile)) {
            roles.add(RoleCodes.PLATFORM_OPERATOR);
        }
        if (platformSupportMobiles.contains(mobile)) {
            roles.add(RoleCodes.PLATFORM_SUPPORT);
        }
        if (platformBillingMobiles.contains(mobile)) {
            roles.add(RoleCodes.PLATFORM_BILLING);
        }
        if (platformAuditorMobiles.contains(mobile)) {
            roles.add(RoleCodes.PLATFORM_AUDITOR);
        }
        return List.copyOf(roles);
    }

    private OrgEntity requireOrg(String orgId) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException("Organization not found");
        }
        OrgEntity org = orgRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!"ACTIVE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Organization is disabled");
        }
        return org;
    }

    private OrgEntity createOrg(String organizationName) {
        String name = trimOrNull(organizationName);
        if (name == null) {
            throw new IllegalArgumentException("组织名称不能为空");
        }
        String id;
        do {
            id = "org-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (orgRepository.existsById(id));
        return orgRepository.save(new OrgEntity(id, name, "ACTIVE"));
    }

    private List<Map<String, Object>> organizationRows(String accountId, String currentOrgId) {
        return userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(accountId, UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getOrg().getStatus()))
                .map(member -> organizationRow(member, currentOrgId))
                .toList();
    }

    private Map<String, Object> organizationRow(UserEntity member, String currentOrgId) {
        return Map.of(
                "orgId", member.getOrg().getId(),
                "orgName", member.getOrg().getName(),
                "memberId", member.getId(),
                "roleCode", member.getRoleCode(),
                "current", member.getOrg().getId().equals(currentOrgId == null ? "" : currentOrgId)
        );
    }

    private String normalizeMobile(String mobile) {
        return mobile == null ? "" : mobile.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private LoginIdentifier normalizeLoginIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.contains("@")) {
            return new LoginIdentifier(AccountLoginIdentifierEntity.TYPE_EMAIL, normalizeEmail(value));
        }
        return new LoginIdentifier(AccountLoginIdentifierEntity.TYPE_MOBILE, normalizeMobile(value));
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
                    existing.updateValue(normalizedEmail, emailValue.trim());
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
                            emailValue.trim()));
                });
    }

    private void verifyAccountPassword(UserAccountEntity account, String password) {
        accountAuthCredentialRepository
                .findByAccount_IdAndCredentialTypeAndStatus(
                        account.getId(),
                        AccountAuthCredentialEntity.TYPE_PASSWORD,
                        AccountAuthCredentialEntity.STATUS_ACTIVE)
                .ifPresentOrElse(
                        credential -> verifyPasswordHash(
                                password,
                                credential.getPasswordHash(),
                                credential.getSalt(),
                                credential.getIterations(),
                                credential.getAlgorithm()),
                        () -> verifyFixedPassword(password));
    }

    private void verifyPasswordHash(String password, String passwordHash, String salt, int iterations, String algorithm) {
        if (!"PBKDF2WithHmacSHA256".equals(algorithm)) {
            throw new UnauthorizedException("Unsupported password credential");
        }
        try {
            byte[] expected = Base64.getDecoder().decode(passwordHash);
            KeySpec spec = new PBEKeySpec(
                    password == null ? new char[0] : password.toCharArray(),
                    salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    iterations,
                    expected.length * 8
            );
            byte[] actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new UnauthorizedException("Invalid mobile or password");
            }
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Password verification failed");
        }
    }

    private PasswordHash hashPassword(String password) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        int iterations = 120000;
        String algorithm = "PBKDF2WithHmacSHA256";
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), iterations, 256);
            String passwordHash = Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded());
            return new PasswordHash(passwordHash, salt, iterations, algorithm);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing failed", ex);
        }
    }

    private String deriveDisplayName(String firstName, String lastName, String mobile) {
        String joined = ((trimOrNull(lastName) == null ? "" : trimOrNull(lastName))
                + (trimOrNull(firstName) == null ? "" : trimOrNull(firstName))).trim();
        return joined.isBlank() ? mobile : joined;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private record PasswordHash(String passwordHash, String salt, int iterations, String algorithm) {
    }

    private record LoginIdentifier(String type, String value) {
        boolean isEmail() {
            return AccountLoginIdentifierEntity.TYPE_EMAIL.equals(type);
        }

        boolean isMobile() {
            return AccountLoginIdentifierEntity.TYPE_MOBILE.equals(type);
        }
    }
}
