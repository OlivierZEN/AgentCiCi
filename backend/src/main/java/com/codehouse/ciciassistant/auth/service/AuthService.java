package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.util.AvatarDataUrlValidator;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final OrgRepository orgRepository;
    private final UserRepository userRepository;
    private final SmsCodeStore smsCodeStore;
    private final JwtService jwtService;
    private final Set<String> bootstrapAdminMobiles;
    private final Set<String> platformAdminMobiles;
    private final Set<String> platformOperatorMobiles;
    private final Set<String> platformSupportMobiles;
    private final Set<String> platformBillingMobiles;
    private final Set<String> platformAuditorMobiles;

    public AuthService(OrgRepository orgRepository,
                       UserRepository userRepository,
                       SmsCodeStore smsCodeStore,
                       JwtService jwtService,
                       @Value("${app.auth.bootstrap-admin-mobiles:}") String bootstrapAdminMobilesRaw,
                       @Value("${app.auth.platform-admin-mobiles:}") String platformAdminMobilesRaw,
                       @Value("${app.auth.platform-operator-mobiles:}") String platformOperatorMobilesRaw,
                       @Value("${app.auth.platform-support-mobiles:}") String platformSupportMobilesRaw,
                       @Value("${app.auth.platform-billing-mobiles:}") String platformBillingMobilesRaw,
                       @Value("${app.auth.platform-auditor-mobiles:}") String platformAuditorMobilesRaw) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
        this.smsCodeStore = smsCodeStore;
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
        OrgEntity org = requireOrg(orgId);
        String code = smsCodeStore.createCode(org.getId(), mobile);
        return Map.of(
                "orgId", org.getId(),
                "mobile", mobile,
                "expiresInSeconds", 300,
                "status", "sent",
                "devCode", code
        );
    }

    @Transactional
    public Map<String, Object> loginBySms(String orgId, String mobile, String code) {
        OrgEntity org = requireOrg(orgId);
        smsCodeStore.verifyCode(org.getId(), mobile, code);

        String mobileNorm = mobile == null ? "" : mobile.trim();
        boolean bootstrapAdmin = bootstrapAdminMobiles.contains(mobileNorm);
        String initialRole = bootstrapAdmin ? RoleCodes.ORG_ADMIN : RoleCodes.ORG_USER;
        UserEntity user = userRepository.findByOrgIdAndMobile(org.getId(), mobileNorm)
                .orElseGet(() -> userRepository.save(new UserEntity(org, mobileNorm, initialRole)));
        if (bootstrapAdmin && RoleCodes.ORG_USER.equals(user.getRoleCode())) {
            user.setRoleCode(RoleCodes.ORG_ADMIN);
            user = userRepository.save(user);
        }
        List<String> roles = resolveRoles(user);
        String token = jwtService.issueToken(user, roles);

        return Map.of(
                "token", token,
                "orgId", org.getId(),
                "userId", user.getId(),
                "roles", roles,
                "issuedAt", Instant.now().toString()
        );
    }

    public Map<String, Object> currentUser(String orgId, String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.getOrg().getId().equals(orgId)) {
            throw new UnauthorizedException("Tenant mismatch");
        }
        return Map.of(
                "orgId", orgId,
                "userId", user.getId(),
                "mobile", user.getMobile(),
                "nickname", user.getNickname() == null ? "" : user.getNickname(),
                "avatarBase64", user.getAvatarBase64() == null ? "" : user.getAvatarBase64(),
                "roles", resolveRoles(user)
        );
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
        OrgEntity org = orgRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!"ACTIVE".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalArgumentException("Organization is disabled");
        }
        return org;
    }
}
