---
kind: task-status
task_id: TASK-210
status: in_progress
updated_at: 2026-07-14T15:55:28Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: frontend-agent
assignment_path: .claw/assignments/TASK-210.yaml
spec_path: docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md
---

# TASK-210 - 客户互动工作台标准渠道图标治理

## Scope

- 用公开规范的微信品牌图标替换通用消息气泡。
- 为电话、会议、邮件、CRM 任务、CRM 日程和客户反馈建立标准图标映射。
- 修复来源回退逻辑与动态中文 CSS 类名。
- 补充单元测试、构建和桌面端视觉验证。

## Current State

- 已确认根因：`wechat` 映射到 Lucide `MessageCircle`，`CRM_TASK` 落入默认 `MessageSquare`。
- 已批准 FEAT-116，等待授权校验后按 TDD 开始实现。

## Next Action

- 校验 TASK-210 写入范围，提交并推送分配文档，然后先补来源映射失败测试。

## Changed Files

- `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md`
- `.claw/tasks/TASK-210.md`
- `.claw/assignments/TASK-210.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`

## Handoff

- 分支：`codex/TASK-210-customer-workbench-standard-icons`。
- 不触碰 TASK-208 的 CRM 分析实现范围。

