---
kind: task-status
task_id: TASK-240
status: review
updated_at: 2026-07-23T08:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-240.yaml
spec_path: docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md
---

# TASK-240 - 混合智能体运行时 P6：组织隔离灰度与运营验证

## Scope

- 为 Plan-Exec、模式路由和 Reflect 的服务端开关增加精确组织 + Agent 双白名单，默认全部关闭；
- 补齐低基数、脱敏运行指标与定向测试，覆盖命中、未命中、回退和审查结果；
- 完成定向质量门、全新库迁移、前端构建、Compose/发布 dry-run，并形成生产试点、观察与回滚清单。

## Non-goals

- 不在没有用户明确指定生产组织 ID、只读 Agent ID 与观察窗口时启用任何生产开关、发布镜像或修改线上环境；
- 不放宽工具、写入、凭据、确认、审查、组织隔离或 Trace 脱敏边界；
- 不新增移动端、页面路由、主题分叉、任意工具执行、外部副作用或数据破坏性回滚。

## Acceptance

- 三个运行时能力均要求开关、组织白名单和 Agent 白名单同时命中；任一缺失/不命中安全回退，客户端不可绕过；
- 指标无组织/Agent/会话/运行 ID 或正文等高基数字段，且可区分固定模式、结果和原因；
- 相关单元/集成、编译、前端构建、Compose、静态检查和发布 dry-run 真实通过；
- 生产试点只在用户指定目标后执行，且使用 Runbook 的备份、同版本号、观察、关闭开关和回滚步骤。

## Implementation result

- Plan-Exec、模式路由和 Reflect 现在都要求服务器开关、精确组织白名单、精确 Agent 白名单同时命中；默认配置继续为关闭和空白名单，Chat、流式、OpenAPI 与评测入口都携带可信 `orgId` 参与判断。
- 新增 Micrometer 低基数指标 `cici.agent_runtime.mode_decisions`、`cici.agent_runtime.plan_exec`、`cici.agent_runtime.reflect`，标签只允许固定 `mode`、`outcome` 与 `reason` 枚举；未知运行错误归一为 `OTHER`，不写入组织、Agent、会话、运行 ID 或正文。
- 跨组织 Reflect 请求仍先用 `runId + orgId` 回读并拒绝不存在的同组织事实；不在不被允许的组织创建审查记录。

## Remaining production prerequisite

- 代码、测试和发布 dry-run 已完成，真实生产灰度尚未开始。需要用户明确提供生产试点的组织 ID、只读 Agent ID，以及首次观察窗口；未获得这些输入前不得推送镜像、执行备份/部署或修改线上开关。
