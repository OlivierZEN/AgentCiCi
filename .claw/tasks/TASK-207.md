---
kind: task-status
task_id: TASK-207
status: ready
updated_at: 2026-07-14T13:07:01Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-207.yaml
spec_path: docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md
---

# TASK-207 - 前台主题一致性与视觉对齐全量治理

## Scope

- 审计并修复认证后普通用户前台的八主题一致性。
- 将组织切换入口从固定 `CB` 改为组织名称首字符。
- 逐页修复工具栏、表单、列表、表格、卡片、弹窗和空态对齐。
- 完成测试、构建、桌面端逐页截图与控制台验收。

## Current State

- 用户提供七张真实截图，确认 AI 应用菜单、客户互动弹窗、数据洞察、知微画像、智能体工作区、专属记忆和组织菜单存在主题或对齐缺陷。
- FEAT-113 已将全量前台范围、主题 token、组织首字符和页面验收矩阵写明。
- 等待 assignment 验证和实现分支创建。

## Next Action

- 验证授权，创建实现计划，然后按 TDD 完成静态主题守卫、组织首字符和逐页 CSS/布局修复。

## Changed Files

- `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md`
- `.claw/tasks/TASK-207.md`
- `.claw/assignments/TASK-207.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- 目标分支：`codex/TASK-207-frontend-theme-alignment-audit`。
- 保留未跟踪 `diagrams/`，本任务不读取、不修改、不提交。

