---
kind: task-status
task_id: TASK-123
assignee: DEV-fengchu
owner_role: fullstack-agent
status: done
branch: codex/TASK-123-openapi-cloudcc-token-override
pr_url: n/a
spec_path: docs/specs/FEAT-045-openapi-cloudcc-token-override.md
assignment_path: .claw/assignments/TASK-123.yaml
updated_at: 2026-05-21T03:49:27Z
updated_by: DEV-fengchu
---

# TASK-123 - OpenAPI CloudCC token override and key typing

## Scope

Own FEAT-045 implementation for OpenAPI CloudCC token override:

- add `agent_api_credential.key_type` with first-phase `standard` and `cloudcc`
- expose `keyType` in Agent API key management create/read paths
- extend OpenAPI runtime request DTOs with `cloudccContext`
- validate `standard` vs `cloudcc` behavior in the shared OpenAPI runtime layer
- introduce request-scoped CloudCC runtime override so native CloudCC tools, MCP CloudCC tools, and skill API `integration:cloudcc.accessToken` all reuse caller-supplied token when allowed
- block silent fallback from `cloudcc` key runtime to run-as-derived CloudCC token
- harden logs, traces, and error responses so CloudCC token never leaks
- update in-repo OpenAPI spec/docs wording where required

## Out Of Scope

- OA or Salesforce caller-token implementation
- unrelated FEAT-036 parity items that do not serve FEAT-045
- redesigning internal non-CloudCC run-as semantics

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `DEV-fengchu` on branch `codex/TASK-123-openapi-cloudcc-token-override`.

## Verification Target

- Backend integration coverage for `standard` compatibility and `cloudcc` required-token behavior
- tests proving no fallback to run-as token when caller CloudCC token is invalid or expired
- tests proving CloudCC token does not leak into call logs or trace persistence
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest test` in `backend/`
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` in `backend/`
- `npm run build` in `frontend/`
- `git diff --check`
- `.claw` state validation after manager integration if state files are updated during handoff

## Coordination Notes

- FEAT-045 is adjacent to FEAT-036 and FEAT-021. Shared OpenAPI runtime seams should be changed once and reused.
- If work touches FEAT-036 compatibility endpoints in the same branch, they must inherit FEAT-045 key typing and CloudCC override rules.
- This task should preserve current FEAT-021 `/chat` and `/chat/stream` compatibility while extending them with the new guarded CloudCC behavior.

## Assignment History

- 2026-05-21T11:18:00+08:00: User requested formal in-repo renumbering of the external CloudCC token override draft and assignment to `DEV-fengchu`. `MANAGER-001` created FEAT-045 and TASK-123.

## Progress

- Assignment and task status initialized.
- Formal in-repo feature spec created at `docs/specs/FEAT-045-openapi-cloudcc-token-override.md`.
- Implementation completed on `codex/TASK-123-openapi-cloudcc-token-override`: key typing, guarded CloudCC context validation, request-scoped CloudCC runtime override, token leak hardening, and OpenAPI docs/UI wording are in place.

## Completed Work

- Added `agent_api_credential.key_type` migration with `standard` default and `cloudcc` as the first embedded CloudCC runtime key type.
- Exposed `keyType` through Agent API key create/list responses and management UI copy.
- Extended shared OpenAPI chat runtime DTOs with `cloudccContext` and enforced `standard` vs `cloudcc` rules in the common run service.
- Added request-scoped CloudCC session override in `CloudccAccessTokenService` so native CloudCC, MCP CloudCC, and skill API auth paths that call `getSessionContext` reuse the caller-supplied token during the OpenAPI request.
- Blocked CloudCC runtime fallback to run-as-derived credentials when a CloudCC override is active for the same org.
- Added CloudCC token and gateway validation, including expired JWT rejection, denied local/IP hosts, trusted CloudCC host checks, and strict `/lightningapi` / `/setup` path-prefix boundaries.
- Kept caller CloudCC tokens out of OpenAPI call-log failure messages, request persistence, response payloads, and trace assertions.
- Updated OpenAPI docs modal and API key dialog wording for `standard` vs `cloudcc` behavior.

## Verification Evidence

- `identity-manager-assignment`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments .claw/tasks docs/specs --no-cache --json` -> allowed.
- `identity-task`: `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/dev-login.py .claw --ssh-key /Users/xuhm/.ssh/id_ed25519_agentcici_fengchu --developer DEV-fengchu --git-username Bimo --task TASK-123 --branch codex/TASK-123-openapi-cloudcc-token-override --files backend/src/main/java/com/codehouse/ciciassistant/openapi/AgentOpenApiRunService.java backend/src/main/java/com/codehouse/ciciassistant/integration/service/CloudccAccessTokenService.java backend/src/main/java/com/codehouse/ciciassistant/openapi/api/AgentOpenApiCredentialController.java backend/src/main/java/com/codehouse/ciciassistant/openapi/domain/AgentApiCredentialEntity.java backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationService.java backend/src/main/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiCredentialService.java backend/src/test/java/com/codehouse/ciciassistant/openapi/AgentOpenApiIntegrationTest.java backend/src/main/resources/db/migration frontend/src/assistant/AgentOpenApiDocsDialog.tsx frontend/src/assistant/AgentOpenApiKeysDialog.tsx .claw/tasks/TASK-123.md --no-cache --json` -> allowed.
- `backend-openapi`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest test` in `backend/` -> success.
- `backend-compile`: `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` in `backend/` -> success.
- `frontend`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `diff`: `git diff --check` -> success.
- `state`: `python3 /Users/xuhm/.codex/skills/cc-aidev-guidelines-common/scripts/validate-state.py .claw` -> success.
