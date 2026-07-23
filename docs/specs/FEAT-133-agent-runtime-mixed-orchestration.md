---
kind: feature-spec
feature_id: FEAT-133
title: 可执行计划、动态路由与受控反思的混合智能体运行时
status: in_progress
owner_role: project-manager
task_ids: TASK-235
related_decisions: DEC-006,DEC-010,DEC-018,DEC-027
related_issues: none
updated_at: 2026-07-23T04:30:00Z
updated_by: MANAGER-001
---

# FEAT-133 - 可执行计划、动态路由与受控反思的混合智能体运行时

## 1. 用户确认的目标

将 AgentCiCi 从“以受限工具循环为主的对话运行时”，演进为可以按任务复杂度与风险选择 `DIRECT`、`REACT`、`PLAN_EXEC`，并在必要时追加受控 `REFLECT` 的企业级混合运行时。

本功能必须优先完成真实、可恢复、可审计的计划执行，不能仅生成计划文本、流程图或代码预览后仍由另一条对话链路执行。所有对外结论、工具调用、人工确认与 Trace 必须以实际发生的运行事件为事实源。

## 2. 现状、问题与设计依据

### 2.1 已验证的可复用能力

- `ChatOrchestratorService` 已具备模型选择工具、执行、回填工具结果并继续决策的多轮循环；工具轮次有策略上限，且流式、非流式路径均记录模型与工具 Trace。
- Agent 编译器可生成意图识别、知识检索、工具调用、人工移交与答复生成的工作流定义、Manifest 和预览图。
- `AgentWorkflowRuntimeService` 已能解析发布版定义、钉住 Skill 版本并输出节点级检查信息；依据 FEAT-122，它必须被表述为“工作流定义检查”，不能冒充真实工具、检索或调度执行。
- 平台已有工具白名单、Agent/Skill 版本钉住、组织隔离、输入安全、写操作人工确认、Trace、评测、发布门禁、会话状态、组织生命周期和费用记录。
- FEAT-106 已具备确定性断言、Trace 回放、候选/基线比较及发布门禁，可作为新运行时的质量控制面。

### 2.2 当前缺口

- 不存在持久化、可逐步执行的任务计划；复杂任务不能表达步骤依赖、当前步骤、失败原因、重试、重规划或恢复点。
- “工作流定义”与聊天实际执行路径分离，无法证明某个定义的每一个节点确实被执行。
- 不存在统一模式路由，简单请求、跨系统多步任务和高风险任务都会落入同一种工具循环。
- 现有评测与发布门禁属于离线或发布前质量控制，不是单次任务中的“产出→审核→定向修订”闭环。
- Trace 能记录调用片段，但还不能完整回答“选择了何种模式、计划了什么、哪个步骤失败、是否重规划、审查依据为何”。

### 2.3 设计原则

1. **执行优先于描述**：计划、步骤、审查与结果均以服务端状态机和实际工具事件为准。
2. **安全边界不旁路**：新执行器只能经既有权限、Tool Orchestrator、确认、审计和凭据解析路径调用工具。
3. **确定性优先**：完成条件、权限、写入确认、预算、超时、状态迁移优先用确定性规则；LLM 只能在受限位置提出计划、选择下一步或给出审查意见。
4. **最小模式成本**：不能因为引入 Plan-Exec 使简单问答变慢；默认选择成本最低、满足约束的模式。
5. **不暴露思维链**：对用户和普通管理界面只展示可审计的计划摘要、工具意图、事实依据、状态和错误摘要，不存储或返回模型私有推理文本。
6. **版本可重放**：一次运行固定 Agent、工作流、Skill、策略包、模型路由、工具 Schema、知识库和执行政策快照；后续配置变更不改写历史运行解释。
7. **先后端灰度，后产品面**：首期只扩展现有聊天和 Trace 契约，不新建路由或重做工作台。任何后续界面任务须另行完成用户确认的 shape 与桌面端视觉验收。

## 3. 范围

### In Scope

