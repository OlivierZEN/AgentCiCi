package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SemanticQueryAuditIntegrationTest {

    @Autowired
    private SemanticQueryService queries;

    @Autowired
    private OntologyTenantPersistence persistence;

    @Autowired
    private OntologyQueryAuditRepository audits;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orgId;

    @AfterEach
    void cleanUp() {
        if (orgId != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM ontology_query_audit WHERE org_id = ?", orgId);
                jdbcTemplate.update("DELETE FROM ontology_version WHERE org_id = ?", orgId);
                jdbcTemplate.update("DELETE FROM ontology_workspace WHERE org_id = ?", orgId);
            });
        }
        TenantContext.clear();
    }

    @Test
    void failedAuditCommitsEvenWhenCallerTransactionRollsBackAndRethrows() throws Exception {
        orgId = "org-audit-" + UUID.randomUUID();
        String ontologyKey = "delivery-" + UUID.randomUUID();
        TenantContext.setOrgId(orgId);
        TenantContext.setUserId("user-a");
        Long workspaceId = seedPublishedSnapshot(ontologyKey);
        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> callerTransaction.executeWithoutResult(status -> {
            try {
                queries.execute(orgId, "user-a", new SemanticQueryService.SemanticQuery(
                        ontologyKey,
                        1,
                        "task",
                        List.of("unknown"),
                        List.of(),
                        List.of(),
                        50));
            } catch (RuntimeException failure) {
                status.setRollbackOnly();
                throw failure;
            }
        })).hasMessage("QUERY_FIELD_UNKNOWN");

        List<OntologyQueryAuditEntity> persisted = new TransactionTemplate(transactionManager)
                .execute(status -> audits.findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(
                        workspaceId, orgId));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getStatus()).isEqualTo("FAILED");
        assertThat(persisted.getFirst().getErrorCode()).isEqualTo("QUERY_FIELD_UNKNOWN");
        assertThat(persisted.getFirst().getDataSourceId()).isNull();
        assertThat(persisted.getFirst().isSensitiveValuesRedacted()).isTrue();
    }

    private Long seedPublishedSnapshot(String ontologyKey) throws Exception {
        TransactionTemplate seed = new TransactionTemplate(transactionManager);
        return seed.execute(status -> {
            OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(
                    new OntologyWorkspaceEntity(
                            orgId, ontologyKey, "审计测试", "", "user-a"));
            OntologyDocument document = snapshot(ontologyKey);
            try {
                persistence.saveForCurrentOrg(new OntologyVersionEntity(
                        orgId,
                        workspace.getId(),
                        1,
                        1L,
                        "hash",
                        objectMapper.writeValueAsString(document),
                        "{}",
                        "type Query { task: Task }",
                        "{}",
                        "[]",
                        "user-a"));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return workspace.getId();
        });
    }

    private OntologyDocument snapshot(String ontologyKey) {
        OntologyDocument.Property name = new OntologyDocument.Property(
                "name", "名称", "", OntologyDocument.DataType.TEXT,
                true, false, false, true, List.of());
        OntologyDocument.Concept task = new OntologyDocument.Concept(
                "task", "任务", "任务", "", OntologyDocument.ConceptType.EVENT,
                "name", 0, 0, true, true, List.of(name));
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                11L,
                "inline",
                "内置样例",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                "{\"tasks\":[]}");
        return new OntologyDocument(
                ontologyKey,
                "审计测试",
                "",
                List.of(task),
                List.of(),
                List.of(),
                List.of(),
                List.of(source),
                List.of(
                        new OntologyDocument.Mapping(
                                "CONCEPT", "task", 11L, "tasks", null, null,
                                "DIRECT", 1.0, "MANUAL", "VALID"),
                        new OntologyDocument.Mapping(
                                "PROPERTY", "task.name", 11L, "tasks", "name", null,
                                "DIRECT", 1.0, "MANUAL", "VALID")));
    }
}
