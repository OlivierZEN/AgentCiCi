---
updated_at: 2026-05-28T09:35:00Z
status: draft
feature_id: FEAT-022
related_specs:
  - docs/specs/FEAT-003-saas-billing-and-packaging.md
  - docs/specs/FEAT-021-agent-open-api.md
---

# FEAT-022 Agent Workload Billing Model

## Goal

- 将 AgentCiCi 的 SaaS 计费从“席位 + token + 零散资源”升级为更容易销售和解释的 `智能体工作量` 模型。
- 让客户为可理解的企业 AI 工作成果付费，例如一次智能体运行、一次知识检索、一次工具动作、一次 Open API 调用，而不是直接面对底层 token 和基础设施细账。
- 保留 FEAT-003 的企业混合计费框架，但把核心按量单位收束为统一 `Work Credits`，用于套餐额度、超额、预算控制和用量看板。

## Background

FEAT-003 已确定当前产品不适合纯聊天次数计费，应采用组织级企业混合模型：平台订阅、席位、资源用量和增值模块。竞品 Swan 的定价策略进一步说明了一件事：AI agent 产品更适合按“完成了多少业务工作”收费，而不是按“谁能访问软件”或“消耗了多少 token”收费。

对 AgentCiCi 来说，这个方向更贴合当前能力：

- 已有多组织、助手工作台、管理后台和平台治理控制面。
- 已有 Agent Builder、Skill、Workflow、知识库、工具/MCP、Open API 和运行 trace。
- 已有 Open API call log、daily quota、agent run trace 等早期计量事实。
- 还缺统一 `usage_meter_event`、真实 token usage、quota enforcement 和套餐账单域。

## Design Position

AgentCiCi 的商业包装应采用：

```text
平台订阅 + 付费操作席位 + 智能体工作量 credits + 企业增值模块
```

其中：

- `平台订阅` 覆盖组织空间、权限、基础治理、审计、配置中心和基础运行环境。
- `付费操作席位` 覆盖主动创建、指挥、审核、配置和发布 AI 工作的人。
- `智能体工作量 credits` 覆盖真正产生 AI 推理、检索、工具、工作流、Open API、索引和自动化成本的动作。
- `企业增值模块` 覆盖 SSO、SLA、专属实例、高级审计、成本归因、私有化和专属支持。

系统必须同时支持两种 deployment billing mode：

- `private_deployment`：私有化年费许可、操作/构建席位、模块/容量包、实施运维服务费为主；credits 做治理和平台代付资源口径。
- `saas`：平台订阅、操作/构建席位、Work Credits、企业增值模块为主；credits 可进入套餐额度和超额扣费。

这个模式由部署配置决定，不由租户普通管理员在页面上切换。

### Private Deployment Position

私有化和本地部署场景不应把 `Work Credits` 作为第一阶段主收费项。更稳的商业口径是：

```text
私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费
```

原因：客户使用本地模型和自有 GPU 时，模型算力、推理成本和 token 成本都由客户承担。AgentCiCi 不应对本地模型 token 二次收费。此时 credits 的定位应是：

- 用量看板和运行解释。
- 成本归因、部门分摊和预算控制。
- 大客户合同额度、容量治理和超额预警。
- 后续云托管版本、平台代付模型或第三方代付服务的统一计费口径。

首版必须区分资源付款责任：

- `customer_paid`：客户自有模型、本地工具、本地连接器和客户自付第三方服务。通常只做低倍率平台调度计量、归因、预算和容量治理，不作为额外强收费。
- `platform_paid`：平台代付模型、云端语音、第三方搜索、托管连接器和平台统一资源池。可以按 credits、容量包或实际用量收费。
- `included`：授权或模块包内已包含的基础能力。
- `non_billable`：只读查看、通知接收、平台错误、无业务结果的失败、内部运维动作。

因此，`usage_meter_event` 和真实 token usage 仍然要做，但它们在私有化场景首先服务治理、审计和未来合同扩展，而不是立即把本地模型调用包装成强按量收费。

## Naming

推荐外部销售口径使用：

- 中文：`智能体工作量` / `工作量额度`
- 单位：`credits` / `工作量点数`
- 内部代码：`work_credit`
- 内部基础计量事件：`usage_meter_event`

