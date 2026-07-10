---
kind: task-status
task_id: TASK-182
title: 客户互动工作台生产闭环
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-081-customer-interaction-workbench.md
assignment_path: .claw/assignments/TASK-182.yaml
updated_at: 2026-07-10T17:36:00Z
updated_by: MANAGER-001
---

# TASK-182 - 客户互动工作台生产闭环

## 当前目标

依据 FEAT-081 的生产版详细功能设计和 32 项差距矩阵，补齐真实 CloudCC 数据、权限、建议状态机、CRM 写回、指标信号、业务页签、AI 结构化动作与异常状态，完成生产发布和真实 CRM 验收。

## 当前进展

- FEAT-081 P0/P1/P2 代码闭环与本地验收已完成，当前生产基线仍为 `2.3.9`，待发布版本为 `2.3.10`。
- CloudCC 当前用户权限范围内的 Account/Contact/Opportunity/Task/Event/Case/Contract 已成为运行时事实源；新客户推进与老客户经营使用服务端互斥队列、筛选、排序、分页和真实指标/信号。
- 建议已具备编辑、忽略、采纳、确认、真实 Task/Opportunity 写入、权限范围回读、幂等审计、失败重试与用户反馈；V73/V74 本地迁移通过。
- AI 助理已支持客户级历史恢复、结构化页面动作、语音回填、互动确认录入/去重和主管概览；AgentCiCi 与 CRM 嵌入共享用户和客户级上下文。
- 技能 CLI 已完成真实标准目录、记录权限、临时 Task/Opportunity 创建/回读/删除、pagecomponent/customPage 和注入页核验；验证数据已清理。
- 聚焦后端测试、前端 50 项测试、生产构建、Compose 配置及 1920x1000 页面功能/布局验收通过。下一步是提交推送、备份、发布和线上双入口验收。
- `2.3.10` 首次线上真实数据验收发现老客户模式默认“续约90天”在无近期合同租户会返回空队列，并残留上一位新客户详情；热修已改为老客户默认展示全部可见客户、筛选仅在用户主动选择后生效，空队列立即清空详情，待发布 `2.3.11` 复验。
- `2.3.11` 真实建议写回验收发现 repository `save` 后继续使用旧乐观锁版本，远端 Task 已创建但建议卡在 `APPLYING`。审计已保留远端 ID；修复增加 save 返回实体接续、成功写入后的本地状态保存隔离，以及 `APPLYING/FAILED audit + remote ID` 幂等回读恢复，确保不重复创建。

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
