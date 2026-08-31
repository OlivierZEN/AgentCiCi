---
kind: feature-spec
feature_id: FEAT-205
title: 应用版本与外部 MCP Provider 工具绑定
status: implementation
owner_role: integration-agent
task_ids: [TASK-341, TASK-345]
related_decisions: INT-029
updated_at: 2026-08-31T03:26:04Z
updated_by: codex
---

# FEAT-205 - 应用版本与外部 MCP Provider 工具绑定

## 背景与目标

复用 AgentCiCi 已有 MCP Server 配置能力，并补齐应用中心缺失的正式治理关系：`App Version → MCP Provider → Tool 集合 → Tenant MCP Server`。外部应用只需发布 MCP endpoint 和声明工具，无需在 AgentCiCi 增加专属执行服务。

## 范围

- 应用版本 manifest v2 声明 Provider、Keycloak audience/scope 和允许工具集合。
- 租户把已配置 MCP Server 精确绑定到应用版本 Provider；绑定时执行工具发现并校验集合。
- MCP Server 支持加密保存 Keycloak client secret，以 `client_credentials` 取得短时 token，永不向前端回传 secret。
- 应用声明的 MCP 工具作为租户工具目录的一等能力显示，来源必须明确为应用、Provider 和绑定 Server，不能标记为内置工具。
- 研发交付六工具只允许按当前租户有效应用绑定路由；缺少绑定、绑定停用或 Server 停用时明确失败关闭，不得调用 AgentCiCi 内部 Semattice Service，也不得从全局同名 MCP 工具中模糊选择。
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
- 旧研发交付内置工具从 `BuiltinToolCatalog`、平台内置治理数据和本地运行分发中移除；智能体既有白名单名称保持不变，由应用绑定 MCP 目录接管，因此不迁移业务 Agent ID 或工具名。
- 应用版本声明过的工具名属于应用契约保留名；即使租户尚未绑定，也不能落入通用 MCP 或内部实现。
- Provider 先发布并通过鉴权/工具发现，消费方再启用绑定；回滚只需停用绑定。

## 智能体编译与发布

- Agent Builder 的工具白名单只消费统一工具目录：内置工具、租户自定义工具、当前租户 ACTIVE 应用绑定 MCP 工具。
- 编译产物必须记录工具名和应用 MCP 风险元数据；白名单变化必须进入编译指纹并生成新的 DRAFT 版本。
- `web` 交互渠道与外部 Web 浮窗配置相互独立。工作流发布不要求浮窗的 `widgetKey / Origin / runAsUser`；公开浮窗配置和 Token 签发仍在自身服务边界严格校验完整配置。

## 验收标准

- 保存/读取配置不会泄露 secret；token 缓存短于 access token 有效期。
- 绑定拒绝跨租户 Server、错误认证要求、缺失工具或未发布版本。
- 工具执行只命中绑定的 Server，未绑定时不虚报外部集成完成。
- `/tools` 中六个研发交付工具显示为应用 MCP 来源且不再显示“内置”；未绑定租户不显示这些工具。
- 调整应用 MCP 工具白名单后，后端产生新 DRAFT，页面允许执行发布；无变化编译仍不新增版本。
- Flyway、后端聚焦测试/package、前端 build 通过；真实本地环境另做 Keycloak 与业务链路验收。

## Out of Scope

- 不修改 DevAutopilot 工具业务 Schema，不直接访问 Semattice 数据库。
- 不发布远程、UAT 或生产，不自动给 Keycloak Client 扩权。
