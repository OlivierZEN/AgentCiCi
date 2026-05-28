---
kind: feature-spec
feature_id: FEAT-003
title: SaaS Billing And Packaging
status: approved
owner_role: shared
task_ids: TASK-007
related_decisions: none
related_issues: none
updated_at: 2026-05-28T09:35:00Z
updated_by: MANAGER-001
---

# FEAT-003 SaaS Billing And Packaging

## Goal

- 为 `cc-cici-assistant` 设计一套适合 SaaS 化交付的企业混合计费模型，兼顾平台订阅收入与 AI/自动化资源成本回收。
- 让计费口径与当前产品形态对齐，而不是把系统错误简化为“只按聊天次数收费”。
- 为后续账单中心、套餐配置、用量统计、超额控制和销售报价提供统一设计底稿。

## Problem

- 当前项目已经具备企业 AI 平台雏形：多组织、助手端、管理后台、知识库、模型管理、工具治理、Skill、Agent Builder、个人 Workflow、审计和基础成本统计。
- 这类产品的成本来源并不单一，至少同时包含：
  - 大模型 token 与推理调用
  - Agent / Workflow 执行
  - 知识库解析、切片、向量存储与检索
  - MCP / 第三方工具调用
  - 组织级治理、审计、支持与运维
- 如果仅按聊天次数收费，会低估治理能力和构建能力的价值，也无法覆盖不同组织之间显著不同的资源消耗。
- 当前后端仅有 `GET /ops/metrics/cost` 的轻量成本估算入口，尚未形成可运营的套餐、账单、配额和超额治理闭环。

## Product Context

- 主计费主体应是 `组织/租户`，而不是个人用户。
- 当前系统的可计费能力已经覆盖：
  - 助手聊天与流式会话
  - 模型配置与多模型路由
  - 知识库、文档上传、发布和 RAG 检索
  - Tool / Skill / MCP 治理
  - Agent Builder、发布版本、运行时调试与执行轨迹
  - 用户记忆与个人 Workflow
  - 审计与运维成本统计
- 因此适合采用 `平台订阅 + 资源席位 + AI/自动化用量 + 增值模块` 的企业混合计费方案。

## Design Principles

1. 账单归属到组织，避免个人付费模型破坏多租户治理。
2. 平台能力和资源消耗分层计费，避免“基础治理价值”被纯按量模型吞没。
3. 计量口径优先绑定后端真实事件，而不是前端点击或页面动作。
4. 先建立统一的用量事件协议，再派生账单、套餐配额和超额规则。
5. 同一事实只保留一个结算来源，例如 token 用量来自模型调用记录，不能同时由聊天表和审计表重复计算。
6. 对客户自带资源和平台代付资源做区分，避免错误计费。

## Recommended Billing Model

### Two Supported Commercial Schemes

AgentCiCi 同时支持两种商业方案，但二者的收费主轴不同：

| 方案 | 主收费口径 | 适用客户 | 模型资源责任 | credits 角色 |
| --- | --- | --- | --- | --- |
| 私有化 / 本地部署 | 私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费 | 本地部署、专属网络、客户自有模型和 GPU、强数据隔离 | 客户承担本地模型、GPU、推理和 token 成本 | 用量看板、成本归因、预算控制、合同额度、平台代付资源治理 |
| SaaS / 云托管 | 平台订阅 + 操作/构建席位 + Work Credits + 企业增值模块 | 标准云服务、平台代付模型或托管连接器、弹性扩容 | 平台承担或代付模型、语音、搜索、托管连接器等资源 | 套餐额度、超额、预算控制和真实用量扣费 |

部署级开关由 `app.billing.deployment-mode` 决定，支持 `private_deployment` 和 `saas`。当前代码默认 `private_deployment`，避免在未明确选择 SaaS 前误把客户自有本地模型 token 作为收费项。

### Edition Lines

需要定义版本，但不要把版本设计成所有客户都必须完全一致的价格表。版本用于产品包装、默认 plan seed、页面展示和销售报价锚点；具体合同仍可叠加容量包、服务包和定制条款。

私有化版本线：

