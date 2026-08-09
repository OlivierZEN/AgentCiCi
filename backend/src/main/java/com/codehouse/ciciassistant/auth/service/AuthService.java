package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.ProductThemeCodes;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialEntity;
import com.codehouse.ciciassistant.auth.domain.AccountAuthCredentialRepository;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierEntity;
import com.codehouse.ciciassistant.auth.domain.AccountLoginIdentifierRepository;
import com.codehouse.ciciassistant.auth.domain.AuthPasswordEntity;
import com.codehouse.ciciassistant.auth.domain.AuthPasswordRepository;
import com.codehouse.ciciassistant.auth.domain.CompanyEntity;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountAuthCredentialRepository accountAuthCredentialRepository;
    private final AccountLoginIdentifierRepository accountLoginIdentifierRepository;
    private final UserRepository userRepository;
    private final AuthPasswordRepository authPasswordRepository;
    private final JwtService jwtService;
    private final CompanyProvisioningService companyProvisioningService;
    private final PasswordHashService passwordHashService;
    private final Set<String> bootstrapAdminMobiles;

    public AuthService(CompanyRepository companyRepository,
                       UserAccountRepository userAccountRepository,
                       AccountAuthCredentialRepository accountAuthCredentialRepository,
                       AccountLoginIdentifierRepository accountLoginIdentifierRepository,
                       UserRepository userRepository,
                       AuthPasswordRepository authPasswordRepository,
                       JwtService jwtService,
                       CompanyProvisioningService companyProvisioningService,
                       PasswordHashService passwordHashService,
                       @Value("${app.auth.bootstrap-admin-mobiles:}") String bootstrapAdminMobilesRaw) {
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountAuthCredentialRepository = accountAuthCredentialRepository;
        this.accountLoginIdentifierRepository = accountLoginIdentifierRepository;
        this.userRepository = userRepository;
        this.authPasswordRepository = authPasswordRepository;
        this.jwtService = jwtService;
        this.companyProvisioningService = companyProvisioningService;
        this.passwordHashService = passwordHashService;
        this.bootstrapAdminMobiles = parseMobileSet(bootstrapAdminMobilesRaw);
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

    public Map<String, Object> sendSmsCode(String companyId, String mobile) {
        throw new IllegalArgumentException("SMS verification login is disabled");
    }

    @Transactional
    public Map<String, Object> loginBySms(String companyId, String mobile, String code) {
        throw new IllegalArgumentException("SMS verification login is disabled");
    }

    @Transactional
    public Map<String, Object> loginByPassword(String companyId, String identifier, String password) {
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
        if (companyId == null || companyId.isBlank()) {
            if (existingAccount == null) {
                throw new UnauthorizedException("Invalid mobile or password");
            }
            return loginWithoutCompany(existingAccount);
        }
        CompanyEntity org = requireCompany(companyId);
        return issueLogin(org, loginIdentifier, existingAccount);
    }

    @Transactional
    public Map<String, Object> register(String mobile, String password, String companyName) {
        verifyFixedPassword(password);
        String mobileNorm = normalizeMobile(mobile);
        if (mobileNorm.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (companyProvisioningService.findMobileAccount(mobileNorm).isPresent()) {
            throw new IllegalArgumentException("该手机号已注册，请登录后创建或切换组织");
        }
        UserAccountEntity account = companyProvisioningService.createMobileAccount(mobileNorm, null, null);
        CompanyEntity org = companyProvisioningService.createCompany(companyName);
        UserEntity owner = companyProvisioningService.createOwnerMembership(org, account, null);
        return issueLoginForMember(owner);
    }

    public Map<String, Object> companies(String currentCompanyId, String currentUserId) {
        UserEntity current = userRepository.findByIdAndCompany_Id(currentUserId, currentCompanyId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        List<Map<String, Object>> companies = companyRows(current.getAccountId(), currentCompanyId);
        return Map.of(
                "accountId", current.getAccountId(),
                "currentCompanyId", currentCompanyId,
                "companies", companies
        );
    }

    @Transactional
    public Map<String, Object> switchCompany(String currentUserId, String targetCompanyId) {
        UserEntity current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        CompanyEntity org = requireCompany(targetCompanyId);
        UserEntity target = userRepository
                .findByCompany_IdAndAccount_IdAndMemberStatus(org.getId(), current.getAccountId(), UserEntity.STATUS_ACTIVE)
                .orElseThrow(() -> new ForbiddenException("当前账号不属于该组织"));
        return issueLoginForMember(target);
    }

    @Transactional
    public Map<String, Object> createCompany(String currentUserId, String companyName) {
        UserEntity current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        CompanyEntity org = companyProvisioningService.createCompany(companyName);
        UserEntity owner = companyProvisioningService.createOwnerMembership(org, current.getAccount(), null);
        return issueLoginForMember(owner);
    }

    @Transactional
    public Map<String, Object> loginAsMember(String companyId, String userId) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new UnauthorizedException("No active company membership"));
        if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
            throw new UnauthorizedException("No active company membership");
        }
        return issueLoginForMember(user);
    }

    @Transactional
    public Map<String, Object> loginByExternalIdentityAccount(String accountId, String idpSessionId) {
        if (idpSessionId == null || idpSessionId.isBlank()) {
            throw new UnauthorizedException("Missing identity provider session");
        }
        List<UserEntity> members = userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(accountId, UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getCompany().getStatus()))
                .toList();
        if (members.isEmpty()) {
            // A member is only promoted after the person completed a successful
            // OIDC login. This is the activation boundary for an email invite.
            List<UserEntity> pendingMembers = userRepository
                    .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(accountId, UserEntity.STATUS_PENDING_ACTIVATION)
                    .stream()
                    .filter(member -> "ACTIVE".equalsIgnoreCase(member.getCompany().getStatus()))
                    .toList();
            if (!pendingMembers.isEmpty()) {
                pendingMembers.forEach(member -> member.setMemberStatus(UserEntity.STATUS_ACTIVE));
                userRepository.saveAll(pendingMembers);
                members = pendingMembers;
            }
        }
        if (members.isEmpty()) {
            throw new UnauthorizedException("No active company membership");
        }
        // A multi-company user can immediately switch using the existing protected endpoint. The
        // initial company is deterministic and remains constrained to an active membership.
        return issueLoginForMember(members.get(0), Map.of("idp_session_id", idpSessionId));
    }

    private Map<String, Object> loginWithoutCompany(UserAccountEntity account) {
        List<UserEntity> members = userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(account.getId(), UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getCompany().getStatus()))
                .toList();
        if (members.isEmpty()) {
            throw new UnauthorizedException("No active company membership");
        }
        if (members.size() == 1) {
            return issueLoginForMember(members.get(0));
        }
        return Map.of(
                "requiresCompanySelection", true,
                "accountId", account.getId(),
                "companies", members.stream().map(member -> companyRow(member, "")).toList()
        );
    }

    private Map<String, Object> issueLogin(CompanyEntity org, LoginIdentifier loginIdentifier, UserAccountEntity existingAccount) {
        String mobileNorm = loginIdentifier.isMobile() ? loginIdentifier.value() : "";
        if (loginIdentifier.isEmail()) {
            if (existingAccount == null) {
                throw new UnauthorizedException("Invalid account or password");
            }
            UserEntity user = userRepository.findByCompany_IdAndAccount_Id(org.getId(), existingAccount.getId())
                    .orElseThrow(() -> new UnauthorizedException("No active company membership"));
            if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
                throw new UnauthorizedException("No active company membership");
            }
            return issueLoginForMember(user);
        }
        boolean bootstrapAdmin = bootstrapAdminMobiles.contains(mobileNorm);
        String initialRole = bootstrapAdmin ? RoleCodes.ORG_ADMIN : RoleCodes.ORG_USER;
        UserAccountEntity account = existingAccount == null ? findOrCreateMobileAccount(mobileNorm) : ensureMobileIdentifier(existingAccount, mobileNorm);
        UserEntity user = userRepository.findByCompany_IdAndAccount_Id(org.getId(), account.getId())
                .orElseGet(() -> userRepository.save(new UserEntity(org, account, initialRole)));
        if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
            throw new UnauthorizedException("No active company membership");
        }
        if (bootstrapAdmin && RoleCodes.ORG_USER.equals(user.getRoleCode())) {
            user.setRoleCode(RoleCodes.ORG_ADMIN);
            user = userRepository.save(user);
        }
        return issueLoginForMember(user);
    }

    private Map<String, Object> issueLoginForMember(UserEntity user) {
        return issueLoginForMember(user, Map.of());
    }

    private Map<String, Object> issueLoginForMember(UserEntity user, Map<String, Object> additionalClaims) {
        List<String> roles = resolveRoles(user);
        String token = jwtService.issueToken(user, roles, additionalClaims);
        return Map.of(
                "token", token,
                "companyId", user.getCompany().getId(),
                "companyName", user.getCompany().getName(),
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

    public Map<String, Object> currentUser(String companyId, String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.getCompany().getId().equals(companyId)) {
            throw new UnauthorizedException("Tenant mismatch");
        }
        if (!UserEntity.STATUS_ACTIVE.equals(user.getMemberStatus())) {
            throw new UnauthorizedException("No active company membership");
        }
        UserAccountEntity account = user.getAccount();
        String displayName = account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? (user.getNickname() == null ? "" : user.getNickname())
                : account.getDisplayName();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("companyId", companyId);
        row.put("companyName", user.getCompany().getName());
        row.put("userId", user.getId());
        row.put("memberId", user.getId());
        row.put("accountId", user.getAccountId());
        row.put("mobile", user.getMobile());
        row.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        row.put("firstName", account.getFirstName() == null ? "" : account.getFirstName());
        row.put("lastName", account.getLastName() == null ? "" : account.getLastName());
        row.put("displayName", displayName);
        row.put("email", account.getEmail() == null ? "" : account.getEmail());
        row.put("themeCode", ProductThemeCodes.normalizeStored(account.getThemeCode()));
        row.put("avatarBase64", user.getAvatarBase64() == null ? "" : user.getAvatarBase64());
        row.put("roles", resolveRoles(user));
        return row;
    }

    @Transactional(readOnly = true)
    public OfficialAccessTokenService.IssuedToken issueSematticeOfficialAccess(String companyId,
                                                                                 String userId,
                                                                                 OfficialAccessTokenService tokenService) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return tokenService.issueForSematticeConsole(user);
    }

    @Transactional(readOnly = true)
    public OfficialAccessTokenService.IssuedToken issueSematticeOfficialAccessForRuntime(String companyId,
                                                                                            String userId,
                                                                                            OfficialAccessTokenService tokenService) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return tokenService.issueForSemattice(user);
    }

    @Transactional
    public Map<String, Object> updateCurrentUserAvatar(String companyId, String userId, String avatarBase64) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        String normalizedAvatar = AvatarDataUrlValidator.normalizeNullableDataUrl(avatarBase64, "avatarBase64");
        user.setAvatarBase64(normalizedAvatar);
        userRepository.save(user);
        return currentUser(companyId, userId);
    }

    @Transactional
    public Map<String, Object> updateCurrentUserTheme(String companyId, String userId, String themeCode) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        UserAccountEntity account = user.getAccount();
        account.setThemeCode(ProductThemeCodes.requireAllowed(themeCode));
        userAccountRepository.save(account);
        return currentUser(companyId, userId);
    }

    @Transactional
    public Map<String, Object> updateCurrentUserProfile(String companyId,
                                                        String userId,
                                                        String firstName,
                                                        String lastName,
                                                        String displayName,
                                                        String mobile,
                                                        String email) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
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
        return currentUser(companyId, userId);
    }

    @Transactional
    public Map<String, Object> changeCurrentUserPassword(String companyId, String userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findByIdAndCompany_Id(userId, companyId)
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
        PasswordHashService.PasswordHash hash = passwordHashService.hash(nextPassword);
        credential.replacePassword(hash.passwordHash(), hash.salt(), hash.iterations(), hash.algorithm());
        accountAuthCredentialRepository.save(credential);
        return Map.of("updated", true);
    }

    private List<String> resolveRoles(UserEntity user) {
        return List.of(user.getRoleCode());
    }

    private CompanyEntity requireCompany(String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("Company not found");
        }
        CompanyEntity org = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (!"ACTIVE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Company is disabled");
        }
        return org;
    }

    private List<Map<String, Object>> companyRows(String accountId, String currentCompanyId) {
        return userRepository
                .findByAccount_IdAndMemberStatusOrderByCreatedAtDesc(accountId, UserEntity.STATUS_ACTIVE)
                .stream()
                .filter(member -> "ACTIVE".equalsIgnoreCase(member.getCompany().getStatus()))
                .map(member -> companyRow(member, currentCompanyId))
                .toList();
    }

    private Map<String, Object> companyRow(UserEntity member, String currentCompanyId) {
        return Map.of(
                "companyId", member.getCompany().getId(),
                "companyName", member.getCompany().getName(),
                "memberId", member.getId(),
                "roleCode", member.getRoleCode(),
                "current", member.getCompany().getId().equals(currentCompanyId == null ? "" : currentCompanyId)
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

    private record LoginIdentifier(String type, String value) {
        boolean isEmail() {
            return AccountLoginIdentifierEntity.TYPE_EMAIL.equals(type);
        }

        boolean isMobile() {
            return AccountLoginIdentifierEntity.TYPE_MOBILE.equals(type);
        }
    }
}
