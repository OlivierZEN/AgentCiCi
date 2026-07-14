---
kind: task-status
task_id: TASK-209
status: in_progress
updated_at: 2026-07-14T16:05:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-209.yaml
spec_path: docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md
---

# TASK-209 - 运营平台登录页宇宙智能视觉刷新

## Scope

- 按用户提供的 1672×941 原图重做 `/platform/login` 桌面端视觉，并将该原图作为默认态全页背景。
- 保留现有平台账号认证接口、字段、角色校验、token 存储和跳转逻辑。
- 用原图作为核心背景资产，以语义 HTML 提供无障碍与真实登录交互覆盖层。
- 同步设计事实源、规格、测试报告和任务交接记录。

## Current State

- 用户已明确拒绝 2.7.1 的 CSS/SVG 近似实现，要求以其提供原图做背景实现 1:1 默认态。
- 本会话的 MANAGER-001 SSH 身份门禁为 `allowed`，TASK-209 已重新打开。
- 当前待实现：原图资产、透明坐标交互层、同尺寸像素对比和 2.7.2 发布。

## Next Action

- 先写原图背景结构的失败测试，再完成同尺寸截图对比并发布 2.7.2。

## Changed Files

- `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md`
- `docs/superpowers/plans/2026-07-14-platform-login-cosmic-visual-refresh.md`
- `.claw/assignments/TASK-209.yaml`
- `.claw/tasks/TASK-209.md`
- `frontend/src/platform/PlatformLogin.tsx`
- `frontend/src/platform/PlatformLogin.test.tsx`
- `frontend/src/assets/platform-login-reference-1672x941.png`
- `frontend/src/styles.css`
- `design-qa.md`
- `DESIGN.md`
- `DESIGN.json`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- 认证逻辑不在本任务范围内，不得改变 `/auth/platform/password/login`、请求体、角色校验、平台 token key 和 `/platform` 跳转。
- 未跟踪 `diagrams/` 属于用户/其他工作，不读取、不修改、不提交。
- 原图真值路径：`/var/folders/ld/pqvgd4g52h555q74hhmy47ch0000gn/T/codex-clipboard-fe3f07a0-c764-4a22-9731-739b7212a088.png`，1672×941；发布资产必须来自该文件的无损副本。
- 2.7.1 仍是回滚版本；发布前备份和发布后验收继续遵循 `docs/production-release-runbook.md`。
