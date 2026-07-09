---
kind: task-status
task_id: TASK-175
title: 客户互动工作台外层滚动与 CRM 主页按钮清理
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-085-customer-workbench-scroll-cleanup.md
assignment_path: .claw/assignments/TASK-175.yaml
updated_at: 2026-07-10T10:50:00+08:00
updated_by: MANAGER-001
---

# TASK-175 - 客户互动工作台外层滚动与 CRM 主页按钮清理

## 当前目标

按用户截图反馈，删除“打开 CRM 客户主页”按钮，并确保智能体平台端和 CloudCC CRM 内嵌端都不出现浏览器最右侧整页滚动条，只保留工作台内部局部滚动。

## 当前进展

- 已读取 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json`、Impeccable product register 和 frontend-design 要求。
- 已确认当前线上生产版本为 `2.3.2`。
- 已删除 `CustomerWorkbenchApp.tsx` 中心头部“打开 CRM 客户主页”按钮。
- 已修正 AgentCiCi AI 应用工作台容器、CRM embed 直达页、CloudCC pagecomponent iframe 宿主高度与 overflow。
- 已补充建议卡片操作按钮三列约束，避免窄卡片内按钮裁切。
- 本地验证通过，待提交后按生产 runbook 发布。

## 计划

1. 建立 FEAT-085、TASK-175 和授权边界。`done`
2. 删除 CRM 主页按钮与相关 notice 行为。`done`
3. 修正工作台、AI 应用主面板、embed 容器和 CloudCC pagecomponent iframe 宿主高度/overflow。`done`
4. 前端构建、静态检查和桌面端 Playwright 截图验证。`done`
5. 提交、推送、按生产 runbook 发布，并完成线上 smoke。`in_progress`

## 验证记录

- `identity-gate`: generic MANAGER-001 dev-login for intended files -> allowed.
- `identity-gate-task`: `dev-login.py .claw --developer MANAGER-001 --task TASK-175 --branch main --files ... --json` -> allowed.
- `assignment-check`: `check-assignment.py .claw --developer MANAGER-001 --task TASK-175 --branch main --files ... --json` -> allowed.
- `cloudcc-pagecomponent-docs`: read `platform/overview`, `platform/capabilityMap`, `platform/standardCapabilities`, and `platform/pagecomponent`; `cloudcc scan msapi . online-highcode` found online `component-customer-workbench`.
- `frontend-build`: `npm --prefix frontend run build` -> success; existing Vite large chunk warning remains.
- `static-check`: `git diff --check` -> success.
- `local-browser-platform`: Playwright at `1620x900`, `/app?aiApp=customer-workbench` -> success; `rootScrollable=false`, `hasCrmHomeButton=false`, internal containers keep `overflowY=auto`.
- `local-browser-embed`: Playwright at `1620x900`, `/app?aiApp=customer-workbench&embed=crm` -> success; `rootScrollable=false`, `hasCrmHomeButton=false`.
- `local-cloudcc-host-umd`: Playwright simulated CRM host with 164px top bar and local UMD -> success; host `visibleRightScrollbar=false`, component/iframe height `735px`, host document overflow hidden.
- Screenshots: `output/playwright/task175-local-platform-workbench-v3.png`, `output/playwright/task175-local-embed-workbench-v3.png`, `output/playwright/task175-local-cloudcc-host-umd-v3.png`.

## 变更文件

- `docs/specs/FEAT-085-customer-workbench-scroll-cleanup.md`
- `.claw/tasks/TASK-175.md`
- `.claw/assignments/TASK-175.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/cici-ui.css`
- `frontend/pagecomponents/customer-workbench/customer-workbench.vue`
- `frontend/build/customer-workbench.umd.min.js`

## 交接说明

- 不输出、提交或记录密码、token、secret、cookie 或可复用凭据。
- 当前工作区存在 TASK-176/数据洞察相关未提交改动；TASK-175 提交和发布必须避开这些文件，使用干净提交或临时 worktree 发布。
