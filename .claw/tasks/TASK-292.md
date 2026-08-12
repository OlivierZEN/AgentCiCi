---
kind: task-status
task_id: TASK-292
status: in_progress
updated_at: 2026-08-12T12:40:22Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
spec_path: docs/specs/FEAT-176-platform-managed-web-tools.md
depends_on: none
---

# TASK-292 - 平台联网搜索与网页抓取集成

## 范围

- 两张平台配置卡、密钥加密、草稿校验和连接检测。
- Responses API 联网搜索/网页抓取客户端与两个可治理内置工具。
- 工具目录、Agent/Skill 授权和运行时分派。
- 定向测试、完整前端测试、构建、本地 main 归并与开发环境更新。

## 完成条件

- 满足 `FEAT-176` 验收标准且 Tavily 行为不回归。
- 不提交真实凭据，不修改历史迁移，不夹带主工作树改动。
- 本地开发环境只从 AgentCiCi 本地 `main` 的明确提交构建。

## 当前进展

- 已核对官方协议：搜索只声明 `web_search`；抓取必须同时声明 `web_search` 与 `web_extractor`。
- 实现与测试进行中。
