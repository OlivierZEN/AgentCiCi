---
kind: current-status
version: 4
updated_at: 2026-05-22T10:32:57Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-133 / TASK-132 / TASK-114 / TASK-115 / TASK-116 / TASK-124"
next_action: "DEV-fengchu runs task-scoped dev-login.py on codex/TASK-133-agent-builder-new-agent-model-config-fix, then fixes the no-model new-Agent click path and validates the /admin/models notice handoff."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: false
  test_report: true
  devops: false
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Focus: continue FEAT-046 on the now-integrated local branch while keeping `.claw/` aligned with `cc-aidev-guidelines-common` 4.1.0.
- Mainline active work: `TASK-133`, `TASK-132`, `TASK-114`, `TASK-115`, `TASK-116`, `TASK-124`
- Newly assigned: `TASK-133` gives `DEV-fengchu` the Agent Builder no-model new-Agent feedback; `TASK-132` remains assigned to `DEV-fengchu` for focused Agent detail reload.
- Just completed: `TASK-129` aligned `/admin/login` with the account-first organization-selection flow and removed the visible orgId input
- `TASK-127` completed: remaining local branches were processed on `codex/TASK-124-feat-046-platform-tenant-provisioning`; `git branch --no-merged` now reports `0`.
- `TASK-129` completed: admin login now authenticates by account first, expands organization choices only when needed, and keeps admin-role checks before entering `/admin/*`.
- Just restored: final `TASK-118` organization profile usage summary on current branch, including standalone metric cards and the missing backend `usageSummary` API data
- Recently restored: `TASK-125` database-name defaults now again target `agentcici` / `agentcici_test`
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
- Parked follow-ups: `TASK-023`, `TASK-036`, `TASK-096`, `TASK-020`, `TASK-007`, `TASK-070`, `TASK-063`
- Latest verification: `TASK-133` assignment passed manager identity, team-status regeneration, `.claw` state validation, and targeted `git diff --check`; implementation has not started.

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-133.md` - Agent Builder no-model new-Agent feedback assigned to `DEV-fengchu`
- `.claw/tasks/TASK-132.md` - Agent Builder focused-agent skill binding refresh bug assigned to `DEV-fengchu`
- `.claw/tasks/TASK-114.md` - billing ledger work slice
- `.claw/tasks/TASK-115.md` - knowledge base maintenance slice
- `.claw/tasks/TASK-116.md` - skill module completion slice
- `.claw/tasks/TASK-124.md` - platform tenant manual provisioning and lifecycle split
- `.claw/tasks/TASK-125.md` - database-name restore evidence for `agentcici`
- `docs/specs/PROJECT-BASELINE.md` - only when legacy architecture or manager-gated coordination context matters

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
