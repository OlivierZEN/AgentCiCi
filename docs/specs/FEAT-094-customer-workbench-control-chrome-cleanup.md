---
kind: feature-spec
feature_id: FEAT-094
title: 产品控件去框化与客户互动工作台全页治理
status: verified
owner_role: frontend-agent
task_ids: TASK-186
related_decisions: FEAT-093
related_issues: none
updated_at: 2026-07-11T05:29:43Z
updated_by: MANAGER-001
---

# FEAT-094 - 产品控件去框化与客户互动工作台全页治理

## 背景

客户互动工作台顶部模式切换同时存在外层分段容器边框和内层按钮外观，形成重复框线。提醒、AI 助理展开和关闭按钮虽然使用标准图标，但局部样式没有完整重置全局按钮的圆角、阴影、transform 和 appearance，导致图标下方出现不规则的按钮套层。该问题来自项目通用按钮基线与局部控件规则的职责冲突，不能只修截图中的三个按钮。

## 设计

- “新客户推进 / 老客户经营”保留双模式切换能力，移除外层容器的边框、底色、圆角和内边距。
- 两个模式按钮继续承担清晰的选中/未选中状态；控件只保留一层视觉，不使用外层卡片、阴影或内阴影。
- 客户提醒、AI 助理展开/恢复和关闭统一为裸图标按钮：默认无边框、无背景、无阴影、无 transform、无浏览器默认外观。
- hover 与键盘 focus 仅使用克制的暖金浅底和前景色变化，不能生成第二层边框或阴影。
- AI 助理展开态通过图标语义、`aria-pressed` 和前景色表达，不增加持续底板。
- 不改变提醒弹层、模式切换、AI 助理展开/关闭和 CRM 刷新的业务行为。
- 全页审查客户互动工作台中的图标工具、文本 tab、筛选、分页、主次操作和弹窗关闭按钮，确保每类控件只有一层视觉语义。
- 在公共样式中新增可复用的产品裸图标按钮与无外框模式切换基础类；新页面必须使用公共基础类，不再依赖带渐变、阴影和位移的无范围全局 `button` 外观。
- 将上述约束写入 `DESIGN.json` 与 `DESIGN.md`，作为以后新页面的实现和截图验收规则。

## 根因

- 模式切换外层 `.customer-workbench__mode-switch` 明确设置了 `border/background/padding`，其内部按钮又有独立圆角和选中底色，因此必然形成双层框。
- 全局 `button` 规则带有圆角、阴影和 hover 位移；工作台的 `.customer-workbench__icon-button` 与 `.customer-workbench__assistant-tools button` 只覆盖了部分属性，未重置 `box-shadow/transform/appearance/outline/border-radius` 的完整状态矩阵。

## 验收标准

- 模式切换外层不存在可见边框、背景、阴影和额外内边距。
- 默认、hover、focus、active、expanded 五种状态下，提醒、展开/恢复和关闭图标均只有单层点击热区，不出现不规则套框或阴影。
- 客户互动工作台所有原生按钮的 computed style 审查中，除明确的弹层/浮层容器外不得出现按钮 `box-shadow` 或 hover `transform`。
- 新增的公共裸图标按钮和无外框模式切换基础类具备完整 default/hover/focus/active/selected 状态重置，可被后续产品页面直接复用。
- 图标按钮保持至少 28px 点击区，图标为 16px，`aria-label` 和 tooltip 保留。
- AgentCiCi 主入口与 CloudCC CRM iframe 的桌面端截图均符合去框化效果，无新增外层滚动条、布局位移或控制重叠。

## 实现进展

- 公共 `cici-product-icon-button` 和 `cici-product-mode-switch` 已建立，并写入设计事实源。
- 客户互动工作台提醒、队列设置、工作台链接、助理展开/关闭和编辑弹窗关闭控件已接入公共原语。
- 工作台全页原生按钮已阻断旧全局 `box-shadow` 与 `translateY`；非浮层区域的剩余按钮阴影已清零。
- 57 项前端测试、Vite 生产构建、JSON 解析与 diff 检查通过；待生产发布和双入口截图验收。
- 已发布生产 `2.4.5`（提交 `b615cf417601`）。AgentCiCi 和 CloudCC iframe 的模式容器计算样式均为 `border: 0`、透明背景、`box-shadow: none`、`padding: 0`。
- 两个入口中的客户提醒、列表设置、工作台链接、助理展开和关闭按钮均为透明单层热区，计算样式无边框、阴影、渐变和位移。
- 两个入口全页按钮计算样式审计 `offenderCount=0`，无 document/root 外层溢出。截图：`output/playwright/task186-prod-agent-control-chrome.png`、`output/playwright/task186-prod-cloudcc-control-chrome.png`。
- CloudCC 实施专家技能无预设组件校验 `issues=[]`；携带预期组件名时仍因 customPage 不暴露版本数组产生已知 `stale_component_reference` warning，不影响同组件 ID 的真实 iframe 加载。
