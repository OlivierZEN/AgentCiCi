---
kind: task-status
task_id: TASK-190
title: CloudCC 嵌入端会话失效自动恢复
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-098-cloudcc-session-refresh.md
assignment_path: .claw/assignments/TASK-190.yaml
updated_at: 2026-07-11T14:40:42Z
updated_by: MANAGER-001
---

# TASK-190 - CloudCC 嵌入端会话失效自动恢复

## 目标

修复 CRM 嵌入端已映射用户因 CloudCC Token 业务失效而持续显示连接异常的问题。

## 计划

1. 为同一组织/用户增加 Token 单次并发刷新。
2. 识别 HTTP 200 登录失效并刷新重试一次。
3. 补齐错误映射、自动化测试和生产嵌入页验证。

