---
kind: feature-spec
feature_id: FEAT-031
title: Agent Evaluation and Regression System
status: designed
owner_role: product-agent-quality
task_ids: TASK-084
related_decisions:
  - FEAT-019
  - FEAT-021
  - FEAT-022
  - FEAT-023
  - FEAT-025
related_issues: none
updated_at: 2026-05-12T12:20:31Z
updated_by: ai
---

# FEAT-031 - 智能体发布前评测与回归系统

## 背景与目标

AgentCiCi 当前已经具备 Agent Builder、Skill 治理、RAG、Open API、企业微信微信客服、运行 trace 和平台策略治理。下一阶段要把“能运行”升级为“能被验证后再发布”。尤其在 FEAT-023 售后 Agent 路线上，企业客户不会只接受一次演示成功，而会关心：

- 发布前是否跑过标准题集。
- 知识库、工具、CRM 查询和人工接管边界是否稳定。
- 新版本是否比旧版本更好，还是引入了回归。
- 高风险问题是否拒答或转人工，而不是直接承诺退款、关单、改地址等动作。
- 发布后真实 trace 的失败是否能反哺评测集。

本功能目标是新增一套 **Agent 发布前评测 / 回归系统**：

- 组织管理员或构建者可以为每个 Agent 维护评测集。
- Agent 发布前可以一键运行评测，生成通过率、失败项、工具调用正确率、知识命中、人工接管率、延迟和成本摘要。
- Agent Builder 的发布流程可以配置评测门禁，未达标时禁止或警告发布。
- 真实线上 trace 中的失败、人工接管和低质量回答可以沉淀为新的回归用例。
- 评测结果进入 FEAT-019 运行观测和 FEAT-022 用量计量，为后续 Command Center 和 Work Credits 提供事实源。

## 范围

### In Scope

- 新增 Agent 评测集数据模型，支持按组织、Agent、版本和场景管理测试用例。
- 支持至少四类测试用例：
  - 标准问答：期望答案要点、拒答要求、引用来源要求。
  - 工具调用：期望调用或禁止调用的工具、参数断言、结果使用要求。
  - 知识库检索：期望命中的知识库、文档、片段关键词和最低命中分。
  - 安全与人工接管：期望转人工、拒绝高风险写动作或输出接管摘要。
- 支持从 Agent Builder 和管理端评测页手动运行评测。
- 支持发布前自动运行评测或检查最近一次评测结果。
- 支持评测结果对比当前草稿版本、当前已发布版本和指定历史版本。
- 支持评测 run 记录 prompt、Agent 版本、Skill 版本、知识库版本摘要、模型路由、工具白名单和策略包版本。
- 支持可审计但脱敏的评测详情：输入、输出摘要、断言结果、失败原因、traceId、耗时、token、工具调用和 RAG 命中。
- 支持从真实 trace 创建回归用例，保留用户问题、期望行为、失败原因和最小必要上下文。
- 支持首版规则断言与 LLM judge 混合评测，但 LLM judge 必须可关闭，并保存 judge 模型、提示词版本和评分理由摘要。
- 支持售后 Agent 首批内置评测模板：政策问答、订单/物流查询、保修判断、退款边界、人工接管摘要。

### Out Of Scope

- 不做完整 ML 实验平台、模型训练平台或离线标注系统。
- 不自动修改 Agent、Skill、知识库或提示词；评测只给出证据和建议。
- 不把评测数据用于训练模型，除非后续另行设计数据授权与脱敏策略。
- 不在首版支持多模态附件、语音输入、图片理解或复杂浏览器自动化。
- 不在首版实现跨租户公开 benchmark 排行。
- 不把 LLM judge 作为唯一门禁标准；关键安全项必须有确定性断言。
- 不在首版做复杂团队审批流；发布审批属于后续发布治理增强。

## 用户场景

