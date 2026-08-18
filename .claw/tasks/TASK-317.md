---
kind: task
id: TASK-317
title: 服务端 UUID 会话身份与历史完整性修复
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
spec_path: docs/specs/FEAT-194-server-owned-chat-session-identity.md
created_at: 2026-08-18T10:30:00+08:00
updated_at: 2026-08-18T11:20:00+08:00
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

## 完成证据

- 租户 `org0gtwzqvxell4gly8s` 的 Trace 与消息使用 `workbench:devautopilot-pm`，但该全局会话主键归属另一租户，导致列表为 0。
- 相同稳定 ID 已被三个租户写入消息/Trace，证明客户端业务键不能作为全局主键。
- 实现提交 `0b34fb65` 已进入本地 `main`；后续本地主线 `0cd88875` 包含该提交。
- 后端相关 5 个测试类、`mvn -q -DskipTests package` 与 test compile 通过；独立 Spring 集成测试因本机未提供 `agentcici_test` 数据库而未执行，改由真实本地栈验证迁移和租户约束。
- 前端全量 53 个测试文件、292 项和 production build 通过；`git diff --check` 通过。
- V122 在本地数据库成功执行，清空测试会话并建立 UUID 检查、渠道/可见范围/source key 约束及三项 `(session_id, company_id)` 复合外键；无旧 `workbench:*` 会话或孤儿消息、状态、附件。
- `org0gtwzqvxell4gly8s / CC DevAutopilot1` 登录态页面自动创建首个会话，点击“新对话”创建第二个会话，刷新后历史仍显示 2 条；数据库回读为两个不同 UUID，均为 `web / USER / source_key=NULL`。
- 本地 backend/frontend 运行 `2.8.61-dev.0cd8887`，均 healthy、restart=0；完整 `cc-local-stack ./stack verify` 通过。UAT、生产未修改。

## 下一步

等待用户确认本地会话历史体验；UAT、生产仅在另行授权后部署，届时会按同一 V122 策略清空测试会话。
