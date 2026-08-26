---
kind: task-status
task_id: TASK-326
feature_id: FEAT-198
integration_id: INT-025
status: done
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-26T04:18:24Z
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
- Semattice UAT 已运行 `1.0.7-beta.5 / 54f2ab93558f`；AgentCiCi 已运行 `2.8.66-beta.1 / 2c9d3821b458`，DevAutopilot 已运行 `1.0.5-beta.1 / 58253da87aa2`。
- 目标 AgentCiCi UAT SERVICE 身份签名调用模板应用端点返回 7 对象/87 字段、state=applied；历史 7×86 不兼容策略保持失败关闭。
- AgentCiCi 完整备份及 SHA-256 清单、PostgreSQL custom dump 恢复目录、Flyway V122、六容器 healthy/restart=0、Nginx、公开 smoke、匿名 401 JSON 和错误日志门禁通过；生产未修改。
- 用户已确认 UAT HUMAN 缺陷业务链路验收通过。生产按提供方优先顺序发布：Semattice `1.0.7 / 54f2ab93558f` 先上线并由 AgentCiCi 生产 SERVICE 回读 7×87 applied，随后 AgentCiCi `2.8.66 / e805c0ef7142` 上线；INT-025 完成。
