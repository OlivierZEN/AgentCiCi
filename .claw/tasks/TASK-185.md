---
kind: task-status
task_id: TASK-185
title: 客户互动工作台 AI 助理展开模式
status: in_progress
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-093-customer-assistant-expand-mode.md
assignment_path: .claw/assignments/TASK-185.yaml
updated_at: 2026-07-11T04:27:00Z
updated_by: MANAGER-001
---

# TASK-185 - 客户互动工作台 AI 助理展开模式

## 目标

移除无价值的固定按钮，实现 IDE 式 AI 助理展开/恢复：隐藏左侧客户队列、保持中间详情宽度、扩大右侧对话区域并提供平滑侧滑。

## 计划

1. 移除 pinned 状态和切换客户自动关闭行为。
2. 新增标准面板展开/恢复按钮与两态网格。
3. 补充状态测试和窄/宽桌面浏览器尺寸验收。
4. 发布生产并复验 AgentCiCi 与 CloudCC iframe。

## 当前进展

- 代码和本地浏览器验收完成；中间区展开前后宽度误差为 0。
- 队列展开态为 hidden/inert，恢复后客户和列表状态保留；关闭再打开回到默认三栏。
- 前端 57 项测试、Vite production build、`git diff --check` 通过。
- 待提交、发布生产并复验真实 CloudCC iframe。
