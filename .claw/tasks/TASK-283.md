---
kind: task-status
task_id: TASK-283
status: review
updated_at: 2026-08-11T15:19:00+08:00
updated_by: codex
assignee: codex
owner_role: integration-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-171-user-friendly-delivery-intake.md
integration_id: INT-009
---

# TASK-283 - 普通用户研发事项受理与全栈开发者交接

## 范围

- 主动识别需求、缺陷或变更，不要求用户选择专业对象。
- 保留原始描述，记录产品经理整理、用户补充、确认和来源会话。
- 支持从最近有效草案恢复简短确认，并以可信回执写入 Semattice。
- 缺陷从当前租户 active 开发者池分派全栈开发者，停用主体失败关闭。

## 完成条件

- FEAT-171 的分类、澄清、确认、取消、原话校验、开发者分派和兼容性测试通过。
- 流式与非流式对话共用同一服务端协议。
- Semattice 回读与对话回执一致，DevAutopilot 可展示完整 intake 交接包。

## 发布边界

- 先完成本地实现与验证；未获得新的 UAT 或生产发布授权前不发布运行环境。

## 实施与验证

- 已实现需求/缺陷/变更主动分类、原始消息逐字校验、不可见 intake 草案、短确认恢复和 Semattice 写后回读门禁。
- 缺陷只从当前租户 DevAutopilot 应用的 active 全栈开发者池稳定分派；没有可用开发者时保留待分配。
- 相关 Maven 测试通过；后端全量测试因本地 PostgreSQL 不可连接而停在既有 Hikari 重试，未声明全量通过。
- 待 UAT 真实对话、Semattice 实际记录回读和停用开发者负例验收后转 done。
