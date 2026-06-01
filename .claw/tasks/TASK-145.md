---
kind: task-status
task_id: TASK-145
assignee: MANAGER-001
owner_role: fullstack-agent
status: review
branch: codex/TASK-145-platform-model-provider-governance
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
assignment_path: .claw/assignments/TASK-145.yaml
updated_at: 2026-06-01T00:00:00Z
updated_by: MANAGER-001
---

# TASK-145 Platform Model Provider Governance

## Goal

Move model provider configuration from organization administration to platform operations so credits, resource responsibility, and provider availability are governed centrally.

## Scope

- Backend platform model provider APIs.
- Backend organization-side provider write API lockout.
- Model provider service reads runtime provider credentials, Agent base-model candidates, and embedding candidates from the platform governance scope.
- Platform `/platform/models` UI and navigation entry.
- Organization admin model navigation removal and `/admin/models` redirect to billing.
- Help copy updates so model provider configuration is described as platform-managed.
- Focused backend and frontend verification.

## Verification

- 2026-06-01 follow-up: restored platform-managed scene model routing after trace review showed runtime had regressed to first selected model.
- `python3 .../scripts/dev-login.py .claw --developer MANAGER-001 ... --files ... --json` -> success, manager identity allowed.
- `python3 .../scripts/check-assignment.py .claw --developer MANAGER-001 --task TASK-145 ... --files ... --json` -> success, representative backend/frontend writes allowed after assignment expansion.
- `mvn -q -DskipTests compile` in `backend/` -> success.
- `npm run build` in `frontend/` -> success; existing Vite chunk-size warning remains.
- `git diff --check` -> success.
- Local run -> success. Docker Desktop was started, existing `cici-postgres`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant` containers were started, backend `mvn spring-boot:run -Dspring-boot.run.profiles=local` started on `8080`, frontend `npm run dev -- --host 127.0.0.1` started on `5173`.
- Local smoke -> success. `GET http://127.0.0.1:5173/` returned 200, `GET http://127.0.0.1:8080/actuator/health` returned `{"status":"UP"}`, platform login succeeded, and `GET /platform/models/providers` returned 6 providers.
- `SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/agentcici_test' SPRING_DATASOURCE_USERNAME=cici SPRING_DATASOURCE_PASSWORD=cici123 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3 mvn -q -Dtest='ModelProviderServiceIntegrationTest,PlatformModelProviderIntegrationTest,ManagementConsoleIntegrationTest' test` in `backend/` -> success.

## Changed Files

- `docs/specs/FEAT-062-platform-model-provider-governance.md`
- `.claw/assignments/TASK-145.yaml`
- `.claw/tasks/TASK-145.md`
- `.claw/current-status.md`
- `.claw/task-board.md`
- `backend/src/main/java/com/codehouse/ciciassistant/model/api/PlatformModelProviderController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/model/api/ModelConfigController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/model/service/ModelProviderService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ModelRouterService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/MeetingMinutesService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/customerinsight/service/CustomerInsightService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/model/PlatformModelProviderIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/model/ModelProviderServiceIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/management/ManagementConsoleIntegrationTest.java`
- `frontend/src/App.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/admin/pages/AdminAgentBuilderPage.tsx`
- `frontend/src/admin/pages/AdminKnowledgePage.tsx`
- `frontend/src/admin/pages/AdminModelsPage.tsx`
- `frontend/src/assistant/AgentBuilderShell.tsx`
- `frontend/src/help/HelpCenterApp.tsx`
- `frontend/src/help/helpContent.ts`
- `frontend/src/platform/PlatformShell.tsx`
- `frontend/src/platform/pages/PlatformModelsPage.tsx`
- `frontend/src/styles.css`

## Handoff

- Worktree: `/Volumes/AISpace/codehouse/cc-codeup-agentcici_PM_TASK145`.
- This task was split into its own worktree because the main workspace currently contains unrelated TASK-144 public website changes.
- Implementation is ready for review. Local frontend/backend/dev dependencies are currently running for manual verification.
