---
kind: task-status
task_id: TASK-305
feature_id: FEAT-176
status: done
updated_at: 2026-08-14T02:20:00Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-176-platform-managed-web-tools.md
---

# TASK-305 - 平台长任务集成超时上限调整

## 范围

- 将代码解释器、联网搜索和网页抓取的可配置请求超时上限从 180 秒提升到 60 分钟。
- 统一前端数字输入约束、提示文案、后端保存校验和实际运行时 HTTP 请求上限。
- 默认值仍为 120 秒，最小值仍为 10 秒，不自动修改既有配置。

## 完成条件

- `3,600,000 ms` 可保存并用于实际客户端请求，`3,600,001 ms` 被后端拒绝。
- 平台页面明确显示最长 60 分钟且浏览器输入上限一致。
- 定向测试、前端构建、后端 package、本地主线提交和本地开发环境回读通过。

## 当前证据

- 后端 `ManagedWebToolServiceTest,SandboxCodeInterpreterServiceTest` 定向测试通过；60 分钟边界被接受，超过 1 ms 被拒绝。
- 后端 package 通过。
- 前端定向 5/5 通过，production build 通过；仅保留既有 chunk-size warning。
- 功能提交 `4f7aca02bf85` 已进入本地 main；运行 backend/frontend 版本为 `2.8.61-dev.4f7aca0`，均 healthy/restart=0。
- 部署 JS 已回读“允许 10000–3600000（最长 60 分钟）”；backend `/system/version` 的 version、commit 和 imageTag 一致。
- Compose 依赖图同时重建 Semattice 与 DevAutopilot 应用容器；共享 PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和边缘 Nginx 未重建，完整 `./stack verify` 通过。
- 当前 AgentCiCi main 的后续提交 `18f28b04` 仅含其他任务交付文档，不影响本次制品；UAT/生产未修改。
- 远程 `main@626f7e22c774` 已冻结并发布 UAT `2.8.61-beta.21`；部署前完整备份可回滚到 beta.20，仅重建 backend/frontend，所有容器 healthy/restart=0，状态服务 ID 未改变。
- UAT 平台集成路由为 200、匿名 API 为 JSON 401，部署 JS 已回读“最长 60 分钟”和 `3600000`；最新五条 Flyway 均成功，公网 smoke 和稳定窗口通过。
- UAT 未配置或调用真实长达 60 分钟的厂商任务，因此当前结论为技术发布通过，真实长任务业务验收仍待平台管理员使用受权配置完成；生产未修改。
