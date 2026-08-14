---
kind: task-status
task_id: TASK-304
feature_id: FEAT-185
integration_id: INT-020
status: review
updated_at: 2026-08-14T01:14:07Z
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
- 本地环境与真实 Semattice actor/业务结果待从 AgentCiCi、DevAutopilot 本地 `main` 重建后验收。
