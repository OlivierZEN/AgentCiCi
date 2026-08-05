package com.codehouse.ciciassistant.ontology.semattice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SematticeOntologyStateStore {

    Optional<Binding> findBinding(String companyId, Long workspaceId);

    Binding saveBinding(Binding binding);

    List<ElementBinding> listElements(String companyId, Long workspaceId);

    ElementBinding saveElement(ElementBinding element);

    Optional<Operation> findOperation(String companyId, Long workspaceId, String operationId);

    Optional<Operation> findLatestOperation(String companyId, Long workspaceId);

    Optional<Operation> findOperationByRevision(
            String companyId,
            Long workspaceId,
            String operationType,
            long sourceRevision,
            String sourceDigest);

    Operation saveOperation(Operation operation);

    record Binding(
            String companyId,
            Long workspaceId,
            String sematticeTenantId,
            String activeMetadataVersionId,
            Long activeSequence,
            String activeDigest,
            String syncStatus,
            String lastErrorCode,
            Instant lastCheckedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    record ElementBinding(
            String companyId,
            Long workspaceId,
            String elementType,
            String elementKey,
            String sematticeElementId,
            String sematticeApiName,
            long firstBoundRevision,
            long lastSyncedRevision,
            String sourceDigest,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    record Operation(
            String operationId,
            String companyId,
            Long workspaceId,
            String operationType,
            long sourceRevision,
            String sourceDigest,
            String baseMetadataVersionId,
            String candidateMetadataVersionId,
            String changesetId,
            String subjectType,
            String subjectId,
            String approvalRequestId,
            String status,
            String riskLevel,
            boolean requiresBackfill,
            String requestedBy,
            String approvedBy,
            String lastErrorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt) {
    }
}
