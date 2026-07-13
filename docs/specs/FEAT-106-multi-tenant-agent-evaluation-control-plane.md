---
kind: feature-spec
feature_id: FEAT-106
title: 多租户智能体评测控制面与生产发布门禁
status: in_progress
owner_role: product-agent-quality
task_ids:
  - TASK-200
related_decisions:
  - DEC-020
  - DEC-021
  - DEC-027
related_features:
  - FEAT-031
  - FEAT-019
  - FEAT-022
  - FEAT-080
updated_at: 2026-07-14T00:00:00+08:00
updated_by: MANAGER-001
---

# FEAT-106 - 多租户智能体评测控制面与生产发布门禁

## 1. 用户确认的目标

在 AgentCiCi 现有 `agent_eval_*` 骨架上落地生产就绪的多租户评测体系，覆盖平台标准、行业、租户私有评测资产，真实运行评测、确定性断言、版本快照、结果解释、发布门禁、线上 Trace 回流，以及平台运营端、租户管理端和 Agent Builder 的完整产品入口。

用户明确指出：智能体构建页面中的“发布渠道”指飞书、钉钉、企业微信、Web、Open API 等运行渠道，不是发布评测功能。评测必须使用独立的信息架构和页面语义，不得继续嵌入或混淆在“发布渠道”区域。

## 2. 当前基线

### 2.1 已验证能力

- V67 已有 `agent_eval_suite`、`agent_eval_case`、`agent_eval_run`、`agent_eval_case_result`。
- `AgentEvaluationService` 支持创建租户 Agent 评测集、添加单断言用例、同步运行和查询结果。
- `AgentWorkflowRuntimeService.evaluateVersionForEvaluation` 可针对指定编译版本运行，并标记 `runMode=EVALUATION`。
- `AgentProductionReadinessService` 已把 blocking suite 的最近结果纳入生产就绪检查。
- Agent Builder 已有一个基础“发布评测”卡片，但当前放在 `activeEditorTab=publish` 的发布渠道页面中。
- `/admin/ops` 已有运行 Trace，可作为生产问题回流的数据源。
- `/platform/*` 与 `/admin/*` 已形成独立认证、RBAC 与产品壳层。

### 2.2 当前缺口

- 评测资产只属于单个租户和单个 Agent，无法表达平台核心、标准应用、行业包与租户私有四层资产。
- 平台标准评测集没有草稿、发布、版本冻结、租户授权与隐藏用例边界。
- 用例只能维护单个简单断言，缺少结构化行为、工具参数、RAG、权限、确认、延迟与成本断言。
- 运行缺少候选版本与基线版本对比、完整依赖快照、结果失效判断和统一质量结论。
- 运行仍是同步串行最小实现，缺少稳定的运行状态、错误隔离和可重入结果查询。
- 没有租户 AI 质量中心、平台智能体质量中心、质量问题闭环和 Trace 转回归入口。
- Agent Builder 的评测能力与“发布渠道”页面混在一起，产品语义错误。
- 没有平台隐藏挑战集保护、跨租户数据脱敏、平台运营审计和写工具副作用声明。

## 3. 产品信息架构

### 3.1 平台运营端

新增 `/platform/evaluation`，菜单名“智能体质量”，面向平台管理员和运营人员：

- 质量总览：平台评测资产、运行、P0、安全失败和租户采用情况。
- 标准评测集：维护平台核心集、标准 AI 应用集和行业评测包。
- 套件版本：草稿、发布、归档；已发布版本不可原地修改。
- 隐藏用例：租户只得到必要结果，不暴露输入、期望答案、裁判 Prompt 和完整评分细则。
- 运行视图：按套件、租户、Agent、版本和状态查看脱敏后的运行摘要。

平台端不得默认展示租户原始客户输入、模型完整输出或业务系统 payload。

### 3.2 租户管理端

新增 `/admin/evaluation`，一级菜单名“AI质量”：

- 质量概览：组织 Agent 生产可用、阻塞、通过率、P0 与回归趋势。
- 评测集：查看强制平台集、已授权行业包，维护租户私有评测集。
- 运行记录：按 Agent、版本、套件和状态筛选。
- 质量问题：从失败结果或 Trace 创建问题，记录根因、修复版本和复测状态。
- 门禁策略：租户只能在平台下限之上收紧，不得关闭平台 P0/安全强制门禁。

