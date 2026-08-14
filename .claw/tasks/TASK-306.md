---
kind: task-status
task_id: TASK-306
feature_id: FEAT-186
integration_id: INT-021
status: implementing
updated_at: 2026-08-14T04:15:00Z
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
- 为非平台运营的租户组织管理员提供明确确认的授权模板同步入口，只允许重新应用模板并审计。
- 通过真实旧任务数据验证删除、回收站和后续单任务重建闭环。

## 当前证据

- 定向测试通过：`ServicePrincipalServiceTest`、`SematticeProjectDeliveryDeleteToolServiceTest`、`ToolOrchestratorServiceTest`、`DeliveryWriteReceiptGuardTest`。
- `mvn -q -DskipTests package` 通过。
- 角色化候选 scope 已随本地 backend `2.8.61-dev.26809b8 / 26809b8a07b7` 运行，healthy、restart=0，完整 `./stack verify` 通过。
- 受权页面已回读大乔PM候选包含 delete、哪吒候选不包含 delete；未替用户勾选或提交。
- 人工显式授权、真实旧任务回收站删除和单任务重建仍待完成。
- 当前本地模板升级需要通过该入口重新下发，平台账号登录边界不能由组织管理员绕过。
