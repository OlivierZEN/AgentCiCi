---
kind: task-status
task_id: TASK-203
status: done
updated_at: 2026-07-21T05:16:00Z
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

- 已完成 `TASK-203-DEMO-V2`：16 Account、30 Contact、8 Lead、21 Opportunity、30 Task、45 Event、8 Contract、8 Case，全部归 SalesA 所有。
- AgentCiCi 已写入 30 份确认互动档案、30 条记忆、30 条五维信号、16 个客户评分快照和 12 条证据驱动待确认动作。
- Owen/SalesA API 回读 `visibleAccounts=16`，新客/老客各 8；八个筛选分别为 `4/8/1/7` 与 `4/5/5/8`，全部非零。
- SalesA 通过最小权限集 `cac203DemoVis01` 获得 Contract/Case 只读权限；operation `ope202682B741D7w0fRu` 已验证并有可执行 rollback plan。
- 2026-07-21 发现后续经营分析演示复用原 16 个客户并补充合同/赢单商机，导致原 8 个新客全部按实时规则归入老客；不是 CRM 记录丢失。
- 已追加隔离批次 `TASK-203-NEW-PIPELINE-R1`：8 Account、7 Contact、8 Lead、7 个开放 Opportunity、16 Task、16 Event，全部归 SalesA 且不创建合同或赢单商机。
- SalesA 实时投影验收为可见客户 124、新客 60；隔离批次 8 个客户全部命中新客，6 个命中默认重点推进、2 个命中风险。真实页面默认“新客户推进”已显示 6 条，CloudCC 连接正常且控制台无 error/warn。

## Next Action

- 无。后续经营分析造数不得复用 `TASK-203-NEW-PIPELINE-R1` 客户，也不得给该批次创建合同或 `7-签约关单` 商机。
