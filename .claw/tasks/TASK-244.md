---
kind: task-status
task_id: TASK-244
status: done
updated_at: 2026-07-24T12:21:15Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-244.yaml
spec_path: docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md
---

# TASK-244 - OIDC 统一入口 state 修复

## Current State

- Status: `done`
- Next action: 等待用户完成一次真实 Keycloak 登录；若仍失败，以当前 `2.8.13` 后端日志中的 callback Cookie/Host 诊断为准。
- Blocked: none
- Spec: `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- Assignment: `.claw/assignments/TASK-244.yaml`

## Progress

- 已只读确认根因：主站 host 发起时写入 host-only state Cookie，而 Keycloak callback 固定至 `x.agentcici.com`，Cookie 不会跨 host 发送。
- 已实现入口规范化：非 callback host 只重定向到 `redirect-uri` 的源站；规范 host 才创建 state Cookie 与 Redis transaction。
- 已发布 `2.8.13 / 877337078ea8`；生产备份、镜像、容器健康、版本、Nginx、公网入口重定向与规范 host state Cookie smoke 均已通过。

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

- 保持 state Cookie host-only；通过规范入口消除跨 host 回调，不能用父域 Cookie 作为快捷修复。最终验收为用户从主站入口完成 Keycloak 登录后确认回调带 `oidc_ticket` 并进入应用。
