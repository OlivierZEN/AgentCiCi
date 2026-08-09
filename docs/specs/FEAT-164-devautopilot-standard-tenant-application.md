---
kind: feature-spec
feature_id: FEAT-164
title: DevAutopilot standard tenant application
status: in_implementation
owner_role: integration-agent
task_ids: TASK-275
related_decisions: ADR-006, ADR-007, ADR-008
related_issues: none
updated_at: 2026-08-09T02:00:00Z
updated_by: codex
---

# FEAT-164 - DevAutopilot 标准多租户应用模板

## 背景与目标

AgentCiCi 的平台租户应用页已经将 AgentCiCi 和 Semattice 作为独立应用展示和开通。DevAutopilot 需要成为第三个可开通的组合型租户应用：共享无状态运行时，但每个租户必须拥有独立的 Agent、SERVICE Principal、机器凭据、Semattice Principal 投影、业务数据和审计链。

本功能以 `devautopilot-standard@1.0.0` 为平台签名、版本化模板。模板定义逻辑角色和最小权限，租户管理者定义实际名称和账号数量。`大乔`、`悟空`等仅是历史租户显示名，不能作为固定账号、全局 Client ID 或跨租户授权依据。

## 范围

### In Scope

- 平台应用目录中增加 `devautopilot` activation 及其操作、资源和审计记录。
- 开通、重试、暂停、恢复、状态查询；所有操作使用稳定幂等键与关联 ID。
- 平台只开通应用和数据基线，不代替租户创建或指定任何人、Agent 或机器主体。
- 租户 ORG_ADMIN 在 AgentCiCi `/admin/service-principals` 创建主产品经理 Agent/对应 PM SERVICE Principal，以及任意数量的开发者 SERVICE Principal；显示名称由租户自定义。
- 受控调用 Semattice 标准基线应用，校验回执、模板版本和资源映射。
- 平台租户应用页展示依赖、初始化步骤、失败原因、模板版本和生命周期动作。
- 暂停应用时关闭运行时授权并暂停本应用拥有的 SERVICE Principal；不删除业务数据。

### Out Of Scope

- 自动创建带可分发 Secret 的默认开发者账号。
- 在浏览器、模板配置、日志或审计文本中保存 Client Secret、OACT 或私钥。
- 通过关闭应用删除 Semattice 业务记录或永久撤销身份。
- 将租户的 Agent、Principal、Client ID、会话或内存资源共享给另一租户。

## 模板与资源模型

模板只声明固定逻辑角色：

| logical_role | 基数 | 租户可配置项 | 运行职责 |
|---|---:|---|---|
| `product_manager` | 至少 1，且仅 1 个 primary | `display_name`、`resource_alias`、HUMAN owner | 查询、受控创建、设计与验收评审 |
| `developer` | 0..N | `display_name`、`resource_alias`、HUMAN owner、executor type | Coding Agent/CI 领取和交付任务 |
| `observer` | 0..N | 显示名称、HUMAN owner | 只读访问 |

每个资源均使用不可变 `principal_id`、内部 `agent_id` 和系统生成的 `client_id` 作为技术身份；`display_name` 和租户内唯一的 `resource_alias` 可变。任务归属、权限、审计、密钥轮换和跨系统关联只使用不可变标识。历史审计保留名称快照，改名不改变历史 actor。

## 控制面数据

本功能不得复用 `integration_app`。该表服务于外部连接配置，不包含模板版本、长事务 operation 或资源清单语义。新增 AgentCiCi 控制面实体：

```text
tenant_application_activation
  company_id + app_code (unique), template_code, template_version, template_digest,
  desired_state, actual_state, operation_id, idempotency_key, semattice_tenant_id,
  revision, activated_by, activated_at, last_error_code, last_error_step

tenant_application_resource
  activation_id, logical_role, resource_type, resource_alias, display_name,
  external_id, lifecycle_state, expected_version, actual_version, is_primary

tenant_application_operation
  operation_id, activation_id, step, state, attempt, correlation_id,
  request_digest, result_digest, started_at, completed_at
```

这些记录是 AgentCiCi 的租户应用控制面事实，不是 DevAutopilot 项目、任务、工时或交付事件的副本。

## 生命周期与编排

状态：`NOT_ENABLED → PROVISIONING → AWAITING_APPROVAL? → ACTIVE`；失败为 `FAILED`。暂停和恢复为 `ACTIVE → SUSPENDING → SUSPENDED → RESUMING → ACTIVE`。失败或中断保留操作与资源回执，可使用相同幂等键重试，不得重复创建资源。

开通步骤：

1. 验证 company active、调用者为平台管理员、Semattice 已 `PROVISIONED`、模板版本受支持。
2. 创建 activation/operation，锁定 `company_id + app_code`。
3. 请求 Semattice 应用 `devautopilot.standard.v1` 标准基线；已有非模板 metadata 时失败关闭，不覆盖已有模型。
4. 写入空的租户团队资源清单，不创建默认机器主体或 Secret。
5. 请求 DevAutopilot 健康/entitlement 探针；所有回执一致后置为 `ACTIVE`。
6. 租户管理员在 AgentCiCi 租户管理端按需初始化 PM 或开发者。创建 PM 时才创建 Agent、Tool/Skill binding 和 PM SERVICE Principal；创建开发者时才创建 developer SERVICE Principal。

