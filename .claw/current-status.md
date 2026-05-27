---
kind: current-status
version: 4
updated_at: 2026-05-27T08:05:30Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-132 / TASK-133 / TASK-137 in review; TASK-136 / TASK-138 / TASK-139 / TASK-140 ready"
next_action: "Developers update only .claw/tasks/TASK-xxx.md for progress; PM/integration owner syncs task-board.md and marks done after merge verification."
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

- Focus: manager-gated task status and assignment authorization cleanup while keeping `.claw/` aligned with `cc-aidev-guidelines-common` 4.1.2.
- Status governance: task progress source is `.claw/tasks/TASK-xxx.md`; `.claw/task-board.md` is PM/integration-owned; `.claw/assignments/TASK-xxx.yaml` status tracks authorization lifecycle only.
- Reauthorized: `TASK-136`, `TASK-139`, and `TASK-140` now use recursive `allowed_write_roots` globs and no longer grant developer write scope to `.claw/task-board.md`.
- Normalized status: `TASK-132`, `TASK-133`, and `TASK-137` are `review`; `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140` remain `ready`.
- Team registry: added active fullstack developer `DEV-houyi` / 后羿 with Codeup identity `zhengyan`.
- Just implemented: `TASK-137` custom Agent delete action adds soft-delete backend semantics, custom-only frontend delete confirmation, local list removal, and selected-Agent fallback; focused backend integration execution is blocked only by unavailable local PostgreSQL.
- Assigned from feedback: `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Just completed: `TASK-134` is closed because the remaining whole-file upload reset is a local-network limitation and the online environment is normal; `TASK-124` is also complete.
- Static bug verification: `TASK-139` confirmed from `/agents` list/detail loading; `TASK-140` confirmed from backend route and docs path shape.
- Existing mainline active work remains: `TASK-114`, `TASK-115`, `TASK-116`
- Recently completed: `TASK-135` cleared hard-coded default account values from assistant/admin login inputs and the read-only login showcase; static search, frontend build, and desktop browser checks passed.
- `TASK-131` is merged to `main`; focused `PlatformAuthIntegrationTest` rerun waits for local Docker/Postgres.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns canonical versions.
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
- Parked follow-ups: `TASK-023`, `TASK-036`, `TASK-096`, `TASK-020`, `TASK-007`, `TASK-070`, `TASK-063`

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-132.md`, `.claw/tasks/TASK-133.md`, `.claw/tasks/TASK-137.md` - tasks waiting review/merge
- `.claw/tasks/TASK-140.md` - OpenAPI public route contract bug
- `.claw/tasks/TASK-139.md` - Agent list OpenAPI badge bug
- `.claw/tasks/TASK-136.md`, `.claw/tasks/TASK-138.md` - new feature tasks
- `docs/specs/FEAT-054-bailian-real-audio-test-plan.md` - optional another-network whole-file smoke handoff
- `.claw/tasks/TASK-114.md`, `.claw/tasks/TASK-115.md`, `.claw/tasks/TASK-116.md` - existing active slices
- `docs/specs/PROJECT-BASELINE.md` - only when legacy architecture or manager-gated coordination context matters

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
