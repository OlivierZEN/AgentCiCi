---
kind: task-status
task_id: TASK-243
status: done
updated_at: 2026-07-24T11:53:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-243.yaml
spec_path: docs/specs/FEAT-136-keycloak-unified-identity-and-official-access.md
---

# TASK-243 - Keycloak 统一身份与官方应用访问

## Scope

- 基于线上 AgentCiCi `2.8.9`/V95 基线实现 Keycloak 外部身份到全局账户的映射、OIDC BFF 和 OACT 签发；
- 用当前 `company_id -> semattice_tenant_id` binding 确定面向 Semattice 的公司上下文；
- 提供部署、密钥轮换、验证和回滚步骤，并协调 Semattice 的本地 JWKS 资源服务发布。

## Constraints

- 不提交真实 client secret、私钥、refresh token 或用户密码；生产密码迁移不读取或导出真实用户凭据。
- 不把 OACT 用作第三方服务凭据；第三方必须走独立 Keycloak service-account client。
- 不在未完成 V94 维护窗口协调、定向验证、备份和发布 dry-run 前部署 AgentCiCi 认证变更。

## Progress

- 已验证 Keycloak 26.7.0 基础服务、`agentcici` realm 与 discovery/JWKS 可用；已验证遗留 PBKDF2 兼容格式的无真实数据导入。
- 已切换到线上 tag `2.8.9` 的独立干净工作树，避免把旧 V52 迁移基线发布到生产。
- 已实现 V96 `account_external_identity`、OIDC Authorization Code + PKCE BFF 回调、服务端一次性 state/completion ticket、加密 refresh-token Redis 存储、RS256 OACT 签发与公开 JWKS。OACT 不是浏览器 API，只供 AgentCiCi 服务内调用。
- 已确认生产共有 24 个 `user_account`、31 个活跃成员、2 条独立密码凭据；Keycloak 业务 realm 当前 0 用户。已将 `agentcici-bff` callback 精确限制为 `https://x.agentcici.com/auth/oidc/callback`。
- 已定位并修复租户应用页的 Semattice 状态误报：页面原先只以本页内存状态渲染，刷新后固定回退为 `NOT_PROVISIONED`；新增平台只读状态接口，按 `semattice_provisioning_binding.company_id` 读取真实绑定。无记录时才返回 `NOT_PROVISIONED`，`RESERVED` 映射为页面“开通中”。
- 已发布生产 `2.8.12 / 6574f168234e`。后端与前端均已切换；真实公司 `org2sva14i4udjmi2t4s` 的 binding 为 `PROVISIONED`，新接口匿名请求为预期 `401`，页面可在具备平台角色的会话中读取并显示“已开通”。
