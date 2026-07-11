---
kind: task-status
task_id: TASK-186
title: 产品控件去框化与客户互动工作台全页治理
status: in_progress
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-094-customer-workbench-control-chrome-cleanup.md
assignment_path: .claw/assignments/TASK-186.yaml
updated_at: 2026-07-11T05:12:37Z
updated_by: MANAGER-001
---

# TASK-186 - 产品控件去框化与客户互动工作台全页治理

## 目标

移除模式切换的重复外框，从根因上清除工作台控件继承的阴影、圆角和位移套层，并形成后续新页面可复用的公共控件规则。

## 计划

1. 完成控件状态和 CSS 层叠根因检查。
2. 建立公共裸图标按钮、无外框模式切换规则并更新设计事实源。
3. 全页审查工作台按钮类型和 computed style，验证默认、hover、focus、展开和关闭状态。
4. 发布生产并验证 AgentCiCi 与 CloudCC iframe。

## 当前进展

- 已确认模式切换为外层容器和内部按钮双层框。
- 已确认图标按钮只做局部透明覆盖，未完整重置全局按钮 chrome。
