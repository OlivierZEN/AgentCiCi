---
kind: current-status
version: 4
updated_at: 2026-07-07T14:18:00+08:00
updated_by: MANAGER-001
phase: implementation
active_task: "TASK-170 安全规则平台与输入输出安全网关"
next_action: "Resume TASK-170: validate assignment, run task-scoped dev-login/check-assignment, then implement V71 security rules platform and runtime gateway."
read_next:
  goals: false
  decisions: false
  issue_list: false
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
- TASK-170 is assigned and next; it covers FEAT-080: sensitive data detection/redaction, sensitive lexicon maintenance, content moderation classification, prompt injection detection, input/output safety gateway, audit redaction, runtime integration, and `/admin/security-rules`.
- FEAT-067 remains the source for existing enterprise KB readiness capabilities: parser/PDF, ACL, eval, connector skeleton, drift audit, embedding metadata, Qdrant smoke, and `/admin/kb` desktop validation.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-170.md` - current security rules platform task state.
- `.claw/assignments/TASK-170.yaml` - current authorized write scope.
- `docs/specs/FEAT-080-security-rules-platform.md` - current security rules platform feature spec.
- `.claw/tasks/TASK-169.md` - completed data cleaning and annotation task state.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - existing KB platform readiness source.
- `.claw/test-report.md` - latest verified commands.
