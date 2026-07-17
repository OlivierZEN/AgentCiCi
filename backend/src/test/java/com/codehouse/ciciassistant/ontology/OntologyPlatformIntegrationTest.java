package com.codehouse.ciciassistant.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingKey;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingPreparation;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.SourcePreparation;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class OntologyPlatformIntegrationTest {

    private static final List<String> DELETE_ORDER = List.of(
            "ontology_query_audit",
            "ontology_version",
            "ontology_ai_proposal",
            "ontology_mapping",
            "ontology_physical_field",
            "ontology_physical_object",
            "ontology_data_source",
            "ontology_property",
            "ontology_relation",
            "ontology_metric",
            "ontology_action",
            "ontology_concept",
            "ontology_workspace");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OntologyManagementService management;

    @Autowired
    private OntologyCatalogTransactionService catalogTransactions;

    @Autowired
    private OntologyMappingRepository mappingRepository;

    @SpyBean
    private OntologyWorkspaceRepository workspaceRepository;

    private String runPrefix;
    private String orgA;
    private String orgB;
    private String ownerAToken;
    private String memberAToken;
    private String ownerBToken;
    private String platformToken;

    @BeforeEach
    void setUp() {
        runPrefix = "ontology-api-" + UUID.randomUUID();
        orgA = runPrefix + "-a";
        orgB = runPrefix + "-b";
        ownerAToken = organizationToken(orgA, "owner-a", RoleCodes.OWNER);
        memberAToken = organizationToken(orgA, "member-a", RoleCodes.ORG_USER);
        ownerBToken = organizationToken(orgB, "owner-b", RoleCodes.OWNER);
        platformToken = jwtService.issuePlatformToken("platform-a", List.of(RoleCodes.PLATFORM_ADMIN));
    }

    @AfterEach
    void cleanUp() {
        for (String table : DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE org_id LIKE ?", runPrefix + "%");
        }
    }

    @Test
    void protectsManagementRoutesAndScopesWorkspaceLookup() throws Exception {
        mockMvc.perform(get("/admin/ontologies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "project-delivery",
                                  "name": "项目交付",
                                  "description": "通用样例"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("project-delivery"))
                .andExpect(jsonPath("$.data.createdBy").value("owner-a"))
                .andExpect(jsonPath("$.data.draftRevision").value(0))
                .andReturn();
        long workspaceId = data(created).path("id").asLong();

        mockMvc.perform(get("/admin/ontologies/{workspaceId}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_NOT_FOUND"));
    }

    @Test
    void rejectsOversizedWorkspaceMetadataBeforeCreatingAnyOntologyRows() throws Exception {
        ObjectNode request = objectMapper.createObjectNode()
                .put("key", "oversized-workspace")
                .put("name", "超大工作区")
                .put("description", "x".repeat(65_537));

        mockMvc.perform(post("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        for (String table : DELETE_ORDER) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE org_id = ?",
                    Long.class,
                    orgA)).as(table).isZero();
        }
    }

    @Test
    void concurrentWorkspaceCreatesReturnOneStableKeyConflictAndPersistOneRow() throws Exception {
        OntologyManagementService.WorkspaceCreateRequest request =
                new OntologyManagementService.WorkspaceCreateRequest(
                        "concurrent-create", "并发创建", "并发唯一键合同");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothPrechecksComplete = new CountDownLatch(2);
        AtomicInteger guardedPrechecks = new AtomicInteger();
        doAnswer(invocation -> {
            String requestedOrg = invocation.getArgument(0);
            String requestedKey = invocation.getArgument(1);
            if (orgA.equals(requestedOrg) && request.key().equals(requestedKey)) {
                if (guardedPrechecks.getAndIncrement() >= 2) {
                    throw new IllegalStateException("unexpected extra guarded workspace precheck");
                }
                bothPrechecksComplete.countDown();
                if (!bothPrechecksComplete.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("workspace create precheck barrier timed out");
                }
                return Optional.empty();
            }
            throw new IllegalStateException("unexpected workspace lookup in guarded create test");
        }).when(workspaceRepository).findByOrgIdAndKey(orgA, request.key());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> first = concurrentWorkspaceCreate(
                    executor, ready, start, request);
            CompletableFuture<MvcResult> second = concurrentWorkspaceCreate(
                    executor, ready, start, request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(
                    firstResult.getResponse().getStatus(),
                    secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
            MvcResult conflictResult = firstResult.getResponse().getStatus() == 409
                    ? firstResult
                    : secondResult;
            JsonNode conflict = objectMapper.readTree(
                    conflictResult.getResponse().getContentAsByteArray());
            assertThat(conflict.path("success").asBoolean()).isFalse();
            assertThat(conflict.path("data").isNull()).isTrue();
            assertThat(conflict.path("message").asText())
                    .isEqualTo("ONTOLOGY_KEY_CONFLICT");
            assertThat(conflict.path("code").asText())
                    .isEqualTo("ONTOLOGY_KEY_CONFLICT");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_workspace WHERE org_id = ? AND key = ?",
                Long.class,
                orgA,
                request.key())).isEqualTo(1L);

    }

    @Test
    void semanticQueryRequiresAnOrganizationMemberButNotAnAdministrator() throws Exception {
        mockMvc.perform(post("/semantic-query/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/semantic-query/explain")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/semantic-query/explain")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_CONTRACT_INVALID"));
    }

    @Test
    void semanticQueryReturnsStableForbiddenContractForSensitiveProperties() throws Exception {
        long workspaceId = createEmptyWorkspace("sensitive-contract", "敏感字段合同");
        OntologyDocument.Property secret = new OntologyDocument.Property(
                "secret-note", "私密备注", null, OntologyDocument.DataType.TEXT,
                false, false, true, false, List.of());
        OntologyDocument.Concept customer = new OntologyDocument.Concept(
                "customer", "客户", "客户", null, OntologyDocument.ConceptType.ENTITY,
                "secret-note", 0, 0, true, true, List.of(secret));
        OntologyDocument snapshot = new OntologyDocument(
                "sensitive-contract", "敏感字段合同", null,
                List.of(customer), List.of(), List.of(), List.of(),
                List.of(new OntologyDocument.DataSource(
                        999L, "sample", "样例", OntologyDocument.SourceType.INLINE_SAMPLE,
                        "{}", "{\"customers\":[]}")),
                List.of(
                        new OntologyDocument.Mapping(
                                "CONCEPT", "customer", 999L, "customers", null, null,
                                "DIRECT", 1, "MANUAL", "VALID"),
                        new OntologyDocument.Mapping(
                                "PROPERTY", "customer.secret-note", 999L,
                                "customers", "secret_note", null,
                                "DIRECT", 1, "MANUAL", "VALID")));
        jdbcTemplate.update("""
                        INSERT INTO ontology_version(
                            org_id, workspace_id, version_no, source_draft_revision,
                            content_hash, snapshot_json, json_schema, graphql_sdl,
                            query_contract_json, validation_summary_json, published_by
                        ) VALUES (?, ?, 1, 0, ?, ?, '{}', 'type Query { noop: String }', '{}', '[]', ?)
                        """,
                orgA, workspaceId, "sensitive-contract-hash",
                objectMapper.writeValueAsString(snapshot), "owner-a");
        jdbcTemplate.update("""
                        UPDATE ontology_workspace
                        SET status = 'PUBLISHED', published_version = 1
                        WHERE id = ? AND org_id = ?
                        """,
                workspaceId, orgA);

        mockMvc.perform(post("/semantic-query/explain")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ontologyKey":"sensitive-contract",
                                  "version":1,
                                  "concept":"customer",
                                  "select":["secret-note"],
                                  "filters":[],
                                  "orderBy":[],
                                  "limit":10
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SENSITIVE_PROPERTY_FORBIDDEN"));
    }

    @Test
    void rejectsOversizedDraftBeforeAnyRowsOrRevisionAreChanged() throws Exception {
        long workspaceId = createEmptyWorkspace("oversized-draft", "超大草稿");
        ObjectNode document = emptyDocument("oversized-draft", "超大草稿");
        ArrayNode concepts = (ArrayNode) document.path("concepts");
        for (int index = 0; index < 101; index++) {
            concepts.add(conceptNode("concept-" + index, "概念" + index));
        }
        ObjectNode request = objectMapper.createObjectNode()
                .put("expectedRevision", 0)
                .set("document", document);

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM ontology_workspace WHERE id = ?",
                Long.class,
                workspaceId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_concept WHERE workspace_id = ?",
                Long.class,
                workspaceId)).isZero();
    }

    @Test
    void rejectsNullEnumValuesBeforeAnyRowsOrRevisionAreChanged() throws Exception {
        long workspaceId = createEmptyWorkspace("null-enum", "空枚举草稿");
        ObjectNode document = emptyDocument("null-enum", "空枚举草稿");
        ObjectNode concept = conceptNode("customer", "客户");
        ObjectNode property = objectMapper.createObjectNode()
                .put("key", "status")
                .put("name", "状态")
                .put("description", "客户状态")
                .put("dataType", "ENUM")
                .put("required", true)
                .put("multiple", false)
                .put("sensitive", false)
                .put("queryable", true)
                .putNull("enumValues");
        ((ArrayNode) concept.path("properties")).add(property);
        concept.put("displayPropertyKey", "status");
        ((ArrayNode) document.path("concepts")).add(concept);
        ObjectNode request = objectMapper.createObjectNode()
                .put("expectedRevision", 0)
                .set("document", document);

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM ontology_workspace WHERE id = ?",
                Long.class,
                workspaceId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_property WHERE workspace_id = ?",
                Long.class,
                workspaceId)).isZero();
    }

    @Test
    void rejectsOversizedMappingReplacementBeforeRevisionChanges() throws Exception {
        long workspaceId = createEmptyWorkspace("oversized-mappings", "超大映射");
        ObjectNode request = objectMapper.createObjectNode().put("expectedRevision", 0);
        ArrayNode mappings = request.putArray("mappings");
        for (int index = 0; index < 5_001; index++) {
            mappings.addObject()
                    .put("targetType", "PROPERTY")
                    .put("targetKey", "entity.field-" + index)
                    .put("dataSourceId", 1)
                    .put("physicalObjectKey", "entities")
                    .put("physicalFieldKey", "field-" + index)
                    .putNull("relationTargetFieldKey")
                    .put("transform", "DIRECT")
                    .put("confidence", 1);
        }

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/mappings", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM ontology_workspace WHERE id = ?",
                Long.class,
                workspaceId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_mapping WHERE workspace_id = ?",
                Long.class,
                workspaceId)).isZero();
    }

    @Test
    void installsReferencePackageWithoutEchoingConfigOrSampleRecords() throws Exception {
        mockMvc.perform(get("/admin/ontologies/reference-packages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 'project-delivery')]").exists())
                .andExpect(jsonPath("$.data[?(@.id == 'customer-operations')]").exists());

        MvcResult installed = mockMvc.perform(post(
                        "/admin/ontologies/reference-packages/{packageId}/install",
                        "project-delivery")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("project-delivery"))
                .andExpect(jsonPath("$.data.draftRevision").value(1))
                .andReturn();
        long workspaceId = data(installed).path("id").asLong();

        MvcResult draft = mockMvc.perform(get(
                        "/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources[0].adapterKey").doesNotExist())
                .andExpect(jsonPath("$.data.sources[0].sample.rowCount").value(7))
                .andExpect(jsonPath("$.data.document.dataSources[0].configJson").doesNotExist())
                .andExpect(jsonPath("$.data.document.dataSources[0].sampleDataJson").doesNotExist())
                .andReturn();
        assertThat(draft.getResponse().getContentAsString())
                .doesNotContain("完成本体设计")
                .doesNotContain("语义平台一期");

        JsonNode draftData = objectMapper.readTree(
                draft.getResponse().getContentAsString()).path("data");
        com.fasterxml.jackson.databind.node.ObjectNode roundTrip =
                objectMapper.createObjectNode();
        roundTrip.put("expectedRevision", 1);
        roundTrip.set("document", draftData.path("document"));
        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(roundTrip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(2))
                .andExpect(jsonPath("$.data.sources[0].sample.rowCount").value(7));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sample_data_json FROM ontology_data_source WHERE workspace_id = ?",
                String.class,
                workspaceId))
                .contains("完成本体设计", "语义平台一期");

        jdbcTemplate.update("""
                        INSERT INTO ontology_ai_proposal(
                            org_id, workspace_id, proposal_type, status, instruction,
                            payload_json, diff_json, validation_json, created_by
                        ) VALUES (?, ?, 'REFINE', 'PENDING', 'pending', '{}', ?, '[]', 'owner-a')
                        """,
                orgA,
                workspaceId,
                "{\"baseRevision\":2,\"candidateHash\":\"\",\"added\":[],\"changed\":[],\"removed\":[]}");
        jdbcTemplate.update("""
                        INSERT INTO ontology_ai_proposal(
                            org_id, workspace_id, proposal_type, status, instruction,
                            payload_json, diff_json, validation_json, created_by
                        ) VALUES (?, ?, 'REFINE', 'FAILED', 'failed', '{}', ?, ?, 'owner-a')
                        """,
                orgA,
                workspaceId,
                "{\"baseRevision\":2,\"candidateHash\":\"\",\"added\":[],\"changed\":[],\"removed\":[]}",
                "{\"code\":\"AI_MODEL_UNAVAILABLE\",\"diagnostic\":\"MODEL_REQUEST_FAILED\"}");
        mockMvc.perform(get("/admin/ontologies/{workspaceId}/proposals", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.status == 'PENDING')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'FAILED')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'FAILED')].validation[0].code")
                        .value("AI_MODEL_UNAVAILABLE"));
    }

    @Test
    void keepsWorkspaceKeyImmutableAndBlocksArchivedDraftWrites() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"immutable-key","name":"不可变标识","description":"测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long workspaceId = data(created).path("id").asLong();

        mockMvc.perform(patch("/admin/ontologies/{workspaceId}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"renamed-key","name":"试图改名","description":"测试","expectedRevision":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_KEY_IMMUTABLE"));

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/archive", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedRevision": 0,
                                  "document": {
                                    "key":"immutable-key","name":"归档后修改","description":"测试",
                                    "concepts":[],"relations":[],"metrics":[],"actions":[],
                                    "dataSources":[],"mappings":[]
                                  }
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_WORKSPACE_ARCHIVED"));
    }

    @Test
    void compilePreviewRequiresTheExactDraftRevisionAndReturnsItsBinding() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"compile-binding","name":"编译绑定","description":"测试"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long workspaceId = data(created).path("id").asLong();

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedRevision": 0,
                                  "document": {
                                    "key":"compile-binding","name":"编译绑定","description":"测试",
                                    "concepts":[{
                                      "key":"project","name":"项目","pluralName":"项目",
                                      "description":"项目","conceptType":"ENTITY","displayPropertyKey":"name",
                                      "positionX":40,"positionY":40,"queryable":true,"enabled":true,
                                      "properties":[{
                                        "key":"name","name":"项目名称","description":"名称","dataType":"TEXT",
                                        "required":true,"multiple":false,"sensitive":false,"queryable":true,"enumValues":[]
                                      }]
                                    }],"relations":[],"metrics":[],"actions":[],
                                    "dataSources":[],"mappings":[]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(1));

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/compile-preview", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceDraftRevision").value(1));

        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedRevision": 1,
                                  "document": {
                                    "key":"compile-binding","name":"编译绑定 v2","description":"测试",
                                    "concepts":[{
                                      "key":"project","name":"项目","pluralName":"项目",
                                      "description":"项目","conceptType":"ENTITY","displayPropertyKey":"name",
                                      "positionX":40,"positionY":40,"queryable":true,"enabled":true,
                                      "properties":[{
                                        "key":"name","name":"项目名称","description":"名称","dataType":"TEXT",
                                        "required":true,"multiple":false,"sensitive":false,"queryable":true,"enumValues":[]
                                      }]
                                    }],"relations":[],"metrics":[],"actions":[],
                                    "dataSources":[],"mappings":[]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(2));

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/compile-preview", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_REVISION_CONFLICT"));
    }

    @Test
    void serializesCatalogCommitsAndWritesFreshServerValidationTimestamp() throws Exception {
        TenantContext.setOrgId(orgA);
        TenantContext.setUserId("owner-a");
        OntologyManagementService.WorkspaceView installed =
                management.installReferencePackage("owner-a", "project-delivery");
        long workspaceId = installed.id();
        long dataSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_data_source WHERE workspace_id = ?",
                Long.class,
                workspaceId);
        SourcePreparation firstPreparation = catalogTransactions.prepareSource(
                orgA, workspaceId, dataSourceId, 1L, null);
        SourcePreparation secondPreparation = catalogTransactions.prepareSource(
                orgA, workspaceId, dataSourceId, 1L, null);
        TenantContext.clear();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> first = concurrentCatalogCommit(
                    executor, ready, start, firstPreparation);
            CompletableFuture<String> second = concurrentCatalogCommit(
                    executor, ready, start, secondPreparation);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("COMMITTED", "CONFLICT");
        } finally {
            executor.shutdownNow();
        }

        TenantContext.setOrgId(orgA);
        TenantContext.setUserId("owner-a");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_revision FROM ontology_workspace WHERE id = ?",
                Long.class,
                workspaceId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology_mapping WHERE workspace_id = ? AND validation_status = 'PENDING'",
                Long.class,
                workspaceId)).isEqualTo(15L);

        SourcePreparation fieldPreparation = catalogTransactions.prepareSource(
                orgA, workspaceId, dataSourceId, 2L, "projects");
        catalogTransactions.commitFields(fieldPreparation, "owner-a", List.of(
                new PhysicalField("projects", "id", "编号", "text", false, false, "{}"),
                new PhysicalField("projects", "name", "名称", "text", false, false, "{}"),
                new PhysicalField("projects", "status", "状态", "text", false, false, "{}")));
        MappingKey key = new MappingKey("PROPERTY", "project.name", dataSourceId);
        MappingPreparation mappingPreparation = catalogTransactions.prepareMapping(
                orgA, workspaceId, 3L, key);
        OntologyCatalogTransactionService.MappingCommit validated =
                catalogTransactions.commitMappingValidation(
                        mappingPreparation, "owner-a", MappingValidation.success());

        assertThat(validated.validation().valid()).isTrue();
        assertThat(validated.revision()).isEqualTo(4L);
        OntologyMappingEntity mapping = mappingRepository
                .findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKeyAndDataSourceId(
                        workspaceId, orgA, "PROPERTY", "project.name", dataSourceId)
                .orElseThrow();
        java.time.Instant discoveredAt = jdbcTemplate.queryForObject(
                """
                        SELECT discovered_at
                        FROM ontology_physical_field
                        WHERE workspace_id = ? AND field_key = 'name'
                        """,
                java.time.Instant.class,
                workspaceId);
        assertThat(mapping.getValidationStatus()).isEqualTo("VALID");
        assertThat(mapping.getLastValidatedAt()).isAfterOrEqualTo(discoveredAt);
        TenantContext.clear();
    }

    @Test
    void installsDiscoversValidatesPublishesAndQueriesProjectReferencePackage() throws Exception {
        MvcResult installed = mockMvc.perform(post(
                        "/admin/ontologies/reference-packages/{packageId}/install",
                        "project-delivery")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andReturn();
        long workspaceId = data(installed).path("id").asLong();
        long sourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_data_source WHERE workspace_id = ?",
                Long.class,
                workspaceId);

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/publish", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        long revision = discoverObjects(workspaceId, sourceId, 1L);
        assertThat(revision).isEqualTo(2L);
        for (String objectKey : List.of("projects", "tasks", "owners")) {
            revision = discoverFields(workspaceId, sourceId, objectKey, revision);
        }
        assertThat(revision).isEqualTo(5L);

        MvcResult mappingResult = mockMvc.perform(get(
                        "/admin/ontologies/{workspaceId}/mappings", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(15))
                .andReturn();
        ArrayNode identities = objectMapper.createArrayNode();
        for (JsonNode mapping : data(mappingResult)) {
            identities.addObject()
                    .put("targetType", mapping.path("targetType").asText())
                    .put("targetKey", mapping.path("targetKey").asText())
                    .put("dataSourceId", mapping.path("dataSourceId").asLong());
        }
        ObjectNode validationRequest = objectMapper.createObjectNode();
        validationRequest.put("expectedRevision", revision);
        validationRequest.set("mappings", identities);
        MvcResult validated = mockMvc.perform(post(
                        "/admin/ontologies/{workspaceId}/mappings/validate", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(6))
                .andExpect(jsonPath("$.data.results.length()").value(15))
                .andExpect(jsonPath("$.data.results[?(@.valid == false)]").isEmpty())
                .andReturn();
        revision = data(validated).path("revision").asLong();

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/compile-preview", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.sourceDraftRevision").value(revision))
                .andExpect(jsonPath("$.data.contentHash").isNotEmpty())
                .andExpect(jsonPath("$.data.graphqlSdl").value(
                        org.hamcrest.Matchers.containsString("type Project")));

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/publish", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.sourceDraftRevision").value(6));

        mockMvc.perform(get("/admin/ontologies/{workspaceId}/draft/diff", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));

        MvcResult version = mockMvc.perform(get(
                        "/admin/ontologies/{workspaceId}/versions/{versionNo}", workspaceId, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.dataSources[0].configJson").doesNotExist())
                .andExpect(jsonPath("$.data.document.dataSources[0].sampleDataJson").doesNotExist())
                .andReturn();
        assertThat(version.getResponse().getContentAsString())
                .doesNotContain("完成本体设计")
                .doesNotContain("语义平台一期");

        String semanticQuery = """
                {
                  "ontologyKey":"project-delivery",
                  "version":1,
                  "concept":"project",
                  "select":["id","name","status","contains-task.title"],
                  "filters":[{"field":"status","operator":"EQ","value":"ACTIVE"}],
                  "orderBy":[{"field":"name","direction":"ASC"}],
                  "limit":10
                }
                """;
        mockMvc.perform(post("/semantic-query/explain")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semanticQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalObjectKey").value("projects"))
                .andExpect(jsonPath("$.data.relations[0].logicalRelation")
                        .value("contains-task"))
                .andExpect(jsonPath("$.data.relations[0].targetObject").value("tasks"));

        mockMvc.perform(post("/semantic-query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semanticQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].name").value("语义平台一期"))
                .andExpect(jsonPath("$.data.rows[0].contains-task.length()").value(2))
                .andExpect(jsonPath("$.data.evidence.ontologyVersion").value(1))
                .andExpect(jsonPath("$.data.evidence.totalCount").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT query_json FROM ontology_query_audit WHERE workspace_id = ?",
                String.class,
                workspaceId))
                .contains("REDACTED")
                .doesNotContain("ACTIVE");

        mockMvc.perform(post("/semantic-query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semanticQuery))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_NOT_FOUND"));

        MvcResult draft = mockMvc.perform(get(
                        "/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andReturn();
        ObjectNode reordered = data(draft).path("document").deepCopy();
        reverse((ArrayNode) reordered.path("concepts"));
        reverse((ArrayNode) reordered.path("mappings"));
        ObjectNode reorderRequest = objectMapper.createObjectNode()
                .put("expectedRevision", revision)
                .set("document", reordered);
        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(reorderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(revision + 1));
        mockMvc.perform(get("/admin/ontologies/{workspaceId}/draft/diff", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));

        reordered.put("description", "真实业务定义已变化");
        ObjectNode changedRequest = objectMapper.createObjectNode()
                .put("expectedRevision", revision + 1)
                .set("document", reordered);
        mockMvc.perform(put("/admin/ontologies/{workspaceId}/draft", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(changedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftRevision").value(revision + 2));
        mockMvc.perform(get("/admin/ontologies/{workspaceId}/draft/diff", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true));

        mockMvc.perform(post("/semantic-query/execute")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semanticQuery.replace("\"version\":1", "\"version\":99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_NOT_FOUND"));
    }

    @Test
    void customerOperationsReferenceRequiresLiveCatalogBeforePublish() throws Exception {
        MvcResult installed = mockMvc.perform(post(
                        "/admin/ontologies/reference-packages/{packageId}/install",
                        "customer-operations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk())
                .andReturn();
        long workspaceId = data(installed).path("id").asLong();

        mockMvc.perform(post("/admin/ontologies/{workspaceId}/publish", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_VALIDATION_FAILED"));

        long sourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM ontology_data_source WHERE workspace_id = ?",
                Long.class,
                workspaceId);
        mockMvc.perform(post(
                        "/admin/ontologies/{workspaceId}/data-sources/{sourceId}/discover-objects",
                        workspaceId,
                        sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DATA_SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("DATA_SOURCE_UNAVAILABLE"));
    }

    private String organizationToken(String orgId, String memberId, String role) {
        return jwtService.issueToken(memberId, Map.of(
                "org_id", orgId,
                "member_id", memberId,
                "roles", List.of(role)), 3600);
    }

    private long createEmptyWorkspace(String key, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/ontologies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("key", key)
                                .put("name", name)
                                .put("description", "结构安全测试")
                                .toString()))
                .andExpect(status().isOk())
                .andReturn();
        return data(created).path("id").asLong();
    }

    private ObjectNode emptyDocument(String key, String name) {
        ObjectNode document = objectMapper.createObjectNode()
                .put("key", key)
                .put("name", name)
                .put("description", "结构安全测试");
        document.putArray("concepts");
        document.putArray("relations");
        document.putArray("metrics");
        document.putArray("actions");
        document.putArray("dataSources");
        document.putArray("mappings");
        return document;
    }

    private ObjectNode conceptNode(String key, String name) {
        ObjectNode concept = objectMapper.createObjectNode()
                .put("key", key)
                .put("name", name)
                .put("pluralName", name)
                .put("description", name)
                .put("conceptType", "ENTITY")
                .putNull("displayPropertyKey")
                .put("positionX", 0)
                .put("positionY", 0)
                .put("queryable", false)
                .put("enabled", true);
        concept.putArray("properties");
        return concept;
    }

    private CompletableFuture<String> concurrentCatalogCommit(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            SourcePreparation prepared) {
        return CompletableFuture.supplyAsync(() -> {
            TenantContext.setOrgId(orgA);
            TenantContext.setUserId("owner-a");
            ready.countDown();
            try {
                start.await();
                catalogTransactions.commitObjects(prepared, "owner-a", List.of(
                        new PhysicalObject("projects", "项目", "INLINE", "{}")));
                return "COMMITTED";
            } catch (ConflictException exception) {
                return "CONFLICT";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                TenantContext.clear();
            }
        }, executor);
    }

    private CompletableFuture<MvcResult> concurrentWorkspaceCreate(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            OntologyManagementService.WorkspaceCreateRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                start.await();
                return mockMvc.perform(post("/admin/ontologies")
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                        .andReturn();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private long discoverObjects(long workspaceId, long sourceId, long revision) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/admin/ontologies/{workspaceId}/data-sources/{sourceId}/discover-objects",
                        workspaceId,
                        sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andReturn();
        return data(result).path("revision").asLong();
    }

    private long discoverFields(
            long workspaceId,
            long sourceId,
            String objectKey,
            long revision) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("objectKey", objectKey);
        request.put("expectedRevision", revision);
        MvcResult result = mockMvc.perform(post(
                        "/admin/ontologies/{workspaceId}/data-sources/{sourceId}/discover-fields",
                        workspaceId,
                        sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn();
        return data(result).path("revision").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void reverse(ArrayNode values) {
        List<JsonNode> reversed = new java.util.ArrayList<>();
        values.forEach(reversed::add);
        java.util.Collections.reverse(reversed);
        values.removeAll();
        reversed.forEach(values::add);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
