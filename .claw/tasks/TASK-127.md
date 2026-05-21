---
kind: task-status
version: 1
task_id: TASK-127
title: current-status 热状态页精简
status: done
assignee: MANAGER-001
branch: codex/TASK-124-platform-tenant-manual-provisioning
spec_path: docs/specs/FEAT-049-current-status-hotfile-compression.md
assignment_path: .claw/assignments/TASK-127.yaml
updated_at: 2026-05-21T06:37:15Z
updated_by: ai
---

# TASK-127 - current-status 热状态页精简

## 范围

检查 `.claw/current-status.md` 是否承载了过多历史、验证和任务细节，并将其收口为只表达当前项目状态、近期关键变化和下一步动作的热状态文件；同时用一个最小 spec 记录该职责边界。

## 计划

1. 创建本任务的 assignment 和 task status。
2. 运行 `TASK-127` 的 task-scoped `dev-login.py`，确认状态文件编辑范围被授权。
3. 精简 `.claw/current-status.md`，移除与 `task-board.md`、`test-report.md`、`tasks/TASK-xxx.md` 和 spec 重复的长段内容。
4. 运行最小状态校验，确认 `.claw` 结构仍然有效。

## 协作说明

- 当前工作树已有大量未提交改动，本任务继续复用现有分支。
- 本任务只处理 `.claw` 状态文件，不改实现代码。
- 如果需要保留详细历史，只在 `task-board.md`、`tasks/TASK-xxx.md` 和 `test-report.md` 中维护。
- 规格仅用于说明热状态文件边界，不重写已有项目基线。

## 进展

- 2026-05-21T06:33:50Z：已创建 TASK-127 assignment 和 task status，准备运行 task-scoped `dev-login.py` 后开始收口 `current-status.md`。
- 2026-05-21T06:35:05Z：已将 `current-status.md` 收口为热状态短版，只保留当前态、近期关键变化和下一步。
- 2026-05-21T06:37:15Z：已补最小规格 `FEAT-049`、重新通过 task-scoped `dev-login.py`，并确认 `.claw` 状态校验通过。

## 验证

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/test-report.md .claw/assignments/TASK-127.yaml .claw/tasks/TASK-127.md --no-cache --json` -> allowed。
- `identity-bootstrap-spec`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments/TASK-127.yaml .claw/tasks/TASK-127.md docs/specs/FEAT-049-current-status-hotfile-compression.md --no-cache --json` -> allowed。
- `identity-task-scoped`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-127 --branch codex/TASK-124-platform-tenant-manual-provisioning --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments/TASK-127.yaml .claw/tasks/TASK-127.md docs/specs/FEAT-049-current-status-hotfile-compression.md --no-cache --json` -> allowed。
- `state-validate`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw` -> passed。

## 备注

- 目标是减少重复和降低阅读成本，不是补充更多运行细节。
