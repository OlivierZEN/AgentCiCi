---
kind: task-status
task_id: TASK-306
feature_id: FEAT-186
integration_id: INT-021
status: implementing
updated_at: 2026-08-14T03:50:00Z
updated_by: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-186-governed-delivery-delete-route.md
---

# TASK-306 - 补齐产品经理受治理删除路由

## 范围

- 修正删除确认指令的对象类型解析。
- 补齐聊天确定性删除路由和 Tool 编排器本地分发。
- 将 Semattice 删除结果收敛为可由成功声明守卫核验的回收站回读。
- 通过真实旧任务数据验证删除、回收站和后续单任务重建闭环。

## 当前证据

- 定向测试通过：`SematticeProjectDeliveryDeleteToolServiceTest`、`ToolOrchestratorServiceTest`、`DeliveryWriteReceiptGuardTest`。
- `mvn -q -DskipTests package` 通过。
- 本地真实数据纠正和运行部署待完成。
