---
kind: feature-spec
feature_id: FEAT-055
title: Frontend auth token sync
status: approved
owner_role: fullstack-agent
task_ids: TASK-136
related_decisions: none
related_issues: R20260527-ZK4M2
updated_at: 2026-05-27T03:32:12Z
updated_by: MANAGER-001
---

# FEAT-055 - Frontend Auth Token Sync

## Metadata

- source_feedback: `R20260527-ZK4M2`
- source_doc: `https://zucfl0psd6.feishu.cn/docx/D8mXdjbxboJiaCxFD3CcLIyPn6c`
- status: `ready-for-implementation`
- owner_role: `fullstack-agent`
- created_at: 2026-05-27
- task: `TASK-136`

## Problem

AgentCiCi has separate authenticated surfaces for the assistant workbench, organization admin, and platform console. Each surface stores its auth payload in localStorage, then many components keep the token in React state or props and reuse that in fetch calls.

When several browser tabs are open, a login, organization switch, or logout in one tab updates localStorage only for that tab's in-memory state. Other tabs can continue sending old bearer tokens until refresh, causing avoidable 401 responses and stale UI state.

## Goals

- Every authenticated frontend request reads the latest token from localStorage immediately before sending the request.
- Assistant, admin, and platform tokens stay isolated by storage key.
- Other open tabs react to storage changes and update UI state or return to login where appropriate.
- 401 handling retries once only when localStorage now contains a different token from the one used by the failed request.
- SSE and long-lived assistant streams start with the latest token and reconnect when the assistant token changes.

## Non Goals

- No refresh token, silent refresh, backend session table, or JWT lifetime change.
- No merge of assistant, admin, and platform auth identities.
- No change to OpenAPI keys, embed tokens, or CloudCC OpenAPI token semantics.
- No mobile-specific layout or testing scope.

## Design

### Auth Storage Utility

Add a small frontend auth module that owns localStorage parsing and mutation:

- `readAuthPayload(storageKey)` returns parsed auth payload or `null`.
- `readAuthToken(storageKey)` returns the trimmed bearer token or an empty string.
- `writeAuthPayload(storageKey, payload)` serializes login payloads.
- `clearAuthPayload(storageKey)` removes the payload.

The utility must tolerate missing `window` in tests, malformed JSON, empty token values, and unknown payload shapes.

### Auth Fetch Wrapper

Add `authFetch(storageKey, input, init, options)` as the default request path for authenticated frontend API calls.

Rules:

- Build `Authorization: Bearer <latest token>` from `readAuthToken(storageKey)` at request time.
- Preserve caller headers and request options.
- If the response is not 401, return it unchanged.
- If the response is 401, read localStorage again. When the latest token is non-empty and different from the token just used, retry the same request once.
- When retry is not possible or also returns 401, call an optional unauthorized handler so the owning surface can clear state and navigate to login.

### Storage Sync Hook

Add `useAuthStorageSync(storageKey, onChange)` for cross-tab state sync.

Rules:

- Listen to the browser `storage` event.
- Ignore events for other storage areas or keys.
- Invoke `onChange(readAuthPayload(storageKey))`.
- Remember that the current tab does not receive its own storage event, so login and logout handlers must still update local React state directly.

### Surface Adoption

Minimum implementation order:

1. Centralize login, organization choice, and logout writes for `LS_ASSISTANT_TOKEN`, `LS_ADMIN_TOKEN`, and `LS_PLATFORM_TOKEN`.
2. Replace high-traffic authenticated fetch calls in assistant, admin, and platform guards/shells with `authFetch`.
3. Wire storage sync into `AssistantApp`, `AdminGuard` or `AdminShell`, and `PlatformGuard` or `PlatformShell`.
4. Update assistant SSE helpers in `frontend/src/chat/streamChat.ts` so stream start reads the latest assistant token and storage changes force reconnect.

### UX Behavior

- Current tab: login/logout updates state immediately.
- Other tabs: token removal sends the relevant surface back to login; token update refreshes local state or fetches `/auth/me` / `/auth/platform/me` before showing identity-sensitive labels.
- Stale request race: one automatic retry is allowed only if another tab has already written a different token.

## Acceptance Criteria

- Two assistant tabs: after tab B logs in again, tab A can perform an authenticated action without refresh and sends the new token.
- Two admin tabs: after tab B switches organization or logs out, tab A no longer keeps making requests with the old admin token.
- Platform console keeps its token isolated and never reads admin or assistant storage.
- 401 retry happens at most once per request and does not loop.
- SSE reconnects or stops cleanly after assistant token storage changes.
- Tests cover token parsing, retry once behavior, storage sync, and at least one representative assistant/admin/platform request path.

## Verification Plan

- Unit tests for `tokenStorage`, `authFetch`, and storage sync hook behavior.
- Focused frontend tests or Playwright desktop smoke for two-tab assistant and admin flows, using mocked endpoints if backend login is unavailable.
- `npm run build`.
- `git diff --check`.

## Handoff Notes

- The source requirement explicitly prefers a small frontend-only fix before any backend auth model change.
- Treat every direct `Authorization: Bearer ${token}` fetch as a migration candidate, but keep the implementation scoped to authenticated product surfaces.