- 服务端混合模式路由、真实 Plan-Exec 状态机、受限重试/重规划与受控 Reflect。
- 运行、计划、步骤、审查与事件的持久化模型，以及与既有 Trace 的关联。
- 现有聊天（流式和非流式）、OpenAPI 和评测运行接入同一运行时语义。
- 模式、计划摘要、步骤事实和审查结果的 Trace/评测/审计/费用扩展。
- 灰度开关、熔断与回退策略；新功能默认关闭。
- 后端、PostgreSQL、权限、并发、回归和生产发布验收设计。

### Out Of Scope

- 不提供用户可编辑的通用流程图、拖拽编排器或任意脚本沙箱。
- 不引入多 Agent 自由辩论、无限自治、后台无限执行或跨组织共享计划。
- 不改变既有 Agent/Skill/Tool/KB 的授权模型，不新增任意写工具权限。
- 不把用户请求、模型输出或外部工具结果自动写入长期记忆；记忆候选仍遵循 FEAT-131 的审核与证据规则。
- 不新增移动端适配、移动端页面或移动端验收。
- 首期不新增独立产品页面、计划画布或新的可视化控制台。

## 4. 用户与核心场景

| 场景 | 期望模式 | 成功定义 |
| --- | --- | --- |
| 简短问答、无需工具的说明 | `DIRECT` | 一次模型生成，保留现有安全与输出契约，不创建计划。 |
| 单一或少量可独立的只读查询 | `REACT` | 模型按工具结果继续决策，在预算内收口并形成答案。 |
| “先查订单，再查工单，再依据两者形成处理建议” | `PLAN_EXEC` | 创建顺序/依赖明确的服务端计划；每步真实执行、验证与记录，失败时受限重试或重规划。 |
| 需要多源分析、结构完整报告或关键结论 | 主模式 + `REFLECT` | 先验证事实和输出契约，再由受限审查器判断是否修订；超过上限后给出部分结果或转人工。 |
| 含写操作、敏感数据或高风险工具 | 任意主模式 + 人工确认 | 计划可准备到确认前，但未经既有确认机制不得执行写步骤；拒绝/超时后安全停止。 |

## 5. 总体架构

```text
请求入口（Web / 渠道 / OpenAPI / Evaluation）
  → 既有认证、组织、Agent/Skill/策略解析与输入安全
  → Runtime Mode Router
      ├─ DIRECT：单次生成
      ├─ REACT：既有受限工具循环
      └─ PLAN_EXEC：Plan Builder → Durable Step Executor → 可选 Replan
  → 可选 Reflect Gate（确定性校验 → 受限审查 → 定向修订或移交）
  → 既有消息持久化、工具审计、费用、Trace、评测与发布门禁

所有模式
  → AgentTaskRun / Plan / Step / Review / Event（运行事实）
  → AgentRunTrace（管理员可见的脱敏投影）
```

新增 `AgentTaskRuntimeService` 作为唯一模式路由和任务状态机入口。`ChatOrchestratorService` 保留会话、RAG、流式输出与兼容响应职责，但不得自行绕过该服务执行新的计划步骤。`AgentWorkflowRuntimeService` 继续负责发布定义检查、版本钉住和治理视图；它不承担通用代码解释执行。

## 6. 模式路由设计

### 6.1 路由输入

路由器只读取脱敏、已授权的运行上下文：用户请求特征、有效 Agent/Skill 输出契约、工具风险与写入属性、当前会话未完成状态、显式技能选择、调用渠道、评测模式及策略阈值。

`ModeDecision` 至少包含：

```text
mode: DIRECT | REACT | PLAN_EXEC
reflectRequired: boolean
reasonCodes: stable enum[]
riskLevel: LOW | MEDIUM | HIGH
budget: maxSteps, maxToolRounds, maxReplans, maxReflectRounds, deadlineMs
requiresConfirmation: boolean
```

禁止把自由文本“推理理由”作为路由事实或持久化字段。

