---
kind: task-status
task_id: TASK-119
assignee: MANAGER-001
owner_role: project-manager
status: review
branch: codex/TASK-119-agent-access-control
pr_url: https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/22
spec_path: docs/specs/FEAT-042-agent-access-control.md
assignment_path: .claw/assignments/TASK-119.yaml
updated_at: 2026-05-28T04:27:41Z
updated_by: MANAGER-001
---

# TASK-119 Agent Access Control And User Authorization

## Scope

Own FEAT-042 implementation for first-phase Agent access control:

- `agent_access_grant` and `agent_permission_audit` persistence via `V60__agent_access_control.sql`.
- `AgentAccessControlService` for `VIEW`, `RUN`, `DEBUG`, `EDIT`, `PUBLISH`, `MANAGE`, `OPENAPI`, and `LOG_VIEW`.
- First-phase principals: `ORG`, `USER`, `SYSTEM_ROLE`, and owner implicit permissions.
- Backend gates for Agent list/detail/run/debug/publish/Open API/log entry points.
- Open API Key run-as validation requiring target Agent `RUN`.
- Admin/Agent Builder permission management UI that follows product-register design rules.

## Out Of Scope

- Full custom role management.
- Department management.
- User group management UI or membership maintenance.
- Cross-organization authorization.
- Rebuilding business row-level data permissions for CRM, knowledge documents, or third-party systems.
- Expanding Tool permissions through Agent `RUN` grants.

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-119-agent-access-control`.

## Verification Target

- Backend focused tests for `AgentAccessControlService`.
- Integration tests for ordinary member denial, explicit user grant allow, admin/owner implicit permissions, and grant revocation.
- Open API run-as denial test when the run-as user lacks target Agent `RUN`.
- Frontend build: `npm run build` in `frontend/`.
- Browser screenshots for the permission management UI on desktop and 390px mobile.
- `git diff --check`.
- `.claw` state validation.

## Assignment History

- 2026-05-20T20:47:44+08:00: User requested assigning the Agent access control task to Owen; task assigned to `MANAGER-001`.

## Progress

- Assignment and task status initialized.
- FEAT-042 design document already exists and is linked as the source spec.
- 2026-05-27: PM-scoped preflight found `V57`, `V58`, and `V59` already exist on mainline; assignment was corrected to reserve `V60__agent_access_control.sql` for this task before implementation.
- 2026-05-27: Implemented first-phase ACL persistence, `AgentAccessControlService`, backend gates, OpenAPI run-as validation, and Agent Builder permission management UI.
- 2026-05-27: Continued verification on current source. Local Spring Boot on `18080` applied `V60`, `/agents` returned effective ACL payloads, and browser QA confirmed the Agent Builder `权限管理` dialog loads existing `ORG VIEW/RUN` grants with no console errors.
- 2026-05-27: Redesigned the Agent Builder `权限管理` dialog layout and styling after UI review: separated新增授权/current grants, replaced the cramped checkbox row with a stable permission matrix, added summary counts, and tightened the grant table hierarchy.
- 2026-05-28: Resumed on `codex/TASK-119-agent-access-control`, fixed ACL ordering so missing/deleted/cross-org Agents still return 404 instead of permission-shaped 403, tightened OpenAPI run-as active-member validation, and added an integration test for run-as users without Agent `RUN`.
- 2026-05-28: Resolved the previous local integration-test blocker: host IPv4 `127.0.0.1:5432` was intercepted by an SSH listener, while Docker PostgreSQL was reachable on IPv6 `::1`; reran the focused integration tests with `SPRING_DATASOURCE_URL=jdbc:postgresql://[::1]:5432/agentcici_test`.
- 2026-05-28: Fixed permission dialog footer and width stability after visual bug report: removed accidental horizontal body scrolling, allowed permission action tiles to wrap within the modal, and added a scoped footer rail so `关闭 / 保存权限` keep safe right padding.
- 2026-05-28: Removed the unintended hover/focus background and shadow from the permission grant row `移除` text action so it stays a product-register inline command.

## Completed Work

