---
kind: feature-spec
feature_id: FEAT-033
title: Assistant AI Apps Workspace
status: implemented
owner_role: frontend-product-assistant
task_ids: TASK-097
related_decisions: FEAT-029, FEAT-032
related_issues: none
updated_at: 2026-05-14T13:28:00Z
updated_by: ai
---

# FEAT-033 - 助手工作台 AI 应用入口

## 背景与目标

助手工作台左侧 rail 当前已有会话工作台、客户会话和 CRM 系统入口。用户要求在同一区域新增“AI应用”菜单，并在点击后让右侧主页面分为两栏：

- 左侧窄栏展示 AI 应用卡片列表。
- 右侧主区域展示所选 AI 应用的主页面。
- “AI 听记”作为第一个内置 AI 应用出现在卡片列表第一项。

目标是把已经存在的 FEAT-029 AI 听记能力从“只能通过聊天触发抽屉”扩展为可直接进入的产品页面入口，同时保持原会话触发入口不变。

## 范围

### In Scope

- `/` 左侧 rail 新增“AI应用”菜单。
- 新增助手侧 `aiApps` 工作区。
- AI 应用工作区使用双栏结构，左侧为紧凑应用列表，右侧为当前应用主页面。
- 第一项内置应用为“AI 听记”，复用现有 `MeetingMinutesPanel`、ASR、发言人编辑、纪要生成和状态提示。
- AI 听记主页面可直接点击开始听记，不需要先在聊天框输入触发词。
- UI 继续遵循 `鎏金账房` product register：暖象牙表面、紧凑密度、墨色文字、香槟金结构线。

### Out Of Scope

- 本轮不接入后端 AI 应用目录 API，先使用前端内置应用元数据。
- 本轮不新增 AI 应用安装、排序、权限配置或应用市场。
- 本轮不改变 FEAT-032 的嵌入式智能应用 admin 管理入口。
- 本轮不新增写回 CRM 的内部工作台确认流，CRM 写回仍在嵌入场景中处理。

## 交互设计

- rail 中“AI应用”位于 CRM 系统附近，作为同层工作区入口。
- 左侧应用列表使用真实卡片列表，因为它是独立应用选择器；卡片内保持 13px 主文字与 11-12px 辅助文字。
- 选中态使用金色文字、细线和轻微边界强调，避免面板内 hover 背景块、行阴影或伪按钮样式。
- 右侧 AI 听记主页面复用共享会议面板，但作为页面内容常驻展示，不显示关闭按钮。
- 主页面顶部只保留任务必要信息和“开始听记”主操作，不增加营销式说明或大号指标。

## 验收标准

- 点击左侧 rail 的“AI应用”后，主区域切换为两栏布局。
- 左侧卡片列表第一项为“AI 听记”，并显示内置状态。
- 右侧显示 AI 听记主页面，未开始时可读、可点击开始听记。
- 桌面和移动视口均无横向溢出、文字遮挡或内层卡片堆叠。
- `frontend npm run build` 通过；桌面和移动截图完成视觉复核。

## 实现记录

- `frontend/src/assistant/AssistantApp.tsx` 新增 `aiApps` 工作区、rail “AI应用”按钮、内置 AI 应用元数据和 AI 听记页面入口。
- AI 听记主页面复用 `MeetingMinutesPanel` 与现有 ASR/summary 状态；从 AI 应用页点击开始听记不会写入工作台聊天历史，原聊天触发抽屉入口保持不变。
- `frontend/src/assistant/cici-ui.css` 新增 AI 应用双栏布局、应用卡片列表、主页面和移动端折叠样式；749px 宽视口仍保持左窄栏 + 右主页面，390px 移动视口改为顶部应用列表 + 主页面。
- 截图：`output/playwright/assistant-ai-apps-desktop.png`、`output/playwright/assistant-ai-apps-mobile.png`。