不要把销售页、账单总览或套餐页的一级口径写成 token。token 是内部成本核算字段，不是客户最先理解的价值单位。

## Billing Subject

主计费主体为 `组织/租户`。

- 个人用户不直接作为账单主体。
- API Key、外部系统和飞书等渠道不作为独立客户账单主体，它们归属到组织、Agent、credential、run-as user 或 external user。
- 平台方可按组织、部门、Agent、用户、API Key 和渠道做成本归因。

## Seat Model

### Paid Seats

`操作席位`：主动和 AgentCiCi 交互并驱动工作的人。

典型行为：

- 在助手工作台发起对话、选择技能、执行指令。
- 审核、确认、驳回或发布智能体动作。
- 在 Agent Builder、Skill、Workflow、知识库、工具、模型页面进行配置。
- 管理 API Key、发布渠道或运行策略。

`构建席位`：具备配置和发布能力的高级操作席位。

典型行为：

- 创建或编辑 Agent、Skill、Workflow。
- 维护知识库、模型、工具、MCP Server。
- 发布版本、调试运行、查看治理与运行日志。

### Free Or Low-cost Viewers

以下用户默认不占付费操作席位：

- 只接收飞书、邮件、Slack 或 Webhook 通知的人。
- 只看 dashboard、运行结果、审计摘要的人。
- 只作为 Open API 的 external user 元数据出现的人。
- 被智能体引用为业务对象、审批对象或通知对象但不主动操作系统的人。

这个规则能降低企业内部扩散阻力：让结果免费流动，让主动指挥、审核和构建产生席位价值。

### Seat Classification

首版不建议只按角色编码判断席位类型。应按 `用户启用状态 + 月内实际行为 + 显式席位分配` 共同决定：

- `ORG_USER` 调用聊天、技能或个人工作流后，可计为操作席位。
- `ORG_ADMIN` 如果只接收通知或只看 dashboard，不一定计为构建席位。
- 使用 `/admin/*` 构建类 API、发布类 API 或平台治理类 API 后，可计为构建席位。
- 席位统计按自然月峰值或合同约定的最低购买数出账。

## Work Credits

### Core Rule

一个 `work_credit` 表示一次标准强度的智能体工作量。它不是固定 token 数，也不是固定请求数，而是由底层计量事件折算出来的客户可理解单位。

在私有化本地模型场景，`work_credit` 默认是治理和归因单位，不是对客户自有 token 的直接收费单位。只有 `platform_paid` 资源或合同明确约定的超大规模额度，才进入强账单扣减。

内部计算采用：

```text
work_credits =
  base_action_credits
  + model_tier_multiplier * token_cost_credits
  + retrieval_credits
  + tool_call_credits
  + workflow_credits
  + external_service_credits
  + storage_or_indexing_credits
```

对客户展示时应尽量简化为：

```text
本次运行消耗 6.4 credits
包含：对话生成、知识检索、2 次工具调用、1 次业务写入
```

底层 token、embedding、Qdrant、第三方 API 费用只进入明细展开和平台成本报表。本地模型 token、客户自有 embedding 服务和客户自有向量库成本进入客户侧成本归因，不进入 AgentCiCi 的默认收费明细。

### Billable Domains

| Domain | Customer-facing item | Example unit | First-phase source |
| --- | --- | --- | --- |
| `assistant_chat` | 助手对话 | per accepted turn | `ChatOrchestratorService` / `agent_run_trace` |
| `model_usage` | 模型推理 | token envelope / model tier | 模型客户端 usage，当前需补齐 |
| `agent_run` | 智能体运行 | per run | `agent_run_trace` |
| `rag_retrieval` | 知识检索 | per retrieval | `RagService.retrieveContext()` |
| `kb_indexing` | 知识库索引 | per document / chunk / MB | `KbIndexWorker` / KB repositories |
| `tool_call` | 工具调用 | per call | `ToolOrchestratorService` / trace tool nodes |
| `workflow_run` | 工作流执行 | per execution | `AgentWorkflowExecutionLogEntity` |
| `workflow_node` | 工作流节点 | per real node | 后续真实执行引擎 |
| `open_api_chat` | Open API 对话 | per accepted API call | `agent_api_call_log` |
| `scheduled_run` | 定时自动运行 | per trigger/run | workflow scheduler / execution log |
| `external_connector` | 第三方连接器动作 | per platform-paid call | tool / integration billing type |

