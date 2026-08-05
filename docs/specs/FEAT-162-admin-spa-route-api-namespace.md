---
kind: feature-spec
feature_id: FEAT-162
title: 组织管理端 SPA 路由与浏览器 API 命名空间隔离
status: done
owner_role: fullstack-agent
task_ids: TASK-272
related_decisions: none
related_issues: none
updated_at: 2026-08-05T10:45:24Z
updated_by: MANAGER-001
---

# FEAT-162 - 组织管理端 SPA 路由与浏览器 API 命名空间隔离

## 背景与目标

组织管理端的页面路由和后端管理 API 同时使用 `/admin/*`。刷新 `/admin/service-principals` 时，Nginx 将 HTML 文档请求代理到受保护的后端控制器，浏览器因此直接显示 `Authentication required` JSON，而不是加载 SPA 后由登录守卫处理会话。

本次将浏览器发起的管理 API 统一放入 `/api/admin/*`，由 Nginx 和本地 Vite 去前缀代理回既有后端 `/admin/*` 控制器；`/admin/*` 只保留给 SPA 页面路由。该边界适用于所有当前组织管理端功能页，避免后续新增页面与 API 再次冲突。

## 范围

### In Scope

- 迁移当前前端管理 API 请求到 `/api/admin/*`，包括用户、机器主体、组织简档、计费、运行观测、微信客服、本体与元数据审批。
- 将生产 HTTP/HTTPS Nginx 与本地 Vite 统一为通用 `/api/admin/* → /admin/*` 代理。
- 删除会截获 `/admin/*` SPA 深链的旧代理规则，保留非管理 API 的既有代理。
- 增加定向前端/配置回归，验证请求命名空间和深链刷新不再命中后端。

### Out Of Scope

- 不修改后端控制器、认证逻辑、权限、数据、路由 UI、主题或移动端。
- 不改变外部 OpenAPI 契约、权限或业务数据。

## 方案与约束

- 前端页面 URL 保持 `/admin/*`，浏览器 API URL 使用 `/api/admin/*`。
- 代理仅剥离 `/api` 前缀，后端仍接收原有 `/admin/*` 路径，因此无需改动服务端契约。
- `/admin/*` 文档请求必须回到 SPA 入口；未登录时由前端既有 `AdminGuard` 进入标准登录流，而不是暴露后端 JSON。

## 验收标准

- `Accept: text/html` 请求 `/admin/service-principals` 返回 SPA HTML，不再返回 401 JSON。
- 所有当前管理端浏览器 API 均从 `/api/admin/*` 访问，并由开发/生产代理正确转发。
- 未授权 API 仍由 `/api/admin/*` 以 JSON 401 返回，不降低鉴权边界。
- 定向前端测试、构建、Nginx/Compose 配置检查和 diff 检查通过。

## 风险与回滚

- 本次只改变浏览器到代理的路径，不改变后端控制器；若发现代理回归，回滚前端与两份 Nginx/Vite 配置即可恢复上一版本。
- 发布前必须按生产 Runbook 验证匿名页面深链和匿名 `/api/admin/*` 的 401 边界。

## 实现与验证

- 已迁移当前组织管理端的用户、机器主体、组织简档、计费、运行观测、微信客服、本体与元数据审批浏览器请求。
- HTTP 与 HTTPS Nginx、Vite 均使用通用 `/api/admin/*` 去前缀代理，旧 `/admin/*` API 代理已移除。
- 前端定向测试 52/52、完整后端测试、生产构建、Compose 配置与 HTTP `nginx -t` 通过；挂载配置的本地 Nginx 镜像已验证深链返回 SPA HTML。
- 已随 `2.8.56 / 564fb9fbfd8d` 发布生产。线上六容器 healthy、后端 health=UP、版本接口/Nginx 语法通过；`x.agentcici.com/admin/service-principals` 的 HTML 请求返回 `200 text/html` 和 SPA root，匿名 `/api/admin/service-principals` 仍返回 JSON 401，证明页面路由与 API 鉴权边界均已生效。
