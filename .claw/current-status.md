---
kind: current-status
version: 4
updated_at: 2026-07-07T14:20:00+08:00
updated_by: MANAGER-001
phase: release
active_task: "TASK-169 独立数据清洗与智能标注平台能力"
next_action: "Complete TASK-169 main merge, push origin/main, then release the next production version with scripts/release-acr.sh."
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

- Current branch: `main`; production is running release `2.1.12` from Git commit `caf4baf90575`.
- User opened a goal to补齐 AgentCiCi 数据清洗与智能标注平台能力，并达到生产就绪状态.
- User explicitly chose the larger standalone platform direction: new `/admin/data-quality`, facing all data sources; KB and KB connectors are the first adapter.
- TASK-169 is implemented and locally validated for FEAT-079: data-source aggregation, quality scan, duplicate/invalid data detection, regex cleaning preview/apply, manual review queue, annotation suggestions, audited apply flow, and standalone admin UI.
- Latest TASK-169 validation: assignment and identity gates passed, `git diff --check` passed, production compose config rendered, frontend build passed, backend `KnowledgeBaseLifecycleIntegrationTest` passed against local `agentcici_test`, real local backend/frontend Playwright desktop validation of `/admin/data-quality` passed, scan `POST /data-quality/knowledge-bases/{kbId}/runs` returned `200`, browser console had 0 errors/warnings, no horizontal overflow at 1440px, and screenshot is `output/playwright/task169-data-quality-desktop.png`.
- Front AI app follow-up: original `客户洞察` AI 应用 is preserved as a separate app; new `知微画像` AI 应用 now uses an independent `zhiwei-portrait` module and high-fidelity CDP demo structure with 对象列表、画像详情、标签库、AI 配置、运营看板. Desktop Playwright validation passed with 0 console errors and no horizontal overflow; screenshot is `output/playwright/zhiwei-portrait-ai-app.png`.
- TASK-170 is already assigned on `origin/main` and covers FEAT-080: sensitive data detection/redaction, sensitive lexicon maintenance, content moderation classification, prompt injection detection, input/output safety gateway, audit redaction, runtime integration, and `/admin/security-rules`; resume it after TASK-169 release.
- FEAT-067 remains the source for existing enterprise KB readiness capabilities: parser/PDF, ACL, eval, connector skeleton, drift audit, embedding metadata, Qdrant smoke, and `/admin/kb` desktop validation.
- TASK-168 is done in production release `2.1.12`; user should still retest AI 听记 and chat microphone from the browser when convenient.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-169.md` - current data cleaning and annotation task state.
- `.claw/assignments/TASK-169.yaml` - current authorized write scope.
- `docs/specs/FEAT-079-kb-data-quality-annotation.md` - current standalone data-quality feature spec.
- `.claw/tasks/TASK-170.md` - next security rules platform task state.
- `.claw/assignments/TASK-170.yaml` - next authorized write scope.
- `docs/specs/FEAT-080-security-rules-platform.md` - next security rules platform feature spec.
- `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md` - existing KB platform readiness source.
- `.claw/test-report.md` - latest verified commands.
