---
kind: task-status
task_id: TASK-314
feature_id: FEAT-177
integration_id: INT-025
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-17T10:23:35Z
updated_by: codex
---

# TASK-314 - DevAutopilot HUMAN 应用管理员授权同步

## 范围

- 消费 Semattice `devautopilot.authorization.v4`。
- 从当前租户权威成员关系生成 active `OWNER`、`ORG_ADMIN` 和显式 active `APP_ADMIN` 的 HUMAN `application_admin` 分配。
- 保持 PM/developer/observer SERVICE 分配逻辑和最小权限不变。
- 通过排序、去重和主体集合摘要维持幂等；成员失活或管理员资格移除后由下一次正式同步收敛。

## 完成条件

- 当前 ORG_ADMIN 出现在授权模板调用体，普通 active ORG_USER 不出现。
- 显式 active APP_ADMIN 出现，REVOKED/非激活成员不出现。
- 初始 Owner 始终保留且至少存在一个 HUMAN 管理员。
- Semattice 返回的 assignment count 与完整集合一致；定向测试和 package 通过。
- UAT 正式同步和两条业务读取链通过；生产未修改。