暂停以 activation 门禁为先：先将 desired state 置为 suspended，使运行时立即 fail closed；随后暂停本 activation 资源清单中的 PM 和 developer SERVICE Principal 并同步 Semattice Principal 状态。任何后续步骤失败时保持 `SUSPENDING` 且入口持续关闭。恢复按反向顺序执行并重新验证。

## API 契约

提供方为 AgentCiCi；详细版本化 HTTP 契约由本规格实现后在 AgentCiCi API 文档维护。

| 方法 | 路径 | 语义 |
|---|---|---|
| POST | `/api/platform/tenants/{companyId}/applications/devautopilot/activations` | 创建或幂等重放应用与数据基线；输入仅为 `Idempotency-Key` |
| GET | `/api/platform/tenants/{companyId}/applications/devautopilot` | 返回 activation、依赖、资源摘要、最近操作与安全错误码 |
| POST | `/.../suspensions` | 请求暂停，不删除数据 |
| POST | `/.../resumptions` | 请求恢复 |
| GET | `/api/admin/devautopilot/team` | 当前租户读取应用状态与团队资源；公司只能从已认证会话推导 |
| POST | `/api/admin/devautopilot/team/product-managers` | 当前 ORG_ADMIN 创建唯一 PM；输入仅为显示名称，Secret 仅一次返回 |
| POST | `/api/admin/devautopilot/team/developers` | 当前 ORG_ADMIN 创建一个自定义名称的 developer SERVICE Principal；输入仅为显示名称，Secret 仅一次返回 |

平台写接口要求 `Idempotency-Key` 与平台管理员授权；租户团队写接口要求当前租户 `ORG_ADMIN` 会话。两类接口都不接受调用方指定 tenant、负责人、principal、scope、Semattice tenant ID 或 Client Secret；公司、负责人和最小 scope 均由服务端推导。

## 安全与隔离

- 每个 PM Agent、SERVICE Principal、Client 和 Secret 都属于且仅属于一个 `company_id`。
- PM SERVICE 仅具备标准模板的 Semattice 最小 scope；developer 仅具有 developer scope。租户不能通过改显示名改变角色。
- DevAutopilot 以可信 OACT 解析 company/tenant/principal，并使用短时、按租户的 activation 快照缓存避免控制面短暂抖动阻塞业务；未开通、暂停、身份不一致和缓存过期后无法复核仍一律拒绝。
- Semattice 必须从可信 OACT 推导 tenant/company，并以 Principal、RBAC、RLS、PDP 作为资源端最终门禁。
- 关闭应用只暂停资源；永久撤销、导出和数据清理走独立保留期/审批流程。

## 跨项目契约

- `INT-008`：本规格为 AgentCiCi 控制面所有者。
- Semattice 子规格：`cc-semattice/docs/specs/FEAT-059-devautopilot-standard-tenant-baseline.md`。
- DevAutopilot 子规格：`cc-dev-autopilot/docs/specs/FEAT-010-tenant-activation-runtime-gate.md`。
- 版本/兼容：模板 `1.x` 只新增向后兼容资源和字段；破坏性模板升级必须新建主版本、显式迁移和租户确认。

## 验收标准

- 两个 UAT 测试租户可用不同 PM 和 developer 显示名开通，技术资源与数据完全不同。
- 租户 A 的 OACT、PM Tool、developer CLI 和 DevAutopilot Web/API 均不能读写租户 B 数据。
- 同一激活 key 重试不重复创建 Agent、Principal、metadata 基线或操作记录。
- 暂停后所有入口 fail closed，恢复后仅恢复对应租户的原资源。
- 新增 developer 账号不会影响其他账号；密钥只一次显示、轮换和暂停均限于本租户。
- UAT 验证包含 AgentCiCi、Semattice、DevAutopilot 三侧关联 ID、回滚和负向隔离证据。

## 风险与回滚

- Semattice 基线失败：activation 保持 `FAILED`，已存在的预置资源通过资源清单补偿暂停，不删除数据。
- Agent 创建失败：不标记 active；恢复操作沿资源清单幂等重试。
- DevAutopilot 新门禁故障：关闭该模板版本的 enforcement 开关并回到 `SUSPENDED`，不得开放未知租户。
- 任一子仓回滚时，其他项目不回滚；activation 保留失败原因与最后成功步骤。

## 实现进展

- [x] 控制面模型与 migration。
- [ ] 将团队身份管理迁入 AgentCiCi 租户管理端，移除运营端人员字段。
- [ ] DevAutopilot activation 快照短时缓存与 UAT 验证。
- [x] Semattice 标准基线契约与实现。
- [x] DevAutopilot runtime gate。
- [ ] 双租户 UAT E2E。
