---
kind: task-status
task_id: TASK-193
title: 客户队列默认按最近互动倒序
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-100-large-crm-organization-async-sync.md
assignment_path: .claw/assignments/TASK-193.yaml
updated_at: 2026-07-12T02:45:57Z
updated_by: MANAGER-001
---

# TASK-193 - 客户队列默认按最近互动倒序

## 目标

新客户推进与老客户经营的客户列表默认统一按最近互动时间倒序，最近客户优先，无时间客户置后并保持稳定排序。

## 计划

1. 将前端和后端队列默认排序统一为 `interaction desc`。
2. 为同一互动时间增加稳定的客户 ID 次排序。
3. 补充双模式回归测试并发布线上版本。

## 完成结果

- 前后端默认排序已统一为 `interaction desc`，暂无互动时间的客户不再使用同步时间冒充互动时间。
- 后端 10 项相关测试、前端 59 项测试及生产构建通过。
- 已发布生产版本 `2.5.3`；真实组织的新客户、老客户默认队列各取 12 条，时间严格倒序且空时间记录置后，六服务健康且发布后错误扫描为空。
