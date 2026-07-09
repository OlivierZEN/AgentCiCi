---
kind: current-status
version: 4
updated_at: 2026-07-09T15:24:00+08:00
updated_by: MANAGER-001
phase: release
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 CRM clean-embed hotfix 已完成 2.2.7 生产发布、CloudCC pagecomponent V8 发布和真实 CRM 嵌入页验证；后续仅需继续修复 cc-customization-expert-msapi 的 bind pagecomponent 写入失败。"
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

- Current branch: `main`; production is running release `2.2.7` from Git commit `78fa13dd1185`.
- TASK-171 is production-ready: Customer Interaction Workbench AI app, new-customer progression, existing-customer growth, CloudCC CRM embedded entry, and CloudCC/AgentCiCi SSO handoff are live.
- Latest TASK-171 release: `2.2.7` adds the CRM clean-embed route so CloudCC CRM embeds only the customer interaction workbench body, not the full AgentCiCi platform shell.
- Production release `2.2.7` passed: ACR backend/frontend images and Git tag were pushed; ECS backup is `/opt/cici/backups/20260709-151814-before-2.2.7-task171-clean-embed`; six services are healthy; `/system/version` reports `version=2.2.7`, `imageTag=2.2.7`, `gitCommit=78fa13dd1185`; `https://x.agentcici.com/` and `/app?aiApp=customer-workbench&embed=crm` return HTTP 200.
- CloudCC CRM real embedded SSO verification passed on 2026-07-09 using the supplied CRM web account: CRM loads `component-customer-workbench-V7.0.js`; iframe URL includes a one-time `ssoTicket`; `/auth/cloudcc-sso/ticket`, `/auth/cloudcc-sso/consume`, `/auth/me`, `/customer-workbench/accounts`, and customer detail requests returned HTTP 200; screenshot is `output/playwright/task171-cloudcc-sso-final.png`.
- User reported the V7 CRM embedded page was functionally visible but visually wrong because it embedded the full AgentCiCi platform shell. The fix adds `/app?aiApp=customer-workbench&embed=crm`, renders only `CustomerWorkbenchApp`, removes the pagecomponent outer header, and points CloudCC SSO `targetPath` to the clean embed route.
- CloudCC pagecomponent V8 was published through `cc-customization-expert-msapi` as id `6a4f4be8e4b0a577cbba1f70`, apiName `custc_202607F3INXE0S`; real CRM runtime loaded `component-customer-workbench-V8.0.js` and iframe `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm&ssoTicket=...`.
- Real CRM iframe verification passed: inside the AgentCiCi iframe `hasRail=false`, `hasAiApps=false`, `hasEmbedded=true`, and the visible content starts from the customer queue/workbench body. Screenshot: `output/playwright/task171-cloudcc-clean-embed-v8.png`.
- CloudCC high-code operations in the latest SSO closure used `cc-customization-expert-msapi`: `package pagecomponent --dry-run`, `publish pagecomponent`, `verify injectionPage`, and readback. The process did not bypass the skill after `bind pagecomponent` and `update customPage` failed.
- Remaining CloudCC skill gap: `cloudcc bind pagecomponent . customer_interaction_workbench 6a4f4be8e4b0a577cbba1f70 --embedded true --workspace-url ...` still fails with `系统发生异常`. `verify injectionPage --expected-component-id 6a4f4be8e4b0a577cbba1f70 --stale-policy warning` correctly reports warning issues for stale `actualComponentIds=["6a4db950e4b0a577cbba1eca"]`; runtime still loads V8 by component name. Track this in `ISSUE-2026-07-09-cloudcc-custompage-bind-skill-gap`.
- Production release history for TASK-171: `2.2.2` main workbench, `2.2.3` HTTPS proxy, `2.2.4` CloudCC SSO handoff, `2.2.5` CloudCC object-list planning hotfix, `2.2.6` org-scoped workbench demo seed fix, `2.2.7` CRM clean embed.
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
