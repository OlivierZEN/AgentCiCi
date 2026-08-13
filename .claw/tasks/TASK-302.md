---
kind: task-status
task_id: TASK-302
feature_id: FEAT-183
integration_id: INT-019
status: review
updated_at: 2026-08-13T12:59:30Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-183-system-api-catalog.md
---

# TASK-302 - 运营端系统 API 目录

## 范围

- 建立 AgentCiCi 首批核心跨应用 API 的提供方目录。
- 聚合 Semattice 受治理目录投影，并在不可用时显式降级。
- 在能力治理中新增“系统 API”及 AgentCiCi、Semattice 子菜单。
- 实现概览、提供方列表、宽抽屉速览和独立调用文档页。

## 完成条件

- 目录读取受平台角色保护，业务 API 原鉴权和功能逻辑不变。
- 前后端定向测试与生产构建通过。
- 任务提交进入 AgentCiCi 本地 `main`，从该提交更新 `cici.localhost` 并回读运行指纹。

## 实现结果

- AgentCiCi 提供方收录 8 个核心契约，其中新增可访问公司查询和公司上下文切换；Semattice 通过提供方持有的 HMAC 投影聚合 11 个核心 Capability。
- 公司切换沿用现有 `/auth/switch-company` 逻辑：服务端校验同一全局账号的 ACTIVE 成员关系并签发新令牌；目录文档区分 HUMAN 会话令牌、SERVICE Token、OACT 与内部 HMAC，不改变原接口逻辑。
- 运营端已实现提供方首页、可搜索/筛选列表、宽抽屉速览和独立文档页；URL 支持列表、抽屉与文档深链。
- 目录读取继续受平台角色保护，页面不提供在线执行入口，目录可见性不授予业务 API 调用权限。

## 验证证据

- 后端定向测试：`mvn -q -Dtest=SystemApiCatalogServiceTest test` 通过。
- 后端生产包：`mvn -q -DskipTests package` 通过。
- 前端全量测试：49 个测试文件、265 个测试通过；`npm run build` 通过。
- 完整后端测试已执行，但受共享开发库既有 Flyway V81 checksum mismatch 和模型调用配置缺失阻塞，不计为本任务通过证据。
- 功能提交 `5c4c7c5` 已进入本地 `main`；最终对齐部署从当时本地 `main` `ea1f6ab` 构建，后端与前端均运行 `2.8.61-dev.ea1f6ab`、健康且重启次数为 0。本条证据产生的后续纯文档提交不改变制品。
- `https://cici.localhost/platform/system-apis` 返回 200；匿名读取 `/api/platform/system-apis` 返回 401；部署 JS 制品包含系统 API 菜单与权限边界文案。
- `cc-local-stack ./stack verify` 最终通过，覆盖共享数据库隔离、TLS 边缘、OIDC、应用健康/版本和匿名鉴权边界。
- 浏览器无运营平台登录态，真实平台账号下的最终视觉/交互验收待人工完成，因此任务保持 `review`。

## 目录加载缺陷修复

- 根因：前端请求 `/platform/system-apis` 命中 SPA fallback，返回 `text/html`；页面直接执行 JSON 解析，向用户暴露 `Unexpected token '<'`。
- 修复：浏览器请求改为 `/api/platform/system-apis`，显式声明 `Accept: application/json`，并使用共享安全解析器处理非 JSON 回应；业务后端映射和鉴权逻辑未改变。
- 回归：新增 API namespace、HTML fallback 和结构化错误信息测试；前端全量 49 文件/269 项与生产构建通过。
- 本地环境：修复提交 `b5d189a1` 已进入本地 `main`；backend/frontend 均运行 `2.8.61-dev.b5d189a`、healthy/restart=0，匿名接口为 `401 application/json`，完整 `./stack verify` 通过。

## 公司上下文 API 公布

- 目录新增 `GET /auth/companies` 与 `POST /auth/switch-company`，分别说明可访问公司查询、ACTIVE 成员关系校验、新公司上下文令牌替换和租户缓存清理要求。
- 调用文档根据契约显示 HUMAN session token、SERVICE token、OACT 或 Internal HMAC，不再为所有 API 固定展示 OACT。
- 后端 `SystemApiCatalogServiceTest` 通过；前端定向 1 文件/7 项、全量 49 文件/271 项及 production build 通过；后端 production package 通过。
