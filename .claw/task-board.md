---
kind: task-board
version: 4
updated_at: 2026-06-23T00:48:00Z
updated_by: MANAGER-001
board_status: active
---

# Task Board

`task-board.md` is a compact index. Historical task cards are archived in `.claw/task-archive.md`.

Recommended statuses: `todo` / `ready` / `in_progress` / `blocked` / `review` / `done` / `canceled`
Recommended priorities: `critical` / `high` / `medium` / `low`

## Active Tasks

### TASK-158 - Agent runtime concurrency hardening

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md`
- task_status_path: `.claw/tasks/TASK-158.md`
- assignment_path: `.claw/assignments/TASK-158.yaml`
- blocked_by: `none`
- next_action: Push local `main` to `origin/main`; branch merge, main integration gates, and frontend build passed locally.

### TASK-156 - Agent Builder production readiness closure

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-066-agent-builder-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-156.md`
- assignment_path: `.claw/assignments/TASK-156.yaml`
- blocked_by: `none`
- next_action: Review TASK-156 production-readiness closure; focused backend integration, frontend build, real-backend desktop validation, and readiness/evaluation gate smoke passed.

### TASK-157 - Enterprise knowledge platform readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md`
- task_status_path: `.claw/tasks/TASK-157.md`
- assignment_path: `.claw/assignments/TASK-157.yaml`
- blocked_by: `none`
- next_action: Review TASK-157 enterprise KB closure; focused backend integration, frontend build, real-backend desktop validation, Rabbit/Qdrant smoke, and drift audit evidence passed.

### TASK-155 - 运营端前端页面 UI 整体美化

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-065-platform-console-ui-polish.md`
- task_status_path: `.claw/tasks/TASK-155.md`
- assignment_path: `.claw/assignments/TASK-155.yaml`
- blocked_by: `none`
- next_action: Review TASK-155 platform UI polish; task gates, frontend build, `git diff --check`, and desktop Playwright screenshots for all platform routes passed, with `/api/platform/audit/logs` still returning backend 500.

### TASK-154 - Credits metering production readiness sweep

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-154.md`
- assignment_path: `.claw/assignments/TASK-154.yaml`
- blocked_by: `none`
- next_action: Review TASK-154 Credits metering completion; assignment/login gates, Open API/KB/admin billing focused tests, and `git diff --check` passed.

### TASK-153 - Platform-governed Tavily, Iflytek, and OneKeyToken provider

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-153.md`
- assignment_path: `.claw/assignments/TASK-153.yaml`
- blocked_by: `none`
- next_action: Review TASK-153 changes; focused backend tests, frontend build, local API smoke, local service restart, and `git diff --check` passed.

### TASK-152 - AI 听记 credits and start-timeout hotfix

- status: `in_progress`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-152.md`
- assignment_path: `.claw/assignments/TASK-152.yaml`
- blocked_by: `none`
- next_action: Fix local-test defects where AI 听记 start can time out and successful AI 听记 usage does not consume organization credits; then run focused backend validation and local smoke.

### TASK-151 - RBAC and audit production readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-064-rbac-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-151.md`
- assignment_path: `.claw/assignments/TASK-151.yaml`
- blocked_by: `none`
- next_action: Review and merge the TASK-151 platform audit query fix, then redeploy so `/platform/audit` initial load no longer hits the production `text ~~ bytea` error.

### TASK-150 - Knowledge Base production readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-150.md`
- assignment_path: `.claw/assignments/TASK-150.yaml`
- blocked_by: `none`
- next_action: Review and merge TASK-150 production-readiness implementation; frontend build, desktop smoke, backend compile, assignment check, KB lifecycle integration, and Qdrant stack smoke all passed.

### TASK-149 - Knowledge Base DOCX upload parser

- status: `review`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-149.md`
- assignment_path: `.claw/assignments/TASK-149.yaml`
- blocked_by: `none`
- next_action: Review and merge `codex/TASK-149-kb-docx-upload-parser`; local KB lifecycle regression and static diff checks passed.

### TASK-146 - 观测与运维生产就绪收口

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-019-agent-observability-monitoring.md`
- task_status_path: `.claw/tasks/TASK-146.md`
- assignment_path: `.claw/assignments/TASK-146.yaml`
- blocked_by: `none`
- next_action: Review TASK-146 local changes; merge after normal integration gates. State validation has an existing TASK-143 line-budget blocker outside this task.

### TASK-148 - Production domain cutover

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-063-production-domain-cutover.md`
- task_status_path: `.claw/tasks/TASK-148.md`
- assignment_path: `.claw/assignments/TASK-148.yaml`
- blocked_by: `none`
- next_action: Monitor production traffic and update any external integrations still using retired hostnames.

### TASK-147 - WeCom customer-service connection test

- status: `review`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `.claw/tasks/TASK-147.md`
- assignment_path: `.claw/assignments/TASK-147.yaml`
- blocked_by: `none`
- next_action: Review TASK-147 changes, then run live WeCom callback smoke once real account details and filed callback domain are available.

### TASK-145 - Platform model provider governance

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-145.md`
- assignment_path: `.claw/assignments/TASK-145.yaml`
- blocked_by: `none`
- next_action: Included in the local `main` integration merge; run focused backend/frontend integration gates before marking done.

