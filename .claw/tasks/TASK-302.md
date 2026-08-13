---
kind: task-status
task_id: TASK-302
feature_id: FEAT-183
integration_id: INT-019
status: review
updated_at: 2026-08-13T12:30:00Z
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

- AgentCiCi 提供方首批收录 6 个核心契约；Semattice 通过提供方持有的 HMAC 投影聚合 11 个核心 Capability。
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
