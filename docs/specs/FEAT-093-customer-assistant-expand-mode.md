---
kind: feature-spec
feature_id: FEAT-093
title: 客户互动工作台 AI 助理展开模式
status: production
owner_role: frontend-agent
task_ids: TASK-185
related_decisions: FEAT-092
related_issues: none
updated_at: 2026-07-11T04:35:00Z
updated_by: MANAGER-001
---

# FEAT-093 - 客户互动工作台 AI 助理展开模式

## 背景

AI 客户助理标题栏的固定按钮只有切换客户时自动关闭这一弱行为，固定与未固定的视觉和日常使用差异不明显。用户需要类似 IDE 右侧面板的展开/恢复能力，以便阅读长回复，同时保持中间客户详情区宽度稳定。

## 交互设计

- 移除固定/取消固定按钮及相关状态和切换客户自动关闭逻辑。
- 在关闭按钮左侧增加标准面板展开按钮，使用 `PanelRightOpen/PanelRightClose` 图标和明确 tooltip。
- 默认态为三栏：客户队列、客户详情、AI 助理。
- 展开态保持三轨网格：客户队列轨道滑动收缩为 0，AI 助理轨道增加同等宽度，中间客户详情轨道宽度不变。
- 展开时客户队列不可见、不可聚焦、不可点击；恢复时反向滑回。
- 关闭 AI 助理后回到无助理布局；从客户详情区重新打开时默认恢复三栏，不继承展开态。
- 不改变 AI 对话、CRM 数据、客户选择、语音输入和消息滚动逻辑。

## 视觉与可访问性

- 使用 `lucide-react` 标准 `PanelRightOpen/PanelRightClose` 图标，不绘制自定义 SVG。
- 按钮提供 `aria-label`、`title`、`aria-pressed`；展开态按钮有克制的激活色，不使用阴影。
- 网格轨道、队列透明度和水平位移使用 220-260ms ease 动画；遵循 `prefers-reduced-motion`。

## 验收标准

- 页面不存在固定/取消固定按钮和 Pin 图标。
- 展开后客户队列宽度为 0、不可见且不可交互，AI 助理宽度增加，中间客户详情宽度与展开前误差不超过 1px。
- 恢复后三栏尺寸回到展开前数值，客户列表状态和当前客户不丢失。
- 关闭并重新打开 AI 助理后为默认三栏。
- 712x725 与 1920x960 桌面视口无 document/body 外层溢出；对话区继续内部滚动。
- AgentCiCi 入口和 CloudCC CRM iframe 均通过真实浏览器点击、尺寸测量和截图验收。

## 实现进展

- 已移除 Pin 图标、pinned 状态和切换客户自动关闭行为。
- 已实现标准面板展开/恢复按钮、三轨等量宽度转移、队列 inert/aria-hidden 和 reduced-motion 处理。
- 本地 1920x960：展开前后中间区均为 `1214px`；队列 `307px -> 0`，助理 `317px -> 624px`；恢复尺寸完全一致，关闭再打开回到默认三栏。
- 前端 12 个测试文件、57 项测试和生产构建通过；待生产发布与 CloudCC iframe 复验。
- 已发布生产 `2.4.4`（提交 `f69d2191ed3b`）。AgentCiCi 中间区展开前后均 `1214px`；CloudCC iframe 中间区均 `1213px`，助理 `327px -> 653px`，恢复后尺寸完全一致。
- 真实 CRM iframe、公开入口、技能 CLI 注入页验证和静置日志均通过。
