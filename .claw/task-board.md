---
kind: task-board
version: 3
updated_at: 2026-04-30T11:54:33Z
updated_by: ai
status: active
board_status: active
---

# Task Board

## Task Cards

### TASK-027 Project-wide impeccable design governance

- status: completed
- priority: P1
- owner_role: design-governance
- spec_path: `docs/specs/FEAT-012-project-design-governance.md`
- summary: 将 `impeccable` 固化为项目级页面设计规范，并把设计事实源与执行入口同步到 `AGENTS.md`、`README.md`、`PRODUCT.md`、`DESIGN.md` 与 `DESIGN.json`。
- done:
  - 已在 `AGENTS.md` 中加入 `impeccable` 的项目级强约束，覆盖预检、默认 register、设计事实源和禁用模式。
  - 已在 `README.md` 中补充面向开发者的人类可读设计治理入口。
  - 已将根 `PRODUCT.md` 从平台单页上下文扩展为全项目认证产品面的统一战略上下文。
  - 已将 `DESIGN.md` / `DESIGN.json` 扩展为 assistant、admin、platform 共用的产品面设计基线，并保留 `鎏金账房` 作为默认视觉语言。
  - 已新增 `docs/specs/FEAT-012-project-design-governance.md` 作为本轮规范落地的事实源，并在 `decisions.md` 中记录项目级设计治理决策。
- next_action: 后续任意页面改版先按 `AGENTS.md` 的 `impeccable` 预检执行；若引入品牌页或活动页，先单独 `shape` 并补 spec。
- handoff_notes:
  - 本任务只改治理文档，不改业务逻辑或页面实现。
  - `/`、`/admin/*`、`/platform/*` 默认都按 `product` register 处理，但允许按任务流在密度和节奏上做细分。

### TASK-026 Assistant workbench rail cleanup and reorder

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 收口前台会话工作台左侧 rail：去掉重复悬浮提示，调整头像与 logo 的上下位置，并把 rail 的整体配色切到和主页面一致的暖白金线风格。
- done:
  - 已移除左侧 rail 按钮上的原生 `title` 提示，仅保留 `data-menu-label` 自定义 tooltip，避免重复悬浮提示。
  - 已将个人头像按钮移到左侧 rail 顶部，将 `CB` logo 调整到底部区域。
  - 已将 rail 的背景、边框、图标按钮、active 态和 tooltip 全部改成与主页面一致的暖白、香槟金、墨色语言。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做视觉验收，优先在浏览器检查 rail 的 hover/active 态、tooltip 位置，以及头像/底部 logo 的层级是否顺眼。
- handoff_notes:
  - 本轮只调整 `frontend/src/assistant/AssistantApp.tsx` 与 `frontend/src/assistant/cici-ui.css` 的结构与样式，不涉及路由逻辑或功能语义。
  - 当前 `task-board.md` front matter 里保留了历史重复 `updated_at` 字段，后续如继续整理状态文件可顺手清理。

### TASK-025 Assistant workbench sidebar state layout refinement

- status: completed
- priority: P1
- owner_role: frontend-assistant-workbench
- summary: 按用户标注继续收口前台会话工作台，移除右侧旧头像状态卡，将左侧状态机搬到右侧侧栏顶端，并删除状态机右侧摘要区，让“今日工作概览”下移后与会话历史衔接。
- done:
  - 已将 `WorkbenchAgentBar` 收口为仅保留智能体切换条，移除左侧顶部完整状态机。
  - 已新增右侧顶部精简 `WorkbenchStateCard`，保留头像、名称、状态、上一项、当前、下一项，并去掉右侧过程摘要区。
  - 已移除右侧原有“思思头像 + 空闲中”独立状态卡。
  - 已将“今日工作概览”与“会话历史”合并到侧栏下半区，形成连续衔接的纵向结构。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做人工验收，优先在浏览器检查会话工作台桌面端与窄屏下的侧栏间距、状态机换行和会话历史滚动表现。
- handoff_notes:
  - 本轮只调整 `frontend/src/assistant/AssistantApp.tsx` 与 `frontend/src/assistant/cici-ui.css` 的结构和样式，不涉及聊天逻辑、状态机数据模型或接口。
  - 用户明确要求：右侧顶部去掉旧头像状态条，左侧状态机移到右侧最上方，并删除状态机右侧部分。

### TASK-024 Platform console visual refresh

- status: completed
- priority: P1
- owner_role: frontend-product-platform
- spec_path: `docs/specs/FEAT-011-platform-console-visual-refresh.md`
- depends_on: `docs/specs/FEAT-010-platform-operations-console.md`
- summary: 在不改业务功能的前提下，整体重构 `/platform/login` 与 `/platform/*` 的样式、层级和工作区布局，改为浅色、克制、紧凑的平台运营控制面。
- done:
  - 已完成 `impeccable` shape：确认核心用户为平台运营，整体方向以 Stripe Dashboard 式清晰克制为主，选定视觉探针 `2`。
  - 已补齐 `PRODUCT.md`、`DESIGN.md` 与 `DESIGN.json`，作为本轮平台控制面视觉重构的设计事实源。
  - 已新增 `docs/specs/FEAT-011-platform-console-visual-refresh.md`，明确范围仅包含 `/platform/login + /platform/*` 的前端样式与结构优化。
  - 已完成 `PlatformLogin`、`PlatformShell`、`PlatformHomePage`、`PlatformSkillsPage`、`PlatformToolsPage`、`PlatformAuditPage` 的结构与信息层级重构。
  - 已将平台页 scoped 主题从深色渐变/玻璃化风格切换为浅色、克制、紧凑的控制台样式，并补齐统一的页头、工作区、按钮、表格、表单和提示条语言。
  - 已完成前端验证：`frontend npm run build` 通过。
- next_action: 如继续做视觉验收，优先在浏览器人工检查 `/platform/login`、概览、技能页、工具页、审计页的桌面/窄屏表现；否则回到 `TASK-023` 项目主线。
- handoff_notes:
  - 用户明确要求“不涉及功能改动，只做页面样式和结构美化”。
  - 视觉要求已明确：浅色、紧凑、克制，不要花哨渐变、不要大面积深色、不要玻璃拟态、不要营销感。
  - 注意平台页大量复用 `skills-*` / `platform-console-*` 类名，改动时既要统一语言，也要避免误伤 `/admin/*`。

### TASK-020 Knowledge base lifecycle completion

