---
kind: feature-spec
feature_id: FEAT-095
title: AI 应用壳层导航稳定性治理
status: in_implementation
owner_role: frontend-agent
task_ids: TASK-187
related_decisions: FEAT-090, FEAT-094
related_issues: none
updated_at: 2026-07-11T05:38:44Z
updated_by: MANAGER-001
---

# FEAT-095 - AI 应用壳层导航稳定性治理

## 背景

AI 应用主区域与一级侧栏之间存在额外画布间距；侧栏图标仍继承旧全局按钮 hover 位移，个人头像还显式执行缩放，鼠标经过时会造成页面抖动。AI 应用入口把“展开应用菜单”和“切换 AI 应用画布”耦合，导致用户仅想查看应用列表时主区域已经切换。悬浮菜单关闭按钮也没有完整复用公共裸图标原语，hover 时可能重新继承阴影套层。

## 交互设计

- AI 应用一级侧栏按钮只控制应用菜单开关，不改变当前 `workspaceTab` 和主区域内容。
- 用户点击菜单中的具体应用后，才设置活动应用、切换到 AI 应用主区域并关闭菜单。
- 当前已经位于 AI 应用页面时，一级按钮仍只负责打开/关闭菜单，不重置当前应用。
- AI 应用菜单可在会话工作台、客户会话、CRM、设置和个人简档上方浮出，关闭后底层画布保持原状。
- 菜单关闭使用 Lucide `X` 与公共 `cici-product-icon-button`，保留可访问名称，不使用文本字符和局部按钮外观。

## 布局与视觉

- AI 应用画布移除壳层外侧 padding，使主区域直接衔接一级侧栏和窗口边界；各应用自身负责内部内容留白。
- 一级侧栏所有按钮保持固定宽高；default、hover、focus、active 状态禁止 `transform`、缩放、尺寸、padding、border-width 和 box-shadow 变化。
- hover 只允许前景色或同尺寸背景色变化，transition 不得使用 `all`。
- 工作台概览筛选箭头使用标准 `ChevronDown` 图标，与文字通过 `inline-flex + align-items:center` 垂直居中。
- 壳层导航规则写入 `DESIGN.json`/`DESIGN.md`，后续一级导航与应用选择器必须复用。

## 验收标准

- AI 应用主容器左、上、右、下外侧 padding 均为 `0`，主区域与侧栏之间没有额外背景缝隙。
- 鼠标依次经过个人头像、会话、客户、AI 应用、CRM、设置和退出按钮，主内容区 `getBoundingClientRect()` 始终不变，所有按钮 computed `transform=none`。
- 从非 AI 应用画布点击“AI应用”后，菜单可见且当前画布、`workspaceTab`、主区域标识不变。
- 点击任一具体应用后才切换对应主区域，菜单关闭且所选项成为当前应用。
- 菜单关闭按钮默认和 hover 均 `box-shadow:none`、`transform:none`、无第二层边框。
- 两个概览筛选按钮的箭头图标与文字中心线对齐，无文本字符 `⌄`。
- 1920x960 桌面端无 document/body 外层滚动和控件重叠。

