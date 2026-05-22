---
kind: task-status
version: 1
task_id: TASK-121
title: Database rename to agentcici
status: done
assignee: MANAGER-001
owner_role: project-manager
branch: codex/TASK-121-db-rename-agentcici
spec_path: docs/specs/FEAT-043-database-rename-agentcici.md
assignment_path: .claw/assignments/TASK-121.yaml
updated_at: 2026-05-21T02:14:53Z
updated_by: ai
---

# TASK-121 - Database rename to agentcici

## Scope

Rename the project's default PostgreSQL database from `cici_assistant` to `agentcici`, including runtime config, test config, deploy defaults, helper scripts, and the live local database.

## Plan

1. Update task/spec/state files for the rename scope.
2. Replace default database names in runtime/test/deploy config and helper scripts.
3. Rename the local PostgreSQL database and align the test database default.
4. Run targeted verification and record follow-up notes.

## Coordination

- Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-121-db-rename-agentcici`.
- Keep existing dirty worktree changes intact; do not revert unrelated TASK-120 work.
- Do not rename unrelated `cici_*` technical identifiers outside direct database-name usage.

## Progress

- 2026-05-21T02:11:08Z: Created TASK-121 assignment and status slice for FEAT-043.
- 2026-05-21T02:14:53Z: Updated local/runtime/test/deploy defaults from `cici_assistant` to `agentcici`, renamed local PostgreSQL databases to `agentcici` / `agentcici_test`, and restarted backend successfully against the renamed main database.

## Verification

- `identity`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-121 --branch codex/TASK-121-db-rename-agentcici --git-username OwenZheng-Cloud --files docker-compose.yml backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml backend/src/test/resources/application.yml deploy/acr.env.example deploy/docker-compose.acr.yml scripts/run-full-demo.sh docs/production-release-runbook.md docs/specs/FEAT-043-database-rename-agentcici.md .claw/decisions.md .claw/devops.md .claw/test-report.md .claw/current-status.md .claw/task-board.md .claw/tasks/TASK-121.md --no-cache --json` -> allowed.
- `db-rename`: stopped local backend screen, terminated active PostgreSQL sessions on `cici_assistant` / `cici_assistant_test`, then renamed databases to `agentcici` / `agentcici_test` -> success.
- `db-list`: `docker exec cici-postgres psql -U cici -d postgres -Atc "select datname from pg_database where datname in ('cici_assistant','agentcici','cici_assistant_test','agentcici_test') order by datname;"` -> `agentcici`, `agentcici_test`.
- `backend-health`: restarted local backend with `mvn ... spring-boot:run -Dspring-boot.run.profiles=local`; `GET http://127.0.0.1:8080/actuator/health` -> success, `{"status":"UP"}`.
- `backend-log`: `/tmp/cici-backend.log` shows `Database: jdbc:postgresql://localhost:5432/agentcici (PostgreSQL 16.13)` and `Started CiciAssistantApplication`.
- `db-ready`: `docker exec cici-postgres pg_isready -U cici -d agentcici` -> accepting connections.
- `db-current`: `docker exec cici-postgres psql -U cici -d agentcici -Atc "select current_database(), current_user;"` -> `agentcici|cici`.
- `search`: `rg -n --hidden -S "cici_assistant" docker-compose.yml backend/src/main/resources backend/src/test/resources deploy/acr.env.example deploy/docker-compose.acr.yml scripts/run-full-demo.sh docs/production-release-runbook.md .claw/decisions.md` -> no matches.
- `diff`: `git diff --check -- docker-compose.yml backend/src/main/resources/application-local.yml backend/src/main/resources/application.yml backend/src/test/resources/application.yml deploy/acr.env.example deploy/docker-compose.acr.yml scripts/run-full-demo.sh docs/production-release-runbook.md docs/specs/FEAT-043-database-rename-agentcici.md .claw/assignments/TASK-121.yaml .claw/tasks/TASK-121.md .claw/task-board.md .claw/current-status.md .claw/decisions.md` -> success.
