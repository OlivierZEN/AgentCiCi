# Skill DAG 只读治理闭环 Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于现有 Agent/工作流/Skill 版本钉住事实交付统一只读 Skill DAG，并在 Agent Builder、调试链路和平台 Skill 影响分析中生产可用。

**Architecture:** 后端新增 `SkillDependencyGraphService` 作为唯一图读模型，查询现有租户隔离实体并输出稳定节点、边、摘要和 warning；两个只读 API 分别服务 Agent 和 Skill 影响视角。前端新增共享 `SkillDependencyGraph` 组件，Builder 与平台页只负责加载、选择和错误状态，不在页面内重复布局算法。

**Tech Stack:** Java 21、Spring Boot、Spring Data JPA、JUnit 5/MockMvc、React 19、TypeScript 5.9、Vitest、Lucide React、Vite。

## Global Constraints

- 只实现运行时派生的只读 DAG，不新增数据库表或业务数据迁移；允许 V81 为工作流引用与当前 Agent 绑定两条 Skill 影响查询并发创建匹配复合索引。
- 已编译工作流必须以 `agent_workflow_skill_ref.skill_version_id` 为事实源，不漂移到 Skill 当前版本。
- 所有查询必须包含 `orgId`，Agent 接口要求 `AgentPermission.VIEW`，Skill 接口要求组织管理员。
- `metadata` 不返回 Prompt 正文、密钥、Token、连接参数或知识库内容。
- 不实现 DAG 编辑、Skill-to-Skill 调用、`invokeSkill` 或独立子流程执行。
- 认证后界面继续使用 `鎏金账房`，不改变 `DESIGN.json` 的视觉语言；不做移动端范围。
- 每个生产实现步骤先写失败测试并真实观察 RED，再写最小实现。
- 发布严格遵循 `docs/production-release-runbook.md` 和 `./scripts/release-acr.sh --dry-run`。

---

## File Map

