---
kind: feature-spec
feature_id: FEAT-063
title: Production Domain Cutover
status: approved
owner_role: project-manager
task_ids: TASK-148
related_decisions: none
related_issues: none
updated_at: 2026-06-01T09:05:00Z
updated_by: MANAGER-001
---

# FEAT-063 - Production Domain Cutover

## Source Request

用户要求更换线上环境域名：

- 停用：`agentcici.com`、`www.agentcici.com`、`autoservice.agentcici.com`
- 启用：`onechat.agentcici.com`、`x.agentcici.com`

备注：用户已确认停用域名应为当前线上配置中的 `autoservice.agentcici.com`。

## Goals

- 线上 HTTPS vhost 只把 `onechat.agentcici.com` 与 `x.agentcici.com` 作为生产服务域名。
- 两个新域名先承载同一套 AgentCiCi 前端、认证、管理端、平台端、Open API、嵌入和 WebSocket 代理能力。
- 发布手册、部署记录、Open API 示例和登录页预约链接不再指向停用域名。
- 明确上线前置条件，避免只改 Nginx 但 DNS 或证书未准备导致生产不可用。

## Non Goals

- 不在本任务内操作 DNS 控制台或申请证书。
- 不拆分 `onechat` 与 `x` 的产品路由或权限含义；后续如果要让两个域名承载不同体验，需要单独规格。
- 不迁移历史数据、API Key 前缀、JWT issuer、代码包名或数据库标识。
- 不改写历史规格里作为背景、反馈证据或旧发布记录出现的旧域名。

## Design

### Hostnames

Canonical production host:

- `https://onechat.agentcici.com`

Secondary production alias:

- `https://x.agentcici.com`

Retired hostnames:

- `https://agentcici.com`
- `https://www.agentcici.com`
- `https://autoservice.agentcici.com`

Nginx must remove retired hostnames from production `server_name`. Requests for retired hostnames should not be served by the AgentCiCi HTTPS application vhost after reload.

### TLS

The deployed certificate currently remains referenced as:

- `/opt/cici/deploy/certs/agentcici.com.pem`
- `/opt/cici/deploy/certs/agentcici.com.key`

The file names may remain unchanged, but the certificate content must cover `onechat.agentcici.com` and `x.agentcici.com` before Nginx reload. A wildcard `*.agentcici.com` certificate is acceptable if it includes both hostnames.

### DNS

Before deployment smoke:

- `onechat.agentcici.com` resolves to the production ECS/public endpoint.
- `x.agentcici.com` resolves to the production ECS/public endpoint.
- DNS TTL and propagation are checked before cutting traffic.

### Application Links

- Public Open API examples use `https://onechat.agentcici.com`.
- The login page "立即预约" link uses `https://onechat.agentcici.com/#demo`.
- The deployment smoke still verifies both new domains.

## Acceptance Criteria

- `deploy/nginx.cici.ssl.conf` HTTP and HTTPS production vhosts list `onechat.agentcici.com x.agentcici.com`.
- Active production runbook smoke commands use the new domains.
- Public code examples no longer use `autoservice.agentcici.com`.
- Repository active deployment docs no longer present the retired domains as current production endpoints.
- Final production rollout records `nginx -t`, reload, and smoke results for both new hostnames.

## Rollout Checklist

1. Prepare DNS records for `onechat.agentcici.com` and `x.agentcici.com`.
2. Install or replace the production certificate so the configured cert files cover both new hostnames.
3. Sync updated Nginx config to `/opt/cici/deploy/`.
4. Run `docker exec cici-frontend nginx -t`.
5. Reload Nginx in the frontend container.
6. Smoke `https://onechat.agentcici.com/`, `https://x.agentcici.com/`, login, `/auth/me`, Open API health, and WebSocket-dependent flows if in scope.
7. Confirm retired hostnames are no longer served by the AgentCiCi production vhost.
