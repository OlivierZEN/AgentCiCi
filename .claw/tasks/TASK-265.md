---
kind: task-status
task_id: TASK-265
status: in_progress
updated_at: 2026-08-04T04:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-265.yaml
spec_path: docs/specs/FEAT-157-dev-autopilot-delivery-review-tool.md
---

# TASK-265 - DEV Autopilot 研发交付评审 Tool

## Current State

- 已完成 AgentCiCi 侧评审 Tool、SERVICE 执行、Skill 显式绑定和状态机边界设计。
- 等待完成任务授权提交后开始编码。
- Blocked: none

## Next Action

- 实现查询事件扩展、评审 Tool、显式绑定与定向测试。

## Evidence

- 设计事实源：`docs/specs/FEAT-157-dev-autopilot-delivery-review-tool.md`。
- DEV Autopilot 评审 API 契约：`POST /api/pm/v1/tasks/{taskId}/reviews`。