- Create `backend/src/main/java/com/codehouse/ciciassistant/agent/service/SkillDependencyGraphService.java`: 统一图契约、Agent 图和 Skill 影响图组装。
- Create `backend/src/main/java/com/codehouse/ciciassistant/agent/api/AgentSkillDagController.java`: Agent 维度只读接口与 VIEW 权限。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/platform/api/PlatformController.java`: Skill 影响图平台角色接口。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentWorkflowSkillRefRepository.java`: 按租户和 Skill 查询引用。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentWorkflowVersionRepository.java`: 按租户和版本 ID 批量查询。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentDefinitionRepository.java`: 按租户和 Agent ID 批量查询。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/AgentSkillBindingRepository.java`: 按租户和 Skill 查询当前绑定。
- Modify `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/SkillVersionRepository.java`: 按租户和版本 ID 查询。
- Create `backend/src/test/java/com/codehouse/ciciassistant/agent/SkillDependencyGraphServiceTest.java`: 隔离 Repository 的真实实体服务测试，覆盖钉住、回退、影响、缺失引用与稳定排序。
- Create `backend/src/test/java/com/codehouse/ciciassistant/agent/AgentSkillDagControllerTest.java`: 定向控制器测试，覆盖 VIEW 权限调用和参数透传。
- Create `frontend/src/shared/skill-dag.ts`: 图类型、稳定布局和请求竞态纯函数。
- Create `frontend/src/shared/SkillDependencyGraph.tsx`: 共享只读图、控制条、节点选择、空态与 warning。
- Create `frontend/src/shared/skill-dependency-graph.css`: 组件级 `鎏金账房` 样式。
- Create `frontend/src/shared/SkillDependencyGraph.test.tsx`: 静态渲染和布局纯函数测试。
- Modify `frontend/src/assistant/AgentBuilderShell.tsx`: 编译图分段、Skill DAG 请求、调试解析链路。
- Modify `frontend/src/assistant/AgentBuilderShell.test.ts`: URL、版本选择和调试治理映射测试。
- Modify `frontend/src/assistant/cici-ui.css`: Builder 分段与解析链路局部布局。
- Modify `frontend/src/platform/pages/PlatformSkillsPage.tsx`: 选择 Skill 时加载影响图并下钻。
- Create `frontend/src/platform/pages/PlatformSkillsPage.test.ts`: 最新请求接受规则和依赖图 URL 测试。
- Modify `docs/specs/FEAT-117-skill-dag-governance-phase1.md`: 实现与生产事实。
- Modify `.claw/tasks/TASK-212.md`, `.claw/test-report.md`, `.claw/current-status.md`: 进度、真实验证和最终快照。

---

### Task 1: 后端统一图读模型与 Agent API

**Files:**

- Create: `backend/src/test/java/com/codehouse/ciciassistant/agent/SkillDependencyGraphServiceTest.java`
- Create: `backend/src/test/java/com/codehouse/ciciassistant/agent/AgentSkillDagControllerTest.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/agent/service/SkillDependencyGraphService.java`
- Create: `backend/src/main/java/com/codehouse/ciciassistant/agent/api/AgentSkillDagController.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentWorkflowVersionRepository.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/SkillVersionRepository.java`

**Interfaces:**

- Produces: `GraphView getAgentGraph(String orgId, String agentId, Integer versionNo)`。
- Produces: `GET /agents/{agentId}/skill-dag?versionNo={n}`。
- Produces records: `GraphView`, `GraphScope`, `GraphNode`, `GraphEdge`, `GraphSummary`。

- [ ] **Step 1: 写 Agent 图失败集成测试**

在 `SkillDependencyGraphServiceTest` 中使用完整真实实体作为 Repository 返回值，创建 Agent、两个工作流版本、Skill 与 Skill Version；断言显式 v1 查询仍返回 v1 Skill Version，默认查询优先已发布版本，并断言节点 ID、边类型、摘要和 Tool/KB 边界。Repository 只作为数据访问边界替身，不断言 mock 调用次数。

```java
SkillDependencyGraphService.GraphView graph = service.getAgentGraph(ORG_ID, AGENT_ID, 1);
assertThat(graph.sourceMode()).isEqualTo("PINNED_WORKFLOW_VERSION");
assertThat(graph.nodes()).filteredOn(node -> node.type().equals("SKILL_VERSION"))
        .extracting(node -> node.metadata().get("versionNo"))
        .containsExactly(1);
assertThat(graph.edges()).extracting(SkillDependencyGraphService.GraphEdge::type)
        .contains("PINS_SKILL_VERSION", "ALLOWS_TOOL", "ALLOWS_KNOWLEDGE_BASE");
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -Dtest=SkillDependencyGraphServiceTest,AgentSkillDagControllerTest test`

Expected: FAIL，原因是 `SkillDependencyGraphService` 和 `AgentSkillDagController` 尚不存在，测试编译失败。

- [ ] **Step 3: 实现最小图契约和 Agent 图查询**

在服务中定义稳定契约：

```java
public record GraphView(
        GraphScope scope,
        String sourceMode,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        GraphSummary summary,
        List<String> warnings) {}

public record GraphNode(
        String id,
        String type,
        String label,
        String detail,
        String status,
        int layer,
        Map<String, Object> metadata) {}

