---
kind: task-status
task_id: TASK-191
title: CloudCC 嵌入页重复刷新与客户信号并发修复
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-099-cloudcc-embed-remount-signal-idempotency.md
assignment_path: .claw/assignments/TASK-191.yaml
updated_at: 2026-07-11T15:26:48Z
updated_by: MANAGER-001
---

# TASK-191 - CloudCC 嵌入页重复刷新与客户信号并发修复

## 目标

消除 CRM 注入页重复刷新白屏和客户详情并发信号落库导致的通用服务器错误。

## 计划

1. 修复 pagecomponent 延迟插入和宿主复用后的重新挂载。
2. 修复客户信号确定性 ID 的并发插入竞态。
3. 通过技能发布组件并完成连续刷新生产验收。

