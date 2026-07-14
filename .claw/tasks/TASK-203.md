---
kind: task-status
task_id: TASK-203
status: in_progress
updated_at: 2026-07-14T06:28:42Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-203.yaml
spec_path: docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md
---

# TASK-203 - 客户互动工作台全场景演示数据

## Scope

- 扩展绑定的 CloudCC CRM 与 AgentCiCi 演示组织数据，覆盖 FEAT-109 全部场景。
- 修复 V1 数据只对 SalesB 可见的问题，确保 Owen/SalesA 可完整演示。
- 保持脚本幂等、操作可审计、数据可回滚，不自动确认或写回经营动作。

## Current State

- 已验证 Owen/SalesA CRM 会话正常但可见 Account 为 0；CCAdmin/SalesB 可见 110。
- TASK-172 的 10 个核心客户全部归 SalesB 所有，且脚本创建 Account 时未显式设置 owner。
- FEAT-109 已给出新客、老客、互动、评分、记忆和动作完整场景矩阵。

## Next Action

- 完成任务授权验证并推送分配记录到 `origin/main`。
- 扩展 V2 数据脚本，先做 CRM 字段/选项 dry-run 和生产备份，再执行写入。

