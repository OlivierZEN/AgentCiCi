---
kind: feature-spec
feature_id: FEAT-034
title: Customer Insight AI App
status: draft
owner_role: fullstack-product-ai-app
task_ids: TASK-098,TASK-099,TASK-100,TASK-101,TASK-102
related_decisions: FEAT-025, FEAT-028, FEAT-033
related_issues: none
updated_at: 2026-05-15T07:10:00Z
updated_by: ai
---

# FEAT-034 - 客户洞察 AI 应用

## 背景与目标

用户要求参考 `/Volumes/AISpace/codehouse/cc-customer-insight` 项目，在 AgentCiCi 的 AI 应用列表中新增一个“客户洞察”AI 应用。参考项目是 Vue + Element Plus 的客户洞察工作台，核心能力包括客户基础信息、行业宏观环境、行业洞察、股权及组织架构、权力地图、铁三角、客户产出、市场空间、战略/KPI/未来变化、决策链、竞争分析、关系开拓、一客一策和报告预览。

本功能的目标不是把参考项目整页 iframe 或机械搬迁，而是把它产品化为 AgentCiCi 内的原生 AI 应用：

- 在 `/` 助手工作台的“AI应用”列表中新增“客户洞察”。
- 保留参考项目“客户洞察智能体”的业务模块和分析框架，但改成符合当前项目 `鎏金账房` 产品风格的 React 工作区。
- 将参考项目前端直连模型、硬编码 key、mock 数据和本地 prompt 的实现，迁移为 AgentCiCi 后端托管能力。
- 模型路由、模型厂商凭证、标准技能、CloudCC CRM 查询工具、知识库、运行 trace、租户权限和审计都复用 CiCi 现有体系。
- 首版形成可保存、可继续编辑、可逐段 AI 生成、可汇总为“一客一策”报告的客户洞察应用。

## 范围

### In Scope

- AI 应用列表新增 `customer-insight` 应用卡片，名称为“客户洞察”。
- 新增客户洞察主页面，嵌入现有 `aiApps` 工作区右侧主区域。
- 客户洞察页面提供客户搜索/选择、业务来源刷新、洞察任务状态、左侧模块导航、右侧模块编辑和报告预览。
- 复刻参考项目的信息架构，并补入当前系统业务事实，按当前项目压缩为 6 组 product 文本 tab/导航：
  - 客户画像：客户基本信息、舆情、股权及组织架构、权力地图、铁三角。
  - 行业与空间：行业宏观环境、子行业/行业洞察、客户市场空间。
  - 战略与决策：客户战略、KPI、战略变化、决策链、决策流程。
  - 竞争与关系：供应商竞争格局、客户眼中的供应商、关系对比、关系开拓、竞争对手策略、伙伴合作。
  - 业务闭环：签约合同、订单与履约、客户服务、续约与增购。
  - 一客一策：总目标、一客一策汇总、客户洞察报告。
- 后端新增客户洞察服务，支持创建/查询洞察项目、保存模块草稿、按模块 AI 生成、整案分析、报告汇总。
- 新增平台标准技能 `ai-customer-insight-analyst`，显式约束商业分析口径、输出格式、证据边界和人工确认规则。
- AI 生成必须通过 `ModelRouterService`、`ModelProviderService`、`AliyunBailianClient` 的组织模型配置调用，不允许前端保存或提交模型 API key。
- 可选使用现有 CloudCC 工具读取客户、联系人、商机等只读数据；若未配置 CloudCC，则允许用户手填或导入基础事实后继续生成。
- 客户洞察必须可承接当前系统数据摘要，包括已签合同、订单履约、客户服务/工单和续约增购信号；首版允许以 source snapshot 摘要和人工补充方式进入分析，不要求直接写回业务系统。
- 生成过程写入 AgentCiCi 运行 trace，记录模型、技能、输入摘要、模块、耗时和错误状态。
- UI 遵守 `DESIGN.md` 的 `Product UI Scale`、Product Tabs、无 box-in-box、无伪按钮 hover/selected 背景等规则。

### Out Of Scope

