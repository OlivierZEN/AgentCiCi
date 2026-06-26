---
kind: task-status
task_id: TASK-161
status: done
updated_at: 2026-06-26T06:02:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-161.yaml
spec_path: docs/specs/FEAT-071-mail-body-and-voice-input-fix.md
---

# TASK-161 - 对话邮件正文展示与语音输入识别修复

## Scope

- 修复邮件工具搜索后未按用户要求展示正文的问题。
- 修复实时语音输入对 ASR 文本事件解析过窄、停录后可能丢 final 片段的问题。
- 补充 focused 后端/前端测试与验证记录。

## Plan

- 建立 FEAT-071 规格和 TASK-161 授权。
- 调整 `ChatOrchestratorService` 邮件正文意图下的工具规划收口逻辑。
- 调整 `useAsrVoiceInput` 的 ASR 文本提取和停录收尾。
- 增加 focused tests。
- 运行任务级门禁、focused backend/frontend tests、build/static checks。

## Verification

- `dev-login.py` for `MANAGER-001` / `TASK-161` covering backend orchestrator, backend test, frontend ASR hook/test, spec, and state files -> **allowed**.
- `check-assignment.py` for TASK-161 changed files -> **allowed**.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` in `backend/` -> **success**.
- `npm run test -- useAsrVoiceInput.test.ts` in `frontend/` -> **success**.
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` in `backend/` -> **success**.
- `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
- `git diff --check` -> **success**.
- Merged `codex/TASK-161-mail-voice-dialog-fix` into `main` and pushed `origin/main` at `947e47ddbe5a` -> **success**.
- `./scripts/release-acr.sh --dry-run` -> **success**, next version `2.1.5`.
- `./scripts/release-acr.sh --version 2.1.5` -> **success**; backend/frontend images and Git tag `2.1.5` were pushed.
- Production backup created at `/opt/cici/backups/20260626-135931-before-2.1.5` -> **success**.
- Production deploy updated `/opt/cici/deploy/acr.env` to `CICI_IMAGE_TAG=2.1.5` and `CICI_APP_VERSION=2.1.5`, pulled backend/frontend images, tagged infra images locally as `2.1.5`, and restarted compose -> **success**.
- Production verification passed: six services healthy, backend `/actuator/health` `UP`, `/system/version` returns `version=2.1.5`, frontend Nginx config passes, `https://x.agentcici.com/` returns `200`, `http://x.agentcici.com/` redirects to HTTPS, `/auth/me` returns expected `401`, and recent backend error scan is empty.

## Changed Files

- `docs/specs/FEAT-071-mail-body-and-voice-input-fix.md`
- `.claw/tasks/TASK-161.md`
- `.claw/assignments/TASK-161.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `frontend/src/shared/useAsrVoiceInput.ts`
- `frontend/src/shared/useAsrVoiceInput.test.ts`

## Handoff

- Branch: `codex/TASK-161-mail-voice-dialog-fix`.
- Merged to `main`, pushed to origin, and released to production as `2.1.5`.
