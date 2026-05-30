---
kind: current-status
version: 4
updated_at: 2026-05-30T11:32:30Z
updated_by: MANAGER-001
phase: maintenance
active_task: "Integrating TASK-143, TASK-114, and TASK-144 on main; TASK-145 remains to merge; TASK-142 / TASK-141 / TASK-136 / TASK-138 / TASK-139 ready; TASK-132 / TASK-133 / TASK-137 in review; TASK-140 in progress"
next_action: "Merge TASK-145 into main, resolve conflicts, then run integration tests."
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

- Focus: integrating billing runtime, public website restructure, and model-provider governance on local `main`.
- TASK-114 runtime billing is ready for review: billable traces write ledger debits, platform config errors stay non-billable, and org-admin billing shows `官网报价条目 · Credits 包`.
- TASK-144 implemented locally: public website now uses AgentCiCi enterprise agent platform IA with bilingual `Solutions`, `SkillsHub`, `Pricing`, `Docs`, and `Community`; old `/suite/*`, `/pricing/global`, and `/autoservice/*` public routes redirect to the new structure.
- TASK-143 implemented and integration-verified: platform-configurable SaaS/private editions plus protected organization-admin `/admin/billing` read chain for current edition, credits balance, consumption, quota warnings, usage events, and ledger details.
- Local PostgreSQL cleanup complete: TASK-143 billing integration tests pass through `jdbc:postgresql://127.0.0.1:5432/agentcici_test`.
- Newly assigned from 飞书 BUG反馈: `TASK-142` to `DEV-fengchu` for OpenAPI `chat-messages` true SSE streaming (`B20260527-SSE01`).
- Newly assigned: `TASK-141` to `DEV-houyi` for AI 听记 local FunASR / Paraformer-zh realtime ASR; realtime transcription is P0 and uses an isolated Python `services/local-asr/**` sidecar.
- Status governance: task progress source is `.claw/tasks/TASK-xxx.md`; `.claw/task-board.md` is PM/integration-owned; `.claw/assignments/TASK-xxx.yaml` status tracks authorization lifecycle only.
- Reauthorized: `TASK-136`, `TASK-139`, and `TASK-140` now use recursive `allowed_write_roots` globs and no longer grant developer write scope to `.claw/task-board.md`.
- Normalized status: `TASK-132`, `TASK-133`, and `TASK-137` are `review`; `TASK-140` is `in_progress`; `TASK-142`, `TASK-136`, `TASK-138`, and `TASK-139` are `ready`.
- Assigned from feedback: `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Just completed: `TASK-134` is closed because the remaining whole-file upload reset is a local-network limitation and the online environment is normal; `TASK-124` is also complete.
- Static bug verification: `TASK-139` confirmed from `/agents` list/detail loading; `TASK-140` confirmed from backend route and docs path shape.
- Existing mainline active work remains: `TASK-115`, `TASK-116`; billing platform configuration is split to `TASK-143`, and runtime billing is now `TASK-114` review work.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` now owns numeric production versions and production-based beta test versions.
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-144.md`, `docs/specs/FEAT-061-agentcici-public-website-restructure.md` - public AgentCiCi website restructure
- `.claw/tasks/TASK-114.md`, `.claw/tasks/TASK-143.md`, `docs/specs/FEAT-037-saas-billing-usage-ledger.md` - billing runtime ledger and edition configuration tasks
- `.claw/tasks/TASK-142.md`, `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md` - OpenAPI SSE streaming bug assignment
- `.claw/tasks/TASK-132.md`, `.claw/tasks/TASK-133.md`, `.claw/tasks/TASK-137.md` - tasks waiting review/merge
- `.claw/tasks/TASK-140.md` - OpenAPI public route contract bug
- `.claw/tasks/TASK-139.md` - Agent list OpenAPI badge bug
- `.claw/tasks/TASK-136.md`, `.claw/tasks/TASK-138.md` - new feature tasks
- `.claw/tasks/TASK-141.md`, `docs/specs/FEAT-059-ai-minutes-local-asr.md` - local FunASR realtime ASR implementation assignment
- `.claw/tasks/TASK-115.md`, `.claw/tasks/TASK-116.md` - remaining active slices

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
