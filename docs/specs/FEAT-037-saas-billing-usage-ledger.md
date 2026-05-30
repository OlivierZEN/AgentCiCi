---
kind: feature-spec
feature_id: FEAT-037
title: SaaS Billing Usage Ledger
status: implemented
owner_role: project-manager
task_ids: TASK-114
related_decisions: FEAT-003, FEAT-022
related_issues: none
updated_at: 2026-05-30T11:32:30Z
updated_by: MANAGER-001
---

# FEAT-037 SaaS Billing Usage Ledger

## 2026-05-28 Design Reassessment

本次重评估结论：计费方向保持不变，但首版交付必须从“大而全账单中心”收敛为“可信计量事实层优先”。FEAT-003 的组织级企业混合计费仍是商业模型事实源，FEAT-022 的 `智能体工作量 credits` 仍是客户可理解的用量口径，FEAT-037 负责把二者落成可审计、可追溯、可逐步扩展的工程底座。

2026-05-28 补充：私有化和本地部署版本的主收费口径调整为 `私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费`。客户自有本地模型的 token、GPU 和推理成本由客户承担，AgentCiCi 不对本地模型 token 做二次强收费。`Work Credits` 在私有化首版中优先服务用量看板、成本归因、预算控制、合同额度和平台代付资源治理。

实现开关：新增部署级 billing mode fact，默认 `private_deployment`，可通过 Spring 配置 `app.billing.deployment-mode=saas` 切换为 SaaS。后端通过 `BillingModeProperties` 归一化配置并由 `/billing/mode` 暴露只读视图；前端通过 `billingMode.ts` 使用同样的归一化规则。后续 rating、quota、billing UI 和 plan seed 必须读取这个事实源。

需要立即修正的设计点：

- 首版优先级从套餐/页面前置改为 `usage_meter_event`、真实模型 usage、rating 规则、credit ledger 最小闭环前置。
- 页面首版只做只读解释和审计，不承诺支付、发票、合同、自动续费、税务、销售折扣或完整套餐运营。
- 运行时埋点按风险拆分，先接入聊天和模型 usage，再接入 RAG、工具、Open API、Workflow、KB 索引；不要求一次提交打穿所有高冲突链路。
- rating 必须区分 `customer_paid`、`platform_paid`、`included`、`non_billable`。本地模型和客户自有连接器默认属于 `customer_paid`，只做低倍率平台调度计量、归因或预算治理；平台代付模型、第三方搜索、云端语音、托管连接器才进入真实扣费。
- 当前仓库和 `origin/main` 的迁移最高版本均为 `V59`，原任务里的 `V53__billing_usage_ledger.sql` 已不适合新增。实现前应将 assignment 从 `V53__billing_usage_ledger.sql` 刷新为下一个可用迁移号，当前建议为 `V60__billing_usage_ledger.sql`，如 rebase 后主线新增迁移则继续顺延。
- TASK-114 已从 `DEV-nezha` 改为 `MANAGER-001`，本规格以 `.claw/tasks/TASK-114.md` 和 `.claw/assignments/TASK-114.yaml` 的当前授权为准。

首版成功标准不是“能收费”，而是“未来收费不会乱”：同一业务事实只进一个计量事件，事件可幂等去重，rating 可版本化复算，ledger 只追加不改写，页面能解释 credits 从哪里来。

## 2026-05-29 Organization Admin Billing Confirmation

组织管理员必须能在 `/admin/billing` 查看本组织的当前版本、订阅状态、席位和容量权益、包含 credits、剩余 credits、消耗比例、quota 状态、按域消耗分布、最近 usage meter events 和 credits ledger 明细。

可见性边界：

- 组织管理员只能查看当前组织，不支持跨组织查询。
- 普通成员可在后续运行详情中查看单次运行 credits 解释，但不默认开放组织级余额、ledger、top-up 或合同额度。
- 平台运营继续通过 `/platform/billing` 管理版本、套餐、容量包、服务包、SLA、credits 策略，并保留跨组织查看和人工调整能力。

