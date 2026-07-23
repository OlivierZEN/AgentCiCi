---
kind: task-status
task_id: TASK-239
status: in_progress
updated_at: 2026-07-23T07:05:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-239.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-239 - 混合智能体运行时 P5：Trace 运行执行投影与多主题界面

## Scope

- 在已有 `/admin/ops` Trace 详情内新增“运行执行”分区，面向组织管理员与平台运营，默认聚焦运行总览；
- 将 P1–P4 的同组织运行、计划、步骤、事件和审查事实与保存该轮的 Trace 精确关联，并提供脱敏只读详情投影；
- 展示模式、终态、风险、计划修订、审查状态、步骤时间线和最小证据；有确认或失败/部分完成事实时才显示对应原因；
- 在既有 `gilded` 与 `galaxy` 等主题 token 下保持同一信息结构、紧凑密度和交互，完成桌面端双主题检查。

## Non-goals

- 不新增路由、独立控制台、计划画布、移动端适配或主题专属布局；
- 不改变模式路由、计划/步骤状态机、工具、凭据、确认、Reflect、评测或生产灰度；
- 不返回模型思维链、原始工具/模型 payload、密钥、凭据、未脱敏用户正文或跨组织运行事实；
- 不移除既有 Trace 节点、工作流定义检查或“加入回归集”。

## Acceptance

- Trace 仅能通过持久化 `runtimeRunId` 精确关联同组织运行；历史或非 Plan-Exec Trace 给出“此 Trace 没有关联运行执行事实”，跨组织和不存在 ID 均不能泄露运行信息；
- 默认总览先展示模式、终态、风险、计划修订和审查状态，步骤证据默认收起且复制内容保持脱敏；确认、失败或部分完成原因只在存在时展示；
- 现有 Trace 详情信息架构、回归集入口和工作流定义检查仍可用，界面在 `gilded` 与 `galaxy` 下使用语义 token、无外层横向溢出、console error/warning；
- 后端定向单元/集成、前端定向测试与生产构建、桌面端截图/交互状态、静态 diff 检查通过；共享测试库 V81 checksum 历史漂移与本任务结果分开记录。