### 6.2 路由规则

规则按以下固定优先级求值：

1. 安全策略、权限或渠道规则拒绝时，直接 `BLOCKED`，不选模式。
2. 当前会话存在已确认但未完成的确定性续执行状态时，按既有专用续执行路径处理；不得重新生成一份泛化计划覆盖它。
3. 无授权工具、无多步要求且输出契约不要求外部事实时选择 `DIRECT`。
4. 仅含独立只读工具调用、预估工具轮次不超过策略阈值且没有步骤依赖时选择 `REACT`。
5. 出现显式顺序/依赖词、多类工具或知识源、需要中间产物、需要跨步骤核验、预计工具调用超过 ReAct 阈值，或策略明确指定时选择 `PLAN_EXEC`。
6. 写工具、敏感工具、外部副作用、法律/财务/客户权益敏感输出强制 `requiresConfirmation=true`；这不等同于允许写入。
7. 结构化报告、候选高风险结论、明确要求审查或策略标记的 Agent，设置 `reflectRequired=true`。

初期路由采用规则优先、LLM 仅可输出受 JSON Schema 约束的建议分类。规则与工具元数据冲突时规则获胜；模型无法解析或超时时降级为 `DIRECT` 或受限 `REACT`，绝不擅自升级到 `PLAN_EXEC` 或写操作。

### 6.3 预算

平台策略包和已发布 Agent 版本可分别收紧下列上限，运行时取更小值：

| 字段 | 默认建议 | 硬约束 |
| --- | --- | --- |
| `maxToolRounds` | 沿用现有策略 | 1–12 |
| `maxSteps` | 6 | 1–12 |
| `maxReplans` | 1 | 0–2 |
| `maxReflectRounds` | 1 | 0–2 |
| `deadlineMs` | 由渠道策略提供 | 必须有上限 |
| `maxConcurrentRunsPerOrg` | 由组织并发政策提供 | 不得绕过既有并发限制 |

预算耗尽时必须进入 `PARTIAL`、`FAILED` 或 `HANDOFF_REQUIRED`，并以现有结果和缺失事实收口；禁止继续工具调用或静默重新开始。

## 7. Plan-Exec 状态机

### 7.1 计划结构

计划由 `Plan Builder` 生成严格 JSON，服务端 Schema、权限、预算和依赖校验通过后才可执行。计划不存储思维链，只存可操作事实。

```json
{
  "goal": "对用户请求的可验证目标摘要",
  "successCriteria": ["可验证的完成条件"],
  "steps": [
    {
      "key": "step-1",
      "kind": "RETRIEVE | TOOL | SYNTHESIZE | VERIFY | REQUEST_CONFIRMATION | HANDOFF",
      "dependsOn": [],
      "allowedToolNames": ["当前运行时有效工具的子集"],
      "inputContract": {"requiredFacts": []},
      "expectedEvidence": ["脱敏证据类型或字段"],
      "onFailure": "STOP | RETRY | REPLAN | HANDOFF"
    }
  ]
}
```

服务端拒绝循环依赖、未知步骤类型、未授权工具、空目标、超过预算的步骤、跨 Agent/组织引用和跳过确认的写操作。步骤顺序按拓扑排序稳定化，计划摘要与输入快照分别有不可变哈希。

### 7.2 运行状态

`AgentTaskRun`：

```text
CREATED → ROUTED → PLANNING → READY → RUNNING
RUNNING → WAITING_CONFIRMATION | REVIEWING | SUCCEEDED | PARTIAL | FAILED | HANDOFF_REQUIRED | CANCELLED | TIMED_OUT
WAITING_CONFIRMATION → RUNNING | CANCELLED | TIMED_OUT
REVIEWING → RUNNING（定向修订）| SUCCEEDED | PARTIAL | HANDOFF_REQUIRED | FAILED
```

`AgentTaskStep`：

```text
PENDING → READY → RUNNING → SUCCEEDED | FAILED | BLOCKED | SKIPPED | CANCELLED | TIMED_OUT
```

