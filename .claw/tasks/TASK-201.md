---
kind: task-status
task_id: TASK-201
status: review
updated_at: 2026-07-14T00:31:25Z
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

- MANAGER-001 通用与 TASK-201 任务级 SSH challenge：allowed；代表性文件 assignment 检查：allowed。
- `npm test -- src/assistant/AgentBuilderShell.test.ts`：14 项通过。
- `npm test`：12 个文件、68 项通过。
- `npm run build`：成功，仅保留既有 Vite 大 chunk 提示。
- `git diff --check`：成功。
- 本地真实 1280x720 管理后台桌面验收：定义区等宽等高、无横向溢出，头像/上传/清除/四个策略按钮同一视觉行，系统提示词位于右栏，具体模型入口不存在。
- “评测”和“发布渠道”在下方“版本控制与交付”分别打开；active/focus 状态、内容隔离和浏览器 console 0 error / 0 warning 通过。

## Changed Files

- `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md`
- `.claw/tasks/TASK-201.md`
- `.claw/assignments/TASK-201.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `frontend/src/assistant/AgentBuilderShell.tsx`
- `frontend/src/assistant/AgentBuilderShell.test.ts`
- `frontend/src/assistant/cici-ui.css`

## Handoff

- 目标分支：`codex/TASK-201-agent-builder-layout`。
- 本地实现与桌面端验收完成，待合并或进入后续发布流程；本任务未执行生产发布。
- 保留未跟踪 `diagrams/`，本任务不读取、不修改、不提交。
