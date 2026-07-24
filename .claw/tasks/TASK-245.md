---
kind: task-status
task_id: TASK-245
status: review
updated_at: 2026-07-24T13:09:40Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-245.yaml
spec_path: docs/specs/FEAT-138-assistant-admin-session-entrypoint.md
---

# TASK-245 - 前台会话内置组织管理入口

## Current State

- Status: `review`
- Next action: 由持有真实组织管理员会话的用户完成菜单内同组织、跨组织与后台退出的浏览器验收；未获生产发布授权前不发布。
- Blocked: none
- Spec: `docs/specs/FEAT-138-assistant-admin-session-entrypoint.md`
- Assignment: `.claw/assignments/TASK-245.yaml`

## Progress

- 用户已确认组织菜单内的“管理后台”形态和独立 Admin 登录入口关闭规则。
- 已实现：管理员组织行显示轻量“管理后台”命令；跨组织先调用 `/auth/switch-company`，随后复用返回会话进入 `/admin`。
- 已关闭独立后台表单：`/admin/login`、无前台管理员会话的 `/admin/*` 均回到 `/app`；后台返回前台只清除 `cici_admin_token` 镜像。

## Changed Files

- `docs/specs/FEAT-138-assistant-admin-session-entrypoint.md`
- `.claw/tasks/TASK-245.md`
- `.claw/assignments/TASK-245.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/help/helpContent.ts`
- `frontend/src/suite/AgentCiciWebsite.tsx`
- `frontend/src/admin/adminSession.ts`
- `frontend/src/admin/adminSession.test.ts`
- `frontend/src/admin/AdminGuard.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/App.tsx`
- `frontend/src/admin/AdminLogin.tsx`（删除）
- `README.md`
- `AgentCiCi智能体平台实现设计方案.md`
- `DESIGN.json`

## Verification

- Status: `passed_with_manual_acceptance_pending`
- Evidence: `npm test -- --run src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（3 files / 18 tests）；`npm run build`、`git diff --check` 通过；`/admin/login` 在 1280×900 桌面端浏览器直接回到 `/app`，控制台无错误。当前会话没有真实组织管理员凭据，菜单内授权交互未伪造验收。

## Handoff

- 仅复用现有用户会话和组织切换 API；角色校验必须继续 fail closed。真实验收应覆盖当前组织进入、跨组织进入、普通成员无入口/直达拒绝，以及后台返回前台后助手会话仍有效。
