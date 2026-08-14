---
kind: task-status
task_id: TASK-304
feature_id: FEAT-185
integration_id: INT-020
status: done
updated_at: 2026-08-14T01:30:50Z
updated_by: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-185-devautopilot-delegated-product-manager-execution.md
---

# TASK-304 - 提供产品经理 SERVICE 窄范围委托执行授权

## 范围

- 为 DevAutopilot 的需求确认和任务方案确认提供固定操作白名单。
- 复用已有 HUMAN 委托、应用角色、机器身份健康、scope 和审计门禁签发短时 SERVICE OACT。
- 禁止调用方选择 scope、执行主体或租户，并校验激活资源一致性。

## 当前证据

- `mvn -q -Dtest=DevAutopilotExecutionAuthorizationServiceTest test`：通过。
- `mvn -q -DskipTests package`：通过。
- 本地 backend 已从 `main@53715a337691` 构建为 `2.8.61-dev.53715a3`，healthy/restart=0。
- 真实需求确认和 5 项 `dev_task` 创建的 Semattice 审计 actor 均为 primary 产品经理 SERVICE `d841968a-88c3-4681-b866-e230f3563616`；没有使用 HUMAN 写权限回退。
- 完整 `./stack verify` 通过，UAT/生产未修改。