- 不在第一版实现把分析结果自动写回 CRM。
- 不在第一版实现公开互联网实时搜索，除非组织已启用现有 web-search/Tavily 能力，并在模块中显式选择“补充公开资料”。
- 不复制参考项目的 Element Plus 组件库、蓝色按钮体系、调试控制台或前端模型代理。
- 不新增独立营销页或新的路由级视觉语言。
- 不在第一版实现多人协同编辑、报告导出 PPT/Word/PDF。
- 不把客户洞察作为“嵌入式智能应用”对外 SDK 暴露；本轮是 AgentCiCi 内部 AI 应用。

## 参考项目分析

### 可复用的产品能力

- `ReportWorkbench.vue` 提供了完整工作台骨架：顶部客户搜索、模型选择、智能分析按钮、左侧菜单、主内容区、报告预览。
- `SidebarMenu.vue` 的模块分组清晰，可作为 AgentCiCi 内客户洞察导航的业务框架。
- `AI_FEATURES.md` 列出了 9 个已具备 AI 生成价值的模块：行业宏观环境、客户战略、KPI、战略变化、竞争对手策略、关系开拓、一客一策、客户基本信息、决策链联系人丰富。
- `aiService.js` 中的 prompt 函数可作为后端模块 prompt 的初稿，但需要去除前端 key、直连 URL 和浏览器解析 JSON 的脆弱逻辑。
- `mockData.js` 可作为前端空态/本地 demo 数据参考，但正式实现不应将 mock 当作业务事实源。

### 必须改造的点

- 参考项目通过浏览器调用模型，并包含硬编码 API key；AgentCiCi 必须全部迁移到后端模型配置。
- 参考项目的 `selectedModel` 下拉与 CiCi 的组织级模型路由冲突；新应用不提供页面级模型 key 选择，最多展示“使用组织 chat 模型”状态。
- 参考项目缺少租户隔离、用户权限、持久化、trace、审计和工具调用治理；新实现必须补齐。
- 参考项目布局较像独立业务系统，AgentCiCi 中需要嵌入 AI 应用双栏，不再使用全屏顶栏 + 250px Element aside。
- 参考项目大量大卡片和 Element 默认 hover/按钮样式不符合 `鎏金账房`，需要改成紧凑面板、文本 tab、1px 分隔线和统一金色主操作。

## 用户场景

- 销售负责人输入或选择一个客户，快速生成客户画像、行业空间、关键人 KPI、竞争格局和下一步关系开拓策略。
- 客户经理打开客户洞察项目，逐个模块补充事实、点击 AI 生成建议，再人工编辑保存。
- 售前/交付团队根据决策链、机会沙盘和客户战略，形成面向内部协作的一客一策报告。
- CloudCC 已配置时，用户先从 CRM 选择客户，再由系统只读拉取客户、联系人、商机和历史活动作为上下文。
- CloudCC 未配置时，用户仍可通过手填客户名称、行业、关键联系人、机会背景等字段使用 AI 分析。

## 现状与约束

### Verified Facts

- 当前助手工作台已经有 `aiApps` 工作区，第一项内置应用是“AI 听记”，实现文件为 `frontend/src/assistant/AssistantApp.tsx` 和 `frontend/src/assistant/cici-ui.css`。
- 当前会议纪要能力已经展示了 AI 应用与后端标准技能融合模式：`MeetingMinutesService` 通过 `SkillPromptAssembler`、组织模型路由和模型厂商配置生成纪要。
- 当前后端已有 CloudCC 只读工具：`cloudcc_getStandardObjects`、`cloudcc_getCustomObjects`、`cloudcc_getObjectFields`、`cloudcc_pageQuery`。
- 当前后端已有运行 trace、技能治理、工具白名单、模型路由和组织级模型厂商配置。

### Constraints

