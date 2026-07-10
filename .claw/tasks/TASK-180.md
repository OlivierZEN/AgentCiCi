---
kind: task-status
task_id: TASK-180
title: AI 应用页与客户互动工作台 UI 重构
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md
assignment_path: .claw/assignments/TASK-180.yaml
updated_at: 2026-07-10T12:05:00+08:00
updated_by: MANAGER-001
---

# TASK-180 - AI 应用页与客户互动工作台 UI 重构

## 当前目标

按用户截图反馈重构 AI 应用页 UI：减少外框线和常驻列表占位，让客户互动工作台更紧凑，滚动条默认隐藏，并把 AI 应用菜单改成点击左侧一级入口后出现的悬浮窄列表。

## 当前进展

- 已读取 Product Design 路由、项目产品与设计事实源。
- 已完成 FEAT-090 设计规格和任务授权准备。
- 待执行身份门禁、assignment check 后进入前端实现。

## 计划

1. 创建 FEAT-090、TASK-180 和授权边界。`in_progress`
2. 改造 AI 应用菜单为点击触发的悬浮窄列表。`pending`
3. 收紧客户互动工作台在 AI 应用页内的布局、框线和滚动条策略。`pending`
4. 本地构建、桌面端浏览器截图与交互验证。`pending`
5. 更新测试证据并提交推送。`pending`

## 验证记录

- 待补充。

## 变更文件

- `docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md`
- `.claw/tasks/TASK-180.md`
- `.claw/assignments/TASK-180.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/cici-ui.css`

## 交接约束

- 不改变业务 API、CRM 绑定、智能体调用链路或语音 ASR 行为。
- 不新增移动端适配或移动端自动化测试。
- 不输出、提交或记录任何凭证或可复用会话信息。
