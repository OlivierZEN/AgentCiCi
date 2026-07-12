---
kind: task-status
task_id: TASK-199
title: 互动驱动的客户经营动作
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-105-interaction-driven-customer-actions.md
assignment_path: .claw/assignments/TASK-199.yaml
updated_at: 2026-07-12T12:20:00Z
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

- 规格和授权已建立，进入实现。
