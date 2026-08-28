---
kind: task-status
task_id: TASK-335
feature_id: FEAT-204
status: in_progress
priority: critical
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-28T08:35:14Z
updated_by: codex
---

# TASK-335 - Web 浮窗流式回复空白修复

## 范围

- 修复 Web 浮窗未消费后端规范 SSE `delta` 事件 `{ "text": "..." }` 的协议漂移。
- 保留纯文本、`content` 和 `delta` 旧负载兼容，不改变会话、Token、权限或视觉结构。
- 从 AgentCiCi 本地 `main` 同一提交重建 backend/frontend，并在 `https://cici.localhost/` 完成真实对话回归。

## 完成条件

- `{ text }` 增量被逐段追加到当前 assistant 消息，不再渲染“本次未返回文字内容。”。
- 聚焦测试覆盖规范字段与兼容字段；前端全量测试、production build、域名门禁和 diff check 通过。
- backend/frontend 从本地 `main` 同一提交构建，运行版本、commit、健康与 restart 回读一致。
- 官网浮窗发送用户截图中的问题后返回非空模型正文，并有成功执行记录。
- 远程、UAT 与生产保持不变。

## 当前证据

- 后端 `ChatOrchestratorService.safeSendDelta` 与 Agent OpenAPI 测试均以 `Map.of("text", text)` 发送 `delta`。
- `SisiEmbedPage.consumeStream` 只读取 `content ?? delta`，规范 `text` 被忽略；流结束后空字符串由 `ChatMarkdown` 显示为兜底文案。
- 修复已抽取 `streamDeltaText`，聚焦 4 项、前端全量 58 文件/318 项、production build、域名门禁和 diff check 通过。

## 下一步

- 提交到本地 `main`，重建并回归本地全栈官网浮窗。