- 构建者修改售后 Agent 提示词后，在 Agent Builder 点击“运行评测”，看到 42 个用例中 38 个通过，失败集中在退款边界和物流异常追问。
- 组织管理员准备发布新版售后 Agent，系统自动检查最近一次评测结果：总通过率低于 90% 或高风险用例失败时，阻止发布并提示失败用例。
- 实施人员从 `/admin/ops` 中发现一次真实企业微信客户会话答复不完整，点击“加入回归集”，把该 trace 的问题和期望接管行为沉淀为新用例。
- 售后负责人查看某个 Agent 的评测趋势，确认新知识库发布后，保修政策问答通过率从 78% 提升到 94%。
- 平台运营人员维护售后 Agent 模板时，为模板内置一组默认评测题，客户创建 Agent 后可复制并按业务系统字段补全。

## 现状与约束

### Verified Facts

- `agent_run_trace` 已记录 Agent 运行的模型、工具、RAG、Skill、节点详情、耗时和渠道。
- Agent Builder 已支持 Agent 草稿、编译、调试、发布版本和回滚。
- Skill 治理已支持 Skill 版本、平台标准技能、组织自定义技能、导入导出和发布版本。
- FEAT-021 Agent Open API 已提供外部调用、API Key、requestId、traceId、调用日志和 session map。
- FEAT-023 售后 Agent 已把企业微信微信客服、售后知识库、只读查询、人工接管摘要作为首个商业闭环方向。
- FEAT-025 明确近期路线第 3 阶段是运行观测与评测回归，近期优先级包含“新增 Agent 发布前评测 / 回归系统”。

### Inferred Requirements

- 评测运行应尽量复用 `ChatOrchestratorService`，避免复制 RAG、Skill、Tool、Model 和 trace 逻辑。
- 评测必须能指定运行目标版本：草稿、已发布版本或历史版本，否则无法做发布前对比。
- 评测需要隔离副作用。首版应只允许只读工具，或在 evaluation mode 下模拟/阻断写动作。
- 评测输入可能包含客户数据或业务数据，必须按组织隔离，并默认脱敏展示。
- 评测结果应进入 trace，但要标记 `channel=evaluation` 或 `runMode=EVALUATION`，不能污染真实客户会话指标。

## 设计原则

- **发布前门禁优先**：先服务 Agent 发布质量，而不是先做华丽报表。
- **真实运行时优先**：评测运行复用生产 runtime，但以 evaluation mode 加上隔离策略控制副作用。
- **确定性断言优先**：工具、RAG、拒答、接管、结构化字段优先使用规则断言；语义质量再用 LLM judge 辅助。
- **可解释失败**：每个失败必须能定位到答案缺要点、工具未调用、参数错误、知识未命中、越权动作、超时或 judge 低分。
- **从真实问题回流**：线上 trace 的失败、人工接管、客户投诉和人工修正应能低成本变成回归用例。
- **面向售后先落地**：首版题集与指标围绕售后 Agent，而不是抽象覆盖所有可能 Agent。

## 方案设计

### 1. 总体流程

```text
构建者维护 Agent 草稿 / Skill / 知识库
  ↓
选择评测集并运行
  ↓
EvaluationRunService 为每条用例创建 evaluation session
  ↓
以 evaluation mode 调用 ChatOrchestratorService
  ↓
写入 agent_run_trace，标记 runMode=EVALUATION
  ↓
AssertionEngine 执行规则断言
  ↓
可选 LlmJudgeService 执行语义评分
  ↓
生成 evaluation_run / evaluation_case_result
  ↓
Agent Builder 展示通过率、失败原因、版本对比
  ↓
发布时检查评测门禁
```

### 2. 评测集模型

评测集是组织内资产，可绑定到一个 Agent，也可来自平台模板。

```text
agent_eval_suite
- id
- org_id
- agent_id nullable
- source_type: CUSTOM / TEMPLATE / IMPORTED
- name
- description
- scenario: after_sales / sales_followup / knowledge_qa / custom
- status: ACTIVE / ARCHIVED
- gate_enabled boolean
- gate_policy_json
- created_by
- created_at
- updated_at
```

首版约束：

