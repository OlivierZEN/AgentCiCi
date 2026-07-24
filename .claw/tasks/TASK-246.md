---
kind: task-status
task_id: TASK-246
status: ready
updated_at: 2026-07-24T13:20:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-platform-agent
assignment_path: .claw/assignments/TASK-246.yaml
spec_path: docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md
---

# TASK-246 - 租户详情路由标识兼容修复

## Current State

- Status: `ready`
- Next action: 完成任务级身份门禁后，归一租户标识并加入无效详情地址保护。
- Blocked: none

## Progress

- 用户截图已确认路由为 `/platform/tenants/undefined`，页面错误为 `Validation failure`。
- 已确认根因是迁移期 `orgId` 响应与当前 `companyId` 前端契约不一致。

## Changed Files

- `docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md`
- `.claw/tasks/TASK-246.md`
- `.claw/assignments/TASK-246.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Verification

- Status: `not_run`
- Evidence: none

## Handoff

- 仅修改平台租户前端边界和路由保护；不得更改后端合同或正在进行的 TASK-245。