首版实现可以先用 deterministic seed 补足默认订阅、usage events 和 ledger，保证组织管理员侧形成完整可用链路；后续 runtime metering 接入后，这些读视图保持不变，事实来源从 seed 过渡为真实事件。

## 2026-05-30 Runtime Billing Completion

本次完成真实运行时计费闭环，组织管理员账单不再依赖演示用量 seed。`ChatOrchestratorService` 在同步和流式聊天路径完成运行后调用 `BillingUsageMeteringService`，把同一次用户聊天拆成可审计的 usage meter events，并按 SaaS 计费策略写入 append-only credits ledger。计费写入失败不得打断用户对话；错误只影响计费事件补偿，不影响聊天响应。

运行时首批扣费项目：

| 计费域 | item code | 官网报价条目 | 首版费率 | 说明 |
| --- | --- | --- | --- | --- |
| `assistant_chat` | `conversation_credit` | `Credits 包` | 1.00 credit / turn | 每个用户有效对话轮次基础扣减 |
| `model_usage` | `model_token_credit` | `Credits 包` | input 0.10 / 1k tokens，output 0.30 / 1k tokens | 基于 model trace token usage；无真实 token 时不产生正向扣减 |
| `rag_retrieval` | `retrieval_credit` | `Credits 包` | 0.20 / chunk | 按返回上下文 chunk 数扣减 |
| `tool_call` | `tool_call_credit` | `Credits 包` | 0.50 / call | 按已调度工具调用数扣减 |
| `workflow_run` | `workflow_credit` | `Credits 包` | 0.20 / run | 对有耗时的运行治理链路计入基础运行成本 |

幂等规则使用 `orgId:sessionId:domain` 作为首版 source id。这样同一组织内同一会话重复提交不会重复扣费，不同组织即使 session id 相同也不会互相抵消。账本余额以 ledger id 顺序取最后余额，避免同秒多事件按时间排序导致余额反读。

SaaS 默认版本和官网 Pricing 事实源对齐：

- `saas_team` / 团队版：50,000 Credits，本期默认操作席位 1，构建席位 0；权益上限为操作席位 20、构建席位 1、Agent 1、文档 5,000 页、知识库 10GB、Open API QPS 20、并发 2、Trace 7 天。
- `saas_business` / 商业版：250,000 Credits；权益上限为操作席位 100、构建席位 3、Agent 3、文档 50,000 页、知识库 100GB、并发 10、Trace 30 天。
- `saas_enterprise` / 企业版：1,000,000 Credits；权益上限为操作席位 300、构建席位 8、Agent 10、文档 300,000 页、知识库 1TB、并发 50、Trace 90 天。
- SaaS 加购包补齐官网报价条目：`SaaS Credits 加购包`、`知识库容量包`、`文档处理包`、`并发与构建扩展`、`上线服务包`。

读视图要求：

- `/admin/billing/overview`、`/usage-events`、`/ledger` 必须展示真实 ledger 推导的 consumed / remaining credits；`customer_paid` 和 `non_billable` 事件可以进入用量事实，但不得消耗 credits。
- 用量明细必须同时显示内部 `itemCode`、中文说明、`billingType` 和 `officialPricingItem`，确保客户能把系统扣减项映射回官网 Pricing 条目。
- 平台配置错误、空响应、模型失败等无有效业务结果的聊天必须记录为 `non_billable` 用量事实，不得扣减 conversation/workflow credits；有真实成功回答或测试构造的 billable trace 才能写入 `usage_debit`。
- 本地开发代理必须只转发 `/admin/billing/overview|subscription|usage-events|ledger|quota` API 子路径，不能转发 `/admin/billing` 页面路由，否则 React 页面会被后端 404 抢走。
- 新 SaaS 组织默认 quota 状态必须全部正常，不得沿用演示席位用量造成刚创建即超限。

## Goal

Implement the first production-shaped SaaS billing foundation for AgentCiCi:

- package plans and organization subscriptions
- immutable usage meter events
- work-credit rating
- credit ledger entries
- quota pre-check hooks
- admin and platform billing surfaces

