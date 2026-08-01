---
kind: feature-spec
feature_id: FEAT-154
title: DEV Autopilot 受治理机器身份生命周期
status: implemented
owner_role: backend-agent
task_ids: TASK-262
related_decisions: "AgentCiCi 是全局 Principal 与凭据权威；Semattice 只保存租户投影和授权事实"
related_issues: none
updated_at: 2026-08-01T15:46:00Z
updated_by: MANAGER-001
---

# FEAT-154 - DEV Autopilot 受治理机器身份生命周期

## 背景与目标

DEV Autopilot 需要一个由人类产品总监负责的产品经理机器主体和开发者机器主体。现有 FEAT-145 已建立 HUMAN/SERVICE Principal、Keycloak confidential client、一次性密钥返回、负责人关系和短时 Semattice OACT，但管理面仅支持创建，缺少查询、密钥轮换、暂停、恢复、撤销和负责人移交。

本功能补齐机器身份的完整治理生命周期，使外部开发智能体可以使用可撤销的 client credentials 获取短时 OACT，同时让所有高影响管理动作可审计。

## 范围

### In Scope

- 组织管理员查询本组织机器主体及其状态、负责人、client ID、scope 和最近轮换时间；永不返回密钥。
- 创建机器主体时继续只返回一次 client secret，并记录创建审计。
- 轮换 client secret；旧密钥由 Keycloak 立即失效，新密钥仍只返回一次。
- 暂停、恢复、永久撤销机器主体；暂停和撤销立即禁止获取新的 Keycloak/OACT 令牌。
- 将机器主体的 PRIMARY 人类负责人移交给同组织有效人类成员。
- 所有操作限定当前公司、要求组织管理员，并写入脱敏平台审计。

### Out Of Scope

- 不在 AgentCiCi 保存 client secret、Keycloak access token 或 Semattice OACT。
- 不让机器主体成为公司成员，也不为其创建人类登录会话。
- Semattice Principal 投影、租户角色及 DevAutopilot CLI 由对应仓库的独立规格交付。

## 生命周期与安全设计

- `ACTIVE`：Keycloak client 启用，identity binding 有效，可按已授权 scope 交换 OACT。
- `SUSPENDED`：Keycloak client 禁用且 Principal 暂停；保留负责人和历史，可恢复。
- `REVOKED`：Keycloak client 禁用，Principal、identity binding 和 owner 关系终止；不可恢复。
- 轮换先由 Keycloak 生成新 secret，成功后更新 `last_rotated_at`；只在 HTTP 响应中返回新 secret。
- 负责人移交在单一事务内结束旧 PRIMARY 关系并建立新 PRIMARY 关系。
- OACT 最长十分钟；暂停/撤销不能追回已签发令牌，因此资源端还需以短 TTL 和 Semattice 投影状态共同限制。

## API 契约

- `GET /admin/service-principals`
- `POST /admin/service-principals`
- `POST /admin/service-principals/{principalId}/rotate-secret`
- `POST /admin/service-principals/{principalId}/suspend`
- `POST /admin/service-principals/{principalId}/activate`
- `POST /admin/service-principals/{principalId}/revoke`
- `POST /admin/service-principals/{principalId}/transfer-owner`

所有响应均使用现有 `ApiResponse`；只有 create/rotate 响应包含一次性 `clientSecret`。

## 验收标准

- 同公司管理员可创建、查询、轮换、暂停、恢复、撤销和移交负责人。
- 跨公司 principal ID 一律按不存在/无权处理；普通成员不能调用管理 API。
- 暂停或撤销后 OACT 交换失败；恢复仅适用于 SUSPENDED。
- 轮换后旧 secret 失效，新 secret 可完成 client credentials 与 OACT exchange。
- 审计不包含 secret 或 token；定向单元测试、后端 package、迁移回归和生产 smoke 通过。

## 风险与回滚

- Keycloak 状态变化与关系库事务不是分布式事务；服务以失败关闭为原则，只有远端操作成功才推进本地状态，异常需通过同一幂等管理操作重试。
- 应用镜像可回滚；已完成的密钥轮换和撤销不可通过代码回滚恢复旧 secret。

## 实现进展

- 已随 AgentCiCi `2.8.38` 上线并通过 TASK-262 生产验收：两台 SERVICE 已由产品总监负责，查询/轮换/暂停/恢复/撤销/移交/审计齐备，旧 secret 失效与暂停阻断 CLI 均已验证。
