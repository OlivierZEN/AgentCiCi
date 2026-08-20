---
kind: feature-spec
feature_id: FEAT-198
title: DevAutopilot 缺陷验证模板消费契约
status: implemented
primary_project: agentcici
task_ids: TASK-326
related_integrations: INT-025
updated_at: 2026-08-20T02:03:46Z
updated_by: codex
---

# FEAT-198 - DevAutopilot 缺陷验证模板消费契约

## 问题

Semattice `devautopilot.standard.v1` 已为 `dev_defect` 增加可选 JSON 字段 `validation`，当前完整基线为 7 个对象、87 个字段。AgentCiCi 仍只接受 86 字段并复用 `shape-7x86` 幂等键，会让正式租户开通或初始化补偿在提供方成功后被消费方错误拒绝。

## 设计

- AgentCiCi 只接受当前 `7×87` 基线，不保留 `7×86` 双版本兼容。
- 元数据补偿幂等键推进为 `devautopilot.standard.v1:shape-7x87:<activationId>`，使本次基线变更具有独立请求身份。
- 继续要求模板版本、公司 ID、对象数、字段数和 applied/already_applied 状态全部匹配；不放宽 HMAC、租户或初始化门禁。

## 验收

1. 初始化 Saga 接受 Semattice 返回的 7 对象、87 字段。
2. 初始化补偿使用稳定的 `shape-7x87` 幂等键并持久化新 metadata version/digest。
3. 旧 6 对象或非 87 字段结果仍失败关闭。
4. Semattice 提供方先发布并完成真实 AgentCiCi SERVICE 身份模板调用，再发布 AgentCiCi 和 DevAutopilot 消费方。

## 回滚

应用代码可回滚到上一不可变 AgentCiCi 候选；已由 Semattice 正向发布的可选字段和 metadata version 不删除、不反向迁移。
