---
kind: task-status
task_id: TASK-244
status: ready
updated_at: 2026-07-24T12:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-244.yaml
spec_path: docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md
---

# TASK-244 - OIDC 统一入口 state 修复

## Current State

- Status: `ready`
- Next action: 通过任务级身份门禁后，先补充规范入口与 state 比较的定向回归，再实现入口重定向。
- Blocked: none
- Spec: `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- Assignment: `.claw/assignments/TASK-244.yaml`

## Progress

- 已只读确认根因：主站 host 发起时写入 host-only state Cookie，而 Keycloak callback 固定至 `x.agentcici.com`，Cookie 不会跨 host 发送。

## Changed Files

- `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- `.claw/tasks/TASK-244.md`
- `.claw/assignments/TASK-244.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/issue-list.md`

## Verification

- Status: `not_run`
- Evidence: production anonymous start response at both hostnames has a host-only `CICI_OIDC_STATE` Cookie; its Keycloak `redirect_uri` is always `https://x.agentcici.com/auth/oidc/callback`.

## Handoff

- 保持 state Cookie host-only；通过规范入口消除跨 host 回调，不能用父域 Cookie 作为快捷修复。
