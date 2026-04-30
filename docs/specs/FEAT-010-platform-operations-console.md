---
updated_at: 2026-04-30T00:00:00Z
status: planned
feature_id: FEAT-010
owner_role: product-platform
---

# FEAT-010 Platform Operations Console

## Goal

- 为 `cc-cici-assistant` 增加平台运营管理后台，用于平台方管理多租户、计费、标准能力、底层策略、用量成本、灰度发布和审计支持。
- 第一阶段采用模块化单体架构：同一仓库、同一后端服务、同一数据库，但独立权限、独立路由、独立领域模块和独立表前缀。
- 按未来可拆分为独立平台控制面的模式设计，避免把平台运营能力耦合成租户管理端的杂项页面。

## Problem

当前系统已经具备企业 AI 平台雏形：多组织、助手端、租户管理端、Agent Builder、Skill、知识库、工具/MCP、集成应用、审计和轻量成本统计。

随着系统 SaaS 化，会出现平台方必须统一管理的问题：

- 多租户开通、停用、套餐、到期和资源限额。
- 组织级账单、用量、成本归因和超额治理。
- 平台核心策略与标准 Skill 的维护、发布、灰度和回滚。
- 租户问题排障、运行 trace、工具错误、模型成本异常。
- 平台级审计、安全操作、支持人员访问租户数据的边界。

如果把这些能力直接塞进现有租户管理后台，会混淆两类用户：

- 租户管理员只应管理自己的组织。
- 平台运营人员需要跨租户、跨套餐、跨能力版本管理整个 SaaS 平台。

## Architecture Decision

第一阶段采用：

```text
Modular Monolith + Shared Database + Isolated Platform Control Plane
```

也就是：

- 同一个代码仓库。
- 同一个 Spring Boot 后端服务。
- 同一个前端工程，可用独立路由和布局。
- 同一个数据库，但平台表与计费表使用独立前缀。
- 独立 RBAC，不复用 `ORG_ADMIN`。
- 计量事件化，为未来拆出 metering / billing 服务做准备。

暂不采用独立微服务，原因：

- 当前 Agent、Skill、计费、知识库、工具治理模型仍在快速演进。
- 跨服务接口过早固定会降低迭代速度。
- 鉴权、事务、部署、监控复杂度会提前放大。
- 当前更需要平台能力闭环，而不是先承担分布式治理成本。

## Target System Boundary

```text
cc-cici-assistant
├── Tenant Runtime Plane
│   ├── assistant workspace
│   ├── tenant admin console
│   ├── chat / agent runtime
│   ├── skill / tool / KB / integration
│   └── org-scoped audit
│
└── Platform Control Plane
    ├── /platform/**
    ├── platform RBAC
    ├── tenant lifecycle
    ├── billing and package management
    ├── usage and cost operations
    ├── platform skill / policy version management
    ├── rollout and impact analysis
    └── support and platform audit
```

## Core Principles

1. 平台后台不是租户后台的超级模式，而是独立控制面。
2. 租户事实源仍归业务域，平台后台通过受控服务读取和操作。
3. 跨租户访问必须记录平台审计。
4. 计费用量以统一后端事件为事实源，不从前端点击拼账单。
5. 第一阶段同库不同表，未来允许拆出 metering、billing 和 platform config 服务。
6. 平台配置更新必须支持灰度、回滚和运行时缓存兜底。
7. 敏感业务内容默认脱敏，平台支持人员只能按工单或授权查看必要信息。

## Next Phase Implementation Scope

本阶段平台运营后台只实现 **平台内置 Skill 与内置工具治理**，不展开完整计费、租户运营、用量成本和支持观测。

包含：

1. 平台后台基础壳：独立 `/platform/**` 路由、平台角色、菜单和鉴权守卫。
2. 平台审计基础：平台人员操作内置 Skill / Tool 时写审计。
3. 内置 Skill 管理：
   - 查看平台标准 Skill 列表、详情和版本。
   - 创建/编辑平台标准 Skill 模板版本。
   - 发布、停用、回滚、查看影响范围。
   - 对租户可见状态与默认启用策略做管理。
