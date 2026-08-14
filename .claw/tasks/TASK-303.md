---
kind: task-status
task_id: TASK-303
feature_id: FEAT-184
integration_id: INT-020
status: done
updated_at: 2026-08-14T01:30:50Z
updated_by: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-184-devautopilot-confirmed-requirement-status.md
---

# TASK-303 - 修正确认式需求创建状态

## 范围

- 将产品经理明确确认后的 `create_requirement` 初始状态改为 `已确认`。
- 增加请求载荷和写后回读回归测试。
- 保持确认前零写入、父项目解析、intake 审计和最小权限边界。

## 完成条件

- 定向测试和 backend package 通过。
- 代码与本任务文档进入 AgentCiCi 本地 `main`。
- 本地 backend 从该 `main` 提交构建并回读版本、健康与匿名边界。

## 当前证据

- `mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest test`：通过。
- `mvn -q -DskipTests package`：通过。
- backend 全量套件受既有 Spring 上下文/AI fixture 失败影响；未将其声明为本任务通过证据。
- 本地 `main@e8275353e0d0` 已构建 `cc-aixone/agentcici-backend:local`；运行版本 `2.8.61-dev.e827535`、health=`UP`、container healthy/restart=0。
- 本地真实需求 `REQ-6F34ECF3` 已通过受治理 HUMAN 确认链进入 `已确认`，并在同一闭环完成 5 项开发任务派发。
- AgentCiCi backend 运行 `2.8.61-dev.53715a3`、healthy/restart=0；完整 `./stack verify` 通过，UAT/生产未修改。
