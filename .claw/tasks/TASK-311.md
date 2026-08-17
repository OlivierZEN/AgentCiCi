---
kind: task-status
task_id: TASK-311
feature_id: FEAT-190
integration_id: INT-024
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-17T03:10:00Z
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
