---
kind: task-status
task_id: TASK-302
feature_id: FEAT-183
integration_id: INT-019
status: in_progress
updated_at: 2026-08-13T11:15:21Z
updated_by: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-183-system-api-catalog.md
---

# TASK-302 - 运营端系统 API 目录

## 范围

- 建立 AgentCiCi 首批核心跨应用 API 的提供方目录。
- 聚合 Semattice 受治理目录投影，并在不可用时显式降级。
- 在能力治理中新增“系统 API”及 AgentCiCi、Semattice 子菜单。
- 实现概览、提供方列表、宽抽屉速览和独立调用文档页。

## 完成条件

- 目录读取受平台角色保护，业务 API 原鉴权和功能逻辑不变。
- 前后端定向测试与生产构建通过。
- 任务提交进入 AgentCiCi 本地 `main`，从该提交更新 `cici.localhost` 并回读运行指纹。