- status: paused
- priority: P0
- owner_role: fullstack-knowledge-platform
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- depends_on: `ISSUE-2026-04-29-kb-delete-leaves-vector-points`
- summary: 补齐管理端知识库完整生命周期，并按 Dify Knowledge 对标补齐数据源、文件解析、切片设置、索引/检索参数、内容运营、元数据、检索测试和 API 运营能力；P0 仍先修复文档/知识库删除后 DB chunk、源文件、向量库 point、Agent KB 绑定不同步清理的问题。
- done:
  - 已完成现状代码检查与功能缺口盘点。
  - 已新增 `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md` 作为后续实现 source of truth。
  - 已按 Dify 官方 Knowledge 文档补充对标缺口：数据源、切片预览、High Quality/Economical、hybrid/rerank、文档/chunk 启停编辑、metadata filtering、retrieval test/log、Service API。
  - 已完成 FEAT-008 P0 第一阶段实现：`V27__kb_lifecycle_completion.sql` 增加 KB/document/chunk 生命周期字段；`VectorStoreClient` 支持结构化 hit 和按 vectorId/document/KB 删除；memory/Qdrant 适配器已实现删除契约。
  - 已修复 `deleteDocument/deleteKnowledgeBase/unpublish/reindex`：同步清理 DB chunk、源文件、向量点，删除 KB 时清理 Agent KB 绑定；重复发布先清旧索引，避免重复可检索 chunk。
  - 已收口 `RagService`：向量召回必须回 DB 校验 KB/document/chunk 状态，DB fallback 也只返回有效状态内容。
  - 管理端已补最小生命周期入口：重建索引、下线、删除确认文案、chunk 数、清理中/清理失败状态展示。
  - 已新增 `KnowledgeBaseLifecycleIntegrationTest` 覆盖删除文档后不再召回、取消发布后不再召回、重复重建不重复、删除 KB 级联清理绑定。
  - 已完成 FEAT-008 P0 第二阶段第一批：新增 `V28__kb_chunking_and_retrieval_settings.sql`，落地 KB `chunk_size/chunk_overlap/chunk_delimiter/top_k/score_threshold/retrieval_strategy` 与 `kb_retrieval_log`。
  - 已新增后端 API：`GET/PUT /kb/{id}/settings`、`POST /kb/{id}/chunking/preview`、`POST /kb/{id}/retrieval/test`、`GET /kb/{id}/retrieval/logs`。
  - `KnowledgeBaseService` 已按 KB 设置参与分片与检索：发布索引按 KB chunk 参数切片，retrieval test 支持 topK/scoreThreshold 并写入检索日志。
  - 管理端设置页已接入切片参数、chunk preview、检索参数、retrieval test 和最近检索日志展示。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkPreviewAndRetrievalTestWithKbSettings`。
  - 已完成 FEAT-008 P0 第二阶段第二批：新增迁移 `V29__kb_metadata_and_chunk_ops.sql`；补文档 rename/enable/disable/archive、chunk list/update/enable/disable/delete、metadata field 与 document metadata API。
  - retrieval test 已支持 `metadataFilters`，并可结合文档 metadata 做过滤召回。
  - 管理端文档列表已接入：重命名、文档启停、归档、切片管理弹层（编辑/启停/删除）、文档 metadata 编辑；设置页新增 metadata 字段管理与 retrieval metadata filter 输入。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkToggleAndMetadataFilteringInRetrievalTest`。
  - 已完成 FEAT-008 P0 第二阶段第三批：后端新增文档与切片批量操作 API（enable/disable/archive/unarchive/delete），前端新增批量勾选与批量动作条，并补失败明细提示。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportBatchDocumentOperations`。
  - retrieval test 新增 metadata filter 字段校验（未知字段会返回明确错误），前端补“可用字段”提示，降低回归与运维排错成本。
  - 批量操作按钮已补禁用态与“已选数量”提示，避免空操作请求。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldRejectUnknownMetadataFilterField`。
  - 已完成管理端页面视觉收尾第一批：按钮从高饱和渐变改为简洁实色；侧边栏/表格/输入框/弹层统一为紧凑中性风格；失败态/批量区信息密度增强。
  - 已完成管理端页面 UX 收口第二轮：metadata/doc metadata 输入新增格式校验提示；检索测试与切片预览补空结果反馈；文档/切片批量条新增“清空选择”；失败态错误文案统一样式；设置区网格改为响应式布局。
  - 已完成前端验证：`frontend npm run build` 通过（含第二轮 UX 调整）。
  - 已完成真实后端接口回归：建库、metadata 字段创建、unknown metadata 字段报错、文档上传发布、metadata 过滤检索、检索日志、文档批量启停均通过。
  - 已完成前端 UX 收口第三批：检索 metadata 字段与文档 metadata 字段新增前端字段存在性校验；输入变化时自动重置空结果提示，避免提示残留。
  - 已完成前端验证：`frontend npm run build` 通过（含第三批 UX 调整）。
  - 已完成前端 UX 收口第四批：批量操作结果新增内联反馈块（成功/部分失败 + 失败样本）并可关闭；切片预览与检索测试按钮在空输入时禁用。
  - 已完成回归复核：重启本地后端后再次执行 `/tmp/feat008_reg.sh`，建库/metadata 字段/unknown field 报错/文档上传发布/metadata 检索/检索日志/批量启停均通过。
  - 已完成前端验证：`frontend npm run build` 通过（含第四批 UX 调整）。
- next_action: 用户已要求暂停 FEAT-008；待恢复后从页面级人工回归（文档/设置/切片弹层）继续，确认第四批“批量反馈块 + 按钮禁用态”无回归。
- handoff_notes:
  - 用户已点名“删除知识文档后没有同步删除向量库内容”；该问题已验证且是本任务最高优先级。
  - P0 第一阶段已经保证删除/下线后即使向量库残留，也会被 DB 状态二次过滤拦住；历史无 `chunk_id` 的旧向量命中不会直接信任 payload content。
  - 2026-04-29：用户明确要求“先保持当前状态，暂停 FEAT-008”；本卡暂停，不继续提交新代码。

### TASK-023 CloudCC runtime smoke unblock

- status: in_progress
- priority: P0
- owner_role: backend-external-integration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-08-cloudcc-token-invalid-credential`, `ISSUE-2026-04-30-chat-smoke-blocked-by-aliyun-api-key`
- summary: 收口 CloudCC 真实工具 smoke 的最后两处阻塞，先把真实绑定用户凭证与本地模型入口配置校正到可复跑状态，再补一轮真实 `/ai/chat` 与工具调用验收。
- done:
  - 已完成 CloudCC 运行态第一轮 smoke，确认 `POST /mcp-servers/1/health` 与 `GET /mcp-servers/1/tools` 均成功，MCP server 连通与缓存快照可用。
  - 已确认 CloudCC 组织网关解析正常，`orgapi_address=https://szyd.apis.cloudcc.cn/lightningapi`。
  - 已确认真实绑定用户 `13800000001/哪吒` 当前换取 CloudCC token 返回 `Please check your username and password.`，阻塞点收敛到用户绑定凭证而非组织级配置。
  - 已确认 `/ai/chat` 对 `sales-agent` 的 CloudCC 查询类请求当前会直接返回 `Aliyun API key is not configured.`；同次响应里的 `effectiveToolNames` 已包含 CloudCC 相关工具，说明问题发生在模型入口而非工具暴露面。
