---
kind: current-status
version: 4
updated_at: 2026-06-18T00:19:00+08:00
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-155 platform console UI polish is ready for review; TASK-154, TASK-153, and TASK-152 remain review/local validation work in the same dirty worktree."
next_action: "Review local changes on branch codex/TASK-152-ai-minutes-billing-timeout, then decide how to split or commit TASK-152/TASK-153/TASK-154/TASK-155."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `codex/TASK-152-ai-minutes-billing-timeout`.
- TASK-155 completes the `/platform/*` desktop UI polish sweep for the authenticated platform console: global platform theme gradients were removed, model page/provider/model route styling was brought back to the Gilded Ledger gold-line vocabulary, platform buttons/tabs/checkboxes were normalized, website lead search and audit filter layouts were fixed, and audit errors now show a Chinese fallback.
- TASK-155 validation passed: manager identity gate, task-scoped assignment gate, `npm run build`, `git diff --check`, and Playwright desktop screenshots at `1440x1000` for `/platform`, `/platform/skills`, `/platform/models`, `/platform/integrations`, `/platform/tools`, `/platform/billing`, `/platform/tenants`, `/platform/tenants/demo-org`, `/platform/website-leads`, and `/platform/audit`; all final route screenshots had no horizontal overflow.
- TASK-155 known limitation: current local `/api/platform/audit/logs?limit=100` returns backend HTTP 500; frontend displays a Chinese fallback, but the backend audit API issue is outside the UI polish task.
- TASK-154 completes the Credits metering sweep for production-ready runtime gaps: Open API blocking/streaming success now records request-level `open_api_chat`, KB document/chunk indexing success records `kb_indexing`, and personal workflow success outside chat records `workflow_run`.
- TASK-154 validation passed: manager identity gate, task-scoped assignment gate, `mvn -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest,KnowledgeBaseLifecycleIntegrationTest,AdminBillingIntegrationTest test`, and `git diff --check`.
- TASK-153 moves Tavily Search and Iflytek realtime ASR configuration to platform operations: new `/platform/integrations` API/page owns those configs, organization `/integrations` hides/rejects them, runtime config resolves from the platform governance org, and `/platform/models` now includes OneKeyToken with `https://my.onekeytoken.com/v1`.
- TASK-153 latest UI polish renames the platform sidebar entry to `模型配置` and makes `/platform/models` use peer tabs for `模型厂商治理` and `场景模型路由`; the route tab is no longer nested under the selected provider panel.
- TASK-153 latest backend fix treats OneKeyToken model listing as a static catalog from the developer guide because the current gateway does not expose OpenAI-compatible `/models`; OneKeyToken `全部模型` now opens without a 404 and labels the modal as `预设模型`.
- TASK-153 validation passed: manager identity gate, task-scoped assignment gate, `mvn -Dmaven.repo.local=.m2 -Dtest=PlatformIntegrationGovernanceIntegrationTest,PlatformModelProviderIntegrationTest,ManagementConsoleIntegrationTest,TavilyToolServiceTest test`, focused `PlatformModelProviderIntegrationTest`, repeated `npm run build`, `git diff --check`, local API smoke, Playwright desktop smoke, and local backend/frontend restart.
- TASK-152 fixes the user-reported local AI 听记 defects: successful embedded meeting-minutes summary now records `meeting-minutes` usage events and credits ledger debits; frontend AI 听记 start no longer hard-forces disabled-by-default Iflytek in local/default paths; WebSocket start failure reports immediately instead of only waiting for the generic timeout.
- TASK-152 validation passed: manager identity gate, task-scoped assignment gate, `mvn -Dmaven.repo.local=.m2 -Dtest=EmbedAppIntegrationTest,AdminBillingIntegrationTest test`, `npm run build`, local API smoke, and `git diff --check`.
- Local services have been restarted with the latest TASK-152/TASK-153 work; backend `8080` health is `UP` and frontend `5173` returns `HTTP 200`.
- TASK-155, TASK-154, TASK-151, TASK-150, TASK-149, TASK-146, TASK-147, TASK-145, TASK-144, TASK-143, TASK-137, TASK-133, and TASK-132 remain review/integration work in `.claw/task-board.md`.
- TASK-142, TASK-141, TASK-139, TASK-138, TASK-136, TASK-116, and TASK-115 remain ready or active per `.claw/task-board.md`.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-155.md` - platform console UI polish state, changes, screenshots, and verification.
- `docs/specs/FEAT-065-platform-console-ui-polish.md` - scope, design constraints, acceptance criteria, and known audit API limitation.
- `.claw/tasks/TASK-154.md` - Credits metering sweep state, changes, and verification.
- `docs/specs/FEAT-037-saas-billing-usage-ledger.md` - billing facts and TASK-154/TASK-152 metering addenda.
- `.claw/tasks/TASK-153.md` - platform integrations and OneKeyToken provider state, changes, and verification.
- `docs/specs/FEAT-062-platform-model-provider-governance.md` - platform model/integration governance spec and TASK-153 addendum.
- `.claw/tasks/TASK-152.md` - current hotfix state, changes, and verification.
- `docs/specs/FEAT-059-ai-minutes-local-asr.md` - AI 听记 ASR/provider constraints and TASK-152 local start behavior addendum.
- `.claw/test-report.md` - latest verified commands.
- `.claw/devops.md` - local restart and smoke commands.
