---
kind: task-status
task_id: TASK-209
status: done
updated_at: 2026-07-14T16:22:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-209.yaml
spec_path: docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md
---

# TASK-209 - 运营平台登录页原图像素锁定复刻

## Scope

- 按用户提供的 1672×941 原图重做 `/platform/login` 桌面端视觉，并将该原图作为默认态全页背景。
- 保留现有平台账号认证接口、字段、角色校验、token 存储和跳转逻辑。
- 用原图作为核心背景资产，以语义 HTML 提供无障碍与真实登录交互覆盖层。
- 同步设计事实源、规格、测试报告和任务交接记录。

## Current State

- 用户提供的 1672×941 原图已无损纳入受控前端资产，默认态直接作为整页背景；真实表单使用透明、原图坐标对齐的语义交互层。
- 本会话的 MANAGER-001 SSH 身份门禁与代表文件授权均为 `allowed`；TDD 先红后绿。
- 本地和生产 `1672 × 941` 浏览器均确认背景 `100% 100%`、默认覆盖层透明、无横向溢出、控制台 error/warning 为 0；输入后按钮可用，未提交假凭据。
- 已发布生产 `2.7.2 / ddcda0ef6111`。

## Next Action

- 无；已完成生产发布与回读。

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
- 回滚版本为 `2.7.1 / 5a5e9489035c`；本次备份目录为 `/opt/cici/backups/20260715-001809-before-2.7.2-task209-reference-login`。
- 原图/生产默认态对比图：`output/playwright/task209-reference-production-comparison-2.7.2.png`；生产默认态截图：`output/playwright/task209-reference-production-2.7.2-1672x941.jpg`。