- next_action: 先轮换并核实 `cc_username/cc_safetymark`，再补齐本地可用 Aliyun API key，随后复跑真实 `/ai/chat` 与 CloudCC 工具触发链路。
- handoff_notes:
  - 先修用户凭证，再修模型入口，二者都不通时无法判断真实工具执行是否完全恢复。
  - 验证结果统一沉淀到 `.claw/test-report.md` 与 `.claw/issue-list.md`，不要把长命令日志堆回 `current-status.md`。

### TASK-021 Skill layering and governance

- status: completed
- priority: P0
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-009-skill-layering-and-governance.md`
- summary: 下一阶段完整实现 Skill 分层治理闭环：将当前 `builtin/custom` Skill 体系升级为“平台核心策略、平台标准 Skill、租户派生 Skill、租户自定义 Skill、隐藏平台服务能力”，并落地租户可见/可编辑规则、平台模板版本、PolicyBundle、Agent 发布版本 pin、影响分析、灰度与回滚。
- done:
  - 已新增 `docs/specs/FEAT-009-skill-layering-and-governance.md`，记录分类、数据模型、运行时解析、平台更新流、API、迁移阶段和验收标准。
  - 已新增迁移 `V30__skill_governance_and_platform_audit.sql`：为 `skill_definition` 补齐 `source_type/visibility/edit_policy/binding_policy/update_policy/template_code/base_template_version/current_published_version_id/latest_draft_version_id`。
  - 已将现有内置 Skill 回填为平台标准 Skill，并将 `conversation-core/knowledge-first/safe-handoff` 标为隐藏核心策略语义（`HIDDEN + MANDATORY`）。
  - 租户 `/skills` 列表已收口为仅展示可见 Skill；平台标准 Skill 变为 `CONFIGURABLE`，租户只能启停，不能直接修改正文或删除。
  - 已新增 `POST /skills/{id}/derive`，支持从平台标准 Skill 派生租户 Skill，并保留 `templateCode/baseTemplateVersion`。
  - `AgentSkillBindingService` 已补隐藏/强制 Skill 保护：Agent Builder 保存可见 Skill 时，不会误删既有隐藏 mandatory/internal bindings。
  - 已新增 `SkillGovernanceIntegrationTest`，验证核心 Skill 隐藏、平台标准 Skill 仅可配置启停，以及派生链路。
  - 已新增 `V31__platform_skill_template_and_tool_governance.sql`，落地 `platform_skill_template/platform_skill_template_version` 与 `platform_tool_definition`。
  - `/platform/skills` 已接入模板版本治理：支持列表、版本历史、创建草稿、发布、回滚，并把平台标准 Skill 同步到模板版本事实源。
  - 平台模板发布会同步更新标准 Skill 正文/工具边界/治理字段，并写入 `skill_version` 发布快照，供后续 pin/runtime 使用。
  - 已新增 `PlatformGovernanceIntegrationTest`，验证平台 Skill 模板草稿/发布、平台审计与租户工具目录联动。
  - 已新增 `V32__platform_policy_bundle_runtime_controls.sql`，落地 `platform_policy_bundle`，并把 `conversation-core/knowledge-first/safe-handoff` 种子为 `core-default@v1` 平台核心策略包。
  - `SkillResolverService` / `SkillPromptAssembler` 已改为先注入 `PlatformPolicyBundle`，聊天 `runtimePolicy` 与 debug/chat trace 会回显 bundle code/version。
  - 已新增 `V33__agent_workflow_skill_ref.sql`、`AgentWorkflowSkillRefService` 与发布时 skill snapshot 固定逻辑；`AgentDefinitionService.publishVersion(...)` 会为已发布 workflow 写入 pinned skill refs。
  - chat/debug 运行时已优先读取 pinned skill refs；`/ai/chat` 新增 `resolvedSkillVersions`，`OrchestratorIntegrationTest.shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits` 已验证 skill 后续编辑不会影响旧发布版本的 tool scope / version 摘要。
  - 已完成 S6 第一批治理收口：`/platform/policies/core` 新增核心 `PolicyBundle` 概览；`/platform/skills` 版本历史新增 impact 摘要、pinned workflow/agent 命中数、灰度与回滚提示。
  - `AgentWorkflowRuntimeService` / `/agents/{agentId}/debug` / `/ai/chat` 已新增 `policyBundle`、`runtimeGovernanceNotes` 与结构化 `resolvedSkillVersions` 摘要，方便运营排障时确认命中的平台策略与 pinned skill snapshot。
  - 已补并通过 `PlatformGovernanceIntegrationTest` / `OrchestratorIntegrationTest` 新断言；同时 `frontend npm run build` 通过。
  - 已完成 S6 第二批治理收口：`/platform/policies/core/versions` 支持核心策略包草稿创建、独立发布与回滚；平台治理页已新增 PolicyBundle 编辑器、版本历史与影响摘要。
  - 已补并通过 `PlatformGovernanceIntegrationTest` 核心策略包 draft/publish/rollback + 审计断言；`frontend npm run build` 通过。
  - Agent Builder 调试面板已改为“后端真实运行优先”：`runDebug()` 成功时直接消费 `/agents/{agentId}/debug` 的 runtime trace / governance 摘要，只在接口异常时才回退前端模拟路径。
  - 已补当前收口验证：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldUsePublishedWorkflowInDebugRuntime test` 与 `frontend npm run build` 通过。
  - 已完成页面级人工回归第一轮：平台登录、概览、平台技能、内置工具、平台审计已在本地浏览器验证通过；平台技能页成功创建 `core-default` 草稿 `v3`，平台审计出现对应创建记录。
  - 已修复前端平台页 dev proxy 回归：平台页原先直接请求 `/platform/**`，会与 SPA 路由前缀冲突并拿到 HTML；现已统一走 `/api/platform/**`，由 `vite.config.{js,ts}` rewrite 到后端 `/platform/**`，`frontend npm run build` 通过。