- 不能在前端引入模型 API key 或让页面直接调用 DashScope/DeepSeek。
- 产品页必须使用现有 `鎏金账房` 视觉语言，不引入 Element Plus。
- 客户洞察涉及 CRM 客户、联系人、商机等敏感数据，trace 和错误信息只记录摘要，不记录完整 prompt 明文或凭证。
- AI 结果必须可编辑、可保存，并明确标注“AI 生成待人工确认”，不能直接作为已验证事实。
- 第一版优先只读查询和内容生成，写回 CRM 需要后续单独规格。

## 方案设计

### 1. 产品入口

在 `AI_APPLICATIONS` 中新增：

```ts
{
  code: "customer-insight",
  name: "客户洞察",
  shortName: "客",
  status: "内置",
  summary: "客户画像、行业空间、竞争关系和一客一策分析。",
  description: "面向销售与售前团队，汇总 CRM 事实、人工补充和 AI 分析，形成可编辑的客户洞察报告。",
  meta: "CRM 洞察 · 一客一策"
}
```

AI 应用列表保持左侧紧凑卡片。右侧主区域根据 `activeAiAppCode` 分支渲染：`meeting-minutes` 继续使用 `MeetingMinutesPanel`；`customer-insight` 渲染新组件 `CustomerInsightAppPanel`。

### 2. 前端页面结构

建议新增：

```text
frontend/src/assistant/customer-insight/
├── CustomerInsightAppPanel.tsx
├── CustomerInsightModuleNav.tsx
├── CustomerInsightSectionEditor.tsx
├── CustomerInsightReportPreview.tsx
├── customerInsightApi.ts
├── customerInsightTypes.ts
└── customerInsightSections.ts
```

布局：

- 顶部为紧凑操作条：客户搜索/选择、行业、数据来源状态、保存、生成当前模块、整案分析。
- 中间为三栏结构：
  - 左侧 188-220px 模块导航，使用文本分组 + 1px 分隔线，不用 Element 菜单壳。
  - 中间主编辑区，显示当前模块字段、AI 生成结果、人工编辑区。
  - 右侧 260-320px 洞察摘要栏，显示事实完整度、已生成模块、风险/待确认项、最近 trace。
- 窄屏改为顶部横向模块 tab + 单列编辑，摘要栏折到模块下方。
- 模块内禁止嵌套卡片：字段组使用标题、说明文字、表格和分隔线；行操作使用裸文本或三点菜单。
- 所有弹出确认或编辑器遵守 modal 规则：遮罩、`role="dialog"`、`aria-modal="true"`、标题、统一 footer。

### 3. 数据模型

新增迁移建议为 `V51__customer_insight_ai_app.sql`：

```text
customer_insight_project
- id BIGSERIAL
- public_id VARCHAR(64) UNIQUE
- org_id VARCHAR(64)
- owner_user_id VARCHAR(64)
- customer_name VARCHAR(256)
- customer_external_id VARCHAR(128) NULL
- customer_object_api_name VARCHAR(128) NULL
- industry VARCHAR(128) NULL
- source_type VARCHAR(32)       -- MANUAL / CLOUDCC / MIXED
- status VARCHAR(32)            -- DRAFT / ANALYZING / READY / ERROR / ARCHIVED
- completeness_score INTEGER
- latest_summary TEXT
- created_at / updated_at

customer_insight_section
- id BIGSERIAL
- project_id BIGINT
- section_code VARCHAR(64)
- section_group VARCHAR(64)
- title VARCHAR(128)
- input_json TEXT
- output_json TEXT
- markdown TEXT
- status VARCHAR(32)            -- EMPTY / DRAFT / GENERATING / GENERATED / ERROR
- ai_generated BOOLEAN
- model_provider VARCHAR(64) NULL
- model_name VARCHAR(128) NULL
- skill_code VARCHAR(64) NULL
- trace_id VARCHAR(64) NULL
- error_message VARCHAR(1000) NULL
- updated_at

customer_insight_source_snapshot
- id BIGSERIAL
- project_id BIGINT
- source_type VARCHAR(32)       -- CLOUDCC_CUSTOMER / CLOUDCC_CONTACT / CLOUDCC_OPPORTUNITY / MANUAL / WEB
- source_key VARCHAR(256)
- source_label VARCHAR(256)
- snapshot_json TEXT
- collected_at

customer_insight_generation_job
- id BIGSERIAL
- project_id BIGINT
- section_code VARCHAR(64) NULL
- job_type VARCHAR(32)          -- SECTION / FULL_REPORT / REFRESH_SOURCES
- status VARCHAR(32)
- request_summary TEXT
- result_summary TEXT
- trace_id VARCHAR(64) NULL
- created_at / completed_at
```

