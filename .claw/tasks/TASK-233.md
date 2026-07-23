---
kind: task-status
task_id: TASK-233
status: ready
updated_at: 2026-07-23T10:30:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-233.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-233 - 通用记忆人工管理与生产就绪审计

## Scope

- 提供已生效通用记忆的最小查询、撤销及按应用主体删除 API，所有操作均同时验证组织、目标 Agent 的 `MANAGE` 权限、主体归属和 legal hold；
- 撤销必须同步移除派生向量并产生不含正文的审计事件；主体删除复用既有生命周期服务；
- 建立 FEAT-131 逐项生产就绪审计，记录代码、迁移、定向测试、适配契约、生命周期与已知环境限制的证据。

## Non-goals

- 不新增外部应用领域对象、自动写入、前端页面、生产发布或测试之外的外部业务数据。

## Acceptance

- 无权限、跨组织、跨 Agent、已撤销记录或 legal hold 请求均安全拒绝；
- 管理 API、生命周期、审核、Trace/评测和两份适配契约形成可复核证据；
- 不因既有共享库 Flyway V81 checksum 漂移而伪报全量集成测试成功。

## Progress

- V90 为已生效记忆增加归属 `agent_id`；候选审核生成记录时固化此归属。管理 API 只返回最小记录视图，并提供按 Agent 撤销和按应用主体删除入口。
- 主体删除前验证主体的全部记录均归属目标 Agent；跨 Agent 主体删除被拒绝。撤销或删除始终调用派生向量清理并立即撤销/脱敏关系型记录。
