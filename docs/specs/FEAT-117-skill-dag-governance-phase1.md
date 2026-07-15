---
kind: feature-spec
feature_id: FEAT-117
title: Skill DAG 只读治理闭环 Phase 1
status: implemented
owner_role: fullstack-agent
task_ids: TASK-212
related_decisions: none
related_issues: none
updated_at: 2026-07-15T17:22:27Z
updated_by: MANAGER-001
---

# FEAT-117 - Skill DAG 只读治理闭环 Phase 1

## 1. 背景与目标

当前系统已具备 Skill 定义、Skill 版本、Agent-Skill 绑定、工作流版本对 Skill 版本的编译期钉住，以及运行时工具/知识库边界解析。但这些事实分散在多个接口和页面中，用户只能看到数量摘要或通用工作流图，无法直接回答以下治理问题：

- 某个 Agent 的已编译工作流实际钉住了哪些 Skill 版本。
- 每个 Skill 版本允许调用哪些 Tool、引用哪些知识库。
- 一个 Skill 被哪些 Agent、工作流版本和已发布版本依赖。
- 调试时生效的是当前 Skill 配置，还是工作流已钉住的历史版本。

Phase 1 的目标是在不引入可编辑编排器、不改变运行语义、不新增数据库表的前提下，把现有依赖事实收敛成一份可查询、可视化、可追溯的只读 Skill DAG，并完成生产上线。

用户已于 2026-07-15 明确批准本 Phase 1 范围并要求达到生产就绪后发布线上环境。

## 2. 方案决策

采用“运行时派生的统一只读图模型”。服务层从现有 `agent_workflow_skill_ref`、`agent_workflow_version`、`skill_version`、`skill_definition`、`agent_skill_binding`、工具与知识库元数据组装 DAG；Agent Builder 和平台技能治理页使用同一份节点/边契约。

未采用的方案：

- 独立持久化 DAG 表：会复制现有依赖事实并引入一致性、迁移和回填成本，Phase 1 无必要。
- 可拖拽 DAG 编辑器：会改变自然语言 Spec 编译工作流的产品方向，并需要拓扑校验、保存协议和执行语义，留待后续单独立项。
- 仅在前端拼接关系：无法统一租户隔离、版本选择和影响分析，也无法成为 API 与审计事实源。

## 3. 用户与核心场景

- Agent 构建者：编译后在构建页切换到“Skill 依赖”，确认 Agent、工作流版本、Skill 版本、Tool、知识库的真实关系。
- 调试人员：执行调试后查看“Skill 解析链路”，区分工作流钉住版本、Skill 声明边界和最终有效边界。
- 平台运营管理员：在平台标准技能页展开“依赖关系”，定位受影响的 Agent 与工作流版本，而不是只看汇总数字。
- 发布与回滚人员：发布 Skill 或 Agent 前确认依赖范围；回滚时确认目标版本仍被哪些工作流钉住。

## 4. 范围

### 4.1 In Scope

- 新增统一 Skill DAG 只读模型与查询服务。
- 新增 Agent 维度依赖图接口，支持按 `versionNo` 查询；未指定时优先当前已发布工作流，若无已发布版本则使用最新编译版本。
- 新增 Skill 维度依赖图接口，展示当前 Skill、版本、工作流与 Agent 的入向影响关系。
- Agent Builder 编译结果新增“工作流 / Skill 依赖”分段视图。
- Agent Builder 调试结果新增结构化“Skill 解析链路”，复用现有 `resolvedSkillVersions` 事实。
- 平台标准技能页将影响摘要补齐为可下钻的依赖图。
- 后端服务、权限、租户隔离、控制器、前端状态与交互测试。
- V81 为工作流引用与当前 Agent 绑定两条 Skill 影响查询增加匹配的复合索引；索引使用 `CREATE INDEX CONCURRENTLY`、迁移不进入事务，并关闭 Flyway PostgreSQL transaction-level advisory lock，不改变业务数据与执行语义。
- 桌面端真实页面截图、空态/加载态/错误态和交互检查。
- 按生产发布手册完成不可变版本发布、健康检查、功能 smoke 和回滚点记录。

### 4.2 Out Of Scope

- Skill-to-Skill 依赖声明、子 Skill 调用和递归执行。
- 拖拽、连线、增删节点、保存或发布 DAG。
- 新增 `invokeSkill` 执行原语或将 Skill 编译为独立子流程。
- 新增图持久化表、历史数据回填或异步图索引；除 V81 只读查询索引外不新增业务结构迁移。
- 通用拓扑编辑、循环依赖编辑校验和跨组织依赖图。
- 移动端布局、截图或自动化测试。

