---
kind: task-status
task_id: TASK-182
title: 客户互动工作台生产闭环
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
assignment_path: .claw/assignments/TASK-182.yaml
updated_at: 2026-07-10T15:26:34Z
updated_by: MANAGER-001
---

# TASK-182 - 客户互动工作台生产闭环

## 当前目标

依据 FEAT-081 的生产版详细功能设计和 32 项差距矩阵，补齐真实 CloudCC 数据、权限、建议状态机、CRM 写回、指标信号、业务页签、AI 结构化动作与异常状态，完成生产发布和真实 CRM 验收。

## 当前进展

- 用户已明确开启 goal 模式并要求达到生产就绪后发布线上版本。
- FEAT-081 已完成逐元素详细设计、P0/P1/P2 差距矩阵、API/数据模型和验收标准。
- 当前生产基线为 `2.3.9`；首要缺口是本地快照队列、模拟 CRM 写回、前端推导指标和不完整建议状态机。
- 已建立任务范围，下一步执行 CloudCC 标准对象/字段扫描与现有实现契约审计。

## 执行计划

1. CloudCC 标准目录、对象字段、OpenAPI 与现有工作台实现审计。
2. V73 数据模型与真实客户队列、互动、指标、信号、关注通知后端实现。
3. 建议编辑/忽略/确认/执行/重试、真实 CRM 写回、回读与审计实现。
4. 结构化 AI UI 动作、客户级会话和全部工作台前端交互实现。
5. 聚焦测试、构建、桌面浏览器、CRM 嵌入、真实写回与回滚验证。
6. 提交推送，按生产 runbook 发布新版本并回写证据。

## 验收门

- 不存在固定客户总数、固定页码、前端公式指标、固定关键项或模拟 CRM ID。
- 同一用户从 AgentCiCi 与 CRM 嵌入入口看到相同权限范围内的客户数据。
- 至少 Task 与 Opportunity 两类写入完成确认、执行、回读、审计和失败重试闭环。
- 新客户推进、老客户经营、AI 助理、语音、筛选分页和异常状态可用。
- 本地与生产桌面端无外层滚动、无明显重叠、控制台无错误。
- 真实 CloudCC 嵌入页和生产版本验收通过。

## 约束

- CloudCC 元数据与嵌入操作必须使用 `cc-customization-expert-msapi` 技能 CLI，不绕过技能。
- CloudCC runtime token 只用于身份入口；OpenAPI/MCP 使用 AgentCiCi 后端生成的当前用户 accessToken。
- 不新增移动端适配，不记录或提交 token、密码、cookie 或其他可复用凭证。
