---
kind: feature-spec
feature_id: FEAT-082
title: 双环境真实演示数据
status: implemented
owner_role: project-manager
task_ids: TASK-172
related_decisions: FEAT-081
related_issues: none
updated_at: 2026-07-09T23:39:05+08:00
updated_by: MANAGER-001
---

# FEAT-082 - 双环境真实演示数据

## 背景与目标

客户互动工作台当前已经实现 CloudCC CRM 嵌入、SSO 和 AgentCiCi 页面，但业务数据仍主要来自 AgentCiCi 本地演示种子。用户要求打造一套完整演示环境：智能体平台演示环境与其绑定的 CloudCC CRM 组织必须互通，并且两边都有可验证的真实模拟业务数据。

目标是把演示口径从“页面演示数据”提升为“CRM 标准对象真实记录 + AgentCiCi 工作台聚合视图引用同一批 CRM 记录”的闭环。

## 双环境标识

- AgentCiCi 演示组织：`org2sva14i4udjmi2t4s`，页面显示为“智能体平台演示环境”。
- CloudCC CRM 演示组织：`org0720f814430017229`。
- CloudCC 访问域：`https://ap6.lightning.cloudcc.cn`。
- CRM 嵌入入口：`customer_interaction_workbench` / `/app?aiApp=customer-workbench&embed=crm`。

## 范围

### In Scope

- 在 CloudCC CRM 标准对象创建或更新一批真实演示记录：
  - `Account` 客户。
  - `Contact` 联系人。
  - `cloudcclead` 潜在客户。
  - `Opportunity` 业务机会。
  - `Task` 跟进任务。
  - `Event` 会议/互动事件。
  - 可用时扩展 `contract`、`product` 等标准对象作为老客户经营证据。
- 在 AgentCiCi 演示组织中写入对应工作台聚合数据：
  - `customer_workbench_snapshot`。
  - `customer_interaction_event`。
  - `customer_workbench_recommendation`。
- 保证 AgentCiCi 工作台看到的客户、联系人、阶段、风险、建议与 CRM 记录 ID 可互相追溯。
- 保留数据建设脚本和执行证据，便于重复刷新演示环境。

### Out Of Scope

- 不新建 CloudCC 自定义对象来替代标准客户、联系人、线索、商机、任务或活动对象。
- 不改变客户互动工作台 UI 视觉语言。
- 不做移动端专项适配。
- 不把真实客户隐私数据导入演示环境。

## 能力路径

```text
standard-catalog
  -> 标准对象字段确认
  -> OpenAPI 查询/创建/更新标准业务记录
  -> AgentCiCi 工作台本地聚合表写入
  -> CRM 嵌入页和 AgentCiCi 平台入口交叉验证
```

## 数据设计

演示数据使用“真实模拟”原则：组织、联系人、沟通、商机、风险和下一步动作均为虚构，但以真实 CRM 标准对象记录承载。

首批演示故事线：

- 新客户推进：制造业 MES 集成、权限治理、合规审计、销售过程管理。
- 老客户经营：续约风险、增购机会、服务问题闭环、战略客户集团级扩展。
- 主管视角：同一工作台可切换新客户推进队列和老客户经营队列。

## 验收标准

- CloudCC 标准目录扫描确认 `Account`、`Contact`、`cloudcclead`、`Opportunity`、`Task`、`Event` 可用。
- CloudCC CRM 中可查询到带统一演示批次标识的客户、联系人、线索、商机、任务/事件记录。
- AgentCiCi 组织 `org2sva14i4udjmi2t4s` 的 `/customer-workbench/accounts` 返回同一批 CRM account id，而不是 `demo-account-xxx`。
- 从 CRM 嵌入入口进入时，工作台可显示同一批客户队列和详情。
- 执行日志不得输出密码、token、secret、cookie 或可复用凭据。

## 风险与回滚

- CloudCC 标准对象必填字段可能与离线目录不完全一致；脚本必须先小批量创建并捕获原始错误，再补字段。
- 记录创建失败时，不应清空现有演示环境。
- AgentCiCi 本地聚合表刷新前应只删除本演示批次或目标组织工作台旧种子，避免影响其他租户。
- 如需要回滚，可按演示批次标识删除 AgentCiCi 聚合表记录；CloudCC 侧优先保留记录并将其标记为演示批次，避免误删。

## 实现进展

- 2026-07-09：任务建立；已确认两个组织 ID 和 CloudCC 标准对象可用性。
- 2026-07-09：`scripts/seed-demo-environment.py` 已完成真实演示数据建设：
  - CloudCC CRM 批次 `TASK-172-DEMO-V1`：10 个客户、10 个联系人、6 个潜在客户、10 个业务机会、10 个任务、20 个事件。
  - AgentCiCi `org2sva14i4udjmi2t4s`：10 个工作台客户快照、30 条互动事件、20 条 CRM 落地建议，均引用同批 CRM record id。
  - 生产备份：`/opt/cici/backups/20260709-153648-before-task172-demo-data`。
  - `13900009999` 已作为常用演示登录绑定 CloudCC 账号；旧成员绑定已清理，避免一个 CloudCC 用户映射多个 AgentCiCi 成员。
  - 生产 API 验证 `/customer-workbench/accounts` 返回 10 个真实 CRM id 客户且无 `demo-account-xxx`，详情返回 `crmConnection.ready=true`。

## 交接说明

- 先看 `.claw/tasks/TASK-172.md` 获取最新执行状态。
- CloudCC 操作必须使用技能包内置 `cloudcc` CLI 或已验证的 OpenAPI 通道。
- AgentCiCi 生产数据写入必须先确认目标组织 ID 为 `org2sva14i4udjmi2t4s`。
- 后续刷新演示数据时直接运行 `python3 scripts/seed-demo-environment.py`，并确认执行日志不含 token、secret、cookie 或数据库密码。
