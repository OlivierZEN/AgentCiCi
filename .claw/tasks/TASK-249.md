---
kind: task-status
task_id: TASK-249
status: review
updated_at: 2026-07-24T14:35:07Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-249.yaml
spec_path: docs/specs/FEAT-142-admin-company-profile-proxy-route.md
---

# TASK-249 - 组织简档接口反向代理修复

## Current State

- Status: `review`
- Next action: 等待用户授权合并或生产发布；发布后以受权组织管理员会话复核真实组织简档。
- Blocked: none

## Evidence

- 生产匿名请求 `https://x.agentcici.com/admin/company/profile` 当前返回 `HTTP 200`、`content-type: text/html` 与 SPA `index.html`，未进入后端。
- 生产数据库当前 schema V96 中 `company_member`、`knowledge_base`、`kb_document`、`skill_definition`、`agent_definition`、`company_export_job` 的 `company_id` 与组织简档统计使用的字段均存在；根因不在统计 SQL。

## Handoff

- 仅修复路由代理；保留后端 API、角色和数据边界。
- 未获生产发布授权前不得构建镜像、修改线上 Nginx 或重启容器。

## Verification

- `nginx:1.27-alpine nginx -t` 使用更新后的 HTTP 配置通过。
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml config`、`npm run build` 与 `git diff --check` 通过。
- 静态检查确认 Vite、HTTP Nginx 与 HTTPS Nginx 均匹配 `/admin/company/profile`；生产仍是旧配置，待授权发布后复核匿名请求不再返回 HTML。
