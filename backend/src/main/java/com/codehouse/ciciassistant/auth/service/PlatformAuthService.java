package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.ProductThemeCodes;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountCredentialEntity;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountCredentialRepository;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountEntity;
import com.codehouse.ciciassistant.auth.domain.PlatformAccountRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuthService {

    private static final String INVALID_ACCOUNT_OR_PASSWORD = "Invalid account or password";

    private final PlatformAccountRepository platformAccountRepository;
    private final PlatformAccountCredentialRepository platformAccountCredentialRepository;
    private final PasswordCredentialService passwordCredentialService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public PlatformAuthService(PlatformAccountRepository platformAccountRepository,
                               PlatformAccountCredentialRepository platformAccountCredentialRepository,
                               PasswordCredentialService passwordCredentialService,
                               JwtService jwtService,
                               ObjectMapper objectMapper) {
        this.platformAccountRepository = platformAccountRepository;
        this.platformAccountCredentialRepository = platformAccountCredentialRepository;
        this.passwordCredentialService = passwordCredentialService;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loginByPassword(String identifier, String password) {
        LoginIdentifier loginIdentifier = normalizeLoginIdentifier(identifier);
        if (loginIdentifier.value().isBlank()) {
            throw new UnauthorizedException(INVALID_ACCOUNT_OR_PASSWORD);
        }
        PlatformAccountEntity account = findPlatformAccount(loginIdentifier);
        verifyPassword(account, password);
        return issueLogin(account);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentPlatformAccount(String platformAccountId) {
        PlatformAccountEntity account = platformAccountRepository.findById(platformAccountId)
                .filter(this::isActive)
                .orElseThrow(() -> new UnauthorizedException("Platform account not found"));
        return platformAccountPayload(account, resolveRoles(account));
    }

    @Transactional
    public Map<String, Object> updateCurrentPlatformTheme(String platformAccountId, String themeCode) {
        PlatformAccountEntity account = platformAccountRepository.findById(platformAccountId)
                .filter(this::isActive)
                .orElseThrow(() -> new UnauthorizedException("Platform account not found"));
        account.setThemeCode(ProductThemeCodes.requireAllowed(themeCode));
        platformAccountRepository.save(account);
        return platformAccountPayload(account, resolveRoles(account));
    }

    private PlatformAccountEntity findPlatformAccount(LoginIdentifier loginIdentifier) {
        return (loginIdentifier.isEmail()
                ? platformAccountRepository.findByEmailIgnoreCase(loginIdentifier.value())
                : platformAccountRepository.findByMobile(loginIdentifier.value()))
                .filter(this::isActive)
                .orElseThrow(() -> new UnauthorizedException(INVALID_ACCOUNT_OR_PASSWORD));
    }

    private boolean isActive(PlatformAccountEntity account) {
        return PlatformAccountEntity.STATUS_ACTIVE.equalsIgnoreCase(account.getStatus());
    }

    private void verifyPassword(PlatformAccountEntity account, String password) {
        platformAccountCredentialRepository
                .findByPlatformAccount_IdAndCredentialTypeAndStatus(
                        account.getId(),
                        PlatformAccountCredentialEntity.TYPE_PASSWORD,
                        PlatformAccountCredentialEntity.STATUS_ACTIVE)
                .ifPresentOrElse(
                        credential -> passwordCredentialService.verifyPasswordHash(
                                password,
                                credential.getPasswordHash(),
                                credential.getSalt(),
                                credential.getIterations(),
                                credential.getAlgorithm(),
                                INVALID_ACCOUNT_OR_PASSWORD),
                        () -> passwordCredentialService.verifyDefaultPassword(password, INVALID_ACCOUNT_OR_PASSWORD));
    }

    private Map<String, Object> issueLogin(PlatformAccountEntity account) {
        List<String> roles = resolveRoles(account);
        Map<String, Object> payload = platformAccountPayload(account, roles);
        payload.put("token", jwtService.issuePlatformToken(account.getId(), roles));
        payload.put("issuedAt", Instant.now().toString());
        return payload;
    }

    private Map<String, Object> platformAccountPayload(PlatformAccountEntity account, List<String> roles) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tokenType", "platform");
        payload.put("platformAccountId", account.getId());
        payload.put("email", account.getEmail());
        payload.put("mobile", account.getMobile());
        payload.put("displayName", account.getDisplayName());
        payload.put("themeCode", ProductThemeCodes.normalizeStored(account.getThemeCode()));
        payload.put("roles", roles);
        return payload;
    }

    private List<String> resolveRoles(PlatformAccountEntity account) {
        try {
            List<String> rawRoles = objectMapper.readValue(account.getRolesJson(), new TypeReference<List<String>>() {
            });
            LinkedHashSet<String> roles = new LinkedHashSet<>();
            for (String role : rawRoles) {
                if (role != null && RoleCodes.isPlatformRole(role.trim())) {
                    roles.add(role.trim());
                }
            }
            if (roles.isEmpty()) {
                throw new ForbiddenException("当前平台账号没有平台角色");
            }
            return List.copyOf(roles);
        } catch (ForbiddenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Platform account roles are invalid");
        }
    }

    private LoginIdentifier normalizeLoginIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.contains("@")) {
            return new LoginIdentifier("EMAIL", value.toLowerCase(java.util.Locale.ROOT));
        }
        return new LoginIdentifier("MOBILE", value);
    }

    private record LoginIdentifier(String type, String value) {
        boolean isEmail() {
            return "EMAIL".equals(type);
        }
    }
}