- Added `agent_access_grant`, `agent_permission_audit`, `agent_definition.owner_user_id`, and existing-agent `ORG VIEW/RUN` backfill in `V60__agent_access_control.sql`.
- Added effective permission checks for `VIEW`, `RUN`, `DEBUG`, `EDIT`, `PUBLISH`, `MANAGE`, `OPENAPI`, and `LOG_VIEW`, including admin/owner implicit allow and active grant matching.
- Gated Agent list/detail/edit/delete/spec/bindings/skills/publish/runtime, chat run, OpenAPI key management, OpenAPI call logs, and OpenAPI authenticate/run-as paths.
- Added Agent Builder `权限管理` dialog for `ORG`, `USER`, and `SYSTEM_ROLE` grants with compact product-register styling.
- Added focused unit coverage for admin/owner implicit permissions, explicit user grant allow, and expired grant denial.
- Added integration coverage that OpenAPI Key creation rejects a run-as user who lacks target Agent `RUN` permission.
- Preserved existing Agent delete/list not-found semantics under ACL by checking current-org resource existence before enforcing `VIEW` or `MANAGE`.

## Verification Evidence

- `identity`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/assignments/TASK-119.yaml .claw/tasks/TASK-119.md .claw/task-board.md .claw/current-status.md .claw/team-status.md --no-cache --json` -> allowed.
- `task-preflight`: task-scoped `dev-login.py` for `MANAGER-001` on `codex/TASK-119-agent-access-control` with representative backend/frontend/spec/task files -> allowed.
- `assignment-fix`: `check-assignment.py` for representative V60/backend/frontend/spec/task files -> allowed.
- `backend-compile`: `mvn -q -DskipTests compile` in `backend/` -> success.
- `backend-unit`: `mvn -q -Dtest=AgentAccessControlServiceTest test` in `backend/` -> success.
- `local-runtime-migration`: `mvn -q spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=18080` -> started current source and applied `V60__agent_access_control.sql` to local PostgreSQL.
- `acl-api-smoke`: `GET /agents/approval-agent/access-grants` on current source -> success, returned default `ORG VIEW/RUN` grants; `GET /agents` returned `access.permissions` and `canManage/canEdit/canRun/canOpenApi/canViewLogs`.
- `frontend-build`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `browser-desktop`: Vite against current backend at `http://127.0.0.1:5177/admin/agent-builder/approval-agent` -> success; `权限管理` button opened the dialog, existing grants loaded, layout matched product-register modal rules, and console errors were `0`. Screenshot: `output/playwright/task119-acl-dialog-desktop.png`.
- `frontend-redesign-build`: `npm run build` in `frontend/` after UI redesign -> success; existing Vite chunk-size warning remains.
- `frontend-redesign-browser`: Playwright desktop QA at `http://127.0.0.1:5178/admin/agent-builder/approval-agent` against current source on backend `18081` -> success; redesigned dialog rendered at 980x608, no horizontal/vertical document overflow, console errors `0`. Screenshot: `output/playwright/task119-acl-dialog-redesign-desktop.png`.
- `diff`: `git diff --check` -> success.
- `state`: `validate-state.py .claw` -> success.
- `resume-identity`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-119` on `codex/TASK-119-agent-access-control` with representative backend/frontend/spec/task files -> allowed.
- `resume-assignment`: `check-assignment.py` for representative V60/backend/frontend/spec/task files -> allowed.
- `resume-backend-compile`: `mvn -q -DskipTests compile` in `backend/` -> success.
- `resume-backend-unit`: `mvn -q -Dtest=AgentAccessControlServiceTest test` in `backend/` -> success.
- `resume-backend-integration`: `SPRING_DATASOURCE_URL=jdbc:postgresql://[::1]:5432/agentcici_test SPRING_DATASOURCE_USERNAME=cici SPRING_DATASOURCE_PASSWORD=cici123 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3 mvn -q -Dtest=AgentDefinitionDeleteIntegrationTest,AgentOpenApiIntegrationTest test` in `backend/` -> success.
- `resume-frontend-build`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `resume-diff`: `git diff --check` -> success.
- `resume-state`: `validate-state.py .claw` -> success.
- `dialog-footer-fix-build`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `dialog-footer-fix-browser`: Chrome desktop QA at `http://127.0.0.1:5178/admin/agent-builder/approval-agent` -> success; `权限管理` dialog no longer exposes horizontal drift, permission actions wrap inside the modal, and footer buttons keep consistent right padding.
- `remove-action-style-build`: `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `remove-action-style-browser`: Chrome desktop QA reopened `权限管理`; backend grant row restored because no previous save occurred, and the `移除` action renders as borderless transparent text.