public record GraphEdge(String id, String source, String target, String type, String label) {}
```

`getAgentGraph` 必须按 FEAT-117 的版本选择顺序读取工作流；有版本时读取钉住引用，无版本时读取当前启用绑定。Tool/KB 白名单使用 `ObjectMapper` 解析 JSON 数组，缺失元数据时保留业务键并增加 warning。

- [ ] **Step 4: 暴露权限受控的 Agent API**

```java
@GetMapping("/{agentId}/skill-dag")
public ApiResponse<GraphView> getSkillDag(
        @PathVariable String agentId,
        @RequestParam(required = false) Integer versionNo) {
    String orgId = TenantContext.requireOrgId();
    accessControlService.require(orgId, requireUserId(), TenantContext.getRoles(), agentId, AgentPermission.VIEW);
    return ApiResponse.ok(graphService.getAgentGraph(orgId, agentId, versionNo));
}
```

- [ ] **Step 5: 运行测试并确认 GREEN**

Run: `cd backend && mvn -Dtest=SkillDependencyGraphServiceTest,AgentSkillDagControllerTest test`

Expected: PASS，Agent 图测试全部通过且无 Spring 启动错误。

- [ ] **Step 6: 提交 Task 1**

```bash
git add backend/src/main/java/com/codehouse/ciciassistant/agent backend/src/main/java/com/codehouse/ciciassistant/skill/domain/SkillVersionRepository.java backend/src/test/java/com/codehouse/ciciassistant/agent
git commit -S -m "feat: add agent skill dependency graph"
```

---

### Task 2: Skill 影响图与租户安全

**Files:**

- Modify: `backend/src/test/java/com/codehouse/ciciassistant/agent/SkillDependencyGraphServiceTest.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/agent/service/SkillDependencyGraphService.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/platform/api/PlatformController.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentWorkflowSkillRefRepository.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentWorkflowVersionRepository.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/agent/domain/AgentDefinitionRepository.java`
- Modify: `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/AgentSkillBindingRepository.java`

**Interfaces:**

- Consumes: Task 1 的 `GraphView` 契约。
- Produces: `GraphView getSkillImpactGraph(String orgId, Long skillId)`。
- Produces: 后端 `GET /platform/skills/{id}/dependency-graph`，前端 `/api/platform/skills/{id}/dependency-graph`。

- [ ] **Step 1: 写影响图和权限失败测试**

增加服务测试，断言一个 Skill 同时返回当前绑定 Agent、已发布工作流、历史钉住版本和 Agent 归属；所有 Repository fixture 使用同一 `orgId`，并增加“目标 Skill 不属于当前组织”异常测试。组织管理员权限继续由现有 `@RequireOrgAdmin` 切面回归与生产 smoke 验证。

```java
SkillDependencyGraphService.GraphView graph = service.getSkillImpactGraph(ORG_ID, SKILL_ID);
assertThat(graph.scope().type()).isEqualTo("SKILL_IMPACT");
assertThat(graph.nodes()).extracting(SkillDependencyGraphService.GraphNode::type)
        .contains("AGENT", "WORKFLOW_VERSION", "SKILL_VERSION", "SKILL");
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd backend && mvn -Dtest=SkillDependencyGraphServiceTest test`

Expected: FAIL，原因是 Skill 影响接口尚不存在。

- [ ] **Step 3: 增加租户限定 Repository 查询并实现影响图**

新增方法签名：

```java
List<AgentWorkflowSkillRefEntity> findByOrgIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(
        String orgId, Long skillId);
List<AgentWorkflowVersionEntity> findByOrgIdAndIdIn(String orgId, List<Long> ids);
List<AgentDefinitionEntity> findByOrgIdAndAgentIdIn(String orgId, List<String> agentIds);
List<AgentSkillBindingEntity> findByOrgIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
        String orgId, Long skillId);
