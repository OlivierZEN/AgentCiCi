---
kind: task-status
task_id: TASK-311
feature_id: FEAT-190
integration_id: INT-024
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-17T03:24:24Z
updated_by: codex
---

# TASK-311 - DevAutopilot 可恢复租户开通

## 范围

- 将开通拆为持久化阶段并记录最后成功检查点。
- 外部 Semattice 调用失败后保存安全错误码和失败阶段。
- 重复 activate/reconcile 从未完成阶段继续，保持外部调用幂等。
- UI/API 可以区分 schema 未就绪、授权模板失败和一般依赖失败。

## 完成条件

- 失败状态不会随事务整体回滚丢失。
- 重试不会重复创建 PM Agent、SERVICE 或资源绑定。
- 定向测试覆盖每个阶段失败与恢复。
- UAT 运营开通成功，生产未修改。

## 当前证据

- 本地 `main@0c56f468b8f8` 已通过真实 PostgreSQL 16 的 V118→V119 升级、失败持久化与同键恢复回归，没有启用 Flyway `outOfOrder`。
- 本地开发测试环境已从该 `main` 构建并通过完整 `./stack verify`：AgentCiCi `2.8.61-dev.0c56f46`，V118/V119 均成功，backend/frontend healthy 且 restart=0。
- 已登录运营页面回读同一版本，现有租户 DevAutopilot 为“运行中 / 初始化已完成”；未为造失败场景重复修改已开通业务数据。
- 待按提供方先行顺序完成 Semattice UAT schema/release 门禁，再发布 AgentCiCi UAT 候选并执行真实 HUMAN 开通/重试。
