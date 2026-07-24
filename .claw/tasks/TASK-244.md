---
kind: task-status
task_id: TASK-244
status: review
updated_at: 2026-07-24T12:11:02Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-244.yaml
spec_path: docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md
---

# TASK-244 - OIDC 统一入口 state 修复

## Current State

- Status: `review`
- Next action: 审核并在获得单独生产发布授权后，按 Runbook 发布和执行真实 SSO 回调 smoke。
- Blocked: none
- Spec: `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- Assignment: `.claw/assignments/TASK-244.yaml`

## Progress

- 已只读确认根因：主站 host 发起时写入 host-only state Cookie，而 Keycloak callback 固定至 `x.agentcici.com`，Cookie 不会跨 host 发送。
- 已实现入口规范化：非 callback host 只重定向到 `redirect-uri` 的源站；规范 host 才创建 state Cookie 与 Redis transaction。

## Changed Files

- `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/api/AuthController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/service/KeycloakOidcLoginService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/auth/KeycloakOidcLoginServiceTest.java`
- `.claw/tasks/TASK-244.md`
- `.claw/assignments/TASK-244.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/issue-list.md`

## Verification

- Status: `passed`
- Evidence: `mvn -q -Dmaven.repo.local=.m2 -Dtest=KeycloakOidcLoginServiceTest test` 通过（3/3），覆盖主站跳转、规范 host、相似/畸形 host 拒绝和不匹配 state fail closed；`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。

## Handoff

- 保持 state Cookie host-only；通过规范入口消除跨 host 回调，不能用父域 Cookie 作为快捷修复。生产需要从主站入口完成一次 Keycloak 登录并确认回调带 `oidc_ticket` 后进入应用。