第一版可用 `TEXT` 存 JSON，保持与现有代码风格简单一致；若后续需要结构化查询，再迁移为 `jsonb` 或拆表。

### 4. 后端 API

新增包：

```text
backend/src/main/java/com/codehouse/ciciassistant/customerinsight/
├── api/CustomerInsightController.java
├── domain/*
└── service/CustomerInsightService.java
```

接口草案：

```http
GET    /ai/customer-insights/projects
POST   /ai/customer-insights/projects
GET    /ai/customer-insights/projects/{projectId}
PATCH  /ai/customer-insights/projects/{projectId}
DELETE /ai/customer-insights/projects/{projectId}

POST   /ai/customer-insights/projects/{projectId}/refresh-sources
PUT    /ai/customer-insights/projects/{projectId}/sections/{sectionCode}
POST   /ai/customer-insights/projects/{projectId}/sections/{sectionCode}/generate
POST   /ai/customer-insights/projects/{projectId}/generate-full
GET    /ai/customer-insights/projects/{projectId}/jobs/{jobId}
```

响应统一使用 `ApiResponse`，租户来自 `TenantContext.requireOrgId()`，用户来自 `TenantContext.getUserId()`。所有查询必须按 `org_id` 过滤。

### 5. AI 能力融合

新增标准技能 `ai-customer-insight-analyst`：

- `name`: 客户洞察分析师
- `riskLevel`: MEDIUM
- `toolWhitelist`: `cloudcc_getStandardObjects,cloudcc_getCustomObjects,cloudcc_getObjectFields,cloudcc_pageQuery`
- `promptFragment`: 要求先区分事实、推断和待确认；输出必须结构化；不得编造客户收入、组织架构、联系人立场、竞争信息；缺事实时写“待补充”。
- `handoffRule`: 涉及商务承诺、价格策略、竞品指控、客户高管个人判断、写回 CRM 时必须人工确认。
- `outputContract`: 每个模块输出 `summary`、`evidence`、`risks`、`nextActions` 和模块专属字段；整案报告输出客户画像、行业机会、决策链、竞争态势、一客一策和待确认清单。

后端生成流程：

1. `CustomerInsightService` 读取项目、模块输入、已有 source snapshots。
2. 如果 CloudCC 已配置并用户选择刷新，先通过服务端只读工具或专用 CloudCC service 拉取客户/联系人/商机候选，保存 snapshot。
3. 组装模块 prompt：模块定义 + 人工输入 + CRM snapshot 摘要 + 已生成相关模块摘要。
4. 通过 `SkillPromptAssembler` 注入 `ai-customer-insight-analyst`。
5. 通过 `ModelRouterService.route(orgId, "chat")` 和 `ModelProviderService.credentialsForProvider(...)` 获取组织模型和凭证。
6. 调用模型，要求返回 JSON + Markdown 摘要。服务端解析 JSON，失败时保留 Markdown 并标记 `GENERATED_WITH_PARSE_WARNING` 或 `ERROR`。
7. 写入 section、generation job 和 trace。

### 6. 模块定义

用后端枚举或资源文件定义模块，前端只消费 API 返回的 section catalog，避免前后端 prompt 漂移。

首版模块：