4. 内置工具治理：
   - 查看内置工具目录。
   - 管理工具展示名、描述、风险等级、启用状态、所属能力分类。
   - 查看哪些标准 Skill / Agent 依赖该工具。
   - 支持紧急禁用高风险内置工具。
5. 与 FEAT-009 打通：平台后台操作标准 Skill 时使用平台模板与版本模型，不直接修改租户自定义 Skill。

暂不包含：

- 套餐、订阅、账单、支付。
- 完整 `usage_meter_event` 计量面。
- 跨租户支持工单与敏感 trace 查看。
- 平台策略包可视化编辑以外的复杂灰度运营。

## Platform Roles

新增平台级角色，不与组织角色混用：

| Role | Scope | Capabilities |
|---|---|---|
| `PLATFORM_ADMIN` | full platform | 管租户、套餐、计费、平台能力版本、角色和高风险操作 |
| `PLATFORM_OPERATOR` | operations | 查看租户状态、用量、成本、灰度发布、运行监控 |
| `PLATFORM_SUPPORT` | support | 按授权查看租户配置摘要、运行错误和脱敏 trace |
| `PLATFORM_BILLING` | billing | 管套餐、订阅、账单、额度和超额策略 |
| `PLATFORM_AUDITOR` | audit | 只读查看平台审计、安全事件和计费记录 |

用户可以同时是平台用户和某个租户用户，但登录态和权限判断必须明确区分。

## Route And UI Structure

### Frontend Routes

```text
/platform/login
/platform
/platform/tenants
/platform/tenants/:orgId
/platform/plans
/platform/billing
/platform/usage
/platform/skills
/platform/policies
/platform/rollouts
/platform/observability
/platform/audit
/platform/support
/platform/settings
```

### Backend Routes

平台 API 使用独立前缀：

```text
/platform/**
```

租户管理端继续使用：

```text
/admin/**
/agents/**
/skills/**
/kb/**
/tools/**
```

高风险操作需要二次确认或审计理由：

- 停用租户。
- 强制切换套餐。
- 全量发布平台策略。
- 查看租户敏感 trace。
- 紧急禁用某个标准 Skill / Tool。

## MVP Modules

### 1. Tenant Operations

能力：

- 租户列表、搜索、筛选。
- 租户状态：trial / active / suspended / expired / archived。
- 套餐、席位、到期时间、管理员、最近活跃。
- 关键资源概览：用户数、Agent 数、Skill 数、知识库容量、工具/MCP 数。
- 租户详情页：配置摘要、集成状态、最近错误、用量曲线。
- 暂停 / 恢复 / 标记到期。

第一阶段不做：

- 复杂合同管理。
- 自动发票。
- 跨产品租户合并。

### 2. Plan And Subscription

能力：

- 套餐定义：Standard / Pro / Enterprise。
- 套餐额度：席位、Agent、Skill、知识库、文档、token、workflow 执行、MCP server。
- 订阅记录：组织、套餐、周期、状态、开始/结束时间。
- 超额策略：允许超额 / 软提醒 / 硬阻断。
- 套餐变更记录。

与 FEAT-003 对齐：先建立套餐和额度，不急着接支付系统。

### 3. Usage And Cost

能力：

- 查看组织级用量：聊天、token、模型、Agent 执行、Workflow 节点、知识库处理、检索、工具调用、第三方集成。
- 查看成本：平台代付模型成本、第三方工具成本、向量存储和检索成本。
- 按时间、租户、Agent、Skill、Tool、模型分组。
- 异常检测：成本突增、错误率突增、外部工具调用异常。

事实源：

- `usage_meter_event`

不从聊天表、审计表、前端点击直接结算。

### 4. Platform Skill Management

能力：

- 管理平台标准 Skill 模板。
- 创建模板版本。
- 发布、灰度、回滚。
- 查看影响分析：启用租户、绑定 Agent、发布 Workflow、派生 Skill。
- 标准 Skill 可按套餐或租户开放。

与 FEAT-009 对齐。