## 5. 统一图模型

### 5.1 节点

统一节点字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 稳定节点 ID，格式为 `<type>:<业务主键>`，不得包含组织外信息 |
| `type` | `AGENT`、`WORKFLOW_VERSION`、`SKILL`、`SKILL_VERSION`、`TOOL`、`KNOWLEDGE_BASE` |
| `label` | 用户可读名称 |
| `detail` | 版本、状态或边界摘要 |
| `status` | 发布/草稿/停用/历史等稳定状态；无状态时为空字符串 |
| `layer` | 从 0 开始的展示层级，仅用于稳定布局 |
| `metadata` | 当前节点必要的只读字段，不返回 Prompt 正文、密钥或敏感配置 |

Agent 图的层级固定为：

1. Agent。
2. Workflow Version。
3. Skill。
4. Skill Version。
5. Tool / Knowledge Base。

Skill 影响图的层级固定为：

1. Skill。
2. Skill Version。
3. Workflow Version；尚未编译的当前绑定以绑定关系节点元数据表达，不伪造工作流版本。
4. Agent。

### 5.2 边

| `type` | 语义 |
| --- | --- |
| `COMPILED_AS` | Agent 编译为某工作流版本 |
| `BINDS_SKILL` | Agent 当前绑定 Skill |
| `CURRENT_SKILL_VERSION` | Skill 当前展示版本；仅用于没有编译版本的当前绑定图 |
| `USES_SKILL` | 已编译工作流引用 Skill |
| `PINS_SKILL_VERSION` | 工作流版本钉住 Skill 版本 |
| `VERSION_OF` | Skill Version 属于 Skill |
| `ALLOWS_TOOL` | Skill 版本允许调用 Tool |
| `ALLOWS_KNOWLEDGE_BASE` | Skill 版本允许引用知识库 |
| `USED_BY_AGENT` | Skill 影响图中工作流/绑定归属 Agent |

所有节点和边按 `layer`、`type`、业务键稳定排序并去重。服务端必须检测无效端点与重复边；发现不完整历史引用时返回 `warnings`，不跨租户补查，也不让整张图 500。

### 5.3 版本选择

- 显式 `versionNo`：只读取该 Agent 的指定工作流版本；不存在时返回标准未找到错误。
- 未显式指定：优先 `agent_definition.published_version_id` 对应版本；其次最新工作流版本。
- 没有任何编译版本：返回 Agent 与当前启用 Skill 绑定的只读草稿图，`sourceMode = CURRENT_BINDINGS`。
- 已编译版本：只以 `agent_workflow_skill_ref` 的钉住引用为准，`sourceMode = PINNED_WORKFLOW_VERSION`；不得静默替换为 Skill 当前版本。
- 历史工作流引用回填时，Manifest 明确记录的 Skill `versionNo` 必须精确解析；显式版本不存在时保留缺失钉住引用并在图中告警，运行时对 Prompt、Tool、知识库、移交与输出边界 fail-closed，不得回退到可变的当前 Skill 定义。只有 Manifest 未记录版本号时，才按当前发布版本、最新发布版本、最新版本依次降级。
- 编译指纹必须包含排序后的 Skill 代码、Skill ID、解析版本号、来源和解析状态；同一 Skill 升级版本后即使其他编译输入不变，也必须生成新的工作流版本并固化新引用。

## 6. API 设计

### 6.1 Agent Skill DAG

`GET /agents/{agentId}/skill-dag?versionNo={optional}`

- 权限：沿用 `AgentPermission.VIEW`。
- 租户：只读取 `TenantContext.requireOrgId()` 下的 Agent 与依赖。
- 返回：`scope`、`sourceMode`、`nodes`、`edges`、`summary`、`warnings`。
- `summary` 至少包含 `agentCount`、`workflowVersionCount`、`skillCount`、`skillVersionCount`、`toolCount`、`knowledgeBaseCount`。

### 6.2 Skill 依赖影响图

后端：`GET /platform/skills/{id}/dependency-graph`

前端运营入口：`GET /api/platform/skills/{id}/dependency-graph`，由现有代理规则重写到后端 `/platform` 路由。

- 权限：沿用 `PlatformController` 类级 `@RequirePlatformRole`，与平台技能列表一致。
- 组织边界：使用 `PlatformAccountProperties.governanceOrgId` 的治理组织，禁止使用调用者组织 token 覆盖平台治理范围。
- 返回：同一图契约，`scope.type = SKILL_IMPACT`；包含当前绑定 Agent、所有被引用 Skill 版本、相关工作流版本与发布状态。
- 影响图只返回当前组织数据；历史版本存在但工作流已删除时，以 warning 说明，不伪造 Agent。

