---
task_id: TASK-275
integration_id: INT-008
status: blocked
primary_project: agentcici
blocked_by: ISSUE-2026-08-10-new-tenant-owner-missing-oidc; Demo Company normal tenant employee login
---

# TASK-275 - DevAutopilot 标准租户应用控制面

## 范围

实现 `devautopilot-standard` 模板 activation、资源编排和应用生命周期。开通时自动创建租户独立的标准产品经理 Agent、其受控机器主体和执行绑定；开发者仍由 AgentCiCi 租户 ORG_ADMIN 按需管理，不得放入平台运营端。详情以 `docs/specs/FEAT-164-devautopilot-standard-tenant-application.md` 为准。

## 边界

- 不修改 Semattice 私有数据库；DevAutopilot 的运行时接入由其独立 TASK-018 交付。
- 不保存或回读 Client Secret；开发者 secret 仅由既有受治理创建路径一次返回。
- 现有未提交 `PlatformTenantLifecycleController` 修改须保留并纳入同一兼容逻辑。

## 完成条件

- 平台管理员可对已 provisioned 公司幂等开通/暂停/恢复 DevAutopilot；开通自动生成标准 PM Agent/SERVICE/binding，并使用同租户 OWNER/ORG_ADMIN 作为初始负责人。
- 租户 ORG_ADMIN 可在 AgentCiCi 管理端按租户自定义名称新增任意开发者机器主体，并可调整 PM 或开发者的显示名称与 HUMAN 负责人。
- 所有资源、操作和错误按 company 隔离并有审计/关联 ID。
- 从 AgentCiCi 前台进入 DevAutopilot 时，以一次性 ticket 交接同租户会话，浏览器不得复用或保存 OACT。
- 定向后端、前端和跨系统 UAT 验证通过；其中正常 ORG_ADMIN 的真实创建、双租户隔离及暂停/恢复使用业务会话完成，不以平台账号替代。

## 交付状态

