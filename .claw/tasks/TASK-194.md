---
kind: task-status
task_id: TASK-194
title: 全量客户名称搜索与产品输入焦点治理
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-101-global-customer-search-and-field-focus.md
assignment_path: .claw/assignments/TASK-194.yaml
updated_at: 2026-07-12T03:10:00Z
updated_by: MANAGER-001
---

# TASK-194 - 全量客户名称搜索与产品输入焦点治理

## 目标

让客户名称搜索覆盖当前 CRM 用户全部可见客户，并从认证后产品基础样式消除输入控件双层焦点框。

## 计划

1. 实现权限范围内的 CloudCC Account 服务端名称搜索和缓存外详情加载。
2. 调整搜索态 UI 文案、结果语义和空时间展示。
3. 增加产品壳层输入焦点守卫与客户搜索单层 `focus-within`。
4. 完成自动化、浏览器、真实 CRM 与生产发布验收。

## 当前进展

- 已实现权限范围内 Account 名称全局搜索、搜索表达式转义和缓存外客户按需详情投影。
- 已实现客户搜索单层 `focus-within` 与全页面文本字段无阴影焦点守卫。
- 后端 11 项相关测试、前端 59 项测试及生产构建通过；待浏览器与真实 CRM 验收。
