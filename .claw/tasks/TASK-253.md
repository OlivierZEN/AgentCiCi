---
kind: task-status
task_id: TASK-253
status: canceled
updated_at: 2026-07-29T12:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-253.yaml
spec_path: docs/specs/FEAT-146-billing-company-member-query-repair.md
---

# TASK-253 - 计费用量公司成员查询修复

## Current State

- Status: `canceled`
- Next action: 已由 TASK-254 的完整 company_id 审计替代；不得单独合并本任务分支。
- Blocked: none

## Evidence

- 线上组织管理端截图显示：`Could not resolve attribute 'org' of 'UserEntity'`。
- 当前 `UserEntity` 只有 `company` 关联；失败查询位于 `BillingUsageMeteringService.activeBuilderSeatUsers`。

## Scope

- 仅修改计费服务的实体路径与定向回归测试。
- 不修改计费策略、迁移、前端、主线或生产环境。

## Superseded By

- `TASK-254` / `FEAT-147-company-id-completeness-audit.md` 将重新纳入本任务的 JPQL 修复，并补齐当前可执行脚本中的遗留路径。
