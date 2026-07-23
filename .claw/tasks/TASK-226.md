---
kind: task-status
task_id: TASK-226
status: done
updated_at: 2026-07-22T15:51:44Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-226.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-226 - 通用主体记忆 Phase 1 核心

## Scope

- 建立不含任何外部应用领域命名的主体、记忆项和会话快照关系型核心；
- 定义可信服务端输入的外部应用/主体/会话上下文，并在运行时按组织、应用、主体、scope 与 Agent 权限隔离；
- 为后续向量索引、候选提炼、人工审核和 `agent_handoff` 保留通用扩展点；
- 增加定向数据、权限与上下文预算测试。

## Non-goals

- 不新增外部应用页面、路由、渠道或领域工具；
- 不写入或迁移真实外部用户数据，不发布生产；
- 不实现 Phase 2 的向量索引、自动候选写入或 Phase 3 的治理界面。

## Acceptance

- 平台核心代码、数据库迁移、API/服务命名不含外部应用或领域耦合标识；
- 相同外部主体标识在不同组织或不同 `applicationCode` 下严格隔离；
- Agent 上下文只能读取已授权、未过期且 scope 匹配的最小必要记忆；
- `mvn` 定向测试、编译和 `git diff --check` 通过；结果写入 test report。

## Progress

- 已新增 V85、通用主体/记忆记录/会话快照模型、仓储与 `ExternalMemoryContextService`；没有引入外部应用或领域命名。
- 新增 `MemoryContextPromptAssembler`，仅将已经过授权过滤的会话摘要和记忆项在严格字符预算内组装为通用提示词片段；该类尚未接入任一外部应用入口或 Chat 编排器。
- 定向测试覆盖不同 `applicationCode` 的主体隔离、scope 过滤、只读上下文不隐式创建主体，以及提示词预算小于标题时仍不越界。
- 已通过定向 JUnit、后端编译和 `git diff --check`；在新建且验证后删除的 PostgreSQL 16 临时库上，从 V1 完整迁移至 V85 并断言三张记忆表存在。
