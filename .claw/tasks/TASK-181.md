---
kind: task-status
task_id: TASK-181
title: 客户互动工作台客户列表排版修复
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-091-customer-workbench-account-list-alignment.md
assignment_path: .claw/assignments/TASK-181.yaml
updated_at: 2026-07-10T13:20:00+08:00
updated_by: MANAGER-001
---

# TASK-181 - 客户互动工作台客户列表排版修复

## 当前目标

修复生产截图反馈的客户互动工作台左侧客户列表排版混乱问题，让客户名称、关注状态、负责人/阶段、时间、标签和最近互动摘要在窄队列中稳定对齐。

## 当前进展

- 已读取项目产品和设计事实源。
- 已确认根因集中在客户列表行的 grid/flex-wrap 截断和 AI 应用页窄队列覆盖规则。
- 待完成任务授权检查后进入前端热修。

## 计划

1. 创建 FEAT-091、TASK-181 和授权边界。`in_progress`
2. 调整客户列表行结构与 CSS 截断、行高、标签约束。`pending`
3. 本地构建与桌面端浏览器截图验证。`pending`
4. 提交推送，并按需要发布生产与回写发布证据。`pending`

## 验证记录

- 待补充。

## 变更文件

- `docs/specs/FEAT-091-customer-workbench-account-list-alignment.md`
- `.claw/tasks/TASK-181.md`
- `.claw/assignments/TASK-181.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/cici-ui.css`

## 交接约束

- 不改变客户互动工作台业务 API、CRM 绑定、智能体调用链路或语音 ASR 行为。
- 不新增移动端适配或移动端自动化测试。
- 不输出、提交或记录任何凭证、token、cookie 或可复用会话信息。
