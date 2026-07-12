---
kind: task-status
task_id: TASK-194
title: 全量客户名称搜索与产品输入焦点治理
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-101-global-customer-search-and-field-focus.md
assignment_path: .claw/assignments/TASK-194.yaml
updated_at: 2026-07-12T03:33:00Z
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

## 完成结果

- 已实现权限范围内 Account 名称全局搜索、搜索表达式转义和缓存外客户按需详情投影。
- 已实现客户搜索单层 `focus-within` 与全页面文本字段无阴影焦点守卫。
- 搜索结果会按客户真实分类自动切换“新客户推进”或“老客户经营”，避免详情语义错位。
- 后端 11 项相关测试、前端 60 项测试及生产构建通过。
- 已发布生产 `2.5.6 / 12c766bed77d`；真实大数据组织按“青岛海信商用显示”命中缓存范围外客户，搜索约 0.76 秒、详情约 0.22 秒，页面自动切换到老客户经营且无错误提示。
- 浏览器验证输入本体无边框、无阴影、透明背景，外层仅保留一个 1px 聚焦边框；截图见 `output/playwright/task194-prod-global-search-existing-mode-2.5.6.png`。
