---
kind: task-status
task_id: TASK-201
status: ready
updated_at: 2026-07-14T00:16:48Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-201.yaml
spec_path: docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md
---

# TASK-201 - 智能体构建页布局与模型治理收敛

## Scope

- 移除 Agent Builder 用户可见的基础模型选择入口，保留内部字段与平台统一模型治理。
- 重排 Agent 定义双栏，把系统提示词放到右栏，把业务/安全/版本元数据收拢到左栏。
- 将策略开关与头像放到同一行。
- 将评测和发布渠道移入下方版本控制与交付工作区。
- 完成聚焦测试、构建和桌面端截图验收。

## Initial Findings

- `draft.model` 同时承担历史兼容和编译输入，不能随 UI 移除一起删除。
- `activeEditorTab` 目前同时控制定义/评测/渠道；下方 `activeCompileTab` 已是适合承载生命周期页签的状态。
- FEAT-106 已确保评测内容与发布渠道内容独立，本次只调整二者的页面位置。

## Implementation Plan

- 建立 FEAT-107、TASK-201 和任务授权。
- 收敛页签状态与发布/评测跳转。
- 重排 JSX 与桌面端 CSS，保留现有组件和数据行为。
- 更新聚焦测试，运行前端测试/构建并完成浏览器视觉检查。

## Verification

- 待执行。

## Changed Files

- `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md`
- `.claw/tasks/TASK-201.md`
- `.claw/assignments/TASK-201.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- 目标分支：`codex/TASK-201-agent-builder-layout`。
- 保留未跟踪 `diagrams/`，本任务不读取、不修改、不提交。
