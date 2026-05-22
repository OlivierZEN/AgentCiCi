---
kind: team-status
version: 3
updated_at: 2026-05-22T10:52:51Z
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
| 授权任务 | 21 |
| 已开始任务 | 15 |
| 等待 review | 1 |
| 已集成任务 | 13 |
| 阻塞任务 | 0 |

## 成员状态

### DEV-fengchu

- display_name: `凤雏`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `TASK-112, TASK-123, TASK-132, TASK-133`
- active_tasks: `TASK-132, TASK-133`
- latest_pr: `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/2`
- contribution_status: `claimed, merged`
- validation_status: `unknown`
- integration_status: `integrated, not_ready`

### DEV-nezha

- display_name: `哪吒`
- role: `fullstack-agent`
- identity_status: `active`
- assigned_tasks: `none`
- active_tasks: `none`
- latest_pr: `none`
- contribution_status: `none`
- validation_status: `none`
- integration_status: `none`

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
- assigned_tasks: `TASK-114, TASK-117, TASK-118, TASK-119, TASK-120, TASK-121, TASK-122, TASK-124, TASK-125, TASK-126, TASK-127, TASK-128, TASK-129, TASK-130, TASK-131`
- active_tasks: `TASK-114, TASK-119, TASK-124, TASK-131`
- latest_pr: `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/6`
- contribution_status: `claimed, code_submitted, merged, review_requested`
- validation_status: `unknown`
- integration_status: `integrated, not_ready, waiting_review`

## 任务状态

| Task | Title | Assignee | Board | Contribution | Validation | Integration | Branch | PR |
|------|-------|----------|-------|--------------|------------|-------------|--------|----|
| `TASK-112` | unknown | `DEV-fengchu` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-112-agent-openapi-dify-parity` | `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/2` |
| `TASK-114` | FEAT-037 SaaS billing usage ledger | `MANAGER-001` | `ready` | `claimed` | `unknown` | `not_ready` | `codex/TASK-114-feat-037-billing-ledger` | `n/a` |
| `TASK-115` | Knowledge base module maintenance | `DEV-zhongda` | `ready` | `claimed` | `unknown` | `not_ready` | `codex/TASK-115-kb-module-maintenance` | `n/a` |
| `TASK-116` | Skill module completion and optimization | `DEV-wolong` | `ready` | `claimed` | `unknown` | `not_ready` | `codex/TASK-116-skill-module-completion` | `n/a` |
| `TASK-117` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-117-agentcici-help-center-site` | `n/a` |
| `TASK-118` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `n/a` |
| `TASK-119` | unknown | `MANAGER-001` | `unknown` | `claimed` | `unknown` | `not_ready` | `codex/TASK-119-agent-access-control` | `n/a` |
| `TASK-120` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-120-platform-accountless-login` | `None` |
| `TASK-121` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-121-db-rename-agentcici` | `None` |
| `TASK-122` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-122-platform-console-production-polish` | `None` |
| `TASK-123` | unknown | `DEV-fengchu` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-123-openapi-cloudcc-token-override` | `n/a` |
| `TASK-124` | FEAT-046 platform tenant manual provisioning and lifecycle split | `MANAGER-001` | `in_progress` | `code_submitted` | `unknown` | `waiting_review` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/5` |
| `TASK-125` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `n/a` |
| `TASK-126` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `n/a` |
| `TASK-127` | Merge remaining local branches into the current branch | `MANAGER-001` | `done` | `merged` | `unknown` | `integrated` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `n/a` |
| `TASK-128` | unknown | `MANAGER-001` | `unknown` | `merged` | `unknown` | `integrated` | `codex/TASK-124-platform-tenant-manual-provisioning` | `None` |
| `TASK-129` | Admin login organization-selection alignment | `MANAGER-001` | `done` | `merged` | `unknown` | `integrated` | `codex/TASK-124-feat-046-platform-tenant-provisioning` | `None` |
| `TASK-130` | ACR release version governance and app version badge | `MANAGER-001` | `done` | `merged` | `unknown` | `integrated` | `codex/local-uncommitted-feature-mr` | `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/6` |
| `TASK-131` | Platform account orgless auth context | `MANAGER-001` | `review` | `review_requested` | `unknown` | `waiting_review` | `codex/local-uncommitted-feature-mr` | `https://codeup.aliyun.com/627b18115b46541dd2ff340e/cloudcc-aidev-projects/cc-agentcici/change/6` |
| `TASK-132` | Agent Builder focused-agent skill binding refresh bugfix | `DEV-fengchu` | `ready` | `claimed` | `unknown` | `not_ready` | `codex/TASK-132-agent-builder-skill-refresh-bugfix` | `None` |
| `TASK-133` | Agent Builder no-model new-Agent model-config redirect | `DEV-fengchu` | `ready` | `claimed` | `unknown` | `not_ready` | `codex/TASK-133-agent-builder-new-agent-model-config-fix` | `None` |

## 集成状态

- 暂无集成队列记录。

## 维护规则

- 不手工维护本文件的事实内容。
- 管理者需要查看团队状态时，运行 `python3 scripts/summarize-team-status.py .claw --write`。
- 远端 PR/CI 数据只有在写入任务状态或由后续平台脚本接入后，才会进入本汇总。