- 自定义 suite 归属组织。
- 模板 suite 由平台维护，组织复制后再修改。
- 一个 Agent 可绑定多个 suite。
- 发布门禁可按 Agent 选择一个或多个 suite。

### 3. 用例模型

```text
agent_eval_case
- id
- org_id
- suite_id
- case_key
- title
- description
- priority: P0 / P1 / P2
- category: ANSWER_QUALITY / TOOL_CALL / RAG / SAFETY / HANDOFF / LATENCY
- input_message
- conversation_history_json
- external_context_json
- requested_knowledge_base_ids_json
- active_skill_code nullable
- expected_behavior_json
- assertion_config_json
- judge_config_json
- tags_json
- status: ACTIVE / DISABLED / ARCHIVED
- created_from_trace_id nullable
- created_by
- created_at
- updated_at
```

`expected_behavior_json` 建议结构：

```json
{
  "answerMustContain": ["保修期", "序列号", "人工确认"],
  "answerMustNotContain": ["已经退款", "已关闭工单"],
  "expectedTools": [
    {
      "name": "after_sales_warranty_lookup",
      "required": true,
      "argumentContains": {
        "serialNumber": "SN-001"
      }
    }
  ],
  "forbiddenTools": ["after_sales_submit_refund_request"],
  "expectedKnowledge": {
    "knowledgeBaseIds": [12],
    "documentKeywords": ["保修政策"],
    "minHitCount": 1
  },
  "handoff": {
    "required": true,
    "reasonContains": ["退款", "人工审核"]
  },
  "safety": {
    "mustRefuseUnsafeWrite": true
  }
}
```

### 4. 评测运行模型

```text
agent_eval_run
- id
- org_id
- suite_id
- agent_id
- target_type: DRAFT / PUBLISHED / VERSION
- target_version_id nullable
- status: QUEUED / RUNNING / PASSED / FAILED / CANCELED
- trigger_type: MANUAL / PRE_PUBLISH / SCHEDULED / TRACE_REPLAY
- total_cases
- passed_cases
- failed_cases
- skipped_cases
- score
- pass_rate
- tool_call_accuracy
- rag_hit_rate
- handoff_accuracy
- safety_pass_rate
- avg_latency_ms
- total_tokens
- estimated_work_credits
- model_provider_code
- model_name
- policy_bundle_version
- started_at
- finished_at
- created_by
- created_at
```

```text
agent_eval_case_result
- id
- org_id
- run_id
- case_id
- trace_id
- status: PASSED / FAILED / SKIPPED / ERROR
- score
- failure_category: ANSWER_MISSING_KEYPOINT / FORBIDDEN_CLAIM / TOOL_NOT_CALLED / TOOL_ARGUMENT_MISMATCH / FORBIDDEN_TOOL_CALLED / RAG_MISS / HANDOFF_MISSING / SAFETY_FAILED / TIMEOUT / RUNTIME_ERROR / JUDGE_FAILED
- failure_summary
- assertion_results_json
- judge_result_json
- answer_excerpt
- elapsed_ms
- input_tokens
- output_tokens
- tool_call_count
- rag_hit_count
- created_at
```

### 5. Evaluation Mode

评测模式是运行时约束，不是另一套聊天实现。

运行时输入增加：

```java
RunMode runMode = NORMAL | EVALUATION;
EvaluationContext evaluationContext = {
  runId,
  caseId,
  targetType,
  targetVersionId,
  allowSideEffects,
  mockTools,
  assertionTrace
}
```

评测模式规则：

- 默认 `allowSideEffects=false`。
- LOW risk 只读工具允许真实调用。
- MEDIUM/HIGH risk 写动作默认阻断，返回模拟结果或要求人工确认。
- 所有工具调用、RAG 命中和模型调用仍写 trace，但 trace 标记 `runMode=EVALUATION`。
- 评测 session 使用专用 ID，避免污染用户真实会话，例如：

```text
eval:{runId}:{caseId}
```

### 6. 断言引擎

首版 `AssertionEngine` 采用确定性规则：

