---
kind: task-status
task_id: TASK-172
status: done
updated_at: 2026-07-09T23:39:05+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-172.yaml
spec_path: docs/specs/FEAT-082-demo-environment-real-data.md
---

# TASK-172 - 双环境真实演示数据建设

## Scope

- 为 AgentCiCi“智能体平台演示环境”和绑定的 CloudCC CRM 演示组织创建互通的真实模拟业务数据。
- CRM 侧复用标准对象承载真实记录。
- AgentCiCi 侧客户互动工作台聚合表引用同一批 CRM record id。
- 保留可重复执行、可审计、尽量幂等的数据建设流程。

## Initial Findings

- AgentCiCi 演示组织 ID 为 `org2sva14i4udjmi2t4s`。
- CloudCC CRM 演示组织 ID 为 `org0720f814430017229`。
- CloudCC 标准目录扫描确认 `Account`、`Contact`、`cloudcclead`、`Opportunity`、`Task`、`Event`、`contract`、`product` 等标准对象存在。
- 当前客户互动工作台数据仍来自 AgentCiCi 本地种子，不是实时 CRM 标准对象数据。

## Implementation Plan

- 建立 FEAT-082、TASK-172 和授权边界。
- 探测 CloudCC OpenAPI 创建/查询标准对象的字段契约。
- 编写并运行幂等数据建设脚本，先创建 CRM 标准对象真实记录，再刷新 AgentCiCi 工作台聚合数据。
- 用 AgentCiCi API 与 CloudCC 查询结果交叉验证客户数、CRM record id 和业务故事线。

## Verification

- `identity-gate`: skill-packaged `dev-login.py .claw --developer MANAGER-001 --task TASK-172 --branch main --files ... --json` -> allowed.
- `assignment-check`: skill-packaged `check-assignment.py .claw --developer MANAGER-001 --task TASK-172 --branch main --files ... --json` -> allowed.
- `cloudcc-standard-catalog`: `cloudcc scan msapi . standard-catalog` -> passed; CRM org has `Account`, `Contact`, `cloudcclead`, `Opportunity`, `Task`, `Event`, `contract`, and `product`.
- `cloudcc-create-probe`: OpenAPI `create Account` with a temporary probe row -> passed; probe row was deleted successfully.
- `script-static`: `python3 -m py_compile scripts/seed-demo-environment.py` and `git diff --check -- docs/specs/FEAT-082-demo-environment-real-data.md .claw/tasks/TASK-172.md .claw/assignments/TASK-172.yaml scripts/seed-demo-environment.py` -> passed.
- `dual-seed`: `python3 scripts/seed-demo-environment.py` -> passed. CloudCC records ready: accounts `10`, contacts `10`, leads `6`, opportunities `10`, tasks `10`, events `20`.
- `agentcici-backup`: production PostgreSQL backup created at `/opt/cici/backups/20260709-153648-before-task172-demo-data`.
- `agentcici-aggregate-refresh`: for org `org2sva14i4udjmi2t4s`, old workbench rows deleted (`24` recommendations, `36` events, `12` snapshots) and new rows inserted (`10` snapshots, `30` events, `20` recommendations).
- `cloudcc-readback`: OpenAPI `pageQuery Account` found `10` CRM accounts with `beizhu` starting `TASK-172-DEMO-V1`.
- `agentcici-api-readback`: production login `org2sva14i4udjmi2t4s / 13900009999` returned org name `智能体平台演示环境`; `/customer-workbench/accounts` returned `10` accounts, all with real CRM ids and no `demo-account` ids.
- `agentcici-binding-readback`: `13900009999` is now the only member in the demo org with CloudCC binding; the previous member binding was cleared to avoid SSO ambiguity.
- `workbench-detail-readback`: first account detail returned timeline count `3`, recommendation count `2`, and `crmConnection.ready=true`.
- `assistant-smoke`: `/customer-workbench/assistant` for `北京智造科技有限公司` returned the expected risk summary using the CRM-backed account id.

## Changed Files

- `docs/specs/FEAT-082-demo-environment-real-data.md`
- `.claw/tasks/TASK-172.md`
- `.claw/assignments/TASK-172.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `scripts/seed-demo-environment.py`

## Handoff

- Demo data is ready for customer presentation.
- Use `scripts/seed-demo-environment.py` to refresh the same batch. It reuses existing CRM records by name and replaces only the AgentCiCi workbench aggregate rows for org `org2sva14i4udjmi2t4s`.
- Do not rebind the same CloudCC username to multiple AgentCiCi members; SSO expects a single mapped member in the demo org.