This feature turns FEAT-003 and FEAT-022 from product direction into an executable implementation plan. The first release is not a payment processor, invoice system, contract system, renewal workflow, or sales discount console. It is the trustworthy billing facts layer that future payment, renewal, contract, top-up, and finance workflows can rely on.

## Relationship To Earlier Billing Specs

FEAT-037 does not replace FEAT-003 or FEAT-022.

- `FEAT-003 SaaS Billing And Packaging` remains the upstream commercial model and packaging source of truth. It defines organization-level billing, platform subscription, seats, resource usage, add-ons, overage posture, and SaaS packaging.
- `FEAT-022 Agent Workload Billing Model` refines the usage layer from FEAT-003 into the customer-facing `work credits` / `智能体工作量` model.
- `FEAT-037 SaaS Billing Usage Ledger` is the first engineering delivery spec for those decisions. It defines schema, services, APIs, UI surfaces, quota hooks, and task execution boundaries.

If FEAT-037 appears to conflict with FEAT-003 on commercial packaging, FEAT-003 wins and FEAT-037 should be corrected. If FEAT-037 appears to conflict with FEAT-022 on work-credit naming or customer-facing usage language, FEAT-022 wins and FEAT-037 should be corrected.

## Product Position

Use this external commercial model:

```text
cloud SaaS: platform subscription + paid operation/build seats + work credits + enterprise add-ons
private deployment: annual license + operation/build seats + module/capacity packs + implementation/support services
```

Do not make token usage the first-level customer-facing billing concept. Tokens, embeddings, vector storage, and third-party API cost stay as internal cost and expanded detail. Customer-facing product copy should use `智能体工作量`, `工作量额度`, and `credits`.

For private deployment with customer-owned local models, do not charge again for local model tokens. Keep model usage as an internal observability, attribution, capacity, and future contract-governance fact. Strong usage billing applies only to `platform_paid` resources or contract-defined overage.

## Billing Subject

The billing subject is always the organization.

- A personal user is never the direct billing subject.
- API keys, external users, channels, and credentials are attribution dimensions under an organization.
- Cost attribution must support organization, user, agent, API key, channel, external user, and billable domain.

## Architecture

### 1. Usage Events

`usage_meter_event` is the immutable fact table. It answers: what happened in the system?

Events are append-only. Corrections must be written as new rating or ledger adjustments, not by rewriting the source event.

Required fields:

- `id`
- `org_id`
- `user_id`
- `agent_id`
- `session_id`
- `credential_id`
- `external_user_id`
- `billable_domain`
- `billable_item_code`
- `quantity`
- `unit`
- `work_credit_quantity`
- `model_provider`
- `model_name`
- `model_tier`
- `is_platform_paid`
- `billing_type`
- `source_type`
- `source_id`
- `idempotency_key`
- `request_id`
- `trace_id`
- `status`
- `occurred_at`
- `rated_at`
- `plan_code`
- `rate_card_version`
- `metadata_json`

Use `idempotency_key` to deduplicate retries and replays. Recommended keys:

- chat turn: `chat:{sessionId}:{messageId}`
- model call: `model:{traceId}:{phase}:{attempt}`
- RAG retrieval: `rag:{traceId}:{retrievalId}`
- tool call: `tool:{traceId}:{toolCallId}`
- Open API request: `openapi:{credentialId}:{idempotencyKey|requestId}`
- workflow execution: `workflow:{executionId}`

首版事件状态使用 FEAT-022 的状态语义：

- `reserved`
- `succeeded`
- `failed_billable`
- `failed_non_billable`
- `refunded`
- `adjusted`

`usage_meter_event` 不直接等同于账本。它记录发生过的事实和可计量数量；credit 扣减、赠送、冲正和到期必须进入 `billing_credit_ledger`。

### 2. Rating

Rating converts source events into work credits. It answers: how much billable work did this event represent?

Rating is deterministic for a given `rate_card_version`. If pricing changes, keep the old version for historical events.

First-phase domains:

| Domain | Source | First release behavior |
| --- | --- | --- |
| `assistant_chat` | `ChatOrchestratorService` / chat trace | accepted user turn base credits |
| `model_usage` | model client usage fields | token envelope and model tier credits |
| `rag_retrieval` | `RagService.retrieveContext()` | retrieval count credits |
| `tool_call` | `ToolOrchestratorService` | read/write/platform-paid tool credits |
| `workflow_run` | `AgentWorkflowExecutionLogEntity` | execution-level credits only |
| `open_api_chat` | `agent_api_call_log` | accepted Open API request credits |
| `kb_indexing` | KB indexing lifecycle | document/chunk/MB credits |

Do not enable workflow node-level billing until the workflow runtime has real node execution facts. Current execution logs are enough for workflow-run level billing only.

接入顺序按事实成熟度推进：

1. `assistant_chat` 和 `model_usage`：当前 `AliyunBailianClient` 已能返回 prompt/completion token，`ChatOrchestratorService` 已记录 model call trace，适合作为第一批。
2. `rag_retrieval`：`RagService.retrieveDetailed()` 已有检索数量、KB、fallback 和 timings，可作为第二批。
3. `tool_call`：`ToolOrchestratorService` 已统一调度内置、Skill API、MCP 工具，但还缺工具级 `billing_type`，接入前先补 included/platform_paid/customer_paid/non_billable 分类。
4. `open_api_chat`：`agent_api_call_log` 和 `agent_api_usage_daily` 已可提供 request、credential、quota 原型，但 Open API 相关文件仍需避开其他活跃任务冲突。
5. `workflow_run` 和 `kb_indexing`：先记录 execution/job 级事件，暂不做 workflow node 级计费。

### 3. Ledger

`billing_credit_ledger` is the financial work-credit book. It answers: what happened to the customer's credit balance?

Ledger entries are append-only. Examples:

- `included_grant`: monthly or annual included credits
- `top_up_grant`: purchased top-up package
- `usage_debit`: rated usage consumes credits
- `adjustment_credit`: support or finance credit
- `adjustment_debit`: manual correction
- `reversal_credit`: refund for a prior usage debit
- `expiration_debit`: unused credits expire

Every usage debit should reference the rated usage event or rating batch. Refunds and corrections should reference the original debit where possible.

### 4. Plans And Subscriptions

Plans define capability and allowance. Subscriptions bind a plan to an organization.

First-phase plan codes:

| Deployment mode | Plan code | Display name | First release behavior |
| --- | --- | --- | --- |
| `saas` | `trial` | 试用 | low credits, restricted Open API, one real pilot |
| `saas` | `saas_team` | 团队版 | small team usage, basic knowledge, common tools, limited credits |
| `saas` | `saas_business` | 商业版 | production usage, Open API, billing views, trace and attribution |
| `saas` | `saas_enterprise` | 企业版 | annual contract, SSO/SLA, custom credits, enterprise add-ons |
| `private_deployment` | `private_department` | 部门版 | annual license, one or few orgs, basic Agent/KB/connectors |
| `private_deployment` | `private_enterprise` | 企业版 | annual license, multi-org, Open API, observability, capacity packs |
| `private_deployment` | `private_group` | 集团版 | multi-instance, prod/test/DR environments, SSO/SLA, advanced audit |

Capacity and service packages are separate from plan code: Agent count, Skill count, KB capacity, Open API QPS/concurrency, connector count, meeting minutes concurrency, trace retention, audit retention, environment count, implementation service, and annual maintenance.

Subscription states:

- `trialing`
- `active`
- `past_due`
- `paused`
- `canceled`

Plan configuration must include:

- included credits
- billing period
- rollover policy
- top-up policy
- overage mode per domain
- seat package limits
- feature flags
- audit and data retention limits
- Open API production access flag
- plan code and deployment mode compatibility
- capacity package entitlements
- service package and SLA tier references

首版只需要最小可运行套餐和订阅数据，支持读视图、默认组织订阅和 included credits grant。完整套餐编辑、折扣、合同价、税务、自动续费和外部支付留给后续销售运营阶段。

