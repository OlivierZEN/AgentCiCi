---
kind: current-status
version: 4
updated_at: 2026-05-21T12:32:27Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-114 / TASK-115 / TASK-116 / TASK-124"
next_action: "Continue FEAT-046 on codex/TASK-124-feat-046-platform-tenant-provisioning; the inherited Flyway V58 collision is resolved and the focused backend integration gate is green after resetting the local test database."
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
- Mainline active work: `TASK-114`, `TASK-115`, `TASK-116`, `TASK-124`
- Just completed: `TASK-129` aligned `/admin/login` with the account-first organization-selection flow and removed the visible orgId input
- `TASK-127` completed: remaining local branches were processed on `codex/TASK-124-feat-046-platform-tenant-provisioning`; `git branch --no-merged` now reports `0`.
- `TASK-129` completed: admin login now authenticates by account first, expands organization choices only when needed, and keeps admin-role checks before entering `/admin/*`.
- Just restored: final `TASK-118` organization profile usage summary on current branch, including standalone metric cards and the missing backend `usageSummary` API data
- Recently restored: `TASK-125` database-name defaults now again target `agentcici` / `agentcici_test`
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
- Parked follow-ups: `TASK-023`, `TASK-036`, `TASK-096`, `TASK-020`, `TASK-007`, `TASK-070`, `TASK-063`
- Latest verification: TASK-127 renumbered the inherited `platform_account` migration from V58 to V59, reset local `agentcici_test`, and passed `mvn clean -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` with 22/22 green.

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-127.md` - completed local branch integration record and verification notes
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