| 版本 | 目标客户 | 主收费项 | 典型包含能力 | 可追加包 |
| --- | --- | --- | --- | --- |
| `部门版` | 单部门试点、本地知识问答、少量业务连接 | 年费许可 + 少量操作/构建席位 + 基础容量 | 单组织或少量组织、基础 Agent/Skill/知识库、基础连接器、基础审计、基础运行日志 | 文档容量、Agent 数、连接器实施、培训 |
| `企业版` | 公司级推广、多部门协作、Open API 和运行观测 | 年费许可 + 席位包 + 模块/容量包 + 年度维护 | 多组织、更多 Agent/Skill/知识库、Open API、运行 trace、成本归因、会议听记、更多连接器 | 高并发 Open API、日志保留、会议听记并发、测试/灾备环境 |
| `集团版` | 集团级治理、多实例/多环境、强审计与集成 | 定制年费许可 + 多实例/环境 + 专属服务 | 多实例、生产/测试/灾备、高级审计、SSO、数据保留、专属支持、定制集成 | 专属 SLA、驻场/专属支持、私有模型适配、集团数据隔离 |

SaaS 版本线：

| 版本 | 目标客户 | 主收费项 | 典型包含能力 | 超额/升级 |
| --- | --- | --- | --- | --- |
| `团队版` | 小团队试点、轻量知识问答和常用工具 | 平台订阅 + 少量操作/构建席位 + 基础 credits | 基础 Agent、基础知识库、基础工具、基础用量看板 | soft limit 或购买小额 credits 包 |
| `商业版` | 部门级到公司级生产使用 | 平台订阅 + 席位包 + 中高 credits + 模块包 | 正式 Open API、更多 Agent/Skill/Workflow、运行 trace、成本归因、常用连接器 | 自动 top-up、容量包、更多连接器 |
| `企业版` | 大客户、集团采购、严格治理和 SLA | 年度合同 + 定制 credits/容量 + 企业增值模块 | SSO、SLA、高级审计、专属资源池、数据保留、专属支持、定制连接器 | 合同级最低消费、阶梯单价、年度额度滚动 |

命名建议：

- 对外中文优先用 `团队版 / 商业版 / 企业版` 表达 SaaS。
- 对外中文优先用 `部门版 / 企业版 / 集团版` 表达私有化。
- 内部代码建议使用稳定 code：`saas_team`、`saas_business`、`saas_enterprise`、`private_department`、`private_enterprise`、`private_group`。
- `trial` 只作为试用状态或试用 plan，不作为正式付费版本线。

### Private Deployment Commercial Position

私有化和本地部署版本的主报价口径应是：

```text
私有化年费许可 + 操作/构建席位 + 模块/容量包 + 实施运维服务费
```

不要把本地模型 token 作为第一阶段主收费项。客户本地部署并使用自己的本地模型时，GPU、推理、模型服务和 token 成本都在客户侧；AgentCiCi 再按 token 强收费会被理解为对客户自有资源二次收费。

AgentCiCi 在私有化场景应收取平台价值：

- 多组织治理、权限、登录、审计和数据保留。
- 助手工作台、管理后台、模型配置和路由。
- 知识库、Skill、Agent Builder、Workflow、Open API。
- 工具连接、CRM / 企业微信 / 飞书 / CloudCC / Salesforce 等企业集成。
- 运行观测、trace、成本归因、会议听记、部署运维和企业支持。

私有化版本建议按规模分档：

- `部门版`：单组织或少量组织，少量用户，基础 Agent、知识库和连接器。
- `企业版`：多组织，更多用户，更多 Agent、知识库、连接器、Open API 和运行观测。
- `集团版`：多实例、生产/测试/灾备环境、高级审计、SSO、数据保留、专属支持和定制集成。

私有化容量包优先售卖客户可理解的软件能力，而不是 token 包：

- Agent 数量、Skill 数量、Workflow 数量。
- 知识库数量、文档数、文档容量、chunk 数。
- Open API 并发、QPS、credential 数。
- 连接器数量、MCP Server 数量、平台内置工具治理能力。
- 审计日志和运行 trace 保留期。
- 会议听记并发路数和转写任务容量。
- 部署实例数和环境数：生产、测试、灾备。

一次性交付费和年度维护费必须单列：

- 部署实施费、环境适配费、模型接入适配费。
- CRM、企业微信、飞书、CloudCC、Salesforce 等连接器实施费。
- 数据迁移、知识库初始化、培训、验收支持。
- 年度维护与升级服务费，可按授权费固定比例配置，例如 15%-25%，具体取决于 SLA。