私有化首版的 plan/subscription 数据应支持年费授权、操作席位、构建席位、模块包、容量包、环境/实例数和服务等级展示；不要把本地模型 token 包作为默认套餐项。

### 5. Quota Enforcement

Use three enforcement layers:

1. Request pre-check: subscription active, feature allowed, hard limit not exceeded.
2. Reservation for high-cost actions: reserve estimated credits, settle actual usage after execution.
3. Async rating and ledger write: source events are rated and debited in the background.

Overage modes:

| Mode | Behavior | Default use |
| --- | --- | --- |
| `auto_charge` | continue and bill overage | enterprise Open API and contract accounts |
| `soft_limit` | warn but allow | assistant chat and normal RAG |
| `hard_limit` | block new high-cost work | premium models, third-party paid tools, bulk indexing |

Platform errors that produce no useful business result should be non-billable or reversed. User cancellation after completed model/tool work can bill completed portions.

## Backend API Shape

### Organization Admin APIs

- `GET /admin/billing/overview`
- `GET /admin/billing/usage-events`
- `GET /admin/billing/ledger`
- `GET /admin/billing/subscription`
- `GET /admin/billing/quota`

These APIs show the current organization only.

### Platform APIs

- `GET /platform/billing/plans`
- `POST /platform/billing/plans`
- `GET /platform/billing/subscriptions`
- `PUT /platform/billing/subscriptions/{orgId}`
- `GET /platform/billing/usage-events`
- `GET /platform/billing/ledger`
- `POST /platform/billing/ledger-adjustments`

Platform APIs require platform authorization and must support organization filtering.

## Frontend Surfaces

### `/admin/billing`

Organization admins need a dense account-level view:

- current plan and subscription status
- remaining credits and period dates
- usage by domain
- recent ledger entries
- recent usage events
- quota warning states

Follow `鎏金账房`: compact tables, warm ivory surfaces, gold linework for active state, no marketing metrics, no oversized cards, no nested background boxes inside panels.

### `/platform/billing`

Platform operators need:

- plan list and feature flags
- organization subscription list
- organization usage lookup
- ledger adjustment workflow
- rating version and quota policy visibility

Any adjustment must require an explicit reason. Do not silently rewrite historical entries.

## Implementation Phases

### Phase 0: Rescope And Migration Preflight

- Update TASK-114 assignment before implementation so the migration file uses the next valid version after the current mainline, currently `V60__billing_usage_ledger.sql`.
- Keep FEAT-003 and FEAT-022 as upstream commercial and Work Credits sources.
- Confirm active task conflicts before touching Open API runtime files.
- Establish deployment-level billing mode switching before schema-heavy work: `private_deployment` and `saas` must resolve to explicit revenue model, token policy, credits role, primary charge items, and supported billing types.

### Phase 1: Metering Foundation

- Add billing schema migration for `usage_meter_event`, rating configuration, `billing_credit_ledger`, minimal plan, and subscription tables.
- Add billing domain entities, repositories, DTOs, and services with append-only behavior.
- Capture real model usage from `AliyunBailianClient` and dynamic provider paths.
- Emit first batch events from chat/model paths with stable `idempotency_key`.
- Persist `billing_type` so local/customer-paid resources can be separated from platform-paid resources before rating.
- Thread deployment mode into billing seed, rating policy, quota copy, and UI payloads through `BillingModeProperties`, not route-specific conditionals.
- Add deterministic seed behavior for default plans, default subscription, and included credits grant.

### Phase 2: Rating And Ledger

- Convert meter events into work credits through versioned rate cards.
- Write `usage_debit` entries to `billing_credit_ledger`.
- For private deployment, default customer-paid local model usage to governance/attribution entries rather than billable ledger debit unless the contract enables overage billing.
- Add non-billable, reversal, and manual adjustment paths without rewriting original events.
- Add focused tests for idempotency, rating version stability, append-only ledger behavior, and deterministic grants.

### Phase 3: Read APIs And Product UI

