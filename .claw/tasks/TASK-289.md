---
kind: task-status
task_id: TASK-289
status: done
updated_at: 2026-08-12T06:55:03Z
updated_by: codex
assignee: codex
owner_role: frontend-agent
spec_path: n/a
depends_on: none
---

# TASK-289 - 平台集成卡片主线恢复

- 根因是首次修复仅存在于隔离分支，后续 main 构建重新带回错误 SPA 路由。
- 修复已正式进入本地 main，平台页面使用 `/api/platform/integrations`。
- 加载、错误重试与空数据状态完整保留，Tavily/讯飞配置逻辑不变。
- 前端 46 文件/247 项、构建和本地容器/制品回读通过。
