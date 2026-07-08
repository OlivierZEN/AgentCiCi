---
kind: current-status
version: 4
updated_at: 2026-07-08T10:26:57+08:00
updated_by: MANAGER-001
phase: release
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 is production released in 2.2.3; CloudCC CRM menu visibility hotfix is applied to all 8 apps, monitor user-side refresh/login cache and customer-workbench API smoke."
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: false
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `main`; production is running release `2.2.1` from Git commit `65364b4460c9`.
- User explicitly chose the larger standalone platform direction: new `/admin/data-quality`, facing all data sources; KB and KB connectors are the first adapter.
- TASK-169 is done in production release `2.2.1`: data-source aggregation, quality scan, duplicate/invalid data detection, regex cleaning preview/apply, manual review queue, annotation suggestions, audited apply flow, standalone `/admin/data-quality`, and independent「知微画像」AI 应用 are live.
- Latest TASK-169 validation: assignment and identity gates passed, `git diff --check` passed, production compose config rendered, frontend build passed, backend `KnowledgeBaseLifecycleIntegrationTest` passed against local `agentcici_test`, real local backend/frontend Playwright desktop validation of `/admin/data-quality` passed, scan `POST /data-quality/knowledge-bases/{kbId}/runs` returned `200`, browser console had 0 errors/warnings, no horizontal overflow at 1440px, and screenshot is `output/playwright/task169-data-quality-desktop.png`.
- Production release `2.2.1` was built and deployed on 2026-07-07: backend/frontend ACR images and Git tag were pushed, ECS backup is `/opt/cici/backups/20260707-141611-before-2.2.1-task169-data-quality`, six services are healthy, `/system/version` reports `version=2.2.1`, `imageTag=2.2.1`, `gitCommit=65364b4460c9`, and `x.agentcici.com` plus authenticated core APIs passed smoke.
- Front AI app follow-up: original `客户洞察` AI 应用 is preserved as a separate app; new `知微画像` AI 应用 now uses an independent `zhiwei-portrait` module and high-fidelity CDP demo structure with 对象列表、画像详情、标签库、AI 配置、运营看板. Desktop Playwright validation passed with 0 console errors and no horizontal overflow; screenshot is `output/playwright/zhiwei-portrait-ai-app.png`.
- TASK-171 is done and production released in `2.2.3`: Customer Interaction Workbench AI app with new-customer progression, existing-customer growth, CloudCC CRM integration/module linkage, supporting skill content, demo data, and CRM embedded entry.
- TASK-171 AgentCiCi-side implementation is functionally complete in local workspace: backend models/API/demo data, built-in `customer-interaction-workbench` skill binding, AI app entry, three-column workbench UI, new-customer progression, existing-customer growth, CRM landing suggestions, and AI assistant interactions are implemented.
- TASK-171 validation: backend `mvn -q -DskipTests compile` passed, frontend `npm run build` passed, compose config rendered, Playwright desktop and deep-link validation passed locally, CloudCC pagecomponent/html/customPage/menu/app binding passed, production release `2.2.3` is healthy, and authenticated production `/customer-workbench/accounts` smoke returned 12 demo customers.
- TASK-171 CloudCC side: OpenAPI and MetadataService are reachable; MetadataService capabilities returned 21 domains; standard `Task`, `Event`, and `Opportunity` queries returned real CRM rows; `customer-workbench` pagecomponent plus UMD bundle were safely published and updated through a temporary minimal project. Current online pagecomponent id is `6a4d348fe4b0a577cbba1ebf`, apiName is `custc_202607Hdhm60zo`, and default component URL targets `https://x.agentcici.com/app?aiApp=customer-workbench`. Do not publish this component directly from the repository root until the CLI dependency collection whitelist is fixed.
- TASK-171 CRM side also has an online HTML wrapper component id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`, accessPath `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`.
- TASK-171 CloudCC CRM-side visible entry is now verified through direct devconsole/setup APIs after the Go CLI `customPage` dispatcher and MSAPI apply path proved insufficient. Online customPage id is `6a4d3b831b8c6d0ec6dd22ef`, pageApi is `customer_interaction_workbench`; CRM page menu id is `acf2026C53BE54B9R1Iu`, tab label is `客户互动工作台`, lightning page is `customer_interaction_workbench#lightning`, and six profiles are authorized.
- TASK-171 CloudCC CRM menu visibility hotfix (2026-07-08): the tab originally existed only in Sales Cloud `selectedTabList`, so users in the default `CloudCC` app or other apps could not see it. Direct setup API update appended tab id `acf2026C53BE54B9R1Iu` to all 8 existing apps: `CloudCC`, `销售云`, `市场云`, `服务云`, `商务云`, `客服服务云`, `项目管理系统`, and `利润云`; verification returned `appCount=8`, `selectedCount=8`, `selectedInAllApps=true`.
- Production release notes: `2.2.2` shipped the main workbench at commit `5a4633dd0409`; `2.2.3` hotfixed production HTTPS Nginx routing for `/customer-workbench/*` at commit `f0ec47509bde`, with a follow-up source-only SSL vhost config commit `0271e52` synced to ECS and reloaded. Current `/system/version` reports `version=2.2.3`, `imageTag=2.2.3`, `gitCommit=f0ec47509bde`.
- TASK-170 remains assigned and active but is no longer the current working focus in this thread; it covers FEAT-080: sensitive data detection/redaction, sensitive lexicon maintenance, content moderation classification, prompt injection detection, input/output safety gateway, audit redaction, runtime integration, and `/admin/security-rules`.
- FEAT-067 remains the source for existing enterprise KB readiness capabilities: parser/PDF, ACL, eval, connector skeleton, drift audit, embedding metadata, Qdrant smoke, and `/admin/kb` desktop validation.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-171.md` - current customer interaction workbench task state.
- `.claw/assignments/TASK-171.yaml` - current authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - customer interaction workbench feature spec.
- `.claw/tasks/TASK-170.md` - active security rules platform task state.
- `.claw/assignments/TASK-170.yaml` - active security rules platform write scope.
- `docs/specs/FEAT-080-security-rules-platform.md` - security rules platform feature spec.
- `.claw/tasks/TASK-169.md` - completed data cleaning and annotation task state.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - existing KB platform readiness source.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - latest CloudCC customPage/menu automation findings for TASK-171.
