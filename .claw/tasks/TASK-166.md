---
kind: task-status
task_id: TASK-166
status: done
updated_at: 2026-07-03T08:38:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-166.yaml
spec_path: docs/specs/FEAT-076-product-kb-trigger-and-pseudo-tool-guard.md
---

# TASK-166 - 产品功能类知识库触发与伪工具标签防护

## Scope

- 修复 `CloudCC 产品都有什么功能` 未触发知识库检索的问题。
- 禁止模型把 `<search_knowledge ... />` 这类伪工具标签直接输出给用户。
- 保留 2.1.9 已修复的部署类检索、闲聊跳过、普通业务工具查询跳过行为。

## Plan

- 建立 FEAT-076 规格和 TASK-166 授权。
- 先补 RED 测试覆盖产品功能/公司介绍类知识问法和伪工具标签 prompt 防护。
- 最小修改检索触发词表与工具边界 prompt。
- 跑 focused 后端测试、编译、静态检查。
- 合并、推送、按生产发布 runbook 发布新版本。

## Verification

- `dev-login.py` for `MANAGER-001` without task scope before assignment creation -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-166` covering orchestrator, focused test, task, and spec files -> allowed.
- `check-assignment.py` for `TASK-166` intended implementation files -> allowed.
- Production read-only trace check -> failing run `3405538b-7215-42ca-ade9-1315f45c0aab` had `rag_context_count=0`, `knowledge_base_names_json=[]`, `tool_call_count=0`, and summary literal `<search_knowledge ... />`.
- RED: `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` failed as expected on:
  - `shouldRetrieveKnowledgeForProductFeatureAndCompanyQuestionsWithDefaultKb`
  - `shouldGuardAgainstPseudoKnowledgeSearchXmlInToolBoundaryPrompt`
- GREEN: `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` -> success, 28 tests passed.
- `mvn -DskipTests compile` in `backend/` -> success.
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task166.yml` -> success.
- `git diff --check` -> success.
- `./scripts/release-acr.sh --dry-run` -> success, resolved production version `2.1.10`.
- `./scripts/release-acr.sh --version 2.1.10` -> success, backend/frontend images and Git tag `2.1.10` pushed.
- Production backup -> `/opt/cici/backups/20260703-083603-before-2.1.10-product-kb-trigger`.
- Production deploy -> success, `/opt/cici/deploy/acr.env` updated to `CICI_IMAGE_TAG=2.1.10` and `CICI_APP_VERSION=2.1.10`.
- Production health -> six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.10`, `imageTag=2.1.10`, `gitCommit=b635a8fb11fd`; frontend `nginx -t` passed; recent backend error scan empty.
- Public smoke -> `https://x.agentcici.com/` returned `200`; unauthenticated `/auth/me` returned expected `401` after backend warmup.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `docs/specs/FEAT-076-product-kb-trigger-and-pseudo-tool-guard.md`
- `.claw/tasks/TASK-166.md`
- `.claw/assignments/TASK-166.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`

## Handoff

- Branch: `codex/TASK-166-product-kb-trigger-guard`.
- Verified production root cause: the failing run had `rag_context_count=0`, `knowledge_base_names_json=[]`, `tool_call_count=0`, and final answer was the literal pseudo tool tag.
- Fix: default-KB knowledge intent now covers product/function/capability/module/company-introduction questions, and the tool boundary prompt forbids literal `search_knowledge` / XML pseudo tool tags.
- 已合并并发布生产版本 `2.1.10`。用户可用同一句 `CloudCC 产品都有什么功能` 复测，链路追踪应进入 `知识库检索` / `rag_done`，不再输出 `<search_knowledge ... />`。
