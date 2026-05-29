---
kind: current-status
version: 4
updated_at: 2026-05-29T13:24:50Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-143 implemented with integration tests passing; TASK-142 / TASK-141 / TASK-136 / TASK-138 / TASK-139 ready; TASK-132 / TASK-133 / TASK-137 in review; TASK-140 in progress"
next_action: "TASK-143 has been merged into latest origin/main on codex/integrate-TASK-143-main and passed focused backend integration plus frontend verification; ready for review/push decision."
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

- Focus: manager-gated task delivery while keeping `.claw/` aligned with `cc-aidev-guidelines-common` 4.1.2.
- TASK-143 implemented and integration-verified: platform-configurable SaaS/private editions plus protected organization-admin `/admin/billing` read chain for current edition, credits balance, consumption, quota warnings, usage events, and ledger details.
- Integration branch check complete: latest `origin/main` plus TASK-143 on `codex/integrate-TASK-143-main` passes focused backend billing tests, frontend billing tests, and frontend production build.
- Local PostgreSQL cleanup complete: stale Colima `default` and Lima `cici-docker` listeners were removed; TASK-143 billing integration tests now pass through `jdbc:postgresql://127.0.0.1:5432/agentcici_test`.
- Newly assigned from 飞书 BUG反馈: `TASK-142` to `DEV-fengchu` for OpenAPI `chat-messages` true SSE streaming (`B20260527-SSE01`).
- Newly assigned: `TASK-141` to `DEV-houyi` for AI 听记 local FunASR / Paraformer-zh realtime ASR; realtime transcription is P0 and uses an isolated Python `services/local-asr/**` sidecar.
- Status governance: task progress source is `.claw/tasks/TASK-xxx.md`; `.claw/task-board.md` is PM/integration-owned; `.claw/assignments/TASK-xxx.yaml` status tracks authorization lifecycle only.
- Reauthorized: `TASK-136`, `TASK-139`, and `TASK-140` now use recursive `allowed_write_roots` globs and no longer grant developer write scope to `.claw/task-board.md`.
- Normalized status: `TASK-132`, `TASK-133`, and `TASK-137` are `review`; `TASK-140` is `in_progress`; `TASK-142`, `TASK-136`, `TASK-138`, and `TASK-139` are `ready`.
- Team registry: added active fullstack developer `DEV-houyi` / 后羿 with Codeup identity `zhengyan`.
- Just implemented: `TASK-137` custom Agent delete action adds soft-delete backend semantics, custom-only frontend delete confirmation, local list removal, and selected-Agent fallback; focused backend integration execution is blocked only by unavailable local PostgreSQL.
- Assigned from feedback: `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Just completed: `TASK-134` is closed because the remaining whole-file upload reset is a local-network limitation and the online environment is normal; `TASK-124` is also complete.
- Static bug verification: `TASK-139` confirmed from `/agents` list/detail loading; `TASK-140` confirmed from backend route and docs path shape.
- Existing mainline active work remains: `TASK-114`, `TASK-115`, `TASK-116`; billing platform configuration is split to `TASK-143`.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` now owns numeric production versions and production-based beta test versions.
## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-143.md`, `docs/specs/FEAT-037-saas-billing-usage-ledger.md` - billing edition configuration task
- `.claw/tasks/TASK-142.md`, `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md` - OpenAPI SSE streaming bug assignment
- `.claw/tasks/TASK-132.md`, `.claw/tasks/TASK-133.md`, `.claw/tasks/TASK-137.md` - tasks waiting review/merge
- `.claw/tasks/TASK-140.md` - OpenAPI public route contract bug
- `.claw/tasks/TASK-139.md` - Agent list OpenAPI badge bug
- `.claw/tasks/TASK-136.md`, `.claw/tasks/TASK-138.md` - new feature tasks
- `.claw/tasks/TASK-141.md`, `docs/specs/FEAT-059-ai-minutes-local-asr.md` - local FunASR realtime ASR implementation assignment
- `.claw/tasks/TASK-114.md`, `.claw/tasks/TASK-115.md`, `.claw/tasks/TASK-116.md` - existing active slices

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
