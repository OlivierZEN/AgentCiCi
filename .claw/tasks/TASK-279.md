---
kind: task-status
task_id: TASK-279
status: done
updated_at: 2026-08-11T03:53:08Z
updated_by: codex
assignee: codex
owner_role: fullstack-agent
assignment_path: n/a
spec_path: docs/specs/FEAT-167-devautopilot-delegated-execution-access.md
---

# TASK-279 - DevAutopilot 产品经理委托授权模型调整

## Current State

- 已按应用角色拆分机器主体治理负责人和业务调用者，负责人继续承担所有权、生命周期与问责，不再是唯一委托人。
- `2.8.61-beta.2 / c66d9448c95b` 已发布 UAT；当前 UAT 后续版本 `2.8.61-beta.3 / 47affe4086e5` 沿主线包含本任务全部变更。
- Demo Company 的非负责人 `ORG_ADMIN` 已通过产品经理 Agent 查询 Semattice；审计同时记录实际调用者和不同的治理负责人。

## Scope

- 按 FEAT-167 实现应用角色、风险分级委托、双主体 OACT/审计、管理 API/UI、Agent 权限预检和 SSE 错误终止。
- 更新 DevAutopilot 初始化为新委托策略并对既有激活幂等补偿。
- 完成定向测试、构建、状态校验、独立提交和下一生产版本 UAT 发布。

## Next Action

- 无；生产发布时将当前下一生产版本候选整体晋升，并保留 V109 与应用角色数据。

## Verification

- V109 从空 PostgreSQL 16 的 V1 到 V109 共 105 项 migration 全部成功；两条 DevAutopilot 绑定均为 `TENANT_APP_ROLE`，应用角色表存在。
- 后端产品经理授权、SSE、初始化 readiness、Semattice 查询/创建/评审定向测试通过；前端应用角色管理和聊天预检定向测试通过，生产构建通过。
- 完整 Maven 套件已尝试，但 15 份集成报告因本机 `localhost:5432` 拒绝连接未完成，未记为全量通过；UAT 真实 PostgreSQL/Flyway 和业务链路另行通过。
- UAT `2.8.61-beta.2` 只重建 backend/frontend，完整备份非空且 `0600`，四个状态服务 ID 不变；六容器、health、Nginx、匿名 401 和启动日志通过。
- 第二租户 OWNER 查询项目成功；Demo Company 的 `ORG_ADMIN`（非机器负责人）查询成功，返回本租户 0 项目，并在审计中出现 `delegationPolicy=TENANT_APP_ROLE`、`appRole=APP_ADMIN`、不同的 actor/owner principal。
- 租户管理端“管理应用调用权限”弹窗显示负责人和 ORG_ADMIN 自动 APP_ADMIN、普通 ORG_USER 默认不允许调用；未修改任何角色，浏览器 error/warning 为 0。
- 当前 UAT `2.8.61-beta.3` 是 `c66d9448c95b` 的后继版本，V109 和本任务业务验收均保持通过。