## Credit Rate Card

以下是产品设计口径，不是最终财务价格表。正式价格应由成本、毛利目标和销售策略校准。私有化本地模型场景下，以下 credits 建议默认作为治理折算表，而不是立即出账价格表；平台代付或云托管场景可复用同一折算表进入真实扣费。

| Action | Suggested credits | Notes |
| --- | ---: | --- |
| 普通助手消息，未调用工具/知识库 | 1 | 含标准模型和小 token envelope |
| 启用知识库检索的助手消息 | 2 | 检索、rerank 或上下文注入另行可展开 |
| 高级模型或深度推理消息 | 3-8 | 按模型档位乘数 |
| 单次内置工具只读调用 | 1 | 如查询对象、读取字段、查日程 |
| 单次业务写入或外部副作用工具调用 | 2-5 | 如写 CRM、发邮件、更新工单 |
| 单次平台代付第三方搜索/富化 | 3-10 | 如 Tavily、企业数据增强服务 |
| 一次标准智能体运行 | 3-10 | 按包含的模型、RAG、工具明细折算 |
| 一次 Open API non-stream chat | 2 + 明细 | 覆盖 API 网关、鉴权、session map、trace |
| 一次 Open API stream chat | 3 + 明细 | 流式连接和运行记录成本更高 |
| 文档上传索引 | 按 MB / chunk | 与存储额度分开 |
| 定时后台任务 | 运行明细 + 调度费 | 避免低频定时器被误解为免费 |

## Plan Packaging

版本线按 deployment mode 拆分：

- SaaS：`团队版`、`商业版`、`企业版`，内部 code 为 `saas_team`、`saas_business`、`saas_enterprise`。
- 私有化：`部门版`、`企业版`、`集团版`，内部 code 为 `private_department`、`private_enterprise`、`private_group`。
- `trial` 是试用状态或试用 plan，不作为正式收费版本。
- 容量包和服务包独立叠加，不写死在版本名里。

### Trial

- 目标：让客户完成一个真实智能体试点。
- 包含：
  - 1 个组织
  - 少量操作席位
  - 少量构建席位
  - 固定 work credits
  - 基础知识库、基础 Agent、基础工具
- 限制：
  - 不开放生产 Open API 或只开放低 quota sandbox key
  - 不提供 SSO、SLA、专属支持

### Team

- 目标：一个团队或部门开始使用 AgentCiCi 做知识问答和轻量业务流程；对应 SaaS `团队版`。
- 包含：
  - 操作席位包
  - 构建席位包
  - 中等 work credits
  - 多 Agent、知识库、常用工具和飞书渠道
  - 基础运行日志和用量看板
- 超额：
  - 自动购买 credits 或 soft limit 告警

### Business

- 目标：部门级到公司级的 AI 工作流落地；对应 SaaS `商业版`。
- 包含：
  - 更高 work credits
  - Open API 正式额度
  - 更多 Agent / Skill / Workflow 发布数
  - 更多 MCP / 第三方连接器
  - 成本归因、运行 trace、审计导出
  - 更高并发和更长数据保留期

### Enterprise

- 目标：组织级治理、大客户年度合同和高等级 SLA；对应 SaaS `企业版` 或私有化 `企业版/集团版`。
- 包含：
  - 定制 credits 包和超额单价
  - SSO、SLA、专属实例或私有化部署
  - 高级审计、部门成本中心、数据保留策略
  - 平台级治理、专属 onboarding、定制连接器
  - 合同级最低消费和年度额度滚动规则

## Credit Lifecycle

### Included Credits

- 每个套餐按月或按年包含固定 credits。
- 年付客户可获得年度 credits 池，并允许在合同期内滚动使用。
- 月付客户 credits 默认月度清零，是否滚动由套餐决定。

### Top-up