推荐报价话术：

> 本地部署版按“平台授权 + 主动操作/构建席位 + 企业模块与容量 + 实施运维服务”计费；客户自有模型的 token 和推理成本由客户承担，AgentCiCi 不对本地模型 token 二次收费，只对平台治理、编排、观测、知识库、连接器和企业支持收费。

### SaaS Commercial Position

SaaS 和云托管版本仍可采用：

```text
平台订阅 + 操作/构建席位 + Work Credits + 企业增值模块
```

SaaS 方案适合由平台承担或代付模型、云端语音、第三方搜索、托管连接器、运行资源和弹性扩容的场景。此时 `Work Credits` 可以作为客户套餐额度、超额、预算控制和真实用量扣费口径，但仍不把 token 暴露为客户第一层购买单位。

SaaS 版本默认售卖：

- 平台基础订阅：组织空间、权限、审计、后台、基础运行环境。
- 操作席位和构建席位。
- Work Credits：覆盖模型推理、RAG、工具、工作流、Open API、索引和平台代付资源。
- 企业增值模块：SSO、SLA、高级审计、成本归因、专属支持、专属资源池。

SaaS 中 token 仍是底层成本和争议查账字段，不作为销售页、账单页和套餐页的一线收费语言。

### Switching Rule

`deployment-mode` 是部署级商业模式开关，不是普通租户管理员可随意切换的套餐字段：

- `private_deployment`：默认不对 `customer_paid` 本地模型和本地连接器产生强扣费 ledger debit；展示授权、席位、模块、容量、服务和 credits 治理。
- `saas`：允许 `platform_paid` 模型、云端语音、第三方服务和托管连接器进入 Work Credits 扣费；套餐和超额围绕 credits 展开。
- 租户差异仍由 plan/subscription/capacity pack 表达，不能覆盖部署级资源责任边界。

运行时、rating、quota 和 UI 都必须读取同一个 billing mode fact，不能各自硬编码“私有化”或“SaaS”判断。


### 1. Billing Layers

- `平台基础订阅费`
  - 按组织按月或按年收取。
  - 覆盖组织空间、多租户隔离、基础鉴权、管理后台、基础审计、基础模型/工具/知识库配置能力。

- `席位费`
  - 按组织内启用席位计费。
  - 推荐区分两类：
    - `协作席位`：面向普通业务成员，主要使用聊天、知识检索、个人工作流。
    - `构建席位`：面向管理员、AI 应用构建者，允许管理模型、工具、知识库、Skill、Agent、Workflow。

- `资源用量费`
  - 对直接产生基础设施或第三方 API 成本的行为计量收费。
  - 包含模型调用、Agent/Workflow 执行、知识库处理/检索、第三方工具调用等。

- `增值模块费`
  - 对企业级治理、高级安全、专属支持、私有化能力单独收费。

### 2. Why This Model Fits

- 该项目不是单纯问答机器人，而是企业 AI 平台。
- 基础订阅可体现多组织治理、权限、审计、运维和配置中心价值。
- 席位可反映组织采用规模。
- 按量可回收模型、检索、调度和第三方连接成本。
- 增值模块可支持更高客单价和企业销售场景。

## Billable Items

### 1. Platform Subscription

- 组织空间数
- 基础组织管理
- 基础角色权限
- 助手端与管理后台访问权
- 基础审计日志保留期
- 基础运维成本看板
- 基础模型配置数量上限
- 基础工具配置数量上限

建议默认按套餐内置，不按单项拆零出售。

### 2. Seats

- `协作席位`
  - 使用聊天、会话历史、知识检索、个人工作流、个人邮箱等员工侧能力。
- `构建席位`
  - 使用知识库维护、模型管理、工具治理、Skill Authoring、Agent Builder、Workflow 发布与调试等构建侧能力。

建议计费规则：

- 按月统计启用席位峰值，避免频繁开关账户导致逃费。
- `ORG_ADMIN` 默认属于构建席位候选，但最终应按“是否使用构建能力”判断，而不是仅按角色编码判断。
- 企业版可提供最低采购席位包和阶梯单价。

### 3. AI Usage