```

`getSkillImpactGraph` 只通过这些租户限定方法读取数据，Skill Version → Workflow Version → Agent 形成稳定层级；当前绑定但尚未编译的 Agent 使用 `BINDS_SKILL` 边直连 Skill，不伪造工作流版本。

- [ ] **Step 4: 暴露组织管理员接口**

```java
@GetMapping("/skills/{id}/dependency-graph")
public ApiResponse<GraphView> dependencyGraph(@PathVariable Long id) {
    return ApiResponse.ok(graphService.getSkillImpactGraph(platformScopeId(), id));
}
```

- [ ] **Step 5: 运行后端范围测试**

Run: `cd backend && mvn -Dtest=SkillDependencyGraphServiceTest,AgentSkillDagControllerTest test`

Expected: PASS，新增图服务与 Agent 控制器定向回归通过；共享 PostgreSQL 基线失败单独记录，不得混入 TASK-212 回归判定。

- [ ] **Step 6: 提交 Task 2**

```bash
git add backend/src/main/java/com/codehouse/ciciassistant backend/src/test/java/com/codehouse/ciciassistant/agent
git commit -S -m "feat: add skill dependency impact graph"
```

---

### Task 3: 共享 Skill DAG 可视化组件

**Files:**

- Create: `frontend/src/shared/skill-dag.ts`
- Create: `frontend/src/shared/SkillDependencyGraph.tsx`
- Create: `frontend/src/shared/skill-dependency-graph.css`
- Create: `frontend/src/shared/SkillDependencyGraph.test.tsx`

**Interfaces:**

- Produces: `SkillDagGraph`, `SkillDagNode`, `SkillDagEdge` TypeScript 类型。
- Produces: `buildSkillDagLayout(graph)`，返回稳定 `width`、`height`、`nodes`、`edges`。
- Produces: `<SkillDependencyGraph graph loading error onRetry />`。

- [ ] **Step 1: 执行 IMPECCABLE 前端门禁**

读取 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`，并在编辑前记录：

`IMPECCABLE_PREFLIGHT: context=pass product=pass command_reference=pass shape=pass image_gate=skipped:只读治理图沿用既有产品视觉且不需要位图资产 mutation=open`

- [ ] **Step 2: 写布局与静态渲染失败测试**

使用 `react-dom/server` 渲染真实组件，断言节点名称、warning、空态、缩放按钮 accessible name；纯函数断言同一输入每次产生相同坐标，孤立节点不会生成破损边。

```tsx
const html = renderToStaticMarkup(<SkillDependencyGraph graph={graph} />);
expect(html).toContain("CRM 经营分析");
expect(html).toContain('aria-label="适配画布"');
expect(buildSkillDagLayout(graph)).toEqual(buildSkillDagLayout(graph));
```

- [ ] **Step 3: 运行测试并确认 RED**

Run: `cd frontend && npm test -- src/shared/SkillDependencyGraph.test.tsx`

Expected: FAIL，原因是共享组件和布局函数不存在。

- [ ] **Step 4: 实现最小共享组件**

组件使用固定节点宽高和分层坐标，SVG 只绘制连线，节点使用可聚焦 HTML button；控制按钮使用 Lucide `ZoomIn`、`ZoomOut`、`Scan`、`RotateCcw` 并提供 tooltip/`aria-label`。选择节点时在图下方显示 metadata 摘要，禁止渲染敏感字段。

```ts
export type SkillDagNode = {
  id: string;
  type: "AGENT" | "WORKFLOW_VERSION" | "SKILL" | "SKILL_VERSION" | "TOOL" | "KNOWLEDGE_BASE";
  label: string;
  detail: string;
  status: string;
  layer: number;
  metadata: Record<string, unknown>;
};
```

- [ ] **Step 5: 运行组件测试并确认 GREEN**

Run: `cd frontend && npm test -- src/shared/SkillDependencyGraph.test.tsx`

Expected: PASS，且 stderr 无 React key 或 accessibility warning。

- [ ] **Step 6: 提交 Task 3**

```bash
git add frontend/src/shared
git commit -S -m "feat: add shared skill dependency graph"
```

---

### Task 4: Agent Builder 编译图与调试解析链路

**Files:**

- Modify: `frontend/src/assistant/AgentBuilderShell.test.ts`
- Modify: `frontend/src/assistant/AgentBuilderShell.tsx`
- Modify: `frontend/src/assistant/cici-ui.css`

**Interfaces:**

- Consumes: Task 3 的 `SkillDependencyGraph` 与 `SkillDagGraph`。
- Produces: `buildAgentSkillDagUrl(agentId, versionNo)`。
- Produces: `buildSkillResolutionRows(resolvedSkillVersions)`。

- [ ] **Step 1: 写 Builder 失败测试**

断言 URL 包含编译返回的 `draftVersionNo`，无版本时不发送 `versionNo=undefined`；调试治理映射保留 Skill 代码、钉住版本、风险、Tool/KB 边界和来源。

