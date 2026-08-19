---
kind: task-status
task_id: TASK-323
feature_id: FEAT-190
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-19T09:32:00Z
updated_by: codex
---

# TASK-323 - 修复生产 DevAutopilot 机器身份空 scope

## 范围

- 当部署未显式配置模板 scope 时，由 AgentCiCi 服务端回退到受治理的 `runtime.record.read/create/update` 最小执行集合。
- 保留显式配置覆盖能力，但禁止空配置继续流入机器主体创建。
- 完成本地回归后从同一提交发布 UAT `2.8.62-beta.N` 与生产 `2.8.62`，再重试两个失败租户并回读 ACTIVE。

## 当前证据

- 生产 Semattice metadata 已成功推进，失败阶段从 `METADATA_READY` 前移到 `PRODUCT_MANAGER_READY`。
- 生产 backend 未注入 `APP_DEVAUTOPILOT_TEMPLATE_PM_SCOPES`，构造器把默认空值保留为空列表，最终触发“机器账户至少需要一个 scope”。
- 已实现空配置回退到固定最小执行 scope；聚焦单测、全新 PostgreSQL 16 的 118 项 Flyway migration 与恢复 Saga 集成测试、backend production package 均通过。
- backend 全量测试启动时被既有默认数据源不可达持续重试阻塞，人工终止；本任务定向与真实数据库证据不受影响。