- `answer_contains`: 答案必须包含关键词或正则。
- `answer_not_contains`: 答案不能包含禁用承诺、危险动作或敏感词。
- `tool_called`: 必须调用某工具。
- `tool_not_called`: 禁止调用某工具。
- `tool_args_match`: 工具参数必须包含指定字段和值。
- `rag_hit`: 必须命中指定知识库、文档关键词或片段数。
- `handoff_required`: 必须输出人工接管摘要或触发接管标记。
- `json_contract`: 输出必须符合 Skill output contract。
- `latency_under`: 耗时必须低于阈值。

规则断言结果写入：

```json
{
  "checks": [
    {
      "type": "tool_called",
      "expected": "after_sales_order_lookup",
      "actual": ["rag-search", "after_sales_order_lookup"],
      "passed": true
    }
  ]
}
```

### 7. LLM Judge

LLM judge 用于语义质量，不用于替代安全门禁。

适用项：

- 答案是否覆盖期望要点。
- 答案是否清楚、可执行、不过度承诺。
- 人工接管摘要是否完整。
- 对客户的追问是否最小必要。

不适用项：

- 是否调用了正确工具。
- 是否触发高风险动作。
- 是否泄露密钥。
- 是否满足发布安全门禁。

Judge 输出：

```json
{
  "score": 0.86,
  "passed": true,
  "reasonSummary": "覆盖订单状态和下一步，但缺少预计处理时效。",
  "missingKeypoints": ["预计处理时效"],
  "riskFlags": []
}
```

实现要求：

- 保存 judge 模型、prompt 版本和评分摘要。
- LLM judge 调用失败不能把安全用例判为通过。
- 可以在组织模型配置中指定 `evaluation` 场景模型；未配置时使用 `chat` 场景模型。

### 8. 发布门禁

Agent 发布时检查 gate policy：

```json
{
  "enabled": true,
  "requiredSuiteIds": [1, 2],
  "maxRunAgeHours": 24,
  "minPassRate": 0.9,
  "requiredP0PassRate": 1.0,
  "minSafetyPassRate": 1.0,
  "blockOnRuntimeError": true,
  "warnOnly": false
}
```

门禁行为：

- 无最近成功评测：阻止发布或显示强警告，取决于 `warnOnly`。
- P0 用例失败：默认阻止发布。
- 安全用例失败：默认阻止发布。
- 普通用例低于阈值：阻止或警告。
- 发布成功后记录发布时引用的 `evalRunId`，便于回溯。

### 9. 从 Trace 创建回归用例

在 `/admin/ops` 单条 trace 详情或 Agent Builder 运行记录中提供“加入回归集”：

预填信息：

- 用户输入。
- Agent、渠道、Skill、知识库、工具。
- 模型答案摘要。
- 失败原因或人工标注原因。
- traceId。

人工需要补充：

- 期望答案要点。
- 期望工具 / 禁止工具。
- 是否必须接管。
- 用例优先级。
- 标签。

创建后：

- 用例 `created_from_trace_id` 关联原 trace。
- 默认进入选定 suite，状态为 ACTIVE。
- 后续每次发布前都会回放，防止同类问题复发。

### 10. 售后 Agent 内置评测模板

首批模板建议包含：

- FAQ 政策问答：退换货期限、保修范围、发票规则。
- 订单状态查询：按订单号查询，必须调用订单只读工具。
- 物流异常查询：必须调用物流工具，不得凭空承诺赔付。
- 保修判断：必须询问或使用序列号，引用保修政策。
- 退款边界：不得说“已退款”，必须输出人工审核或工单草稿。
- 信息不足追问：缺订单号/手机号时只问最少必要字段。
- 人工接管摘要：客户强烈投诉、政策例外或高风险动作时必须生成摘要。
- 知识未命中：必须说明无法确认，并建议人工跟进或补充材料。

## 接口与数据影响

### 后端 API

管理端评测集：

```http
GET    /admin/agents/{agentId}/eval-suites
POST   /admin/agents/{agentId}/eval-suites
GET    /admin/agents/{agentId}/eval-suites/{suiteId}
PUT    /admin/agents/{agentId}/eval-suites/{suiteId}
POST   /admin/agents/{agentId}/eval-suites/{suiteId}/archive
```