```ts
expect(buildAgentSkillDagUrl("sales-agent", 4)).toBe("/agents/sales-agent/skill-dag?versionNo=4");
expect(buildAgentSkillDagUrl("sales-agent", null)).toBe("/agents/sales-agent/skill-dag");
expect(buildSkillResolutionRows([runtimeSkill])[0]).toMatchObject({ versionLabel: "v3", source: "WORKFLOW_PINNED" });
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts`

Expected: FAIL，原因是导出函数尚不存在。

- [ ] **Step 3: 实现请求状态与分段视图**

编译成功后使用 `draftVersionNo` 请求图；通过 `AbortController` 或递增 request id 丢弃旧响应。现有流程图保持默认，新增“Skill 依赖”分段按钮；加载、空态、失败重试由共享组件处理。

- [ ] **Step 4: 实现调试 Skill 解析链路**

在调试治理区域按优先级排序现有 `resolvedSkillVersions`，显示：Skill 名称/代码、`vN`、引用来源、风险、声明 Tool/KB 与最终范围。没有 Skill 时显示明确空态，不伪造解析记录。

- [ ] **Step 5: 运行 Builder 与共享组件测试**

Run: `cd frontend && npm test -- src/assistant/AgentBuilderShell.test.ts src/shared/SkillDependencyGraph.test.tsx`

Expected: PASS。

- [ ] **Step 6: 提交 Task 4**

```bash
git add frontend/src/assistant/AgentBuilderShell.tsx frontend/src/assistant/AgentBuilderShell.test.ts frontend/src/assistant/cici-ui.css
git commit -S -m "feat: show skill DAG in agent builder"
```

---

### Task 5: 平台 Skill 影响下钻

**Files:**

- Create: `frontend/src/platform/pages/PlatformSkillsPage.test.ts`
- Modify: `frontend/src/platform/pages/PlatformSkillsPage.tsx`
- Modify: `frontend/src/shared/skill-dag.ts`

**Interfaces:**

- Consumes: Task 3 的共享组件与图类型。
- Produces: `buildPlatformSkillDagUrl(skillId)`。
- Produces: `shouldAcceptSkillDagResponse(requestedSkillId, selectedSkillId, requestSequence, latestSequence)`。

- [ ] **Step 1: 写平台请求竞态失败测试**

```ts
expect(buildPlatformSkillDagUrl(42)).toBe(`${PLATFORM_API_BASE}/skills/42/dependency-graph`);
expect(shouldAcceptSkillDagResponse(42, 43, 2, 2)).toBe(false);
expect(shouldAcceptSkillDagResponse(42, 42, 1, 2)).toBe(false);
expect(shouldAcceptSkillDagResponse(42, 42, 2, 2)).toBe(true);
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd frontend && npm test -- src/platform/pages/PlatformSkillsPage.test.ts`

Expected: FAIL，原因是 URL 和竞态函数尚不存在。

- [ ] **Step 3: 实现依赖图加载与影响区**

选择 Skill 时与版本请求并行加载影响图；切换选择后旧响应不得写入。将共享图放在现有“影响摘要”之后，保留四个汇总数字；加载失败只影响依赖关系区，不阻断版本治理和发布回滚。

- [ ] **Step 4: 运行平台与全量前端测试**

Run: `cd frontend && npm test`

Expected: PASS，所有 Vitest 文件和断言通过，stderr 无新增 warning。

- [ ] **Step 5: 运行生产构建**

Run: `cd frontend && npm run build`

Expected: PASS，TypeScript 与 Vite 均成功，产物无 unresolved import。

- [ ] **Step 6: 提交 Task 5**

```bash
git add frontend/src/platform/pages/PlatformSkillsPage.tsx frontend/src/platform/pages/PlatformSkillsPage.test.ts frontend/src/shared/skill-dag.ts
git commit -S -m "feat: add skill impact graph drilldown"
```

---

### Task 6: 全量验证、桌面验收与生产发布

**Files:**

