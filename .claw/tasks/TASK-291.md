---
kind: task-status
task_id: TASK-291
status: done
updated_at: 2026-08-12T11:45:43Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-175-platform-code-interpreter-integration.md
depends_on: none
---

# TASK-291 - 平台代码解释器集成与内置工具

## 范围

- 平台托管配置、密钥加密与连接检测。
- OpenAI 兼容 Responses API 客户端与受管代码解释器工具。
- 工具目录、平台治理、Agent/Skill 授权与运行时执行接入。
- 运营端配置卡片、字段说明和检测交互。
- 定向测试、完整前端测试、构建、本地 main 归并与开发环境更新。

## 完成条件

- 满足 `FEAT-175` 验收标准。
- 不夹带主工作树已有未提交 CSS 与任务板变更。
- 本地环境只从 AgentCiCi 本地 `main` 的明确提交构建。

## 后续验收

- 平台管理员在受权页面录入百炼 API Key，执行“测试沙箱”并启用集成，再用 Agent 会话完成真实厂商调用业务验收。

## 验证结果

- 后端 `SandboxCodeInterpreterClientTest`、`SandboxCodeInterpreterServiceTest`、`ToolOrchestratorServiceTest` 共 10 项通过；后端 package 通过。
- `PlatformIntegrationGovernanceIntegrationTest` 的 Spring 上下文仍被仓库既有共享测试库 Flyway V81 checksum 漂移阻断，未执行 repair；该阻断不来自本任务且新功能无迁移。
- 前端定向 2 文件/4 项通过；完整前端 46 文件/248 项通过；生产构建通过，仅有既有 chunk size warning。
- `git diff --check` 通过；未提交真实凭据。
- 功能提交 `0c58cfb` 已合并本地 `main@8f76e39`；本地 backend/frontend 均从该主线提交构建为 `2.8.62-dev.8f76e39`。
- `https://cici.localhost/platform/integrations` 返回 SPA 200；匿名平台集成 API 返回 `401 application/json`；backend/frontend 均为 healthy、restart=0，镜像 label 和运行环境提交均为 `8f76e39abedf`。
- 浏览器无受权平台会话，仅验证登录边界且 console 0 error/warning；未配置真实 API Key，未将真实上游连接或 Agent 业务调用误报为通过。
