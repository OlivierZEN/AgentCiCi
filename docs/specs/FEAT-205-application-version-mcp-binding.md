---
kind: feature-spec
feature_id: FEAT-205
title: 应用版本与外部 MCP Provider 工具绑定
status: implemented
owner_role: integration-agent
task_ids: TASK-341
related_decisions: INT-029
updated_at: 2026-08-28T14:30:00+08:00
updated_by: codex
---

# FEAT-205 - 应用版本与外部 MCP Provider 工具绑定

## 背景与目标

复用 AgentCiCi 已有 MCP Server 配置能力，并补齐应用中心缺失的正式治理关系：`App Version → MCP Provider → Tool 集合 → Tenant MCP Server`。外部应用只需发布 MCP endpoint 和声明工具，无需在 AgentCiCi 增加专属执行服务。

## 范围

- 应用版本 manifest v2 声明 Provider、Keycloak audience/scope 和允许工具集合。
- 租户把已配置 MCP Server 精确绑定到应用版本 Provider；绑定时执行工具发现并校验集合。
- MCP Server 支持加密保存 Keycloak client secret，以 `client_credentials` 取得短时 token，永不向前端回传 secret。
- 研发交付六工具在存在有效绑定时按指定 Server 路由，不从全局同名工具中模糊选择。
- 管理页面支持配置 Keycloak 认证、应用版本 Provider/工具及租户绑定。

## 数据与安全

- `application_version_mcp_provider`：Provider 契约和认证要求。
- `application_version_mcp_tool`：版本允许的工具名、Schema digest 和风险等级。
- `tenant_application_mcp_binding`：租户实际 Server 绑定及启停状态。
- `mcp_server` 的 client secret 使用平台 SecretCipherService 加密；API 仅返回 configured 布尔值。
- 相同 Keycloak realm 不绕过 audience/client/scope；平台业务 RBAC 与租户隔离继续生效。

## 兼容与迁移

- 既有 `mcp_server` 和通用 MCP 工具调用保持兼容。
- DevAutopilot `1.0.0` 应用版本预置 `devautopilot.mcp` 与六工具声明，但租户必须显式绑定可用 Server 才启用外部路径。
- Provider 先发布并通过鉴权/工具发现，消费方再启用绑定；回滚只需停用绑定。

## 验收标准

- 保存/读取配置不会泄露 secret；token 缓存短于 access token 有效期。
- 绑定拒绝跨租户 Server、错误认证要求、缺失工具或未发布版本。
- 工具执行只命中绑定的 Server，未绑定时不虚报外部集成完成。
- Flyway、后端聚焦测试/package、前端 build 通过；真实本地环境另做 Keycloak 与业务链路验收。

## Out of Scope

- 不修改 DevAutopilot 工具业务 Schema，不直接访问 Semattice 数据库。
- 不发布远程、UAT 或生产，不自动给 Keycloak Client 扩权。
