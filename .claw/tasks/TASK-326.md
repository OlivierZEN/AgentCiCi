---
kind: task-status
task_id: TASK-326
feature_id: FEAT-198
integration_id: INT-025
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-20T02:05:34Z
updated_by: codex
---

# TASK-326 - 对齐 DevAutopilot 7×87 元数据基线

## 范围

- 将 AgentCiCi 对 Semattice `devautopilot.standard.v1` 的精确消费契约从 7×86 推进到 7×87。
- 更新初始化恢复幂等键并补充聚焦回归。
- 不实现历史 7×86 兼容，不修改业务记录，不放宽租户或 HMAC 边界。

## 完成条件

- 聚焦测试与 backend package 通过。
- 本地 main 与远端 main 一致，冻结 UAT 候选可追溯。
- Semattice 提供方、AgentCiCi 编排方、DevAutopilot 消费方按顺序发布并完成技术与真实 SERVICE 契约探测。

## 当前证据

- 精确消费校验和元数据补偿幂等键已推进到 7×87；聚焦单测 14 项、backend package 与 `git diff --check` 通过。
- 历史 7×86 结果有独立失败关闭回归，不实现双版本兼容。
- Semattice UAT 已运行 `1.0.7-beta.5 / 54f2ab93558f`；AgentCiCi/DevAutopilot 候选待发布。