| group | sectionCode | 来源参考 |
|---|---|---|
| customer_profile | customer_info | `CustomerInfoCard` |
| customer_profile | customer_sentiment | `CustomerSentiment` |
| customer_profile | equity_org | `EquityAndOrgStructure` |
| customer_profile | power_map | `CustomerPowerMap` |
| customer_profile | iron_triangle | `IronTriangleConfig` |
| industry_space | macro_environment | `IndustryMacroEnvironment` |
| industry_space | sub_industry_sandbox | `SubIndustrySandbox` |
| industry_space | market_space | `CustomerMarketSpace` |
| strategy_decision | strategy_review | `CustomerStrategyReview` |
| strategy_decision | kpi_analysis | `CustomerKPIAnalysis` |
| strategy_decision | strategic_changes | `CustomerStrategicChanges` |
| strategy_decision | decision_chain | `DecisionChainTable` |
| strategy_decision | decision_process | `CustomerDecisionProcess` |
| competition_relation | supplier_landscape | `SupplierCompetitiveLandscape` |
| competition_relation | supplier_in_customer_eyes | `SupplierInCustomerEyes` |
| competition_relation | relationship_comparison | `CustomerRelationshipComparison` |
| competition_relation | relationship_development | `CustomerRelationshipDevelopment` |
| competition_relation | competitor_strategy | `CompetitorStrategyForecast` |
| competition_relation | partner_cooperation | `PartnerCooperation` |
| business_service | signed_contracts | 当前系统签约合同/合同订单数据 |
| business_service | order_fulfillment | 当前系统订单、交付、履约和上线数据 |
| business_service | customer_service | 当前系统客户服务、工单、咨询和投诉数据 |
| business_service | renewal_expansion | 合同、订单、服务体验综合推断 |
| one_customer_strategy | overall_goals | `OverallGoals` |
| one_customer_strategy | one_customer_one_strategy | `OneCustomerOneStrategy` |
| one_customer_strategy | report_preview | `ReportPreview` |

### 7. CloudCC 数据策略

首版采用“可用则增强，不可用也能运行”：

- 已配置 CloudCC：用户可搜索客户并绑定 `customer_external_id`，系统拉取客户、联系人、商机、最近活动等只读摘要。
- 已接入当前系统业务数据：服务端生成 `BUSINESS_CONTRACT`、`BUSINESS_ORDER`、`CUSTOMER_SERVICE` 等 source snapshot，供“业务闭环”模块和整案报告使用。
- 未配置 CloudCC：页面显示配置提示，但允许手动输入客户名称、行业、关键联系人、当前机会、竞争对手等信息。
- 未接入合同/订单/客服数据：页面仍保留业务闭环模块，source snapshot 标记待接入，AI 输出必须把合同金额、订单状态、服务结论等列为“待补充”，不得编造。
- 所有 CRM 查询均服务端执行，凭证不进 prompt、不进前端、不进 trace。
- 模型只接收经过字段白名单和长度裁剪后的摘要，不接收完整原始 CRM JSON。

### 8. Trace 与治理

- 每次模块生成创建 `AgentRunTrace`，metadata 包含 `appCode=customer-insight`、`projectId`、`sectionCode`、`skillCode=ai-customer-insight-analyst`。
- trace detail 中记录输入摘要、source snapshot 数量、模型提供商、模型名、耗时、解析状态。
- 若调用 CloudCC 工具，保留现有工具调用 trace 和 allowlist 审计。
- 后续可在管理端观测与运维中按 `appCode` 筛选。

## 接口与数据影响

- 新增 4 张客户洞察表，不影响现有聊天、会议纪要和嵌入式应用表。
- 新增后端 controller/service/repository，不改动通用 `/ai/chat` 协议。
- 新增 `ai-customer-insight-analyst` 平台标准技能，可通过 `SkillDefinitionService` 常量或后续文件型内置技能同步。
- 前端新增客户洞察组件目录，`AssistantApp.tsx` 只增加应用元数据和渲染分支。
- `DESIGN.md` / `DESIGN.json` 无需变更，因为视觉语言不新增规则，只复用现有 `鎏金账房`。

## 任务拆分

### TASK-098 Customer insight AI app design

- status: draft
- owner_role: product-architecture
- scope: 完成本规格文档、参考项目能力映射、实现拆分。

### TASK-099 Backend data model, API, and standard skill

- owner_role: backend-ai-app
- depends_on: TASK-098
- scope: Flyway、实体、repository、controller、`CustomerInsightService`、`ai-customer-insight-analyst` 标准技能、基础单元/集成测试。

