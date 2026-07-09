---
kind: current-status
version: 4
updated_at: 2026-07-09T23:02:00+08:00
updated_by: MANAGER-001
phase: production-validated
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 Agent 平台客户互动工作台视觉比例回归修复已发布为 2.2.10，并完成生产健康、公网 smoke 与线上浏览器验证。"
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `main`; production is running release `2.2.10` from Git commit `8a003121df0c`.
- TASK-171 is the active focus: customer interaction workbench for AgentCiCi and CloudCC CRM, covering new-customer progression, existing-customer growth, clean CRM embed, and SSO handoff.
- User reported the `2.2.9` Agent platform workbench layout looked poor: the workbench was visually squeezed, account badges wrapped badly, and the right AI customer assistant showed a visible internal scrollbar.
- CSS-only repair is live: Agent platform mode hides the duplicate inner brand/breadcrumb, restores three-column proportions, prevents account badges from vertical wrapping, and hides the visible scrollbar in the right AI customer assistant chat.
- Local verification passed at 1920x960 before release:
  - `git diff --check` passed.
  - `npm run build` in `frontend/` passed with the existing Vite large chunk warning.
  - New-customer mode screenshot: `output/playwright/task171-agent-workbench-repair-local.png`; assertions included `heroCount=0`, `outerOverflow=false`, `brandVisible=false`, `verticalBadges=[]`, `chatScrollbarVisible=false`, and workbench bottom visible.
  - Existing-customer mode screenshot: `output/playwright/task171-agent-workbench-repair-existing-local.png`; assertions included `hasExistingQueue=true`, `hasRiskPanel=true`, `outerOverflow=false`, `chatScrollbarVisible=false`, and `bottomVisible=true`.
- Production release `2.2.10` passed:
  - `./scripts/release-acr.sh --dry-run` resolved `2.2.10`; `./scripts/release-acr.sh --version 2.2.10` pushed backend/frontend images and Git tag for commit `8a003121df0c`.
  - ECS backup: `/opt/cici/backups/20260709-225131-before-2.2.10-task171-workbench-visual-repair`.
  - Six services healthy; `/system/version` returned `version=2.2.10`, `imageTag=2.2.10`, `gitCommit=8a003121df0c`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/`, `/app?aiApp=customer-workbench`, `/app?aiApp=customer-workbench&embed=crm`, and production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200.
  - Production browser screenshots: `output/playwright/task171-agent-prod-2.2.10-visual-repair.png`, `output/playwright/task171-agent-prod-2.2.10-existing-visual-repair.png`.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-171.md` - current customer interaction workbench task state.
- `.claw/assignments/TASK-171.yaml` - current authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - customer interaction workbench feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
