package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.kb.domain.KbAccessGrantEntity;
import com.codehouse.ciciassistant.kb.domain.KbAccessGrantRepository;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
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
public class KbAccessControlService {

    private static final Set<String> PRINCIPAL_TYPES = Set.of("ORG", "USER", "SYSTEM_ROLE");

    private final KbAccessGrantRepository grantRepository;
    private final KbDocumentRepository documentRepository;
    private final KbChunkRepository chunkRepository;

    public KbAccessControlService(KbAccessGrantRepository grantRepository,
                                  KbDocumentRepository documentRepository,
                                  KbChunkRepository chunkRepository) {
        this.grantRepository = grantRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    public boolean canReadChunk(String orgId, Long knowledgeBaseId, Long documentId, Long chunkId, AccessPrincipal principal) {
        if (principal == null || principal.unrestricted()) {
            return true;
        }
        if (isOrgAdmin(principal.roles())) {
            return true;
        }
        Instant now = Instant.now();
        List<KbAccessGrantEntity> chunkGrants = chunkId == null
                ? List.of()
                : activeGrants(grantRepository.findByOrgIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        orgId,
                        knowledgeBaseId,
                        chunkId,
                        KbAccessGrantEntity.TARGET_CHUNK,
                        KbAccessGrantEntity.STATUS_ACTIVE), now);
        if (!chunkGrants.isEmpty()) {
            return grantsAllow(orgId, chunkGrants, principal);
        }
        List<KbAccessGrantEntity> documentGrants = documentId == null
                ? List.of()
                : activeGrants(grantRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        orgId,
                        knowledgeBaseId,
                        documentId,
                        KbAccessGrantEntity.TARGET_DOCUMENT,
                        KbAccessGrantEntity.STATUS_ACTIVE), now);
        if (!documentGrants.isEmpty()) {
            return grantsAllow(orgId, documentGrants, principal);
        }
        return true;
    }

    public List<Map<String, Object>> listDocumentGrants(String orgId, Long documentId) {
        KbDocumentEntity document = requireDocument(orgId, documentId);
        return activeGrants(grantRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        orgId,
                        document.getKnowledgeBaseId(),
                        documentId,
                        KbAccessGrantEntity.TARGET_DOCUMENT,
                        KbAccessGrantEntity.STATUS_ACTIVE), Instant.now())
                .stream()
                .map(this::grantPayload)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> replaceDocumentGrants(String orgId, Long documentId, String actorUserId, List<GrantInput> grants) {
        KbDocumentEntity document = requireDocument(orgId, documentId);
        List<KbAccessGrantEntity> existing = grantRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                orgId,
                document.getKnowledgeBaseId(),
                documentId,
                KbAccessGrantEntity.TARGET_DOCUMENT,
                KbAccessGrantEntity.STATUS_ACTIVE);
        existing.forEach(KbAccessGrantEntity::revoke);
        saveUniqueGrants(orgId, document.getKnowledgeBaseId(), KbAccessGrantEntity.TARGET_DOCUMENT, documentId, null, actorUserId, grants);
        return listDocumentGrants(orgId, documentId);
    }

    public List<Map<String, Object>> listChunkGrants(String orgId, Long chunkId) {
        KbChunkEntity chunk = requireChunk(orgId, chunkId);
        Long knowledgeBaseId = Long.parseLong(chunk.getKnowledgeBaseId());
        return activeGrants(grantRepository.findByOrgIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        orgId,
                        knowledgeBaseId,
                        chunkId,
                        KbAccessGrantEntity.TARGET_CHUNK,
                        KbAccessGrantEntity.STATUS_ACTIVE), Instant.now())
                .stream()
                .map(this::grantPayload)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> replaceChunkGrants(String orgId, Long chunkId, String actorUserId, List<GrantInput> grants) {
        KbChunkEntity chunk = requireChunk(orgId, chunkId);
        Long knowledgeBaseId = Long.parseLong(chunk.getKnowledgeBaseId());
        List<KbAccessGrantEntity> existing = grantRepository.findByOrgIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                orgId,
                knowledgeBaseId,
                chunkId,
                KbAccessGrantEntity.TARGET_CHUNK,
                KbAccessGrantEntity.STATUS_ACTIVE);
        existing.forEach(KbAccessGrantEntity::revoke);
        saveUniqueGrants(orgId, knowledgeBaseId, KbAccessGrantEntity.TARGET_CHUNK, chunk.getDocumentId(), chunkId, actorUserId, grants);
        return listChunkGrants(orgId, chunkId);
    }

    private void saveUniqueGrants(String orgId,
                                  Long knowledgeBaseId,
                                  String targetType,
                                  Long documentId,
                                  Long chunkId,
                                  String actorUserId,
                                  List<GrantInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        ArrayList<KbAccessGrantEntity> grants = new ArrayList<>();
        for (GrantInput input : inputs) {
            NormalizedPrincipal principal = normalizePrincipal(orgId, input.principalType(), input.principalId());
            String key = principal.type() + "|" + (principal.id() == null ? "" : principal.id());
            if (!seen.add(key)) {
                continue;
            }
            grants.add(new KbAccessGrantEntity(
                    orgId,
                    knowledgeBaseId,
                    targetType,
                    documentId,
                    chunkId,
                    principal.type(),
                    principal.id(),
                    KbAccessGrantEntity.PERMISSION_READ,
                    "MANUAL",
                    actorUserId,
                    input.expiresAt()));
        }
        grantRepository.saveAll(grants);
    }

    private boolean grantsAllow(String orgId, List<KbAccessGrantEntity> grants, AccessPrincipal principal) {
        for (KbAccessGrantEntity grant : grants) {
            if (!KbAccessGrantEntity.PERMISSION_READ.equals(grant.getPermission())) {
                continue;
            }
            if (principalMatches(orgId, grant, principal)) {
                return true;
            }
        }
        return false;
    }

    private boolean principalMatches(String orgId, KbAccessGrantEntity grant, AccessPrincipal principal) {
        return switch (grant.getPrincipalType()) {
            case "ORG" -> grant.getPrincipalId() == null || grant.getPrincipalId().isBlank() || orgId.equals(grant.getPrincipalId());
            case "USER" -> principal.userId() != null && principal.userId().equals(grant.getPrincipalId());
            case "SYSTEM_ROLE" -> principal.roles() != null && principal.roles().contains(grant.getPrincipalId());
            default -> false;
        };
    }

    private List<KbAccessGrantEntity> activeGrants(List<KbAccessGrantEntity> grants, Instant now) {
        return grants.stream().filter(item -> item.isCurrentlyActive(now)).toList();
    }

    private KbDocumentEntity requireDocument(String orgId, Long documentId) {
        return documentRepository.findByIdAndOrgId(documentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found"));
    }

    private KbChunkEntity requireChunk(String orgId, Long chunkId) {
        return chunkRepository.findByIdAndOrgId(chunkId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge chunk not found"));
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
        if ("SYSTEM_ROLE".equals(type)) {
            String role = normalizeUpper(id, "principalId");
            if (!RoleCodes.ORG_ADMIN.equals(role) && !RoleCodes.ORG_USER.equals(role)) {
                throw new IllegalArgumentException("SYSTEM_ROLE principal must be ORG_ADMIN or ORG_USER");
            }
            return new NormalizedPrincipal(type, role);
        }
        return new NormalizedPrincipal(type, requireText(id, "principalId"));
    }

    private boolean isOrgAdmin(List<String> roles) {
        return roles != null && roles.stream().anyMatch(RoleCodes::isOrgAdminRole);
    }

    private String normalizeUpper(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private Map<String, Object> grantPayload(KbAccessGrantEntity grant) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", grant.getId());
        payload.put("targetType", grant.getTargetType());
        payload.put("knowledgeBaseId", grant.getKnowledgeBaseId());
        payload.put("documentId", grant.getDocumentId() == null ? "" : grant.getDocumentId());
        payload.put("chunkId", grant.getChunkId() == null ? "" : grant.getChunkId());
        payload.put("principalType", grant.getPrincipalType());
        payload.put("principalId", grant.getPrincipalId() == null ? "" : grant.getPrincipalId());
        payload.put("permission", grant.getPermission());
        payload.put("source", grant.getSource());
        payload.put("expiresAt", grant.getExpiresAt() == null ? "" : grant.getExpiresAt().toString());
        payload.put("createdAt", grant.getCreatedAt().toString());
        payload.put("updatedAt", grant.getUpdatedAt().toString());
        return payload;
    }

    public record AccessPrincipal(String userId, List<String> roles, boolean unrestricted) {
        public static AccessPrincipal system() {
            return new AccessPrincipal("", List.of(), true);
        }

        public static AccessPrincipal user(String userId, List<String> roles) {
            return new AccessPrincipal(userId == null ? "" : userId, roles == null ? List.of() : List.copyOf(roles), false);
        }
    }

    public record GrantInput(String principalType, String principalId, Instant expiresAt) {
    }

    private record NormalizedPrincipal(String type, String id) {
    }
}
