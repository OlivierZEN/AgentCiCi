# 通用本体建模与语义查询平台 V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 AgentCiCi 中交付领域无关、面向业务人员的可视化本体设计器，并以 CloudCC CRM 和项目交付示例完成只读语义查询闭环后发布生产。

**Architecture:** 使用 PostgreSQL 保存组织隔离的草稿元模型、物理目录、映射和不可变发布快照；运行时只消费已发布快照。AI 只生成可审阅提案，JSON Schema、GraphQL SDL 和查询计划由确定性编译器生成；数据访问经通用适配器执行，V1 提供 `INLINE_SAMPLE` 与 `CLOUDCC` 两个适配器。

**Tech Stack:** Java 21、Spring Boot 3、Spring Data JPA、Flyway、PostgreSQL、React 19、TypeScript 5.9、Vite 7、Vitest 3、现有模型路由与 CloudCC OpenAPI 服务。

## Global Constraints

- 本体内核及通用前端组件不得使用 CloudCC、客户、商机或其他外部产品名称作为类型、变量或 CSS 标识符。
- AI 只能创建和应用草稿提案；人工 `publish` 是唯一线上版本变更入口。
- V1 只执行已发布版本上的受限只读查询；动作只生成契约，不执行写回。
- 所有持久化数据按 `org_id` 隔离，CloudCC 查询继续使用当前用户会话与记录权限。
- 查询默认 `limit=50`、硬上限 `200`、关系最多一跳；禁止任意 SQL、脚本、URL 和无界扫描。
- 前端只验收桌面端，继承 `鎏金账房` 暖象牙、墨色、紧凑、香槟金结构线设计事实。
- 生产发布遵循 `docs/production-release-runbook.md`，真实发布前必须运行 `./scripts/release-acr.sh --dry-run`。

---

## File Structure

### Backend domain and persistence

