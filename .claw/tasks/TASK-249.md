---
kind: task-status
task_id: TASK-249
status: ready
updated_at: 2026-07-24T14:31:37Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-249.yaml
spec_path: docs/specs/FEAT-142-admin-company-profile-proxy-route.md
---

# TASK-249 - 组织简档接口反向代理修复

## Current State

- Status: `ready`
- Next action: 为生产 Nginx 与本地 Vite 同步加入 `/admin/company/profile` 后端代理，并执行配置与路由回归。
- Blocked: none

## Evidence

- 生产匿名请求 `https://x.agentcici.com/admin/company/profile` 当前返回 `HTTP 200`、`content-type: text/html` 与 SPA `index.html`，未进入后端。
- 生产数据库当前 schema V96 中 `company_member`、`knowledge_base`、`kb_document`、`skill_definition`、`agent_definition`、`company_export_job` 的 `company_id` 与组织简档统计使用的字段均存在；根因不在统计 SQL。

## Handoff

- 仅修复路由代理；保留后端 API、角色和数据边界。
- 未获生产发布授权前不得构建镜像、修改线上 Nginx 或重启容器。
