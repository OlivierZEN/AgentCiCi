---
kind: task-status
task_id: TASK-246
status: done
updated_at: 2026-07-24T13:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-platform-agent
assignment_path: .claw/assignments/TASK-246.yaml
spec_path: docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md
---

# TASK-246 - 租户详情路由标识兼容修复

## Current State

- Status: `done`
- Next action: 将已验证分支合并后发布前端。
- Blocked: none

## Progress

- 用户截图已确认路由为 `/platform/tenants/undefined`，页面错误为 `Validation failure`。
- 已确认根因是迁移期 `orgId` 响应与当前 `companyId` 前端契约不一致。
- 已在租户接口边界将旧 `orgId` 归一为 `companyId`，并阻止无效标识生成详情 URL。
- 详情页会在请求前拦截 `/platform/tenants/undefined` 等无效参数并 replace 返回目录。

## Changed Files

- `docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md`
- `.claw/tasks/TASK-246.md`
- `.claw/assignments/TASK-246.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `frontend/src/platform/pages/platformTenantsShared.ts`
- `frontend/src/platform/pages/PlatformTenantsPage.tsx`
- `frontend/src/platform/pages/PlatformTenantApplicationsPage.tsx`
- `frontend/src/platform/pages/platformTenantsShared.test.ts`

## Verification

- Status: `passed`
- Evidence: `npm test -- --run src/platform/pages/platformTenantsShared.test.ts` 通过 3/3；`npm run build` 通过（仅保留既有 Vite chunk-size warning）；`git diff --check` 通过。

## Handoff

- 仅修改平台租户前端边界和路由保护；未更改后端合同、生产环境或正在进行的 TASK-245。
