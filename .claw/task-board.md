---
updated_at: 2026-04-24T15:03:00Z
status: active
---

# Task Board

## Active Queue

### TASK-018 Agent tool whitelist strict boundary and MCP enforcement verification

- status: completed
- priority: P0
- owner_role: backend-agent-runtime
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 修复 Agent 对话运行时工具边界在“Agent 白名单与 Skill 白名单无交集”时被错误放宽为并集的问题，并验证 MCP 白名单约束生效。
- done:
  - `AgentCapabilityResolverService.mergeBoundary(...)` 已改为严格交集策略，不再在无交集时回退并集。
  - 无交集场景 warning 文案已更新为“严格收敛为空”，便于排障定位。
  - 新增 `OrchestratorIntegrationTest#shouldEnforceIntersectionForAgentToolWhitelistAndSkillToolWhitelistIncludingMcp`，构造“Agent 仅 tavily_search、Skill 仅 get_object_list（MCP）”场景，验证 `effectiveToolNames` 为空且存在 warnings。
  - 已执行回归：
    - `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldEnforceIntersectionForAgentToolWhitelistAndSkillToolWhitelistIncludingMcp test` -> success
    - `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldResolvePhaseOneSkillsForSalesAgent,OrchestratorIntegrationTest#shouldNormalizeLegacyAgentToolIdsForApprovalAgent test` -> success
- next_action: 在管理端手工回归“配置 MCP 白名单后发起真实对话”场景，确认 UI 展示与后端 `effectiveToolNames` 一致。
- handoff_notes:
  - 该修复会改变历史“无交集时仍可调用工具”的宽松行为，若业务需要放宽策略，应通过显式通配或统一白名单配置实现，不应隐式并集兜底。

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
- remaining:
  - （冲刺目标已完成，无阻塞剩余项）
- next_action: 转入 FEAT-002 人工对话回归与复杂语义覆盖。
- handoff_notes:
  - 真实运行态阻塞不是代码编译失败，而是登录/权限上下文不稳定。
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

- status: pending
- priority: P0
- owner_role: fullstack-agent-builder
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- depends_on: `ISSUE-2026-04-21-agent-debug-still-simulated`
- summary: 把 Agent Builder 的试运行/调试从“前端模拟路径”收口到可验证的真实后端 trace。
- next_action: 复核当前 `/agents/{agentId}/debug` 覆盖范围，确认前端仍在模拟的路径与缺失的执行证据。

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

- status: pending
- priority: P2
- owner_role: ai-collaboration
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- summary: 将 `/cloudcc-aidev-guidelines-common` 作为后续会话与多代理协作的统一待完善项，补齐触发式读写、状态文件一致性校验、任务卡与 spec 对齐机制。
- next_action: 在下一轮流程治理迭代中，先定义本仓库落地范围（`.claw/` hot/warm/cold 读写触发、spec 关联规则、session end 更新清单），再补自动校验脚本与最小执行规范。
- handoff_notes:
  - 本项是流程治理增强，不影响当前功能发布；按“未来待完善项”排期执行。
  - 落地时优先保证“少读少写、事实可验证、source-of-truth 不重复”三条原则。

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
- next_action: 在工作台人工回归“查客户资料 / 查对象字段 / 查待审批”等典型话术，确认思思会优先发起工具调用而不是只输出泛化文本。
- handoff_notes:
  - 本次修复对现有组织立即生效依赖运行时 resolver 兜底，不要求先重建 builtin agent 或重绑 skill。
  - 更大范围的 `OrchestratorIntegrationTest` 当前仍受短信频控和既有调度唯一键问题影响，不能把整类失败解读为本任务回归失败。

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
