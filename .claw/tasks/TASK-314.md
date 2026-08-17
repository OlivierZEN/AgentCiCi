---
kind: task-status
task_id: TASK-314
feature_id: FEAT-177
integration_id: INT-025
status: done
priority: critical
owner_role: backend-agent
claimed_by: codex
updated_at: 2026-08-17T11:01:08Z
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

## 完成证据

- `76d06ad8` 已合并远程 `main@e8dc3b3a` 并发布为 UAT `2.8.61-beta.27`；定向测试和 backend package 通过。完整 Maven 集成套件仅因本机 `localhost:5432` 不可用产生 17 个 Spring context 环境错误，无断言失败。
- 平台管理员通过正式“同步交付授权”动作完成目标租户同步；回读模板 `devautopilot.authorization.v4`、分配数 4、`verified=true`、实际/阶段状态均为 `ACTIVE`。
- 当前 ORG_ADMIN 的变更、缺陷、交付事件、研发项目、需求、开发任务、工时记录 7 个对象均成功读取空态；从 AgentCiCi 正式 AI 应用入口进入 DevAutopilot 后显示 Semattice 实时数据、在线和完整资源/项目空态。
- 六容器 healthy/restart=0，运行版本/commit 为 `2.8.61-beta.27 / e8dc3b3ad891`，四个状态服务未重建。备份 `/data/apps/agentcici/backups/20260817T104142Z-before-2.8.61-beta.27` 已校验，生产未修改。
