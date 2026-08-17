---
kind: feature-spec
feature_id: FEAT-190
title: DevAutopilot 可恢复租户开通 Saga
status: implementing
primary_project: agentcici
task_ids: TASK-311
related_integrations: INT-024
updated_at: 2026-08-17T03:10:00Z
updated_by: codex
---

# FEAT-190 - DevAutopilot 可恢复租户开通 Saga

## 问题

现有 `activate` 在一个本地事务中依次调用 Semattice 元数据、创建 PM 资源、同步 Principal 和应用授权模板。外部调用已经成功但后续失败时，本地事务回滚，失败阶段与检查点无法持久化；用户只看到通用 502，重试也无法判断应从哪一步恢复。

## 状态机

`PROVISIONING → METADATA_READY → PRODUCT_MANAGER_READY → PRINCIPALS_READY → AUTHORIZATION_READY → ACTIVE`

失败时保留当前已完成阶段，并设置 `actual_state=FAILED`、`failed_stage`、稳定 `last_error_code`。重试只执行后续阶段；每一步必须可通过已存在资源回读实现幂等。

## 事务边界

- 创建 activation 记录先独立提交。
- 每个远程步骤在事务外执行；成功后以短事务推进检查点。
- 失败记录使用独立事务持久化，不能与抛出的业务异常一起回滚。
- Semattice metadata/template 使用既有稳定幂等键；PM 和资源创建改为 ensure 语义。

## 错误语义

- Semattice `SCHEMA_MIGRATION_REQUIRED` 原样映射为安全的开通阻塞码。
- 远程 5xx 统一为依赖不可用，但保留失败阶段和 correlation ID。
- API 不返回 SQL、数据库 URL、HMAC、OACT 或 Secret。

## 验收

1. 每个阶段注入失败后 activation 仍存在，阶段与安全错误码可回读。
2. reconcile 从失败阶段恢复，已完成步骤不重复创建资源。
3. 同一 idempotency key 重放返回同一 activation；不同 key 对既有 activation 返回冲突。
4. UAT migration 完成后，运营平台开通最终进入 ACTIVE。

## 回滚

回滚应用代码不会删除 activation、PM 资源、Semattice metadata 或授权事实。处于 FAILED 的记录继续失败关闭；恢复必须通过正式 reconcile，不直写数据库。
