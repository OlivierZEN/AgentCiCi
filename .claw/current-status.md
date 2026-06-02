---
kind: current-status
version: 4
updated_at: 2026-06-02T04:11:00Z
updated_by: MANAGER-001
phase: maintenance
active_task: "TASK-148 production vhost updated: agentcici.salesforchina.com now routes HTTP WeCom callback requests to backend."
next_action: "Retry Enterprise WeChat callback save using agentcici.salesforchina.com; rotate screenshot-exposed Token/EncodingAESKey after verification if needed."
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

- Focus: production domain cutover plus FEAT-023 Enterprise WeChat customer-service continuation.
- TASK-148 production domain cutover deployed on 2026-06-01, then `agentcici.com` was restored to the same production vhost for Enterprise WeChat trusted-domain verification; Nginx backup path is `/opt/cici/backups/20260601-163715-before-agentcici-domain-restore`.
- Current production vhost serves `agentcici.com`, `onechat.agentcici.com`, and `x.agentcici.com`; `http://agentcici.com/WW_verify_fWLFCmXQ3JU36hfZ.txt` and HTTPS with production IP resolution both return `fWLFCmXQ3JU36hfZ`.
- New WeCom verification file deployed directly to production static root on 2026-06-01: `http://agentcici.com/WW_verify_k3ew8Iachbzg5pIw.txt` and HTTPS both return `k3ew8Iachbzg5pIw`; HTTP Nginx now serves `WW_verify_*.txt` files directly.
- `agentaicc.com` added to the production HTTP-only vhost on 2026-06-01; DNS resolves to `47.97.119.160`, and `http://agentaicc.com/WW_verify_fWLFCmXQ3JU36hfZ.txt` plus `http://agentaicc.com/WW_verify_k3ew8Iachbzg5pIw.txt` both return the expected bodies. HTTPS vhost was not changed.
- `agentcici.salesforchina.com` restored to the production HTTP vhost on 2026-06-02 for WeCom callback configuration; the raw callback URL now reaches backend, and a simulated WeCom GET verification returned `HTTP 200` with body `wecom-verify-ok`.
- TASK-147 implemented locally: backend `POST /admin/wecom/kf-accounts/{id}/connection-test` force-refreshes the WeCom access token from stored CorpID/Secret without exposing secrets/tokens; `/admin/channels/wechat-kf` now has `测试连接` and a connection-test metadata row.
- TASK-147 validation passed: WeCom backend focused tests, frontend production build, `git diff --check`, and Playwright desktop smoke with mocked admin/WeCom APIs; screenshot `output/playwright/task-147-wecom-kf-connection-test.png`.
- Local `main` is synced with `origin/main` at `7ee3db6`; `origin/main` contains `e83aa11` production Nginx proxy repair for `/admin/billing/*` API subpaths and `fa8df0e` unified Credits billing presentation.
- Production hotfix deployed on 2026-05-31: synced `deploy/nginx.cici.conf` and `deploy/nginx.cici.ssl.conf` to `/opt/cici/deploy/`, reloaded `cici-frontend` Nginx, and verified `/admin/billing/overview` returns backend JSON instead of SPA HTML.
- Production billing data reset on 2026-05-31: backed up PostgreSQL to `/opt/cici/backups/20260531-162707-before-billing-professional-reset/postgres.dump`, set all 5 ACTIVE organizations to `saas_business` 专业版, reset consumed credits to `0`, remaining credits to `35,000`, rebuilt 5 included-grant ledger rows, and cleared usage events.
- TASK-114 runtime billing is ready for review: billable traces write ledger debits, platform config errors stay non-billable, and org-admin billing shows `官网报价条目 · Credits 包`.
- TASK-144 implemented locally: public website now uses AgentCiCi enterprise agent platform IA with bilingual `Solutions`, `SkillsHub`, `Pricing`, `Docs`, and `Community`; old `/suite/*`, `/pricing/global`, and `/autoservice/*` public routes redirect to the new structure.
- TASK-145 integrated locally: model provider configuration moved from organization administration to platform operations; runtime model credentials, Agent base-model options, and embedding options resolve from platform governance scope.
- TASK-143 implemented and integration-verified: platform-configurable SaaS/private editions plus protected organization-admin `/admin/billing` read chain for current edition, credits balance, consumption, quota warnings, usage events, ledger details, and readable Credits consumption explanations.
- Local integration gates pass: TASK-145 focused backend integration tests, billing backend tests, billing frontend unit tests, frontend production build, and `git diff --check`.
- Status governance: task progress source is `.claw/tasks/TASK-xxx.md`; `.claw/task-board.md` is PM/integration-owned; `.claw/assignments/TASK-xxx.yaml` status tracks authorization lifecycle only.
- Normalized status: `TASK-132`, `TASK-133`, and `TASK-137` are `review`; `TASK-140` is `in_progress`; `TASK-142`, `TASK-136`, `TASK-138`, and `TASK-139` are `ready`.
- Assigned from feedback: `DEV-fengchu` owns `TASK-136`, `TASK-138`, `TASK-139`, and `TASK-140`.
- Existing mainline active work remains: `TASK-115`, `TASK-116`; billing platform configuration is split to `TASK-143`, and runtime billing is `TASK-114` review work.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks only
- `.claw/tasks/TASK-148.md`, `docs/specs/FEAT-063-production-domain-cutover.md` - production domain cutover
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
