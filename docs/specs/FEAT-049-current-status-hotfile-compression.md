# FEAT-049 current-status 热状态页精简

## 背景

`.claw/current-status.md` 已经累计了过长的历史描述、验证明细和任务执行细节，阅读成本高，并与 `task-board.md`、`test-report.md`、`.claw/tasks/TASK-xxx.md` 以及既有 spec 发生明显重复。

## 目标

- 让 `current-status.md` 回到热状态文件职责。
- 只保留当前主线、近期关键变化和下一步动作。
- 降低重复维护成本，避免同一事实在多个状态文件中展开叙述。

## 非目标

- 不回写或重构历史任务卡、测试报告和 feature spec。
- 不调整项目实现代码、产品规则或设计 token。
- 不把 `current-status.md` 变成新的任务归档文件。

## 收口规则

1. `current-status.md` 只保留当前项目状态、近期关键变化、下一步动作和少量高价值提示。
2. 详细任务历史保留在 `.claw/task-board.md`。
3. 验证命令、截图、测试结果和门禁证据保留在 `.claw/test-report.md` 与 `.claw/tasks/TASK-xxx.md`。
4. 设计、实现边界和验收条件继续保留在 `docs/specs/`。

## 验收标准

- `current-status.md` 可以在短时间内读完，并能回答“现在在做什么、刚完成了什么、下一步是什么”。
- 文件不再逐条罗列长验证命令、完整历史链路和与其他状态文件重复的实现细节。
- `.claw` 状态校验通过。
