---
kind: task-status
task_id: TASK-272
status: in_progress
updated_at: 2026-08-05T09:11:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-272.yaml
spec_path: docs/specs/FEAT-162-admin-spa-route-api-namespace.md
---

# TASK-272 - 组织管理端深链刷新回退修复

## Current State

- Status: `in_progress`
- Next action: 将浏览器管理 API 迁移到 `/api/admin/*`，移除吞掉 SPA 深链的旧 `/admin/*` 代理。
- Blocked: none

## Evidence

- 2026-08-05 线上只读请求 `Accept: text/html https://x.agentcici.com/admin/service-principals` 返回 `401 application/json` 与 `Authentication required`，已复现用户截图问题。

## Scope

- 修复前端管理 API、Vite 和 HTTP/HTTPS Nginx 的路径命名空间。
- 保持后端 `/admin/*` 控制器和鉴权语义；不发布生产。
