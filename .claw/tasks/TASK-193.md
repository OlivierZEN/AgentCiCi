---
kind: task-status
task_id: TASK-193
title: 客户队列默认按最近互动倒序
status: ready
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-100-large-crm-organization-async-sync.md
assignment_path: .claw/assignments/TASK-193.yaml
updated_at: 2026-07-12T02:05:00Z
updated_by: MANAGER-001
---

# TASK-193 - 客户队列默认按最近互动倒序

## 目标

新客户推进与老客户经营的客户列表默认统一按最近互动时间倒序，最近客户优先，无时间客户置后并保持稳定排序。

## 计划

1. 将前端和后端队列默认排序统一为 `interaction desc`。
2. 为同一互动时间增加稳定的客户 ID 次排序。
3. 补充双模式回归测试并发布线上版本。

