---
kind: task-status
task_id: TASK-249
status: done
updated_at: 2026-08-05T07:41:57Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-249.yaml
spec_path: docs/specs/FEAT-142-admin-company-profile-proxy-route.md
---

# TASK-249 - 组织简档接口反向代理修复

## Current State

- Status: `done`
- 已为两份版本化 Nginx 配置和 Vite 开发代理增加精确 `/admin/company/profile` 后端路由，同时保留既有组织兼容路由及生产已有的用户/机器主体 API 白名单。
- 线上配置已通过 Nginx 语法校验并热重载。服务器回环、服务器公网 IP 与 DNS 解析后的 `x.agentcici.com` 均返回 `401 application/json`，不再返回 SPA HTML。
- Next action: 已完成；受权组织管理员刷新页面即可由既有后端接口返回真实简档数据。
- Blocked: none

## Evidence

- 修复前生产匿名请求返回 `HTTP 200 text/html`；修复后从回环、公网 IP 和 DNS 域名三处验证均为 `HTTP 401 application/json;charset=ISO-8859-1`，符合后端 `@RequireOrgAdmin` 鉴权边界。
- `npm --prefix frontend run build` 通过；`docker exec cici-frontend nginx -t` 通过；前端与后端容器均为 healthy，`/actuator/health` 为 `UP`。
- 生产数据库当前 schema V96 中 `company_member`、`knowledge_base`、`kb_document`、`skill_definition`、`agent_definition`、`company_export_job` 的 `company_id` 与组织简档统计使用的字段均存在；根因不在统计 SQL。

## Handoff

- 仅修复路由代理；保留后端 API、角色和数据边界。
- 配置备份位于 `/opt/cici/backups/20260805-154049-before-task249-company-profile-proxy`。本次未构建或发布应用镜像，未修改后端、数据库、权限、UI 或业务数据。
