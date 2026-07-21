---
kind: task-status
task_id: TASK-214
status: ready
updated_at: 2026-07-21T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-214.yaml
spec_path: docs/specs/FEAT-119-onekeytoken-live-validation.md
---

# TASK-214 - OneKeyToken 实时凭据检测修复

## Scope

- 修复运营端 OneKeyToken “检测”只读取静态目录、错误 Key 仍显示成功的问题。
- 用当前表单草稿调用 OneKeyToken Chat Completions，并提供无泄露的成功与失败反馈。

## Current State

- 已复现并确认根因：`onekeytoken` 使用 `static-catalog`，`checkProvider` 没有发出远程请求。
- 文档契约、服务端和前端交互设计已记录于 FEAT-119；待完成授权预检后开始实现。

## Next Action

- 在授权分支实现真实检测、自动化测试和桌面端回归。
