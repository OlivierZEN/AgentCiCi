---
kind: task-status
task_id: TASK-210
status: in_progress
updated_at: 2026-07-14T16:22:41Z
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

- 已确认并修复根因：微信曾映射到 Lucide 通用气泡，`CRM_TASK` 又落入默认消息图标。
- 微信现读取 Simple Icons 规范路径；电话、会议、邮件、CRM 任务、CRM 日程和客户反馈均使用独立 Lucide 语义图标。
- 样式类已改为稳定英文业务语义，不再由中文展示文本动态拼接；八主题共用相同形状、尺寸和轴线坐标。
- 完整时间线的重复 CRM event id 已使用组合键隔离，桌面端复验无新增控制台错误。
- Vitest 16 个文件、89 项与生产构建通过，等待生产发布。

## Next Action

- 在 `2.7.3` 生产基线上完成复测，执行统一发布 dry-run、生产备份、`2.7.4` 发布与 AgentCiCi/CloudCC 嵌入页复验。

## Changed Files

- `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md`
- `.claw/tasks/TASK-210.md`
- `.claw/assignments/TASK-210.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.test.ts`

## Handoff

- 分支：`codex/TASK-210-customer-workbench-standard-icons`。
- 不触碰 TASK-208 的 CRM 分析实现范围。
- 本地视觉证据位于 `output/playwright/task210-*.png`，未纳入发布产物。
