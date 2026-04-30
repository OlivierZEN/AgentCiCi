---
updated_at: 2026-04-30T07:51:29Z
status: completed
feature_id: FEAT-011
owner_role: frontend-product-platform
---

# FEAT-011 Platform Console Visual Refresh

## Goal

- 在不改动业务功能的前提下，整体重构 `/platform/login` 与 `/platform/*` 的前端页面样式、信息层级与工作台结构。
- 让平台运营控制面从当前偏重深色、装饰性较强的风格，切换为更接近 Stripe Dashboard 的浅色、克制、紧凑、可信赖控制台体验。
- 为后续平台治理页面继续扩展提供统一的视觉语言和布局骨架。

## Non-goals

- 不新增、删除或改写任何平台业务能力。
- 不改动 `/admin/*`、助手工作台或后端接口语义。
- 不进行数据模型、权限、API、交互流程的产品级重设计。

## User And Context

- 核心用户是平台运营人员。
- 使用环境是白天办公场景下的高频内部工作台。
- 用户状态通常是连续处理多项治理任务，需要快速扫读状态、切换列表与详情、保存配置并回查审计。

## Design Direction

- Register: `product`
- Color strategy: `Restrained`
- Theme scene sentence: 平台运营人员在白天办公环境下连续处理技能版本、工具治理和审计排查，需要一个高对比、低噪声、长时间使用也不疲劳的浅色控制台。
- Anchor references:
  - Stripe Dashboard 的清晰克制
  - Linear 的密度控制
  - 企业后台里的 ledger/workspace 语言
- Anti-goals:
  - 不要花哨渐变
  - 不要大面积深色
  - 不要营销感太强
  - 不要玻璃拟态
  - 不要看起来像 AI 生成模板

## Scope

### In Scope

- `/platform/login`
- `/platform`
- `/platform/skills`
- `/platform/tools`
- `/platform/audit`
- 平台侧 shell、导航、页头、数据表、工作区面板、表单控件、内联提示、统计块、响应式布局

### Out Of Scope

- 新页面、新导航项、新数据字段
- 后端返回结构调整
- 与平台控制面无关的全局视觉改造

## Layout Strategy

- 整体采用浅色控制台骨架：左侧紧凑导航，右侧主工作区。
- 概览页使用“页头摘要 + 事实面板 + 简洁数据表”的结构，而不是大面积展示型视觉。
- 技能页与工具页统一成“列表工作台 + 详情编辑面板”的双栏结构，突出选中项和当前工作上下文。
- 审计页强化时间、角色、事件、资源、详情的表格可扫读性。
- 登录页延续控制台语言，避免营销化 hero。

## Implementation Plan

1. 建立 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 作为本次样式重构的设计事实源。
2. 重构 `PlatformLogin`、`PlatformShell`、`PlatformHomePage` 的结构与文案层级。
3. 统一平台页的页头、工作区、统计块、表格、表单、提示条与按钮结构。
4. 用新的浅色受限主题替换当前 `.platform-*` 深色主题，同时保持 `/admin/*` 现有样式不被误伤。
5. 执行 `frontend npm run build` 验证前端编译通过。

## Acceptance Criteria

- `/platform/login + /platform/*` 全部页面改为浅色、克制、紧凑的控制台风格。
- 页面不再使用当前深色渐变、玻璃化、重阴影和装饰性背景。
- 主要页面具备统一的导航、页头、表格、表单与消息反馈语言。
- 技能页和工具页在桌面端保持稳定双栏工作台体验，小屏下自动堆叠。
- 现有业务功能、数据绑定和交互行为不发生回归。
- `frontend npm run build` 通过。

## Risks

- 当前平台页使用深色 scoped 主题，替换时要避免误伤 `/admin/*` 的共用类名。
- 技能页结构复杂，若只改皮肤不补结构层次，容易仍显得杂乱。
- 仓库当前存在大量未提交变更，本次改动需严格收敛在平台前端与状态文档。

## Verification

- 以 `frontend npm run build` 作为本轮已验证结果。
- 如需后续人工验证，优先检查登录、概览、技能页双栏、工具页双栏和审计表格的桌面/窄屏表现。

## Delivery Notes

- 已完成 `PlatformLogin`、`PlatformShell`、`PlatformHomePage`、`PlatformSkillsPage`、`PlatformToolsPage`、`PlatformAuditPage` 的结构与视觉重构。
- 已完成 `.platform-*` 受限主题切换：从深色装饰性风格改为浅色、克制、紧凑的控制台语言。
- 本轮未改动业务逻辑、数据字段、接口路径或功能流程。
