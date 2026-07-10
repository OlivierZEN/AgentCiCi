---
kind: task-status
task_id: TASK-180
title: AI 应用页与客户互动工作台 UI 重构
status: review
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md
assignment_path: .claw/assignments/TASK-180.yaml
updated_at: 2026-07-10T12:38:00+08:00
updated_by: MANAGER-001
---

# TASK-180 - AI 应用页与客户互动工作台 UI 重构

## 当前目标

按用户截图反馈重构 AI 应用页 UI：减少外框线和常驻列表占位，让客户互动工作台更紧凑，滚动条默认隐藏，并把 AI 应用菜单改成点击左侧一级入口后出现的悬浮窄列表。

## 当前进展

- 已读取 Product Design 路由、项目产品与设计事实源。
- 已完成 FEAT-090 设计规格、任务授权、身份门禁和 assignment check。
- 已将 AI 应用页常驻列表改为点击侧栏触发的悬浮窄列表，释放主内容宽度。
- 已收紧客户互动工作台在 AI 应用页内的核心布局，减少外框线，并调整局部滚动条默认隐藏策略。
- 已完成本地构建、静态检查、桌面 Chrome 截图和交互断言验证；当前待提交推送和产品复核，未发布生产。

## 计划

1. 创建 FEAT-090、TASK-180 和授权边界。`done`
2. 改造 AI 应用菜单为点击触发的悬浮窄列表。`done`
3. 收紧客户互动工作台在 AI 应用页内的布局、框线和滚动条策略。`done`
4. 本地构建、桌面端浏览器截图与交互验证。`done`
5. 更新测试证据并提交推送。`done`

## 验证记录

- `identity-gate`: generic MANAGER-001 login and task-scoped `dev-login.py .claw --developer MANAGER-001 --task TASK-180 --branch main --files ... --json` -> **allowed**。
- `assignment-check`: `check-assignment.py .claw --developer MANAGER-001 --task TASK-180 --branch main --files ... --json` -> **allowed**。
- `frontend-build`: `npm --prefix frontend run build` -> **success**；保留既有 Vite large chunk warning。
- `static-check`: `git diff --check` -> **success**。
- `local-browser-workbench`: Chrome at `2048x1000`, mocked authenticated local APIs, `/app?aiApp=customer-workbench` -> **success**；常驻 AI 应用列表不存在，页面外层无横向/纵向滚动条，最右侧无可见滚动条，metrics 外框线和圆角已移除，关键局部滚动区默认 `scrollbar-width: none`，console errors `0`。
- `local-browser-flyout`: 点击左侧 AI 应用入口 -> **success**；悬浮窄列表出现，应用项 `5` 个；点击“客户洞察”后列表关闭并切换主页面。
- 截图证据：`output/playwright/task180-ai-apps-workbench-local.png`、`output/playwright/task180-ai-apps-flyout-local.png`。

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
