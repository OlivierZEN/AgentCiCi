---
kind: task-status
task_id: TASK-165
status: review
updated_at: 2026-07-03T00:14:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-165.yaml
spec_path: docs/specs/FEAT-075-agent-kb-runtime-retrieval.md
---

# TASK-165 - 智能体绑定知识库运行时检索触发修复

## Scope

- 修复客户成功智能体已绑定知识库但业务知识类提问未触发检索的问题。
- 扩展默认知识库检索意图判定，覆盖部署、私有云、注意事项、最佳实践等业务知识问法。
- 保留闲聊不检索、普通业务工具查询不检索的现有保护。

## Plan

- 建立 FEAT-075 规格和 TASK-165 授权。
- 先补失败用例覆盖 `CloudCC私有云部署注意事项有哪些`。
- 最小修改 `shouldUseKnowledgeRetrieval(...)` 知识意图词表。
- 运行 focused 后端测试、编译、静态检查。
- 合并并按生产发布 runbook 发布新版本。

## Verification

- `dev-login.py` for `MANAGER-001` without task scope before assignment creation -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-165` covering orchestrator, focused test, task, and spec files -> allowed.
- `check-assignment.py` for `TASK-165` intended implementation files -> allowed.
- Production read-only metadata check -> customer success Agents have enabled ACTIVE KB bindings:
  - `demo-org / agent-348465 / 客户成功 -> kb 6 CloudCC知识库`
  - `org5nszpgj99jaysxv6y / after-sales-agent / 客户成功 -> kb 8 CloudCC客户成功知识库`
- RED: `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` failed as expected: `shouldRetrieveKnowledgeForDeploymentQuestionWithDefaultKb` returned `false`.
- GREEN: `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest -DskipITs` in `backend/` -> success, 26 tests passed.
- `mvn -DskipTests compile` in `backend/` -> success.
- `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task165.yml` -> success.
- `git diff --check` -> success.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `docs/specs/FEAT-075-agent-kb-runtime-retrieval.md`
- `.claw/tasks/TASK-165.md`
- `.claw/assignments/TASK-165.yaml`
- `.claw/task-board.md`
- `.claw/test-report.md`

## Handoff

- Branch: `codex/TASK-165-agent-kb-runtime-retrieval`.
- Root cause: RAG 有效知识库已解析，但未显式选择知识库时的触发词表漏掉部署/注意事项类知识问法。
- Fix: 默认知识库检索意图词表增加 `部署`、`私有云`、`公有云`、`注意事项`、`最佳实践`、`解决方案`。
- Next: merge to `main`, push remote, dry-run release, publish production version.
