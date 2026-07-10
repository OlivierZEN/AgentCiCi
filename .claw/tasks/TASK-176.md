---
kind: task-status
task_id: TASK-176
title: 数据洞察与客户洞察解耦热修复
status: completed
owner_role: project-manager
assignee: MANAGER-001
spec_path: docs/specs/FEAT-086-data-insight-decoupling-hotfix.md
assignment_path: .claw/assignments/TASK-176.yaml
updated_at: 2026-07-10T08:04:21+08:00
updated_by: MANAGER-001
---

# TASK-176 - 数据洞察与客户洞察解耦热修复

## 当前目标

立刻修复 TASK-174 错误耦合：恢复客户洞察为独立应用，新增独立数据洞察应用和独立仪表盘 API。

## 当前进展

- 已确认错误点：`customer-insight` 应用被改名为“数据洞察”，客户洞察面板中被嵌入数据洞察仪表盘。
- 已创建 FEAT-086，明确客户洞察不得承载数据洞察仪表盘。
- 已恢复客户洞察入口和项目/报告编辑流。
- 已新增独立数据洞察入口、前端模块和 `/ai/data-insights/dashboard` API。
- 已移除客户洞察 dashboard API，并补充数据洞察独立集成测试。
- 已完成前端生产构建、后端目标集成测试、`git diff --check` 和桌面端 Playwright 截图检查。

## 计划

1. 恢复客户洞察入口和客户洞察前端模块。`completed`
2. 新建数据洞察前端模块和 `data-insight` 应用入口。`completed`
3. 新建数据洞察后端 API，移除客户洞察 dashboard API。`completed`
4. 跑后端测试、前端构建和桌面端浏览器检查。`completed`
5. 提交、推送并发布热修复。`in_progress`

## 验证记录

- `identity-gate`: allowed.
- `assignment-check`: allowed.
- `backend-data-customer-insight`: `mvn -q -Dtest='com.codehouse.ciciassistant.customerinsight.CustomerInsightIntegrationTest,com.codehouse.ciciassistant.datainsight.DataInsightIntegrationTest' test` -> success.
- `frontend-build`: `npm run build` -> success; existing Vite large chunk warning remains.
- `static-check`: `git diff --check` -> success.
- `local-browser-check`: Playwright verified `/app?aiApp=data-insight` dashboard categories and `/app?aiApp=customer-insight` independence -> success.