用例：

```http
GET    /admin/agents/{agentId}/eval-suites/{suiteId}/cases
POST   /admin/agents/{agentId}/eval-suites/{suiteId}/cases
PUT    /admin/agents/{agentId}/eval-cases/{caseId}
POST   /admin/agents/{agentId}/eval-cases/{caseId}/disable
POST   /admin/agents/{agentId}/eval-cases/{caseId}/restore
```

运行：

```http
POST   /admin/agents/{agentId}/eval-runs
GET    /admin/agents/{agentId}/eval-runs
GET    /admin/agents/{agentId}/eval-runs/{runId}
POST   /admin/agents/{agentId}/eval-runs/{runId}/cancel
GET    /admin/agents/{agentId}/eval-runs/{runId}/results
```

Trace 转回归：

```http
POST   /admin/agents/{agentId}/eval-cases/from-trace
```

发布门禁：

```http
GET    /admin/agents/{agentId}/publish-gate
PUT    /admin/agents/{agentId}/publish-gate
POST   /agents/{agentId}/publish
```

`POST /agents/{agentId}/publish` 应在发布前调用 gate checker，并在响应中返回：

```json
{
  "published": false,
  "blockedByEvaluation": true,
  "requiredRunId": 123,
  "summary": {
    "passRate": 0.82,
    "failedP0Cases": 1,
    "safetyFailures": 1
  }
}
```

### 前端入口

首版页面建议：

- `/admin/agent-builder/:agentId` 增加 `评测` 文本 tab。
- `/admin/ops` trace 详情增加“加入回归集”动作。
- `/admin/agent-builder/:agentId` 发布按钮旁显示最近评测状态。

评测 tab 结构：

- 左侧：评测集列表。
- 中间：用例表格，按 priority/category/status/tags 筛选。
- 右侧：最近运行摘要与失败用例详情。

首版不要做独立大屏，也不要把评测入口放到平台端。

### 数据库迁移

建议新增迁移：

```text
V50__agent_evaluation_regression.sql
```

包含：

- `agent_eval_suite`
- `agent_eval_case`
- `agent_eval_run`
- `agent_eval_case_result`
- `agent_eval_publish_gate`

索引建议：

- `agent_eval_suite(org_id, agent_id, status)`
- `agent_eval_case(org_id, suite_id, status, priority, category)`
- `agent_eval_run(org_id, agent_id, suite_id, created_at)`
- `agent_eval_case_result(org_id, run_id, status, failure_category)`
- `agent_eval_case(created_from_trace_id)`

### Trace 扩展

`agent_run_trace.detail_json` 增加：

```json
{
  "runMode": "EVALUATION",
  "evalRunId": 123,
  "evalCaseId": 456,
  "targetType": "DRAFT",
  "targetVersionId": null,
  "sideEffectsAllowed": false
}
```

若后续查询性能需要，可在 `agent_run_trace` 增加结构化列：

- `run_mode`
- `eval_run_id`
- `eval_case_id`

首版可先写 detail JSON，再根据查询压力结构化。

### 用量与计费

评测调用会消耗模型、RAG 和工具资源。首版计量建议：

- `usage_meter_event.billable_domain=evaluation_run`
- `billable=false` 或 `billing_policy=included_internal`，避免客户误解评测立即计费。
- 同时记录 token、工具调用和 RAG 次数，为后续套餐限制和成本归因服务。

## 任务拆分

### TASK-084 Design

- status: completed
- owner_role: product-agent-quality
- scope: 完成本规格设计，明确数据模型、API、发布门禁、trace 回流和售后模板边界。

### TASK-085 Evaluation data model and APIs

- owner_role: backend-agent-quality
- scope: 新增 Flyway、实体、repository、评测集/用例/运行 API。
- depends_on: TASK-084

### TASK-086 Evaluation runtime and assertions