### 6.3 向后兼容

- 现有编译、调试、Skill 列表与版本接口字段不删除、不改名。
- 调试页面继续读取现有 `resolvedSkillVersions`；本阶段只新增展示结构，不改变调试响应语义。
- V81 仅增加 `agent_workflow_skill_ref(org_id, skill_id, skill_version_id, workflow_version_id)` 与 `agent_skill_binding(org_id, skill_id, enabled, agent_id, priority)` 两个查询索引，不写业务数据；应用回滚即可移除接口与 UI，索引可安全保留，无数据回滚步骤。

## 7. 前端产品设计

### 7.1 共用依赖图组件

- 新增共享只读依赖图组件，接收统一节点/边契约。
- 使用分层 DAG 布局、稳定节点尺寸、曲线连线、缩放/适配/重置图标按钮和节点详情区。
- 节点使用类型、名称、版本/状态三层信息；长文本截断并通过原生标题或详情区完整展示。
- 颜色遵循 `鎏金账房`：暖象牙底、墨色文字、香槟金结构线，并使用克制的绿/蓝/红表达资源和风险状态；不得引入紫色渐变、玻璃拟态或营销式卡片。
- 图为空时展示明确空态；加载失败时保留重试命令；节点/边数据不合法时显示 warning，不渲染破损连线。

### 7.2 Agent Builder

- 在现有编译结果图区域增加“工作流 / Skill 依赖”分段控件，默认保留“工作流”。
- 编译成功后按 `draftVersionNo` 拉取 Skill DAG；切换版本历史或重新编译时刷新。
- 没有 Skill 时显示“当前版本未解析到 Skill 依赖”，不把 Agent 直接工具绑定误标成 Skill 依赖。
- 调试成功后在现有治理摘要附近增加“Skill 解析链路”，显示 Skill 名称/代码、钉住版本、风险等级、工具与知识库边界、引用来源。

### 7.3 平台标准技能

- 在“影响摘要”后增加“依赖关系”区；选择技能时并行加载依赖图。
- 点击图中工作流或 Agent 节点只更新右侧详情，不在 Phase 1 引入跨页编辑或跳转。
- 保留原有汇总数字，依赖图作为下钻事实，不替代发布/回滚控制。

## 8. 错误处理与安全

- Agent 不存在、版本不属于 Agent、Skill 不存在：使用项目统一 404/参数错误语义。
- 无 `VIEW` 或组织管理员权限：沿用现有 403 门禁。
- 所有 Repository 查询必须包含 `orgId`；不得按裸 ID 读取后再在内存中过滤。
- `metadata` 不返回 Prompt 正文、输出契约、密钥、Token、连接参数或知识库内容。
- 历史引用中的 Skill Version 缺失时保留 Skill 节点并产生 warning；Tool/KB 元数据缺失时使用稳定业务键作为降级标签。
- 前端请求竞态以当前选中 Agent/Skill 为准，旧响应不得覆盖新选择；选择详情加载期间所有写入口同步禁用并在函数入口二次校验。保存、编译、发布、回滚或调试操作进行时同步锁住 Agent 选择，操作请求冻结目标 ID、Draft/Governance 快照和操作序号；每次异步回写前必须确认目标与序号仍为当前值。

## 9. 测试与验收标准

### 9.1 后端

- 服务测试覆盖：已发布版本优先、显式版本、最新版本回退、无版本当前绑定、版本钉住不漂移、工具/知识库边界、去重和稳定排序。
- 影响图覆盖：当前绑定、已发布工作流、历史钉住版本、缺失历史引用、跨租户不可见，以及工作流引用和当前绑定分别超过 1,000 条时的稳定截断与 warning。
- 控制器覆盖 Agent `VIEW` 与 Skill 组织管理员权限。
- 相关 Maven 测试必须通过；完整后端诊断不得出现 TASK-212 新增失败，若命中仓库既有基线，必须逐项记录来源且不得误报全绿。

### 9.2 前端

- 共享组件覆盖节点/边渲染、空态、warning、节点选择和适配控制。
- Agent Builder 覆盖编译后拉取、版本参数、工作流/Skill 依赖切换、失败重试和调试解析链路。
- 平台技能页覆盖选择切换、依赖图加载、竞态保护、错误态和影响详情。
- 前端全量测试与生产构建通过。

### 9.3 桌面端产品质量门

