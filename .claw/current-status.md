---
updated_at: 2026-04-27T17:50:00Z
status: in_progress
phase: workbench_composer_ui_redesign
current_task: 思思工作台对话框已按参考图重构——左下角新增「➕」弹出菜单（上传文件/添加快捷短语），发送按钮替换为深灰圆形上箭头图标。前后端构建均通过。
next_action: 人工回归验证对话框新布局交互效果（➕ 菜单弹出/收起、发送按钮样式、点击外部关闭菜单）。
priority: HIGH
---

# Current Status

## Snapshot

- 已完成：
  - 2026-04-27 已完成 `TASK-013`：思思工作台会话历史新增“下载 Markdown / 删除会话”操作。
  - 2026-04-27 已新增后端删除接口 `DELETE /ai/sessions/{sessionId}`，会同步删除会话主记录、消息与 session state。
  - 2026-04-27 已完成前后端验证：`frontend npm run build` 与 `backend mvn -q -DskipTests compile` 均通过。
  - 2026-04-27 已完成 `TASK-012`：会话历史列表顶部新增“新对话”入口；新会话创建后按稳定 `sessionId` 连续复用，支持多轮上下文归并为同一会话。
  - 2026-04-27 已完成草稿会话保留策略：列表刷新时保留本地未落库会话，首条发送后与后端会话自然合并。
  - 2026-04-27 已优化新建会话消息加载：`/ai/sessions/{id}/messages` 返回 404 时按空历史处理，不再误报加载失败。
  - 2026-04-27 已完成 `frontend npm run build` 验证（含会话新建与归并改造）。
  - 2026-04-27 已完成 `TASK-011`：工作台会话改为按智能体稳定 sessionId（`workbench:{agentKey}`），避免不同智能体共用 `assistant-ui-workbench` 造成上下文串线。
  - 2026-04-27 工作台会话消息改为从后端真实历史加载并回灌；发送后会强制刷新该 session 历史，减少本地临时状态与持久化状态不一致。
  - 2026-04-27 工作台右侧“会话历史”改为与主对话区同源（同一消息集），不再使用单独裁剪/倒序造成的数据错位。
  - 2026-04-27 新增前端单测 `workbenchSessions.test.ts`，覆盖工作台 sessionId 生成与历史抽取规则；通过 `frontend npm run test -- src/assistant/workbenchSessions.test.ts`。
  - 2026-04-27 已完成 `frontend npm run build` 验证，构建通过。
  - 2026-04-25 已完成 `TASK-010`：`/ai/chat/stream` 增加 `avatar_state/task_created/task_status/task_delta/task_done` 事件，scene 页可消费真实后端协议。
  - 2026-04-25 已在工具阶段接入任务状态推进（含审批等待 `waiting_user`），并保持既有 `delta/tool_call/tool_result/phase/done/error` 兼容。
  - 2026-04-25 已完成 `TASK-009`：`scene` 页接入虚拟人状态联动（idle/listening/thinking/speaking）+ 多任务卡工作区 + 语音/文本输入联动。
  - 2026-04-25 已扩展 `streamAiChat` 以透传未知 SSE 事件，前端可消费 `task_created/task_status/task_delta/task_done/avatar_state`。
  - 2026-04-25 已更新场景样式，补充虚拟人 speaking 动效、任务卡状态样式、麦克风激活态与发送按钮交互。
  - 后端/前端基础平台、双入口鉴权、知识库、工具治理、审计与多租户骨架已在仓库中落地。
  - Agent Builder 主线已打通“自然语言编排 -> 编译产物 -> 版本治理”的主要建模与 UI 骨架。
  - Skill Authoring、用户记忆、个人 Workflow、系统 MCP 缓存、Tavily 内置 skill 等近期能力已完成代码接入，并在 `.claw/test-report.md` 记录了最近一次真实编译/测试结果。
  - 2026-04-24 已修复管理端示例账号 bootstrap admin 配置偏差；`AuthFlowIntegrationTest` 与 `ManagementConsoleIntegrationTest` 已验证通过。
  - 2026-04-24 已完成 skill authoring 生成策略重构：模型提示词不再鼓励按审批/CRM 等内置样例套行业；无模型 fallback 改为通用结构化提取，优先保留用户原文中的目标、事实、显式工具名、编号步骤和输出要求。
  - 2026-04-24 已完成“思思多轮会话连续记忆/执行状态”设计落盘，新增 `docs/specs/FEAT-002-cici-session-continuity-and-state.md`，明确会话历史回灌、session state 持久层、状态注入规则与工具结果 reducer 方案。
  - 2026-04-24 已完成 FEAT-002 第一轮实现：新增 `chat_session_state` 迁移与实体/服务；`ChatOrchestratorService` 现在会注入最近历史消息与 session state；新增 `GET /ai/sessions/{sessionId}/state` 调试接口；工具调用后会写回基础会话状态。
  - 2026-04-24 已完成 `TASK-002` 最小实现：`SkillResolverService` 运行时会优先读取 `agent_definition.published_version_id` 对应 `workflow_manifest.dependencies`（skills/tools/knowledgeBases）作为能力边界来源；无发布版本时回退到现有能力解析路径。
  - 2026-04-24 新增 `OrchestratorIntegrationTest.shouldPreferPublishedWorkflowDependenciesAtRuntime`，验证发布后的依赖在运行时生效。
  - 2026-04-24 已补 FEAT-002 与 TASK-002 强化回归：新增 “同 session 双轮状态连续” 与 “publish V1 -> publish V2 -> rollback V1 三态依赖切换” 集成测试并通过。
  - 2026-04-24 已补 `shouldGracefullyHandleInvalidPublishedManifest`，验证发布版本 manifest 异常时运行时不会崩溃，聊天链路可继续响应。
  - 2026-04-24 已接入 published runtime policy：`workflow_manifest.policies.maxToolCalls` 可驱动聊天 tool loop 轮数，并在 `/ai/chat` 返回 `runtimePolicy`（含 `publishedVersionId`）。
  - 2026-04-24 已新增 `AgentWorkflowRuntimeService`，`/agents/{agentId}/debug` 可返回 `runtimeSource/publishedVersionId/workflowCodePreview`，不再仅是前端模拟 trace 占位。
  - 2026-04-24 已把 debug runtime 从 `simulated-runtime` 升级为最小执行器输出：`/agents/{agentId}/debug` 新增 `executionStatus` 与 `executionOutput`，并在 trace 中记录执行状态。
  - 2026-04-24 已把最小执行器复用到 chat 主链路：`/ai/chat` 响应新增 `runtimeExecution`（`status/output/publishedVersionId`），用于观测发布版本运行态执行结果。
  - 2026-04-24 已新增节点级执行轨迹：`/agents/{agentId}/debug` 的 `executionTrace` 与 `/ai/chat.runtimeExecution.trace` 统一输出 workflow 节点轨迹。
  - 2026-04-24 已接入 `workflow_code` 最小节点解析：从 `runAgent` 方法体识别 `intent-classify/knowledge-search/handoff-request/tool-invoke-best/response-generate` 等核心节点并投影到 execution trace。
  - 2026-04-24 已接入最小 `contextSnapshot`：debug 与 chat 统一返回 `runtimeSource/inputRoute/toolScopeSize/intent/parsedNodes/knowledgeUsed/toolInvoked/responsePlanned` 等字段。
  - 2026-04-24 已把 `contextSnapshot` 扩展为执行期指标：新增 `branchHit`、`nodeMetrics[{nodeId,costMs,status}]`、`errorNode/errorType`，为后续可回放 trace 提供基础协议。
  - 2026-04-24 已把 `nodeMetrics` 扩展为可回放摘要：每节点新增 `ioSummary.input/output`，并新增 `replayHint` 指导按节点顺序重放执行路径。
  - 2026-04-24 已完成 `TASK-002` 三项冲刺收尾：`ioPayload`（结构化 I/O）落地；新增 fallback 回放断言与 published-invalid 回放断言；全量回归通过。
  - 2026-04-24 已完成 `TASK-006` 三项收尾：`ChatSessionStateService` 新增对象/范围/缺失字段/下一步动作抽取；新增 `shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns`；`OrchestratorIntegrationTest` 与 `ChatRealtimeIntegrationTest` 回归通过。
  - 2026-04-24 已完成 `TASK-001` 冲刺：新增 `McpServerIntegrationTest`，验证 ORG_USER 被拒绝、ORG_ADMIN 可访问，以及“未命中缓存 -> 成功发现 -> discover 失败后旧快照仍可读”链路。
  - 2026-04-24 已新增 `docs/specs/FEAT-003-saas-billing-and-packaging.md`，为项目 SaaS 化设计组织级企业混合计费方案，覆盖平台订阅、席位、AI 用量、Agent/Workflow、知识库、工具集成与企业增值模块。
  - 2026-04-24 已将计费设计映射到现有代码能力：`/ops/metrics/cost`、聊天编排、Agent runtime、工具调度、知识库处理链路均被纳入后续统一计量事件设计。