- 用完套餐 credits 后可购买 top-up 包。
- top-up 单价可高于套餐内含单价，用于鼓励客户升级套餐。
- 企业合同可约定自动 top-up、人工审批或 hard limit。

### Overage Modes

| Mode | Behavior | Recommended use |
| --- | --- | --- |
| `auto_charge` | 超额继续运行并计入账单 | 成熟企业客户、Open API 生产调用 |
| `soft_limit` | 超额告警但不中断 | 普通助手对话、知识检索 |
| `hard_limit` | 超额阻断新高成本任务 | 高级模型、第三方代付、写操作工具 |

### Refund / Non-billable Rules

- 平台内部错误、超时且无业务结果的执行不计费或自动冲正。
- 用户取消前已完成的模型调用、工具调用可按实际完成部分计费。
- 重试必须使用幂等键去重；同一业务动作因系统重放不能重复扣费。
- 只打开页面、只查看日志、只接收通知不消耗 credits。

## Meter Event Design

FEAT-003 的 `usage_meter_event` 需要升级为可支撑 credits 的事实源。

### Required Fields

- `id`
- `org_id`
- `user_id`
- `agent_id`
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
- `session_id`
- `request_id`
- `trace_id`
- `idempotency_key`
- `status`
- `occurred_at`
- `metadata_json`

### Status

- `reserved`
- `succeeded`
- `failed_billable`
- `failed_non_billable`
- `refunded`
- `adjusted`

### Idempotency

每个计量事件必须有稳定 `idempotency_key`：

- 聊天 turn：`chat:{sessionId}:{messageId}:{phase}`
- 模型调用：`model:{traceId}:{modelCallIndex}`
- 工具调用：`tool:{traceId}:{toolCallId}`
- Open API：`openapi:{requestId}:{domain}`
- 知识库索引：`kb_index:{documentId}:{indexJobId}:{phase}`
- 工作流执行：`workflow:{executionId}:{nodeId?}`

## Source Mapping

### Current Sources

- `agent_run_trace`：适合作为智能体运行、RAG、模型、工具分段的观测源，但仍需补真实 token usage。
- `agent_api_call_log`：适合作为 Open API 请求计量源。
- `agent_api_usage_daily`：适合作为 credential 级 quota 原型，但不能替代统一账单事件。
- `AgentWorkflowExecutionLogEntity`：适合作为首版 workflow instance 计量源。
- `OpsController.cost`：只能作为成本展示原型，不能作为账单事实源。

### Required Gaps

- `AliyunBailianClient` 需要采集 `prompt_tokens`、`completion_tokens`、`total_tokens`。
- 流式模型调用需要从 final chunk 或 provider usage 字段提取 token usage。
- `RagService.retrieveContext()` 需要发射检索事件。
- `ToolOrchestratorService` 需要发射工具事件并带工具 billing type。
- `KbIndexWorker` 需要在索引成功、失败、重建时发射事件。
- MCP Server / Tool Definition / Model Provider 需要标记 `billing_type`：`included`、`platform_paid`、`customer_paid`、`non_billable`。

## Quota Enforcement

### Entry Checks

在高成本动作入口先做预算检查：

- `/ai/chat` 和 `/ai/chat/stream`
- `/openapi/v1/agents/{agentId}/chat`
- `/openapi/v1/agents/{agentId}/chat/stream`
- Agent / Workflow 手动运行
- 定时任务触发
- 知识库批量索引和重建
- 平台代付第三方工具调用

### Reserve And Reconcile

首版采用 `预估预留 + 完成后校准`：

1. 请求入口按动作类型预留最低 credits。
2. 运行中异步写明细 meter events。
3. 运行结束后按真实 token、工具、RAG、索引结果校准。
4. 多扣部分自动释放，少扣部分追加扣减。

### Cache

- 组织级余额和策略可短缓存，避免每个 token 或节点同步查库。
- hard limit 的关键入口必须能读到近实时余额。
- 缓存不可作为最终账单事实源，最终仍以 `usage_meter_event` 和账单汇总任务为准。

## Product UX Requirements

### Admin Billing Overview

组织管理员应能看到：

