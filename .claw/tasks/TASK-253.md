---
kind: task-status
task_id: TASK-253
status: in_progress
updated_at: 2026-07-29T12:00:09Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-253.yaml
spec_path: docs/specs/FEAT-146-billing-company-member-query-repair.md
---

# TASK-253 - 计费用量公司成员查询修复

## Current State

- Status: `in_progress`
- Next action: 修复构建者席位 JPQL 的过期 `org` 属性，并验证组织管理员计费用量总览。
- Blocked: none

## Evidence

- 线上组织管理端截图显示：`Could not resolve attribute 'org' of 'UserEntity'`。
- 当前 `UserEntity` 只有 `company` 关联；失败查询位于 `BillingUsageMeteringService.activeBuilderSeatUsers`。

## Scope

- 仅修改计费服务的实体路径与定向回归测试。
- 不修改计费策略、迁移、前端、主线或生产环境。
