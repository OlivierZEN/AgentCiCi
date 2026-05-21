---
kind: feature-spec
feature_id: FEAT-043
title: Rename default PostgreSQL database to agentcici
status: implemented
owner_role: project-manager
task_ids: TASK-125
related_decisions: DEC-027
related_issues: none
updated_at: 2026-05-21T09:18:03Z
updated_by: MANAGER-001
---

# FEAT-043 - Rename default PostgreSQL database to agentcici

## 背景与目标

- 当前仓库的默认配置又回到了历史数据库名 `cici_assistant`，但本地真实 PostgreSQL 数据库已经保持在 `agentcici` / `agentcici_test`。
- 这会让当前代码默认连不到本地实际数据库，也会让 compose、部署示例和测试默认值互相不一致。
- 本次目标是把数据库名相关的默认配置重新统一回 `agentcici`，并让项目状态记录与当前本地数据库事实一致。

## 范围

### In Scope

- 重新修改本地 compose、Spring runtime/test 配置、部署默认值和辅助脚本中的数据库名默认值。
- 恢复与本次数据库名迁移直接相关的状态/规格文档。
- 验证代码默认值与本地真实数据库 `agentcici` / `agentcici_test` 对齐。

### Out Of Scope

- 不改动 `cici_assistant_token`、容器名、Java package、业务表名或历史协议字段。
- 不改动与 FEAT-046 业务实现无关的前后端功能代码。

## 现状与约束

- 当前分支为 `codex/TASK-124-feat-046-platform-tenant-provisioning`，工作树已经存在 FEAT-046 未提交改动。
- `.claw/` 当前采用精简 hot board 模式，状态更新需要保持简洁。
- 本地 PostgreSQL 当前只有 `agentcici` / `agentcici_test`，没有默认的 `cici_assistant` / `cici_assistant_test`。

## 方案设计

- 只恢复“数据库名默认值”这一条主线，不扩散到其他历史 `cici_*` 技术标识。
- 使用单独 `TASK-125` 记录这次恢复工作，但沿用当前分支，避免打断正在进行的 FEAT-046 工作树。
- 验证以 `rg` 检查关键路径、数据库存在性和后端当前连接事实为主。

## 接口与数据影响

- `jdbc:postgresql://localhost:5432/cici_assistant` -> `jdbc:postgresql://localhost:5432/agentcici`
- `jdbc:postgresql://localhost:5432/cici_assistant_test` -> `jdbc:postgresql://localhost:5432/agentcici_test`
- `POSTGRES_DB` 默认值 -> `agentcici`

## 验收标准

- 关键配置路径不再把数据库默认名写成 `cici_assistant`。
- 本地数据库仍为 `agentcici` / `agentcici_test`。
- 当前状态文档明确“代码默认值”和“本地真实数据库”已重新对齐。

## 风险与回滚

- 风险：再次被其他分支或状态整理覆盖。
- 回滚：仅需把数据库名默认值恢复成旧值；本次不涉及业务数据结构变更。

## 实现进展

- 2026-05-21：恢复任务已重新开启，准备在当前分支上把数据库名相关默认值补回。
- 2026-05-21：关键数据库名默认值已重新补回 `agentcici` / `agentcici_test`，并与当前本地 PostgreSQL 实际数据库重新对齐。
