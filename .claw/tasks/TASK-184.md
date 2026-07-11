---
kind: task-status
task_id: TASK-184
title: 客户互动工作台左侧队列横向裁切热修
status: done
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-092-customer-workbench-ui-streaming.md
assignment_path: .claw/assignments/TASK-184.yaml
updated_at: 2026-07-11T04:16:00Z
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
- 已确认根因是搜索框和设置区 `width: 100%` 未使用 border-box，额外内边距/边框把左栏撑宽；四筛选按钮的 nowrap 与工作台专用 7px 内边距进一步造成文字裁切。
- 已完成盒模型、直接子项最大宽度、筛选网格和按钮内边距修复。
- 本地 712x725：队列 `277/277`，四筛选按钮均无内部溢出；1920x960：队列与筛选区均 `307/307`，页面无外层溢出。
- 前端 12 个测试文件、56 项测试和生产构建通过；待发布生产热修。
- 已发布生产 `2.4.3`，提交/标签/镜像/版本接口统一为 `3b18b8591e2c` / `2.4.3`。
- CloudCC 真实 iframe：队列 `335/335`、筛选区 `315/315`、客户列表 `315/315`，四个筛选按钮均无内部溢出；技能校验、公开入口、健康和静置日志通过。
