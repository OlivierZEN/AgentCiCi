---
kind: feature-spec
feature_id: FEAT-126
title: 组织管理端全页面主题一致性治理
status: approved
created_at: 2026-07-22T00:30:00+08:00
owner_role: frontend-agent
source: user-feedback
---

# FEAT-126 - 组织管理端全页面主题一致性治理

## 目标与范围

组织管理员在 `/admin/*` 的页面主体、二级页面、模态框、抽屉、轻量菜单、折叠内容、详情区、表格、表单与操作反馈必须继承当前登录用户的受控主题。用户选择蓝色主题时，任何 Admin 表面不得回落到鎏金账房的暖象牙、香槟金或固定金色遮罩。

本次覆盖路由：知识库、业务本体、数据质量、工具、技能列表/新建/编辑、Agent 构建/详情/OpenAPI 文档、评测、集成、嵌入应用/详情、微信客服、运行观测、计费、用户和组织。保留 API、权限、表单字段、折叠逻辑、确认语义、数据和桌面布局；不新增移动端实现。

## 已知根因

`theme.css` 已为 `.admin-layout` 建立映射，但只覆盖了外壳、少量卡片和输入控件。更晚定义的 Admin 资源页样式仍含固定暖色遮罩、金色 focus ring、渐变、硬编码语义背景和模态阴影；`AdminToolsPage` 还有按类别内联的色值。它们绕过 `--theme-*` token，导致局部表面与当前主题不一致。

## 设计与实施原则

- Admin 属于高密度 CRUD 产品页：以文字层级、1px 结构线和不透明主题表面组织信息，不把每个区块卡片化。
- 所有中性色、强调、焦点、遮罩、成功、危险和警告色必须读取已存在的 `--theme-*` token。语义状态可保留“成功/危险/警告”的角色，但其具体颜色必须由当前主题提供。
- 选择器、确认、编辑、导入预览和发布弹框保留 modal 交互，必须使用当前主题遮罩、不透明面板、主题结构线、主题按钮和可见的 focus-visible 状态。
- 抽屉、详情/折叠区、表格/列表行和行操作菜单不得回退硬编码白底、金色边线、阴影或渐变；展开与选中以主题文字、直线结构和必要的细边界表达。
- `AdminToolsPage` 的类别识别只保留图标/文字语义，移除会覆盖用户主题的内联色块和渐变，改由共享主题样式承载。
- 仅修改主题覆盖层与必要页面 class/内联实现，避免逐页重写业务逻辑；在每条路由及每种浮层形态存在的情况下做静态清单核对。

## 验收标准

1. 蓝色主题下，全部 `/admin/*` 主体、表单、表格、详情/折叠区、模态框、抽屉和轻量菜单均从当前主题变量取色，不出现鎏金账房固定色。
2. 其他七种受控主题沿用同一结构和组件尺寸，并且 Galaxy 暗色中的文字、边框、按钮与语义状态仍可读。
3. 工具页类别样式不再通过内联渐变/色值覆盖主题；功能筛选、编辑、启停和删除行为不变。
4. 各类控件都包含 default、hover、focus-visible、active、disabled、loading/error/empty（适用时）状态；关闭操作为无边框裸图标。
5. Admin 路由清单、共享弹窗/抽屉/折叠选择器、静态主题契约、前端测试与生产构建均通过。已登录桌面浏览器验收须逐页记录，无法获得登录态时明确记录为待验收，不得伪造。

## 实施记录

- 共享主题层已将 Admin 弹窗遮罩、模型/知识库弹窗、组织与用户弹窗、技能发布与轻量行菜单、业务本体工作台、运维/观测、嵌入应用及计费页面映射为当前 `--theme-*` 语义色。
- 技能编辑页在 `.admin-page` 内重绑 `--ledger-*` 为当前主题，避免二级编辑页面回退到鎏金账房默认值。
- 工具页已移除类别图标和标签的内联渐变/色值，保留图标、名称、风险与业务行为；视觉由主题层统一提供。
- 本地授权检查、11 项主题契约、生产构建和 diff 检查已通过。由于当前会话没有管理员登录态，蓝色主题下的逐页桌面视觉验收待有权限的账号补录。

## 路由检查清单

- `/admin/kb`、`/admin/ontology`、`/admin/data-quality`
- `/admin/tools`、`/admin/skills`、`/admin/skills/new`、`/admin/skills/:skillId/edit`
- `/admin/agent-builder`、`/admin/agent-builder/:agentId`、`/admin/agent-builder/:agentId/openapi-docs`
- `/admin/evaluation`、`/admin/integrations`
- `/admin/embed-apps`、`/admin/embed-apps/:appCode`、`/admin/channels/wechat-kf`
- `/admin/ops`、`/admin/billing`、`/admin/users`、`/admin/organization`
