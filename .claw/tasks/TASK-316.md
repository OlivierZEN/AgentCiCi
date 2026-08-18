---
kind: task-status
task_id: TASK-316
title: 应用中心在线接入指南
status: in_progress
priority: high
owner_role: frontend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-193-internal-application-integration-guide.md
updated_at: 2026-08-18T02:37:22Z
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
- [ ] 从本地 main 更新 `cici.localhost` 并完成正式路由运行验收。
- [ ] 更新项目状态和测试证据。

## 交付边界

- 只修改 `cc-agentcici`。
- 不修改 UAT 或生产环境。
- 不在示例中保存真实环境地址或凭据。