### TASK-144 - AgentCiCi public website restructure

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-061-agentcici-public-website-restructure.md`
- task_status_path: `.claw/tasks/TASK-144.md`
- assignment_path: `.claw/assignments/TASK-144.yaml`
- blocked_by: `none`
- next_action: Included in the local `main` integration merge; rerun frontend build and desktop smoke before marking done.

### TASK-143 - Billing editions configurable in platform operations

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-143.md`
- assignment_path: `.claw/assignments/TASK-143.yaml`
- blocked_by: `none`
- next_action: Local `main` MR validation passed after unified Credits billing presentation; publish/update the Codeup MR for review and merge.

### TASK-142 - OpenAPI chat-messages SSE streaming

- status: `ready`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md`
- task_status_path: `.claw/tasks/TASK-142.md`
- assignment_path: `.claw/assignments/TASK-142.yaml`
- blocked_by: `TASK-140 may change the final public route shape`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-142-openapi-sse-streaming`, then implements true SSE streaming for OpenAPI `chat-messages`.

### TASK-141 - AI 听记本地 FunASR 实时转写

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-059-ai-minutes-local-asr.md`
- task_status_path: `.claw/tasks/TASK-141.md`
- assignment_path: `.claw/assignments/TASK-141.yaml`
- blocked_by: `none`
- next_action: `DEV-houyi` runs task-scoped `dev-login.py` on `codex/TASK-141-local-funasr-realtime-asr`, then implements the local FunASR realtime ASR sidecar and `/ws/asr?provider=local` integration.

### TASK-140 - Remove Agent ID from public OpenAPI routes

- status: `in_progress`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-058-openapi-agentless-endpoints.md`
- task_status_path: `.claw/tasks/TASK-140.md`
- assignment_path: `.claw/assignments/TASK-140.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` completes review fixes / service validation for `codex/TASK-140-openapi-agentless-endpoints`; task status is the progress source.

### TASK-139 - Agent list OpenAPI badge shows only first Agent

- status: `ready`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-139.md`
- assignment_path: `.claw/assignments/TASK-139.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-139-agent-list-openapi-badge`, then fixes list channel data and badge rendering.

### TASK-138 - OpenAPI docs copy cleanup

- status: `ready`
- priority: `medium`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-057-openapi-docs-copy-cleanup.md`
- task_status_path: `.claw/tasks/TASK-138.md`
- assignment_path: `.claw/assignments/TASK-138.yaml`
- blocked_by: `TASK-140 may change the final route examples`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-138-openapi-docs-copy-cleanup`, then updates OpenAPI docs copy.

### TASK-137 - Custom Agent delete action

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-056-custom-agent-delete.md`
- task_status_path: `.claw/tasks/TASK-137.md`
- assignment_path: `.claw/assignments/TASK-137.yaml`
- blocked_by: `none`
- next_action: code is complete on `codex/TASK-137-custom-agent-delete`; review/merge after backend integration rerun when local PostgreSQL is available.

### TASK-136 - Frontend auth token sync across tabs

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-055-frontend-auth-token-sync.md`
- task_status_path: `.claw/tasks/TASK-136.md`
- assignment_path: `.claw/assignments/TASK-136.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-136-frontend-auth-token-sync`, then implements shared token sync.

### TASK-133 - Agent Builder no-model new-Agent model-config redirect

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-133.md`
- assignment_path: `.claw/assignments/TASK-133.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-133-agent-builder-new-agent-model-config-fix`; mark `done` after merge verification.

### TASK-132 - Agent Builder focused-agent skill binding refresh bugfix

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-132.md`
- assignment_path: `.claw/assignments/TASK-132.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-132-agent-builder-skill-refresh-bugfix`; mark `done` after merge verification.

### TASK-116 - Skill module completion and optimization

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-038-admin-skill-module-completion.md`
- task_status_path: `.claw/tasks/TASK-116.md`
- assignment_path: `.claw/assignments/TASK-116.yaml`
- blocked_by: `none`
- next_action: `DEV-wolong` runs task-scoped `dev-login.py`, closes P0 security/regression gaps, then continues P1/P2 work.

### TASK-115 - Knowledge base module maintenance

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-115.md`
- assignment_path: `.claw/assignments/TASK-115.yaml`
- blocked_by: `none`
- next_action: `DEV-zhongda` runs task-scoped `dev-login.py`, executes the P0 hardening package, then continues P1/P2 work.

### TASK-114 - FEAT-037 SaaS billing usage ledger

- status: `ready`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-114.md`
- assignment_path: `.claw/assignments/TASK-114.yaml`
- blocked_by: `none`
- next_action: `MANAGER-001` runs task-scoped `dev-login.py` and continues the end-to-end billing ledger implementation.

## Backlog / Blocked

