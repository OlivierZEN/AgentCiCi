---
kind: task-status
task_id: TASK-288
status: done
updated_at: 2026-08-12T04:55:30Z
updated_by: codex
assignee: codex
owner_role: frontend-agent
spec_path: n/a
depends_on: none
---

# TASK-288 - 平台集成配置卡片恢复

- 根因是平台页面请求了 SPA 路由 `/platform/integrations`，HTML 被当作 JSON 解析后页面静默空白。
- 前端改用同源 API `/api/platform/integrations`，保留 Tavily、讯飞配置与启停逻辑。
- 补齐加载、错误、重试和空数据状态，非 JSON 响应不再静默失败。
- 前端 46 文件/247 项、生产构建和本地 `cici.localhost` 部署验证通过。
