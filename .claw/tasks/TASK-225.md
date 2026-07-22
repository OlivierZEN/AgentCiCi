---
kind: task-status
task_id: TASK-225
status: in_progress
updated_at: 2026-07-22T10:15:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-225.yaml
spec_path: docs/specs/FEAT-130-forced-skill-execution-context.md
---

# TASK-225 - 对话技能选择的强制执行上下文与可观测性

## Scope

- 让工作台选择技能成为本轮强制业务上下文，不再只是工具授权。
- 在 Trace 与两个监控界面明确展示选择、有效上下文、实际激活和未采纳原因。

## Current State

- 已确认当前实现会发送 `activeSkillCode`，但它只影响技能专属工具授权；全部绑定技能的提示词和文件型文档仍被并列注入。
- 已完成规格与桌面端紧凑文本状态设计；尚未改动运行代码。

## Verification

- 待实现后记录后端、前端构建、Trace 合约和桌面端检查结果。
