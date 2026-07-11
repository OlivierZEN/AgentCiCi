---
kind: feature-spec
feature_id: FEAT-096
title: 客户互动工作台标题与静态链接控件修复
status: ready
owner_role: frontend-agent
task_ids: TASK-188
related_decisions: FEAT-095
related_issues: none
updated_at: 2026-07-11T05:58:07Z
updated_by: MANAGER-001
---

# FEAT-096 - 客户互动工作台标题与静态链接控件修复

## 背景

平台内客户互动工作台隐藏了旧品牌面包屑后，顶部左侧没有保留应用身份，形成无意义空白。客户标题后的“复制客户工作台链接”按钮虽然已清除 transform 和阴影，但仍继承公共裸图标按钮的 hover 背景与颜色变化，鼠标经过时产生局部抖动感。

## 设计

- 顶部左侧增加应用级标题“客户互动工作台”，与右侧模式切换、CRM 状态和用户信息共用同一工具栏。
- 不恢复 AgentCiCi 品牌标识、AI 应用面包屑或额外说明，避免形成重复导航。
- 复制链接按钮使用标准 Link 图标，default、hover、focus、active 均保持相同尺寸、背景、颜色、padding、border、shadow 和 transform。
- 鼠标 hover 不产生背景、颜色、位移、缩放、阴影或 transition；键盘 `focus-visible` 保留静态 outline，保证可访问性。

## 验收标准

- 平台和 CRM 嵌入工作台顶部左侧均显示“客户互动工作台”，不与模式切换或客户队列重叠。
- 复制链接按钮 hover 前后 bounding rect、背景色、文字色、transform 和 box-shadow 完全一致。
- 点击复制链接仍写入包含 accountId/mode 的工作台 URL，并显示成功提示。
- 1920x960 页面无 document/body 外层溢出，工作台三栏尺寸不被标题改变。

