---
kind: feature-spec
feature_id: FEAT-142
title: 组织简档接口反向代理修复
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-249
related_decisions: company_id identity terminology; existing admin company profile API
related_issues: production /admin/company/profile served as SPA HTML
updated_at: 2026-07-24T14:31:37Z
updated_by: MANAGER-001
---

# FEAT-142 - 组织简档接口反向代理修复

## 背景与目标

组织管理端“组织简档”页面请求 `GET /admin/company/profile`。生产环境 Nginx 和本地 Vite 代理仍只匹配已废弃的 `/admin/organization/(profile|export-jobs)`，导致该请求落入 SPA `try_files`，返回 HTTP 200 的 `index.html` 而非后端 JSON；前端 JSON 解析失败后显示“组织简档加载失败”。

目标是在不改变鉴权、接口响应、组织隔离或页面视觉的前提下，将当前 `company` 命名空间的简档接口正确代理到后端。

## 范围

### In Scope

- 在生产 HTTP 与 HTTPS Nginx 配置中，将 `/admin/company/profile` 纳入现有后端 API 代理匹配。
- 在 Vite 开发代理中同步匹配 `/admin/company/profile`，使本地与生产路由一致。
- 保留旧 `/admin/organization/(profile|export-jobs)` 代理，以免扩大本任务为路由删除或兼容性破坏。
- 通过 Nginx 配置校验、前端构建、静态路由检查和生产匿名请求内容类型复核验证修复。

### Out Of Scope

- 不修改后端控制器、数据库、迁移、权限模型、`/admin/company/profile` 响应体或组织资料页面 UI。
- 不添加新的管理员入口、移动端适配、主题或组件视觉调整。
- 本任务只修复、测试并推送代码；生产发布必须由用户另行明确授权。

## 设计与接口约定

- 前端现有请求保持 `GET` / `PATCH /admin/company/profile`，携带既有 Bearer 管理员会话。
- Nginx 对该路径使用现有 `proxy_pass $backend_upstream`、Host 和 Forwarded 请求头约定；必须优先于通用 SPA `location /` 的 `try_files`。
- 未认证请求命中后端时应返回 API JSON 的 `401/403`，不得再返回 `text/html` 的 SPA 首页。认证与 `OWNER` / `ORG_ADMIN` 授权仍由既有后端执行。

## 验收标准

- 本地 Vite 和生产 Nginx 配置均显式匹配 `/admin/company/profile`。
- 生产匿名 `GET https://x.agentcici.com/admin/company/profile` 的响应不再为 SPA HTML；状态应由后端鉴权决定。
- 受权组织管理员重新打开“组织简档”可取得真实 JSON 简档数据，不再显示泛化加载失败。
- Nginx 配置校验、前端生产构建和 `git diff --check` 通过。

## 风险与回滚

- 变更仅扩大既有 API 代理白名单，未触及数据或权限；错误匹配的主要风险是把 SPA 页面路由误代理。
- 仅匹配精确 `admin/company/profile` 前缀，`/admin/company` 页面路由保持由 SPA 承载；如需回滚可撤回这一个代理匹配，但会恢复当前故障。
