---
kind: task-status
task_id: TASK-182
title: 客户互动工作台生产闭环
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
assignment_path: .claw/assignments/TASK-182.yaml
updated_at: 2026-07-11T00:45:00Z
updated_by: MANAGER-001
---

# TASK-182 - 客户互动工作台生产闭环

## 当前目标

依据 FEAT-081 的生产版详细功能设计和 32 项差距矩阵，补齐真实 CloudCC 数据、权限、建议状态机、CRM 写回、指标信号、业务页签、AI 结构化动作与异常状态，完成生产发布和真实 CRM 验收。

## 当前进展

- FEAT-081 P0/P1/P2 已在生产 `2.4.1` 完成闭环，运行提交为 `146b6fde4ec2`。
- CloudCC 当前用户权限范围内的 Account/Contact/Opportunity/Task/Event/Case/Contract 已成为运行时事实源；新客户推进与老客户经营使用服务端互斥队列、筛选、排序、分页和真实指标/信号。
- 建议已具备编辑、忽略、采纳、确认、真实 Task/Opportunity 写入、权限范围回读、幂等审计、失败重试与用户反馈；V73/V74 本地迁移通过。
- AI 助理已支持客户级历史恢复、结构化页面动作、语音回填、互动确认录入/去重和主管概览；AgentCiCi 与 CRM 嵌入共享用户和客户级上下文。
- 技能 CLI 已完成真实标准目录、记录权限、临时 Task/Opportunity 创建/回读/删除、pagecomponent/customPage 和注入页核验；验证数据已清理。
- 聚焦后端测试、前端 54 项测试、生产构建、Compose 配置及桌面页面功能/布局验收通过。
- `2.3.10` 完成真实 CRM 数据和写回主链；`2.3.11` 修复老客户默认空队列；`2.3.12` 修复 CRM 已成功但本地乐观锁失败的幂等恢复，未产生重复 Task。
- 真实生产建议写回两条 CloudCC Task 均为 `APPLIED` 且回读一致：`bfa20267174CD4EL9Qjr`（准备增购方案）与 `bfa2026450C1935N4yKT`（补充 TCO 对比）。
- AgentCiCi 与真实 CRM 嵌入入口均以同一 CloudCC 用户 `CCAdmin` 验证；新客户重点队列 2 位，老客户默认可见 48 位，CRM iframe 无平台侧栏、无外层滚动且权限范围一致。
- `2.4.1` 修复语音发送后转写回调把文本写回输入框的竞态，并在用户消息、AI 回复及后续流式内容变化时自动滚到最新消息。真实 CRM iframe 验证发送后输入为空，长回复后消息区精确位于底部。

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
