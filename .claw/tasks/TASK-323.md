---
kind: task-status
task_id: TASK-323
feature_id: FEAT-190
status: done
priority: critical
owner_role: integration-agent
claimed_by: codex
updated_at: 2026-08-19T10:27:00Z
updated_by: codex
---

# TASK-323 - 修复生产 DevAutopilot 机器身份空 scope 与恢复幂等

## 范围

- 当部署未显式配置模板 scope 时，由 AgentCiCi 服务端回退到受治理的 `runtime.record.read/create/update` 最小执行集合。
- 保留显式配置覆盖能力，但禁止空配置继续流入机器主体创建。
- 激活检查点写入前若标准 PM Agent 已由中断的前次尝试创建，重试必须校验并复用该受管模板身份，不得再次创建或把同 ID 的非受管 Agent 接管。
- AgentCiCi 历史 opaque account ID 在 Semattice UUID Principal 契约边界映射为稳定 UUID；本地账号主键与业务数据不迁移，projection、授权模板和后续 OACT 使用同一映射。
- 完成本地回归后从同一最终提交发布 UAT `2.8.65-beta.1` 与生产 `2.8.65`，再重试两个失败租户并回读 ACTIVE。

## 当前证据

- 生产 Semattice metadata 已成功推进，失败阶段从 `METADATA_READY` 前移到 `PRODUCT_MANAGER_READY`。
- 生产 backend 未注入 `APP_DEVAUTOPILOT_TEMPLATE_PM_SCOPES`，构造器把默认空值保留为空列表，最终触发“机器账户至少需要一个 scope”。
- 已实现空配置回退到固定最小执行 scope；聚焦单测、全新 PostgreSQL 16 的 118 项 Flyway migration 与恢复 Saga 集成测试、backend production package 均通过。
- 首次生产重试进一步证实 Agent 创建和 activation checkpoint 之间存在提交窗口：标准 `devautopilot-pm` 已存在但应用资源尚未登记。已把创建改为受管模板的 ensure/reuse 语义，并新增中断恢复回归；同 ID 非受管或禁用 Agent 继续失败关闭。
- 第二租户恢复到 `AUTHORIZATION_READY` 后，15 个应用管理员中的旧式 `account-*` 主键触发 Semattice UUID claim 401。已在官方访问令牌边界增加确定性 UUID projection，并让授权 assignment 使用同一转换；当前 UUID 主体保持原值。
- UUID projection 后的首次授权重试证实本地成员权威查询不能使用资源侧 UUID 回查旧账号主键。已明确拆分：本地成员同步始终用原始 account ID，Semattice token/assignment 才使用确定性 UUID；两者不再混用。
- backend 全量测试启动时被既有默认数据源不可达持续重试阻塞，人工终止；本任务定向与真实数据库证据不受影响。

## 完成证据

- 四项根因修复分别进入 `83b26887`、`0a03d676`、`4829e9ca`、`784ccd23`；最终源码提交 `784ccd23e933` 已与本地/远程 `main` 对齐。
- UAT `2.8.65-beta.1` 与生产 `2.8.65` 均运行同一提交；生产 backend/frontend ACR index digest 为 `sha256:4c4a1c4040872081777d6b3b7c60a5a6ca6892ff650d11545c9ab9e495d97039` / `sha256:37922c74ddc518500abe68b81e40e9b8ad8a96011185aef1e6cb094e6c828ae1`。
- 生产登录态重试 `org5nszpgj99jaysxv6y` 成功，`orgl624a7r54pzp3e5zv` 回归通过；两者 UI 均为已开通 3、待处理 0，数据库均为 `ACTIVE/ACTIVE`、无失败阶段/错误码、资源 2、PM scope 3。
- 生产知识库保持 9 个知识库、35 个文档、661 个 chunk、29 个文件和 549 个 Qdrant points；仅 backend/frontend 重建，四个状态服务容器 ID 未变。
