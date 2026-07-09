---
kind: current-status
version: 4
updated_at: 2026-07-09T14:24:00+08:00
updated_by: MANAGER-001
phase: release
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 已完成 2.2.6 生产发布和真实 CloudCC CRM 嵌入页 SSO 验证；后续仅需把 cc-customization-expert-msapi 的 customPage bind/update 失败与 stale component id 校验缺口回灌到技能。"
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- Current branch: `main`; production is running release `2.2.6` from Git commit `3ed80e1873bf`.
- TASK-171 is production-ready: Customer Interaction Workbench AI app, new-customer progression, existing-customer growth, CloudCC CRM embedded entry, and CloudCC/AgentCiCi SSO handoff are live.
- Latest TASK-171 release: `2.2.6` fixed org-scoped customer-workbench demo seed IDs after CRM SSO exposed a duplicate `customer_workbench_snapshot.public_id` collision for org `org2sva14i4udjmi2t4s`.
- Production release `2.2.6` passed: ACR backend/frontend images and Git tag were pushed; ECS backup is `/opt/cici/backups/20260709-131149-before-2.2.6-task171-cloudcc-sso-seed`; six services are healthy; `/system/version` reports `version=2.2.6`, `imageTag=2.2.6`, `gitCommit=3ed80e1873bf`; `https://x.agentcici.com/` and `/app?aiApp=customer-workbench` return HTTP 200.
- CloudCC CRM real embedded SSO verification passed on 2026-07-09 using the supplied CRM web account: CRM loads `component-customer-workbench-V7.0.js`; iframe URL includes a one-time `ssoTicket`; `/auth/cloudcc-sso/ticket`, `/auth/cloudcc-sso/consume`, `/auth/me`, `/customer-workbench/accounts`, and customer detail requests returned HTTP 200; screenshot is `output/playwright/task171-cloudcc-sso-final.png`.
- CloudCC high-code operations in the latest SSO closure used `cc-customization-expert-msapi`: `package pagecomponent --dry-run`, `publish pagecomponent`, `verify injectionPage`, and readback. The process did not bypass the skill after `bind pagecomponent` and `update customPage` failed.
- Remaining CloudCC skill gap: `cloudcc bind pagecomponent . customer_interaction_workbench ...` and `cloudcc update customPage . customer_interaction_workbench @...` both fail with `系统发生异常`; `verify injectionPage` passes by component name while still reporting stale `actualComponentIds=["6a4db950e4b0a577cbba1eca"]` after V7 publish. Track this in `ISSUE-2026-07-09-cloudcc-custompage-bind-skill-gap`.
- Production release history for TASK-171: `2.2.2` main workbench, `2.2.3` HTTPS proxy, `2.2.4` CloudCC SSO handoff, `2.2.5` CloudCC object-list planning hotfix, `2.2.6` org-scoped workbench demo seed fix.
- TASK-170 remains assigned and active but is not the current working focus in this thread; it covers FEAT-080 security rules platform and runtime safety gateway.
- Production release source of truth remains `docs/production-release-runbook.md`; `scripts/release-acr.sh` owns numeric production versions and production-based beta test versions.

## Read Next

- `.claw/task-board.md` - compact index for live tasks.
- `.claw/tasks/TASK-171.md` - current customer interaction workbench task state.
- `.claw/assignments/TASK-171.yaml` - current authorized write scope.
- `docs/specs/FEAT-081-customer-interaction-workbench.md` - customer interaction workbench feature spec.
- `.claw/test-report.md` - latest verified commands.
- `.claw/issue-list.md` - CloudCC skill and operations findings.
- `.claw/devops.md` - latest production release evidence.
