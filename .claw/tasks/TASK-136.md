---
kind: task-status
task_id: TASK-136
status: ready
updated_at: 2026-05-27T03:37:58Z
updated_by: MANAGER-001
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

- Pending implementation.

## Handoff

- Assigned to `DEV-fengchu` on branch `codex/TASK-136-frontend-auth-token-sync`. Run task-scoped `dev-login.py` before implementation.