- 聊天请求次数
- 输入 token
- 输出 token
- 流式响应会话次数
- 高级模型调用量
- 语音转写分钟数
- 语音合成分钟数（若后续启用）

建议规则：

- 云托管、平台代付模型或平台统一模型网关场景中，token 可作为内部成本和用量折算依据；私有化本地模型场景中，token 不作为第一阶段客户主收费项。
- 按模型档位区分价格，例如基础模型、增强模型、高阶推理模型分别计价。
- 客户自带模型密钥时，可只收平台服务费，不收模型代付费。
- 客户自有本地模型、本地工具和本地连接器应标记为 `customer_paid`，通常只做低倍率平台调度计量、成本归因或预算治理，不额外按 token 强收费。
- 平台代付模型、第三方搜索、云端语音或托管连接器应标记为 `platform_paid`，可按 credits、容量包或实际用量收费。

### 4. Agent And Workflow Usage

- Agent 运行次数
- Workflow 执行次数
- 节点执行数
- 定时任务执行次数
- 发布版本数上限
- 调试运行次数或调试资源额度

建议规则：

- 计量口径应以“进入后端执行引擎的实例”为准，不以按钮点击为准。
- 节点执行数适合用于成本更敏感的专业版/企业版超额计费。
- 对失败重试是否计费需要单独策略：
  - 平台错误导致的失败不计费
  - 业务逻辑正常重试可计入执行量

### 5. Knowledge Base Usage

- 知识库数量
- 文档数量
- 文档总存储容量
- 切片数量
- 向量存储容量
- 检索调用次数
- 文档重建/重新索引次数

建议规则：

- `存储类` 和 `操作类` 分开计费。
- 小客户可在套餐内包含固定知识库和文档额度。
- 大客户超额后再按存储容量、索引次数或检索量收费。

### 6. Tools And Integrations

- 启用工具数量
- MCP Server 数量
- 第三方连接器数量
- 外部 API 代理调用次数
- 同步任务执行量

建议规则：

- 需要区分：
  - 平台内置工具
  - 平台代付的第三方工具
  - 客户自带密钥或客户自有服务的工具
- 对平台代付型工具可按调用量结算。
- 对客户自带型工具可只计平台调度费或直接不计用量费，仅算套餐能力。

### 7. Governance And Enterprise Add-ons

- 高级审计日志保留与导出
- 成本中心/部门归因
- SSO / 企业身份集成
- 高级权限审批流
- SLA 与专属支持
- 专属资源池
- 私有化部署或专属实例

这些能力应作为独立增值包，不建议混入基础版。

## Suggested Packages

正式包装以 `Edition Lines` 为准。旧的 Standard / Pro / Enterprise 命名容易同时混用 SaaS 和私有化语境，后续不再作为主事实源。

首版 plan seed 建议：

- SaaS：`saas_team`、`saas_business`、`saas_enterprise`，另设 `trial` 作为试用状态。
- 私有化：`private_department`、`private_enterprise`、`private_group`。
- 容量包独立建模，不塞进版本名：`agent_pack`、`knowledge_capacity_pack`、`openapi_capacity_pack`、`meeting_minutes_pack`、`retention_pack`、`environment_pack`。

## Metering Model

### 1. Metering Source Of Truth

- 模型 token：来自模型调用日志或统一 AI 网关调用记录。
- 聊天请求：来自编排入口的成功受理记录。
- Agent / Workflow 执行：来自运行时执行器的实例记录和节点 trace。
- 知识库处理：来自文档上传、索引、切片、向量写入与检索记录。
- 工具调用：来自 Tool / Skill / MCP 统一调度层。
- 席位：来自用户启用状态和月内席位峰值快照。

### 2. Meter Event Shape

建议统一抽象为 `usage_meter_event`，核心字段包括：

- `id`
- `org_id`
- `billable_domain`，例如 `ai_token`、`agent_run`、`workflow_node`、`kb_storage`、`kb_retrieval`、`tool_call`
- `billable_item_code`
- `quantity`
- `unit`
- `source_type`
- `source_id`
- `plan_code`
- `occurred_at`
- `metadata_json`

这样可以先统一采集，再按价格表生成账单明细。

### 3. Billing Period

- 支持自然月结算。
- 年付客户按年度购买基础订阅和席位包。
- 按量部分按月汇总出账更符合 SaaS 运营习惯。

