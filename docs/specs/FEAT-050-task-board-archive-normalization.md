# FEAT-050 task-board 与 task-archive 归档归位

## 背景

`.claw/task-board.md` 当前累计 119 张任务卡，其中完成态 96 张，远超 `task-archive.md` 维护规则中“完成态任务超过 20 条即迁移最旧任务”的窗口设定，导致主板变成历史全集而不是执行队列。

## 目标

- 恢复 `task-board.md` 作为活跃队列与近期交接板的职责。
- 把较早的完成态任务迁入 `task-archive.md`。
- 保留所有未完成任务与最近完成窗口，避免打断当前执行上下文。

## 非目标

- 不改动任何实现代码、测试代码和 feature spec 内容。
- 不删除任务事实，只改变其在状态体系中的存放层级。
- 不归档仍处于 `assigned`、`in_progress`、`pending`、`paused`、`superseded` 等非完成态任务。

## 收口规则

1. `task-board.md` 保留所有未完成任务。
2. `task-board.md` 仅保留最近 20 条 `completed` 任务卡。
3. 更早的 `completed` 任务迁入 `task-archive.md`，只保留任务 ID、最终状态、主要范围和必要交接信息。
4. 详细验证与实现证据继续留在 `.claw/tasks/TASK-xxx.md` 与 `.claw/test-report.md`。

## 验收标准

- `task-board.md` 明显小于当前体量，能够快速浏览当前待办与最近完成事项。
- `task-archive.md` 不再为空，并能承接较早完成态任务的摘要。
- `.claw` 状态校验通过。
