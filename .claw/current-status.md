---
kind: current-status
version: 4
updated_at: 2026-05-25T13:07:26Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-134 / TASK-133 / TASK-132 / TASK-114 / TASK-115 / TASK-116 / TASK-124"
next_action: "Review Codeup change/12 for TASK-134; real 百炼 upload/ASR is proven on 60-second chunks, while whole-file upload remains local-network reset."
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

- Focus: review TASK-134 AI 听记 local audio upload and 百炼 multi-speaker transcription implementation while keeping `.claw/` aligned with `cc-aidev-guidelines-common` 4.1.0.
- Mainline active work: `TASK-134`, `TASK-133`, `TASK-132`, `TASK-114`, `TASK-115`, `TASK-116`, `TASK-124`
- `TASK-134` is in Codeup review at change/12: AI 听记 now has local audio/video upload, backend 百炼 temporary OSS upload, Fun-ASR async transcription with speaker diarization, multi-speaker transcript normalization, frontend upload state handling, Spring 256MB multipart limits, and focused tests. Whole-file 7.1MB upload still resets on this local network, but 60-second chunks successfully upload to 百炼 and return Fun-ASR speaker transcript segments.
- Latest verification: TASK-134 passed task-scoped identity gate, backend focused tests, frontend build, in-app browser desktop QA, local account login smoke, generated silence provider smoke, real 60-second chunk upload/ASR smoke, and focused rerun after preserving 百炼 result URLs verbatim.
- Newly assigned: `TASK-133` gives `DEV-fengchu` the Agent Builder no-model new-Agent feedback; `TASK-132` remains assigned to `DEV-fengchu` for focused Agent detail reload.
- Codeup changes merged locally on `main`: `change/6` platform account orgless auth context and `change/7` skill-authoring timeout fix. Post-merge verification passed state validation, frontend build, backend compile, script syntax, and `git diff --check`.
- `TASK-131` is merged to `main`; focused `PlatformAuthIntegrationTest` rerun remains an environment follow-up for when local Docker/Postgres is available.
- Latest maintenance: `FEAT-052` completed ACR release version governance; production release source of truth is `docs/production-release-runbook.md`, with `scripts/release-acr.sh` generating the canonical version.
- Just completed: `TASK-130` added unified ACR/Git/backend/frontend/deploy version propagation and authenticated left-nav version badges.
- `TASK-127` completed: remaining local branches were processed on `codex/TASK-124-feat-046-platform-tenant-provisioning`; `git branch --no-merged` now reports `0`.
- `TASK-129` completed: admin login now authenticates by account first, expands organization choices only when needed, and keeps admin-role checks before entering `/admin/*`.
- Recently restored: `TASK-125` database-name defaults now again target `agentcici` / `agentcici_test`
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
- Parked follow-ups: `TASK-023`, `TASK-036`, `TASK-096`, `TASK-020`, `TASK-007`, `TASK-070`, `TASK-063`

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-134.md` - AI 听记 local upload and 百炼 speaker diarization task in review
- `docs/specs/FEAT-054-bailian-real-audio-test-plan.md` - optional another-network whole-file smoke handoff
- `.claw/tasks/TASK-133.md` - Agent Builder no-model new-Agent feedback assigned to `DEV-fengchu`
- `.claw/tasks/TASK-132.md` - Agent Builder focused-agent skill binding refresh bug assigned to `DEV-fengchu`
- `.claw/tasks/TASK-114.md` - billing ledger work slice
- `.claw/tasks/TASK-115.md` - knowledge base maintenance slice
- `.claw/tasks/TASK-116.md` - skill module completion slice
- `.claw/tasks/TASK-124.md` - platform tenant manual provisioning and lifecycle split
- `docs/specs/PROJECT-BASELINE.md` - only when legacy architecture or manager-gated coordination context matters

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
