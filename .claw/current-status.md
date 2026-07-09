---
kind: current-status
version: 4
updated_at: 2026-07-09T21:12:00+08:00
updated_by: MANAGER-001
phase: implementation-validation
active_task: "TASK-171 客户互动工作台生产就绪"
next_action: "TASK-171 客户互动工作台已按最新结构把新客户推进和老客户经营调整为顶部互斥主模式；下一步如需上线，需要按生产发布 runbook 发布新版前端并做真实 CloudCC CRM 嵌入页回归。"
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
- Latest local change in this thread adjusts the workbench structure so `新客户推进` and `老客户经营` are top-level mutually exclusive modes with separate customer queues, not tabs inside one customer detail.
- New-customer mode now shows `新客户推进队列`, `推进概览 / 互动时间线 / 推进信号 / CRM 落地建议 / 下一步行动`, and the bottom `推进关键项` panel; existing-customer mode shows `老客户经营队列`, `经营概览 / 互动时间线 / 服务问题 / 价值兑现 / 续约增购 / 关系地图`, and the bottom `服务与关系预警` panel.
- Local Playwright validation at 1496x1064 passed with screenshots `output/playwright/task171-workbench-mode-final-new.png` and `output/playwright/task171-workbench-mode-final-existing.png`; `git diff --check` and `npm run build` in `frontend/` passed.
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