### TASK-100 Frontend customer insight workspace

- owner_role: frontend-product-assistant
- depends_on: TASK-099 API contract
- scope: `CustomerInsightAppPanel`、模块导航、编辑器、报告预览、AI 应用列表接入和移动端布局。

### TASK-101 CRM source refresh and generation trace

- owner_role: backend-cloudcc-ai
- depends_on: TASK-099
- scope: CloudCC 只读 source snapshot、模块生成 trace、错误降级、输入摘要脱敏。

### TASK-102 Visual QA and end-to-end verification

- owner_role: fullstack-product-qa
- depends_on: TASK-100,TASK-101
- scope: 前后端 build/test、桌面/移动截图、客户洞察模块生成 smoke、无 CloudCC 降级场景、CloudCC 已配置场景。

## 验收标准

- “AI应用”列表中出现“客户洞察”，点击后右侧显示客户洞察工作区。
- 用户可创建客户洞察项目，填写客户名/行业并保存。
- 用户可选择至少 3 个核心模块生成 AI 分析：客户基本信息、行业宏观环境、一客一策汇总。
- 用户可选择业务闭环模块生成 AI 分析：签约合同、订单与履约、客户服务、续约与增购。
- 生成能力通过后端组织模型配置调用，前端 bundle 中没有模型 key 或第三方模型 URL。
- 若组织 CloudCC 未配置，页面清晰提示并允许手填继续；若已配置，可从服务端刷新 CRM source snapshot。
- 若当前系统未接入合同/订单/客服明细，刷新来源仍返回待接入摘要，业务闭环模块提示补充事实，不阻塞整体洞察。
- 每次生成记录 section 状态、模型信息、技能 code、trace id 和错误状态。
- UI 桌面和 390px 移动视口无横向溢出、文字遮挡、内层卡片堆叠或产品 tab 伪按钮样式。
- 验证命令至少包括：
  - `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=CustomerInsightIntegrationTest test`
  - `backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`
  - `frontend npm run build`
  - targeted `git diff --check`
  - in-app Browser 或 Playwright 桌面/移动截图复核。

## 风险与回滚

- **模型输出 JSON 不稳定**：服务端保留 Markdown 降级，并在 UI 标记“格式待整理”；模块 schema 逐步收敛。
- **CRM 字段差异**：先使用对象/字段元数据发现和字段白名单，不假设所有组织字段一致。
- **页面过重**：首版按模块懒加载，列表只显示项目摘要，报告预览按需生成。
- **AI 编造风险**：技能强制区分事实/推断/待确认；UI 对 AI 生成块标记“待人工确认”。
- **CloudCC 不可用**：允许手填模式，不阻塞核心分析。
- **回滚方式**：前端移除 `customer-insight` 应用卡片即可隐藏入口；后端新增表保留不被旧代码访问；标准技能可设为 disabled。

## 实现进展

- 当前状态：implementation in progress。
- 已完成项：已分析参考项目模块、当前 FEAT-033 AI 应用入口、后端标准技能/模型/工具融合路径。
- 2026-05-15T07:10:00Z：补入“业务闭环”模块组，覆盖签约合同、订单与履约、客户服务、续约与增购；后端 source refresh 增加合同/订单/客服摘要占位，标准技能和前端文案同步强调合同订单与客户服务事实边界。
- 未完成项：真实业务系统字段级连接器、截图 QA 和本轮完整测试。

## 交接说明

- 下一位接手者先看本规格，再看 `docs/specs/FEAT-033-assistant-ai-apps-workspace.md` 和参考项目 `cc-customer-insight/src/views/ReportWorkbench.vue`、`src/components/SidebarMenu.vue`、`AI_FEATURES.md`。
- 实现时优先完成 `TASK-099` 的 API contract，再做 `TASK-100` 前端；避免前端先复制 mock 数据后再反向适配后端。
- 继续推进前无需修改 `DESIGN.md`，除非实现过程中引入新的跨页面组件规则或视觉 token。
