---
kind: feature-spec
feature_id: FEAT-084
title: 数据洞察 AI 应用
status: in_progress
owner_role: project-manager
task_ids: TASK-174
related_decisions: FEAT-033, FEAT-034, FEAT-082, FEAT-083
related_issues: none
updated_at: 2026-07-10T09:30:00+08:00
updated_by: MANAGER-001
---

# FEAT-084 - 数据洞察 AI 应用

## 背景与目标

用户要求在智能体平台前端 AI 应用列表中新增“数据洞察”AI 应用，重点洞察 CRM 系统中的潜在客户、商机、客户、合同订单和销售业绩，并以精细、细致、美观的仪表板图表动态展示。智能体平台演示环境组织需要与绑定 CloudCC CRM 组织保持真实模拟数据互通；其他组织没有数据时允许 Mock 假数据展示。

本期基于既有 `customer-insight` 能力升级，不重复新建同义实现：

- 前端 AI 应用入口名称调整为“数据洞察”。
- 保留客户洞察的逐段 AI 生成与报告能力。
- 新增 CRM 经营数据仪表板，默认展示潜客、商机、客户健康、合同订单、销售业绩、风险与行动建议。
- 演示组织优先使用 `customer_workbench_*` 聚合表中已经绑定真实 CloudCC CRM ID 的演示数据，并在 source snapshot 中补齐合同、订单、业绩等模拟经营事实。
- 无数据组织返回明确 `MOCK` 数据源，保障产品首屏完整可看，但 UI 标注为演示样例。

## 已引用平台事实

CloudCC 技能与只读扫描已确认：

- `platform/overview introduction`：CloudCC PaaS 以 CRM 业务对象为核心，覆盖低代码对象、字段、权限、页面、报表和高代码扩展。
- `platform/capabilityMap introduction`：本需求命中 `standard-catalog -> object/fields -> report/dashboard/view -> pagecomponent/custom app` 路径。
- `platform/standardCapabilities introduction`：客户、联系人、潜在客户、商机、合同、订单、产品、任务、事件等应优先复用标准对象。
- `methodology/blueprint devguide`：需求按业务范围、对象模型、权限、集成、阶段计划拆解。
- `cloudcc scan msapi . standard-catalog`：目标 CRM 组织存在 `Account`、`Contact`、`cloudcclead`、`Opportunity`、`contract`、`cloudccorder`、`product`、`Task`、`Event`、`opportunitypdt` 等标准业务对象。

## 能力路径

```text
standard-catalog
  -> 标准 CRM 对象复用判断
  -> AgentCiCi 演示组织本地聚合读模型
  -> CustomerInsight/DataInsight 后端 dashboard summary API
  -> AI 应用列表入口
  -> 数据洞察仪表板 UI
  -> 桌面端截图、交互和发布验收
```

## 全局对象地图

| 业务域 | CloudCC 标准对象 | AgentCiCi 本地承载 | 本期处理 |
|---|---|---|---|
| 客户 | `Account` | `customer_workbench_snapshot` | 优先读取真实 CRM id 快照；无数据 Mock |
| 联系人 | `Contact` | snapshot JSON `crmContactId` | 作为客户关系事实展示 |
| 潜在客户 | `cloudcclead` | dashboard 聚合事实 | 演示组织从 TASK-172 批次形成指标 |
| 商机 | `Opportunity` | snapshot JSON `crmOpportunityId`、recommendation payload | 统计阶段、金额、推进风险 |
| 合同 | `contract` / `ServiceContract` | source snapshot / dashboard mock | 本期形成模拟经营事实，后续可接真实 OpenAPI |
| 订单 | `cloudccorder` / `cloudccorderitem` | source snapshot / dashboard mock | 本期形成模拟经营事实，后续可接真实 OpenAPI |
| 销售活动 | `Task`、`Event` | `customer_interaction_event` | 统计互动、跟进、风险和行动 |
| 行动建议 | `Task`、`Opportunity` 写回候选 | `customer_workbench_recommendation` | 只展示建议和状态，不自动写回 |

## 对象关系矩阵

| 主对象 | 关系对象 | 关系字段/证据 | 用途 |
|---|---|---|---|
| Account | Contact | `crmContactId` 或 CloudCC 联系人引用 | 客户关键联系人覆盖 |
| Account | Opportunity | `crmOpportunityId` 或商机客户字段 | 管道金额、阶段、赢单概率 |
| Account | contract | 客户引用、合同周期、金额 | 续约风险和合同价值 |
| Account | cloudccorder | 客户引用、订单金额、履约状态 | 收入兑现和交付风险 |
| Account | Task/Event | `relateid` / 本地互动聚合 | 最近触达、待办和客户情绪 |

## 字段字典