### 3.3 Agent Builder

在 Agent Builder 编辑器一级文本 Tab 中新增“评测”，与“定义”和“发布渠道”并列：

```text
[智能体定义] [评测] [发布渠道]
```

- “发布渠道”只维护飞书、钉钉、企业微信、Web、Open API、定时触发等运行入口。
- “评测”展示当前 Agent 的适用套件、版本对比、运行状态、发布结论和失败用例。
- Builder 顶部“发布版本”动作仍统一调用生产就绪检查，但失败后引导到“评测”Tab，而不是发布渠道。
- 不再在发布渠道页面创建评测集或维护用例。

### 3.4 观测运维

`/admin/ops` Trace 详情增加“加入回归集”：

- 选择目标租户私有套件。
- 自动带入脱敏后的输入、最小必要上下文、Agent 和 Trace 引用。
- 新用例默认为 `DRAFT`，补充期望行为并审核后才能 `ACTIVE`。
- 平台上报只产生脱敏候选，不直接复制租户原始数据到平台评测资产。

## 4. 评测资产模型

### 4.1 套件作用域

`scope_type`：

- `PLATFORM_CORE`：平台强制安全与权限基线。
- `APP_STANDARD`：标准 AI 应用能力基线。
- `INDUSTRY_PACK`：行业评测包。
- `TENANT_PRIVATE`：租户私有评测集。

`visibility`：

- `SEALED`：租户只看分类、数量和必要失败证据。
- `AUTHORIZED`：授权租户可查看非隐藏用例摘要。
- `TENANT_ONLY`：仅归属租户可见。

`lifecycle_status`：`DRAFT / PUBLISHED / ARCHIVED`。

已发布平台套件版本不可修改；升级创建新版本。租户私有套件允许编辑草稿，任何影响断言或输入的修改都使旧运行结果失效。

### 4.2 套件绑定

新增套件绑定关系，支持按以下对象生效：

- 全平台强制。
- 标准应用 `app_code`。
- 行业 `industry_code`。
- 指定组织。
- 指定 Agent。

运行时按平台强制、应用标准、行业授权、租户私有顺序解析适用套件，去重后执行。

### 4.3 用例

用例补充：

- `case_key`、类别、风险等级、标签和生命周期。
- 多轮历史、外部上下文和 Fixture JSON。
- `assertion_config_json`：多条确定性断言。
- `judge_config_json`：可选语义评分配置和版本。
- `created_from_trace_id`、脱敏状态、审核状态和来源说明。
- `hidden_case`：平台隐藏挑战用例。

首批确定性断言：

- 输出包含/不包含、状态相等。
- 工具必须/禁止调用。
- 工具参数包含指定字段和值。
- RAG 必须使用、知识库或来源关键词命中。
- 必须转人工、必须安全拒答。
- 输出 JSON Schema 关键字段存在。
- 最大耗时、最大工具调用次数。

P0、安全、权限和写入确认不得只依赖 LLM Judge。

## 5. 运行与版本快照

### 5.1 运行目标

- `CANDIDATE`：指定编译版本。
- `PUBLISHED`：当前线上版本。
- `COMPARE`：候选与线上基线成对运行。
- `TRACE_REPLAY`：生产 Trace 回放形成的回归运行。

### 5.2 运行快照

每次运行固化：

- Agent ID、编译版本、已发布基线版本。
- Agent 定义摘要与配置指纹。
- Skill 代码、解析版本、激活模式和提示词指纹。
- 模型路由、模型名称与主要参数。
- 知识库 ID、发布状态和更新时间摘要。
- 工具白名单、工具 Schema 指纹。
- 平台策略包版本。
- 套件和用例版本。

快照指纹与当前候选配置不一致时，结果状态为 `STALE`，不能满足发布门禁。

### 5.3 评测模式和副作用

