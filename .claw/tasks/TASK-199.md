---
kind: task-status
task_id: TASK-199
title: 互动驱动的客户经营动作
status: review
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-105-interaction-driven-customer-actions.md
assignment_path: .claw/assignments/TASK-199.yaml
updated_at: 2026-07-12T15:23:54Z
updated_by: MANAGER-001
---

# TASK-199 - 互动驱动的客户经营动作

## 目标

用已确认互动中的 AI 证据持续生成可解释、可去重、可确认并可写入 CRM 的客户经营动作，移除首次打开客户时的固定建议。

## 计划

1. 扩展互动分析动作候选契约和 CRM 上下文。
2. 增加动作来源、业务键、有效期模型和 V78。
3. 实现动作生成、校验、去重、冷却与刷新。
4. 接入互动确认链路并完善动作解释 UI。
5. 完成自动化、真实数据、CRM 写回和生产发布。

## 当前结果

- 已移除首次打开客户时的固定建议和演示种子动作。
- 已实现互动动作候选、原文证据校验、置信度门槛、业务键去重/刷新、七天冷却、历史时效拦截、来源档案追溯和现有人工确认写回链路。
- V78 已增加来源事件、来源批次、动作键、触发类型和有效期字段。
- 后端 24 项聚焦测试、前端 66 项测试与生产构建、本地桌面浏览器验收通过；等待生产发布和真实环境验收。
