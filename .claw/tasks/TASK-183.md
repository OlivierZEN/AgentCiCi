---
kind: task-status
task_id: TASK-183
title: 客户互动工作台界面规范化与流式助理
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-092-customer-workbench-ui-streaming.md
assignment_path: .claw/assignments/TASK-183.yaml
updated_at: 2026-07-11T03:40:00Z
updated_by: MANAGER-001
---

# TASK-183 - 客户互动工作台界面规范化与流式助理

## 当前目标

依据用户三张生产截图，规范队列工具和连接状态，移除无价值入口，统一标准图标，并把 AI 客户助理改为即时过程反馈、SSE 流式输出和安全 Markdown 渲染。

## 当前进展

- 已确认“演示模式”来自当前 AgentCiCi 用户没有 CloudCC 会话，后端使用 `DEMO_FALLBACK`。
- 已确认通知、复制链接、固定和关闭有真实行为；帮助、信息图标和列表密度按钮不具备独立业务价值。
- 已确认平台现有 `ChatOrchestratorService.chatStream` 可复用，避免新建模型调用链。
- 已完成 SSE 接口、前端事件解析、即时状态、流式 Markdown、输入清空和自动滚底；旧阻塞接口保留兼容。
- 已完成 Lucide 标准图标、单一列表设置入口、明确演示数据状态和无效入口移除。
- 本地浏览器发现并修复设置展开态网格行数错误；1920x960 复验无外层溢出，提醒、固定、关闭/恢复均可操作，控制台错误为 0。
- 前端 56 项测试与生产构建、后端聚焦测试与编译、`git diff --check` 均通过。

## 执行计划

1. 完成任务授权并提交任务分配。
2. 新增客户助理 SSE 适配和测试。
3. 接入标准图标、队列设置、连接状态和 Markdown 流式 UI。
4. 已完成前后端测试和本地桌面浏览器验收。
5. 正在按运行手册发布生产版本并复验 AgentCiCi 与真实 CRM iframe。

## 验收门

- 队列设置不遮挡，所有可见图标语义明确且有真实行为。
- 演示数据状态可理解，CRM 入口不再显示无必要主页按钮。
- 提问后立即显示处理状态，回复按增量输出并持续滚到最新内容。
- Markdown 以友好结构渲染，不显示原始符号。
- 真实 CRM 嵌入页和 AgentCiCi 入口均通过桌面验收。

## 约束

- CloudCC 注入验证必须使用 `cc-customization-expert-msapi`。
- 不改变 CRM 权限、写回和数据事实源规则。
- 不新增移动端适配，不提交凭证或生产会话信息。