### 5. Platform Policy Management

能力：

- 管理平台核心策略包。
- 查看策略版本、变更说明、灰度状态。
- 发布到指定租户、指定套餐、指定比例。
- 回滚到上一稳定版本。
- 查看策略命中摘要和运行指标。

策略正文不对租户后台暴露。

### 6. Rollout Center

能力：

- 平台 Skill / Policy / Tool / Integration 的统一发布任务。
- 灰度批次。
- 影响范围。
- 成功率、错误率、成本变化。
- 暂停、继续、回滚。

### 7. Observability And Support

能力：

- 跨租户运行健康看板。
- 按租户 / Agent / session / traceId 查询运行链路。
- 查看工具调用错误、模型错误、知识库索引错误、外部集成错误。
- 支持人员只能查看脱敏摘要；查看敏感内容需要工单号和审计理由。

### 8. Platform Audit

能力：

- 记录平台人员操作。
- 记录跨租户访问。
- 记录套餐、额度、策略、标准 Skill 更新。
- 支持导出。

## Data Model

### Platform Users

```text
platform_user
- id
- username
- mobile
- email
- display_name
- status
- created_at
- updated_at

platform_user_role
- id
- platform_user_id
- role_code
- created_at
```

第一阶段也可以复用现有用户表，但必须在鉴权层区分平台角色和组织角色。更推荐新增平台用户表，降低误用 `ORG_ADMIN` 的风险。

### Tenant Operations

```text
platform_tenant_profile
- id
- org_id
- tenant_status
- lifecycle_stage
- owner_name
- owner_contact
- sales_owner
- support_owner
- plan_code
- subscription_id
- trial_ends_at
- suspended_at
- suspended_reason
- created_at
- updated_at
```

`org` 仍是租户主事实源，`platform_tenant_profile` 只承载平台运营扩展信息。

### Plans And Subscriptions

```text
billing_plan
- id
- plan_code
- name
- status
- billing_cycle
- base_price
- currency
- included_limits_json
- feature_flags_json
- created_at
- updated_at

billing_subscription
- id
- org_id
- plan_code
- status
- started_at
- current_period_start
- current_period_end
- cancel_at
- metadata_json
- created_at
- updated_at

billing_quota_snapshot
- id
- org_id
- subscription_id
- period_start
- period_end
- quota_json
- usage_json
- exceeded_json
- created_at
- updated_at
```

### Usage Metering

```text
usage_meter_event
- id
- event_id
- org_id
- user_id
- agent_id
- session_id
- source_type
- source_id
- event_type
- quantity
- unit
- model_provider
- model_name
- skill_code
- skill_version_id
- tool_name
- knowledge_base_id
- cost_amount
- cost_currency
- billable
- billing_policy
- occurred_at
- created_at
- metadata_json
```

事件类型示例：

- `chat.request`
- `model.tokens.input`
- `model.tokens.output`
- `agent.run`
- `workflow.node`
- `kb.document.index`
- `kb.retrieval`
- `tool.invoke`
- `third_party.call`

### Platform Skill And Policy

由 FEAT-009 定义：

- `platform_skill_template`
- `platform_skill_template_version`
- `platform_policy_bundle`
- `platform_rollout`
- `platform_rollout_target`

### Platform Audit

```text
platform_audit_log
- id
- actor_platform_user_id
- actor_role
- action
- target_type
- target_id
- org_id
- reason
- risk_level
- ip_address
- user_agent
- before_json
- after_json
- created_at
```

## Runtime Integration

### Metering Event Emitters

统一从后端真实执行点写事件：

- `ChatOrchestratorService`: chat request、model tokens、tool loop、runtime policy。
- `AgentWorkflowRuntimeService`: agent run、workflow node、debug run。
- `ToolOrchestratorService`: tool invoke、provider、success/failure、cost hints。
- `KnowledgeBaseService`: document upload、index、reindex、chunk count。
- `RagService`: retrieval call、hit count、strategy。
- Integration services: platform-paid third-party calls.

原则：

