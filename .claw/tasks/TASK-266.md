---
kind: task-status
task_id: TASK-266
status: in_progress
updated_at: 2026-08-04T16:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-266.yaml
spec_path: docs/specs/FEAT-158-semattice-business-object-list-preview.md
---

# TASK-266 - AI表格业务对象实时列表

## Current State

- 用户已确认高保真 UI 形态，并授权生产实现及线上发布。
- 已确认数据平台提供已发布元数据读取与带对象/字段/记录范围授权的记录查询能力。
- Blocked: none

## Scope

- 在 AgentCiCi 用户端实现真实、只读的 AI表格对象目录和记录列表。
- 后端以当前用户短期 OACT 调用数据平台，前端只调用同源 AgentCiCi API；Vite/Nginx 必须将 `/ai-table` 精确代理至后端。
- 完成服务端游标分页、受索引约束的查询、列配置持久化、详情以及完整异常状态。
- 维持桌面端多主题高保真结构；不实现写入、批量、导出或移动端。

## Next Action

- 在已扩展的授权范围内实现后端桥接与前端真实数据适配，然后完成生产发布验收。

## Verification

- 预览阶段的前端构建、全量测试及桌面视觉证据保留为视觉基线；真实链路上线前将重新执行后端、前端、浏览器和生产验证。
