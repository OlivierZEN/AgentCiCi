---
kind: task-status
task_id: TASK-231
status: review
updated_at: 2026-07-23T10:20:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-231.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-231 - 通用记忆生命周期与组织清理闭环

## Scope

- 将通用主体、记忆记录、会话摘要、候选、证据、向量片段和 API 绑定纳入既有组织导出、dry-run manifest、real purge 及残留行校验；
- 提供通用主体记忆的撤销/删除和过期清理服务，删除或过期必须同步删除向量并使关系型记录不可读取；
- 删除动作遵从组织 legal hold，输出最小审计信息，不记录主体正文、原始向量或秘密；
- 导出保持关系型、脱敏和最小必要原则，禁止导出原始 embedding 或跨组织数据。

## Non-goals

- 不实现任何外部应用、渠道、客户、订单或工单领域模型；
- 不绕过组织 retention policy、legal hold 或现有平台 purge 认证；
- 不执行真实生产 purge、生产发布或创建外部业务数据。

## Acceptance

- 组织 dry-run、导出与 real purge 覆盖所有通用记忆表和凭据绑定，purge 后关系库及向量均不可再召回；
- 主体删除、撤销和过期记录会同步删除对应向量，并保留不含正文的审计事件；
- legal hold 激活时删除与 purge 安全拒绝，其他聊天和 OpenAPI 主链路不受生命周期清理失败影响；
- 后端定向权限、生命周期、检索回归、全新数据库迁移和静态检查通过。

## Progress

- 通用主体删除会撤销并脱敏关联记录、候选和主体标识，删除会话摘要；每条记录都会请求删除派生向量。向量物理删除失败不恢复关系型可读状态，保留活跃片段以便后续重试，并记录不含正文的失败数量。
- 已新增过期清理 worker：仅清理已到期的 `ACTIVE/VERIFIED` 记录；legal hold 激活时跳过该组织的物理向量清理和状态更新。
- `PlatformTenantLifecycleService` 的 dry-run manifest、导出表集合、real purge 删除顺序、残留行校验与向量巡检/删除已覆盖六张通用记忆表及 API 记忆绑定；向量删除汇总同时包含知识库和通用记忆向量 ID。
- 定向生命周期、语义检索、可信上下文和迁移测试及后端编译通过。既有 `PlatformTenantLifecycleIntegrationTest` 仍因共享测试库 V81 checksum 不一致而无法加载 Spring Context；该失败早于本任务，未执行 Flyway repair 或修改历史迁移。
