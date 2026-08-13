---
kind: feature-spec
feature_id: FEAT-180
title: 内部应用统一用户令牌
status: in_progress
primary_project: agentcici
task_ids: TASK-299
related_integrations: INT-017
updated_at: 2026-08-13T08:30:00Z
updated_by: codex
---

# FEAT-180 - 内部应用统一用户令牌

## 目标与边界

AgentCiCi 是生态人类身份签发方。公司成员登录后获得 `ecosystem_user` RS256 Token；AgentCiCi、Semattice 与 DevAutopilot 使用同一 issuer/JWKS 验证，但各自继续执行 activation、角色、权限包、PDP/RLS 和对象权限。统一的是认证上下文，不是业务授权。

平台管理员、嵌入式应用和 SERVICE 主体不是公司人类会话，继续使用各自 Token 类型。SERVICE/CLI/高风险委托仍使用短期、单 audience OACT。旧公司 HS256 Token 没有迁移窗口，部署后用户重新登录。

## `ecosystem-user-token.v1`

- 算法：RS256；issuer 与 JWKS 由 AgentCiCi 受管配置提供。
- 类型：`typ=ecosystem_user`，`principal_type=HUMAN`，`authorized_party=agentcici`。
- audiences：始终含 `agentcici-api`、`devautopilot-api`；租户已完成 Semattice 绑定时增加 `semattice-api`。
- 身份：`sub/principal_id=account_id`，并包含 `company_id`、`member_id`、`account_id`、`roles`、`membership_version`。
- 数据上下文：租户已绑定时包含 `tenant_id` 和经 AgentCiCi 控制面批准的 Semattice scopes。
- 生命周期：默认 7200 秒，可由部署配置在 15 分钟至 12 小时之间调整；不得复用 10 分钟机器 OACT 上限。

## 流程

1. 登录成功后 AgentCiCi 直接签发生态用户令牌。
2. 用户进入 DevAutopilot 时，浏览器只携带一次性 handoff ticket；DevAutopilot 后端兑换同类生态令牌并写入 `HttpOnly + Secure + SameSite=Lax` Cookie。
3. DevAutopilot 后端用同一令牌调用 AgentCiCi 对话与 Semattice 数据 API。
4. 每个资源应用分别校验自己的 audience 和本地授权事实。

## 失败与回滚

- issuer、签名、类型、audience、主体或有效期不正确均返回 401。
- Semattice 未开通时 Token 不含 `semattice-api/tenant_id`，不能调用其业务 API。
- 回滚只回滚各应用版本并要求重新登录，不修改租户业务数据或授权投影。