- `backend/src/main/resources/db/migration/V81__general_ontology_platform.sql`：13 张本体表、唯一约束、租户索引和级联外键。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/model/OntologyDocument.java`：领域无关草稿/快照 DTO 及枚举。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/*Entity.java`：工作区、概念、属性、关系、指标、动作、数据源、物理对象/字段、映射、AI 提案、版本和查询审计实体。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/*Repository.java`：全部按组织/工作区作用域读取。

### Backend behavior

- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyDraftService.java`：工作区与草稿读写、修订乐观锁、归档。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyValidationService.java`：命名、引用、映射、敏感字段和发布校验。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyCompilerService.java`：JSON Schema、GraphQL SDL、内容哈希与发布快照。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyPublishService.java`：事务发布和不可变版本读取。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/OntologyDataSourceAdapter.java`：发现与只读查询 SPI。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/InlineSampleOntologyAdapter.java`：通用 JSON 示例数据。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/CloudccOntologyAdapter.java`：复用现有 CloudCC 当前用户会话、元数据工具与分页读取。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyCatalogService.java`：数据源、物理目录和映射管理。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyAiProposalService.java`：严格 JSON 模型提案、校验、差异和草稿应用。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/SemanticQueryService.java`：查询契约校验、计划、执行、归一化和审计。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/api/AdminOntologyController.java`：组织管理员建模 API。
- `backend/src/main/java/com/codehouse/ciciassistant/ontology/api/SemanticQueryController.java`：组织成员只读查询与 explain API。
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java`：本体域导出和逆序清理。

### Frontend

- `frontend/src/admin/ontology/ontologyTypes.ts`：前端元模型和 API DTO。
- `frontend/src/admin/ontology/ontologyApi.ts`：鉴权请求、错误归一化和草稿修订头。
- `frontend/src/admin/ontology/OntologyCanvas.tsx`：SVG 关系线、可拖动/可聚焦节点和键盘替代操作。
- `frontend/src/admin/ontology/OntologyInspector.tsx`：概念、属性、关系、指标与动作业务检查器。
- `frontend/src/admin/ontology/OntologyProposalPanel.tsx`：AI 提案差异、应用和错误诊断。
- `frontend/src/admin/ontology/OntologyMappingPanel.tsx`：数据源发现、字段映射和验证。
- `frontend/src/admin/pages/AdminOntologyPage.tsx`：列表、向导、工作台、版本与技术预览编排。
- `frontend/src/admin/ontology/ontologyModel.test.ts`：纯状态变换、连线几何、提案应用和修订冲突测试。
- `frontend/src/App.tsx`、`frontend/src/admin/AdminShell.tsx`：路由和菜单。
- `frontend/src/styles/admin-ontology.css`、`frontend/src/styles.css`：本体工作台样式入口。

### Verification and governance

- `backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPlatformIntegrationTest.java`：API、版本、隔离、示例查询和权限集成测试。
- `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyValidationServiceTest.java`：纯校验器单测。
- `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyCompilerServiceTest.java`：稳定契约与哈希单测。
- `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/SemanticQueryServiceTest.java`：预算、映射和适配器路由单测。
- `backend/src/test/java/com/codehouse/ciciassistant/ontology/adapter/CloudccOntologyAdapterTest.java`：字段发现解析与只读查询编译单测。
- `docs/specs/FEAT-116-general-ontology-modeling-platform.md`：实现进展和最终生产事实。
- `.claw/test-report.md`、`.claw/tasks/TASK-210.md`、`.claw/current-status.md`：真实验证与交接快照。

---

### Task 1: 通用元模型与租户持久化

**Files:**

- Create: `backend/src/main/resources/db/migration/V81__general_ontology_platform.sql`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/model/OntologyDocument.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/OntologyWorkspaceEntity.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/OntologyConceptEntity.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/OntologyPropertyEntity.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/domain/OntologyRelationEntity.java`
- Create: remaining ontology entities and repositories listed under File Structure
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPersistenceIntegrationTest.java`

**Interfaces:**

- Produces: `OntologyDocument`, `OntologyWorkspaceRepository.findByIdAndOrgId(Long,String)`, `OntologyVersionRepository.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(Long,String)`.
- Consumes: existing Spring Data JPA, Jackson and `TenantContext` conventions.

- [ ] **Step 1: Write the failing persistence and tenant-isolation test**

```java
@SpringBootTest
class OntologyPersistenceIntegrationTest {
    @Autowired OntologyWorkspaceRepository workspaces;

    @Test
    void scopesWorkspaceLookupToOrganization() {
        OntologyWorkspaceEntity saved = workspaces.save(
                new OntologyWorkspaceEntity("org-a", "project-delivery", "项目交付", "通用性样例", "user-a"));
        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-a")).isPresent();
        assertThat(workspaces.findByIdAndOrgId(saved.getId(), "org-b")).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test and verify the missing types fail compilation**

Run: `cd backend && mvn -Dtest=OntologyPersistenceIntegrationTest test`

Expected: FAIL because the migration, entity and repository do not exist.

- [ ] **Step 3: Implement the domain document contract**

```java
public record OntologyDocument(
        String key,
        String name,
        String description,
        List<Concept> concepts,
        List<Relation> relations,
        List<Metric> metrics,
        List<Action> actions,
        List<DataSource> dataSources,
        List<Mapping> mappings) {
    public record Concept(String key, String name, String pluralName, String description,
                          ConceptType conceptType, String displayPropertyKey,
                          double positionX, double positionY, boolean queryable,
                          boolean enabled, List<Property> properties) {}
    public record Property(String key, String name, String description, DataType dataType,
                           boolean required, boolean multiple, boolean sensitive,
                           boolean queryable, List<String> enumValues) {}
    public record Relation(String key, String name, String description, String sourceConceptKey,
                           String targetConceptKey, Cardinality cardinality,
                           String forwardLabel, String reverseLabel, boolean queryable,
                           boolean enabled) {}
    public record Metric(String key, String name, String conceptKey, Aggregation aggregation,
                         String measurePropertyKey, List<String> groupByPropertyKeys,
                         String timePropertyKey, List<QueryFilter> filters) {}
    public record Action(String key, String name, String conceptKey, String description,
                         List<ActionParameter> parameters) {}
    public record ActionParameter(String key, String name, DataType dataType, boolean required) {}
    public record DataSource(Long id, String key, String name, SourceType type, String configJson) {}
    public record Mapping(String targetType, String targetKey, Long dataSourceId,
                          String physicalObjectKey, String physicalFieldKey,
                          String relationTargetFieldKey, String transform,
                          double confidence, String source, String validationStatus) {}
    public record QueryFilter(String property, Operator operator, Object value) {}
    public enum ConceptType { ENTITY, EVENT }
    public enum DataType { TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM, REFERENCE }
    public enum Cardinality { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY }
    public enum Aggregation { COUNT, SUM, AVG, MIN, MAX }
    public enum SourceType { INLINE_SAMPLE, CLOUDCC }
    public enum Operator { EQ, NE, IN, CONTAINS, GT, GTE, LT, LTE, BETWEEN, IS_NULL }
}
```

- [ ] **Step 4: Add V81 and all scoped repositories**

Implement the 13 tables from FEAT-116 with `org_id`, workspace foreign keys, `ON DELETE CASCADE` for draft children, unique `(org_id, key)` on workspaces, unique `(workspace_id, key)` on concepts/relations/metrics/actions/data sources, and unique `(workspace_id, version_no)` on versions. Keep published snapshots and proposal payloads as `TEXT` JSON to match existing project conventions.

- [ ] **Step 5: Run migration and persistence tests**

Run: `cd backend && mvn -Dtest=OntologyPersistenceIntegrationTest test`

Expected: PASS; Flyway reports schema version `81`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V81__general_ontology_platform.sql \
  backend/src/main/java/com/codehouse/ciciassistant/ontology/model \
  backend/src/main/java/com/codehouse/ciciassistant/ontology/domain \
  backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPersistenceIntegrationTest.java
git commit -m "feat: add general ontology persistence model"
```

### Task 2: 草稿、校验、编译与人工发布

**Files:**

- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyDraftService.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyValidationService.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyCompilerService.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyPublishService.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyValidationServiceTest.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyCompilerServiceTest.java`

**Interfaces:**

- Produces: `saveDraft(orgId,userId,workspaceId,expectedRevision,document)`, `validate(document,forPublish)`, `compile(document,version)`, `publish(orgId,userId,workspaceId,expectedRevision)`.
- Consumes: Task 1 entities/repositories and `ObjectMapper`.

- [ ] **Step 1: Write failing validation and compiler tests**

```java
@Test
void rejectsDanglingRelationAndDuplicatePropertyKeys() {
    OntologyDocument invalid = Fixtures.documentWithDanglingRelationAndDuplicateProperty();
    List<ValidationIssue> issues = validator.validate(invalid, true);
    assertThat(issues).extracting(ValidationIssue::code)
            .contains("DUPLICATE_PROPERTY_KEY", "RELATION_TARGET_NOT_FOUND");
}

@Test
void compilesStableContractsFromSameDocument() {
    CompiledContracts first = compiler.compile(Fixtures.projectDeliveryDocument(), 1);
    CompiledContracts second = compiler.compile(Fixtures.projectDeliveryDocument(), 1);
    assertThat(first.contentHash()).isEqualTo(second.contentHash());
    assertThat(first.graphqlSdl()).contains("type Project", "type Query");
    assertThat(first.jsonSchema()).contains("https://json-schema.org/draft/2020-12/schema");
}
```

- [ ] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=OntologyValidationServiceTest,OntologyCompilerServiceTest test`

Expected: FAIL because the services and fixture helpers are absent.

- [ ] **Step 3: Implement deterministic validation**

`OntologyValidationService` must return ordered `ValidationIssue(code,severity,path,message)` records, validate lower-kebab/snake-safe keys, duplicates, display property references, relation endpoints, metric fields, action parameters, mapping targets, publish-time queryable mappings, allowed transforms and sensitive-query rules.

- [ ] **Step 4: Implement deterministic contract compilation**

Sort concepts, properties, relations, metrics and actions by key before serialization. Generate Draft 2020-12 JSON Schema, read-only GraphQL SDL and a semantic-query JSON contract, then compute SHA-256 over canonical JSON plus version.

- [ ] **Step 5: Implement optimistic draft save and transactional publish**

`saveDraft` must compare `expectedRevision`, replace only draft child rows, increment revision once and throw `ConflictException("ONTOLOGY_REVISION_CONFLICT")` on mismatch. `publish` must require zero `ERROR` issues, create an immutable version snapshot, increment version, update workspace state and never expose an AI-callable publish method.

- [ ] **Step 6: Run focused tests**

Run: `cd backend && mvn -Dtest=OntologyValidationServiceTest,OntologyCompilerServiceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/codehouse/ciciassistant/ontology/service \
  backend/src/test/java/com/codehouse/ciciassistant/ontology/service
git commit -m "feat: validate compile and publish ontology drafts"
```

### Task 3: 数据目录、通用适配器与只读语义查询

**Files:**

- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/OntologyDataSourceAdapter.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/InlineSampleOntologyAdapter.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter/CloudccOntologyAdapter.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyCatalogService.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/SemanticQueryService.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/SemanticQueryServiceTest.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/adapter/CloudccOntologyAdapterTest.java`

**Interfaces:**

- Produces: `discoverObjects(AdapterContext,DataSourceConfig)`, `discoverFields(...)`, `executeRead(AdapterContext,PhysicalQuery)`, `SemanticQueryService.explain(...)`, `SemanticQueryService.execute(...)`.
- Consumes: published `OntologyVersionSnapshot`, Task 1 mappings, `CloudccOpenApiService.pageQueryRecords/getStandardObjects/getCustomObjects/getObjectFields`.

- [ ] **Step 1: Write failing adapter routing and query-budget tests**

```java
@Test
void routesPublishedInlineQueryAndReturnsEvidence() {
    QueryResult result = service.execute("org-a", "user-a",
            new SemanticQuery("project-delivery", 1, "task", List.of("name", "status"),
                    List.of(new Filter("status", "EQ", "IN_PROGRESS")), List.of(), 50));
    assertThat(result.rows()).extracting(row -> row.get("name")).contains("语义平台设计");
    assertThat(result.evidence().sourceType()).isEqualTo("INLINE_SAMPLE");
    assertThat(result.evidence().ontologyVersion()).isEqualTo(1);
}

@Test
void rejectsLimitAboveTwoHundredBeforeAdapterCall() {
    assertThatThrownBy(() -> service.explain("org-a", "user-a", Fixtures.queryWithLimit(201)))
            .hasMessageContaining("QUERY_BUDGET_EXCEEDED");
    verifyNoInteractions(adapter);
}
```

- [ ] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=SemanticQueryServiceTest,CloudccOntologyAdapterTest test`

Expected: FAIL because adapter SPI and query service do not exist.

- [ ] **Step 3: Implement the adapter SPI and inline adapter**

```java
public interface OntologyDataSourceAdapter {
    boolean supports(OntologyDocument.SourceType type);
    List<PhysicalObject> discoverObjects(AdapterContext context, DataSourceConfig source);
    List<PhysicalField> discoverFields(AdapterContext context, DataSourceConfig source, String objectKey);
    MappingValidation validateMapping(AdapterContext context, DataSourceConfig source, OntologyDocument.Mapping mapping);
    PhysicalResult executeRead(AdapterContext context, DataSourceConfig source, PhysicalQuery query);
}
```

`INLINE_SAMPLE` must parse a bounded JSON object-of-arrays, allow only discovered fields, apply the operator whitelist in memory and enforce `limit <= 200`.

- [ ] **Step 4: Implement the CloudCC adapter**

Parse the existing metadata methods into `PhysicalObject`/`PhysicalField`. Compile only mapped field names and safe filter values to CloudCC expressions; reject unsupported operators rather than concatenating them. Call `pageQueryRecords(orgId,userId,objectApiName,fields,expressions,1,limit)` so current-user permissions remain authoritative.

- [ ] **Step 5: Implement explain/execute and audit**

Resolve a published snapshot, reject draft-only versions, unknown/sensitive/unmapped fields, one-hop overflow, cross-source plans and oversized limits. Return `QueryResult(rows,evidence,elapsedMs)` and persist an audit with redacted filter summary, not raw credentials or sensitive values.

- [ ] **Step 6: Run focused tests**

Run: `cd backend && mvn -Dtest=SemanticQueryServiceTest,CloudccOntologyAdapterTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/codehouse/ciciassistant/ontology/adapter \
  backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyCatalogService.java \
  backend/src/main/java/com/codehouse/ciciassistant/ontology/service/SemanticQueryService.java \
  backend/src/test/java/com/codehouse/ciciassistant/ontology
git commit -m "feat: execute governed ontology queries through adapters"
```

### Task 4: AI 草稿副驾驶

**Files:**

- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyAiProposalService.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyAiProposalServiceTest.java`

**Interfaces:**

- Produces: `propose(orgId,userId,workspaceId,ProposalCommand)`, `apply(orgId,userId,proposalId,expectedRevision)`.
- Consumes: `ModelRouterService.route(orgId,"ontology-modeling")`, `ModelProviderService.credentialsForProvider`, `AliyunBailianClient.chatCompletionWithCredentials`, validator and draft service.

- [ ] **Step 1: Write failing AI-boundary tests**

```java
@Test
void storesValidatedProposalWithoutChangingDraft() {
    when(modelClient.chatCompletionWithCredentials(any(), anyList(), isNull(), eq(true), any(), any()))
            .thenReturn(new ChatCompletionResult("assistant", Fixtures.projectProposalJson(), null, "stop", 10, 20));
    ProposalView proposal = service.propose("org-a", "user-a", 1L,
            new ProposalCommand("项目交付领域，包含项目、任务和负责人", List.of(), "DOMAIN_FIRST"));
    assertThat(proposal.status()).isEqualTo("READY");
    verify(draftService, never()).saveDraft(any(), any(), anyLong(), anyLong(), any());
}

@Test
void cannotApplyInvalidOrCrossTenantProposal() {
    assertThatThrownBy(() -> service.apply("org-b", "user-b", 10L, 1L))
            .hasMessageContaining("AI_PROPOSAL_INVALID");
}
```

- [ ] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=OntologyAiProposalServiceTest test`

Expected: FAIL because the proposal service does not exist.

- [ ] **Step 3: Implement strict proposal generation**

Use a system prompt that requires one JSON object matching `OntologyDocument` and explicitly forbids credentials, SQL, scripts, URLs, publishing and writes. Strip code fences, deserialize with Jackson, enforce payload size/concept/property limits, validate all references, store `READY` or `FAILED` with a diagnostic, and never mutate the draft during `propose`.

- [ ] **Step 4: Implement atomic proposal application**

Load proposal by `id + orgId`, require `READY`, merge by stable keys, call `saveDraft` with `expectedRevision`, mark proposal `APPLIED`, and reject re-application or published-version mutation.

- [ ] **Step 5: Run focused tests and commit**

Run: `cd backend && mvn -Dtest=OntologyAiProposalServiceTest test`

Expected: PASS.

```bash
git add backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyAiProposalService.java \
  backend/src/test/java/com/codehouse/ciciassistant/ontology/service/OntologyAiProposalServiceTest.java
git commit -m "feat: add reviewable ontology AI proposals"
```

### Task 5: 管理 API、运行 API、领域样例与租户生命周期

**Files:**

- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/api/AdminOntologyController.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/api/SemanticQueryController.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyReferencePackageService.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java`
- Test: `backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPlatformIntegrationTest.java`
- Modify: `backend/src/test/java/com/codehouse/ciciassistant/platform/PlatformTenantLifecycleIntegrationTest.java`

**Interfaces:**

- Produces: `/admin/ontologies/**`, `/semantic-query/explain`, `/semantic-query/execute`.
- Consumes: Tasks 1–4 services and existing `ApiResponse`, `@RequireOrgAdmin`, `TenantContext`.

- [ ] **Step 1: Write failing end-to-end API tests**

```java
mockMvc.perform(post("/admin/ontologies")
        .header(AUTHORIZATION, "Bearer " + orgAdminToken)
        .contentType(APPLICATION_JSON)
        .content("""{"key":"project-delivery","name":"项目交付","description":"通用样例"}"""))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.draftRevision").value(1));

mockMvc.perform(post("/semantic-query/execute")
        .header(AUTHORIZATION, "Bearer " + orgUserToken)
        .contentType(APPLICATION_JSON)
        .content(Fixtures.projectTaskQueryJson()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.evidence.ontologyVersion").value(1));

mockMvc.perform(get("/admin/ontologies/{id}", workspaceId)
        .header(AUTHORIZATION, "Bearer " + otherOrgAdminToken))
    .andExpect(status().isNotFound());
```

- [ ] **Step 2: Verify red state**

Run: `cd backend && mvn -Dtest=OntologyPlatformIntegrationTest test`

Expected: FAIL with missing endpoints.

- [ ] **Step 3: Implement typed controllers**

Expose create/list/get/archive, draft get/save/validate/diff, proposal create/get/apply, data-source create/discover, mapping replace/validate, compile preview, publish/version list/detail, explain and execute. Put `@RequireOrgAdmin` on every management mutation and derive `orgId/userId` only from `TenantContext`.

- [ ] **Step 4: Add reference packages as ordinary data**

`OntologyReferencePackageService` must return `OntologyDocument` values for `project-delivery` and `customer-operations` without changing the core model. The project package includes inline records for projects, tasks and owners; the CRM package contains candidate concepts/mappings only and requires discovered CloudCC metadata before publish.

- [ ] **Step 5: Add all ontology tables to export and purge order**

Create an `ontology` manifest domain, include non-secret tables in `EXPORT_TABLES`, and add child-to-parent deletion order ending with `ontology_workspace`. Extend lifecycle integration assertions so a purged organization has zero ontology rows.

- [ ] **Step 6: Run integration tests**

Run: `cd backend && mvn -Dtest=OntologyPlatformIntegrationTest,PlatformTenantLifecycleIntegrationTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/codehouse/ciciassistant/ontology/api \
  backend/src/main/java/com/codehouse/ciciassistant/ontology/service/OntologyReferencePackageService.java \
  backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java \
  backend/src/test/java/com/codehouse/ciciassistant/ontology/OntologyPlatformIntegrationTest.java \
  backend/src/test/java/com/codehouse/ciciassistant/platform/PlatformTenantLifecycleIntegrationTest.java
git commit -m "feat: expose ontology modeling and semantic query APIs"
```

### Task 6: 前端模型、API、路由与状态机

**Files:**

- Create: `frontend/src/admin/ontology/ontologyTypes.ts`
- Create: `frontend/src/admin/ontology/ontologyApi.ts`
- Create: `frontend/src/admin/ontology/ontologyModel.ts`
- Create: `frontend/src/admin/ontology/ontologyModel.test.ts`
- Create: `frontend/src/admin/pages/AdminOntologyPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/admin/AdminShell.tsx`

**Interfaces:**

- Produces: `OntologyWorkspaceView`, `OntologyDocument`, `applyProposal`, `moveConcept`, `connectConcepts`, `saveDraft(expectedRevision)`.
- Consumes: Task 5 REST responses and existing admin token fetch conventions.

- [ ] **Step 1: Write failing pure-state tests**

```ts
it("moves a concept without mutating the previous draft", () => {
  const previous = projectDeliveryDraft();
  const next = moveConcept(previous, "task", { x: 460, y: 180 });
  expect(next.concepts.find((item) => item.key === "task")?.positionX).toBe(460);
  expect(previous.concepts.find((item) => item.key === "task")?.positionX).not.toBe(460);
});

it("rejects a second relation with the same stable key", () => {
  expect(() => connectConcepts(projectDeliveryDraft(), relation("project-has-task")))
    .toThrow("DUPLICATE_RELATION_KEY");
});
```

- [ ] **Step 2: Verify red state**

Run: `cd frontend && npm test -- ontologyModel.test.ts`

Expected: FAIL because the model helpers do not exist.

- [ ] **Step 3: Implement immutable model helpers and typed API client**

Normalize API failures into `{code,message,details}`; send `expectedRevision` in save/apply/publish bodies; never silently overwrite on HTTP 409. Keep model helpers side-effect free so canvas and inspector share one draft source.

- [ ] **Step 4: Add route and menu**

Import `AdminOntologyPage` in `App.tsx`, add `<Route path="ontology" element={<AdminOntologyPage />} />`, and add `{ kind: "link", to: "/admin/ontology", label: "业务本体" }` next to knowledge/data-quality navigation.

- [ ] **Step 5: Run tests and commit**

Run: `cd frontend && npm test -- ontologyModel.test.ts`

Expected: PASS.

```bash
git add frontend/src/admin/ontology/ontologyTypes.ts \
  frontend/src/admin/ontology/ontologyApi.ts \
  frontend/src/admin/ontology/ontologyModel.ts \
  frontend/src/admin/ontology/ontologyModel.test.ts \
  frontend/src/admin/pages/AdminOntologyPage.tsx frontend/src/App.tsx frontend/src/admin/AdminShell.tsx
git commit -m "feat: add ontology admin route and state model"
```

### Task 7: 业务可视化建模工作台

**Files:**

- Create: `frontend/src/admin/ontology/OntologyCanvas.tsx`
- Create: `frontend/src/admin/ontology/OntologyInspector.tsx`
- Create: `frontend/src/admin/ontology/OntologyProposalPanel.tsx`
- Create: `frontend/src/admin/ontology/OntologyMappingPanel.tsx`
- Create: `frontend/src/styles/admin-ontology.css`
- Modify: `frontend/src/admin/pages/AdminOntologyPage.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `DESIGN.json`
- Modify: `DESIGN.md`
- Test: `frontend/src/admin/ontology/ontologyModel.test.ts`

**Interfaces:**

- Produces: complete `/admin/ontology` list, domain-first wizard, canvas, inspector, mapping, AI proposal, validation, publish, version and technical-preview flows.
- Consumes: Task 6 state and API client.

- [ ] **Step 1: Extend failing geometry and proposal tests**

```ts
it("calculates a relation line between node edges", () => {
  expect(relationLine({ x: 40, y: 40, width: 220, height: 112 },
                      { x: 420, y: 180, width: 220, height: 112 }))
    .toEqual({ x1: 260, y1: 96, x2: 420, y2: 236 });
});

it("applies an AI proposal only after explicit approval", () => {
  expect(previewProposal(projectDeliveryDraft(), proposal()).draftChanged).toBe(false);
  expect(applyProposal(projectDeliveryDraft(), proposal()).concepts).toHaveLength(4);
});
```

- [ ] **Step 2: Verify red state**

Run: `cd frontend && npm test -- ontologyModel.test.ts`

Expected: FAIL for missing geometry/proposal helpers.

- [ ] **Step 3: Implement list and domain-first wizard**

The first action asks for domain name, purpose, core objects and common questions; “从数据源发现” is a secondary tab. Creation shows model-unavailable diagnostics and keeps manual editing available.

- [ ] **Step 4: Implement accessible canvas and inspector**

Use absolute-positioned semantic buttons for nodes and an underlay SVG for lines. Pointer drag updates local coordinates and saves on pointer-up; arrow keys move a focused node by 8 px. Provide explicit “添加业务对象”和“添加关系” controls so no operation requires drag.

- [ ] **Step 5: Implement proposal, mapping, validation and publish panels**

Proposal panel shows added/changed/removed counts and requires “应用到草稿”. Mapping panel shows business term → data source/object/field and validation state. Publish is disabled while validation has errors and displays the exact human confirmation copy “发布版本 N；当前线上版本不会被 AI 自动替换”。

- [ ] **Step 6: Implement technical preview and version history**

Display JSON Schema, GraphQL SDL and semantic-query example in read-only code surfaces with copy buttons; show version number, content hash, publisher and timestamp.

- [ ] **Step 7: Apply design governance updates**

Add the reusable `semantic-canvas`, `semantic-node`, `mapping-row` and `proposal-diff` component rules to `DESIGN.json`, then summarize them in Chinese in `DESIGN.md`. Keep warm ivory surfaces, ink text, champagne structural lines, compact controls and no mobile overrides.

- [ ] **Step 8: Run frontend tests/build and commit**

Run: `cd frontend && npm test && npm run build`

Expected: all tests PASS and Vite production build succeeds.

```bash
git add frontend/src/admin/ontology frontend/src/admin/pages/AdminOntologyPage.tsx \
  frontend/src/styles/admin-ontology.css frontend/src/styles.css DESIGN.json DESIGN.md
git commit -m "feat: build visual ontology modeling workbench"
```

### Task 8: 全量验证、生产发布与状态收口

**Files:**

- Modify: `docs/specs/FEAT-116-general-ontology-modeling-platform.md`
- Modify: `.claw/tasks/TASK-210.md`
- Modify: `.claw/task-board.md`
- Modify: `.claw/current-status.md`
- Modify: `.claw/test-report.md`
- Modify: `.claw/devops.md` only when a verified operational fact changes

**Interfaces:**

- Consumes: all prior tasks and `docs/production-release-runbook.md`.
- Produces: verified production version, rollback reference, screenshots, smoke evidence and accurate project state.

- [ ] **Step 1: Run focused and full automated verification**

Run:

```bash
cd backend && mvn -Dtest=OntologyPersistenceIntegrationTest,OntologyValidationServiceTest,OntologyCompilerServiceTest,OntologyAiProposalServiceTest,SemanticQueryServiceTest,CloudccOntologyAdapterTest,OntologyPlatformIntegrationTest,PlatformTenantLifecycleIntegrationTest test
cd ../frontend && npm test && npm run build
cd .. && git diff --check
python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py .
```

Expected: every command exits `0`; record exact counts rather than estimates.

- [ ] **Step 2: Run the application and perform desktop product QA**

At `1600 × 1000`, verify list, domain-first creation, AI proposal review, canvas keyboard/pointer interactions, inspector, mapping, validation, publish, version preview and sample query. Capture screenshots under `output/playwright/task210-ontology-*.png`; assert browser console error/warning count `0` and outer horizontal overflow `0`.

- [ ] **Step 3: Verify both reference domains**

Publish and query `project-delivery` through `INLINE_SAMPLE`. For the configured production organization, discover CloudCC objects/fields, validate the CRM template mappings and execute at least one current-user read query; if external credentials are unavailable, do not claim the CloudCC smoke passed and stop before production acceptance.

- [ ] **Step 4: Run release dry-run**

Run: `./scripts/release-acr.sh --dry-run`

Expected: one consistent next version for Git tag, backend/frontend app version and `CICI_IMAGE_TAG`; no dirty-release bypass.

- [ ] **Step 5: Commit implementation and verified state**

```bash
git add backend frontend DESIGN.json DESIGN.md docs/specs/FEAT-116-general-ontology-modeling-platform.md \
  .claw/tasks/TASK-210.md .claw/task-board.md .claw/current-status.md .claw/test-report.md
git commit -m "feat: deliver general ontology platform v1"
```

- [ ] **Step 6: Publish production using the runbook**

Run the real `./scripts/release-acr.sh --version <dry-run-version>`, confirm ACR pushes, create the required production backup, deploy that exact tag, and execute health, login, `/admin/ontology`, sample publish/query and tenant-isolation smoke checks.

- [ ] **Step 7: Record production evidence**

Update FEAT-116 and TASK-210 with exact version, commit, image tag, Flyway version, backup path, smoke results and rollback target; set `done` only after production smoke passes. Commit and push the state-only delta to `origin/main`.

---

## Plan Self-Review

- Spec coverage: all FEAT-116 goals, non-goals, security rules, AI boundary, adapters, compiler, UI, two-domain proof, lifecycle, testing and release have an owning task.
- Placeholder scan: the plan contains no deferred implementation marker; external CloudCC validation has an explicit stop condition rather than a fabricated pass.
- Type consistency: `OntologyDocument`, adapter SPI, draft revision, proposal, published snapshot and semantic query names are stable across tasks.
- Scope check: tasks are separately reviewable, but each produces a runnable increment toward one V1; no unrelated refactor or mobile work is included.