- 主链路不因计量失败而失败。
- 本地事务内可先写 outbox，异步归集到 `usage_meter_event`。
- 第一阶段可以直接写表，但代码接口应命名为 `UsageMeteringService.record(...)`，为后续拆服务留口。

### Quota Enforcement

配额校验位置：

- 登录和页面展示：只提示，不阻断。
- 创建资源：如 Agent / KB / Skill 超额可阻断。
- 运行时调用：token / 工具 / workflow 超额可根据套餐策略软阻断或硬阻断。

建议第一阶段：

- 只做软提醒和平台后台可见。
- 对高成本第三方工具支持租户级硬禁用。

## Security And Compliance

平台后台必须满足：

- 平台角色与组织角色分离。
- 平台 API 必须校验 platform role。
- 所有跨租户读取记录 `platform_audit_log`。
- 敏感内容默认脱敏：
  - 用户消息内容。
  - 工具参数中可能包含客户数据的字段。
  - 第三方凭据。
  - 文档正文和知识库 chunk。
- 支持人员查看敏感 trace 需要 reason / ticket id。
- 平台标准 Skill 和核心策略的编辑发布需要审计。
- 高风险发布动作需要二次确认。

## Frontend Design Notes

平台后台应偏运维工作台，而不是营销页：

- 左侧主导航：租户、套餐计费、用量成本、标准能力、策略发布、观测支持、审计。
- 列表页高密度、可筛选、可批量操作。
- 详情页采用摘要区 + tabs：
  - Overview
  - Usage
  - Limits
  - Agents / Skills
  - Integrations
  - Errors
  - Audit
- 发布中心强调版本、影响范围、批次、指标和回滚。
- 不在页面内展示说明型大段文案，复杂说明进入文档或 tooltip。

## API Examples

### Tenant List

```http
GET /platform/tenants?status=active&plan=pro&q=demo
```

Response:

```json
{
  "items": [
    {
      "orgId": "demo-org",
      "name": "Demo Org",
      "status": "active",
      "planCode": "pro",
      "activeUsers": 24,
      "agents": 6,
      "monthlyCost": 128.34,
      "usageHealth": "normal",
      "lastActiveAt": "2026-04-30T09:30:00Z"
    }
  ]
}
```

### Usage Summary

```http
GET /platform/usage/summary?orgId=demo-org&period=2026-04
```

Response:

```json
{
  "orgId": "demo-org",
  "period": "2026-04",
  "items": [
    {"eventType": "model.tokens.input", "quantity": 1200000, "unit": "token", "cost": 8.2},
    {"eventType": "tool.invoke", "quantity": 4300, "unit": "call", "cost": 3.6}
  ]
}
```

### Platform Audit

```http
POST /platform/tenants/demo-org/suspend
```

Request:

```json
{
  "reason": "Contract expired and customer success confirmed suspension."
}
```

## Migration Plan

### Phase 1: Platform Shell And RBAC

- 新增平台角色模型。
- 新增 `/platform/**` API 鉴权。
- 新增平台后台前端壳、登录态和基础导航。
- 新增 `platform_audit_log`。
- 新增租户列表和详情摘要。

### Phase 2: Usage Metering Foundation

- 新增 `usage_meter_event`。
- 新增 `UsageMeteringService`。
- 在 chat、model、tool、agent runtime、kb 关键点写事件。
- 平台后台展示用量和成本汇总。

### Phase 3: Plans And Quotas

- 新增 `billing_plan`、`billing_subscription`、`billing_quota_snapshot`。
- 平台后台可配置套餐和租户订阅。
- 租户详情显示额度和超额状态。
- 第一阶段只软提醒，少量高成本动作支持硬限制。

### Phase 4: Platform Skill And Policy Management

- 接入 FEAT-009 的平台标准 Skill 和核心策略版本。
- 新增影响分析、灰度和回滚。
- 运行时记录 policy / skill version refs。

### Phase 5: Support Observability

- 跨租户 trace 查询。
- 错误聚合。
- 脱敏查看。
- 工单理由和敏感访问审计。

### Phase 6: Service Extraction Readiness