- 当前套餐、席位、包含 credits、剩余 credits。
- 本月消耗趋势。
- 按 Agent、用户、渠道、API Key、知识库、工具维度的消耗排行。
- 超额策略、告警阈值、top-up 记录。
- 最近高成本运行明细，能跳回 trace 或 Open API call log。

### Run-level Explanation

每次智能体运行应可解释：

- 消耗了多少 credits。
- 哪些部分贡献了消耗：模型、RAG、工具、Open API、索引、第三方服务。
- 哪些失败不计费，哪些失败按已完成部分计费。

### Customer Copy

推荐文案：

- “本地部署版按平台授权、主动席位、模块容量和实施运维服务计费，不对客户自有模型 token 二次收费。”
- “按智能体完成的工作量计费。”
- “通知和查看不占席位，主动指挥、审核和构建才计入席位。”
- “credits 可用于对话、知识检索、工具调用、工作流和 Open API 的用量解释、预算治理和平台代付资源计费。”
- “token 作为底层成本明细保留，不作为客户的一线使用门槛。”

## Implementation Phases

### Phase 0: Metering Foundation

- 新增 `usage_meter_event`。
- 模型客户端采集真实 token usage。
- 聊天、RAG、工具、Open API、Workflow、知识库索引发射计量事件。
- 补幂等键、status、source mapping 和基础归因字段。

### Phase 1: Credit Ledger

- 新增 price catalog，将 meter events 折算为 work credits。
- 新增组织 credits balance 和 monthly aggregation。
- 管理端展示 credits 用量总览。
- 保留 `OpsController.cost` 作为成本原型或迁移为账单聚合底层。

### Phase 2: Quota And Overage

- 实现 reserve / reconcile。
- 支持 `auto_charge`、`soft_limit`、`hard_limit`。
- Open API credential quota 从 daily call count 升级为 credits quota。
- 高级模型、第三方代付和批量索引接入 hard limit。

### Phase 3: Packaging And Sales Ops

- 新增套餐、订阅、席位包、credits 包和 top-up。
- 平台运营端管理计划、订阅、价格表和组织额度。
- 组织管理端展示账单、用量和告警。

### Phase 4: Invoice And Contract

- 对接发票、合同、支付、税务或外部财务系统。
- 支持年度 credits rollover、最低消费、阶梯单价和人工调整。

## Non-goals

- 本文档不定义最终人民币价格、折扣权限或合同条款。
- 本文档不直接实现支付网关、发票系统或税务能力。
- 本文档不把 token 从系统中删除；token 仍是内部成本和争议查账的重要底层字段。
- 本文档不要求所有现有功能立刻 hard limit，首版应先保证计量可信。

## Open Questions

- credits 是否允许跨年度滚动，还是只对企业年付客户开放。
- Open API 的 quota 应按 credential、Agent、组织分别配置到什么粒度。
- 免费 viewer 的范围是否允许包含只读导出和审计查看。
- 第三方平台代付工具的毛利目标和最低扣费单位需要商务确认。

## Acceptance

- 能清楚解释 AgentCiCi 为什么按智能体工作量收费，而不是只按 token 或用户数收费。
- 能把 FEAT-003 的混合计费框架落到统一 credits 模型。
- 能覆盖助手对话、RAG、工具、Agent run、Workflow、知识库、Open API、定时任务和第三方连接器。
- 能指导后续开发拆出计量事件、credits 账本、quota enforcement、账单中心和套餐运营。
- 能保留客户友好的外部口径，同时保留底层成本和争议查账所需的技术明细。
- 能清楚区分私有化本地模型、客户自付资源和平台代付资源，避免对客户自有 token 二次收费。

## Handoff Notes

- 若开始实现，不要先做套餐页面；先补 `usage_meter_event` 和真实 token usage。
- `agent_api_usage_daily` 只能作为 Open API quota 原型，后续应统一汇入 credits ledger。
- 首版 credits 价格表可以配置化，不应写死在业务代码。
- 页面展示必须把 credits 解释为“工作量”，避免让用户觉得系统在神秘扣点。
- 私有化首版报价应优先采用年费授权、主动操作/构建席位、模块/容量包、实施运维服务费；credits 先服务治理、看板、预算和平台代付资源。