- 评测继续复用正式 Agent 编译版本、Skill 解析、工具白名单和平台策略解析。
- `runMode=EVALUATION` 必须进入结果和 Trace 元数据，不进入真实客户会话指标。
- 默认禁止真实写工具；检测到写工具意图时记录 `BLOCKED_WRITE` 证据并按用例断言判断。
- 首版不创建真实 CRM、工单、消息或审批记录。
- 单条用例失败不得中止整个套件；运行结果记录 `PASSED / FAILED / ERROR / SKIPPED`。

## 6. 结果、门禁和问题闭环

### 6.1 统一结论

- `READY`：所有强制门禁满足，可发布。
- `WARNING`：无阻断失败，但存在非强制回归或成本/延迟警告。
- `BLOCKED`：P0、安全、权限、强制套件或最低通过率失败。
- `STALE`：候选配置已变化，需要重新评测。
- `NOT_RUN`：强制套件没有有效运行。

### 6.2 结果详情

运行详情必须展示：

- 套件和版本、候选与基线 Agent 版本。
- 通过率、P0、安全、工具、RAG、延迟和运行错误。
- 每个失败用例的期望行为、实际输出、断言明细、工具证据、RAG 证据和运行 Trace。
- 平台隐藏用例只显示允许租户处理问题所需的失败类别和脱敏证据。

### 6.3 发布门禁

- 平台 `PLATFORM_CORE` 阻断规则不可被租户关闭。
- P0 和安全失败必须阻断。
- blocking 套件没有有效运行、运行失败或结果过期时阻断。
- 发布成功保存所引用的评测运行 ID 和快照指纹，支持审计追溯。
- “发布渠道”是否配置只属于运行入口检查，不决定评测资产的维护位置。

### 6.4 质量问题

失败结果可创建质量问题：

- 根因分类：Prompt、Skill、知识库、检索、工具、权限策略、模型路由、代码或用例缺陷。
- 保存负责人、状态、修复版本、修复说明和验证运行。
- 修复后必须支持单用例复测、所属套件复测和发布前全量回归。

## 7. API 设计

### 7.1 租户 API

```text
GET    /evaluation/overview
GET    /evaluation/suites
POST   /evaluation/suites
GET    /evaluation/suites/{suiteId}
PUT    /evaluation/suites/{suiteId}
POST   /evaluation/suites/{suiteId}/archive
GET    /evaluation/suites/{suiteId}/cases
POST   /evaluation/suites/{suiteId}/cases
PUT    /evaluation/cases/{caseId}
POST   /evaluation/cases/{caseId}/activate
POST   /evaluation/cases/{caseId}/disable
POST   /evaluation/runs
GET    /evaluation/runs
GET    /evaluation/runs/{runId}
GET    /evaluation/runs/{runId}/results
POST   /evaluation/cases/from-trace
GET    /evaluation/issues
POST   /evaluation/issues
PUT    /evaluation/issues/{issueId}
```

保留现有 `/agents/{agentId}/evaluation/*` 兼容 API，内部统一委托新的评测应用服务。

### 7.2 平台 API

```text
GET    /platform/evaluation/overview
GET    /platform/evaluation/suites
POST   /platform/evaluation/suites
PUT    /platform/evaluation/suites/{suiteId}
POST   /platform/evaluation/suites/{suiteId}/publish
POST   /platform/evaluation/suites/{suiteId}/archive
GET    /platform/evaluation/suites/{suiteId}/cases
POST   /platform/evaluation/suites/{suiteId}/cases
PUT    /platform/evaluation/cases/{caseId}
GET    /platform/evaluation/runs
```

平台写操作要求 `PLATFORM_ADMIN` 或 `PLATFORM_OPERATOR`，只读质量摘要允许 `PLATFORM_AUDITOR`。

## 8. 首批内置资产

### 8.1 平台核心集

- Prompt Injection 识别。
- 未确认写动作禁止执行。
- 禁止跨租户数据访问。
- 敏感信息不得原样输出。
- 高风险动作必须拒绝或转人工。

### 8.2 客户互动工作台标准集

- 新老客户识别。
- 已有商机优先更新，避免重复创建。
- 事实、推断和待确认信息分离。
- 客户互动证据可追溯。
- 动态动作必须有原文证据。
- CRM 写回必须人工确认。
- 多 Skill 上下文下不得越过当前激活 Skill 的工具权限。

首批种子用例以确定性、安全和工具边界为主；不虚构行业标准答案。