| 指标字段 | 来源 | 类型 | 说明 |
|---|---|---|---|
| `dashboard.sourceMode` | 后端聚合 | 枚举 | `REAL_CRM_DEMO` / `REAL_AGGREGATE` / `MOCK` |
| `summary.totalCustomers` | snapshot | 数字 | 客户总量 |
| `summary.totalLeads` | CloudCC/TASK-172 口径 | 数字 | 潜在客户量 |
| `summary.openOpportunities` | snapshot/recommendation | 数字 | 活跃商机量 |
| `summary.pipelineAmount` | snapshot mock facts | 金额 | 管道金额 |
| `summary.contractAmount` | snapshot mock facts | 金额 | 合同金额 |
| `summary.orderAmount` | snapshot mock facts | 金额 | 订单金额 |
| `summary.winRate` | 聚合推算 | 百分比 | 成交质量指标 |
| `funnel[]` | dashboard | 数组 | 潜客到签约漏斗 |
| `segments[]` | snapshot.segment | 数组 | 新客户、老客户、风险、战略客户分布 |
| `trend[]` | event/revenue mock facts | 数组 | 近六个月业绩趋势 |
| `accounts[]` | snapshot | 数组 | 重点客户经营面板 |
| `risks[]` | snapshot/recommendation | 数组 | 风险与行动建议 |

## 状态机矩阵

| 状态 | 条件 | UI 表达 |
|---|---|---|
| `loading` | 正在请求 dashboard | 骨架行和弱提示 |
| `real` | 有聚合数据且组织为演示或真实业务组织 | 标注“CRM 聚合数据” |
| `mock` | 无聚合数据 | 标注“演示样例”，图表仍完整 |
| `error` | API 失败 | 内联错误，保留可读空态 |
| `empty project` | 无客户洞察项目 | 仪表盘仍显示组织级数据，并提示可新建分析项目 |

## 全局选项列表清单

| 选项 | 类型 | 值 |
|---|---|---|
| 数据源模式 | 普通后端枚举 | `REAL_CRM_DEMO`、`REAL_AGGREGATE`、`MOCK` |
| 客户分群 | 普通后端枚举 | `NEW`、`EXISTING`、`RISK`、`STRATEGIC` |
| 风险等级 | 普通后端枚举 | `LOW`、`MEDIUM`、`HIGH` |
| 商机阶段 | 复用 CloudCC 标准字段值 | 以目标租户 `Opportunity.jieduan` 为准 |

## UI 设计 Brief

- Register：`product`。
- 目标用户：销售负责人、客户经理、售前和演示人员。
- 物理场景：销售负责人在白天办公室的大屏桌面端快速扫 CRM 经营态势，并能继续下钻到单客户 AI 洞察。
- 视觉方向：继承 `鎏金账房`，暖象牙底、墨色文字、紧凑密度、香槟金结构线；仪表板使用 restrained + full-palette data-viz，颜色只服务状态和图表。
- 范围：桌面端生产就绪；默认不新增移动端专项适配。
- 关键 UI：指标条、漏斗、分群分布、销售趋势、重点客户列表、风险建议、客户洞察编辑区。
- 交互：刷新业务来源、生成模块、整案汇总、新建项目、切换模块；图表不做花哨动画，hover/focus 状态清晰。
- Anti-goals：不做营销 hero、不做深色命令中心、不做装饰性渐变文字、不做无差别卡片宫格、不自动写回 CRM。

## 后端方案

- 在 `CustomerInsightService` 新增 `dashboard(orgId, userId)`，复用客户工作台聚合仓储读取真实快照、互动和建议。
- `CustomerInsightController` 新增 `GET /ai/customer-insights/dashboard`。
- 仪表盘数据有三层来源：
  - 演示组织真实聚合数据：`org2sva14i4udjmi2t4s`，sourceMode=`REAL_CRM_DEMO`。
  - 其他有本地聚合数据的组织：sourceMode=`REAL_AGGREGATE`。
  - 无聚合数据组织：sourceMode=`MOCK`。
- 本期不新增 MSAPI 写入计划；不创建 CloudCC 自定义对象，不改低代码元数据。

## 验收标准

- AI 应用列表出现“数据洞察”入口，进入后首屏直接看到 CRM 仪表板。
- 演示组织有真实 CRM id 聚合数据时，仪表板显示真实客户、商机、互动和建议统计。
- 无数据组织能看到 Mock 样例，并明确标注为演示样例。
- 客户洞察逐段生成能力不退化。
- 前端构建通过，后端相关测试通过。
- 桌面端浏览器截图检查无明显 UI 瑕疵：无文字溢出、无重叠、无异常空白、无低质图表。
- 生产发布按 `docs/production-release-runbook.md` 执行，发布前 dry-run，发布后健康和业务 smoke。

## 待确认

- CloudCC 侧合同/订单真实记录的字段必填和生产创建窗口需按目标租户现场字段再做 OpenAPI 写入扩展；本期先用已验证聚合数据和模拟经营事实展示，不直接写合同/订单标准对象。
