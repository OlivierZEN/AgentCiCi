---
kind: feature-spec
feature_id: FEAT-012
title: Project design governance with impeccable
status: completed
owner_role: design-governance
task_ids: TASK-027
related_decisions: DEC-023
related_issues: none
updated_at: 2026-04-30T11:54:33Z
updated_by: ai
---

# FEAT-012 - Project Design Governance With Impeccable

## 背景与目标

- 当前仓库已经有 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 和平台控制面视觉升级成果，但“页面设计必须如何开始、如何落文、如何约束例外”还没有被写成项目级规则。
- 用户要求把 `impeccable` 固化为本项目的开发规范，让后续所有页面设计都遵循同一套设计方法、设计事实源和禁用模式。
- 本次交付目标是把这套规则落到项目说明、代理指令、状态文档和决策记录中，确保后续会话自动继承。

## 范围

### In Scope

- 将 `impeccable` 设为页面分析、设计、改版、评审、润色和 UI 实现的强制技能。
- 明确根 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 是项目级设计事实源。
- 明确 `/`、`/admin/*`、`/platform/*` 默认都按 `product` register 处理。
- 固化默认视觉基线 `鎏金账房` 的适用范围、共享原则和 route-level tuning。
- 规定何时必须补 spec、何时必须先做 `shape`、何时允许例外。

### Out Of Scope

- 不改任何业务逻辑、接口、权限或数据模型。
- 不在本任务中直接重做助手端、管理端或平台端页面实现。
- 不定义新的品牌页视觉体系；若未来需要，必须另起 shape 与 spec。

## 用户场景

- AI 代理在后续实现新页面或改版时，需要知道从哪里读设计上下文、哪些页面算产品面、哪些模式被明确禁止。
- 人类开发者需要一个简短、稳定、可检索的入口，避免“知道有设计要求，但不知道以哪份文档为准”。
- 接手者需要能区分“共享基线”和“允许按路由微调”的边界，防止 assistant、admin、platform 三套风格越走越散。

## 现状与约束

- 仓库当前已经采用 `.claw/` 状态协议，且 `README.md`、`AGENTS.md`、`docs/specs/` 都是长期可追溯载体。
- 现有 `PRODUCT.md` / `DESIGN.md` 最初更偏 `/platform/*` 语境，若不升级到项目级，很容易误导后续页面任务。
- 规范必须与 `cc-aidev-guidelines-common` 兼容，因此需要同时更新任务卡、当前状态和决策记录。
- 由于仓库工作区较脏，本任务只能收敛在规范文档，不回退或混改已有业务代码。

## 方案设计

- 在 `AGENTS.md` 中写入项目级强约束，让后续代理把 `impeccable` 视为页面工作的默认入口。
- 在 `README.md` 中补充面向开发者的简短设计治理说明，降低人类协作者的进入成本。
- 将根 `PRODUCT.md` 升级为全项目认证产品面的战略上下文，覆盖 assistant、admin、platform 三类用户与目的。
- 将根 `DESIGN.md` / `DESIGN.json` 升级为项目级产品面设计基线，并明确 route-level tuning 与 brand exception rule。
- 在 `.claw/decisions.md` 中记录项目决策，在 `.claw/task-board.md` 和 `.claw/current-status.md` 中记录本轮完成事实。

## 接口与数据影响

- 无 API、数据库、消息或配置结构变更。
- 影响的是项目协作协议与设计治理入口，不影响运行时行为。

## 任务拆分

- `TASK-027`
- 责任角色：`design-governance`
- 依赖：既有 `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 已存在，且 `impeccable` 上下文可加载。

## 验收标准

- `AGENTS.md` 明确规定 `impeccable` 是页面设计强制技能。
- `README.md` 提供简洁的人类可读设计治理说明。
- 根 `PRODUCT.md` 明确 `/`、`/admin/*`、`/platform/*` 的项目级产品上下文与共享设计原则。
- 根 `DESIGN.md` / `DESIGN.json` 明确共享基线、route-level tuning 和例外机制。
- `.claw/decisions.md`、`.claw/task-board.md`、`.claw/current-status.md` 记录了本轮已完成事实。

## 风险与回滚

- 风险：如果把共享基线写得过窄，会继续把未来页面限制在平台页语境；如果写得过宽，会抹平不同产品面的任务差异。
- 控制方式：以 `product register + 共享基线 + route-level tuning + shape exception` 的组合来平衡一致性与差异性。
- 回滚方式：若未来确认某类页面不应继承当前基线，应先新建 spec 和 shape brief，再有针对性调整 `PRODUCT.md` / `DESIGN.md`，而不是在代码层默默分叉。

## 实现进展

- 当前状态：completed
- 已完成项：
  - `AGENTS.md`、`README.md` 的入口规范已补齐。
  - `PRODUCT.md`、`DESIGN.md`、`DESIGN.json` 已升级为项目级产品面设计事实源。
  - `DEC-023`、`TASK-027`、`current-status` 已同步。
- 未完成项：
  - 无代码层改造，本轮只完成治理落文。

## 交接说明

- 下一位接手者先看 `AGENTS.md`、`PRODUCT.md`、`DESIGN.md`。
- 如果接下来要做具体页面改版，先按 `impeccable` 预检加载上下文，再决定是否需要单独 `shape`。
- 如果要引入品牌页、活动页或显著不同的视觉方向，不要直接继承当前基线，先新建 spec 并取得用户确认。