- 在 `1600 × 1000` 桌面视口验证 Agent Builder 和 `/platform/skills`。
- 真实数据至少包含 1 个 Agent、1 个工作流版本、1 个 Skill 版本、1 个 Tool；知识库为空与非空各验证一次。
- 缩放、适配、节点选择、分段切换、空态、错误态可用。
- 控制台 error/warning 为 0；页面外层无横向溢出；文本不遮挡节点或控制条。
- 不做移动端验收。

### 9.4 生产发布门

- 先按 `docs/production-release-runbook.md` 执行 `./scripts/release-acr.sh --dry-run`。
- 版本号、Git tag、镜像 tag 与前后端版本变量保持一致并使用不可变版本。
- 发布前备份，记录当前可回滚应用版本；状态服务不得无故重建。
- 发布后验证应用健康、V81 成功、Agent Skill DAG、Skill 影响图、构建页和平台页真实请求。
- 生产桌面 smoke 的控制台、网络错误和关键页面截图通过后方可标记完成。

## 10. 风险与回滚

- 图节点较多导致布局过宽：组件提供适配与缩放，默认限制展示区域高度，页面外层不横向溢出。
- 历史引用数据不完整：以 warning 降级，不修改历史数据。
- 影响查询放大数据库负载：仅按当前组织和目标 Agent/Skill 查询，将工作流引用与当前绑定各限制为 1,000 条，并由 V81 两个匹配复合索引支撑；两个索引并发创建，避免阻塞生产写入。迁移在创建前并发删除同名索引，确保失败遗留的 INVALID 索引在 Flyway repair 后可安全重建；生产 smoke 记录接口时延。
- 前端竞态显示错误依赖或旧操作结果：依赖图、就绪检查和目标操作均使用序列标识，只接受当前选择的最新请求；目标操作进行期间禁用对象切换作为交互层第二道保护。
- 上线失败：回滚到发布前不可变应用版本；V81 只有可安全保留的索引，无业务数据回滚步骤。

## 11. 实现进展

- 已完成：现状审计、差距分析、范围确认、架构设计、TDD 实现、权限与双向竞态加固、版本感知编译指纹、历史引用精确回填与缺失版本全运行链 fail-closed、聚焦后端 22 项验证、前端 110 项测试与生产构建、V81 干净库正向迁移及重复执行验证、真实 API 权限矩阵和桌面端验收。
- 完整后端诊断：341 项中 3 failure / 7 error，均来自既有平台身份、审计夹具、非空字段、模型配置与连接池基线；TASK-212 聚焦测试无失败，未宣称全量套件通过。
- 合并与发布：PR #10 合并为 `4814d2b9534d`；Git tag、backend/frontend 镜像和应用版本统一为 `2.7.8`。发布前备份目录为 `/opt/cici/backups/20260716-011129-before-2.7.8-task212-skill-dag`，四项产物均非空。
- 生产迁移：Flyway V81 成功；`idx_agent_workflow_skill_ref_org_skill_impact` 与 `idx_agent_skill_binding_org_skill_impact` 均为 `indisvalid=true / indisready=true`。发布仅重建 backend/frontend，四个状态服务容器 ID 未改变。
- 生产接口：匿名 Agent 图 401，组织 token Agent 图与显式 `versionNo=50` 均 200，平台 token Agent 图 403；组织 token 平台图 403，平台 token 平台图 200。实测 Agent 图 24 节点 / 32 边，平台图 6 节点 / 9 边，单次请求约 0.16-0.21 秒。
- 生产桌面验收：Agent Builder 与 `/platform/skills` 在 `1600 x 1000` 下完成真实图、缩放和节点详情点验；页面外层无横向溢出，console warning/error 为 0，稳定窗口 backend ERROR 与 Nginx 精确 5xx 均为 0。
- 当前：Phase 1 已在生产 `2.7.8 / 4814d2b9534d` 完成并关闭。`onechat.agentcici.com` 仍有既有 DNS 解析风险，显式生产 IP vhost 验证为 HTTP 301 / HTTPS 200；主入口 `x.agentcici.com` 为 HTTP 301 / HTTPS 200。
- 未完成：无。编辑 DAG、Skill-to-Skill 依赖与独立子流程执行仍属于明确的 Phase 2 候选范围。

## 12. 交接说明

- 任务事实源：`.claw/tasks/TASK-212.md`。
- 授权事实源：`.claw/assignments/TASK-212.yaml`。
- 实现必须复用现有 Skill 版本钉住事实，不以 Skill 当前版本覆盖历史工作流引用。
- 不得把只读治理图扩张为编辑器或新增 Skill-to-Skill 执行语义。
