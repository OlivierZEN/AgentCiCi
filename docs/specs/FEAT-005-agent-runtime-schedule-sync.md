---
updated_at: 2026-04-25T14:32:00Z
status: implemented
feature_id: FEAT-005
title: 智能体调度触发器同步（Spec 推导 -> 运行时持久化）
owner_role: product_engineering
related_decisions: DEC-019
related_issues: none
---

# FEAT-005 智能体调度触发器同步

## 问题

- FEAT-004 已支持在「触发与调度」里展示调度占位/推导，但缺少“确认并固化”动作。
- 用户在完成「生成流程代码」后需要把时间语义沉淀成可追踪的运行时配置，而不是每次临时推导。

## 产品设计

- 调度展示分三态：
  - `persisted`：已同步并持久化；
  - `inferred`：仅从 Spec 推导，尚未固化；
  - `placeholder`：已发布但无可识别时间语义，显示平台占位说明。
- 在「触发与调度」中新增按钮：`同步到调度`。
- 同步成功后列表来源切换到 `persisted`，并在 UI 给出“已同步 X 条”的反馈。
- 发布渠道配置新增开关：`autoSyncSchedulesOnPublish`（默认开启），启用时发布版本后自动触发一次同步。

## 接口设计

### 1) 查询触发器（扩展）

- `GET /agents/{agentId}/runtime/triggers`
- 返回新增字段：
  - `scheduleSource`: `none | inferred | persisted | placeholder`
  - `scheduleSyncHint`: 当前状态说明文案
  - `scheduleTriggers[*].source`, `scheduleTriggers[*].versionNo`

### 2) 同步调度（新增）

- `POST /agents/{agentId}/runtime/schedules/sync`
- 权限：`ORG_ADMIN`
- 行为：
  - 优先使用已发布版本 manifest；否则使用最新编译版本；
  - 从 `generatedFrom.specIr.steps` 中提取时间语义行；
  - 失活旧的 active 记录并写入新的 `SPEC_SYNC` 记录。
- 响应：
  - `synced`: 本次写入条数
  - `sourceVersionId/sourceVersionNo`
  - `rows`: 持久化后的调度列表

### 3) 发布时自动同步（扩展行为）

- 当 `publishConfigs.feishu.autoSyncSchedulesOnPublish = true` 时，`POST /agents/{agentId}/publish` 在设为 PUBLISHED 后自动调用同步逻辑。
- 同步失败不影响发布主流程（仅记录/吞掉异常，保持发布可用性）。

## 数据模型

- 新表：`agent_runtime_schedule_trigger`
  - 关键字段：`org_id`, `agent_id`, `workflow_version_id`, `version_no`, `trigger_key`, `title`, `cadence`, `detail`, `source`, `stub`, `active`
  - 用 `active` 实现“替换同步”语义。

## 实现说明

- 后端：
  - `AgentRuntimeScheduleSyncService`
  - `AgentRuntimeScheduleTriggerEntity/Repository`
  - `AgentRuntimeController` 新增 sync endpoint
  - `AgentRuntimeCatalogService` 统一聚合 persisted/inferred/placeholder
- 前端：
  - `AgentBuilderShell.tsx` 增加 `同步到调度` 按钮与状态提示
  - 使用 `scheduleSource/scheduleSyncHint` 做 UI 文案切换

## 验证

- 集成测试：
  - `shouldPersistScheduleTriggersAfterSyncEndpoint`
  - `shouldInferScheduleTriggersFromSpecAfterCompile`
  - `shouldAutoSyncSchedulesAfterPublishWhenEnabled`
- 构建验证：
  - `backend mvn compile`
  - `frontend npm run build`
