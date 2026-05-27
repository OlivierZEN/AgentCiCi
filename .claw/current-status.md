---
kind: current-status
version: 4
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-140 / TASK-139 / TASK-138 / TASK-137 / TASK-136 / existing active tasks"
next_action: "TASK-137 assigned to Owen; TASK-136/TASK-138/TASK-139/TASK-140 assigned to DEV-fengchu."
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

- Focus: user feedback triage from fixed Feishu `功能需求` and `BUG反馈` docs while keeping `.claw/` aligned with `cc-aidev-guidelines-common` 4.1.2.
- Assigned from feedback: Owen owns `TASK-137`; `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Static bug verification: `TASK-139` confirmed from `/agents` list/detail loading; `TASK-140` confirmed from backend route and docs path shape.
- Existing mainline active work remains: `TASK-134`, `TASK-133`, `TASK-132`, `TASK-114`, `TASK-115`, `TASK-116`, `TASK-124`
- Just completed: `TASK-135` cleared hard-coded default account values from assistant/admin login inputs and the read-only login showcase; static search, frontend build, and desktop browser checks passed.
- `TASK-134` transcript download update is in Codeup review at change/13: AI 听记 now has local audio/video upload, backend 百炼 temporary OSS upload, Fun-ASR async transcription with speaker diarization, multi-speaker transcript normalization, frontend upload state handling, Spring 256MB multipart limits, a post-transcription `下载转写` Markdown export, and focused tests. Whole-file 7.1MB upload still resets on this local network, but 60-second chunks successfully upload to 百炼 and return Fun-ASR speaker transcript segments.
- Latest verification: TASK-134 transcript download optimization passed task-scoped identity gate, `npm run build`, Vite HTTP 200 reachability, Playwright page-open to the login surface, and `git diff --check`; full AI 听记 desktop screenshot was not completed because local backend/login was unavailable. Earlier TASK-134 gates passed backend focused tests, frontend build, in-app browser desktop QA, local account login smoke, generated silence provider smoke, real 60-second chunk upload/ASR smoke, and focused rerun after preserving 百炼 result URLs verbatim.
- Newly assigned: `TASK-133` gives `DEV-fengchu` the Agent Builder no-model new-Agent feedback; `TASK-132` remains assigned to `DEV-fengchu` for focused Agent detail reload.
- Codeup changes merged locally on `main`: `change/6` platform account orgless auth context and `change/7` skill-authoring timeout fix. Post-merge verification passed state validation, frontend build, backend compile, script syntax, and `git diff --check`.
- `TASK-131` is merged to `main`; focused `PlatformAuthIntegrationTest` rerun waits for local Docker/Postgres.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns canonical versions.
- Recently restored: `TASK-125` database-name defaults now again target `agentcici` / `agentcici_test`
- Recently completed and archived: `TASK-127`, `TASK-126`, `TASK-123`, `TASK-118`, `TASK-117`, `TASK-112`
- Parked follow-ups: `TASK-023`, `TASK-036`, `TASK-096`, `TASK-020`, `TASK-007`, `TASK-070`, `TASK-063`

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-140.md` - OpenAPI public route contract bug
- `.claw/tasks/TASK-139.md` - Agent list OpenAPI badge bug
- `.claw/tasks/TASK-136.md`, `.claw/tasks/TASK-137.md`, `.claw/tasks/TASK-138.md` - new feature tasks
- `.claw/tasks/TASK-134.md` - AI 听记 transcript download update in Codeup change/13
- `docs/specs/FEAT-054-bailian-real-audio-test-plan.md` - optional another-network whole-file smoke handoff
- `.claw/tasks/TASK-133.md` - Agent Builder no-model new-Agent feedback assigned to `DEV-fengchu`
- `.claw/tasks/TASK-132.md` - Agent Builder focused-agent skill binding refresh bug assigned to `DEV-fengchu`
- `.claw/tasks/TASK-114.md`, `.claw/tasks/TASK-115.md`, `.claw/tasks/TASK-116.md`, `.claw/tasks/TASK-124.md` - existing active slices
- `docs/specs/PROJECT-BASELINE.md` - only when legacy architecture or manager-gated coordination context matters

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
- Put feature requirements, design, and acceptance criteria in `docs/specs/`.
- Put real verification evidence in `.claw/test-report.md`.
