---
kind: task-status
task_id: TASK-246
status: done
updated_at: 2026-07-24T13:31:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-platform-agent
assignment_path: .claw/assignments/TASK-246.yaml
spec_path: docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md
---

# TASK-246 - 租户详情路由标识兼容修复

## Current State

- Status: `done`
- Next action: 已合并并发布 `2.8.14`；等待受权平台账号复核真实租户详情页。
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
- Evidence: 合并主线后 `npm test -- --run src/platform/pages/platformTenantsShared.test.ts src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（4 files / 21 tests）；`npm run build`、Compose 配置和 `git diff --check` 通过（构建仅保留既有 Vite chunk-size warning）。

## Release

- 已将 `6cee975539e4` 合并并推送至 `main`，annotated tag `2.8.14` 已推送。
- 发布前备份：`/opt/cici/backups/20260724-212057-before-2.8.14-task246`（env、PostgreSQL、KB、Qdrant 均非空）。
- 仅重建 backend/frontend；生产版本接口返回 `2.8.14 / 6cee975539e4`，health `UP`，Nginx 配置有效，`agentcici.com`、`/platform/tenants` 与 `x.agentcici.com` 均为 HTTP 200。

## Handoff

- 仅修改平台租户前端边界和路由保护；未更改后端合同或正在进行的 TASK-245。未使用或伪造平台运营账号，受保护详情页的真实交互仍待受权账号复核。
