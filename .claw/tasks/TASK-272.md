---
kind: task-status
task_id: TASK-272
status: review
updated_at: 2026-08-05T09:18:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-272.yaml
spec_path: docs/specs/FEAT-162-admin-spa-route-api-namespace.md
---

# TASK-272 - 组织管理端深链刷新回退修复

## Current State

- Status: `review`
- Next action: 等待用户决定是否合并并按生产 Runbook 发布；当前未发布生产。
- Blocked: none

## Evidence

- 2026-08-05 线上只读请求 `Accept: text/html https://x.agentcici.com/admin/service-principals` 返回 `401 application/json` 与 `Authentication required`，已复现用户截图问题。
- 前端 4 个定向测试文件共 52 项、TypeScript/Vite 生产构建、Compose 配置和 `git diff --check` 均通过。
- 挂载新 HTTP Nginx 配置的本地前端镜像验证：`/admin/service-principals` 为 `200 text/html` 且包含 SPA root；`/api/admin/service-principals` 会进入后端代理。HTTP 配置通过 `nginx -t`；SSL 配置因本机没有部署证书未作容器语法检查。

## Scope

- 修复前端管理 API、Vite 和 HTTP/HTTPS Nginx 的路径命名空间。
- 保持后端 `/admin/*` 控制器和鉴权语义；不发布生产。
