---
kind: feature-spec
id: FEAT-085
title: 客户互动工作台外层滚动与 CRM 主页按钮清理
status: active
owner: MANAGER-001
created_at: 2026-07-10T10:05:00+08:00
updated_at: 2026-07-10T10:05:00+08:00
---

# FEAT-085 - 客户互动工作台外层滚动与 CRM 主页按钮清理

## 背景

用户在 CloudCC CRM 内嵌客户互动工作台截图中指出两处生产体验问题：

- 中心客户详情头部的“打开 CRM 客户主页”按钮不应展示。
- 无论在 AgentCiCi 智能体平台端，还是在 CloudCC CRM 内嵌页面中，浏览器最右侧不应出现整页滚动条；只允许客户队列、中心内容、AI 对话等内部局部滚动。

## 范围

- 删除客户详情头部“打开 CRM 客户主页”按钮和对应的演示 notice 行为。
- 锁定客户互动工作台所在的应用页面外层高度与 overflow，确保根页面、AI 应用主面板、CRM 嵌入容器不产生最右侧整页滚动条。
- 更新 CloudCC `component-customer-workbench` 页面组件的 iframe 宿主高度策略，按组件距离视口顶部的剩余高度承载工作台，避免 CloudCC 顶部导航已占高时 iframe 继续使用整屏 `100vh` 撑出宿主页面滚动条。
- 保留内部局部滚动：客户队列、中心内容区、AI 客户助理对话区。
- 同时覆盖 `/app?aiApp=customer-workbench` 与 `/app?aiApp=customer-workbench&embed=crm`。

## 非目标

- 不改 CRM SSO、CloudCC 页面组件绑定、演示数据、AI 助理模型调用或 ASR 链路。
- 不新增移动端适配和移动端测试。
- 不改变 AgentCiCi 认证后产品视觉语言。

## 验收标准

- DOM 中不再出现“打开 CRM 客户主页”按钮文案。
- `.customer-workbench__voice` 和 `.customer-workbench__quick` 仍为 0。
- 普通智能体平台端工作台：`documentElement.scrollHeight <= innerHeight + 2`，`body.scrollHeight <= innerHeight + 2`，无最右侧页面滚动条。
- CRM embed 工作台：同样无最右侧页面滚动条。
- CloudCC 页面组件宿主：模拟 CRM 顶部导航占高后，组件高度应等于剩余视口高度，宿主页面无可见右侧滚动条。
- 内部局部滚动容器仍存在并可滚动或具备滚动能力。
- 前端构建、静态检查、桌面端 Playwright 截图检查通过。
- 生产发布后完成公网 smoke、认证后 browser smoke，并记录版本、备份和截图。
