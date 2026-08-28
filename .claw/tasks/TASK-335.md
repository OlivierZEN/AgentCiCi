---
kind: task-status
task_id: TASK-335
feature_id: FEAT-204
status: review
priority: critical
owner_role: frontend-agent
claimed_by: codex
updated_at: 2026-08-28T08:54:54Z
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
- 实现提交 `d47afb41c66d` 已进入本地 `main`；backend/frontend 从该提交构建为 `2.8.67-dev.d47afb4`，两容器 healthy/restart=0，版本 API、镜像 label 与运行环境一致。
- 真实官网浮窗刷新新制品后发送“我需要一个能跟客户在线沟通的智能软件”，页面展示 678 字完整回答；数据库同问题 user/assistant 历史完整，最新 `sisi_embed` Trace 为 `COMPLETED`、model_call_count=2，浏览器 error/warning=0。

## 下一步

- 由用户目视确认当前官网浮窗；远程推送、UAT 与生产另行授权。
