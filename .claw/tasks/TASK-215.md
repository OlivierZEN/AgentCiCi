---
kind: task-status
task_id: TASK-215
status: ready
updated_at: 2026-07-21T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-215.yaml
spec_path: docs/specs/FEAT-120-trace-full-detail-expansion.md
---

# TASK-215 - 链路追踪全文查看与复制

## Scope

- 为组织管理员在 Trace 节点提供按需展开、复制脱敏详情的能力。
- 区分新 Trace 的受控完整详情与旧 Trace 的历史截断提示。
- 完成后端/前端测试和桌面端浏览器验收。

## Current State

- 已发现当前节点摘要在写入 Trace 时被 `clip(..., 220)` 截断；现有详情页没有全文显示控件。
- 用户已确认采用“展开全文 / 复制内容”的紧凑、按需披露方案。

## Next Action

- 完成 assignment 代表路径校验后开始实现，先为完整详情与历史兼容行为补测试。

## Handoff

- 完整需求、权限边界、交互和验收要求见 `docs/specs/FEAT-120-trace-full-detail-expansion.md`。
- 不读取、不修改、不提交当前工作区中的 TASK-207/TASK-208、`diagrams/` 或其他无关改动。
