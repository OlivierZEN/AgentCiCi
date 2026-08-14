---
kind: task-status
task_id: TASK-306
feature_id: FEAT-186
integration_id: INT-021
status: implementing
updated_at: 2026-08-14T04:05:00Z
updated_by: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-186-governed-delivery-delete-route.md
---

# TASK-306 - 补齐产品经理受治理删除路由

## 范围

- 修正删除确认指令的对象类型解析。
- 补齐聊天确定性删除路由和 Tool 编排器本地分发。
- 将 Semattice 删除结果收敛为可由成功声明守卫核验的回收站回读。
- 仅向 DevAutopilot 产品经理 SERVICE 暴露并接受 `runtime.record.delete`，开发者 SERVICE 保持失败关闭。
- 通过真实旧任务数据验证删除、回收站和后续单任务重建闭环。

## 当前证据

- 定向测试通过：`ServicePrincipalServiceTest`、`SematticeProjectDeliveryDeleteToolServiceTest`、`ToolOrchestratorServiceTest`、`DeliveryWriteReceiptGuardTest`。
- `mvn -q -DskipTests package` 通过。
- 角色化候选 scope 与本地运行配置已修复；部署、人工显式授权、真实数据纠正待完成。