- UAT 已发布 `2.8.57-beta.2 / 2753d268acd9`，但截图确认运营卡片仍错误展示 Semattice 内部 UUID，且前台外部入口硬编码生产地址；本任务已重新打开以修复上述事实展示和 browser handoff。
- 修复已发布 UAT `2.8.57-beta.3 / 1b07df5c6f40`：三张应用卡片统一展示 `租户标识`，DevAutopilot 不再展示 UUID 或产品经理；同源 handoff ticket 的创建、兑换边界和匿名负向已通过。正常租户用户的真实 Semattice 数据回读仍待验收。
- 截图确认带 handoff query 的首页被静态服务错误返回 404，且现有 activation 仅完成 Semattice 基线，未创建标准 PM Agent/SERVICE。任务已重新打开：修复根路由，并在新开通和显式补齐初始化中创建标准 PM 资源。
- 正常 ORG_ADMIN 的新增、编辑和双租户隔离业务验收仍待完成；未为验证而创建测试机器主体或读取 Secret。
- V108 已在 UAT 成功执行；匿名团队管理 API 为预期 `401`。
- UAT 已发布 `2.8.58-beta.1 / 4ffab5c43c0e`：新 activation 自动创建标准 PM Agent/SERVICE/Tool 与执行绑定；早期 activation 通过受平台授权的 `initializations` 显式补齐。正常 ORG_ADMIN 的 PM/开发者创建、双租户隔离及暂停/恢复业务验收仍待完成，任务保持 `in_progress`。
- 真实 UAT 访问日志已定位 workspace 503：handoff issue/exchange 为 200，随后 activation resolve 因 RS256 OACT 被通用会话 JWT 过滤器误判而返回 401。Semattice 管理入口同时因后端返回内部 HTTP base URL、前端仅允许生产 hostname 而被拒绝。已实现 OACT 专用验签过滤器、独立 console public base URL 与下一生产版本 beta 生成规则，待发布 `2.8.59-beta.1` 并完成真实链路回归。
- 已发布 `2.8.59-beta.1 / 94ceb612bd71`。目标租户真实 ORG_ADMIN 链路回归：handoff、DevAutopilot consume/workspace、`/api/admin/devautopilot/team`、Semattice console 均为 200；team 回读标准 PM Agent/SERVICE 与两个动态 developer 共 4 个 ACTIVE 资源。当前单租户链路完成，任务仅因第二租户正向/跨租户负向未执行而保持 `in_progress`。
- 已发布 `2.8.59-beta.3 / 5be204680e16`，UAT scope 配置修订为 `666d570`。平台管理员调用正式 `initializations` 返回 200；目标租户回读标准 PM Agent/SERVICE 与两个 developer，其中 `墨子开发者=SUSPENDED`、`鲁班/天工产品经理=ACTIVE`。Semattice 回读 5 个 Principal，初始化不再被历史 HUMAN owner 缺少统一身份绑定阻断。第二租户隔离仍未执行，任务保持 `in_progress`。
- UAT 截图确认标准产品经理 Agent 已创建但未出现在员工首页。根因是模板只创建 definition 和 Tool binding，没有生成 `published_version_id`，而首页按已发布状态正确过滤。修复将 `web` 渠道、标准 Spec 编译和发布纳入新开通与 `initializations` 幂等补偿；待定向测试及 UAT 发布回读。
- 已发布 UAT `2.8.59-beta.5 / 0edfc3567f85`，定向发布器测试、相关身份回归、后端 package、镜像发布、备份、容器健康与版本回读均通过。目标租户只读回读仍为 `天工产品经理 / devautopilot-pm-09653ab9 / published_version_id=NULL` 且无 channel binding，证明既有租户尚未执行补偿，而非新版代码未生效。当前可控浏览器没有平台管理员登录态；必须由已授权平台管理员调用正式 `initializations` 后再验收首页，禁止直接修改数据库。
- 已发布 UAT `2.8.59-beta.7 / 7e309a39394d`：卡片不再把两条 ACTIVE 资源快照误判为初始化完成，而是使用服务端 `initializationReady` 权威检查已发布版本、`web` 渠道和 SERVICE 主体。真实页面已正确显示“待补齐”；平台管理员点击后返回“暂无平台可用模型”。只读回读确认 UAT 已选模型为 0，所有厂商凭据为空；因此需先通过平台“模型厂商与目录/场景模型路由”配置有效聊天模型，再重试初始化。未直接写数据库、未发布不可运行的假 Agent。
- 已发布 UAT `2.8.59-beta.8 / 8213646b4fa3`：OneKeyToken 检测实际验证 `qwen3.5-flash` 后，平台管理员显式将其加入目录并绑定 chat 路由；目标租户正式 `initializations` 返回成功，卡片回读“已完成”。数据库只读回读确认 PM Agent 已发布、`web` 渠道启用、工作流 `PUBLISHED`、执行 SERVICE ACTIVE。平台账号不能替代正常租户员工会话，首页智能体实际可见与创建会话仍待租户用户刷新确认；第二租户隔离仍待完成。
- 已发布 UAT `2.8.59-beta.9 / 534a3baff64e`：修正 beta.8 的模型语义错误。`onekeytoken/auto` 是实际请求并验证成功的稳定路由别名，网关返回的 `qwen3.5-flash` 只作为下游诊断信息；UAT 已删除错误目录项，chat、技能创作、本体建模、AI 听记、客户洞察五个场景均回读 `onekeytoken/auto`，DevAutopilot 初始化保持完成。
- 用户在员工首页向“天工产品经理”询问创建项目时得到 CRM 限制说明。UAT 只读回读证明 Agent 工作流虽有 query/create/review Tool，但 `skills=[]`、`agent_skill_binding=0`、`agent_workflow_skill_ref=0`，因此缺失 DevAutopilot 领域上下文。已发布 `2.8.59-beta.11 / 4b0be4c4328e`：初始化会为平台标准 Skill 建立已发布不可变快照、always-on 绑定并重新编译发布；readiness 同时验证 Skill binding、workflow ref 和已发布 Skill 版本。真实平台页面完成“待补齐 → 正式补齐 → 已完成”，数据库回读 PM 工作流 v2、5 个研发交付 Tool、标准 Skill 与 Skill v1 引用完整；待正常租户用户用截图原句完成最终对话回归。
- 第二测试租户 `orgvdd8xckmvc8r5yi6q` 已通过运营端正式按钮开通 Semattice，页面提示企业身份绑定完成，数据库回读 `semattice_provisioning_binding=PROVISIONED`。紧接着的 DevAutopilot 正式开通被 `requires an active tenant ORG_ADMIN` 拒绝；该租户唯一 OWNER 仍为 `PENDING_ACTIVATION`，且 `tenant_application_activation` 无记录，证明失败关闭且无半初始化残留。下一步必须由该 Owner 完成邮件激活和首次 OIDC 登录，再继续第二租户正向及跨租户负向验收。
- 连续三次 Goal 复核后外部状态仍未变化：Demo Company 员工会话停留在 Keycloak 登录页，第二测试租户 Owner 仍未激活。代码和平台权限无法代办邮箱验证或真实成员登录，且这些动作是正常租户端验收的身份事实，不得以 SERVICE、平台管理员或数据库更新替代；任务暂置 `blocked`，用户完成上述两项后恢复。
