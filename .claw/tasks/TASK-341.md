---
kind: task-status
task_id: TASK-341
integration_id: INT-029
feature_id: FEAT-205
status: done
priority: critical
primary_project: agentcici
owner_role: integration-agent
claimed_by: codex
spec_path: docs/specs/FEAT-205-application-version-mcp-binding.md
updated_at: 2026-08-28T21:50:00+08:00
updated_by: codex
---

# TASK-341 - 外部应用 MCP Provider 正式绑定

## 范围

- 扩展应用版本、MCP Server 和租户应用绑定数据模型。
- Keycloak client_credentials、Secret 加密、精确 Server 路由和管理 UI。
- DevAutopilot 六工具由外部 MCP 优先执行，保留可控迁移边界。

## 当前进展

- V126、后端服务/API/Nginx 路由、应用 manifest v2 和两个管理页面已进入本地 `main`；核心实现提交为 `d42e4673076e`，路由补丁为 `59cb5a0219d4`。
- 后端聚焦测试、离线 package、前端 production build 和 diff check 通过；在并行主线推进后，backend/frontend 均从最新本地 `main@9191e5a3eacf` 构建并运行当前唯一 DEV 基础版本 `2.8.68-dev.9191e5a`，该提交包含 INT-029 实现，容器 healthy、restart=0，首页制品回读同版本。
- 租户 `org3gxskla32gln3bvop` 已通过正式 API 将 `devautopilot/1.0.0/devautopilot.mcp` 绑定到 MCP Server `1`，PUT/GET 均为 200，状态回读 `ACTIVE`。
- AgentCiCi 使用 Keycloak client_credentials 发现外部 Provider 的六个工具，缓存回读 `ready/6`；Server API 只返回 `clientSecretConfigured=true`，未暴露 Secret。
- 可回滚写探针通过：外部 MCP 创建专用验收 `dev_project`，Semattice 回读 `record_id=01a048a4-b92a-771a-be08-6d1bd56ac57a / revision=1 / readback_verified=true`；随后由同一外部工具移入回收站，回执 `lifecycle_state=trashed / revision=2 / retention=30 days`。
- 标准 `./stack verify` 被任务外 Semattice 基础版本漂移 `config=1.0.7 / repository=1.0.8` 按规则失败关闭；未修改该配置。正式入口、目标容器、绑定、工具发现和真实外部查询均已定向通过，远程、UAT、生产未修改。

## 回滚

停用租户应用绑定即可停止外部调用；数据库新增表和列前向保留，不删除 Secret 或业务数据。恢复上一 AgentCiCi 制品后旧 MCP Server 配置仍可读取。
