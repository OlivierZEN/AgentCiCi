---
kind: task-status
task_id: TASK-323
feature_id: FEAT-190
status: in_progress
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-19T09:52:00Z
updated_by: codex
---

# TASK-323 - 修复生产 DevAutopilot 机器身份空 scope 与恢复幂等

## 范围

- 当部署未显式配置模板 scope 时，由 AgentCiCi 服务端回退到受治理的 `runtime.record.read/create/update` 最小执行集合。
- 保留显式配置覆盖能力，但禁止空配置继续流入机器主体创建。
- 激活检查点写入前若标准 PM Agent 已由中断的前次尝试创建，重试必须校验并复用该受管模板身份，不得再次创建或把同 ID 的非受管 Agent 接管。
- 完成本地回归后从同一提交发布 UAT `2.8.62-beta.N` 与生产 `2.8.62`，再重试两个失败租户并回读 ACTIVE。

## 当前证据

- 生产 Semattice metadata 已成功推进，失败阶段从 `METADATA_READY` 前移到 `PRODUCT_MANAGER_READY`。
- 生产 backend 未注入 `APP_DEVAUTOPILOT_TEMPLATE_PM_SCOPES`，构造器把默认空值保留为空列表，最终触发“机器账户至少需要一个 scope”。
- 已实现空配置回退到固定最小执行 scope；聚焦单测、全新 PostgreSQL 16 的 118 项 Flyway migration 与恢复 Saga 集成测试、backend production package 均通过。
- 首次生产重试进一步证实 Agent 创建和 activation checkpoint 之间存在提交窗口：标准 `devautopilot-pm` 已存在但应用资源尚未登记。已把创建改为受管模板的 ensure/reuse 语义，并新增中断恢复回归；同 ID 非受管或禁用 Agent 继续失败关闭。
- backend 全量测试启动时被既有默认数据源不可达持续重试阻塞，人工终止；本任务定向与真实数据库证据不受影响。
