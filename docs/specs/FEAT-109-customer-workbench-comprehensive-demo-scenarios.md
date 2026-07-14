---
kind: feature-spec
feature_id: FEAT-109
title: 客户互动工作台全场景演示数据
status: approved
owner_role: project-manager
task_ids: TASK-203
related_decisions: FEAT-081,FEAT-082,FEAT-103,FEAT-104,FEAT-105
related_issues: none
updated_at: 2026-07-14T06:28:42Z
updated_by: MANAGER-001
---

# FEAT-109 - 客户互动工作台全场景演示数据

## 背景与目标

现有 TASK-172 只建设了 10 个基础客户以及 Account、Contact、Lead、Opportunity、Task、Event 记录。随着工作台新增老客户经营、服务个案、合同续约、关系地图、互动档案、动态记忆、可解释评分和互动驱动动作，原数据已不能覆盖完整演示链路；且核心记录全部归 SalesB 所有，Owen/SalesA 无记录可见性。

本次建设 V2 演示数据集：以 CloudCC CRM 标准对象为业务事实源，以 AgentCiCi 互动档案、记忆、信号、评分快照和动作表补充 AI 侧事实，形成可重复初始化、可按场景讲解、可由 Owen/SalesA 实际验收的完整环境。

## 身份与权限约束

- AgentCiCi 演示组织：`org2sva14i4udjmi2t4s`。
- CloudCC CRM 演示组织：`org0720f814430017229`。
- Owen/SalesA 必须能看见全部 V2 核心演示客户及关联记录。
- CCAdmin/SalesB 继续用于数据管理员验收，但不得成为唯一可见账号。
- 优先为 V2 记录显式设置 SalesA 所有人；若关联对象不接受显式 owner，则使用 CloudCC role/profile/permission/sharingRule 正规权限路径扩展可见性。
- 不把 AgentCiCi 账号绑定到另一人的 CloudCC 身份，不共享可复用凭据。

## 场景矩阵

### 新客户推进

| 场景 | CRM 事实 | 工作台命中 |
|---|---|---|
| 重点推进 | 高阶段商机、联系人、下一步、未完成任务 | `focus`、推进分 ≥ 70 |
| 待跟进 | 未完成且未逾期任务 | `follow`、下一步任务 |
| 预算确认 | 商机金额、签约日期、预算会议 | 推进信号、时间线 |
| 竞品比较 | 商机描述与事件明确竞品/TCO | 风险、AI 助理总结 |
| 决策链不清 | 缺少采购/决策联系人 | 关系缺口、补联系人动作 |
| 待约演示 | 早期商机与演示任务 | 新客阶段、下一步行动 |
| 商机缺失 | 有客户和联系人但无商机 | `OPPORTUNITY_GAP` |
| 联系人缺失 | 有客户/商机但无联系人 | `RELATION_GAP` |
| 下一步缺失 | 开放商机 `xyb` 为空 | `NEXT_STEP_GAP` |
| 逾期跟进 | 未完成任务已过期 | `risk`、`OVERDUE_TASK` |

### 老客户经营

| 场景 | CRM 事实 | 工作台命中 |
|---|---|---|
| 30 天内续约 | 有效合同即将到期 | `renewal`、高优续约信号 |
| 31–90 天续约 | 合同处于中期续约窗口 | `renewal`、中优信号 |
| 增购机会 | 存量合同 + 增购商机 | `expansion`、增购指标 |
| 服务高风险 | 未关闭高优先级 Case | `service`、服务问题页签 |
| 满意度下降 | 客户反馈/邮件负面互动 | 动态健康/风险负信号 |
| 关键人变化 | 新旧联系人和关系角色变化 | 关系地图、关系覆盖风险 |
| 价值稳定 | 有效合同、已赢单、无开放 Case | 价值兑现、稳定信号 |
| 沉默客户 | 超过 30 天无联系且无近期互动 | 健康下降、互动缺口 |

