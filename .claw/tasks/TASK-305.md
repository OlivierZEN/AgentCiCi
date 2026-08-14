---
kind: task-status
task_id: TASK-305
feature_id: FEAT-176
status: review
updated_at: 2026-08-14T01:31:58Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-176-platform-managed-web-tools.md
---

# TASK-305 - 平台长任务集成超时上限调整

## 范围

- 将代码解释器、联网搜索和网页抓取的可配置请求超时上限从 180 秒提升到 60 分钟。
- 统一前端数字输入约束、提示文案、后端保存校验和实际运行时 HTTP 请求上限。
- 默认值仍为 120 秒，最小值仍为 10 秒，不自动修改既有配置。

## 完成条件

- `3,600,000 ms` 可保存并用于实际客户端请求，`3,600,001 ms` 被后端拒绝。
- 平台页面明确显示最长 60 分钟且浏览器输入上限一致。
- 定向测试、前端构建、后端 package、本地主线提交和本地开发环境回读通过。

## 当前证据

- 后端 `ManagedWebToolServiceTest,SandboxCodeInterpreterServiceTest` 定向测试通过；60 分钟边界被接受，超过 1 ms 被拒绝。
- 后端 package 通过。
- 前端定向 5/5 通过，production build 通过；仅保留既有 chunk-size warning。
- 待提交本地 main 并从该提交重建本地 backend/frontend 后完成运行回读。