- Add organization admin read APIs for overview, usage events, ledger, subscription, and quota summary.
- Add platform read APIs for plans, subscriptions, usage events, ledger, and rating policy visibility.
- Add `/admin/billing` and `/platform/billing` routes with real API loading, empty, and error states.
- Follow `鎏金账房`: dense tables, warm ivory surfaces, restrained gold linework, no marketing metric hero, no nested cards.

### Phase 4: Additional Runtime Metering

- Emit RAG, tool, Open API, workflow-run, and KB-indexing events in small slices.
- Add tool `billing_type` before charging tool calls.
- Keep `agent_api_usage_daily` as a quota prototype only; do not treat it as final billing fact.
- Add model/tool/connector `billing_type` management before charging any tool, connector, or local model usage.

### Phase 5: Quota Controls

- Add request-level pre-check for high-cost domains.
- Add reservation and reconcile only after basic event and ledger correctness is verified.
- Add soft-limit warnings to admin billing overview.
- Add hard-limit enforcement for premium models, paid third-party tools, bulk indexing, and production Open API where configured.

## Delivery Task

### TASK-114 FEAT-037 SaaS billing usage ledger

Owner: `MANAGER-001`

Scope:

- billing package domain model
- next valid migration after mainline, currently `V60__billing_usage_ledger.sql` after assignment refresh
- plan, subscription, usage event, rate card, quota, and credit ledger services
- admin and platform billing APIs
- `/admin/billing` and `/platform/billing` product UI
- runtime metering hooks for chat/model first, then RAG, tool, workflow, KB indexing, and Open API where they do not collide with active tasks
- focused backend tests, frontend build, and desktop screenshot QA

Notes:

- The current assignment still names `V53__billing_usage_ledger.sql`; refresh assignment scope before implementation edits that create the migration.
- Open API runtime metering should be integrated after active Open API work lands or in a small follow-up if direct integration would cause branch conflict.
- The implementation may still be delivered in internal commits or phases, but FEAT-037 accountability is assigned to `MANAGER-001`.

### TASK-143 Billing editions configurable in platform operations

Owner: `unassigned`

Scope:

- platform-configurable edition definitions for `saas_team`, `saas_business`, `saas_enterprise`, `private_department`, `private_enterprise`, and `private_group`
- configurable capacity packs, module packs, service packs, SLA tiers, and credits policies
- platform APIs and UI for editing billing edition indicators with audit reason capture
- organization-admin read APIs and `/admin/billing` view for current edition, credits balance, usage events, ledger, and quota status
- read models that later feed rating, quota enforcement, and tenant subscription views

Notes:

- This task should not hard-code edition limits in frontend copy or backend constants.
- Capacity and service add-ons remain separate from edition names.
- Private deployment mode must not default to charging customer-owned local model token usage.
- Organization-admin billing data is current-organization scoped only; cross-organization inspection remains a platform operation.

## Acceptance Criteria

- Billing facts are append-only.
- Usage facts and credit ledger are separate tables and services.
- Organization admins can view subscription, remaining credits, usage, and ledger for their own organization.
- Platform operators can inspect plans, subscriptions, usage events, and ledger entries across organizations.
- Demo and test data are deterministic.
- Quota checks are designed before enforcement is enabled.
- The first implementation proves chat/model metering, rating, and ledger idempotency before expanding all runtime domains.
- Private deployment does not double-charge customer-owned local model token usage; billing behavior is driven by `billing_type`.
- Code exposes a single deployment billing mode switch and both backend/frontend tests cover private-deployment default plus SaaS alias normalization.
- Product UI follows `DESIGN.md` product rules and has desktop screenshot verification before shipping.
- No private keys, API keys, bearer tokens, or reusable secrets are written to docs, logs, tests, or task status files.

## Verification Plan

- Backend focused tests for billing repositories/services/controllers.
- Backend compile after migrations and entity wiring.
- Frontend build.
- Browser QA for `/admin/billing` and `/platform/billing` at desktop size only; mobile compatibility implementation and mobile tests are out of scope unless separately requested.
- `.claw` state validation after task and assignment updates.
