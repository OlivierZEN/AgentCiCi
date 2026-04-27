package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.time.Instant;
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

    public AuthService(OrgRepository orgRepository,
                       UserRepository userRepository,
                       SmsCodeStore smsCodeStore,
                       JwtService jwtService,
                       @Value("${app.auth.bootstrap-admin-mobiles:}") String bootstrapAdminMobilesRaw) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
        this.smsCodeStore = smsCodeStore;
        this.jwtService = jwtService;
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
        String token = jwtService.issueToken(user);

        return Map.of(
                "token", token,
                "orgId", org.getId(),
                "userId", user.getId(),
                "roles", List.of(user.getRoleCode()),
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
                "roles", List.of(user.getRoleCode())
        );
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