### TASK-096 - End-to-end CRM embed verification

- status: `blocked`
- priority: `high`
- owner_role: `qa-agent`
- spec_path: `docs/specs/FEAT-032-meeting-minutes-embed-sdk.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC iframe host smoke and ACR hotfix persistence are still open`
- next_action: Confirm the iframe host on the real CloudCC page, then repair ACR credentials and persist the deployed hotfix image.

### TASK-036 - Skill declarative API runtime

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-015-skill-declarative-api-runtime.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Real external API smoke still depends on TASK-023 runtime prerequisites`
- next_action: Close the runtime prerequisites, then finish real external API smoke and browser-level admin verification.

### TASK-023 - CloudCC runtime smoke unblock

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC runtime credentials and local Aliyun API key are not yet verified`
- next_action: Rotate and verify `cc_username/cc_safetymark`, restore a usable local Aliyun API key, then rerun the real `/ai/chat` and CloudCC tool chain smoke.

### TASK-020 - Knowledge base lifecycle completion

- status: `blocked`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `User explicitly paused FEAT-008 continuation`
- next_action: Resume only when requested; restart from page-level regression on document/settings/chunk dialogs.

### TASK-070 - AgentCiCi market positioning and roadmap

- status: `todo`
- priority: `high`
- owner_role: `human`
- spec_path: `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Awaiting a shaped follow-up request`
- next_action: Reuse FEAT-025 as the scope source when the next strategy or packaging task is opened.

### TASK-063 - AI native after-sales agent spec

- status: `todo`
- priority: `high`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `WeCom customer-service account details and data mapping are not yet confirmed`
- next_action: Confirm `open_kfid`, CorpID/secret, Token/AESKey, run-as service user, and first-wave after-sales data sources before implementation resumes.

### TASK-007 - SaaS billing and packaging design

- status: `todo`
- priority: `medium`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-003-saas-billing-and-packaging.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `none`
- next_action: If reopened, start with usage meter events, package/subscription entities, and the admin billing overview before any payment-provider work.

## Completed Tasks

### TASK-134 - AI minutes local audio upload and speaker diarization

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-054-ai-minutes-local-audio-upload.md`
- task_status_path: `.claw/tasks/TASK-134.md`
- assignment_path: `.claw/assignments/TASK-134.yaml`
- blocked_by: `none`
- next_action: `none`; local upload, 百炼 Fun-ASR speaker diarization, transcript normalization, and Markdown `下载转写` are complete. The remaining whole-file reset was confirmed as a local-network limitation; online environment is normal.

### TASK-124 - FEAT-046 platform tenant manual provisioning and lifecycle split

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md`
- task_status_path: `.claw/tasks/TASK-124.md`
- assignment_path: `.claw/assignments/TASK-124.yaml`
- blocked_by: `none`
- next_action: `none`; platform tenant list/detail split, manual provisioning, backend provisioning path, focused tests, and desktop QA are complete.

### TASK-135 - Clear default login account values

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-135.md`
- assignment_path: `.claw/assignments/TASK-135.yaml`
- blocked_by: `none`
- next_action: `none`; assistant, admin, and platform login account inputs now start empty and passed static search, frontend build, and browser checks.

### TASK-131 - Platform account orgless auth context

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-053-platform-account-orgless-auth-context.md`
- task_status_path: `.claw/tasks/TASK-131.md`
- assignment_path: `.claw/assignments/TASK-131.yaml`
- blocked_by: `none`
- next_action: `none`; Codeup change/6 was merged with post-merge state, frontend, backend compile, script, and diff verification. Rerun `PlatformAuthIntegrationTest` later when local Docker/Postgres is available.

### TASK-130 - ACR release version governance and app version badge

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-052-acr-release-version-governance.md`
- task_status_path: `.claw/tasks/TASK-130.md`
- assignment_path: `.claw/assignments/TASK-130.yaml`
- blocked_by: `none`
- next_action: `none`; production releases now use `docs/production-release-runbook.md` and `scripts/release-acr.sh` for one canonical version across ACR tags, Git tag, backend metadata, frontend badge, and deploy env.

### TASK-129 - Admin login organization-selection alignment

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`
- task_status_path: `.claw/tasks/TASK-129.md`
- assignment_path: `.claw/assignments/TASK-129.yaml`
- blocked_by: `none`
- next_action: `none`; `/admin/login` now removes the orgId field, supports organization selection after account login, and passed desktop/mobile QA.

### TASK-127 - Merge remaining local branches into the current branch

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-047-local-branch-integration-pass.md`
- task_status_path: `.claw/tasks/TASK-127.md`
- assignment_path: `.claw/assignments/TASK-127.yaml`
- blocked_by: `none`
- next_action: `none`; remaining local branches are integrated and the dirty worktree has been restored on `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Maintenance Rules

- Keep each task card under 20 lines.
- Store only index fields here.
- Store current task details in `.claw/tasks/TASK-xxx.md`.
- Store old completed, superseded, and historical task cards in `.claw/task-archive.md`.