- Modify: `docs/specs/FEAT-117-skill-dag-governance-phase1.md`
- Modify: `.claw/tasks/TASK-212.md`
- Modify: `.claw/test-report.md`
- Modify: `.claw/current-status.md`
- Modify when verified operational facts changed: `.claw/devops.md`

**Interfaces:**

- Consumes: Tasks 1-5 的完整功能。
- Produces: 可审计测试、截图、发布版本、回滚点和线上 smoke 事实。

- [x] **Step 1: 运行后端全量诊断与聚焦发布门**

Run: `cd backend && mvn test`

Actual: 聚焦 9 类 / 22 项、HTTP 权限集成与 package 通过；完整诊断 341 项中的 3 failure / 7 error 为已记录的任务外既有基线，未误报全量通过。

- [x] **Step 2: 运行前端全量测试和构建**

Run: `cd frontend && npm test && npm run build`

Expected: PASS，测试与构建均成功。

- [x] **Step 3: 启动本地应用并做桌面验收**

按仓库既有本地启动方式启动后端与前端，在 `1600 × 1000` 验证 Agent Builder 与 `/platform/skills`：工作流/Skill 依赖切换、缩放、适配、节点选择、调试解析链路、空态、失败重试，控制台 error/warning 0、外层横向溢出 0。截图保存到不纳入应用产物的 `output/playwright/task212-*.png`。

- [x] **Step 4: 更新规格与真实验证记录**

只把真实命令、计数、截图路径和发现写入 `.claw/test-report.md`；将 FEAT-117 实现进展和 TASK-212 当前状态更新为 `review`，不得预写发布成功。

- [x] **Step 5: 请求独立代码审查并修复发现**

使用 `superpowers:requesting-code-review` 检查规格符合性、权限/租户边界、版本钉住、前端竞态与测试缺口；每个确认问题先补失败测试再修复。

- [ ] **Step 6: 执行生产发布 dry-run**

Run: `./scripts/release-acr.sh --dry-run`

Expected: 版本号、Git tag、`CICI_APP_VERSION`、`VITE_CICI_APP_VERSION`、`CICI_IMAGE_TAG` 完全一致，目标为新的不可变版本。

- [ ] **Step 7: 备份并发布不可变版本**

严格执行 `docs/production-release-runbook.md` 的备份、构建、推送和部署步骤；应用发布失败立即回滚到发布前记录的不可变应用版本，状态服务保持原容器与版本。

- [ ] **Step 8: 生产 smoke 与桌面复验**

验证健康接口、Flyway 已为 V81、Agent Skill DAG、Skill 影响图、Builder 和平台页；记录 API 状态/时延、控制台 0 error/warning、页面无横向溢出和截图证据。

- [ ] **Step 9: 完成治理状态并提交**

将 FEAT-117、TASK-212、`current-status.md`、`test-report.md` 更新为真实生产结果，assignment 标记 `completed`；提交并推送，不包含凭据、Cookie、生产数据或截图中的敏感信息。

```bash
git add docs/specs/FEAT-117-skill-dag-governance-phase1.md .claw/tasks/TASK-212.md .claw/assignments/TASK-212.yaml .claw/current-status.md .claw/task-board.md .claw/test-report.md
git commit -S -m "docs: record TASK-212 production release"
git push origin codex/TASK-212-skill-dag-governance
```

---

## Plan Self-Review

- Spec coverage: Agent 图、Skill 影响图、调试解析链路、统一组件、权限/租户、安全、空态/错误态、桌面验收和生产发布均有对应任务。
- Scope: 除 V81 两条只读影响查询索引及其 Flyway 非事务/session-lock 配置外，没有业务结构/数据迁移、编辑器、Skill-to-Skill 调用、移动端或视觉语言变更。
- Type consistency: 后端统一使用 `GraphView`，前端统一使用 `SkillDagGraph`；Agent URL 使用可选 `versionNo`，Skill URL 使用数字 ID。
- Placeholder scan: 无 `TBD`、`TODO`、未定义接口或省略实现步骤。
