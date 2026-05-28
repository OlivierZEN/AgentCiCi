package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantEntity;
import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.domain.AgentPermissionAuditEntity;
import com.codehouse.ciciassistant.agent.domain.AgentPermissionAuditRepository;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAccessControlService {

    private static final Set<String> PRINCIPAL_TYPES = Set.of("ORG", "USER", "SYSTEM_ROLE", "GROUP", "CUSTOM_ROLE", "DEPARTMENT");

    private final AgentAccessGrantRepository grantRepository;
    private final AgentPermissionAuditRepository auditRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AgentAccessControlService(AgentAccessGrantRepository grantRepository,
                                     AgentPermissionAuditRepository auditRepository,
                                     AgentDefinitionRepository agentDefinitionRepository,
                                     UserRepository userRepository,
                                     ObjectMapper objectMapper) {
        this.grantRepository = grantRepository;
        this.auditRepository = auditRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public boolean can(String orgId, String userId, List<String> roles, String agentId, AgentPermission permission) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findByOrgIdAndAgentIdAndEnabledTrue(orgId, normalizeAgentId(agentId))
                .orElse(null);
        UserEntity member = activeMember(orgId, userId);
        if (agent == null || member == null) {
            return false;
        }
        List<String> effectiveRoles = roles == null || roles.isEmpty() ? List.of(member.getRoleCode()) : roles;
        if (isOrgAdmin(effectiveRoles) || isOwner(agent, userId)) {
            return true;
        }
        Set<String> permissionCandidates = permissionCandidates(permission);
        Instant now = Instant.now();
        List<AgentAccessGrantEntity> grants = grantRepository.findByOrgIdAndAgentIdAndStatus(
                orgId,
                agent.getAgentId(),
                AgentAccessGrantEntity.STATUS_ACTIVE);
        for (AgentAccessGrantEntity grant : grants) {
            if (!grant.isCurrentlyActive(now) || !permissionCandidates.contains(grant.getPermission())) {
                continue;
            }
            if (principalMatches(grant, orgId, userId, effectiveRoles)) {
                return true;
            }
        }
        return false;
    }

    public void require(String orgId, String userId, List<String> roles, String agentId, AgentPermission permission) {
        if (!can(orgId, userId, roles, agentId, permission)) {
            recordAudit(orgId, normalizeAgentId(agentId), userId, "RUNTIME_DENIED", null, null, permission.name(),
                    null, null, "缺少 Agent " + permission.name() + " 权限", null);
            throw new ForbiddenException("缺少 Agent " + permission.name() + " 权限");
        }
    }

    public void recordOpenApiRunAsDenied(String orgId, String agentId, String runAsUserId, String reason, String traceId) {
        recordAudit(orgId, normalizeAgentId(agentId), runAsUserId, "OPENAPI_RUN_AS_DENIED", "USER", runAsUserId,
                AgentPermission.RUN.name(), null, null, reason, traceId);
    }

    public Set<AgentPermission> effectivePermissions(String orgId, String userId, List<String> roles, String agentId) {
        LinkedHashSet<AgentPermission> result = new LinkedHashSet<>();
        for (AgentPermission permission : AgentPermission.values()) {
            if (can(orgId, userId, roles, agentId, permission)) {
                result.add(permission);
            }
        }
        return result;
    }

    public List<GrantView> listGrants(String orgId, String agentId) {
        requireAgent(orgId, agentId);
        return grantRepository.findByOrgIdAndAgentIdAndStatusOrderByPrincipalTypeAscPrincipalIdAscPermissionAsc(
                        orgId,
                        normalizeAgentId(agentId),
                        AgentAccessGrantEntity.STATUS_ACTIVE)
                .stream()
                .filter(item -> item.isCurrentlyActive(Instant.now()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public List<GrantView> replaceGrants(String orgId, String agentId, String actorUserId, ReplaceGrantsCommand command) {
        AgentDefinitionEntity agent = requireAgent(orgId, agentId);
        List<GrantView> before = listGrants(orgId, agent.getAgentId());
        grantRepository.findByOrgIdAndAgentIdAndStatus(orgId, agent.getAgentId(), AgentAccessGrantEntity.STATUS_ACTIVE)
                .forEach(AgentAccessGrantEntity::revoke);
        List<GrantInput> inputs = command == null || command.grants() == null ? List.of() : command.grants();
        Set<String> seen = new LinkedHashSet<>();
        for (GrantInput input : inputs) {
            NormalizedPrincipal principal = normalizePrincipal(orgId, input.principalType(), input.principalId());
            List<AgentPermission> permissions = normalizePermissions(input.permissions());
            for (AgentPermission permission : permissions) {
                String key = principal.type() + "|" + (principal.id() == null ? "" : principal.id()) + "|" + permission.name();
                if (!seen.add(key)) {
                    continue;
                }
                grantRepository.save(new AgentAccessGrantEntity(
                        orgId,
                        agent.getAgentId(),
                        principal.type(),
                        principal.id(),
                        permission.name(),
                        "MANUAL",
                        actorUserId,
                        input.expiresAt()));
            }
        }
        List<GrantView> after = listGrants(orgId, agent.getAgentId());
        recordAudit(orgId, agent.getAgentId(), actorUserId, "BULK_REPLACE", null, null, null,
                toJson(before), toJson(after), "Agent access grants replaced", null);
        return after;
    }

    private UserEntity activeMember(String orgId, String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userRepository.findByIdAndOrg_Id(userId, orgId)
                .filter(member -> UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus()))
                .orElse(null);
    }

    private boolean isOrgAdmin(List<String> roles) {
        return roles != null && roles.stream().anyMatch(RoleCodes::isOrgAdminRole);
    }

    private boolean isOwner(AgentDefinitionEntity agent, String userId) {
        return userId != null && !userId.isBlank() && userId.equals(agent.getOwnerUserId());
    }

    private boolean principalMatches(AgentAccessGrantEntity grant, String orgId, String userId, List<String> roles) {
        return switch (grant.getPrincipalType()) {
            case "ORG" -> grant.getPrincipalId() == null || grant.getPrincipalId().isBlank() || orgId.equals(grant.getPrincipalId());
            case "USER" -> userId.equals(grant.getPrincipalId());
            case "SYSTEM_ROLE" -> roles != null && roles.contains(grant.getPrincipalId());
            default -> false;
        };
    }

    private Set<String> permissionCandidates(AgentPermission permission) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(permission.name());
        if (permission == AgentPermission.VIEW) {
            candidates.add(AgentPermission.RUN.name());
        }
        return candidates;
    }

    private AgentDefinitionEntity requireAgent(String orgId, String agentId) {
        String normalized = normalizeAgentId(agentId);
        return agentDefinitionRepository.findByOrgIdAndAgentIdAndEnabledTrue(orgId, normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + normalized));
    }

    private NormalizedPrincipal normalizePrincipal(String orgId, String principalType, String principalId) {
        String type = normalizeUpper(principalType, "principalType");
        if (!PRINCIPAL_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported principalType: " + principalType);
        }
        String id = principalId == null || principalId.isBlank() ? null : principalId.trim();
        if ("ORG".equals(type)) {
            return new NormalizedPrincipal(type, id == null ? null : orgId);
        }
        if ("USER".equals(type)) {
            String userId = requireText(id, "principalId");
            userRepository.findByIdAndOrg_Id(userId, orgId)
                    .filter(member -> UserEntity.STATUS_ACTIVE.equals(member.getMemberStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("USER principal must be an active org member"));
            return new NormalizedPrincipal(type, userId);
        }
        if ("SYSTEM_ROLE".equals(type)) {
            String role = normalizeUpper(id, "principalId");
            if (!RoleCodes.ORG_ADMIN.equals(role) && !RoleCodes.ORG_USER.equals(role)) {
                throw new IllegalArgumentException("SYSTEM_ROLE principal must be ORG_ADMIN or ORG_USER");
            }
            return new NormalizedPrincipal(type, role);
        }
        return new NormalizedPrincipal(type, requireText(id, "principalId"));
    }

    private List<AgentPermission> normalizePermissions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<AgentPermission> result = new ArrayList<>();
        for (String item : raw) {
            AgentPermission permission = AgentPermission.from(item);
            if (!result.contains(permission)) {
                result.add(permission);
            }
        }
        return List.copyOf(result);
    }

    private GrantView toView(AgentAccessGrantEntity item) {
        return new GrantView(
                item.getId(),
                item.getPrincipalType(),
                item.getPrincipalId(),
                item.getPermission(),
                item.getSource(),
                item.getGrantedBy(),
                item.getExpiresAt(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private void recordAudit(String orgId,
                             String agentId,
                             String actorUserId,
                             String action,
                             String principalType,
                             String principalId,
                             String permission,
                             String beforeJson,
                             String afterJson,
                             String reason,
                             String traceId) {
        try {
            auditRepository.save(new AgentPermissionAuditEntity(
                    orgId,
                    agentId,
                    actorUserId,
                    action,
                    principalType,
                    principalId,
                    permission,
                    beforeJson,
                    afterJson,
                    reason,
                    traceId));
        } catch (RuntimeException ignored) {
            // Permission checks must not fail because audit persistence is temporarily unavailable.
        }
    }

    private String normalizeAgentId(String raw) {
        String text = requireText(raw, "agentId").toLowerCase(Locale.ROOT);
        if (!text.matches("^[a-z0-9][a-z0-9-]{1,63}$")) {
            throw new IllegalArgumentException("agentId must match ^[a-z0-9][a-z0-9-]{1,63}$");
        }
        return text;
    }

    private String normalizeUpper(String raw, String fieldName) {
        return requireText(raw, fieldName).toUpperCase(Locale.ROOT);
    }

    private String requireText(String raw, String fieldName) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    public Map<String, Object> permissionPayload(String orgId, String userId, List<String> roles, String agentId) {
        Set<AgentPermission> permissions = effectivePermissions(orgId, userId, roles, agentId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("permissions", permissions.stream().map(AgentPermission::name).toList());
        payload.put("canManage", permissions.contains(AgentPermission.MANAGE));
        payload.put("canEdit", permissions.contains(AgentPermission.EDIT));
        payload.put("canRun", permissions.contains(AgentPermission.RUN));
        payload.put("canOpenApi", permissions.contains(AgentPermission.OPENAPI));
        payload.put("canViewLogs", permissions.contains(AgentPermission.LOG_VIEW));
        return payload;
    }

    public record GrantInput(String principalType, String principalId, List<String> permissions, Instant expiresAt) {
    }

    public record ReplaceGrantsCommand(List<GrantInput> grants) {
    }

    public record GrantView(String id,
                            String principalType,
                            String principalId,
                            String permission,
                            String source,
                            String grantedBy,
                            Instant expiresAt,
                            Instant createdAt,
                            Instant updatedAt) {
    }

    private record NormalizedPrincipal(String type, String id) {
    }
}
