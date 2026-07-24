---
kind: task-status
task_id: TASK-243
status: in_progress
updated_at: 2026-07-24T10:20:00Z
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
- 下一步：先发布仅 backend 的 V96 前向迁移，受控导入并绑定 24 个现有账户，再写入 client secret/OIDC 配置并发布前端统一登录入口；之后发布 Semattice ACS JWKS 和 principal projection。
