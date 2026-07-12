---
kind: feature-spec
feature_id: FEAT-101
title: 全量客户名称搜索与产品输入焦点治理
status: ready
owner_role: fullstack-agent
task_ids: TASK-194
related_decisions: FEAT-081,FEAT-100
related_issues: none
updated_at: 2026-07-12T03:05:00Z
updated_by: MANAGER-001
---

# FEAT-101 - 全量客户名称搜索与产品输入焦点治理

## 问题

- 当前客户搜索只在已同步队列中本地匹配，且先应用新客户/老客户模式与标签筛选；被分到另一模式或超出 10,000 条缓存上限的 CRM 客户无法命中。
- 全局 `input:focus` 阴影叠加在产品组件自身边框内，形成截图中的双层蓝色焦点框；多个认证后页面存在同源问题。

## 功能设计

- 输入客户名称后进入全局搜索态，通过当前 AgentCiCi 用户映射的 CloudCC Token 调用 Account `pageQuery`，按 `name like` 查询当前用户全部可见客户。
- 搜索态不应用新客户/老客户模式及队列标签筛选；返回结果携带 `searchScope=ALL_VISIBLE_ACCOUNTS`，界面明确显示“全部客户搜索结果”。
- 已在投影缓存中的客户复用完整队列视图；缓存外命中项使用中性搜索视图。用户选中缓存外客户时，按 Account ID 权限查询客户及关联对象，合并到当前用户投影后再加载详情。
- 搜索表达式必须转义单引号并限制长度，不能把用户输入直接拼成可注入条件；仍严格使用当前用户 CloudCC 数据权限。
- 客户名为空时恢复当前模式、筛选、排序和分页行为。

## 视觉设计

- 客户搜索框仅保留一层外框；内部原生 input 始终透明、无边框、无阴影、无浏览器 outline。
- 外层搜索容器通过 `:focus-within` 改变现有 1px 边框颜色，不新增边框宽度、阴影或布局尺寸。
- 在认证后助手、AI 应用、CRM、管理端和平台端产品壳层增加共享输入焦点守卫：input/select/textarea 的 focus/focus-visible 不产生 box-shadow 或额外 outline；标准字段仍通过自身单层边框色表达键盘焦点。

## 验收标准

- 在任一新客户或老客户页面按完整或部分客户名称，都能命中当前用户 CloudCC 中的对应 Account，不受当前模式和筛选限制。
- 超出本地投影缓存的搜索结果可被选中并打开详情，且不越过当前用户 CRM 权限。
- 清空搜索词后恢复原客户队列及计数。
- 客户搜索框聚焦前后几何尺寸一致，只存在一层边框；认证后产品页面的文本输入控件不再出现第二层 focus 阴影框。
- 后端注入/转义、全局搜索、缓存外详情加载和现有队列行为有自动化测试；桌面端浏览器完成 focus 与搜索交互检查。

