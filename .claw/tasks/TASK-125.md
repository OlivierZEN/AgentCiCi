---
kind: task-status
version: 1
task_id: TASK-125
title: 顶层规范文档减重与职责拆分
status: done
assignee: MANAGER-001
branch: codex/TASK-124-platform-tenant-manual-provisioning
spec_path: docs/specs/FEAT-047-guidance-doc-compression.md
assignment_path: .claw/assignments/TASK-125.yaml
updated_at: 2026-05-21T05:57:19Z
updated_by: ai
---

# TASK-125 - 顶层规范文档减重与职责拆分

## 范围

收口根目录 `AGENTS.md`、`PRODUCT.md`、`DESIGN.md` 的职责边界，统一改成中文描述，减少重复信息，同时保留项目必须执行的约束和 `DESIGN.json` 的详细事实源角色。

## 计划

1. 新增本任务的 spec、assignment 和状态文件。
2. 运行 `TASK-125` 的 task-scoped `dev-login.py`，确认文档编辑范围被授权。
3. 精简三份文档，并把职责边界写清楚。
4. 做最小验证，确认状态文件和顶层规范没有明显漂移。

## 协作说明

- 当前工作树已有大量未提交改动，不切新分支，不碰实现文件。
- 本任务只处理顶层规范和必要的 `.claw` 状态文件。
- 详细设计 token 与组件规则继续以 `DESIGN.json` 为准。

## 进展

- 2026-05-21T05:53:09Z：已创建 FEAT-047、TASK-125 assignment 和 task status，准备执行 task-scoped `dev-login.py` 后开始文档精简。
- 2026-05-21T05:57:19Z：已通过 `TASK-125` task-scoped `dev-login.py`，并将 `AGENTS.md`、`PRODUCT.md`、`DESIGN.md` 重写为更短的中文版本，明确三者与 `DESIGN.json` 的职责边界。

## 验证

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments/TASK-125.yaml .claw/tasks/TASK-125.md docs/specs/FEAT-047-guidance-doc-compression.md AGENTS.md PRODUCT.md DESIGN.md --no-cache --json` -> allowed。
- `identity-task-scoped`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-125 --branch codex/TASK-124-platform-tenant-manual-provisioning --git-username OwenZheng-Cloud --files AGENTS.md PRODUCT.md DESIGN.md docs/specs/FEAT-047-guidance-doc-compression.md .claw/assignments/TASK-125.yaml .claw/tasks/TASK-125.md .claw/task-board.md .claw/current-status.md --no-cache --json` -> allowed。

## 备注

- 本任务目标是减重，不是补充更多治理条款。
- `DESIGN.json` 保持不变，继续作为详细设计事实源。
