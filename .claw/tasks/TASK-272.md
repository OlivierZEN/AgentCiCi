---
kind: task-status
task_id: TASK-272
status: done
updated_at: 2026-08-05T10:45:24Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-272.yaml
spec_path: docs/specs/FEAT-162-admin-spa-route-api-namespace.md
---

# TASK-272 - 组织管理端深链刷新回退修复

## Current State

- Status: `done`
- Next action: 使用正常组织管理员会话进行设置页硬刷新与真实数据交互回读；不需要额外部署动作。
- Blocked: none

## Evidence

- 2026-08-05 线上只读请求 `Accept: text/html https://x.agentcici.com/admin/service-principals` 返回 `401 application/json` 与 `Authentication required`，已复现用户截图问题。
- 前端 4 个定向测试文件共 52 项、TypeScript/Vite 生产构建、Compose 配置和 `git diff --check` 均通过。
- 挂载新 HTTP Nginx 配置的本地前端镜像验证：`/admin/service-principals` 为 `200 text/html` 且包含 SPA root；`/api/admin/service-principals` 会进入后端代理。HTTP 配置通过 `nginx -t`；SSL 配置因本机没有部署证书未作容器语法检查。
- 已合并至 `main`：合并提交 `564fb9fbfd8d`，Git annotated tag `2.8.56` 已推送。后端/前端 ACR index digest 分别为 `sha256:b9fad83dc1ed0710844a78c645c56bf6b82922047b82f3f7dc2d1b62f1ab12e6`、`sha256:e767ed0177ecd7f599897caad741b2a90f7e7002bc21bb148ae8e912dfb60e89`。
- 生产发布前备份 `/opt/cici/backups/20260805-184240-before-2.8.56` 的环境变量、PostgreSQL、知识库文件和 Qdrant 均非空；六个容器均 healthy，后端 `/actuator/health` 为 `UP`，`/system/version` 返回 `2.8.56 / 564fb9fbfd8d`，线上 `nginx -t` 通过且近期启动错误扫描为 0。
- 公网验收：`x.agentcici.com` HTTP 为 301、HTTPS 首页为 200；`Accept: text/html /admin/service-principals` 为 `200 text/html` 且含 SPA root、没有 `Authentication required`，而匿名 `/api/admin/service-principals` 保持 `401 application/json`。`onechat.agentcici.com` DNS 无法解析，为既有入口风险并未影响当前 x 入口。

## Scope

- 修复前端管理 API、Vite 和 HTTP/HTTPS Nginx 的路径命名空间。
- 保持后端 `/admin/*` 控制器和鉴权语义；生产仅切换版本化镜像与 Nginx 配置，不改业务数据。
