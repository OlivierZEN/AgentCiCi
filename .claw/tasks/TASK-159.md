---
kind: task-status
task_id: TASK-159
status: done
updated_at: 2026-06-26T04:50:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-159.yaml
spec_path: docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md
---

# TASK-159 - Chat session state tenant primary key hotfix

## Scope

- 修复线上 `2.1.3` 工作台对话报错：`chat_session_state_pkey` 在跨组织复用 `workbench:cici-system` session id 时冲突。
- 将 `chat_session_state` 主键从单列 `session_id` 调整为复合主键 `(session_id, org_id)`。
- 补充后端测试和发布验证记录。

## Production Finding

- 2026-06-26 12:15 CST 只读查看线上容器：
  - `cici-backend` 正运行 `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.3`。
  - `/system/version` 返回 `version=2.1.3`, `gitCommit=916ee5f48d7a`。
  - `docker logs --since 48h cici-backend` 中 `chat_session_state_pkey` 出现 9 次。
  - 关键日志时间：`2026-06-26T04:12:12.786Z`，对应北京时间 `2026-06-26 12:12:12`。
  - 栈：`ChatSessionStateService.mergeUserTurn(ChatSessionStateService.java:140)` -> `ChatOrchestratorService.chatStreamBlockingLocked(ChatOrchestratorService.java:440)`。
  - 冲突键：`session_id=workbench:cici-system`。
  - 线上已有行：`workbench:cici-system|demo-org|cici-system|会话进行中|0|2026-05-25 07:40:59.304812`。

## Plan

- 新增 FEAT-069 热修规格和 TASK-159 授权。
- 追加 V69 Flyway migration 调整主键。
- 将 `ChatSessionStateEntity` 改成复合主键实体。
- 增加 focused integration test 覆盖跨组织同 session id 保存状态。
- 本地跑 focused backend test、backend compile 和 `git diff --check`。

## Verification

- `dev-login.py` for `MANAGER-001` / `TASK-159` covering backend session-state entity/repository, V69 migration, focused test, spec, and state files -> **allowed**.
- 线上只读日志确认：
  - `cici-backend:2.1.3` 正常运行，`/system/version` 返回 `version=2.1.3`, `gitCommit=916ee5f48d7a`。
  - `docker logs --since 48h cici-backend` 中 `chat_session_state_pkey` 出现 9 次。
  - 生产栈指向 `ChatSessionStateService.mergeUserTurn` 与 `ChatOrchestratorService.chatStreamBlockingLocked`。
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatSessionStateServiceIntegrationTest test` in `backend/` -> **success** after adding repository transaction coverage for derived delete cleanup.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatSessionStateServiceIntegrationTest,OrchestratorIntegrationTest,AgentRuntimeConcurrencyServiceTest test` in `backend/` -> **success**.
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` in `backend/` -> **success**.
- `git diff --check` -> **success**.
- `check-assignment.py` for TASK-159 changed files -> **allowed**.
- `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task159.yml` -> **success**.
- Merged `codex/TASK-159-chat-session-state-tenant-key-hotfix` into `main` and pushed `origin/main`; production release commit is `d40d53d0a228`.
- `./scripts/release-acr.sh --dry-run` -> **success**, next version `2.1.4`.
- `./scripts/release-acr.sh --version 2.1.4` -> **blocked by registry push** twice: backend build passed but ACR push stalled after `pushing layers ... done` and before manifest/tag completion; no script-created Git tag or ACR image was completed.
- Production fallback release:
  - Backup created at `/opt/cici/backups/20260626-124138-before-2.1.4`.
  - Backend jar, frontend dist, Dockerfiles, and Nginx config copied to `/opt/cici/release-build/2.1.4`.
  - ECS-local Docker build created `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.4` image id `0131f3dcd944` and `cici-frontend:2.1.4` image id `a410d430f10b`.
  - Infra images were locally tagged as `2.1.4`, `/opt/cici/deploy/acr.env` was updated to `CICI_IMAGE_TAG=2.1.4` and `CICI_APP_VERSION=2.1.4`, and compose `up -d` completed.
  - Git tag `2.1.4` was created and pushed to origin.
- Production verification:
  - Six compose services healthy; `/actuator/health` -> `UP`; `/system/version` -> `version=2.1.4`, `imageTag=2.1.4`, `gitCommit=d40d53d0a228`.
  - Flyway latest row: `69|chat session state tenant primary key|true`.
  - `chat_session_state` primary key columns: `session_id`, `org_id`.
  - Transaction rollback proof inserted `workbench:cici-system|task159-verify-org` and rolled back successfully.
  - Real `/ai/chat` smoke using `sessionId=workbench:cici-system` returned HTTP 200 with `success=true`.
  - `docker logs --since 5m cici-backend | grep chat_session_state_pkey` -> `0`.
  - `https://x.agentcici.com/` -> `200`; `http://x.agentcici.com/` -> `301` to HTTPS; frontend `nginx -t` passed; recent backend error scan empty.

## Changed Files

- `docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md`
- `.claw/tasks/TASK-159.md`
- `.claw/assignments/TASK-159.yaml`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/domain/ChatSessionStateEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/domain/ChatSessionStateId.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/domain/ChatSessionStateRepository.java`
- `backend/src/main/resources/db/migration/V69__chat_session_state_tenant_primary_key.sql`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/ChatSessionStateServiceIntegrationTest.java`

## Handoff

- Branch: `codex/TASK-159-chat-session-state-tenant-key-hotfix`; merged to `main`.
- Production hotfix is live in `2.1.4`.
- Follow-up: restore normal ACR push durability for the `2.1.4` image set; current production images are present on the ECS host under the canonical image names/tags but were not pushed to ACR because registry push stalled.
