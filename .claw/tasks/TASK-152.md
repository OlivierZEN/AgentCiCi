---
kind: task-status
task_id: TASK-152
status: review
updated_at: 2026-07-09T23:21:42+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-152.yaml
spec_path: docs/specs/FEAT-037-saas-billing-usage-ledger.md
---

# TASK-152 - AI 听记 credits and start-timeout hotfix

## Scope

- Fix local-test defects in AI 听记:
  - successful meeting-minutes usage does not change organization credits;
  - clicking start listening can fail with a timeout.
- Reuse the existing billing usage ledger and rating service; do not add schema or payment behavior.
- Keep changes scoped to meeting-minutes runtime, billing metering, focused tests, and related specs.

## Current Findings

- Existing runtime billing only covered chat runs; embedded AI 听记 summary did not call `BillingUsageMeteringService`.
- Embedded and assistant AI 听记 start paths forced `provider: "iflytek"`, while local/default config disables Iflytek; the real-time path should use the existing Aliyun provider unless a configured provider selection is introduced.
- `useAsrVoiceInput` waited for the generic WebSocket open timeout even when the connection errored or closed before open.
- 2026-07-09 线上截图复现：AI 听记结束并生成纪要时出现 `Missing required parameter 'payload.task_group'`。对照阿里云实时 ASR WebSocket 协议，`run-task` 已带 `payload.task_group=audio`，错误更符合 `finish-task` 与尾部音频帧异步乱序，任务结束后仍向上游发送音频的协议状态错误。

## Changes

- Added `BillingUsageMeteringService.recordMeetingMinutesRunSafely()` with idempotent `meeting-minutes` usage source ids.
- `MeetingEmbedRuntimeService.summarize()` now records AI 听记 workflow/model usage after successful summary generation.
- `MeetingMinutesService.MeetingMinutesResult` now carries model/token/billable metadata for metering.
- Embedded and assistant AI 听记 start paths default to Aliyun realtime ASR for local/default usage.
- WebSocket startup now reports immediate connection failure/close instead of only waiting for an 8-second timeout.
- Aliyun realtime ASR now serializes audio frames and `finish-task` on one send queue, blocks further binary forwarding as soon as stop is requested, and clears client-side readiness before disconnecting microphone capture.

## Verification Plan

- Run task-scoped `dev-login.py` before implementation edits.
- Add or update focused backend tests for meeting-minutes billing and start/listening behavior where practical.
- Run focused Maven tests for affected billing/embed/AI paths.
- Restart local backend/frontend and smoke the affected endpoints.

## Verification

- `dev-login.py .claw --developer MANAGER-001 --task TASK-152 --branch codex/TASK-152-ai-minutes-billing-timeout ...` -> allowed.
- `cd backend && mvn -Dmaven.repo.local=.m2 -Dtest=EmbedAppIntegrationTest,AdminBillingIntegrationTest test` -> success, 3 tests passed.
- `cd frontend && npm run build` -> success, existing large chunk warning only.
- Local API smoke on restarted services -> success; `POST /embed/v1/apps/meeting-minutes/sessions/{sessionId}/summary` returned `success=true`, and `demo-org` consumed credits changed from `0.00` to `2.24`.
- 2026-07-09 `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --developer MANAGER-001 --task TASK-152 --branch codex/TASK-152-ai-minutes-billing-timeout ... --json` -> allowed.
- 2026-07-09 `cd backend && mvn -Dmaven.repo.local=.m2 -DskipTests compile` -> success.
- 2026-07-09 `cd frontend && npm run build` -> success, existing Vite large chunk warning only.

## Handoff

- Branch: `codex/TASK-152-ai-minutes-billing-timeout`.
- User-reported local symptoms: AI 听记 usage should consume credits but currently does not; start listening reports timeout.
