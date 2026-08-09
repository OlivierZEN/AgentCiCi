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

- 不修改 Semattice 私有数据库；DevAutopilot 的运行时接入由其独立 TASK-018 交付。
- 不保存或回读 Client Secret；开发者 secret 仅由既有受治理创建路径一次返回。
- 现有未提交 `PlatformTenantLifecycleController` 修改须保留并纳入同一兼容逻辑。

## 完成条件

- 平台管理员可对已 provisioned 公司幂等开通/暂停/恢复 DevAutopilot，不输入人员或机器账号信息。
- 租户 ORG_ADMIN 可在 AgentCiCi 管理端的独立弹窗按租户自定义名称、新增唯一 PM 或任意开发者机器主体，并选择同租户有效 HUMAN 负责人；详情中的独立编辑弹窗可修改显示名称与负责人。
- 所有资源、操作和错误按 company 隔离并有审计/关联 ID。
- 从 AgentCiCi 前台进入 DevAutopilot 时，以一次性 ticket 交接同租户会话，浏览器不得复用或保存 OACT。
- 定向后端、前端和跨系统 UAT 验证通过；其中正常 ORG_ADMIN 的真实创建、双租户隔离及暂停/恢复使用业务会话完成，不以平台账号替代。

## 交付状态

- UAT 已发布 `2.8.57-beta.2 / 2753d268acd9`，但截图确认运营卡片仍错误展示 Semattice 内部 UUID，且前台外部入口硬编码生产地址；本任务已重新打开以修复上述事实展示和 browser handoff。
- 正常 ORG_ADMIN 的新增、编辑和双租户隔离业务验收仍待完成；未为验证而创建测试机器主体或读取 Secret。
- V108 已在 UAT 成功执行；匿名团队管理 API 为预期 `401`。
- 正常 ORG_ADMIN 的 PM/开发者创建、双租户隔离及暂停/恢复业务验收为待验收项，任务保持 `review`，不阻塞已发布的控制面职责调整。
