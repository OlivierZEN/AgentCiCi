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

    private static final Set<String> PRINCIPAL_TYPES = Set.of("COMPANY", "USER", "SYSTEM_ROLE");

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

    public boolean canReadChunk(String companyId, Long knowledgeBaseId, Long documentId, Long chunkId, AccessPrincipal principal) {
        if (principal == null || principal.unrestricted()) {
            return true;
        }
        if (isOrgAdmin(principal.roles())) {
            return true;
        }
        Instant now = Instant.now();
        List<KbAccessGrantEntity> chunkGrants = chunkId == null
                ? List.of()
                : activeGrants(grantRepository.findByCompanyIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        companyId,
                        knowledgeBaseId,
                        chunkId,
                        KbAccessGrantEntity.TARGET_CHUNK,
                        KbAccessGrantEntity.STATUS_ACTIVE), now);
        if (!chunkGrants.isEmpty()) {
            return grantsAllow(companyId, chunkGrants, principal);
        }
        List<KbAccessGrantEntity> documentGrants = documentId == null
                ? List.of()
                : activeGrants(grantRepository.findByCompanyIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        companyId,
                        knowledgeBaseId,
                        documentId,
                        KbAccessGrantEntity.TARGET_DOCUMENT,
                        KbAccessGrantEntity.STATUS_ACTIVE), now);
        if (!documentGrants.isEmpty()) {
            return grantsAllow(companyId, documentGrants, principal);
        }
        return true;
    }

    public List<Map<String, Object>> listDocumentGrants(String companyId, Long documentId) {
        KbDocumentEntity document = requireDocument(companyId, documentId);
        return activeGrants(grantRepository.findByCompanyIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        companyId,
                        document.getKnowledgeBaseId(),
                        documentId,
                        KbAccessGrantEntity.TARGET_DOCUMENT,
                        KbAccessGrantEntity.STATUS_ACTIVE), Instant.now())
                .stream()
                .map(this::grantPayload)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> replaceDocumentGrants(String companyId, Long documentId, String actorUserId, List<GrantInput> grants) {
        KbDocumentEntity document = requireDocument(companyId, documentId);
        List<KbAccessGrantEntity> existing = grantRepository.findByCompanyIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                companyId,
                document.getKnowledgeBaseId(),
                documentId,
                KbAccessGrantEntity.TARGET_DOCUMENT,
                KbAccessGrantEntity.STATUS_ACTIVE);
        existing.forEach(KbAccessGrantEntity::revoke);
        saveUniqueGrants(companyId, document.getKnowledgeBaseId(), KbAccessGrantEntity.TARGET_DOCUMENT, documentId, null, actorUserId, grants);
        return listDocumentGrants(companyId, documentId);
    }

    public List<Map<String, Object>> listChunkGrants(String companyId, Long chunkId) {
        KbChunkEntity chunk = requireChunk(companyId, chunkId);
        Long knowledgeBaseId = Long.parseLong(chunk.getKnowledgeBaseId());
        return activeGrants(grantRepository.findByCompanyIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                        companyId,
                        knowledgeBaseId,
                        chunkId,
                        KbAccessGrantEntity.TARGET_CHUNK,
                        KbAccessGrantEntity.STATUS_ACTIVE), Instant.now())
                .stream()
                .map(this::grantPayload)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> replaceChunkGrants(String companyId, Long chunkId, String actorUserId, List<GrantInput> grants) {
        KbChunkEntity chunk = requireChunk(companyId, chunkId);
        Long knowledgeBaseId = Long.parseLong(chunk.getKnowledgeBaseId());
        List<KbAccessGrantEntity> existing = grantRepository.findByCompanyIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
                companyId,
                knowledgeBaseId,
                chunkId,
                KbAccessGrantEntity.TARGET_CHUNK,
                KbAccessGrantEntity.STATUS_ACTIVE);
        existing.forEach(KbAccessGrantEntity::revoke);
        saveUniqueGrants(companyId, knowledgeBaseId, KbAccessGrantEntity.TARGET_CHUNK, chunk.getDocumentId(), chunkId, actorUserId, grants);
        return listChunkGrants(companyId, chunkId);
    }

    private void saveUniqueGrants(String companyId,
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
            NormalizedPrincipal principal = normalizePrincipal(companyId, input.principalType(), input.principalId());
            String key = principal.type() + "|" + (principal.id() == null ? "" : principal.id());
            if (!seen.add(key)) {
                continue;
            }
            grants.add(new KbAccessGrantEntity(
                    companyId,
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

    private boolean grantsAllow(String companyId, List<KbAccessGrantEntity> grants, AccessPrincipal principal) {
        for (KbAccessGrantEntity grant : grants) {
            if (!KbAccessGrantEntity.PERMISSION_READ.equals(grant.getPermission())) {
                continue;
            }
            if (principalMatches(companyId, grant, principal)) {
                return true;
            }
        }
        return false;
    }

    private boolean principalMatches(String companyId, KbAccessGrantEntity grant, AccessPrincipal principal) {
        return switch (grant.getPrincipalType()) {
            case "COMPANY" -> grant.getPrincipalId() == null || grant.getPrincipalId().isBlank() || companyId.equals(grant.getPrincipalId());
            case "USER" -> principal.userId() != null && principal.userId().equals(grant.getPrincipalId());
            case "SYSTEM_ROLE" -> principal.roles() != null && principal.roles().contains(grant.getPrincipalId());
            default -> false;
        };
    }

    private List<KbAccessGrantEntity> activeGrants(List<KbAccessGrantEntity> grants, Instant now) {
        return grants.stream().filter(item -> item.isCurrentlyActive(now)).toList();
    }

    private KbDocumentEntity requireDocument(String companyId, Long documentId) {
        return documentRepository.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge document not found"));
    }

    private KbChunkEntity requireChunk(String companyId, Long chunkId) {
        return chunkRepository.findByIdAndCompanyId(chunkId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge chunk not found"));
    }

    private NormalizedPrincipal normalizePrincipal(String companyId, String principalType, String principalId) {
        String type = normalizeUpper(principalType, "principalType");
        if (!PRINCIPAL_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported principalType: " + principalType);
        }
        String id = principalId == null || principalId.isBlank() ? null : principalId.trim();
        if ("COMPANY".equals(type)) {
            return new NormalizedPrincipal(type, id == null ? null : companyId);
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
