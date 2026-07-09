---
kind: current-status
version: 4
updated_at: 2026-07-09T23:36:00+08:00
updated_by: MANAGER-001
phase: production-validated
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 客户互动工作台客户列表错位热修已发布为 2.2.11，并完成生产健康、公网 smoke 与线上浏览器验证。"
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

- Current branch: `main`; production is running release `2.2.11` from Git commit `d251a2661602`.
- TASK-171 remains the active customer interaction workbench delivery thread.
- User reported the customer interaction workbench customer list was visually broken: queue rows overlapped, badges and recent interaction text spilled out of the red-boxed left list.
- Root cause verified in production before the fix: backend list field `lastInteraction` is a summary sentence, but the frontend rendered it in the row's right-side `time` grid column through `shortDate(...)`; when parsing failed, the long summary became the time text. The fixed 92px row height plus wrapping badges produced `outsideCount=18` at 1620x812.
- Fix is live: customer rows now render `updatedAt` as the compact time and render `lastInteraction` as a clamped in-row summary; the row grid no longer has a long right column and has stable badge/summary bounds.
- Local verification passed:
  - `git diff --check` passed.
  - `npm run build` in `frontend/` passed with the existing Vite large chunk warning.
  - Playwright 1620x812 with real production data through local frontend proxy passed for `/app?aiApp=customer-workbench` and `/app?aiApp=customer-workbench&embed=crm`: `outsideCount=0`, `rowOverlaps=[]`, `bodyOverflow=false`, `chatScrollbarVisible=false`; screenshots `output/playwright/task171-local-list-layout-after.png` and `output/playwright/task171-local-list-layout-embed-after.png`.
- Production release `2.2.11` passed:
  - `./scripts/release-acr.sh --dry-run` resolved `2.2.11`; `./scripts/release-acr.sh --version 2.2.11` pushed backend/frontend images and Git tag for commit `d251a2661602`.
  - ECS backup: `/opt/cici/backups/20260709-232920-before-2.2.11-task171-workbench-queue-layout`.
  - Backend/frontend containers run `2.2.11`; six services healthy; `/system/version` returned `version=2.2.11`, `imageTag=2.2.11`, `gitCommit=d251a2661602`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/`, `/app?aiApp=customer-workbench`, `/app?aiApp=customer-workbench&embed=crm`, and production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200.
  - Production browser screenshots: `output/playwright/task171-prod-2.2.11-queue-layout.png`, `output/playwright/task171-prod-2.2.11-queue-layout-app.png`; assertions included `outsideCount=0`, `rowOverlaps=[]`, `bodyOverflow=false`, `chatScrollbarVisible=false`.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-171.md` - current customer interaction workbench task state.
- `.claw/assignments/TASK-171.yaml` - current authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - customer interaction workbench feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
