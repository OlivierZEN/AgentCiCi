---
kind: task-status
task_id: TASK-125
assignee: MANAGER-001
owner_role: project-manager
status: done
branch: codex/TASK-124-feat-046-platform-tenant-provisioning
pr_url: n/a
spec_path: docs/specs/FEAT-043-database-rename-agentcici.md
assignment_path: .claw/assignments/TASK-125.yaml
updated_at: 2026-05-21T09:18:03Z
updated_by: MANAGER-001
---

# TASK-125 Restore database rename to agentcici

## Scope

Restore the database-name migration in project defaults so runtime/test/deploy config again target `agentcici` / `agentcici_test`, matching the current local PostgreSQL databases.

## Preflight

Before editing, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Progress

- 2026-05-21T09:16:57Z: Reopened FEAT-043 as TASK-125 after the earlier config changes were overwritten while the local databases remained renamed.
- 2026-05-21T09:18:03Z: Restored the database-name defaults in local/runtime/test/deploy config so they again target `agentcici` / `agentcici_test`.

## Verification

- `identity`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-125 --branch codex/TASK-124-feat-046-platform-tenant-provisioning --git-username OwenZheng-Cloud --files docker-compose.yml backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml backend/src/test/resources/application.yml deploy/acr.env.example deploy/docker-compose.acr.yml scripts/run-full-demo.sh docs/release-local-to-cici-cloudcc-cn.md docs/specs/FEAT-043-database-rename-agentcici.md .claw/assignments/TASK-125.yaml .claw/tasks/TASK-125.md .claw/task-board.md .claw/current-status.md .claw/decisions.md .claw/test-report.md .claw/team-status.md --no-cache --json` -> allowed.
- `search`: `rg -n --hidden -S "cici_assistant" docker-compose.yml backend/src/main/resources backend/src/test/resources deploy/acr.env.example deploy/docker-compose.acr.yml scripts/run-full-demo.sh docs/release-local-to-cici-cloudcc-cn.md .claw/decisions.md` -> no matches.
- `db-list`: `docker exec cici-postgres psql -U cici -d postgres -Atc "select datname from pg_database where datname in ('cici_assistant','agentcici','cici_assistant_test','agentcici_test') order by datname;"` -> `agentcici`, `agentcici_test`.
- `backend-health`: `GET http://127.0.0.1:8080/actuator/health` -> success, `{"status":"UP"}`.
- `db-ready`: `docker exec cici-postgres pg_isready -U cici -d agentcici` -> accepting connections.
- `db-activity`: `docker exec cici-postgres psql -U cici -d postgres -Atc "select datname, application_name, state, count(*) from pg_stat_activity where datname in ('agentcici','agentcici_test') group by datname, application_name, state order by datname, application_name, state;"` -> `agentcici|PostgreSQL JDBC Driver|idle|10`.
- `diff`: targeted `git diff --check` for TASK-125 files -> success.