- 将 metering 写入切到 outbox / async consumer。
- 将 billing service 逻辑收口到独立接口。
- 将 platform skill / policy config 加缓存和本地快照。
- 准备独立部署拆分。

## Future Independent Architecture

当出现以下条件时再拆独立服务：

- 平台后台需要管理多个产品，而不只是当前助手平台。
- 计费接入支付、发票、合同、对账，发布节奏独立于主业务。
- `usage_meter_event` 写入量明显影响主库性能。
- 平台配置需要跨集群、跨地域统一下发。
- 合规要求平台人员与租户业务数据物理隔离。

目标拆分：

```text
Tenant App / Agent Runtime
        |
        | emits usage events
        v
Usage Metering Service
        |
        v
Billing Service

Platform Config Service
        |
        | policy / skill template rollout
        v
Tenant App / Agent Runtime

Platform Operations Console
        |
        +-- Tenant Operations API
        +-- Billing API
        +-- Metering API
        +-- Platform Config API
        +-- Audit API
```

拆分原则：

- runtime 服务必须能在平台配置服务短暂不可用时继续使用最近稳定配置。
- 计量事件可以最终一致，不能阻断聊天主链路。
- 账单和支付可以最终一致，但配额阻断必须读稳定快照。
- 平台后台不直接写租户业务表，必须通过领域服务或平台操作服务。

## Implementation Progress

- 2026-04-30 已完成 O1-O4 的第一批落地：
  - 平台独立登录态、`/platform/**` 路由、平台角色守卫与平台审计表已就位。
  - `V31__platform_skill_template_and_tool_governance.sql` 已新增平台 Skill 模板版本表与平台 Tool 治理表。
  - `/platform/skills` 已升级为可写治理页，支持模板版本历史、草稿、发布、回滚，以及平台标准 Skill 的治理字段编辑。
  - `/platform/tools` 已升级为可写治理页，支持展示名、描述、风险、分类、启用状态编辑，并展示关联 Skill / Agent 依赖摘要。
  - 平台 Skill 模板建草稿、发布/回滚与平台 Tool 更新均已写入 `platform_audit_log`。
  - 平台 Tool `enabled=false` 已影响租户 `/tools` 目录展示，禁用的内置工具不再出现在 Agent Builder 可选目录。
- 2026-04-30 已完成 O5/O6：
  - 平台 Tool `enabled=false` 已真正接入运行时工具定义过滤与执行拦截，形成紧急禁用闭环。
  - `PlatformGovernanceIntegrationTest` 已覆盖 `ORG_ADMIN` 不能访问 `/platform/**` 与平台高风险治理动作审计保留。

## Acceptance Criteria

- 平台后台和租户后台路由、权限、菜单完全分离。
- `ORG_ADMIN` 无法访问 `/platform/**`。
- 平台人员访问租户详情会写平台审计。
- 可以查看租户列表、套餐、用量和成本摘要。
- 可以配置基础套餐和租户订阅。
- 运行时关键链路会写统一 `usage_meter_event`。
- 平台标准 Skill / 核心策略版本可在后台查看、发布和回滚。
- 支持未来拆分：metering、billing、platform config 的代码边界清晰。

## Risks

- 如果平台角色复用组织角色，后续跨租户权限会非常难审计。
- 如果计费用量直接从业务表临时聚合，后续账单口径会混乱。
- 如果平台后台直接写租户业务表，未来拆服务和合规审计都会变困难。
- 如果第一阶段做太完整的支付/合同/发票，会拖慢平台控制面 MVP。
- 如果平台支持人员能直接看明文消息和工具参数，会带来严重数据安全风险。

## Open Questions

- 平台用户是否需要独立登录入口，还是复用现有登录后按角色切换？
- 计费周期第一阶段按自然月，还是按订阅开始日期滚动月？
- 平台后台是否需要支持私有化部署客户的离线 License？
- 平台标准 Skill 是否按套餐开放，还是全部租户可见但按用量计费？
- 支持人员查看敏感 trace 的工单系统是否由本系统内置，还是对接外部系统？