### 互动与 AI 场景

- 互动来源覆盖 `WECHAT / PHONE / MEETING / EMAIL / CUSTOMER_FEEDBACK / CRM_TASK / CRM_EVENT`。
- 至少 8 个客户拥有可打开的确认互动档案，包含确认稿、结构化分析、证据和原始批次关联。
- 记忆类型覆盖 `FACT / NEED / RISK / OPPORTUNITY / COMMITMENT / NEXT_ACTION / PENDING_QUESTION`，并包含 `ACTIVE / RESOLVED / SUPERSEDED` 生命周期样例。
- 动态评分覆盖健康、增购、续约、关系、风险五维，至少包含正向、负向、待确认、已解决、已过期信号。
- 互动驱动动作覆盖 `CREATE_TASK / CREATE_OPPORTUNITY / UPDATE_OPPORTUNITY`；每条必须有来源事件、批次、原文证据、业务键、有效期和人工确认状态。
- 至少保留一条低置信度/纯信息互动不生成动作的反例。

## 数据规模

- 16 个核心客户：8 个新客户、8 个老客户；可复用 TASK-172 的 10 个客户并新增/重塑 6 个。
- 24–32 位联系人，覆盖技术、采购、决策、业务、服务和财务角色。
- 16–24 个业务机会，覆盖早期、中期、高阶段、赢单、增购和缺下一步。
- 24–32 个任务，覆盖待办、逾期、已完成、不同优先级。
- 32–48 个 CRM Event，形成最近 90 天内连续时间线。
- 6–10 个合同，覆盖 15/45/80/180 天到期与已完成历史合同。
- 8–12 个 Case，覆盖新建、处理中、等待客户、已关闭和不同优先级。
- 24 条以上正式互动档案、24 条以上客户记忆、20 条以上动态信号、12 条以上证据驱动经营动作。

## 实现方式

1. 使用 `cc-customization-expert-msapi` 内置 CLI 读取标准目录和 OpenAPI 字段契约。
2. 扩展 `scripts/seed-demo-environment.py`，以批次 `TASK-203-DEMO-V2` 幂等创建/复用 CRM 记录。
3. CRM Account 及关联记录显式绑定 SalesA 所有人；创建后以 SalesA 当前用户令牌回读。
4. AgentCiCi 侧只替换本演示组织、V2 稳定 ID 和旧 TASK-172 聚合种子；先备份，再写入互动档案、记忆、动态信号、评分快照和动作。
5. 不执行未限定范围的 CloudCC 删除；旧 V1 CRM 记录优先复用或保留。

## 验收标准

- Owen/SalesA `integration-status.visibleAccounts > 0`，16 个核心客户均可按名称搜索并打开详情。
- 新客四个筛选 `focus/follow/risk/recommendations` 均非零。
- 老客四个筛选 `renewal/health/service/expansion` 均非零。
- Account、Contact、Opportunity、Task、Event、Case、Contract 的场景记录可按统一批次标识查询。
- 每个详情页所需的时间线、信号、建议/动作、服务、价值、续约和关系数据至少有一个代表客户可演示。
- 动态评分解释可看到五维正负信号和证据；互动档案可打开；助手上下文可引用记忆。
- 至少验证一个证据驱动 Task 动作和一个 Opportunity 动作进入待确认状态；不自动写回 CRM。
- 桌面端 AgentCiCi 与 CloudCC 嵌入入口均完成截图和控制台错误检查。
- 执行日志不得输出密码、token、secret、cookie、客户真实隐私或数据库凭据。

## 回滚

- CRM 侧保留 V2 记录并依批次标记识别；若必须清理，另行生成显式删除清单并获得批准。
- AgentCiCi 写入前保存 PostgreSQL 备份；脚本只删除目标组织的 V1/V2 演示聚合记录，失败时恢复备份。
- 权限调整若发生，必须保存 planId/operationId 和变更前快照，并通过 rollback plan 回退。

