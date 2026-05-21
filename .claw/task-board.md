---
kind: task-board
version: 4
updated_at: 2026-05-21T10:49:05Z
updated_by: MANAGER-001
board_status: active
---

# Task Board

`task-board.md` is a compact index. Historical task cards are archived in `.claw/task-archive.md`.

Recommended statuses: `todo` / `ready` / `in_progress` / `blocked` / `review` / `done` / `canceled`
Recommended priorities: `critical` / `high` / `medium` / `low`

## Active Tasks

### TASK-124 - FEAT-046 platform tenant manual provisioning and lifecycle split

- status: `in_progress`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md`
- task_status_path: `.claw/tasks/TASK-124.md`
- assignment_path: `.claw/assignments/TASK-124.yaml`
- blocked_by: `none`
- next_action: `MANAGER-001` runs task-scoped `dev-login.py` on `codex/TASK-124-feat-046-platform-tenant-provisioning`, then lands backend provisioning, route split, modal flow, and visual QA.

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
