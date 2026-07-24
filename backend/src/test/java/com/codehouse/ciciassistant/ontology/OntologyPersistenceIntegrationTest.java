package com.codehouse.ciciassistant.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OntologyPersistenceIntegrationTest {

    @Autowired
    private OntologyWorkspaceRepository workspaces;

    @Autowired
    private OntologyVersionRepository versions;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OntologyTenantPersistence persistence;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void scopesWorkspaceLookupToCompany() {
        TenantContext.setCompanyId("org-a");
        OntologyWorkspaceEntity saved = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        "org-a",
                        "project-delivery",
                        "项目交付",
                        "通用性样例",
                        "user-a"));

        assertThat(workspaces.findByIdAndCompanyId(saved.getId(), "org-a")).isPresent();
        assertThat(workspaces.findByIdAndCompanyId(saved.getId(), "org-b")).isEmpty();
    }

    @Test
    void persistsManualAndReferencePackageWorkspaceProvenance() {
        TenantContext.setCompanyId("org-provenance");
        OntologyWorkspaceEntity manual = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        "org-provenance",
                        "manual-delivery",
                        "手工交付",
                        "手工创建",
                        "user-a"));
        String fingerprint = "a".repeat(64);
        OntologyWorkspaceEntity reference = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        "org-provenance",
                        "project-delivery",
                        "项目交付",
                        "参考包创建",
                        "user-a",
                        "REFERENCE_PACKAGE",
                        "project-delivery",
                        fingerprint));
        persistence.flushForCurrentOrg("org-provenance");

        assertThat(manual.getCreationSource()).isEqualTo("MANUAL");
        assertThat(manual.getReferencePackageId()).isNull();
        assertThat(manual.getReferencePackageFingerprint()).isNull();
        assertThat(reference.getCreationSource()).isEqualTo("REFERENCE_PACKAGE");
        assertThat(reference.getReferencePackageId()).isEqualTo("project-delivery");
        assertThat(reference.getReferencePackageFingerprint()).isEqualTo(fingerprint);
        assertThat(jdbcTemplate.queryForMap("""
                        SELECT creation_source, reference_package_id,
                               reference_package_fingerprint
                        FROM ontology_workspace
                        WHERE id = ?
                        """, reference.getId()))
                .containsEntry("creation_source", "REFERENCE_PACKAGE")
                .containsEntry("reference_package_id", "project-delivery")
                .containsEntry("reference_package_fingerprint", fingerprint);
    }

    @Test
    void provenanceMigrationKeepsThirteenTablesAndRejectsManualPackageReferences() {
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name LIKE 'ontology_%'
                        """, Integer.class))
                .isEqualTo(13);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_workspace
                            (company_id, key, name, creation_source,
                             reference_package_id, reference_package_fingerprint,
                             created_by, updated_by)
                        VALUES (?, ?, ?, 'MANUAL', ?, NULL, ?, ?)
                        """,
                        "org-invalid-provenance",
                        "manual-with-package",
                        "非法来源",
                        "project-delivery",
                        "user-a",
                        "user-a"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void provenanceMigrationRejectsReferencePackageWithEmptyPackageId() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_workspace
                            (company_id, key, name, creation_source,
                             reference_package_id, reference_package_fingerprint,
                             created_by, updated_by)
                        VALUES (?, ?, ?, 'REFERENCE_PACKAGE', '', ?, ?, ?)
                        """,
                        "org-invalid-provenance",
                        "reference-with-empty-id",
                        "非法参考包 ID",
                        "a".repeat(64),
                        "user-a",
                        "user-a"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void provenanceMigrationRejectsReferencePackageWithShortFingerprint() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_workspace
                            (company_id, key, name, creation_source,
                             reference_package_id, reference_package_fingerprint,
                             created_by, updated_by)
                        VALUES (?, ?, ?, 'REFERENCE_PACKAGE', ?, ?, ?, ?)
                        """,
                        "org-invalid-provenance",
                        "reference-with-short-fingerprint",
                        "非法参考包指纹",
                        "project-delivery",
                        "a".repeat(63),
                        "user-a",
                        "user-a"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void provenanceMigrationRejectsReferencePackageWithUppercaseFingerprint() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_workspace
                            (company_id, key, name, creation_source,
                             reference_package_id, reference_package_fingerprint,
                             created_by, updated_by)
                        VALUES (?, ?, ?, 'REFERENCE_PACKAGE', ?, ?, ?, ?)
                        """,
                        "org-invalid-provenance",
                        "reference-with-uppercase-fingerprint",
                        "非法参考包指纹",
                        "project-delivery",
                        "A".repeat(64),
                        "user-a",
                        "user-a"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ordersVersionsWithinWorkspaceAndScopesToCompany() {
        TenantContext.setCompanyId("org-version-a");
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        "org-version-a",
                        "service-operations",
                        "服务运营",
                        "版本隔离样例",
                        "user-a"));
        persistence.saveForCurrentOrg(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 1, 1L, "hash-1", "{}",
                "{}", "type Query { ping: String }", "{}", "{}", "user-a"));
        persistence.saveForCurrentOrg(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 2, 2L, "hash-2", "{}",
                "{}", "type Query { pong: String }", "{}", "{}", "user-a"));

        assertThat(versions.findByWorkspaceIdAndCompanyIdOrderByVersionNoDesc(
                workspace.getId(), "org-version-a"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(2, 1);
        assertThat(versions.findByWorkspaceIdAndCompanyIdOrderByVersionNoDesc(
                workspace.getId(), "org-version-b"))
                .isEmpty();
    }

    @Test
    void preservesTheDomainNeutralDocumentContractThroughJson() throws Exception {
        OntologyDocument.SourceType connector = Arrays.stream(OntologyDocument.SourceType.values())
                .filter(value -> value.name().equals("CONNECTOR"))
                .findFirst()
                .orElse(null);
        assertThat(connector).as("the core source type must stay vendor-neutral").isNotNull();
        assertThat(Arrays.stream(OntologyDocument.SourceType.values()).map(Enum::name))
                .containsExactly("INLINE_SAMPLE", "CONNECTOR");

        OntologyDocument.Property projectName = new OntologyDocument.Property(
                "name", "项目名称", "用于识别项目", OntologyDocument.DataType.TEXT,
                true, false, false, true, List.of());
        OntologyDocument.Property taskStatus = new OntologyDocument.Property(
                "status", "任务状态", "当前交付状态", OntologyDocument.DataType.ENUM,
                true, false, false, true, List.of("PLANNED", "ACTIVE", "DONE"));
        OntologyDocument.Concept project = new OntologyDocument.Concept(
                "project", "项目", "项目", "交付项目", OntologyDocument.ConceptType.ENTITY,
                "name", 120, 120, true, true, List.of(projectName));
        OntologyDocument.Concept task = new OntologyDocument.Concept(
                "task", "任务", "任务", "项目任务", OntologyDocument.ConceptType.EVENT,
                "status", 360, 120, true, true, List.of(taskStatus));
        OntologyDocument.Relation containsTask = new OntologyDocument.Relation(
                "contains-task", "包含任务", "项目包含任务", "project", "task",
                OntologyDocument.Cardinality.ONE_TO_MANY, "包含", "属于", true, true);
        OntologyDocument.QueryFilter activeFilter = new OntologyDocument.QueryFilter(
                "status", OntologyDocument.Operator.IN, List.of("ACTIVE", "DONE"));
        OntologyDocument.Metric activeTasks = new OntologyDocument.Metric(
                "active-task-count", "活跃任务数", "task", OntologyDocument.Aggregation.COUNT,
                null, List.of("status"), null, List.of(activeFilter));
        OntologyDocument.Action assignOwner = new OntologyDocument.Action(
                "assign-owner", "分配负责人", "task", "定义动作契约，不执行写回",
                List.of(new OntologyDocument.ActionParameter(
                        "owner", "负责人", OntologyDocument.DataType.REFERENCE, true)));
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                1L, "delivery-source", "交付数据", connector,
                "{\"adapterKey\":\"delivery-api\"}", null);
        OntologyDocument.Mapping statusMapping = new OntologyDocument.Mapping(
                "PROPERTY", "task.status", 1L, "tasks", "state", null,
                "ENUM_MAP", 0.95, "MANUAL", "VALID");
        OntologyDocument document = new OntologyDocument(
                "project-delivery",
                "项目交付",
                "通用性样例",
                List.of(project, task),
                List.of(containsTask),
                List.of(activeTasks),
                List.of(assignOwner),
                List.of(source),
                List.of(statusMapping));

        String json = objectMapper.writeValueAsString(document);

        assertThat(objectMapper.readValue(json, OntologyDocument.class)).isEqualTo(document);
    }

    @Test
    void repositoriesDoNotExposeUnscopedCrudOperations() {
        List<Class<?>> repositoryTypes = List.of(
                OntologyWorkspaceRepository.class,
                OntologyConceptRepository.class,
                OntologyPropertyRepository.class,
                OntologyRelationRepository.class,
                OntologyMetricRepository.class,
                OntologyActionRepository.class,
                OntologyDataSourceRepository.class,
                OntologyPhysicalObjectRepository.class,
                OntologyPhysicalFieldRepository.class,
                OntologyMappingRepository.class,
                OntologyAiProposalRepository.class,
                OntologyVersionRepository.class,
                OntologyQueryAuditRepository.class);
        Set<String> dangerousMethods = Set.of(
                "save", "findById", "findAll", "deleteById", "deleteAll");

        for (Class<?> repositoryType : repositoryTypes) {
            assertThat(CrudRepository.class.isAssignableFrom(repositoryType))
                    .as("%s must only extend the Spring Data marker", repositoryType.getSimpleName())
                    .isFalse();
            assertThat(Arrays.stream(repositoryType.getMethods())
                    .map(method -> method.getName())
                    .filter(dangerousMethods::contains))
                    .as("%s must not expose unscoped CRUD", repositoryType.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void rejectsWorkspaceChildWhoseCompanyDoesNotMatchItsParent() {
        TenantContext.setCompanyId("org-parent-a");
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(new OntologyWorkspaceEntity(
                "org-parent-a", "delivery-a", "交付 A", "组织边界", "user-a"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_concept
                            (company_id, workspace_id, key, name, concept_type)
                        VALUES (?, ?, ?, ?, ?)
                        """, "org-child-b", workspace.getId(), "task", "任务", "EVENT"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsNestedPropertyWhoseWorkspaceDoesNotMatchItsConcept() {
        TenantContext.setCompanyId("org-nested");
        OntologyWorkspaceEntity workspaceA = persistence.saveForCurrentOrg(new OntologyWorkspaceEntity(
                "org-nested", "delivery-a", "交付 A", "父工作区", "user-a"));
        OntologyWorkspaceEntity workspaceB = persistence.saveForCurrentOrg(new OntologyWorkspaceEntity(
                "org-nested", "delivery-b", "交付 B", "伪造子工作区", "user-a"));
        Long conceptId = jdbcTemplate.queryForObject("""
                        INSERT INTO ontology_concept
                            (company_id, workspace_id, key, name, concept_type)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """, Long.class,
                "org-nested", workspaceA.getId(), "task", "任务", "EVENT");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_property
                            (company_id, workspace_id, concept_id, key, name, data_type)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, "org-nested", workspaceB.getId(), conceptId,
                "status", "状态", "ENUM"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void publishedVersionPreventsWorkspaceDeletion() {
        TenantContext.setCompanyId("org-version-retention");
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(new OntologyWorkspaceEntity(
                "org-version-retention", "delivery", "交付", "保留发布版本", "user-a"));
        persistence.saveForCurrentOrg(new OntologyVersionEntity(
                "org-version-retention", workspace.getId(), 1, 1L, "hash-1", "{}",
                "{}", "type Query { ping: String }", "{}", "{}", "user-a"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ontology_workspace WHERE id = ?", workspace.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsEntityFromAnotherCompanyWithoutWritingIt() {
        TenantContext.setCompanyId("org-write-a");
        OntologyWorkspaceEntity foreignWorkspace = new OntologyWorkspaceEntity(
                "org-write-b", "forbidden-write", "越界写入", "不应持久化", "user-a");

        assertThatThrownBy(() -> persistence.saveForCurrentOrg(foreignWorkspace))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("company");
        assertThat(workspaces.findByCompanyIdAndKey("org-write-b", "forbidden-write")).isEmpty();
    }

    @Test
    void rejectsCrossCompanyScopedDeleteBeforeInvokingTheDelete() {
        TenantContext.setCompanyId("org-delete-a");
        AtomicBoolean invoked = new AtomicBoolean();

        assertThatThrownBy(() -> persistence.deleteForCurrentOrg(
                "org-delete-b", () -> invoked.set(true)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("company");

        assertThat(invoked).isFalse();
    }

    @Test
    void persistsEntityOnlyForTheCurrentCompany() {
        TenantContext.setCompanyId("org-write-a");
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-write-a", "allowed-write", "同租户写入", "允许持久化", "user-a");

        OntologyWorkspaceEntity saved = persistence.saveForCurrentOrg(workspace);

        assertThat(saved.getId()).isNotNull();
        assertThat(workspaces.findByIdAndCompanyId(saved.getId(), "org-write-a")).contains(saved);
    }

    @Test
    void schemaUsesCompositeTenantForeignKeysForAllNestedResources() {
        List<String> workspaceChildren = List.of(
                "ontology_concept",
                "ontology_property",
                "ontology_relation",
                "ontology_metric",
                "ontology_action",
                "ontology_data_source",
                "ontology_physical_object",
                "ontology_physical_field",
                "ontology_mapping",
                "ontology_ai_proposal",
                "ontology_version",
                "ontology_query_audit");
        for (String table : workspaceChildren) {
            assertForeignKey(table,
                    "FOREIGN KEY (workspace_id, company_id) REFERENCES ontology_workspace(id, company_id)");
        }

        assertForeignKey("ontology_property",
                "FOREIGN KEY (concept_id, workspace_id, company_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, company_id)");
        assertForeignKey("ontology_relation",
                "FOREIGN KEY (source_concept_id, workspace_id, company_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, company_id)");
        assertForeignKey("ontology_relation",
                "FOREIGN KEY (target_concept_id, workspace_id, company_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, company_id)");
        assertForeignKey("ontology_metric",
                "FOREIGN KEY (concept_id, workspace_id, company_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, company_id)");
        assertForeignKey("ontology_action",
                "FOREIGN KEY (concept_id, workspace_id, company_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, company_id)");
        assertForeignKey("ontology_physical_object",
                "FOREIGN KEY (data_source_id, workspace_id, company_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, company_id)");
        assertForeignKey("ontology_physical_field",
                "FOREIGN KEY (physical_object_id, workspace_id, company_id) "
                        + "REFERENCES ontology_physical_object(id, workspace_id, company_id)");
        assertForeignKey("ontology_mapping",
                "FOREIGN KEY (data_source_id, workspace_id, company_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, company_id)");
        assertForeignKey("ontology_query_audit",
                "FOREIGN KEY (version_id, workspace_id, company_id) "
                        + "REFERENCES ontology_version(id, workspace_id, company_id)");
        assertForeignKey("ontology_query_audit",
                "FOREIGN KEY (data_source_id, workspace_id, company_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, company_id)");

        assertThat(foreignKeys("ontology_version"))
                .anySatisfy(definition -> assertThat(definition)
                        .contains("FOREIGN KEY (workspace_id, company_id) "
                                + "REFERENCES ontology_workspace(id, company_id) ON DELETE RESTRICT"));
    }

    private void assertForeignKey(String table, String expectedDefinition) {
        assertThat(foreignKeys(table))
                .as("%s must enforce %s", table, expectedDefinition)
                .anySatisfy(definition -> assertThat(definition).contains(expectedDefinition));
    }

    private List<String> foreignKeys(String table) {
        return jdbcTemplate.queryForList("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = CAST(? AS regclass)
                  AND contype = 'f'
                ORDER BY conname
                """, String.class, table);
    }
}
