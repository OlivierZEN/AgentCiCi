---
kind: task-status
task_id: TASK-141
status: ready
updated_at: 2026-05-27T09:30:00Z
updated_by: MANAGER-001
assignee: DEV-houyi
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-141.yaml
spec_path: docs/specs/FEAT-059-ai-minutes-local-asr.md
---

# TASK-141 - AI 听记本地 FunASR 实时转写

## Scope

- Implement `FEAT-059` with local FunASR / Paraformer-zh as a first-class AI 听记 ASR provider.
- Deliver realtime local transcription as the P0 path; file transcription is a same-provider reuse path and cannot replace realtime acceptance.
- Add an isolated Python `local-asr` sidecar service for FunASR streaming inference instead of embedding model runtime into the Spring Boot backend.
- Connect backend `/ws/asr?provider=local` to the local ASR sidecar and preserve the existing AI 听记 transcript, speaker editing, and summary generation flow.

## Source Decision

- User approved the FEAT-059 design and assigned implementation to `DEV-houyi` on 2026-05-27.
- Real-time transcription is a mandatory requirement.

## Acceptance

- `local-asr` exposes a realtime WebSocket stream endpoint and health/readiness endpoint.
- AI 听记 “开始会议纪要” can use `provider=local` and emits partial/final transcript events while recording.
- The implementation does not wait for stop/end-of-recording before returning transcript content.
- First partial target is no more than 2 seconds and stable final target is no more than 5 seconds on the declared delivery hardware, or deviations are recorded with measured evidence and follow-up recommendations.
- Single-GPU delivery baseline supports at least 2 concurrent realtime meetings unless deployment constraints explicitly reduce the accepted scope.
- Audio is not sent to cloud ASR providers when local ASR is enabled and no explicit fallback is configured.
- File upload transcription reuses the local provider where implemented and stays compatible with the existing transcript UI.
- Backend, frontend, sidecar, and deployment docs include focused tests or smoke evidence.

## Expected Work Areas

- `services/local-asr/**`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/**`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/**`
- `backend/src/main/resources/**`
- `frontend/src/**`
- `deploy/**`
- `docs/specs/FEAT-059-ai-minutes-local-asr.md`
- `.claw/tasks/TASK-141.md`

## Verification Plan

- Run task-scoped `dev-login.py` before implementation edits.
- PM assignment preflight completed on 2026-05-27: `check-assignment.py` returned `allowed` for representative sidecar, backend, frontend, deploy, spec, and task-status files.
- Add unit tests for provider selection and local ASR response normalization.
- Add sidecar-level smoke for streaming partial/final events with sample 16k PCM audio.
- Run backend compile/test scope affected by `/ws/asr` and meeting minutes APIs.
- Run frontend build and desktop AI 听记 drawer verification.
- Record real local ASR smoke results and latency measurements in `.claw/test-report.md` or handoff notes after validation.

## Handoff

- Assigned branch: `codex/TASK-141-local-funasr-realtime-asr`.
- Start from `docs/specs/FEAT-059-ai-minutes-local-asr.md`, then inspect existing `AliyunRealtimeAsrWebSocketHandler`, `MeetingMinutesController`, `useAsrVoiceInput`, and `MeetingMinutesPanel`.
- Do not modify root release scripts or root `docker-compose.yml` unless PM expands assignment scope; use `deploy/**` and `services/local-asr/**` for first implementation.