- implementation_plan:
  - S1 Schema 兼容层：为 `skill_definition` 增加 `source_type/visibility/edit_policy/binding_policy/update_policy/template_code/base_template_version/current_published_version_id/latest_draft_version_id` 等字段，保留 `builtin` 兼容。
  - S2 租户 Skill 语义收口：后端 CRUD 与前端 Skill Studio 按平台标准/派生/自定义执行可见、可编辑、可删除、可派生规则。
  - S3 平台标准 Skill 模板版本：新增 `platform_skill_template` / `platform_skill_template_version`，把 Java 内置种子迁移为平台模板来源，并实现标准 Skill 同步策略。
  - S4 平台核心策略包：新增 `platform_policy_bundle`，将底层必备兜底策略从普通租户 Skill 中剥离，运行时先注入 policy bundle。
  - S5 Agent 发布版本 pin：新增 `agent_workflow_skill_ref`，发布时固定具体 `SkillVersion`，运行时优先读取 pinned refs。
  - S6 治理闭环：补标准 Skill / Policy 的影响分析、发布、灰度、回滚、debug/trace 摘要展示。
- next_action: （本任务当前冲刺范围已完成；若继续平台治理，下一卡优先拆 rollout group 编排与更细粒度灰度策略。）
- handoff_notes:
  - 平台核心兜底策略建议长期迁移为 `PlatformPolicyBundle`，不要继续作为普通租户 Skill 展示。
  - 已发布 Agent 现在会在 publish 时写入 `agent_workflow_skill_ref`，运行时优先按 pinned skill refs 解析；后续若继续补 S6，请围绕 impact/debug/rollout 做增量，不要回退 publish pin 语义。
  - 当前“灰度”语义来自已发布 Agent 保持旧 pinned snapshot、后续重新发布命中新模板版本的天然分层；PolicyBundle 虽已具备独立版本发布面板，但仍是全局即时生效，若要继续推进需补 rollout group 编排而不是回退现有入口。
  - 页面级人工回归第一轮已完成；后续若继续平台治理，优先补更多灰度/回滚场景和 debug trace 对照，而不是重复验证基础可达性。

### TASK-022 Platform operations console

