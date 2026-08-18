---
kind: task
id: TASK-317
title: 服务端 UUID 会话身份与历史完整性修复
status: in_progress
priority: critical
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-194-server-owned-chat-session-identity.md
created_at: 2026-08-18T10:30:00+08:00
updated_at: 2026-08-18T10:30:00+08:00
---

# TASK-317

## 范围

- 新增服务端 Web 会话创建 API 与全局 UUID 身份。
- 将外部渠道业务键与内部会话 UUID 分离。
- 所有会话写入、读取、删除、列表和实时事件执行租户/所有者范围校验。
- 清空测试历史并增加数据库身份、唯一性和引用约束。
- 前端移除 `workbench:<agent>` 持久化主键，改为服务端创建、按 agent/channel 归属。
- 完成后端/前端回归、本地 main 归并和 `cici.localhost` 全栈验收。

## 非范围

- 不迁移或恢复 DEV、UAT、生产中的既有测试账号会话历史。
- 不修改 Semattice、DevAutopilot 或父仓业务源码。
- 不发布 UAT 或生产。

## 当前证据

- 租户 `org0gtwzqvxell4gly8s` 的 Trace 与消息使用 `workbench:devautopilot-pm`，但该全局会话主键归属另一租户，导致列表为 0。
- 相同稳定 ID 已被三个租户写入消息/Trace，证明客户端业务键不能作为全局主键。

## 下一步

实现 V122、后端身份服务/API 与前端会话创建链路，并补充跨租户回归。
