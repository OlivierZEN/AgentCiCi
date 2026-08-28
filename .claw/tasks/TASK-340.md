---
kind: task-status
task_id: TASK-340
status: in_progress
priority: high
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-28T13:08:03Z
updated_by: codex
---

# TASK-340 - 工作台发送后输入框立即清空

## 范围

- 修复工作台消息仅在流式回复成功结束后才清空输入框的问题。
- 保持回车与发送按钮共用同一提交路径。
- 请求未通过前置校验时保留草稿；请求被会话接受后立即清空，即使后续 HTTP 或流式响应失败也不回填已发送内容。

## 完成条件

- [ ] 输入内容在请求开始前清空，不等待模型回复完成。
- [ ] HTTP/流式失败不会让已发送内容重新出现在输入框。
- [ ] 回车和发送按钮行为一致。
- [ ] 聚焦测试、前端全量测试与 production build 通过。
- [ ] 实现提交进入本地 `main`，本地开发环境 frontend 可追溯到该提交。

## 当前证据

- 待验证。