- status: completed
- priority: P0
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-010-platform-operations-console.md`
- summary: 下一阶段先实现平台运营后台的内置 Skill 与内置工具治理：独立 `/platform/**` 控制面、平台角色、平台审计、标准 Skill 模板/版本管理、内置工具目录与风险/启停/依赖治理；计费、完整租户运营、用量成本和支持观测暂不展开。
- done:
  - 已新增 `docs/specs/FEAT-010-platform-operations-console.md`，记录架构边界、平台角色、路由/API、MVP 模块、数据模型、计量事件、迁移阶段和未来拆分路径。
  - 已新增平台角色常量与 `@RequirePlatformRole` AOP；phase-1 继续复用短信登录和 JWT，通过 `platform-*-mobiles` 配置把平台角色写入 `roles` claim。
  - 已新增 `/platform/**` 后端基础读接口：`/platform/bootstrap`、`/platform/skills`、`/platform/tools`、`/platform/audit/logs`。
  - 已新增 `platform_audit_log` 表、实体、Repository 与 Service，作为后续平台高风险操作审计事实源。
  - 前端已新增 `/platform/login`、`/platform` 独立壳与菜单，并落地概览/平台技能/内置工具/平台审计基础页面。
  - 已新增 `AuthFlowIntegrationTest.shouldExposePlatformRoleAndAllowPlatformBootstrap`，验证平台角色注入与 `/platform/bootstrap` 门禁。
  - `/platform/skills` 已从只读列表升级为可写治理台：支持模板版本查看、草稿创建、发布/回滚、治理字段编辑与影响范围基础计数。
  - `/platform/tools` 已接入平台治理表：支持展示名、描述、风险、分类、启用状态编辑，并展示关联平台 Skill / Agent 依赖。
  - 平台 Skill 模板建草稿、发布/回滚，以及平台 Tool 更新均已写入 `platform_audit_log`。
  - 平台 Tool `enabled=false` 已影响租户 `/tools` 目录，禁用的内置工具不会继续出现在 Agent Builder 可选目录。
  - 已新增 `PlatformGovernanceIntegrationTest`，验证平台 Skill / Tool 可写治理、平台审计与租户工具目录联动。
  - 平台 Tool `enabled=false` 已真正接入运行时工具解析/执行：禁用的内置工具不会出现在 chat tool definitions 中，且 `ToolOrchestratorService.executeTool` 会硬拦截。
  - 已补 `ORG_ADMIN` / 平台角色边界验收：`ORG_ADMIN` 无法访问 `/platform/**`，平台高风险治理动作保留审计。
  - 已完成页面级人工回归补验：平台概览、平台技能、内置工具、平台审计在本地浏览器已能加载真实数据；技能页与工具页此前的 HTML/JSON 冲突已由 `/api/platform/**` 前缀修复。
- implementation_plan:
  - O1 平台控制面壳：新增 `/platform/**` 后端鉴权、平台角色模型、前端平台后台布局与菜单。
  - O2 平台审计基础：新增 `platform_audit_log`，记录平台人员对内置 Skill / Tool 的新增、编辑、发布、停用、回滚、紧急禁用操作。
  - O3 内置 Skill 治理页：接入 FEAT-009 平台标准 Skill 模板与版本，支持列表、详情、版本、发布/回滚、影响范围查看。
  - O4 内置工具治理页：管理内置工具展示名、描述、风险等级、启用状态、能力分类，展示关联 Skill / Agent 依赖。
  - O5 工具紧急控制：支持平台级紧急禁用/恢复高风险内置工具，并在运行时工具解析中生效。
  - O6 前后端验收：验证 ORG_ADMIN 不能访问 `/platform/**`，平台角色可操作内置 Skill / Tool，所有高风险操作写审计。
- next_action: （本任务冲刺范围已完成，无阻塞剩余项）
- handoff_notes:
  - 平台后台不是租户后台的超级模式，必须独立权限、独立路由和平台审计。
  - 计费用量以 `usage_meter_event` 为事实源，不从前端点击或业务表临时拼账单。

### TASK-019 Agent / Skill / Tool permission model (runtime)

- status: completed
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/agent-skill-tool-permission-model.md`
- summary: 按设计文档实现运行时工具权限语义——Agent 直接绑定与已绑定 Skill 的 `toolWhitelist` 声明合并为会话可用工具面；区分静态绑定与 Skill 独有声明；调试与聊天 API 透出分层字段；工具执行日志标记 invocation 类型。
- done:
  - `AgentCapabilityResolverService`：`effectiveToolNames` = 直接绑定 ∪ Skill 声明（`mergeToolUnion`）；记录 `agentDirectToolNames`、`skillDeclaredToolNames`、`skillScopedToolNames`。
  - `SkillResolverService` / `ResolvedSkillContext`：透出上述字段；始终合并各 Skill 的 toolWhitelist。
  - `/ai/chat` 与 `/agents/{agentId}/debug` 响应增加分层工具字段；`ToolOrchestratorService` 增强审计日志 `invocationType`。
  - `OrchestratorIntegrationTest#shouldUnionAgentDirectToolsAndSkillDeclaredToolsIncludingMcp` 覆盖 Agent=tavily + Skill=MCP 工具并集场景。
- next_action: 可选前端展示三列工具维度；若继续增强，单独补“当前 Skill 执行上下文”强约束与高风险审批策略建模。
- handoff_notes:
  - 文档中的「当前 Skill 执行上下文」强约束与高风险审批策略尚未单独建模；后续可按 FEAT 拆分。
  - **取代说明**：`TASK-018` 中「无交集则 effective 为空」的交集策略已由本任务废止，以设计文档 §6 运行时并集为准。

### TASK-018 Agent tool whitelist strict boundary and MCP enforcement verification

- status: superseded
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: （历史）曾将 Agent 与 Skill 工具白名单改为严格交集；已由 `TASK-019` + `docs/agent-skill-tool-permission-model.md` 按「静态不扩权、运行时并集」取代。
- superseded_by: `TASK-019`
- handoff_notes:
  - 请勿再以本卡描述交集收敛为当前后端行为；评审与排障以 TASK-019 及权限设计文档为准。

### TASK-001 MCP cache runtime smoke closure

- status: completed
- priority: P1
- owner_role: backend-platform
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope`
- summary: 完成系统 MCP 服务器缓存机制的真实管理员运行态验收，覆盖列表、详情缓存读取与强制 discover 刷新。
- done:
  - MCP 缓存字段、服务端缓存兜底与管理端展示已实现。
  - 相关 compile/build/integration test 已通过并记录在 `.claw/test-report.md`。
  - 新增 `McpServerIntegrationTest.shouldRejectOrgUserAndAllowOrgAdminForMcpServerApis`，覆盖管理员权限门禁稳定性。
  - 新增 `McpServerIntegrationTest.shouldKeepCachedSnapshotWhenDiscoverRefreshFails`，覆盖“未命中缓存 -> 发现成功 -> discover 失败后旧快照仍可读取”链路。
  - 与 `OrchestratorIntegrationTest`、`ChatRealtimeIntegrationTest` 组合回归通过。
  - 2026-04-30 已补真实运行态 smoke：`13800138111` 可通过短信登录访问 `/mcp-servers`、读取 `/mcp-servers/1/tools` 真实缓存，并成功触发 `/mcp-servers/1/discover` 刷新；`13800138121` 作为 `ORG_USER` 访问同路径仍被拒绝。
- remaining:
  - （冲刺目标已完成，无阻塞剩余项）
- next_action: 转入 FEAT-002 人工对话回归与复杂语义覆盖。
- handoff_notes:
  - 2026-04-30 已完成真实运行态验收，原先“管理员权限上下文不稳定”的阻塞不再复现。
  - 结果以 `.claw/test-report.md` 和 `.claw/issue-list.md` 为准，不要在本卡重复写测试细节。

### TASK-002 Bind published agent version to runtime

- status: completed
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-21-agent-runtime-not-bound-to-published-workflow`
- summary: 让聊天运行时真正读取已发布 Agent workflow/version，而不是只解析静态 skill/tool/kb 绑定。
- done:
  - `SkillResolverService` 已接入 `published_version_id` 解析：当存在已发布版本时，优先读取 `workflow_manifest.dependencies`（skills/tools/knowledgeBases）作为运行时边界。
  - 无发布版本、解析失败或状态非 `PUBLISHED` 时，自动回退到原有 `AgentCapabilityResolverService` 路径。
  - 新增 `OrchestratorIntegrationTest.shouldPreferPublishedWorkflowDependenciesAtRuntime` 覆盖发布后运行时依赖优先逻辑。
  - 新增 `OrchestratorIntegrationTest.shouldSwitchRuntimeDependenciesAcrossPublishStates`，覆盖 `publish v1 -> publish v2 -> rollback v1` 三态依赖切换回归。
  - 新增 `OrchestratorIntegrationTest.shouldGracefullyHandleInvalidPublishedManifest`，覆盖 published manifest 非法 JSON 时的运行时韧性。
  - 已接入 published runtime policy：运行时解析 `workflow_manifest.policies.maxToolCalls`，并驱动 `ChatOrchestratorService` 的 tool loop 轮数上限；`/ai/chat` 响应新增 `runtimePolicy`（`maxToolCalls`、`publishedVersionId`）。
  - 新增 `OrchestratorIntegrationTest.shouldExposePublishedRuntimePolicyInChatResponse` 验证运行时策略注入。
  - 新增 `AgentWorkflowRuntimeService` 并接入 `/agents/{agentId}/debug`，debug 响应新增 `runtimeSource`、`publishedVersionId`、`workflowCodePreview`。
  - 新增 `OrchestratorIntegrationTest.shouldUsePublishedWorkflowInDebugRuntime`，验证 debug runtime 优先使用已发布版本。
  - debug runtime 已升级最小执行器输出：`/agents/{agentId}/debug` 新增 `executionStatus` 与 `executionOutput`，并在 trace 中输出执行状态（`published-executed` / `fallback-executed` / `published-invalid`）。
  - 聊天主链路已复用最小执行器：`/ai/chat` 响应新增 `runtimeExecution.status/output/publishedVersionId`，并补集成断言验证发布版本状态可见。
  - debug/chat 已统一输出节点级 `executionTrace`：`workflow-node:start -> route-input -> tool-scope -> end:*`，形成最小可扩展执行轨迹协议。
  - 已接入 `workflow_code` 最小节点解析：从发布代码 `runAgent` 方法体识别并输出代码节点（如 `intent-classify`、`knowledge-search`、`tool-invoke-best`、`response-generate`）。
  - 已接入 `contextSnapshot` 最小状态快照：debug/chat 均可返回 `runtimeSource`、`inputRoute`、`toolScopeSize`、`intent`、`parsedNodes`、`knowledgeUsed`、`toolInvoked`、`responsePlanned`。
  - 已接入执行期指标快照：新增 `branchHit`、`nodeMetrics[{nodeId,costMs,status}]`、`errorNode/errorType`，并完成 debug/chat 统一透出与集成断言。
  - 已接入可回放摘要协议：`nodeMetrics` 新增 `ioSummary(input/output)`，并新增 `replayHint`，支持按节点顺序进行最小重放。
  - 已接入结构化节点 I/O：`nodeMetrics` 新增 `ioPayload.input/output`。
  - 新增 fallback 专项回放断言：`shouldExposeFallbackReplayMetadataInDebugRuntime`。
  - 新增 invalid 专项回放断言：`shouldExposeInvalidReplayMetadataInDebugRuntime`。
  - `OrchestratorIntegrationTest` 与 `ChatRealtimeIntegrationTest` 回归通过，`TASK-002` 既定冲刺范围闭环。
- remaining:
  - （本任务冲刺目标已完成，无阻塞剩余项）
- next_action: 转入 `TASK-006` 与 MCP 真实 smoke 验收闭环。
- handoff_notes:
  - 这是 Agent Builder 主线当前最关键的产品闭环缺口。
  - 若开始实现，应补独立 feature spec，而不是只在 baseline 中追加描述。

### TASK-003 Replace simulated debug with runtime trace acceptance path

- status: completed
- priority: P0
- owner_role: fullstack-agent-builder
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-21-agent-debug-still-simulated`
- summary: 把 Agent Builder 的试运行/调试从“前端模拟路径”收口到可验证的真实后端 trace。
- done:
  - 后端已具备 `POST /agents/{agentId}/debug`，返回 `runtimeSource/publishedVersionId/executionStatus/executionTrace/contextSnapshot/policyBundle/resolvedSkillVersions/runtimeGovernanceNotes` 等结构化调试证据。
  - 前端 `AgentBuilderShell.runDebug()` 现已改为优先消费后端调试结果：成功时高亮真实运行路径，并展示 runtime governance 摘要；仅在接口失败时回退到 `simulateDebugTrace(...)`。
  - 已补验证：`backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldUsePublishedWorkflowInDebugRuntime test` -> success；`frontend` `npm run build` -> success。
- next_action: 如后续继续增强，可把节点级 `nodeMetrics/ioPayload` 结构化展示到调试面板，而不只显示文字摘要。

### TASK-004 Brownfield state protocol adoption

- status: completed
- priority: P1
- owner_role: ai-collaboration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 为既有仓库补齐最小 `.claw/` 状态协议骨架，避免状态文件继续只靠 `current-status.md` 堆积。
- done:
  - 保留 `.claw/` 作为 canonical state directory。
  - 重写 `current-status.md` 为短快照。
  - 初始化 `task-board.md`。
  - 初始化 `docs/specs/PROJECT-BASELINE.md`。
- handoff_notes:
  - 后续 session 默认先读 `current-status.md` + `task-board.md`；涉及遗留架构判断时再读 `docs/specs/PROJECT-BASELINE.md`。

### TASK-005 Skill authoring fallback alignment

- status: completed
- priority: P1
- owner_role: backend-skill-authoring
- spec_path: `docs/specs/FEAT-001-skill-authoring-generic-generation.md`
- summary: 把“自然语言创建技能”从内置行业模板依赖改为“模型通用理解优先 + fallback 做 sourceText 结构化提取”。
- done:
  - 新增 feature spec `FEAT-001-skill-authoring-generic-generation.md`。
  - 模型 system prompt 明确禁止按内置行业样例强行套场景，要求优先保留 sourceText 中的事实与边界。
  - 无模型 fallback 不再依赖审批/CRM/合同等固定模板，而是通用提取 skillCode/name/source facts/显式工具/编号步骤/输出要求。
  - 新增 source facts 保留逻辑，避免无编号步骤时又退回抽象模板。
  - 更新 `SkillAuthoringIntegrationTest`，覆盖“通用生成 + 自定义工具 + 营销活动流程”场景。
- next_action: 在真实管理端 UI 上再做一轮人工回归，确认无模型 fallback 和有模型路径都与用户需求贴合，并观察 preview compile 是否引入次级领域偏差。
- handoff_notes:
  - 本次确认的设计问题是：skill authoring 不应以少量内置行业样例为强依赖。
  - 本地默认 `skill-authoring` 场景仍无可用模型，因此当前验证重点是“即使完全走 fallback，也不再被固定模板带偏”。

### TASK-006 CiCi session continuity and state layer

- status: completed
- priority: P0
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/FEAT-002-cici-session-continuity-and-state.md`
- summary: 为思思补齐“会话历史回灌 + session state + 状态驱动追问约束”，解决同一会话内多轮失忆与重复追问问题。
- done:
  - 新增 `V22__chat_session_state.sql`，落地 `chat_session_state` 持久层。
  - 新增 `ChatSessionStateEntity / Repository / Service`，支持会话状态读取、注入块构建、用户输入与工具结果基础 reducer。
  - `ChatOrchestratorService` 已注入最近历史消息（最近 20 条窗口，排除当前用户 turn）和 session state 块。
  - 新增 `GET /ai/sessions/{sessionId}/state` 调试接口，便于管理端/调试页查看状态。
  - 新增集成测试 `shouldPersistSessionStateAfterUserIntentHint`，并通过 `OrchestratorIntegrationTest` 与 `ChatRealtimeIntegrationTest` 回归。
  - 新增集成测试 `shouldKeepSessionStateAcrossSecondTurn`，验证同 `sessionId` 下双轮状态连续与消息历史持久化。
  - `ChatSessionStateService` 已增强语义抽取与字段映射：补齐 `current_object_*`、`target_segment_summary`、`missing_fields`、`next_action`、`no_repeat_questions`。
  - 新增集成测试 `shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns`，覆盖“同 session 第二轮不重复追问”的确定性状态约束断言。
  - `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` 与 `ChatRealtimeIntegrationTest` 回归通过。
- next_action: 转入 FEAT-002 人工回归与复杂语句覆盖评估（必要时引入 LLM 抽取器）。
- handoff_notes:
  - 当前已完成“会话历史回灌 + 会话状态持久层”最小闭环，不再只依赖长期用户记忆。
  - 会话状态仍应保持在 `chat_session_state`，不要把会话内动作确认误写进 `UserMemoryService`。

### TASK-007 SaaS billing and packaging design

- status: pending
- priority: P1
- owner_role: product-platform
- spec_path: `docs/specs/FEAT-003-saas-billing-and-packaging.md`
- summary: 为当前企业多组织 AI 助手平台设计适合 SaaS 化交付的企业混合计费模型，覆盖套餐、计量、账单与超额治理方向。
- done:
  - 已新增 `FEAT-003-saas-billing-and-packaging.md`，明确平台订阅、席位、AI 用量、Agent/Workflow、知识库、工具集成、企业增值模块七类计费项。
  - 已明确主计费主体为组织/租户，建议采用 `平台订阅 + 资源席位 + AI/自动化用量 + 增值模块` 模型。
  - 已将计量来源映射到现有 `ChatOrchestratorService`、`AgentWorkflowRuntimeService`、工具调度链路、知识库处理链路和 `ops/metrics/cost` 雏形。
- next_action: 若进入实现阶段，先拆 `usage_meter_event`、套餐/订阅实体与管理端账单总览页，不要先接支付系统。
- handoff_notes:
  - 这是产品与平台设计任务，当前只完成 spec，不代表账单域已在代码中落地。
  - 后续实现时优先建立统一计量事件和组织级阈值控制，再做价格表、账单和发票域。

### TASK-008 Adopt cloudcc AI dev guidelines as durable protocol

- status: completed
- priority: P2
- owner_role: ai-collaboration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 按 `cc-aidev-guidelines-common` `3.4.0` 对齐当前 brownfield 仓库的项目状态协议，补齐项目级声明、热状态快照、任务卡与 baseline 的最小一致性。
- done:
  - 保持 `.claw/` 为 canonical state directory，不启用 `.ai-dev/` 双写。
  - 在项目根 `README.md` 与新建 `AGENTS.md` 中补齐受管声明块，明确所有 AI agent 必须先加载 `cc-aidev-guidelines-common`。
  - 将 `.claw/current-status.md` 收口为“快照 + 指针”，不再继续堆积长历史日志。
  - 更新 `.claw/task-board.md` 与 `docs/specs/PROJECT-BASELINE.md`，让 active task、baseline 与当前真实阻塞保持对齐。
  - 已执行 `python3 /Users/owenspace/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py .claw`，状态校验通过。
- handoff_notes:
  - 后续继续遵循“少读少写、事实可验证、source-of-truth 不重复”。
  - 若将来要把 `validate-state.py` 内置到仓库，再单独起新任务，不要把工具引入和状态补录混成一次大改。

### TASK-009 FEAT-006 virtual human page MVP

- status: completed
- priority: P1
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 按 FEAT-006 落地虚拟人场景页 MVP，支持沉浸式背景、虚拟人状态联动、任务卡片层与文本/语音输入联动。
- done:
  - 扩展 `streamAiChat`：增加未知 SSE 事件透传回调，支持消费 `task_created/task_status/task_delta/task_done/avatar_state`。
  - `AssistantApp` 的 `scene` 页接入真实 `/ai/chat/stream`，实现虚拟人状态机（`idle/listening/thinking/speaking`）与任务卡状态更新。
  - `scene` 页新增任务卡工作区，支持按 `taskId` 聚合任务状态、摘要和增量内容。
  - 语音输入在 `scene` 页下支持自动填充并自动发送，打通 `useAsrVoiceInput -> streamAiChat` 路径。
  - 更新 `cici-ui.css` 场景样式：虚拟人状态动画、任务卡样式、麦克风激活态、发送按钮与提示区。
- next_action: 如进入下一轮 FEAT-006，优先补后端 task 事件完整协议与任务持久化，减少前端 fallback 到单任务的分支逻辑。
- handoff_notes:
  - 当前实现聚焦 FEAT-006 Phase 1 视觉 MVP；后端若未下发 task 事件，前端会以首任务兜底展示流式结果。
  - 已完成 `frontend npm run build` 验证，结果记录见 `.claw/test-report.md`。

### TASK-010 FEAT-006 backend task/avatar stream protocol

- status: completed
- priority: P1
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 为 `/ai/chat/stream` 补充虚拟人页需要的 task/avatar 事件协议，让 scene 页可消费真实后端事件。
- done:
  - `ChatOrchestratorService.chatStream` 新增 `avatar_state` 事件发送：`thinking -> speaking -> idle`。
  - 新增 `task_created/task_status/task_delta/task_done` 事件发送，按单主任务流驱动前端任务卡状态更新。
  - 工具调用阶段新增任务状态推进：tool calling、审批等待（`waiting_user`）等。
  - 保持现有 `delta/tool_call/tool_result/phase/done/error` 兼容，不破坏已有聊天消费链路。
  - 已完成 `backend mvn -q -DskipTests compile` 与 `frontend npm run build` 验证。
- next_action: 下一轮可在此基础上引入 `chat_task/chat_task_event` 持久化与多任务拆分规则（并行/串行）。
- handoff_notes:
  - 当前 `TASK-010` 聚焦协议打通，不包含数据库任务实体迁移。
  - taskId 当前为 session 派生的轻量 ID，可在持久化阶段替换为真实任务主键。

### TASK-011 Workbench session history alignment

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 修复工作台会话历史与右侧历史列表错位、上下文不完整问题；让工作台会话按智能体隔离并回灌真实持久化消息。
- done:
  - 新增 `frontend/src/assistant/workbenchSessions.ts`，统一工作台 sessionId 规则（`workbench:{agentKey}`）与历史抽取逻辑。
  - 新增 `frontend/src/assistant/workbenchSessions.test.ts`，覆盖 sessionId 与历史抽取行为。
  - `AssistantApp` 在工作台模式下新增 `loadWorkbenchMessages(...)`，从 `/ai/sessions/{sessionId}/messages` 加载真实历史并同步到工作台消息状态。
  - 工作台发送消息后会触发历史强制刷新，避免仅依赖本地乐观更新导致的历史偏差。
  - 右侧“会话历史”改为与主对话区同源消息集，消除“主对话与右侧历史不对应”的现象。
- verification:
  - `frontend`: `npm run test -- src/assistant/workbenchSessions.test.ts` -> success
  - `frontend`: `npm run build` -> success
- handoff_notes:
  - 虚拟人页优化按用户要求暂缓；本任务只处理工作台会话历史一致性。

### TASK-012 Conversation grouping and new-dialog entry

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 在会话历史列表顶部新增“新对话”入口，并将会话组织为“一个多轮上下文=一个会话”，避免每条消息形成独立会话记录。
- done:
  - 在会话列表头部新增“新对话”按钮，支持创建本地草稿会话并立即切换。
  - 新会话使用稳定 `sessionId`（`chat:new:{userId}:{agentId}:{nonce}`）持续复用，保证后续多轮消息归入同一会话。
  - 会话列表刷新时保留未落库草稿会话；首条消息发送后可无缝与后端持久化会话合并。
  - `loadConversationMessages` 对 404 会话做空历史兜底，避免新建未发送会话时出现错误提示。
  - 新增会话列表样式 `cici-threads__new-btn`，与现有层级风格保持一致。
- verification:
  - `frontend`: `npm run build` -> success
- handoff_notes:
  - 本任务不涉及虚拟人页面（用户已明确暂停该方向）。

### TASK-013 Workbench history delete and markdown export

- status: completed
- priority: P1
- owner_role: fullstack-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 为思思工作台会话历史新增“删除会话”和“下载 Markdown”能力，支持会话级清理与本地归档。
- done:
  - 前端会话历史每项新增操作区，支持“下载”“删除”按钮。
  - 下载会把会话完整消息导出为本地 `.md` 文件（含会话元信息与按轮次排布的消息内容）。
  - 后端新增 `DELETE /ai/sessions/{sessionId}`，删除会话主记录、消息记录与会话状态记录。
  - 前端删除后同步更新本地会话列表与当前激活会话，避免空选中态。
- verification:
  - `frontend`: `npm run build` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- handoff_notes:
  - 删除能力当前面向工作台会话使用场景；其余页面可后续按权限策略接入。

### TASK-014 Workbench composer UI redesign

- status: completed
- priority: P1
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 思思工作台对话框按参考图重构——左下角新增「➕」弹出菜单（上传文件/添加快捷短语），发送按钮替换为深灰圆形上箭头图标。
- done:
  - 新增 `showPlusMenu` 状态控制弹出菜单显隐。
  - 新增 `cici-composer-plus` 容器：`➕` 按钮（32x32 圆角）+ 弹出菜单（上传文件/添加快捷短语两项）。
  - 弹出菜单定位在按钮上方，点击外部自动关闭。
  - 发送按钮替换为 `cici-workbench__send-btn`：36x36 深灰圆形 + 白色上箭头 SVG。
  - 移除原 "发送"/"发送中" 文字按钮。
  - 发送后自动关闭弹出菜单。
- verification:
  - `frontend`: `npm run build` -> success
  - `frontend`: `npx tsc -b --noEmit` -> success
- handoff_notes:
  - 「上传文件」和「添加快捷短语」按钮当前为视觉占位，未接入实际功能逻辑。
  - 仅修改了工作台（workbench）对话框，未影响对话页（chat）`cici-composer`。

### TASK-015 Fix CiCi default tool autonomy

- status: completed
- priority: P0
- owner_role: backend-chat-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 修复思思默认对话中“有工具但难以自主调用有效工具”的问题，确保默认运行时既保留全局工具调用规则，也暴露 CloudCC 对象/字段发现能力。
- done:
  - `SkillPromptAssembler` 改为保留全局基础提示词，并叠加 agent 专属规则，避免既有 agent system prompt 覆盖掉全局“该用工具时主动调用工具”的约束。
  - `AliyunBailianClient` 全局提示词新增“当可用工具能提供事实或记录时主动调用工具而不是猜测”规则。
  - `SkillResolverService` 为运行时 `cici-system` 默认工具集补齐 `cloudcc_getStandardObjects`、`cloudcc_getCustomObjects`、`cloudcc_getObjectFields`。
  - `AgentDefinitionService` 的新组织内置 `cici-system` agent 种子同步补齐 CloudCC 发现类工具和主动调工具提示。
  - 新增回归 `OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent`，验证默认思思工具范围包含 CloudCC 发现工具。
- verification:
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent test` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- next_action: 先修复 CloudCC 绑定凭证与聊天入口模型 key，再在工作台人工回归“查客户资料 / 查对象字段 / 查待审批”等典型话术，确认思思会优先发起工具调用而不是只输出泛化文本。
- handoff_notes:
  - 本次修复对现有组织立即生效依赖运行时 resolver 兜底，不要求先重建 builtin agent 或重绑 skill。
  - 更大范围的 `OrchestratorIntegrationTest` 当前仍受短信频控和既有调度唯一键问题影响，不能把整类失败解读为本任务回归失败。
  - 2026-04-30 真实 smoke 补充结论：`effectiveToolNames` 已包含 CloudCC 发现/查询工具，但 `13800000001/哪吒` 的 CloudCC token 换取仍返回 `Please check your username and password.`；同时 `sales-agent` 聊天入口还受 `Aliyun API key is not configured.` 阻塞，需与外部凭证问题分开排查。

### TASK-016 Workbench reply-area blanking guard

- status: completed
- priority: P0
- owner_role: frontend-assistant-experience
- spec_path: `docs/specs/FEAT-006-virtual-human-multitask-workbench.md`
- summary: 修复工作台发送后“先显示思考中，再整段变空白”的问题，避免后端暂时缺少 assistant 历史时把本地流式消息覆盖清空。
- done:
  - 在 `loadWorkbenchMessages(...)` 增加保护：若后端刷新结果没有 assistant 内容，而本地已有 assistant 流式文本，则保留本地消息，不做覆盖。
  - 保持 `conversationMessages` 正常更新，避免影响历史同步和会话刷新路径。
- verification:
  - `frontend`: `npm run build` -> success
- next_action: 人工复现“看下今天的潜在客户”等真实问题，确认 UI 不再从图1跳到空白图2。
- handoff_notes:
  - 该修复是前端防护层；若后端持续写入空 assistant 内容，仍需要继续排查后端流式落库链路。

### TASK-017 Remove virtual human surface

- status: completed
- priority: P1
- owner_role: fullstack-assistant-experience
- spec_path: `docs/specs/FEAT-007-remove-virtual-human-surface.md`
- summary: 按用户要求正式下线助手端“虚拟人”功能，移除菜单入口、scene 页面、专属 SSE 事件和静态封面页，并同步项目文档。
- done:
  - `AssistantApp` 已移除 `scene` 标签页、状态、语音分支和整段沉浸式页面渲染。
  - `streamAiChat` 已回收仅供 scene 页使用的未知事件透传参数。
  - `ChatOrchestratorService.chatStream` 已移除 `avatar_state/task_created/task_status/task_delta/task_done` 发送逻辑，保留通用聊天流事件。
  - `frontend/public/ai-cover.html` 与 `frontend/public/vh-cover.html` 已删除。
  - 已新增 `FEAT-007` 下线 spec，并将 `FEAT-006` 标记为 retired。
- verification:
  - `frontend`: `npm run build` -> success
  - `backend`: `mvn -q -DskipTests compile` -> success
- next_action: 人工回归工作台、监控、客户会话和 CRM 入口，确认移除 scene 分支后导航和语音输入无回归。
- handoff_notes:
  - `FEAT-006` 只保留历史设计上下文，当前产品能力以下线 spec 和 `.claw/current-status.md` 为准。
  - 如果后续要恢复该方向，应重新立项，不要直接恢复旧 scene MVP 代码。
