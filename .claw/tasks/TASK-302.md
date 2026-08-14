---
kind: task-status
task_id: TASK-302
feature_id: FEAT-183
integration_id: INT-019
status: review
updated_at: 2026-08-14T00:29:24Z
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
- 明确公司 API 的 HUMAN 鉴权边界，并按 AgentCiCi 前端、同源扩展、新独立应用和机器应用说明 Token 取得或接入流程。
- 补齐独立内部应用直接使用 Keycloak Access Token 调用的通用入口、受信 Client 治理及无状态公司上下文契约。

## 完成条件

- 目录读取受平台角色保护，业务 API 原鉴权和功能逻辑不变。
- 前后端定向测试与生产构建通过。
- 任务提交进入 AgentCiCi 本地 `main`，从该提交更新 `cici.localhost` 并回读运行指纹。
- 独立应用只需 Keycloak `access_token` 与可选 `X-Company-Id` 即可调用；未知 Client、错误 Audience、停用应用和非 ACTIVE 成员均失败关闭。

## 实现结果

- AgentCiCi 提供方收录 8 个核心契约；公司相关契约已升级为 `GET /openapi/v1/ecosystem/companies` 与 `POST /openapi/v1/ecosystem/company-context`。Semattice 继续通过提供方持有的 HMAC 投影聚合 11 个核心 Capability。
- 内部独立应用使用自己的 Keycloak Client 完成标准登录，直接以 `access_token` 调用；服务端校验 RS256 签名、Issuer、有效期、`typ=Bearer`、`aud=agentcici-api` 和 `azp`，再按 `(issuer, sub)` 映射 HUMAN 账号。
- V115 新增受信内部应用目录；平台管理员可在系统 API 首页进入独立列表，以弹窗登记 `app_code`、Keycloak Client ID、允许 Scope 和状态。Client Secret 不进入 AgentCiCi，停用立即阻断调用，配置变更写入平台审计。
- 公司列表不接受账号参数；公司上下文不签发第二套长期令牌。后续公司级调用继续使用同一 Keycloak Token 和 `X-Company-Id`，服务端逐请求校验 ACTIVE 公司及成员关系。
- 运营端已实现提供方首页、可搜索/筛选列表、宽抽屉速览和独立文档页；URL 支持列表、抽屉与文档深链。
- 目录读取继续受平台角色保护，页面不提供在线执行入口，目录可见性不授予业务 API 调用权限。
- AgentCiCi 自身前端继续使用现有 OIDC BFF；DevAutopilot 单次 handoff 和机器应用 SERVICE/OACT 均保持原逻辑，不强行迁移到 HUMAN 直调入口。

## Keycloak HUMAN 直调实现

- 后端定向 14 项通过：Keycloak HUMAN Token 验签与错误 Audience、受信 Client 未登记/停用/Scope 缺失、HUMAN 映射、公司目录、公司上下文、非成员 403 和错误 HTTP 方法 405 均有断言。
- 后端 production package 通过；前端 49 文件/272 项与 production build 通过。
- OpenAPI CORS 已允许 `X-Company-Id`，具体 Origin 继续由环境配置注入，不在业务源码维护环境域名。
- 功能提交 `e90a2d2b` 与协议错误修复 `9f58d972` 已进入本地 `main`；`cici.localhost` 从最终 `main@9f58d972` 构建并运行 `2.8.61-dev.9f58d97`。backend/frontend 均 healthy，V115 为成功状态，系统 API 页面路由 200，匿名业务调用为 JSON 401，错误方法为 JSON 405，完整 `./stack verify` 通过。UAT/生产未修改。
- 当前没有可用于验收的“新登记独立 Keycloak Client + HUMAN 用户”凭据，未虚构真实登录和业务成功响应；该端到端业务验收是进入 done 前的剩余项。

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
- 功能提交 `a206da9a` 与门禁兼容修复 `6444bbcf` 均已进入本地 `main`。backend/frontend 从 `6444bbcf` 构建为 `2.8.61-dev.6444bbc`，均 healthy/restart=0；运行制品回读包含两个新契约 ID、真实路径和 HUMAN 令牌类型，完整 `./stack verify` 通过。

## HUMAN 鉴权与新应用接入文档

- 后端定向 `SystemApiCatalogServiceTest` 通过，覆盖 Keycloak 原始 Token 不可直调、五步签发链路、新独立应用接入前置和 Semattice 投影兼容。
- 前端定向 1 文件/8 项、全量 49 文件/272 项和 production build 通过；请求示例使用 `AGENTCICI_ECOSYSTEM_HUMAN_TOKEN`，并覆盖现有 `/auth/oidc/login` 与 `/auth/oidc/complete` 流程。
- 功能提交 `99ae151b5ce0` 已进入本地 `main`，backend/frontend 从该提交构建并运行 `2.8.61-dev.99ae151`；两个容器均 healthy/restart=0，镜像 revision 一致。
- `https://cici.localhost/platform/system-apis/agentcici` 返回 200，匿名 `/api/platform/system-apis` 返回 `401 application/json`，完整 `./stack verify` 通过；部署前端制品包含 Keycloak 原始 Token 不可直调的结论。
- 浏览器访问受保护深链会正确进入运营平台登录页；当前没有可复用的运营平台登录态，因此授权态抽屉和完整文档的视觉/交互验收仍待平台运营人员完成。UAT/生产未修改。

## UAT 发布

- Keycloak HUMAN 直调增量已以不可变候选 `2.8.61-beta.20 / 1b6bb8f1974a` 发布；backend/frontend digest 为 `sha256:18c1e7c3c082ad475e3a4b714b96e3f3e385d08deaa6384ec5c944ba0143eb56` / `sha256:48520c667024f7d9e94f9d696c37eb089e0cca115c8a87d7b5f72df4a0180c56`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260814T002542Z-before-2.8.61-beta.20` 已通过数据库、KB/Qdrant tar、beta.19 镜像 gzip 与 SHA 清单校验；仅重建 backend/frontend，状态服务 ID 不变，六容器 healthy/restart=0。
- 运行版本、commit、digest、V115、Nginx、路由、匿名 JSON 401、错误方法 JSON 405、两轮公网 smoke 和稳定窗口均通过。即时应用回滚目标为 beta.19；真实新 Keycloak Client/HUMAN 成功调用仍待业务验收，生产未修改。

- 冻结提交 `2343b9bbafd6` 已推送远程 `main` 并发布不可变候选 `2.8.61-beta.19`；backend/frontend ACR index digest 分别为 `sha256:36f9591b78b9f2c22f2dd5c435f0e2d1dbd693978c195dbe6f241c958184bda7` 与 `sha256:0958cbe7b5614c16548895c233afa828545c1be6775bfd160aefbb0bfb4de0a7`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260813T153050Z-before-2.8.61-beta.19` 已通过 PostgreSQL 容器内 `pg_restore`、KB/Qdrant tar 和 beta.18 旧镜像 gzip 校验，全部工件权限为 `0600`；即时应用回滚目标为 `2.8.61-beta.18`。
- UAT 仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 未改变。六容器 healthy/restart=0，运行版本/commit/digest、health、Flyway V114、Nginx、公开 smoke、系统 API 路由 200、匿名目录 JSON 401 和稳定窗口均通过。
- UAT 当前未提供可复用的平台管理员登录态，因此抽屉与完整调用文档的授权态视觉/交互验收仍待平台运营人员完成；生产未修改。
