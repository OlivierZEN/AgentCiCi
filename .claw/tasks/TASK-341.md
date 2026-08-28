---
kind: task-status
task_id: TASK-341
integration_id: INT-029
feature_id: FEAT-205
status: in_progress
priority: critical
primary_project: agentcici
owner_role: integration-agent
claimed_by: codex
spec_path: docs/specs/FEAT-205-application-version-mcp-binding.md
updated_at: 2026-08-28T14:30:00+08:00
updated_by: codex
---

# TASK-341 - 外部应用 MCP Provider 正式绑定

## 范围

- 扩展应用版本、MCP Server 和租户应用绑定数据模型。
- Keycloak client_credentials、Secret 加密、精确 Server 路由和管理 UI。
- DevAutopilot 六工具由外部 MCP 优先执行，保留可控迁移边界。

## 当前进展

- V126、后端服务/API/路由、应用 manifest v2 和两个管理页面已实现。
- 后端 package、聚焦测试和前端 build 已通过；最终提交和本地全链路待完成。

## 回滚

停用租户应用绑定即可停止外部调用；数据库新增表和列前向保留，不删除 Secret 或业务数据。恢复上一 AgentCiCi 制品后旧 MCP Server 配置仍可读取。