- 进行中：
  - 当前最高优先级仍是 MCP 工具暴露链路的真实运行态验收。代码与测试已收口一轮，但本地真实 smoke 仍被管理员权限上下文异常阻塞。
  - Brownfield 状态治理已补齐最小骨架：本次会话已初始化 `task-board.md` 与 `docs/specs/PROJECT-BASELINE.md`，后续以这两处承接任务队列和遗留系统基线。
  - Skill Authoring 主链路仍建议补一轮真实 UI 回归，重点确认无模型时的 fallback 草稿与有模型时的生成质量都能稳定贴近用户原意。
  - 思思聊天运行时的多轮连续性已从“纯设计”进入“实现中”：当前已有历史回灌 + 会话状态层 + 基础 reducer，但复杂语义抽取与更细颗粒状态字段仍需增强。
  - SaaS 计费方案当前已完成产品级设计文档，但账单实体、统一计量事件、套餐配额和管理端账单中心尚未进入实现。
  - 已新增 `TASK-008`：将 `/cloudcc-aidev-guidelines-common` 纳入项目记录，作为未来多会话/多代理协作的状态协议完善项。
  - 用户已确认“虚拟人页面先暂停”，当前迭代聚焦工作台会话连续性和历史一致性。

- 关键未闭环：
  - `TASK-001`、`TASK-002` 与 `TASK-006` 的既定冲刺范围已闭环；后续增量优化主要是执行器深度与会话语义覆盖面（非本轮阻塞）。
  - MCP 管理链路缺少一次成功的真实管理员 smoke，导致缓存机制仍只有构建级与集成测试级验证。
  - MCP 管理链路缺少一次成功的真实管理员 smoke，导致缓存机制仍只有构建级与集成测试级验证。
  - `chat_session_state` 已落地，但当前 reducer 仍是第一版规则实现；复杂业务语义下的状态稳定性仍需回归验证和增强。
  - SaaS 计费仍停留在设计层，尚无 `usage_meter_event`、套餐/订阅、账单明细和超额策略落库实现。