状态迁移以数据库乐观锁和条件更新保护。一个 `runId` 同时只能有一个 Step Executor 租约；租约过期后可由同一组织内受控恢复器接管，恢复器只能从已持久化的最后成功步骤继续，不能重复已确认的副作用。

### 7.3 执行语义

1. 创建运行快照并作出模式决策。
2. `PLAN_EXEC` 生成并校验计划，写入 `READY`；`DIRECT` 与 `REACT` 同样创建运行记录，但没有计划行。
3. 执行器只领取依赖均 `SUCCEEDED` 的单个 `READY` 步骤；同一计划默认串行。并行执行须由未来独立设计批准。
4. `RETRIEVE` 复用现有受控 RAG/记忆读取；`TOOL` 只能通过现有 Tool Orchestrator；`SYNTHESIZE` 只能消费步骤输出中的脱敏、预算内摘要；`VERIFY` 运行确定性后置条件。
5. 步骤写入真实开始/结束时间、工具调用引用、最小必要结果摘要、错误类别和证据引用。原始密钥、完整敏感 payload 与模型私有推理不得写入。
6. 成功条件满足后标记 `SUCCEEDED`。若有失败，按该步骤允许的处理策略进入一次受限重试、一次受限重规划、人工确认或结束状态。

### 7.4 重试与重规划

- 只对明确可重试的短暂错误（连接超时、限流、上游 5xx 等）执行有限退避重试；权限拒绝、参数无效、确认缺失、数据不存在和安全拒绝不得自动重试。
- 重规划只可修改尚未开始的步骤，必须保留已成功步骤、原始目标、预算和审批约束，并新建 `planRevision`。不得在重规划中扩大工具白名单、数据范围、风险等级或绕过确认。
- 重规划失败、超出次数或产生不可验证计划时转 `HANDOFF_REQUIRED` 或 `PARTIAL`，附上可读的缺口摘要。

## 8. Reflect 设计

Reflect 是质量增强层，不是第二个不受约束的 Agent。

### 8.1 校验顺序

1. **确定性 Gate**：检查计划成功条件、权限、确认、工具结果状态、必要来源、输出 JSON Schema、最大步骤/工具轮次/时延和安全拒答要求。
2. **受限 Reviewer**：仅当策略要求且确定性 Gate 未阻断时，接收任务目标、输出草稿、脱敏事实摘要和固定审查量表，返回 `PASS | REVISE | HANDOFF` 与结构化问题码。
3. **定向修订**：只允许修订 `SYNTHESIZE`、`VERIFY` 等无副作用步骤；不得在审查阶段自行增加新写操作。若需补事实，显式创建受预算约束的新只读步骤或转人工。

### 8.2 审查量表

首期固定维度：目标覆盖、事实可追溯、内部一致性、输出契约、安全/确认完整性、明确的不确定性。评审回复使用 JSON Schema，禁止保存自然语言思维链。

`REVISE` 最多触发 `maxReflectRounds` 次；第二次失败或出现高风险冲突直接 `HANDOFF_REQUIRED`。任何 P0、安全、权限或写入确认失败均由确定性 Gate 直接阻断，不允许 LLM reviewer 覆盖。

## 9. 数据模型与迁移原则

迁移版本号在实施开始前按主线最新 Flyway 版本分配，不能在本设计中预占固定编号。所有新表必须具备 `org_id`、创建/更新时间、最小索引和组织级清理覆盖；不得复制现有 `agent_run_trace` 的全文字段。

