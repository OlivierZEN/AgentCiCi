---
kind: task-status
task_id: TASK-184
title: 客户互动工作台左侧队列横向裁切热修
status: in_progress
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-092-customer-workbench-ui-streaming.md
assignment_path: .claw/assignments/TASK-184.yaml
updated_at: 2026-07-11T04:03:47Z
updated_by: MANAGER-001
---

# TASK-184 - 客户互动工作台左侧队列横向裁切热修

## 问题

生产 `2.4.2` 在较窄桌面可视宽度下，左侧队列 `scrollWidth` 大于 `clientWidth`，搜索框、第四个筛选项、客户总数和客户行右侧被中间主内容区裁切。

## 修复范围

- 只调整客户互动工作台左侧队列的列内收缩与筛选布局。
- 不改变三栏比例、业务数据、交互逻辑或移动端范围。
- 验收较窄桌面和 1920px 桌面下队列 `scrollWidth <= clientWidth`，全部客户行和控件可见且无外层滚动条。

## 当前进展

- 已根据用户截图确认问题并创建热修任务。
- 待完成 CSS 定位、浏览器复测和生产发布。
