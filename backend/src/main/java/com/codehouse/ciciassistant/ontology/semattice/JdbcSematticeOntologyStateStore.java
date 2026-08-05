package com.codehouse.ciciassistant.ontology.semattice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSematticeOntologyStateStore implements SematticeOntologyStateStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSematticeOntologyStateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Binding> findBinding(String companyId, Long workspaceId) {
        return jdbcTemplate.query("""
                SELECT company_id, workspace_id, semattice_tenant_id,
                       active_metadata_version_id, active_sequence, active_digest,
                       sync_status, last_error_code, last_checked_at, created_at, updated_at
                  FROM ontology_semattice_binding
                 WHERE company_id = ? AND workspace_id = ?
                """, (row, ignored) -> binding(row), companyId, workspaceId).stream().findFirst();
    }

    @Override
    @Transactional
    public Binding saveBinding(Binding value) {
        int updated = jdbcTemplate.update("""
                UPDATE ontology_semattice_binding
                   SET semattice_tenant_id = ?, active_metadata_version_id = ?,
                       active_sequence = ?, active_digest = ?, sync_status = ?,
                       last_error_code = ?, last_checked_at = ?, updated_at = ?
                 WHERE company_id = ? AND workspace_id = ?
                """, value.sematticeTenantId(), value.activeMetadataVersionId(), value.activeSequence(),
                value.activeDigest(), value.syncStatus(), value.lastErrorCode(), timestamp(value.lastCheckedAt()),
                timestamp(value.updatedAt()), value.companyId(), value.workspaceId());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ontology_semattice_binding
                        (company_id, workspace_id, semattice_tenant_id,
                         active_metadata_version_id, active_sequence, active_digest,
                         sync_status, last_error_code, last_checked_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.companyId(), value.workspaceId(), value.sematticeTenantId(),
                    value.activeMetadataVersionId(), value.activeSequence(), value.activeDigest(),
                    value.syncStatus(), value.lastErrorCode(), timestamp(value.lastCheckedAt()),
                    timestamp(value.createdAt()), timestamp(value.updatedAt()));
        }
        return findBinding(value.companyId(), value.workspaceId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElementBinding> listElements(String companyId, Long workspaceId) {
        return jdbcTemplate.query("""
                SELECT company_id, workspace_id, element_type, element_key,
                       semattice_element_id, semattice_api_name, first_bound_revision,
                       last_synced_revision, source_digest, status, created_at, updated_at
                  FROM ontology_semattice_element_binding
                 WHERE company_id = ? AND workspace_id = ?
                 ORDER BY element_type, element_key
                """, (row, ignored) -> element(row), companyId, workspaceId);
    }

    @Override
    @Transactional
    public ElementBinding saveElement(ElementBinding value) {
        int updated = jdbcTemplate.update("""
                UPDATE ontology_semattice_element_binding
                   SET semattice_element_id = ?, semattice_api_name = ?,
                       last_synced_revision = ?, source_digest = ?, status = ?, updated_at = ?
                 WHERE company_id = ? AND workspace_id = ?
                   AND element_type = ? AND element_key = ?
                """, value.sematticeElementId(), value.sematticeApiName(),
                value.lastSyncedRevision(), value.sourceDigest(), value.status(),
                timestamp(value.updatedAt()), value.companyId(), value.workspaceId(),
                value.elementType(), value.elementKey());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ontology_semattice_element_binding
                        (company_id, workspace_id, element_type, element_key,
                         semattice_element_id, semattice_api_name, first_bound_revision,
                         last_synced_revision, source_digest, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, value.companyId(), value.workspaceId(), value.elementType(), value.elementKey(),
                    value.sematticeElementId(), value.sematticeApiName(), value.firstBoundRevision(),
                    value.lastSyncedRevision(), value.sourceDigest(), value.status(),
                    timestamp(value.createdAt()), timestamp(value.updatedAt()));
        }
        return listElements(value.companyId(), value.workspaceId()).stream()
                .filter(element -> element.elementType().equals(value.elementType())
                        && element.elementKey().equals(value.elementKey()))
                .findFirst()
                .orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Operation> findOperation(
            String companyId,
            Long workspaceId,
            String operationId) {
        return jdbcTemplate.query(operationSelect() + """
                 WHERE company_id = ? AND workspace_id = ? AND operation_id = ?
                """, (row, ignored) -> operation(row), companyId, workspaceId, operationId)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Operation> findLatestOperation(String companyId, Long workspaceId) {
        return jdbcTemplate.query(operationSelect() + """
                 WHERE company_id = ? AND workspace_id = ?
                 ORDER BY updated_at DESC, created_at DESC
                 LIMIT 1
                """, (row, ignored) -> operation(row), companyId, workspaceId)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Operation> findOperationByRevision(
            String companyId,
            Long workspaceId,
            String operationType,
            long sourceRevision,
            String sourceDigest) {
        return jdbcTemplate.query(operationSelect() + """
                 WHERE company_id = ? AND workspace_id = ? AND operation_type = ?
                   AND source_revision = ? AND source_digest = ?
                """, (row, ignored) -> operation(row), companyId, workspaceId, operationType,
                sourceRevision, sourceDigest).stream().findFirst();
    }

    @Override
    @Transactional
    public Operation saveOperation(Operation value) {
        int updated = jdbcTemplate.update("""
                UPDATE ontology_semattice_operation
                   SET base_metadata_version_id = ?, candidate_metadata_version_id = ?,
                       changeset_id = ?, subject_type = ?, subject_id = ?, approval_request_id = ?,
                       status = ?, risk_level = ?, requires_backfill = ?, approved_by = ?,
                       last_error_code = ?, updated_at = ?, activated_at = ?
                 WHERE operation_id = ? AND company_id = ? AND workspace_id = ?
                """, value.baseMetadataVersionId(), value.candidateMetadataVersionId(),
                value.changesetId(), value.subjectType(), value.subjectId(),
                value.approvalRequestId(), value.status(), value.riskLevel(),
                value.requiresBackfill(), value.approvedBy(), value.lastErrorCode(),
                timestamp(value.updatedAt()), timestamp(value.activatedAt()), value.operationId(),
                value.companyId(), value.workspaceId());
        if (updated == 0) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO ontology_semattice_operation
                            (operation_id, company_id, workspace_id, operation_type,
                             source_revision, source_digest, base_metadata_version_id,
                             candidate_metadata_version_id, changeset_id, subject_type, subject_id,
                             approval_request_id, status, risk_level, requires_backfill,
                             requested_by, approved_by, last_error_code,
                             created_at, updated_at, activated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, value.operationId(), value.companyId(), value.workspaceId(),
                        value.operationType(), value.sourceRevision(), value.sourceDigest(),
                        value.baseMetadataVersionId(), value.candidateMetadataVersionId(),
                        value.changesetId(), value.subjectType(), value.subjectId(),
                        value.approvalRequestId(), value.status(), value.riskLevel(),
                        value.requiresBackfill(), value.requestedBy(), value.approvedBy(),
                        value.lastErrorCode(), timestamp(value.createdAt()),
                        timestamp(value.updatedAt()), timestamp(value.activatedAt()));
            } catch (DuplicateKeyException exception) {
                return findOperationByRevision(
                        value.companyId(), value.workspaceId(), value.operationType(),
                        value.sourceRevision(), value.sourceDigest()).orElseThrow(() -> exception);
            }
        }
        return findOperation(value.companyId(), value.workspaceId(), value.operationId()).orElseThrow();
    }

    private String operationSelect() {
        return """
                SELECT operation_id, company_id, workspace_id, operation_type,
                       source_revision, source_digest, base_metadata_version_id,
                       candidate_metadata_version_id, changeset_id, subject_type, subject_id,
                       approval_request_id, status, risk_level, requires_backfill,
                       requested_by, approved_by, last_error_code,
                       created_at, updated_at, activated_at
                  FROM ontology_semattice_operation
                """;
    }

    private Binding binding(ResultSet row) throws SQLException {
        return new Binding(
                row.getString("company_id"), row.getLong("workspace_id"),
                row.getString("semattice_tenant_id"), row.getString("active_metadata_version_id"),
                nullableLong(row, "active_sequence"), row.getString("active_digest"),
                row.getString("sync_status"), row.getString("last_error_code"),
                instant(row, "last_checked_at"), instant(row, "created_at"), instant(row, "updated_at"));
    }

    private ElementBinding element(ResultSet row) throws SQLException {
        return new ElementBinding(
                row.getString("company_id"), row.getLong("workspace_id"),
                row.getString("element_type"), row.getString("element_key"),
                row.getString("semattice_element_id"), row.getString("semattice_api_name"),
                row.getLong("first_bound_revision"), row.getLong("last_synced_revision"),
                row.getString("source_digest"), row.getString("status"),
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    private Operation operation(ResultSet row) throws SQLException {
        return new Operation(
                row.getString("operation_id"), row.getString("company_id"),
                row.getLong("workspace_id"), row.getString("operation_type"),
                row.getLong("source_revision"), row.getString("source_digest"),
                row.getString("base_metadata_version_id"),
                row.getString("candidate_metadata_version_id"), row.getString("changeset_id"),
                row.getString("subject_type"), row.getString("subject_id"),
                row.getString("approval_request_id"), row.getString("status"),
                row.getString("risk_level"), row.getBoolean("requires_backfill"),
                row.getString("requested_by"), row.getString("approved_by"),
                row.getString("last_error_code"), instant(row, "created_at"),
                instant(row, "updated_at"), instant(row, "activated_at"));
    }

    private Instant instant(ResultSet row, String field) throws SQLException {
        Timestamp value = row.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private Long nullableLong(ResultSet row, String field) throws SQLException {
        long value = row.getLong(field);
        return row.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
