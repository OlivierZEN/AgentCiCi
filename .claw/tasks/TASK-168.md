---
kind: task-status
task_id: TASK-168
status: done
updated_at: 2026-07-03T15:08:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-168.yaml
spec_path: docs/specs/FEAT-078-asr-websocket-auth-hotfix.md
---

# TASK-168 - ASR WebSocket 鉴权与线上语音入口修复

## Scope

- 修复线上 AI 听记和对话窗口麦克风无法启动或无法识别语音的问题。
- 保持普通业务 API 的认证要求不变。
- 保持 `/ws/asr` 由 WebSocket handler 自行校验 query token 的安全边界。

## Plan

- 基于线上日志和真实 WebSocket 请求确认 `/ws/asr?token=...` 被租户过滤器提前返回 401。
- 先补回归测试，覆盖 `/ws/asr` 不应由 `TenantContextFilter` 以缺少 Authorization header 为由拦截。
- 最小化修改租户过滤器公共路径判定，仅放行 ASR WebSocket 握手路径。
- 运行 focused 后端测试、编译和发布前检查。

## Verification

- `dev-login.py` for `MANAGER-001` before TASK-168 assignment creation -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-168` covering tenant filter, focused test, spec, task, and state files -> allowed.
- `check-assignment.py` for TASK-168 intended implementation files -> allowed.
- RED: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=TenantContextFilterTest test` in `backend/` -> failed as expected because `/ws/asr` returned `401`.
- GREEN: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=TenantContextFilterTest test` in `backend/` -> success, 2 tests passed.
- RBAC regression: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=TenantContextFilterTest,RbacProductionReadinessIntegrationTest test` in `backend/` -> success; surefire reports show 7 tests passed with 0 failures and 0 errors.
- `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/` -> success.
- `git diff --check` -> success.
- `git switch main && git merge --ff-only codex/TASK-168-asr-websocket-auth-hotfix && git push origin main` -> success; `main` now includes commit `caf4baf90575`.
- `./scripts/release-acr.sh --dry-run` -> success, resolved production version `2.1.12`.
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task168-release.yml` -> success.
- `./scripts/release-acr.sh --version 2.1.12` -> success; backend/frontend images and Git tag `2.1.12` pushed.
- Production backup -> `/opt/cici/backups/20260703-150359-before-2.1.12-asr-websocket-auth`. Initial backup attempt used stale database name `cici` and failed before deploy; successful backup used production database `agentcici`.
- Production deploy -> success, `/opt/cici/deploy/acr.env` updated to `CICI_IMAGE_TAG=2.1.12` and `CICI_APP_VERSION=2.1.12`.
- Production health -> six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.12`, `imageTag=2.1.12`, `gitCommit=caf4baf90575`; frontend `nginx -t` passed; recent backend ASR/WebSocket/error scan empty.
- Public smoke -> `https://x.agentcici.com/` returned `200`; unauthenticated `/auth/me` returned expected `401`.
- Live ASR smoke -> real login token opened `wss://x.agentcici.com/ws/asr?token=...`, received `status=connected`, sent `start`, and received `status=started`.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/tenant/TenantContextFilter.java`
- `backend/src/test/java/com/codehouse/ciciassistant/tenant/TenantContextFilterTest.java`
- `docs/specs/FEAT-078-asr-websocket-auth-hotfix.md`
- `.claw/tasks/TASK-168.md`
- `.claw/assignments/TASK-168.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- Branch: `codex/TASK-168-asr-websocket-auth-hotfix`.
- 线上复现时不要在记录中暴露 JWT token；只记录路径、状态码和行为。
- 已合并并发布生产版本 `2.1.12`。用户需要在浏览器中复测 AI 听记和对话窗口麦克风权限/语音识别。
