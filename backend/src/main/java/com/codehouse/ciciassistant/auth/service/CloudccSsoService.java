package com.codehouse.ciciassistant.auth.service;

import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudccSsoService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final ConcurrentHashMap<String, SsoTicket> tickets = new ConcurrentHashMap<>();

    public CloudccSsoService(AuthService authService,
                             UserRepository userRepository,
                             CloudccAccessTokenService cloudccAccessTokenService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
    }

    @Transactional
    public Map<String, Object> issueTicket(String agentOrgId,
                                           String cloudccAccessToken,
                                           Map<String, Object> cloudccUser,
                                           String targetPath) {
        String orgId = requireText(agentOrgId, "缺少 AgentCiCi 组织 ID");
        String runtimeToken = requireText(cloudccAccessToken, "缺少 CloudCC runtime token");
        List<String> reportedIdentities = cloudccIdentityCandidates(cloudccUser);
        if (reportedIdentities.isEmpty()) {
            throw new UnauthorizedException("CloudCC 当前用户信息不完整");
        }

        CloudccAccessTokenService.ValidatedCloudccToken validated = cloudccAccessTokenService
                .validateRuntimeAccessToken(orgId, runtimeToken)
                .orElseThrow(() -> new UnauthorizedException("CloudCC runtime token 校验失败"));
        String actorId = normalize(validated.actorId());
        if (actorId.isBlank()) {
            throw new UnauthorizedException("CloudCC runtime token 未返回用户身份，无法确保数据权限一致");
        }
        if (!containsIdentity(reportedIdentities, actorId)) {
            throw new UnauthorizedException("CloudCC runtime token 用户与当前页面用户不一致");
        }

        UserEntity member = findMappedMember(orgId, reportedIdentities);
        if (!containsIdentity(reportedIdentities, member.getCcUsername()) && !identityEquals(actorId, member.getCcUsername())) {
            throw new UnauthorizedException("CloudCC 用户未绑定到当前 AgentCiCi 成员");
        }
        cloudccAccessTokenService.getSessionContext(orgId, member.getId())
                .orElseThrow(() -> new UnauthorizedException("当前 AgentCiCi 成员无法生成 CloudCC accessToken"));

        pruneExpiredTickets();
        String ticket = "ccsso_" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        tickets.put(ticket, new SsoTicket(orgId, member.getId(), member.getCcUsername(), expiresAt, safeTargetPath(targetPath)));
        return Map.of(
                "ticket", ticket,
                "expiresAt", expiresAt.toString(),
                "orgId", orgId,
                "memberId", member.getId(),
                "cloudccUsername", member.getCcUsername(),
                "targetPath", safeTargetPath(targetPath)
        );
    }

    @Transactional
    public Map<String, Object> consumeTicket(String ticket) {
        String ticketValue = requireText(ticket, "缺少 SSO ticket");
        SsoTicket ssoTicket = tickets.remove(ticketValue);
        if (ssoTicket == null || ssoTicket.expiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("SSO ticket 已失效，请重新从 CloudCC CRM 进入");
        }
        Map<String, Object> login = new LinkedHashMap<>(authService.loginAsMember(ssoTicket.orgId(), ssoTicket.userId()));
        login.put("loginSource", "cloudcc_sso");
        login.put("cloudccUsername", ssoTicket.cloudccUsername());
        return login;
    }

    private UserEntity findMappedMember(String orgId, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            UserEntity member = userRepository
                    .findByOrg_IdAndCcUsernameIgnoreCaseAndMemberStatus(orgId, candidate, UserEntity.STATUS_ACTIVE)
                    .orElse(null);
            if (member != null) {
                return member;
            }
        }
        throw new UnauthorizedException("CloudCC 用户尚未绑定到 AgentCiCi 成员");
    }

    private List<String> cloudccIdentityCandidates(Map<String, Object> cloudccUser) {
        List<String> identities = new ArrayList<>();
        addIdentity(identities, nestedText(cloudccUser, "username"));
        addIdentity(identities, nestedText(cloudccUser, "userName"));
        addIdentity(identities, nestedText(cloudccUser, "loginName"));
        addIdentity(identities, nestedText(cloudccUser, "login_name"));
        addIdentity(identities, nestedText(cloudccUser, "email"));
        addIdentity(identities, nestedText(cloudccUser, "mail"));
        addIdentity(identities, nestedText(cloudccUser, "id"));
        addIdentity(identities, nestedText(cloudccUser, "userId"));
        addIdentity(identities, nestedText(cloudccUser, "userid"));
        addIdentity(identities, nestedText(cloudccUser, "data.username"));
        addIdentity(identities, nestedText(cloudccUser, "data.userName"));
        addIdentity(identities, nestedText(cloudccUser, "data.loginName"));
        addIdentity(identities, nestedText(cloudccUser, "data.login_name"));
        addIdentity(identities, nestedText(cloudccUser, "data.email"));
        addIdentity(identities, nestedText(cloudccUser, "data.id"));
        addIdentity(identities, nestedText(cloudccUser, "data.userId"));
        addIdentity(identities, nestedText(cloudccUser, "data.userid"));
        return identities;
    }

    private void addIdentity(List<String> identities, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank() || containsIdentity(identities, normalized)) {
            return;
        }
        identities.add(normalized);
    }

    @SuppressWarnings("unchecked")
    private String nestedText(Map<String, Object> map, String path) {
        if (map == null || map.isEmpty() || path == null || path.isBlank()) {
            return "";
        }
        Object current = map;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return "";
            }
            current = ((Map<String, Object>) currentMap).get(segment);
            if (current == null) {
                return "";
            }
        }
        return String.valueOf(current);
    }

    private void pruneExpiredTickets() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private boolean containsIdentity(List<String> identities, String value) {
        return identities.stream().anyMatch(identity -> identityEquals(identity, value));
    }

    private boolean identityEquals(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isBlank() && a.equalsIgnoreCase(b);
    }

    private String safeTargetPath(String targetPath) {
        String value = normalize(targetPath);
        if (value.isBlank() || !value.startsWith("/") || value.startsWith("//") || value.contains("://")) {
            return "/app?aiApp=customer-workbench";
        }
        return value;
    }

    private String requireText(String value, String message) {
        String text = normalize(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record SsoTicket(String orgId, String userId, String cloudccUsername, Instant expiresAt, String targetPath) {}
}
