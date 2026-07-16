package com.codehouse.ciciassistant.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

    @Test
    void scopesWorkspaceLookupToOrganization() {
        OntologyWorkspaceEntity saved = workspaces.save(
                new OntologyWorkspaceEntity(
                        "org-a",
                        "project-delivery",
                        "项目交付",
                        "通用性样例",
                        "user-a"));

        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-a")).isPresent();
        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-b")).isEmpty();
    }

    @Test
    void ordersVersionsWithinWorkspaceAndScopesToOrganization() {
        OntologyWorkspaceEntity workspace = workspaces.save(
                new OntologyWorkspaceEntity(
                        "org-version-a",
                        "service-operations",
                        "服务运营",
                        "版本隔离样例",
                        "user-a"));
        versions.save(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 1, 1L, "hash-1", "{}",
                "{}", "type Query { ping: String }", "{}", "{}", "user-a"));
        versions.save(new OntologyVersionEntity(
                "org-version-a", workspace.getId(), 2, 2L, "hash-2", "{}",
                "{}", "type Query { pong: String }", "{}", "{}", "user-a"));

        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-version-a"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(2, 1);
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
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
                "{\"adapterKey\":\"delivery-api\"}");
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
        Set<String> dangerousMethods = Set.of("findById", "findAll", "deleteById", "deleteAll");

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
    void rejectsWorkspaceChildWhoseOrganizationDoesNotMatchItsParent() {
        OntologyWorkspaceEntity workspace = workspaces.save(new OntologyWorkspaceEntity(
                "org-parent-a", "delivery-a", "交付 A", "组织边界", "user-a"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_concept
                            (org_id, workspace_id, key, name, concept_type)
                        VALUES (?, ?, ?, ?, ?)
                        """, "org-child-b", workspace.getId(), "task", "任务", "EVENT"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsNestedPropertyWhoseWorkspaceDoesNotMatchItsConcept() {
        OntologyWorkspaceEntity workspaceA = workspaces.save(new OntologyWorkspaceEntity(
                "org-nested", "delivery-a", "交付 A", "父工作区", "user-a"));
        OntologyWorkspaceEntity workspaceB = workspaces.save(new OntologyWorkspaceEntity(
                "org-nested", "delivery-b", "交付 B", "伪造子工作区", "user-a"));
        Long conceptId = jdbcTemplate.queryForObject("""
                        INSERT INTO ontology_concept
                            (org_id, workspace_id, key, name, concept_type)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """, Long.class,
                "org-nested", workspaceA.getId(), "task", "任务", "EVENT");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO ontology_property
                            (org_id, workspace_id, concept_id, key, name, data_type)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, "org-nested", workspaceB.getId(), conceptId,
                "status", "状态", "ENUM"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void publishedVersionPreventsWorkspaceDeletion() {
        OntologyWorkspaceEntity workspace = workspaces.save(new OntologyWorkspaceEntity(
                "org-version-retention", "delivery", "交付", "保留发布版本", "user-a"));
        versions.save(new OntologyVersionEntity(
                "org-version-retention", workspace.getId(), 1, 1L, "hash-1", "{}",
                "{}", "type Query { ping: String }", "{}", "{}", "user-a"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ontology_workspace WHERE id = ?", workspace.getId()))
                .isInstanceOf(DataAccessException.class);
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
                    "FOREIGN KEY (workspace_id, org_id) REFERENCES ontology_workspace(id, org_id)");
        }

        assertForeignKey("ontology_property",
                "FOREIGN KEY (concept_id, workspace_id, org_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, org_id)");
        assertForeignKey("ontology_relation",
                "FOREIGN KEY (source_concept_id, workspace_id, org_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, org_id)");
        assertForeignKey("ontology_relation",
                "FOREIGN KEY (target_concept_id, workspace_id, org_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, org_id)");
        assertForeignKey("ontology_metric",
                "FOREIGN KEY (concept_id, workspace_id, org_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, org_id)");
        assertForeignKey("ontology_action",
                "FOREIGN KEY (concept_id, workspace_id, org_id) "
                        + "REFERENCES ontology_concept(id, workspace_id, org_id)");
        assertForeignKey("ontology_physical_object",
                "FOREIGN KEY (data_source_id, workspace_id, org_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, org_id)");
        assertForeignKey("ontology_physical_field",
                "FOREIGN KEY (physical_object_id, workspace_id, org_id) "
                        + "REFERENCES ontology_physical_object(id, workspace_id, org_id)");
        assertForeignKey("ontology_mapping",
                "FOREIGN KEY (data_source_id, workspace_id, org_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, org_id)");
        assertForeignKey("ontology_query_audit",
                "FOREIGN KEY (version_id, workspace_id, org_id) "
                        + "REFERENCES ontology_version(id, workspace_id, org_id)");
        assertForeignKey("ontology_query_audit",
                "FOREIGN KEY (data_source_id, workspace_id, org_id) "
                        + "REFERENCES ontology_data_source(id, workspace_id, org_id)");

        assertThat(foreignKeys("ontology_version"))
                .anySatisfy(definition -> assertThat(definition)
                        .contains("FOREIGN KEY (workspace_id, org_id) "
                                + "REFERENCES ontology_workspace(id, org_id) ON DELETE RESTRICT"));
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
