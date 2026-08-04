---
kind: feature-spec
feature_id: FEAT-157
title: DEV Autopilot 研发交付评审 Tool
status: in_implementation
owner_role: backend-agent
task_id: TASK-265
updated_at: 2026-08-04T04:10:00Z
updated_by: MANAGER-001
---

# FEAT-157 - DEV Autopilot 研发交付评审 Tool

## 背景与目标

第三方 Coding Agent 已能以受治理开发者 SERVICE 身份从 DEV Autopilot 获取任务。完整闭环还需要产品经理智能体对技术设计和完成申请作出正式决策，并保证决策由产品经理 SERVICE Principal 执行、由 DEV Autopilot 校验状态机、由 Semattice 留存事实与审计。

本功能为 `dev-autopilot-pm` 新增正式内置 Tool `semattice_project_delivery_review`，并把它显式绑定到既有 `semattice-project-delivery-management` Skill。登录 HUMAN 只提供委托、确认和审批上下文，不成为资源操作 actor。

## 范围

- 查询 Tool 同步读取新对象 `dev_delivery_event`，让模型看见待确认设计、阻塞、产物和完成申请。
- 新增评审 Tool，支持 `design` 与 `completion` 两个 Gate，以及 `approve`、`request_changes` 两种决定。
- 评审 Tool 先通过 `AgentServicePrincipalExecutionService` 获取产品经理 SERVICE 的短期 OACT，再调用 DEV Autopilot 的产品经理评审 API。
- DEV Autopilot 是任务状态机唯一编排入口；AgentCiCi 不直接修改 `dev_task` 或创建评审事件。
- 将新 Tool 加入内置目录、运行时 Tool 定义、调度分支、标准 Skill 和数据库显式绑定。

## 非目标

- 不允许开发者 SERVICE 自审设计或完成申请。
- 不允许模型传入租户、用户、Principal、令牌或任意 API 地址。
- 不在 AgentCiCi 建立第二份项目、任务或评审数据库。
- 不绕过 DEV Autopilot 的阻塞项、交付证据和状态转换校验。

## Tool 契约

名称：`semattice_project_delivery_review`

输入：

- `task_id`：DEV Autopilot 任务记录 UUID。
- `gate`：`design` 或 `completion`。
- `decision`：`approve` 或 `request_changes`。
- `summary`：决策摘要，必填且有长度上限。
- `detail`：可选评审意见，长度受限。

服务端固定：

- AgentCiCi company、当前 HUMAN member、当前 agent id。
- 执行 Principal 为该 agent 显式绑定的产品经理 SERVICE。
- OACT scope 为 `runtime.record.read` 与 `runtime.record.create`。
- 目标地址来自受管配置 `app.dev-autopilot.base-url`，Tool 参数不能覆盖。

输出至少包含：实际状态、事件编号、任务编号、Gate、Decision、执行 Principal 类型和显示名。DEV Autopilot 非 2xx、返回体不合法或身份链不完整时失败关闭。

## 安全与审计

- HUMAN 必须是产品经理 SERVICE 的有效 PRIMARY owner 且处于同公司 active member。
- Tool 复用既有安全网关输入/输出检查与平台运行开关。
- 不记录 OACT、client secret、设计正文中的凭据或制品仓库凭据。
- 调用审计记录 SERVICE 委托用途；DEV Autopilot/Semattice 记录实际评审 actor、correlation id 和事件。
- `completion` approve 仍由 DEV Autopilot 强制检查设计已批准、阻塞归零、产物与验收证据完整。

## 验收标准

1. 产品经理智能体运行时定义中可见 query/create/review 三个正式 Tool，Skill 显式包含三者。
2. query 返回 `dev_delivery_event`，可识别设计待确认与完成待验收事件。
3. review 使用产品经理 SERVICE OACT 调用 DEV Autopilot；不使用 HUMAN OACT 直接写入。
4. 非 owner、未绑定 agent、缺 scope、无效参数、目标服务异常全部失败关闭。
5. 数据库迁移对既有 `dev-autopilot-pm` 幂等绑定新 Tool。
6. 定向单元测试、后端编译、生产发布健康检查和真实线上评审闭环通过。

## 实施进度

- [x] 完成架构与 Tool 契约设计。
- [ ] 实现 query 扩展与 review Tool。
- [ ] 完成 Skill、目录、调度和显式绑定。
- [ ] 完成定向测试与生产发布。
- [ ] 以第三方开发者 CLI 提交设计、产品经理 SERVICE 批准、继续执行并申请验收完成线上闭环。
