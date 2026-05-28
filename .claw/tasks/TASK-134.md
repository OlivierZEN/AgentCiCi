---
kind: task-status
version: 1
task_id: TASK-134
title: AI minutes local audio upload and speaker diarization
status: done
assignee: MANAGER-001
owner_role: fullstack-agent
branch: codex/TASK-134-ai-minutes-local-audio-upload
pr_url: https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/13
spec_path: docs/specs/FEAT-054-ai-minutes-local-audio-upload.md
assignment_path: .claw/assignments/TASK-134.yaml
updated_at: 2026-05-27T07:23:58Z
updated_by: MANAGER-001
---

# TASK-134 - AI Minutes Local Audio Upload And Speaker Diarization

## Scope

- Add local audio/video file upload to the AI 听记 product surface.
- Add backend multipart transcription API backed by 阿里云百炼 ASR with speaker diarization.
- Return multi-speaker transcript segments compatible with the existing speaker editing and summary flow.
- Enforce the requested supported format list on both frontend and backend.

## Preflight

- Manager bootstrap `dev-login.py` returned `allowed` for `MANAGER-001` on 2026-05-25.
- Task-scoped `dev-login.py` must return `allowed` for `TASK-134` on `codex/TASK-134-ai-minutes-local-audio-upload` before implementation edits.

## Changed Files

- Backend AI API/service: `MeetingMinutesController`, `AliyunAsrService`.
- Backend focused coverage: `AliyunAsrServiceTest`.
- Frontend AI 听记 surface: `AssistantApp`, `MeetingMinutesPanel`, `cici-ui.css`.
- State/spec evidence: `TASK-134`, `FEAT-054`, `task-board`, `current-status`, `test-report`.

## Progress

- 2026-05-25T00:31:20Z: Opened TASK-134 and FEAT-054 for the requested AI 听记 local upload and multi-speaker 百炼 transcription feature.
- 2026-05-25T00:31:20Z: Implemented `/ai/meeting-minutes/transcribe-file`, 百炼 temporary OSS upload, Fun-ASR async transcription with `diarization_enabled=true`, speaker transcript normalization, frontend `导入录音` entry, upload state handling, and transcript-to-summary reuse.
- 2026-05-25T01:15:00Z: Raised Spring multipart upload limits to 256MB after real local smoke hit the default 1MB cap; changed temporary OSS upload transport from `RestClient` multipart to fixed-length `HttpURLConnection` multipart after JDK streaming upload reset against the 百炼 OSS host.
- 2026-05-25T01:18:00Z: Verified the revised upload transport with a generated 1-second silence WAV; 百炼 ASR returned `ASR_RESPONSE_HAVE_NO_WORDS`, confirming upload/task submission reached the provider and failed only because the sample contained no speech.
- 2026-05-25T01:50:00Z: User explicitly approved real private-recording upload; full 7.1MB file still failed at 百炼 temporary OSS upload with `Connection reset`, generated 7MB control file reproduced the same reset, and 60-second ffmpeg chunks were prepared. Testing is paused per user request; handoff plan is in `docs/specs/FEAT-054-bailian-real-audio-test-plan.md`.
- 2026-05-25T12:24:00Z: Per user direction, stopped after proving upload works. Real 60-second chunks successfully uploaded to 百炼, completed Fun-ASR, and returned speaker diarization segments; whole-file upload still appears blocked by local network/temporary OSS reset. Also fixed 百炼 result JSON download to preserve pre-signed OSS URLs verbatim.
- 2026-05-25T13:07:26Z: Created Codeup change request change/12 for TASK-134 and updated the branch with latest `origin/main`.
- 2026-05-25T23:32:21Z: Added a post-transcription `下载转写` action beside the AI 听记 footer primary action. It exports the current edited speaker transcript to a local Markdown file.
- 2026-05-26T07:45:03+08:00: Created Codeup change request change/13 for the transcript download update.
- 2026-05-27T07:23:58Z: Marked complete per user confirmation. The remaining whole-file upload reset is a local-network limitation; the online environment is normal.

## Verification

- `dev-login.py` for `MANAGER-001` / `TASK-134` on `codex/TASK-134-ai-minutes-local-audio-upload` with intended backend/frontend/spec/state files -> **allowed**.
- `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AliyunAsrServiceTest,MeetingMinutesServiceTest test` in `backend/` -> **success**.
- `npm run build` in `frontend/` -> **success**; existing Vite chunk-size warning remains.
- In-app browser desktop check at `127.0.0.1:5173` -> **success**; AI 听记 upload action renders as compact text command, hidden file input is not exposed as an unnamed accessible control, and transcript/summary layout remains stable.
- Local account login smoke with the provided account against `POST /auth/password/login` -> **success**, HTTP 200.
- Real file smoke with the provided 7.1MB `.m4a` -> **partial**: first run exposed and fixed the default 1MB multipart limit; second run reached 百炼 temporary OSS upload and failed with connection reset from the JDK multipart client; upload transport has been replaced and covered by backend compile/tests.
- Generated 1-second silence WAV smoke through `POST /ai/meeting-minutes/transcribe-file` -> **provider reached**; returned HTTP 400 with 百炼 task failure `ASR_RESPONSE_HAVE_NO_WORDS`, which is expected for silent audio and confirms the fixed OSS upload path no longer resets.
- Full private-recording rerun against 百炼 after explicit confirmation -> **blocked by network/OSS upload reset**; no ASR transcript returned for the whole file on this network.
- Generated 7MB silence control file direct/through-backend OSS upload -> **same connection reset**, indicating network or 百炼 temporary OSS large-upload path instability rather than private audio content.
- `ffmpeg` chunk preparation for the provided recording -> **success**; 15 chunks were generated under `/tmp`, most around 480KB.
- Real 百炼 chunk smoke -> **partial success sufficient for upload proof**: chunks `000`, `001`, `002`, `003`, and `005` uploaded and returned HTTP 200 with Fun-ASR transcript segments and speaker metadata; `chunk-000` confirmed 7 segments / 5 speakers, `chunk-001` 11 / 8, `chunk-002` 5 / 3, `chunk-003` 10 / 4, and `chunk-005` 5 / 2. `chunk-004` hit intermittent 百炼 upload policy / temporary OSS connection reset on this local network.
- Focused backend rerun after result URL fix: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AliyunAsrServiceTest,MeetingMinutesServiceTest test` in `backend/` -> **success**.
- Task-scoped `dev-login.py` for `MANAGER-001` / `TASK-134` with intended frontend/state files -> **allowed**.
- Frontend rerun after transcript download optimization: `npm run build` in `frontend/` -> **success**; existing Vite chunk-size warning remains.
- Local dev server reachability after UI change: `curl -sS -I http://127.0.0.1:5173/` -> **success**, HTTP 200.
- Diff hygiene after UI change: `git diff --check` -> **success**.
- Playwright desktop page-open after UI change -> **partial**; after network escalation the wrapper opened `http://127.0.0.1:5173/` and captured the login surface, but full AI 听记 screenshot QA was not completed because local backend/login was unavailable in this session.

## Completion

- Status: `done`.
- Closeout rationale: feature work and focused verification are complete; chunked real 百炼 upload/ASR proved the provider path, transcript Markdown download is implemented, and user confirmed the whole-file reset is local-network-only while the online environment is normal.
