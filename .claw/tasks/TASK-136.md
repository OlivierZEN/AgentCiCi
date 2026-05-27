---
kind: task-status
task_id: TASK-136
status: review
updated_at: 2026-05-27T15:34:00+08:00
updated_by: DEV-fengchu
assignee: DEV-fengchu
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-136.yaml
spec_path: docs/specs/FEAT-055-frontend-auth-token-sync.md
---

# TASK-136 - Frontend auth token sync across tabs

## Scope

- Implement `FEAT-055` for assistant, admin, and platform authenticated frontend requests.
- Add token storage, authenticated fetch, storage sync, and one-shot 401 retry behavior.
- Handle assistant SSE token changes.

## Source Feedback

- `R20260527-ZK4M2` from the fixed `功能需求` document.
- Linked design doc: `https://zucfl0psd6.feishu.cn/docx/D8mXdjbxboJiaCxFD3CcLIyPn6c`.

## Initial Analysis

- This is a cross-cutting frontend auth reliability task, not a backend JWT task.
- Direct `Authorization: Bearer ${token}` call sites are widespread, so the implementation should migrate high-risk authenticated product paths first and test representative surfaces.

## Acceptance

- Requests read latest token from localStorage before send.
- Other tabs update state or logout after storage changes.
- 401 retry happens once only when a newer token exists.
- Assistant SSE starts with latest token and reconnects/stops on token changes.

## Verification

- 2026-05-27: `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/dev-login.py .claw --developer DEV-fengchu --task TASK-136 --branch codex/TASK-136-frontend-auth-token-sync --git-username Bimo --ssh-key /Users/xuhm/.ssh/id_ed25519_agentcici_fengchu --files frontend/src docs/specs/FEAT-055-frontend-auth-token-sync.md .claw/tasks/TASK-136.md --json` passed with `status: allowed`.
- 2026-05-27: `npm ci` completed in `frontend/` to restore local dependencies for this worktree.
- 2026-05-27: `npm test` passed: 7 files, 32 tests.
- 2026-05-27: `npm run build` passed; Vite reported only the existing large chunk warning.
- 2026-05-27: `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/check-assignment.py .claw --developer DEV-fengchu --task TASK-136 --branch codex/TASK-136-frontend-auth-token-sync --git-username Bimo --ssh-signing-key-fingerprint SHA256:xvufU1n4Ov0fE7jEGrV82H/ABxHdm2VD2TKRHoNSEdQ --files frontend/src .claw/tasks/TASK-136.md --json` passed with `status: allowed`.
- 2026-05-27: `git diff --check` passed.

## Changed Files

- `frontend/src/auth/authStorage.ts` - added localStorage token parsing, payload writes, clearing, authenticated fetch, and storage-event helpers.
- `frontend/src/auth/useAuthStorageSync.ts` - added cross-tab storage sync hook.
- `frontend/src/auth/authStorage.test.ts` - covered parsing, clearing, storage sync filtering, and one-shot 401 retry behavior.
- `frontend/src/assistant/AssistantApp.tsx` and `frontend/src/chat/streamChat.ts` - moved representative assistant requests and assistant SSE starts onto latest-token reads with storage-change state updates.
- `frontend/src/admin/AdminGuard.tsx`, `frontend/src/admin/AdminShell.tsx`, and `frontend/src/admin/AdminLogin.tsx` - centralized admin auth reads/writes and storage-change handling for guard/shell flows.
- `frontend/src/platform/PlatformGuard.tsx`, `frontend/src/platform/PlatformShell.tsx`, `frontend/src/platform/PlatformLogin.tsx`, `frontend/src/platform/pages/PlatformHomePage.tsx`, and `frontend/src/platform/pages/platformTenantsShared.ts` - centralized platform auth reads/writes and migrated representative platform requests to latest-token fetches.

## Handoff

- Implementation is ready for Codeup review on branch `codex/TASK-136-frontend-auth-token-sync`.
- `.claw/test-report.md` was not updated because TASK-136 assignment scope allows `.claw/tasks/**` but not the shared test-report file.