### 4. Overage Policy

每个用量项都应支持三种策略之一：

- `auto_charge`：超额自动按量计费。
- `soft_limit`：超额告警但不停服。
- `hard_limit`：超额后阻断新请求，等待管理员扩容。

推荐：

- 聊天与模型 token：`auto_charge` 或 `soft_limit`
- 高成本高级模型：支持 `hard_limit`
- 知识库存储：`soft_limit`
- 私有连接器/API：按合同配置

## Suggested Backend Scope

### 1. New Domains

- `billing_plan`
- `billing_plan_feature`
- `billing_subscription`
- `billing_usage_balance`
- `billing_price_catalog`
- `billing_invoice`
- `billing_invoice_line`
- `usage_meter_event`
- `billing_threshold_policy`

### 2. API Direction

建议新增或扩展管理端 API：

- `GET /admin/billing/overview`
- `GET /admin/billing/usage`
- `GET /admin/billing/invoices`
- `GET /admin/billing/plans`
- `POST /admin/billing/subscription/change`
- `GET /admin/billing/thresholds`

并让现有 `GET /ops/metrics/cost` 演进为成本侧底层能力，而不是最终账单接口。

### 3. Existing Capability Mapping

- `ChatController` / `ChatOrchestratorService`
  - 提供聊天请求、模型调用、会话级用量事件。
- `AgentWorkflowRuntimeService`
  - 提供 Agent / Workflow 执行和节点级用量事件。
- `ToolOrchestratorService`、Skill、MCP 调用链路
  - 提供工具调用量和外部代付型用量事件。
- 知识库文档处理和检索链路
  - 提供文档处理、存储、检索计量事件。
- `OpsController.cost`
  - 可作为成本聚合原型，后续升级为面向账单域的组织成本汇总来源之一。

## Suggested Frontend Scope

管理后台建议新增账单中心页面：

- 套餐概览
- 本月用量
- 超额预警
- 历史账单
- 可计费资源分布
- 部门或应用成本归因（企业版）

并在现有页面增加额度提示：

- 模型管理页：显示模型额度和当前调用成本
- 工具管理页：显示启用工具数、连接器数、代付调用量
- 知识库页：显示知识库配额、文档/存储占用、索引次数
- Agent Builder / Workflow 页：显示本月执行量、调试量、发布数限制

## Non-goals

- 本设计不在本轮直接实现支付网关、开票系统或税务能力。
- 本设计不处理面向个人 C 端订阅的 Apple/Google 内购模式。
- 本设计不覆盖私有化部署的全部商务条款，仅定义产品侧可支持的计费抽象。
- 本设计不要求私有化本地模型按 token 强制收费；本地模型 token 只作为内部观测、归因和后续合同治理依据。

## Acceptance

- 文档必须明确该项目采用组织级企业混合计费，而不是单用户或纯聊天计费。
- 文档必须覆盖平台订阅、席位、AI 用量、Agent/Workflow、知识库、工具集成、企业增值模块七类计费项。
- 文档必须说明套餐分层、计量来源、超额策略和现有代码能力映射关系。
- 后续实现者仅通过阅读本 spec，就能拆出后端计量、账单中心和套餐控制的开发任务。

## Verification

- 文档评审：
  - 计费项是否覆盖当前已存在的主要产品能力
  - 计量口径是否绑定真实后端事件
  - 套餐分层是否能支持销售报价与超额运营
- 代码映射检查：
  - `backend/src/main/java/com/codehouse/ciciassistant/ops/api/OpsController.java`
  - `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
  - `backend/src/main/java/com/codehouse/ciciassistant/agent/service/AgentWorkflowRuntimeService.java`

## Handoff Notes

- 若开始实现，请先拆第一阶段：
  - 建立统一 `usage_meter_event`
  - 将 `ops/metrics/cost` 升级为真实聚合口径
  - 管理端增加账单总览页
- 不要一开始就接支付系统；应先做计量、套餐和阈值控制。
- 私有化项目不要先卖 token 包；先落地年费授权、主动操作/构建席位、模块/容量包、实施费和年度维护费。
- 如果后续进入正式开发，应再补一份实现计划，将后端数据模型、异步汇总任务和管理端页面拆成可执行任务。
