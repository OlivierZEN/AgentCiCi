---
task_id: TASK-275
integration_id: INT-008
status: in_progress
primary_project: agentcici
---

# TASK-275 - DevAutopilot 标准租户应用控制面

## 范围

实现 `devautopilot-standard` 模板 activation、资源编排和应用生命周期。人员、产品经理 Agent 与开发者机器主体必须由 AgentCiCi 租户 ORG_ADMIN 在 `/admin/service-principals` 自主管理，不得放入平台运营端。详情以 `docs/specs/FEAT-164-devautopilot-standard-tenant-application.md` 为准。

## 边界

- 不修改 Semattice 私有数据库或 DevAutopilot 私有运行文件。
- 不保存或回读 Client Secret；开发者 secret 仅由既有受治理创建路径一次返回。
- 现有未提交 `PlatformTenantLifecycleController` 修改须保留并纳入同一兼容逻辑。

## 完成条件

- 平台管理员可对已 provisioned 公司幂等开通/暂停/恢复 DevAutopilot，不输入人员或机器账号信息。
- 租户 ORG_ADMIN 可在 AgentCiCi 管理端按租户自定义名称新增唯一 PM 或任意开发者机器主体，负责人从当前会话推导。
- 所有资源、操作和错误按 company 隔离并有审计/关联 ID。
- 定向后端、前端和跨系统 UAT 验证通过。
