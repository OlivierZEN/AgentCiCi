---
kind: task-status
task_id: TASK-186
title: 产品控件去框化与客户互动工作台全页治理
status: done
owner_role: frontend-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-094-customer-workbench-control-chrome-cleanup.md
assignment_path: .claw/assignments/TASK-186.yaml
updated_at: 2026-07-11T05:29:43Z
updated_by: MANAGER-001
---

# TASK-186 - 产品控件去框化与客户互动工作台全页治理

## 目标

移除模式切换的重复外框，从根因上清除工作台控件继承的阴影、圆角和位移套层，并形成后续新页面可复用的公共控件规则。

## 计划

1. 完成控件状态和 CSS 层叠根因检查。
2. 建立公共裸图标按钮、无外框模式切换规则并更新设计事实源。
3. 全页审查工作台按钮类型和 computed style，验证默认、hover、focus、展开和关闭状态。
4. 发布生产并验证 AgentCiCi 与 CloudCC iframe。

## 当前进展

- 已确认模式切换为外层容器和内部按钮双层框。
- 已确认图标按钮只做局部透明覆盖，未完整重置全局按钮 chrome。
- 已新增公共裸图标按钮和无外框模式切换原语，工作台全页按钮已阻断旧全局阴影和上浮。
- 已移除品牌标记、选中客户和活动 tab 的非必要内阴影；浮层与 modal 合理层级阴影保留。
- 57 项前端测试、生产构建、JSON 解析和 `git diff --check` 通过；本地浏览器因 localhost 没有有效登录态停在登录页，生产浏览器验收将在发布后完成。
- 已发布生产 `2.4.5`（提交 `b615cf417601`）；AgentCiCi 与 CloudCC iframe 中模式容器和 5 类图标按钮均为零套框、零阴影、零位移，全页按钮 `offenderCount=0`。
- 双入口无 document/root 外层溢出；技能 CLI 无预设组件校验 `issues=[]`；最近 60 秒前后端错误扫描为空。
