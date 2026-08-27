---
kind: task-status
task_id: TASK-332
feature_id: FEAT-202
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-27T09:45:00Z
updated_by: codex
---

# TASK-332 - 思思嵌入式智能应用生产落地

## 范围

- 落地 `FEAT-202` 的 CloudCC 身份换票、会话隔离、真实 Agent 对话、附件、语音、证据、确认与回执。
- 提供 `/embed/sisi`、稳定/版本 SDK、页面/浮窗两种模式和组织管理配置。
- 完成定向测试、构建、安全门禁、桌面浏览器截图、批评修复、本地 `main` 合并与开发环境回读。

## 完成条件

- `docs/specs/FEAT-202-sisi-embedded-agent.md` 的技术验收项通过。
- 任务提交只包含 AgentCiCi 子仓文件，进入本地 `main`；不自动推送远端，不发布 UAT/生产。
- 本地开发全栈运行版本可追溯到本地 `main`，backend/frontend 联合回读一致。

## 当前状态

- 已完成 shape brief、三组视觉探针、B/C 组合确认和两张 north-star mock 确认。
- 已完成 CloudCC 组织/用户名绑定换票、会话隔离、Agent 运行时、文档/图片附件、ASR Scope、SSE、SDK、页面/浮窗和组织管理配置。
- 技术证据：PostgreSQL 16.9 从空库迁移至 V124，`EmbedAppIntegrationTest` 3 项通过；后端附件/租户上下文 16 项与 production package 通过；前端全量 57 文件/311 项（含思思确认协议 2 项）通过；production build、SDK `node --check`/稳定版一致性、`git diff --check` 通过。
- 浏览器证据：页面三栏、408px 浮窗和发送交互已用真实 Chromium 验证，控制台无错误；截图保存在忽略目录 `output/playwright/`，不进入发布制品。
- 待完成：将任务提交归并到最新本地 `main`，只从该主线构建 backend/frontend 本地镜像，更新 `cc-local-stack` 并联合回读版本、制品、页面、健康与重启次数。
