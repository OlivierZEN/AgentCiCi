---
kind: task-status
task_id: TASK-249
status: in_progress
updated_at: 2026-08-05T07:18:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-249.yaml
spec_path: docs/specs/FEAT-142-admin-company-profile-proxy-route.md
---

# TASK-249 - 组织简档接口反向代理修复

## Current State

- Status: `in_progress`
- 已于 2026-08-05 复核线上：`https://x.agentcici.com/admin/company/profile` 返回 `200 text/html`；容器内直连后端同路径返回 `401 application/json`。根因确认是线上生效 Nginx 与当前仓库配置均未匹配 `admin/company/profile`，使请求落入 SPA。
- Next action: 为生产 Nginx 与本地 Vite 同步加入 `/admin/company/profile` 后端代理，完成配置回归后热重载 Nginx 并验证匿名请求命中后端鉴权边界。
- Blocked: none

## Evidence

- 生产匿名请求 `https://x.agentcici.com/admin/company/profile` 当前返回 `HTTP 200`、`content-type: text/html` 与 SPA `index.html`，未进入后端；生产容器的生效 Nginx 规则同样只匹配已废弃 `admin/organization/(profile|export-jobs)`。
- 生产数据库当前 schema V96 中 `company_member`、`knowledge_base`、`kb_document`、`skill_definition`、`agent_definition`、`company_export_job` 的 `company_id` 与组织简档统计使用的字段均存在；根因不在统计 SQL。

## Handoff

- 仅修复路由代理；保留后端 API、角色和数据边界。
- 用户已针对当前生产故障授权修复；不得构建或发布应用镜像，不得重启后端或数据库，仅允许校验并热重载前端 Nginx。
