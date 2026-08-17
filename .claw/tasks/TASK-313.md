---
kind: task-status
task_id: TASK-313
feature_id: FEAT-191
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-17T11:09:36Z
updated_by: codex
---

# TASK-313 - 受治理的内部租户应用注册中心

## 范围

- 建立内部应用、版本和依赖目录，并 seed 现有三类租户应用。
- 提供平台管理员目录治理 API 和发布门禁。
- 提供租户应用动态聚合 API，兼容现有 Semattice 与 DevAutopilot 生命周期。
- 新增运营端“应用中心”页面，并把租户应用中心切为动态读取。
- 完成后端、前端、迁移和本地开发测试环境验证。
- 根据用户验收修订：新增真实运行连接、连接测试/启用、版本绑定、依赖选择器和通用 Provider 生命周期执行。

## 完成条件

- 新应用发布后无需修改租户应用中心页面代码即可显示。
- 非法清单、缺失依赖、版本不满足和依赖环失败关闭。
- 受信 API Client 与租户应用产品目录保持独立。
- DevAutopilot 现有写链路和既有 activation 不回归。
- 本地 main 提交、合并和 `cici.localhost` 验收满足工作区门禁。

## 当前证据

- 用户已确认 FEAT-191 的总体设计与三个核心原则。
- 现有工作树在任务开始时为干净 `main...origin/main`。
- `agentic-project-guidelines`、`impeccable` 和 `frontend-design` 已加载；页面 shape 已由用户确认。
- 后端 `InternalApplicationRegistryServiceTest`、`TenantApplicationCatalogServiceTest` 定向测试通过，生产 package 通过。
- 前端全量 `52` 个测试文件、`287` 个测试通过，TypeScript 与 Vite production build 通过。
- 仓库全量后端测试启动后受既有测试数据库 V81 checksum 漂移及既有 Ontology AI mock 失败阻断；运行至中止时为 `551` tests、`19` failures、`157` errors、`5` skipped。本任务定向测试不受影响，未执行 Flyway repair。
- `.claw` 全仓校验器受既有历史格式债务阻断；输出未报告 FEAT-191 或 TASK-313，本任务文件 front matter 符合当前 schema。未借机改写历史任务与规格。
- 实现提交 `1b0776e0a60d` 已直接进入 AgentCiCi 本地 `main`；远端 `main`、UAT 与生产均未修改。
- 本地 Flyway V120 成功，目录回读 `agentcici/devautopilot/semattice` 均为 `PUBLISHED / 1.0.0`；目录 SPA 为 HTTP 200，匿名目录 API 为 JSON 401。
- backend/frontend 从本地 `main@1b0776e0a60d` 构建为 `2.8.61-dev.1b0776e`，均 healthy、restart=0；后端 `/system/version`、前端静态资源名与提交指纹一致。
- `cc-local-stack ./stack verify` 通过部署域名门禁、共享数据库隔离、TLS、OIDC、应用健康/版本与匿名鉴权边界。
- Chrome 原运营平台页的登录态在重载后过期并跳转登录页；未读取浏览器存储或绕过认证，授权态应用中心、发布和租户应用卡片视觉验收待平台管理员登录后完成。
- 按用户反馈将原用户界面名称“租户应用目录”统一调整为“应用中心”，保留路由、API 和内部 catalog 模型；相关 3 个测试文件、27 项通过，production build 通过。
- 命名提交 `2188e5760087` 已进入本地 `main`；前端从该提交构建为 `2.8.61-dev.2188e57`，正式路由 HTTP 200，运行 JS 回读 9 处“应用中心”且无“租户应用目录”，容器 healthy/restart=0。

## 本轮重开范围

- 用户确认运营控制台可以由超级管理员配置真实服务地址；连接作为独立部署拓扑对象，不写入不可变应用版本。
- 落地连接/修订模型、测试与启用 API、应用详情运行连接工作区、版本和依赖选择器。
- 落地通用 `tenant_application_operation` 步骤执行器及 Provider 标准生命周期回调，保持现有三类专用适配器兼容。
