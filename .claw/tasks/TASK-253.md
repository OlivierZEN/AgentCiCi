---
kind: task-status
task_id: TASK-253
status: done
updated_at: 2026-08-04T15:04:32Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-253.yaml
spec_path: docs/specs/FEAT-146-billing-company-member-query-repair.md
---

# TASK-253 - 计费用量公司成员查询修复

## Current State

- Status: `done`
- Next action: 用户已授权合并历史分支。修复已被 TASK-254 提前吸收并发布，本次只完成分支历史集成，未重复引入业务差异。
- Blocked: none

## Evidence

- 线上组织管理端截图显示：`Could not resolve attribute 'org' of 'UserEntity'`。
- 当前 `UserEntity` 只有 `company` 关联；失败查询位于 `BillingUsageMeteringService.activeBuilderSeatUsers`。

## Scope

- 仅修改计费服务的实体路径与定向回归测试。
- 不修改计费策略、迁移、前端、主线或生产环境。

## Integration Note

- `TASK-254` / `FEAT-147-company-id-completeness-audit.md` 已将同一 JPQL 修复纳入主线并发布；本任务分支于 2026-08-04 按用户要求合并，保留历史追溯。
