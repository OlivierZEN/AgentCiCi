---
kind: task-status
version: 1
task_id: TASK-128
title: task-board 与 task-archive 归档归位
status: done
assignee: MANAGER-001
branch: codex/TASK-124-platform-tenant-manual-provisioning
spec_path: docs/specs/FEAT-050-task-board-archive-normalization.md
assignment_path: .claw/assignments/TASK-128.yaml
updated_at: 2026-05-21T06:46:21Z
updated_by: ai
---

# TASK-128 - task-board 与 task-archive 归档归位

## 范围

检查 `.claw/task-board.md` 是否仍承载过多历史完成态任务，并按 `task-archive.md` 维护规则，把较早的完成态任务迁入归档文件，同时保留所有未完成任务和最近完成窗口。

## 计划

1. 创建 FEAT-050、TASK-128 assignment 和 task status。
2. 运行 `TASK-128` 的 task-scoped `dev-login.py`，确认状态文件编辑范围被授权。
3. 精简 `task-board.md`，仅保留未完成任务与最近完成窗口。
4. 将较早完成态任务以摘要形式迁入 `task-archive.md`。
5. 运行状态校验并确认冷热分层回到可维护范围。

## 协作说明

- 当前工作树已有大量未提交改动，本任务继续复用现有分支。
- 本任务只处理 `.claw` 状态文件与最小规格，不改实现代码。
- 详细验证与实现证据仍保留在 `.claw/tasks/TASK-xxx.md` 和 `.claw/test-report.md`。

## 进展

- 2026-05-21T06:44:35Z：已确认 `task-board.md` 体量为 3184 行 / 379157 字节，且完成态任务 96 条，明显超过 `task-archive.md` 的维护窗口。
- 2026-05-21T06:46:21Z：已将主板收口为“未完成任务 + 最近完成窗口”，并把更早完成态任务迁入 `task-archive.md`；`.claw` 状态校验通过。

## 验证

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/task-archive.md .claw/assignments/TASK-128.yaml .claw/tasks/TASK-128.md docs/specs/FEAT-050-task-board-archive-normalization.md --no-cache --json` -> allowed。
- `identity-task-scoped`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-128 --branch codex/TASK-124-platform-tenant-manual-provisioning --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/task-archive.md .claw/assignments/TASK-128.yaml .claw/tasks/TASK-128.md docs/specs/FEAT-050-task-board-archive-normalization.md --no-cache --json` -> allowed。
- `state-validate`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw` -> passed。

## 备注

- 本任务目标是恢复状态分层，不是删减事实源。