| 表 | 关键字段 | 用途 |
| --- | --- | --- |
| `agent_task_run` | `id, org_id, session_id, agent_id, channel, mode, status, risk_level, policy_snapshot_json, deadline_at, version` | 任务运行事实与状态机根。 |
| `agent_task_plan` | `id, org_id, run_id, revision_no, goal_summary, plan_json, plan_hash, status` | 不可变计划修订；当前修订由 run 引用。 |
| `agent_task_step` | `id, org_id, run_id, plan_id, step_key, step_kind, status, depends_on_json, attempt_no, tool_call_refs_json, evidence_refs_json, error_code, version` | 单步执行与恢复依据。 |
| `agent_task_review` | `id, org_id, run_id, review_round, gate_status, reviewer_status, issue_codes_json, result_summary` | 确定性与受限审查结论。 |
| `agent_task_event` | `id, org_id, run_id, step_id, event_type, occurred_at, payload_redacted_json` | 追加式事件和 Trace 投影来源。 |

索引至少覆盖：`(org_id, id)`、`(org_id, session_id, created_at desc)`、`(org_id, agent_id, status, created_at desc)`、`(org_id, run_id, step_key)` 和恢复器所需的 `status + lease_expires_at`。外键、级联删除、组织导出、保留、legal hold 与 purge 必须按现有组织生命周期协议设计，不得留下计划或事件孤儿记录。

## 10. 接口与事件契约

### 10.1 现有聊天兼容

- 既有 `/ai/chat`、`/ai/chat/stream` 与 OpenAPI 聊天端点保留请求/响应兼容性。
- 成功响应增加可选 `runtimeExecution` 扩展：`runId`、`mode`、`status`、`planSummary`、`currentStep`、`reviewStatus`、`partialReason`。历史客户端忽略未知字段即可继续工作。
- 流式路径新增仅供已认证前端消费的结构化状态事件：`runtime_started`、`plan_ready`、`step_started`、`step_completed`、`waiting_confirmation`、`review_completed`、`runtime_completed`。事件只含用户可见、脱敏摘要，不发送模型思维链、密钥、内部工具 payload 或稳定数据库主键之外的敏感标识。
- 普通文本 answer 只在运行处于可收口终态后持久化；`WAITING_CONFIRMATION` 返回明确确认请求；`PARTIAL` 必须区分已确认与未完成事项。

### 10.2 管理与评测 API

```text
GET /admin/agents/task-runs?agentId=&status=&mode=&from=&to=
GET /admin/agents/task-runs/{runId}
POST /admin/agents/task-runs/{runId}/cancel
POST /admin/agents/task-runs/{runId}/resume        # 仅受控恢复，非任意重跑
GET /evaluation/runs/{runId}/task-execution        # 评测只读投影
```

- 组织、Agent `VIEW/RUN/MANAGE` 与平台角色沿用现有 API 的最小权限原则；任何查询都以 `org_id` 为数据库条件，不能先按裸 ID 读取再内存过滤。
- `cancel` 仅取消尚未执行的步骤；已经提交给外部系统的副作用不能伪称已撤销，必须展示其真实工具结果与后续人工处置需求。
- 评测模式 `runMode=EVALUATION` 默认禁止写步骤。写意图应记录为 `BLOCKED_WRITE` 事实，供 FEAT-106 断言使用。

## 11. Trace、评测、审计与费用

### 11.1 Trace 事实模型

`AgentRunTrace` 增加脱敏投影字段：`runtimeRunId`、`executionMode`、`modeReasonCodes`、`planRevisionCount`、`stepCount`、`completedStepCount`、`replanCount`、`reviewStatus`、`terminalStatus`、`partialReasonCode`。

Trace 节点按真实事件生成：`MODE_ROUTING`、`PLAN_VALIDATION`、`STEP`、`CONFIRMATION`、`REPLAN`、`REFLECT_GATE`、`FINALIZATION`。继续保留“工作流定义检查”节点，但它只能描述版本/边界解析，不能为工具、RAG 或计划步骤背书。

### 11.2 评测与门禁

FEAT-106 增加可选确定性断言：期望模式、步骤数范围、步骤顺序、禁止/必须重规划、确认前无写调用、审查状态、终态、计划预算与部分完成原因。评测运行与生产运行共享状态机，但默认禁止真实写工具。

