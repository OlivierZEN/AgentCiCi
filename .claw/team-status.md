---
kind: team-status
version: 3
updated_at: 2026-05-19T06:19:55Z
updated_by: summarize-team-status
status: derived
---

# 团队状态汇总

`team-status.md` 是管理者视图，由 `scripts/summarize-team-status.py` 根据项目状态文件生成。它不是事实源。

## 汇总规则

事实源优先级：

1. `.claw/developers/*.yaml`
2. `.claw/assignments/*.yaml`
3. `.claw/tasks/*.md`
4. `.claw/task-board.md`
5. `.claw/integration-queue.md`

如果本文件与事实源冲突，修复事实源后重新生成本文件。

## 团队概览

| 指标 | 数量 |
|------|------|
| 开发者 | 5 |
| 活跃开发者 | 5 |
| 授权任务 | 6 |
| 已开始任务 | 2 |
| 等待 review | 0 |
| 已集成任务 | 2 |
| 阻塞任务 | 0 |

## 成员状态

### DEV-fengchu

- display_name: `凤雏`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `TASK-112`
- active_tasks: `TASK-112`
- latest_pr: `none`
- contribution_status: `claimed`
- validation_status: `not_run`
- integration_status: `not_ready`

### DEV-nezha

- display_name: `哪吒`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `TASK-114`
- active_tasks: `TASK-114`
- latest_pr: `none`
- contribution_status: `claimed`
- validation_status: `unknown`
- integration_status: `not_ready`

### DEV-wolong

- display_name: `卧龙`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `TASK-116`
- active_tasks: `TASK-116`
- latest_pr: `none`
- contribution_status: `claimed`
- validation_status: `unknown`
- integration_status: `not_ready`

### DEV-zhongda

- display_name: `仲达`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `TASK-115`
- active_tasks: `TASK-115`
- latest_pr: `none`
- contribution_status: `claimed`
- validation_status: `unknown`
- integration_status: `not_ready`

### MANAGER-001

- display_name: `Owen`
- role: `project-manager`
- identity_status: `active`
- assigned_tasks: `TASK-117, TASK-118`
- active_tasks: `none`
- latest_pr: `none`
- contribution_status: `merged`
- validation_status: `unknown`
- integration_status: `integrated`

## 任务状态

| Task | Title | Assignee | Board | Contribution | Validation | Integration | Branch | PR |
|------|-------|----------|-------|--------------|------------|-------------|--------|----|
| `TASK-112` | unknown | `DEV-fengchu` | `unknown` | `claimed` | `not_run` | `not_ready` | `codex/TASK-112-agent-openapi-dify-parity` | `n/a` |
| `TASK-114` | unknown | `DEV-nezha` | `unknown` | `claimed` | `unknown` | `not_ready` | `codex/TASK-114-feat-037-billing-ledger` | `n/a` |
| `TASK-115` | unknown | `DEV-zhongda` | `unknown` | `claimed` | `unknown` | `not_ready` | `codex/TASK-115-kb-module-maintenance` | `n/a` |
| `TASK-116` | unknown | `DEV-wolong` | `unknown` | `claimed` | `unknown` | `not_ready` | `codex/TASK-116-skill-module-completion` | `n/a` |
| `TASK-117` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-117-agentcici-help-center-site` | `n/a` |
| `TASK-118` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-118-admin-organization-profile` | `n/a` |

## 集成状态

- 暂无集成队列记录。

## 维护规则

- 不手工维护本文件的事实内容。
- 管理者需要查看团队状态时，运行 `python3 scripts/summarize-team-status.py .claw --write`。
- 远端 PR/CI 数据只有在写入任务状态或由后续平台脚本接入后，才会进入本汇总。
