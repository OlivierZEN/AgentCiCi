---
kind: task-status
task_id: TASK-190
title: CloudCC 嵌入端会话失效自动恢复
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-098-cloudcc-session-refresh.md
assignment_path: .claw/assignments/TASK-190.yaml
updated_at: 2026-07-11T14:59:42Z
updated_by: MANAGER-001
---

# TASK-190 - CloudCC 嵌入端会话失效自动恢复

## 目标

修复 CRM 嵌入端已映射用户因 CloudCC Token 业务失效而持续显示连接异常的问题。

## 计划

1. 为同一组织/用户增加 Token 单次并发刷新。
2. 识别 HTTP 200 登录失效并刷新重试一次。
3. 补齐错误映射、自动化测试和生产嵌入页验证。

## 验收结果

- 同一组织/用户的并发 Token 获取已收敛为单次请求；HTTP 200 业务体中的登录失效会条件清除旧 Token、刷新并重试一次。
- CloudCC API 异常返回明确的 502 业务消息，不再进入通用 `Unexpected server error`。
- 聚焦测试通过：8 路并发只申请 1 次 Token，旧 Token 登录失败后使用新 Token 重试成功，普通业务错误不误判。
- 已发布生产 `2.4.9 / 052bf118fc1e`。生产 `CCAdmin` 并发调用连接、队列、提醒与主管摘要均返回 200；连接状态为已连接、可见客户 110、老客户 48，发布后错误日志为空。