## Verified Facts

- 代码库当前是 brownfield 项目，`.claw/` 已长期使用，但此前缺少 `task-board.md` 与 `docs/specs/PROJECT-BASELINE.md`。
- `docs/specs/` 在本次会话前不存在，说明项目状态协议尚未完整落到 brownfield adoption 形态。
- 最近一次明确记录的真实验证包括：
  - `backend`: `mvn -q -DskipTests compile` -> success（2026-04-25，含 FEAT-006 后端事件协议改造）
  - `frontend`: `npm run build` -> success（2026-04-25，含 FEAT-006 场景页改造）
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=AuthFlowIntegrationTest,ManagementConsoleIntegrationTest test` -> success
  - `backend`: `mvn -q -Dtest=OrchestratorIntegrationTest test` -> success
  - `backend`: `mvn -q -Dtest=ChatRealtimeIntegrationTest test` -> success
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> success（含新用例 `shouldPersistSessionStateAfterUserIntentHint`）
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> success（含新用例 `shouldPreferPublishedWorkflowDependenciesAtRuntime`）
  - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> success（回归）
  - `frontend`: `npm run build` -> success

## Active Risks

- `ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope` 仍未解决，阻塞系统 MCP 缓存功能的真实运行态验收。
- `ISSUE-2026-04-21-agent-runtime-not-bound-to-published-workflow` 仍是 Agent Builder 主线的 P0 闭环缺口。
- `ISSUE-2026-04-21-agent-debug-still-simulated` 仍影响“自然语言自动化编排”能力的可验收性。
- skill authoring 仍存在“有模型时输出质量取决于实际路由模型配置”的不确定性；本次修复已经去掉模板依赖，但 preview compile 的关键词推断层仍可能带来次级偏差。
- 思思当前虽已注入同会话历史与基础 session state，但状态抽取仍偏轻量规则，对复杂长指令的稳健性存在不确定性。
- 计费设计已形成 spec，但当前 `ops/metrics/cost` 仍是轻量估算接口，距离可结算账单还有明显数据模型与口径缺口。

## Next Actions

1. 若排期进入 FEAT-003，实现统一 `usage_meter_event` 与组织级用量聚合，替换 `ops/metrics/cost` 的轻量估算口径。
2. 在管理端新增账单总览/用量页，并把模型、知识库、工具、Agent Builder 的额度提示接到各自页面。
3. 并行继续 FEAT-002 人工对话回归，验证复杂中文指令下的状态稳定性，并据此决定是否引入 LLM 抽取器。
4. 对工作台执行一轮人工回归：切换 `cici-system/sales-agent/approval-agent` 验证历史隔离、刷新恢复和右侧历史一致性。