## 9. 权限与多租户边界

- 所有租户资产、运行、结果和问题均带 `org_id`。
- 平台套件使用平台作用域，不伪装成某个租户私有资产。
- 租户不能修改平台套件、隐藏用例和平台门禁下限。
- 平台运行列表默认只返回租户、Agent、状态和汇总指标，不返回原始输入输出。
- Trace 转用例必须脱敏，且只有当前组织管理员或拥有 Agent 管理权限的用户可执行。
- API 返回不得包含模型密钥、连接器密钥、CloudCC token、完整系统 Prompt 或隐藏裁判 Prompt。

## 10. 页面设计约束

- 产品 register，沿用“鎏金账房”：暖象牙底、墨色文字、香槟金结构线和紧凑治理密度。
- 评测总览使用摘要行、表格、文本 Tab 和必要分隔线，不使用英雄数字、玻璃拟态或卡片宫格。
- Builder 的“智能体定义 / 评测 / 发布渠道”使用现有文本 Tab 语汇。
- 评测结果详情优先使用页面内主从结构或侧边详情，不为每条失败创建嵌套卡片。
- 默认仅验收桌面端，不新增移动端适配。

## 11. 实施阶段

### 阶段 A：治理与兼容迁移

- 新增 V79，对现有四表做非破坏扩展，增加绑定、问题与发布引用表。
- 保留 V67 数据和原 API，不删除历史运行。
- 将旧 suite 默认迁移为 `TENANT_PRIVATE / TENANT_ONLY / PUBLISHED`。

### 阶段 B：后端控制面

- 统一租户评测服务、平台模板服务、断言引擎、快照服务和发布门禁。
- 支持多断言、运行详情、版本对比、结果过期、Trace 回流和质量问题。
- 平台与租户 API 完成 RBAC 和跨租户隔离测试。

### 阶段 C：前端产品面

- `/admin/evaluation` 租户 AI 质量中心。
- `/platform/evaluation` 平台智能体质量中心。
- Builder 独立“评测”Tab，彻底移出发布渠道。
- Ops Trace “加入回归集”入口。

### 阶段 D：生产验证

- 后端聚焦集成测试、编译、Flyway 校验。
- 前端 Vitest、TypeScript/Vite 生产构建。
- 真实浏览器验证平台端、租户端、Builder 和 Ops 路由。
- 桌面全页截图检查层级、溢出、状态和交互反馈。
- Compose 配置、`git diff --check`、项目状态校验与生产发布 dry-run。

## 12. 验收标准

- 平台运营可以创建、编辑草稿、发布和归档平台核心、标准应用与行业评测套件。
- 租户管理员可以查看强制套件和维护租户私有套件，不能修改平台资产。
- Agent Builder 存在独立“评测”Tab；“发布渠道”页面不再渲染评测卡片。
- 候选版本可以运行适用套件，并看到统一结论、失败详情和线上基线对比。
- P0、安全、强制门禁失败或结果过期时，发布被后端阻断。
- 用例可包含多条确定性断言，至少覆盖输出、工具、工具参数、RAG、接管、安全和延迟。
- 运行保存可审计版本快照，配置变化后旧结果标记过期。
- `/admin/ops` 可以把当前组织 Trace 转成待审核租户私有回归用例。
- 平台隐藏用例不会向租户返回输入、完整期望答案或裁判配置。
- 跨租户 API、平台 RBAC、写工具副作用隔离和发布门禁均有自动化测试证据。
- 所有新增页面完成桌面端浏览器与截图验收，不与发布渠道语义混淆。

## 13. 非目标

- 不建设模型训练、微调或完整离线标注平台。
- 不允许评测自动修改 Prompt、Skill、知识库或代码并直接发布。
- 不做跨租户公开排行榜。
- 不在本任务中新增移动端适配。
- 不在评测环境真实写入 CRM、工单、消息、审批或外部业务系统。

## 14. 实施进度

- 2026-07-14：用户确认整体信息架构和入口分层，明确评测不得与 IM 发布渠道混淆。
- 2026-07-14：完成现有 V67、`AgentEvaluationService`、生产就绪门禁、Builder 基础卡片、平台/租户壳层与 Trace 入口的代码审计。

