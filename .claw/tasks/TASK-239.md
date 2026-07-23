---
kind: task-status
task_id: TASK-239
status: done
updated_at: 2026-07-23T07:50:00Z
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
- 现有 Trace 详情信息架构、回归集入口和工作流定义检查仍可用，界面在 `gilded` 与 `galaxy` 下使用语义 token 且无外层横向溢出；P5 区域不新增 console error/warning。
- 后端定向单元/集成、前端定向测试与生产构建、桌面端截图/交互状态、静态 diff 检查通过；共享测试库 V81 checksum 历史漂移与本任务结果分开记录。

## Implementation result

- Chat 的阻塞与流式路径仅在真实 Plan-Exec 运行存活时，将最小 `runtimeRunId`、模式风险、确认约束和审查状态写入该 Trace 的既有脱敏详情；不按会话或时间推测关联。
- Trace 详情以 Trace 的 `org_id` 加 `runtimeRunId` 回读运行、计划、步骤、事件和审查事实，并仅投影管理员可见的模式、终态、修订、最小脱敏证据和事件类型；历史/未关联 Trace 返回明确空态。
- 现有 Trace 详情增加“运行执行”总览、步骤/事件时间线、默认收起的证据复制，以及有条件的确认/失败说明；样式仅使用现有主题语义 token。

## Verification

- 后端 `AgentRunTraceServiceTest` 加 P2–P4/Chat 定向回归通过；新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V92，`AgentTaskRuntimeIntegrationTest` 通过。
- 前端 `AdminAgentRunMonitor.test.tsx` 3/3 与 `npm run build` 通过；新增样式未加入硬编码色、渐变或主题专属布局。
- 已使用受权组织管理员完成本地隔离库验收：进入 `/admin/ops` 的关联 Trace 后，运行总览、两步时间线、`gilded`/`galaxy` 同构主题、证据展开与脱敏复制均可用；`documentElement.scrollWidth = innerWidth = 1280`。截图保存为忽略的 `output/playwright/task239-gilded-runtime-execution.png` 与 `task239-galaxy-runtime-execution.png`。
- 同页的既有“审计日志”面板在最小临时库请求独立 `/ops/audit/logs` 时返回 500（2 条 console error）；运行执行 Trace 请求、展开与复制均无错误。该非 P5 接口问题已分开记录，未作为 P5 功能通过证据。
