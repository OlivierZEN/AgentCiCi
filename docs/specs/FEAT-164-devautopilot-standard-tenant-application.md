---
kind: feature-spec
feature_id: FEAT-164
title: DevAutopilot standard tenant application
status: implemented
owner_role: integration-agent
task_ids: TASK-275
related_decisions: ADR-006, ADR-007, ADR-008
related_issues: none
updated_at: 2026-08-08T00:00:00Z
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
- 为每个租户创建一个主产品经理 Agent 和对应 PM SERVICE Principal；显示名称由开通表单提供。
- 为每个租户创建开发者角色配置；租户管理员随后可按需新增任意数量的开发者 SERVICE Principal，名称由租户自定义。
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
4. 创建本租户 PM SERVICE Principal、PM Agent、Tool/Skill binding，并将资源映射写入 activation。
5. 写入开发者角色配置；不创建默认 developer secret。
6. 请求 DevAutopilot 健康/entitlement 探针；所有回执一致后置为 `ACTIVE`。

暂停以 activation 门禁为先：先将 desired state 置为 suspended，使运行时立即 fail closed；随后暂停本 activation 资源清单中的 PM 和 developer SERVICE Principal 并同步 Semattice Principal 状态。任何后续步骤失败时保持 `SUSPENDING` 且入口持续关闭。恢复按反向顺序执行并重新验证。

## API 契约

提供方为 AgentCiCi；详细版本化 HTTP 契约由本规格实现后在 AgentCiCi API 文档维护。

| 方法 | 路径 | 语义 |
|---|---|---|
| POST | `/api/platform/tenants/{companyId}/applications/devautopilot/activations` | 创建或幂等重放开通操作；输入模板版本、PM 显示名/别名/owner 和 `Idempotency-Key` |
| GET | `/api/platform/tenants/{companyId}/applications/devautopilot` | 返回 activation、依赖、资源摘要、最近操作与安全错误码 |
| POST | `/.../suspensions` | 请求暂停，不删除数据 |
| POST | `/.../resumptions` | 请求恢复 |
| POST | `/.../developer-principals` | 创建一个租户自定义名称的 developer SERVICE Principal；Secret 仅一次返回 |
| PATCH | `/.../resources/{resourceId}` | 更新 display name/resource alias/owner；不得改变角色或越权 scope |

所有写接口要求 `Idempotency-Key`、平台管理员授权、company path 与已认证操作范围一致；状态更新使用 revision 并在冲突时返回 409。资源 API 不接受调用方指定 tenant、principal、scope、Semattice tenant ID 或 Client Secret。

## 安全与隔离

- 每个 PM Agent、SERVICE Principal、Client 和 Secret 都属于且仅属于一个 `company_id`。
- PM SERVICE 仅具备标准模板的 Semattice 最小 scope；developer 仅具有 developer scope。租户不能通过改显示名改变角色。
- DevAutopilot 在每个请求前向 AgentCiCi 解析 activation，缺失/暂停/未知状态一律拒绝；不得依赖前端隐藏入口。
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
- [x] AgentCiCi 编排/API/平台页面。
- [x] Semattice 标准基线契约与实现。
- [x] DevAutopilot runtime gate。
- [ ] 双租户 UAT E2E。
