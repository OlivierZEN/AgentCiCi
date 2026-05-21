---
kind: task-status
version: 1
task_id: TASK-126
title: 设计事实源中文化与 README 引用收口
status: done
assignee: MANAGER-001
branch: codex/TASK-124-platform-tenant-manual-provisioning
spec_path: docs/specs/FEAT-048-design-factsource-zh.md
assignment_path: .claw/assignments/TASK-126.yaml
updated_at: 2026-05-21T06:13:36Z
updated_by: ai
---

# TASK-126 - 设计事实源中文化与 README 引用收口

## 范围

把 `DESIGN.json` 的人类可读说明翻成中文，并同步清理 `README.md` 中对旧版 `DESIGN.md` 章节的重引用，使当前设计事实源入口更一致。

## 计划

1. 创建 FEAT-048、TASK-126 assignment 和状态文件。
2. 运行 `TASK-126` 的 task-scoped `dev-login.py`。
3. 翻译 `DESIGN.json` 的说明性字符串，并收口 `README.md` 的设计治理段落。
4. 运行状态校验，确认 `.claw` 未漂移。

## 协作说明

- 本任务不修改 `DESIGN.json` 的键名和 token 数值。
- 本任务不改实现代码，不切新分支。
- 当前工作树较脏，只处理授权文件。

## 进展

- 2026-05-21T06:09:17Z：已创建 FEAT-048、TASK-126 assignment 和 task status，准备执行 task-scoped `dev-login.py` 后开始翻译与收口。
- 2026-05-21T06:13:36Z：已通过 `TASK-126` task-scoped `dev-login.py`，完成 `README.md` 设计治理段落收口与 `DESIGN.json` 中文化，并确认 JSON 解析与 `.claw` 状态校验均通过。

## 验证

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments/TASK-126.yaml .claw/tasks/TASK-126.md docs/specs/FEAT-048-design-factsource-zh.md README.md DESIGN.json --no-cache --json` -> allowed。
- `identity-task-scoped`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --task TASK-126 --branch codex/TASK-124-platform-tenant-manual-provisioning --git-username OwenZheng-Cloud --files README.md DESIGN.json docs/specs/FEAT-048-design-factsource-zh.md .claw/assignments/TASK-126.yaml .claw/tasks/TASK-126.md .claw/task-board.md .claw/current-status.md --no-cache --json` -> allowed。
- `design-json-parse`: `node -e "JSON.parse(require('fs').readFileSync('DESIGN.json','utf8')); console.log('DESIGN.json OK')"` -> success。
- `state-validate`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/validate-state.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw` -> passed。

## 备注

- 本任务是 FEAT-047 之后的补充收口，不回退前一轮职责拆分。
- 为避免潜在结构化消费断裂，`role`、颜色值、尺寸值和少量标识型字段保持原语义，不做翻译。
