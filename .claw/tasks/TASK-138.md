---
kind: task-status
task_id: TASK-138
status: review
updated_at: 2026-05-27T07:33:11Z
updated_by: DEV-fengchu
assignee: DEV-fengchu
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-138.yaml
spec_path: docs/specs/FEAT-057-openapi-docs-copy-cleanup.md
---

# TASK-138 - OpenAPI docs copy cleanup

## Scope

- Implement `FEAT-057` for OpenAPI documentation copy and example cleanup.
- Remove duplicated auth explanation.
- Rename `CLOUDCC_PAGE_TOKEN` to `CLOUDCC_OPENAPI_TOKEN`.
- Add CloudCC help links for accessToken and baseUrl.

## Source Feedback

- `R20260527-5R8TN` and `R20260527-3M7KP` from the fixed `功能需求` document.

## Initial Analysis

- This should be a small frontend documentation task unless it lands after `TASK-140`, in which case it must use the new agentless OpenAPI routes.

## Acceptance

- No visible product docs/UI example uses `CLOUDCC_PAGE_TOKEN`.
- Sending-message examples use `CLOUDCC_OPENAPI_TOKEN`.
- CloudCC accessToken and baseUrl help links are present near the CloudCC context explanation.
- Duplicate auth explanation is removed.

## Verification

- 2026-05-27: task-scoped identity gate passed for `DEV-fengchu` on `codex/TASK-138-openapi-docs-copy-cleanup`.
- 2026-05-27: `npm run build` passed in `frontend/`; Vite reported the existing large chunk warning.
- 2026-05-27: `rg -n "CLOUDCC_PAGE_TOKEN" frontend/src` returned no matches.
- 2026-05-27: `rg -n "CLOUDCC_OPENAPI_TOKEN|CloudCC accessToken 获取方式|CloudCC baseUrl 联调说明|sdkcan-kao|apigai-lan" frontend/src/assistant/AgentOpenApiDocsDialog.tsx` confirmed the updated example and links.
- 2026-05-27: `git diff --check` passed.
- 2026-05-27: Desktop Chrome smoke was attempted with a local Vite server and smoke proxy, but the Codex Chrome Extension connection failed with `native pipe is closed` after initial connection. Chrome, the extension, and native host checks all passed; no built-in browser fallback was used.

## Changed Files

- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx`
- `frontend/src/assistant/cici-ui.css`
- `.claw/tasks/TASK-138.md`

## Handoff

- OpenAPI docs copy is ready for review. TASK-140 has not landed in this branch, so route examples still use the current Agent ID route shape.
- `.claw/test-report.md` was not updated because TASK-138 write authorization only includes `.claw/tasks/**`, `docs/specs/**`, and `frontend/src/**`.
