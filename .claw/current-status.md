---
kind: current-status
version: 4
updated_at: 2026-06-02T05:26:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "Local main integration: TASK-146 ops observability and TASK-147/TASK-148 WeCom customer-service/domain work merged into main."
next_action: "Commit the validated local main integration; then push or open review as appropriate."
read_next:
  goals: false
  decisions: false
  issue_list: false
  task_board: true
  active_task_status: false
  test_report: true
  devops: false
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Focus: local main integration for ops observability plus FEAT-023 Enterprise WeChat customer-service continuation.
- TASK-146 implemented on `codex/TASK-146-ops-observability-audit`: added `/admin/agents/runtime-snapshots`, trace/list `errorReason`, tool `status/errorMessage`, `/ops/audit/logs` filtering and redaction, and production-grade `/admin/ops` audit UI.
- TASK-146 validation passed: `mvn -q -Dtest=AgentRunTraceIntegrationTest test`, `npm run build`, `git diff --check`, and desktop browser smoke for `/admin/ops` with screenshots under `output/playwright/`.
- `.claw` state validation is still blocked by an existing out-of-scope issue: `.claw/tasks/TASK-143.md` has 121 lines, over the 120-line budget.
- TASK-148 production domain work deployed: `agentcici.com`, `agentaicc.com`, and `agentcici.salesforchina.com` HTTP vhosts now serve WeCom verification/callback routes as needed; simulated WeCom GET verification on `agentcici.salesforchina.com` returned `HTTP 200` body `wecom-verify-ok`.
- TASK-147 implemented locally: backend `POST /admin/wecom/kf-accounts/{id}/connection-test` force-refreshes the WeCom access token from stored CorpID/Secret without exposing secrets/tokens; `/admin/channels/wechat-kf` now has `测试连接` and a connection-test metadata row.
- TASK-147 validation passed: WeCom backend focused tests, frontend production build, `git diff --check`, and Playwright desktop smoke with mocked admin/WeCom APIs; screenshot `output/playwright/task-147-wecom-kf-connection-test.png`.
- Local `main` post-merge validation passed after merging `codex/TASK-146-ops-observability-audit` and `codex/TASK-147-wecom-kf-connection-test`; the WeCom client constructor merge issue was fixed by explicit Spring constructor injection.
- Production billing Nginx hotfix and professional-plan data reset were completed on 2026-05-31; details remain in `.claw/test-report.md`.
- TASK-114 runtime billing is ready for review: billable traces write ledger debits, platform config errors stay non-billable, and org-admin billing shows `官网报价条目 · Credits 包`.
- TASK-143 implemented and integration-verified: platform-configurable SaaS/private editions plus protected organization-admin `/admin/billing` read chain for current edition, credits balance, consumption, quota warnings, usage events, ledger details, and readable Credits consumption explanations.
- Local integration gates pass: TASK-145 focused backend integration tests, billing backend tests, billing frontend unit tests, frontend production build, and `git diff --check`.
- Status governance: task progress source is `.claw/tasks/TASK-xxx.md`; `.claw/task-board.md` is PM/integration-owned; `.claw/assignments/TASK-xxx.yaml` status tracks authorization lifecycle only.
- Normalized status: `TASK-132`, `TASK-133`, and `TASK-137` are `review`; `TASK-140` is `in_progress`; `TASK-142`, `TASK-136`, `TASK-138`, and `TASK-139` are `ready`.
- Assigned from feedback: `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Existing mainline active work remains: `TASK-115`, `TASK-116`; billing platform configuration is split to `TASK-143`, and runtime billing is `TASK-114` review work.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-146.md`, `.claw/tasks/TASK-148.md`, `.claw/tasks/TASK-147.md` - newly merged task status slices
- `.claw/tasks/TASK-145.md`, `docs/specs/FEAT-062-platform-model-provider-governance.md` - platform model-provider governance
- `.claw/tasks/TASK-144.md`, `docs/specs/FEAT-061-agentcici-public-website-restructure.md` - public AgentCiCi website restructure
- `.claw/tasks/TASK-114.md`, `.claw/tasks/TASK-143.md`, `docs/specs/FEAT-037-saas-billing-usage-ledger.md` - billing runtime ledger and edition configuration tasks
- `.claw/tasks/TASK-142.md`, `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md` - OpenAPI SSE streaming bug assignment
- `.claw/tasks/TASK-132.md`, `.claw/tasks/TASK-133.md`, `.claw/tasks/TASK-137.md` - tasks waiting review/merge
- `.claw/tasks/TASK-140.md`, `.claw/tasks/TASK-139.md`, `.claw/tasks/TASK-136.md`, `.claw/tasks/TASK-138.md`, `.claw/tasks/TASK-141.md` - assigned implementation tasks
- `.claw/tasks/TASK-115.md`, `.claw/tasks/TASK-116.md` - remaining active slices

## Maintenance Rules

- Keep this file under 60 lines.
- Keep historical progress out of this file.
- Put task progress, verification, changed files, and handoff notes in `.claw/tasks/TASK-xxx.md`.
