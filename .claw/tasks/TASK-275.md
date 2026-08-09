---
task_id: TASK-275
integration_id: INT-008
status: review
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
- 定向后端、前端和跨系统 UAT 验证通过；其中正常 ORG_ADMIN 的真实创建、双租户隔离及独立 DevAutopilot 运行态使用业务会话和该应用的 UAT 发布入口完成，不以平台账号或生产脚本替代。

## 交付状态

- UAT 已发布 `2.8.57-beta.1 / e5c097adda5f`，backend/frontend 均使用同一不可变 ACR 工件，运行时和页脚版本一致。
- V108 已在 UAT 成功执行；匿名团队管理 API 为预期 `401`。
- 正常 ORG_ADMIN 的 PM/开发者创建、双租户隔离及独立 DevAutopilot 缓存运行态为待验收项，任务保持 `review`，不阻塞已发布的 AgentCiCi 控制面职责调整。