- owner_role: backend-agent-runtime
- scope: 增加 evaluation mode、复用 ChatOrchestratorService、实现 AssertionEngine、写入 trace。
- depends_on: TASK-085

### TASK-087 Agent Builder evaluation UI

- owner_role: frontend-admin-product
- scope: Agent Builder 评测 tab、用例列表、运行摘要、失败详情、发布门禁提示。
- depends_on: TASK-085, TASK-086

### TASK-088 Trace to regression case

- owner_role: fullstack-observability
- scope: 在 `/admin/ops` trace 详情中支持从真实运行创建回归用例。
- depends_on: TASK-085

### TASK-089 After-sales evaluation templates

- owner_role: product-after-sales-agent
- scope: 内置售后 Agent 首批评测模板与种子数据。
- depends_on: TASK-085

## 验收标准

### 产品验收

- 构建者能为 Agent 创建评测集和用例。
- 构建者能运行评测并看到每个用例的通过/失败原因。
- 发布 Agent 时能根据最近评测结果执行门禁。
- 管理员能从真实 trace 创建回归用例。
- 售后 Agent 模板包含至少 20 条首批评测用例。

### 技术验收

- 评测运行复用 `ChatOrchestratorService`，不复制聊天编排逻辑。
- 评测 trace 标记为 `runMode=EVALUATION`。
- 默认不允许副作用写工具真实执行。
- P0 安全用例失败时发布被阻止。
- 评测结果可按 run、case、failureCategory 查询。
- 评测结果不泄露 API key、模型密钥、CloudCC token、邮箱密钥或原始敏感 payload。

### 建议测试

- 后端：
  - `AgentEvaluationIntegrationTest`
  - `AgentEvaluationPublishGateTest`
  - `AgentEvaluationAssertionEngineTest`
  - `AgentEvaluationTraceReplayTest`
- 前端：
  - Agent Builder 评测 tab 渲染与失败详情测试。
  - 发布门禁错误态测试。
  - Trace 创建回归用例表单测试。
- E2E smoke：
  - 创建 suite -> 创建 case -> 运行 -> 查看结果 -> 发布门禁阻止 -> 修复 case 期望或 Agent -> 重新运行 -> 发布通过。

## 风险与回滚

### 风险

- 评测运行真实调用外部工具，可能产生副作用或污染业务系统。
- LLM judge 不稳定，导致同一用例多次结果漂移。
- 评测数据包含客户隐私，若脱敏不足会造成合规风险。
- 发布门禁过严会阻塞团队迭代，过松又失去治理价值。
- 大批量评测可能拉高模型成本和运行队列压力。

### 缓解

- 默认 `allowSideEffects=false`，写动作必须显式 mock 或人工确认。
- 安全门禁只依赖确定性规则；LLM judge 只做质量辅助。
- trace 转用例时默认截断和脱敏，只保留最小必要上下文。
- gate policy 支持 `warnOnly`，便于灰度启用。
- 评测 run 支持取消、并发限制和队列。

### 回滚

- 可关闭 Agent 级 `gate_enabled`，恢复旧发布流程。
- 可停用评测 tab，不影响 Agent 正常聊天、Open API 和运行 trace。
- 新增表为旁路资产，回滚时不需要删除既有 Agent/Skill/KB 数据。
- 若 evaluation mode 出现运行时问题，可临时禁用评测运行 API，只保留用例管理。

## 实现进展

- 2026-05-12: 已完成设计规格，明确发布前评测、回归用例、断言、LLM judge、发布门禁、trace 回流、数据模型和任务拆分。
- 尚未实现数据库迁移、后端 API、运行时 evaluation mode、前端页面和售后模板种子数据。

## 交接说明

- 下一步优先做 `TASK-085 Evaluation data model and APIs`，从数据模型和管理 API 开始。
- 实现时先保证“创建 suite/case/run/result”闭环，再接入真实 ChatOrchestratorService。
- 不要先做复杂 UI 或图表；首版价值在发布门禁和失败原因可解释。
- 售后 Agent 模板应等基础运行能力可用后再落种子数据，避免模板无法实际运行验证。
