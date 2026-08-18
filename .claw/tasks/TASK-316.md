---
kind: task-status
task_id: TASK-316
title: 应用中心在线接入指南
status: review
priority: high
owner_role: frontend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-193-internal-application-integration-guide.md
updated_at: 2026-08-18T02:41:39Z
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
- [x] 更新项目状态和测试证据。

## 完成证据

- 实现提交：`94f4e6bcbbd0`。
- 前端定向 3 文件/26 项、全量 53 文件/293 项和 production build 通过。
- 本地独立桌面视觉检查通过目录、锚点、复制反馈和 console 门禁。
- `cc-local-stack ./stack up` 和完整 verify 通过；backend/frontend 均为 `2.8.61-dev.94f4e6b`、healthy、restart=0。
- 正式指南路由 200，部署资源回读指南标题、入口和发布前检查；浏览器保持运营平台登录边界。
- 远端、UAT、生产未修改；授权态最终视觉待平台管理员会话复核。

## 交付边界

- 只修改 `cc-agentcici`。
- 不修改 UAT 或生产环境。
- 不在示例中保存真实环境地址或凭据。
