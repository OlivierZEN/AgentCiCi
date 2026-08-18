---
kind: task-status
task_id: TASK-316
title: 应用中心在线接入指南
status: review
priority: high
owner_role: frontend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-193-internal-application-integration-guide.md
updated_at: 2026-08-18T04:09:38Z
updated_by: codex
---

# TASK-316 - 应用中心在线接入指南

## 目标

在运营平台内交付可在线查阅、可复制示例、可按章节定位的内部应用接入手册，并从应用中心关键路径提供上下文入口。

## 范围

- 新增接入指南页面和认证路由。
- 应用中心列表、应用详情和运行连接空态新增入口。
- 覆盖 Provider 契约、鉴权、连接、版本、依赖、发布、租户开通与运维排错。
- 新增内容和导航定向测试，执行前端全量测试、构建与本地环境验收。

## 当前进展

- [x] 核对 FEAT-191、Provider 连接和通用生命周期执行器契约。
- [x] 完成页面信息架构和内容边界设计。
- [x] 实现指南页面、路由和上下文入口。
- [x] 完成定向测试、全量测试、生产构建和独立桌面视觉检查。
- [x] 从本地 main 更新 `cici.localhost` 并完成正式路由运行验收。
- [x] 修复代码块全局 `pre` 样式覆盖和指南双滚动问题。
- [x] 发布智能体友好的公开 Markdown 地址并验证 MIME、安全响应头和内容门禁。
- [x] 使用已登录本地平台会话复核直接锚点、单滚动容器、代码对比度和 Markdown 入口。
- [x] 更新项目状态和测试证据。

## 完成证据

- 原始实现提交：`94f4e6bcbbd0`；初始入口与内容定向 3 文件/26 项通过。
- 修复提交：`1f1d816c`（可读性与 Markdown）、`4c368db3`（Markdown MIME）、`0cd88875`（单滚动容器与直接锚点）。
- 智能体地址：`/agent-docs/internal-applications/integration-guide.md` 返回 `200 text/markdown`、`nosniff`，共 391 行，不依赖 JavaScript 或登录态。
- 登录态视觉：浏览器外层滚动锁定为 0，运营主区域是唯一滚动容器；`#connection` 直接定位到目标章节；代码区为深棕底、暖白字、0px 内框。
- 最终前端 53 文件/293 项、production build、Nginx 配置校验和完整 stack verify 通过；frontend 为 `2.8.61-dev.0cd8887`、healthy/restart=0。
- 已随 UAT `2.8.61-beta.29 / d2abc9c463b3` 发布；HTML 与 Markdown 地址、MIME、安全响应头和公开 smoke 通过。生产未修改；真实 Provider 接入和租户开通不属于文档修复验收。

## 交付边界

- 只修改 `cc-agentcici`。
- UAT 技术发布已获用户授权并完成；生产环境不修改。
- 不在示例中保存真实环境地址或凭据。
