---
kind: task-status
task_id: TASK-292
status: done
updated_at: 2026-08-12T13:05:08Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-176-platform-managed-web-tools.md
depends_on: none
---

# TASK-292 - 平台联网搜索与网页抓取集成

## 范围

- 两张平台配置卡、密钥加密、草稿校验和连接检测。
- Responses API 联网搜索/网页抓取客户端与两个可治理内置工具。
- 工具目录、Agent/Skill 授权和运行时分派。
- 定向测试、完整前端测试、构建、本地 main 归并与开发环境更新。

## 完成条件

- 满足 `FEAT-176` 验收标准且 Tavily 行为不回归。
- 不提交真实凭据，不修改历史迁移，不夹带主工作树改动。
- 本地开发环境只从 AgentCiCi 本地 `main` 的明确提交构建。

## 验证结果

- 后端客户端、配置和运行时定向共 16 项通过；后端 package 通过。
- 搜索请求断言只含 `web_search`，抓取请求断言同时含 `web_search` 与 `web_extractor`；reasoning 不投影。
- 前端完整 46 文件/249 项测试和生产构建通过；仅保留既有 chunk size warning。
- Spring 平台集成用例在应用启动前被共享测试库既有 Flyway V81 checksum 漂移阻断；未 repair、未修改历史迁移，本任务无迁移。
- 功能提交 `9a8cb9a` 已合并本地 `main@1f362c7`；backend/frontend 从该提交构建为 `2.8.62-dev.1f362c7`。
- `https://cici.localhost/platform/integrations` 返回 200；匿名 API 返回 `401 application/json`；两容器 healthy/restart=0，镜像 label、容器环境和 backend 版本 API 均回读 `1f362c7d86d8`。
- 完整 `./stack verify` 通过；浏览器进入平台登录边界且无 console error/warning。
- 当前未配置真实 API Key 且无受权平台会话，真实连接与 Agent 会话业务验收待管理员完成。
