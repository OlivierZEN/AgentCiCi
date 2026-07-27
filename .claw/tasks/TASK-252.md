---
kind: task-status
task_id: TASK-252
status: in_progress
updated_at: 2026-07-27T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-252.yaml
spec_path: docs/specs/FEAT-145-unified-principal-identity-governance.md
---

# TASK-252 - 统一 Principal 身份与治理模型设计

## Current State

- Status: `in_progress`
- Next action: 完成 Keycloak 生产管理客户端配置与端到端邀请/机器账户验收，随后按 Runbook 合并发布。
- Blocked: none

## Scope

- 覆盖人类主体、机器主体、Keycloak 身份绑定、公司成员、责任人、应用成员投影与生命周期。
- 实现 AgentCiCi Principal、受控人类邀请、Keycloak 绑定、机器主体/责任人、迁移、测试、发布与验收。
- Semattice 运行时改造在其独立仓库任务中实施，保持接口与事件契约一致。

## Evidence

- 已核对现有 `user_account`、V96 `account_external_identity`、V97 `public_id` 与 `company_member`：当前人类账户、OIDC 绑定和成员已分层，但邀请不创建 Keycloak 用户且直接激活。
- 已核对 Semattice 当前 JWT/JWKS verifier：资源服务可本地验证可信 issuer，JWKS 缓存五分钟，不逐请求回调 IdP；本规格在此基础上增加 HUMAN/SERVICE Principal 投影与本地授权校验。
- 本任务只改规格和项目状态，未运行代码测试。
- 已实现 V98 Principal/Identity/Service Principal 基座、受控邀请、首次 OIDC 激活和机器账户责任人 API；已在一次性 PostgreSQL 16 中验证 V1→V98 迁移与兼容映射。

## Handoff

- 规格：`docs/specs/FEAT-145-unified-principal-identity-governance.md`。
- 分支：`main`。