强制评测套件应覆盖每个已启用模式。模式路由、步骤状态迁移、确认门禁、重规划收紧、审查上限和 Trace 真实性出现回归时，发布结论必须为 `BLOCKED`。

### 11.3 费用与指标

现有用量账本按真实模型、检索与工具调用计费，不因计划文本、审查或事件重复计量。新增聚合指标：各模式成功率、部分完成率、重规划率、审查修订率、确认等待率、步骤耗时、预算耗尽率、恢复成功率与每种模式的成本分位数。

## 12. 安全、可靠性与并发

- 所有模型计划/审查输出必须经过严格 JSON Schema 解析；解析失败降级或停止，不能把自由文本当命令执行。
- 工具名、参数、主体、组织、Agent、知识库、凭据和写入范围都由服务端重新解析；计划 JSON 不拥有授权权力。
- 写步骤必须进入既有确认和幂等路径；工具适配器需接受 `runId + stepId + attemptNo` 作为幂等键来源，服务端禁止在确认后重复派发同一副作用。
- 每个运行、计划、步骤和审查更新使用乐观锁；恢复租约采用比较并交换式更新，避免双执行器并发执行同一步骤。
- 超时、模型不可用、上游错误和应用重启均应保留最后一致状态并可给出可读恢复/移交结论。禁止以“模型最终回复”覆盖已记录的步骤失败。
- 计划摘要、事件 payload、Trace 详情、日志和导出均经现有脱敏器处理；不记录密钥、密码、token、个人敏感正文、模型思维链或未授权工具原文。

## 13. 分阶段交付与建议任务拆分

以下是建议工作包，尚未创建任务授权或分配；实现开始前必须按项目门禁建立对应 `TASK`、assignment、状态文件和独立分支。

| 顺序 | 建议工作包 | 责任角色 | 依赖 | 交付结果 |
| --- | --- | --- | --- | --- |
| P1 | 运行事实与真实计划状态机 | fullstack-agent | 无 | 数据迁移、`AgentTaskRuntimeService`、Plan Schema 校验、串行只读步骤、恢复租约、真实 Trace。 |
| P2 | 现有聊天接入与 Plan-Exec 灰度 | fullstack-agent | P1 | Web/OpenAPI/流式兼容、白名单 Agent 灰度、确认前停止、回退开关。 |
| P3 | 模式路由器 | backend-agent | P2 | 规则优先路由、受限 JSON 建议、策略/Agent 阈值、路由评测与指标。 |
| P4 | 受控 Reflect 与发布门禁 | backend-agent | P2,P3 | 确定性 Gate、Reviewer Schema、定向修订、FEAT-106 断言与门禁。 |
| P5 | 管理 Trace 投影与现有界面接入 | frontend-agent + backend-agent | P1–P4 | 仅在确认 shape 后实施现有 Trace 的计划/步骤事实展示及桌面验收。 |
| P6 | 生产灰度与质量运营 | qa-agent + release-agent | P2–P5 | 组织/Agent 灰度、评测基线、告警、生产 smoke、回滚演练。 |

P1 不得提前实现自由多 Agent、并行步骤、可编辑画布或任意脚本执行。P2 只允许少量只读工具和明确的测试 Agent；任何写工具必须在 P4 的确认/幂等验证后才可进入灰度。

## 14. 验收标准

### 14.1 P1：计划执行真实性

- 对明确三步只读任务，服务端持久化计划、依赖、步骤开始/完成、证据引用和终态；Trace 与数据库步骤事实一一对应。
- 发布工作流的“定义检查”与真实步骤 Trace 同时存在时，前者绝不声称工具/RAG 已执行。
- 注入一个可重试上游失败时，最多按政策重试；注入不可重试权限失败时不重试，按步骤规则停止或移交。
- 应用重启后，只能从最后成功步骤恢复，且不会重复执行同一有幂等键的副作用步骤。
- 跨组织、跨 Agent、超预算、循环依赖、未知工具、未授权工具和绕过确认的计划均被服务端拒绝。

### 14.2 P2/P3：模式选择与兼容

- 简单无工具问题选择 `DIRECT`，单一只读查询选择 `REACT`，具有显式依赖的多源任务选择 `PLAN_EXEC`；每一判定均有稳定 reason code。
- 开关关闭、路由解析失败、计划校验失败时，系统安全回退到既有 Direct/ReAct 行为或明确失败，不增加任何副作用。
- 流式和非流式端点仍产生一致的最终业务结果、运行 ID 和真实工具事实；旧客户端不因新增字段失败。

### 14.3 P4：Reflect 与安全

- 确定性缺失（必要来源、确认、输出字段、步骤预算）直接阻断或部分收口，LLM reviewer 不能覆盖。
- Reviewer `REVISE` 只能修改无副作用步骤，且不超过政策轮次；超限后移交或部分完成。
- 评测能断言模式、步骤顺序、确认前零写调用、重规划上限与审查终态；关键失败阻断发布。

### 14.4 质量门

- 新增/修改的后端单元与 PostgreSQL 集成测试、定向编译、静态 diff 检查通过；既有失败必须与本功能失败分开记录，不能伪报全量通过。
- 评测至少覆盖 Direct、ReAct、Plan-Exec、Reflect、确认等待、重规划、恢复、超时和跨组织拒绝十类场景。
- 生产前按 `docs/production-release-runbook.md` 运行 `./scripts/release-acr.sh --dry-run`，保持 Git tag、镜像 tag、`CICI_APP_VERSION`、`VITE_CICI_APP_VERSION` 与 `CICI_IMAGE_TAG` 一致。
- 后续 UI 工作包必须按设计事实源完成真实桌面浏览器、截图、交互状态、无外层横向溢出与 console error/warning 检查；不做移动端范围。

## 15. 灰度、监控与回滚

### 15.1 特性开关

按平台、组织、Agent 三层由严到松解析：

```text
agent-runtime.plan-exec.enabled
agent-runtime.mode-router.enabled
agent-runtime.reflect.enabled
agent-runtime.resume.enabled
```

默认全部关闭。先以内部测试组织和只读工具 Agent 启用 P1/P2，再按评测结果逐步开放 P3/P4。任一开关关闭后，新的请求不创建相应计划/审查；正在等待确认的运行保留其状态并可取消，不得无提示切换到不同模式继续执行。

### 15.2 监控与告警

- 监控每种模式的失败、超时、部分完成、重规划、审查拒绝、恢复与确认等待率。
- 对同一 Agent 的连续计划校验失败、步骤重复领取、确认后重复副作用尝试、Trace/步骤计数不一致、跨组织拒绝异常和预算耗尽突增设置告警。
- 发布后观察期内保留旧 ReAct 路径作为受开关控制的回退路径；不删除新表中的审计事实。

### 15.3 回滚

- 应用回滚或关闭相应开关可停止新计划、路由或审查功能；现有 Direct/ReAct 路径保持可用。
- 数据迁移采用只增不改。历史 `agent_task_*` 记录保留供审计与导出，不执行破坏性回滚。
- 外部副作用只能由原业务系统或既有补偿流程处理，禁止将应用回滚描述为已撤销外部动作。

## 16. 实现进展与交接

- 当前状态：TASK-235 已获授权，开始 P1 的运行事实与真实计划状态机实现；尚未创建迁移或业务代码。
- 实施前先读取：`docs/specs/FEAT-004-agent-flow-compile-triggers-and-executions.md`、`docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md`、`docs/specs/FEAT-122-runtime-execution-trace-correction.md`、`docs/specs/FEAT-130-forced-skill-execution-context.md`、`docs/specs/FEAT-131-agent-memory-platform.md`。
- 首个实现任务必须以 P1 为边界，并明确禁止把 `workflow_code` 字符串解析误升级为任意代码执行器。
- 若产品决定新增计划查看/恢复界面，必须先完成独立 UI shape、用户确认与设计事实源检查；本规格不授权直接改造产品信息架构。
