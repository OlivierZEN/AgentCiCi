---
kind: task-board
version: 4
updated_at: 2026-07-25T03:05:00Z
updated_by: MANAGER-001
board_status: active
---

# Task Board

`task-board.md` is a compact index. Historical task cards are archived in `.claw/task-archive.md`.

Recommended statuses: `todo` / `ready` / `in_progress` / `blocked` / `review` / `done` / `canceled`
Recommended priorities: `critical` / `high` / `medium` / `low`

## Active Tasks

### TASK-253 - 计费用量公司成员查询修复

- status: `in_progress`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-146-billing-company-member-query-repair.md`
- task_status_path: `.claw/tasks/TASK-253.md`
- assignment_path: `.claw/assignments/TASK-253.yaml`
- next_action: 将构建者席位统计切换到当前 `UserEntity.company` 关联，并验证组织管理员账单总览。

### TASK-252 - 统一 Principal 身份与治理模型设计

- status: `in_progress`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-145-unified-principal-identity-governance.md`
- task_status_path: `.claw/tasks/TASK-252.md`
- assignment_path: `.claw/assignments/TASK-252.yaml`
- next_action: 机器 OACT 交换和 Semattice Principal 投影已发布；机器开户与人类邮件邀请已解耦。等待 provisioner client secret、OACT 签名配置、受权 service client 与 Keycloak SMTP 后，分别开启受控灰度并完成真实端到端验收。

### TASK-251 - 全局用户公共编号

- status: `complete`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-144-global-user-public-id.md`
- task_status_path: `.claw/tasks/TASK-251.md`
- assignment_path: `.claw/assignments/TASK-251.yaml`
- next_action: 已发布 `2.8.19 / 99d4cc3cb206`；V97 回填成功，待受权平台会话复核真实目录展示。

### TASK-250 - MCP HTTP 会话复用修复

- status: `done`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-143-mcp-http-session-propagation.md`
- task_status_path: `.claw/tasks/TASK-250.md`
- assignment_path: `.claw/assignments/TASK-250.yaml`
- next_action: 已合并 `main`（`4958bc1`）；等待单独的生产发布和受权会话复核授权。

### TASK-249 - 组织简档接口反向代理修复

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-142-admin-company-profile-proxy-route.md`
- task_status_path: `.claw/tasks/TASK-249.md`
- assignment_path: `.claw/assignments/TASK-249.yaml`
- next_action: 为生产 Nginx 与本地 Vite 同步 `/admin/company/profile` API 代理，并验证不再返回 SPA HTML。

### TASK-248 - 平台注册用户目录展示已加入组织

- status: `complete`
- priority: `high`
- owner_role: `platform-governance-agent`
- spec_path: `docs/specs/FEAT-141-platform-user-directory-organizations.md`
- task_status_path: `.claw/tasks/TASK-248.md`
- assignment_path: `.claw/assignments/TASK-248.yaml`
- next_action: 已发布 `2.8.19 / 99d4cc3cb206`；待受权平台会话复核真实目录展示。

### TASK-247 - 平台全量个人用户目录

- status: `done`
- priority: `high`
- owner_role: `platform-governance-agent`
- spec_path: `docs/specs/FEAT-140-platform-user-directory.md`
- task_status_path: `.claw/tasks/TASK-247.md`
- assignment_path: `.claw/assignments/TASK-247.yaml`
- next_action: 已合并 main 并发布 `2.8.15`；等待受权平台账号复核全量目录内容。

### TASK-246 - 租户详情路由标识兼容修复

- status: `done`
- priority: `high`
- owner_role: `frontend-platform-agent`
- spec_path: `docs/specs/FEAT-139-tenant-detail-route-id-compatibility.md`
- task_status_path: `.claw/tasks/TASK-246.md`
- assignment_path: `.claw/assignments/TASK-246.yaml`
- next_action: 已合并 main 并发布 `2.8.14`；等待受权平台账号复核真实详情页。

### TASK-245 - 前台会话内置组织管理入口与 Semattice 管理端切换

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-138-assistant-admin-session-entrypoint.md`
- task_status_path: `.claw/tasks/TASK-245.md`
- assignment_path: `.claw/assignments/TASK-245.yaml`
- next_action: 已合并 main 并发布 `2.8.17`；侧栏内产品菜单不再裁切，Semattice 顶栏可直接回到 AgentCiCi 管理端。等待真实组织管理员以既有统一登录会话验收。

### TASK-244 - OIDC 统一入口 state 修复

- status: `done`
- priority: `critical`
- owner_role: `integration-agent`
- spec_path: `docs/specs/FEAT-137-oidc-canonical-entrypoint-state.md`
- task_status_path: `.claw/tasks/TASK-244.md`
- assignment_path: `.claw/assignments/TASK-244.yaml`
- next_action: 已发布 `2.8.13`，等待真实用户 SSO 登录复验；若仍失败，查看当前生产 callback 日志并按 Cookie/Host 事实继续诊断。

### TASK-243 - Keycloak 统一身份与官方应用访问

- status: `done`
- priority: `critical`
- owner_role: `integration-agent`
- spec_path: `docs/specs/FEAT-136-keycloak-unified-identity-and-official-access.md`
- task_status_path: `.claw/tasks/TASK-243.md`
- assignment_path: `.claw/assignments/TASK-243.yaml`
- next_action: 已完成 Keycloak、OACT/JWKS、Semattice 联合上线及租户应用开通状态读取热修复；后续官方应用遵循既定 OACT/JWKS 契约接入。
### TASK-241 - AgentCiCi / Semattice 受控开户绑定

- status: `blocked`
- priority: `critical`
- owner_role: `integration-agent`
- spec_path: `docs/specs/FEAT-134-agentcici-semattice-controlled-provisioning.md`
- task_status_path: `.claw/tasks/TASK-241.md`
- assignment_path: `.claw/assignments/TASK-241.yaml`
- blocked_by: `Semattice production migration 13 requires a dedicated migrator connection not present in the runtime host configuration`
- next_action: 使用专用 migrator 显式执行 Semattice migration 13；随后重新部署并验证真实成功开户。

### TASK-240 - 混合智能体运行时 P6：公司隔离灰度与运营验证

- status: `blocked`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-240.md`
- assignment_path: `.claw/assignments/TASK-240.yaml`
- blocked_by: `production pilot company and agent selection`
- next_action: 默认关闭的公司 + Agent 双白名单与脱敏指标已合并 main；真实发布/试点只在用户指定目标与观察窗口后执行。

### TASK-239 - 混合智能体运行时 P5：Trace 运行执行投影与多主题界面

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-239.md`
- assignment_path: `.claw/assignments/TASK-239.yaml`
- blocked_by: `none`
- next_action: 已完成受权组织管理员的隔离 Trace 桌面验收并待集成；随后创建 P6 生产灰度与运营验证任务。

### TASK-238 - 混合智能体运行时 P4：受控 Reflect 与评测门禁

- status: `done`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-238.md`
- assignment_path: `.claw/assignments/TASK-238.yaml`
- blocked_by: `none`
- next_action: 已集成 `6817ba5`；P5 需先完成用户确认的 UI shape，再扩展现有 Trace 详情。

### TASK-237 - 混合智能体运行时 P3：规则优先模式路由

- status: `done`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-237.md`
- assignment_path: `.claw/assignments/TASK-237.yaml`
- blocked_by: `none`
- next_action: 已集成 `5c08c33`；后续由 TASK-238 受控接入 Reflect 与评测事实。

### TASK-236 - 混合智能体运行时 P2：Chat/OpenAPI 受限灰度

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-236.md`
- assignment_path: `.claw/assignments/TASK-236.yaml`
- blocked_by: `none`
- next_action: 已集成 `cbf9728`；后续由 TASK-237 增加服务端规则路由，不改变 P2 无工具边界。

### TASK-235 - 混合智能体运行时 P1：计划状态机基础

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-133-agent-runtime-mixed-orchestration.md`
- task_status_path: `.claw/tasks/TASK-235.md`
- assignment_path: `.claw/assignments/TASK-235.yaml`
- blocked_by: `none`
- next_action: 已集成 `fcc2200`；后续由 TASK-236 灰度接入聊天与 OpenAPI，仍保持工具确认与审计边界。

### TASK-233 - 通用记忆人工管理与生产就绪审计

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-233.md`
- assignment_path: `.claw/assignments/TASK-233.yaml`
- blocked_by: `none`
- next_action: 已完成生产就绪审计；未执行生产发布，等待用户授权后按 Runbook 进入发布流程。

### TASK-232 - 通用记忆审核 API 与质量门禁

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-232.md`
- assignment_path: `.claw/assignments/TASK-232.yaml`
- blocked_by: `none`
- next_action: 已完成候选治理 API、Trace/评测状态与两份独立通用适配契约。

### TASK-231 - 通用记忆生命周期与组织清理闭环

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-231.md`
- assignment_path: `.claw/assignments/TASK-231.yaml`
- blocked_by: `none`
- next_action: 已完成生命周期闭环，并在新建 PostgreSQL 库上通过平台 dry-run、导出与 real purge 6/6 集成验证。

### TASK-230 - 受认证凭据记忆上下文绑定

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-230.md`
- assignment_path: `.claw/assignments/TASK-230.yaml`
- blocked_by: `none`
- next_action: 已完成受认证凭据绑定和 OpenAPI 阻塞/流式可信上下文接入。

### TASK-229 - 通用可信运行时记忆上下文

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-229.md`
- assignment_path: `.claw/assignments/TASK-229.yaml`
- blocked_by: `none`
- next_action: 已完成可信运行时作用域、提示词预算、Trace 状态与内部聊天路径安全降级。

### TASK-228 - 通用记忆受控语义检索

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-228.md`
- assignment_path: `.claw/assignments/TASK-228.yaml`
- blocked_by: `none`
- next_action: 已完成 V87 受控索引、脱敏、二次授权与审核成功后的最佳努力索引；运行时接入须以独立任务继续。

### TASK-227 - 通用记忆候选、证据与时效治理

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-227.md`
- assignment_path: `.claw/assignments/TASK-227.yaml`
- blocked_by: `none`
- next_action: 已完成 V86 候选/证据与审核转化核心；向量检索、治理 API 与 Chat 编排须使用独立任务继续。

### TASK-226 - 通用主体记忆 Phase 1 核心

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-131-agent-memory-platform.md`
- task_status_path: `.claw/tasks/TASK-226.md`
- assignment_path: `.claw/assignments/TASK-226.yaml`
- blocked_by: `none`
- next_action: 已完成 Phase 1 通用核心和全新库 V1→V85 迁移验证；Phase 2 须另行创建授权任务，保持外部应用领域解耦。

### TASK-225 - 对话技能选择的强制执行上下文与可观测性

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-130-forced-skill-execution-context.md`
- task_status_path: `.claw/tasks/TASK-225.md`
- assignment_path: `.claw/assignments/TASK-225.yaml`
- blocked_by: `none`
- next_action: 已发布 `2.8.4 / 2f2f1a013ec2`；等待有授权的组织用户会话补做受保护工作台/Trace 的桌面交互复核。

### TASK-223 - 定时任务周期解析越界修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-128-schedule-cadence-parser-repair.md`
- task_status_path: `.claw/tasks/TASK-223.md`
- assignment_path: `.claw/assignments/TASK-223.yaml`
- blocked_by: `none`
- next_action: 已完成本地修复与定向验证；如需上线，按生产 Runbook 发布后观察首次真实创建。

### TASK-221 - 组织管理端全页面主题一致性治理

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-126-admin-theme-completeness-audit.md`
- task_status_path: `.claw/tasks/TASK-221.md`
- assignment_path: `.claw/assignments/TASK-221.yaml`
- blocked_by: `none`
- next_action: 使用已登录蓝色主题管理员逐页完成主体、弹窗、行菜单和折叠详情视觉复核。

### TASK-220 - 用户会话工作台浮层与操作面主题收敛

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-125-user-workbench-surface-polish.md`
- task_status_path: `.claw/tasks/TASK-220.md`
- assignment_path: `.claw/assignments/TASK-220.yaml`
- blocked_by: `none`
- next_action: 使用已登录蓝色主题账号完成弹窗、菜单与会话历史的桌面视觉验收。

### TASK-218 - 厂商模型目录能力边界

- status: `in_progress`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-123-provider-catalog-capability.md`
- task_status_path: `.claw/tasks/TASK-218.md`
- assignment_path: `.claw/assignments/TASK-218.yaml`
- blocked_by: `none`
- next_action: 移除 OneKeyToken 本地预设目录，未开放远程枚举时展示空目录。

### TASK-219 - 运营管理端信息架构与独立主题重构

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-124-platform-operations-information-architecture.md`
- task_status_path: `.claw/tasks/TASK-219.md`
- assignment_path: `.claw/assignments/TASK-219.yaml`
- blocked_by: `TASK-218 (PlatformModelsPage.tsx 子路由拆分)`
- next_action: 评审租户应用中心的前端实现；集成 FEAT-134 的应用状态读取投影后补齐刷新后的持久状态回读。

### TASK-217 - 智能体定时任务真实创建与链路事实纠偏

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-122-runtime-execution-trace-correction.md`
- task_status_path: `.claw/tasks/TASK-217.md`
- assignment_path: `.claw/assignments/TASK-217.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.12 / b20261d8b89b`; await an authorized user’s first real schedule creation for business-path observation.

### TASK-215 - 链路追踪全文查看与复制

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-120-trace-full-detail-expansion.md`
- task_status_path: `.claw/tasks/TASK-215.md`
- assignment_path: `.claw/assignments/TASK-215.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.11 / 281f35b2cb2f`; retain `2.7.10` as the application rollback target.

### TASK-216 - 四套主题风格智能体对话工作台设计探索

- status: `done`
- priority: `medium`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-121-four-theme-conversation-workbench-design.md`
- task_status_path: `.claw/tasks/TASK-216.md`
- assignment_path: `.claw/assignments/TASK-216.yaml`
- blocked_by: `none`
- next_action: Done; choose one direction before creating any implementation task, with production theme facts unchanged.

### TASK-214 - OneKeyToken 实时凭据检测修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-119-onekeytoken-live-validation.md`
- task_status_path: `.claw/tasks/TASK-214.md`
- assignment_path: `.claw/assignments/TASK-214.yaml`
- blocked_by: `none`
- next_action: 已在生产 `2.8.1 / 9bc8510cbede` 完成；运营人员可使用受保护账号对真实业务 Key 验收，检测草稿不会持久化。

### TASK-210 - 客户互动工作台标准渠道图标治理

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md`
- task_status_path: `.claw/tasks/TASK-210.md`
- assignment_path: `.claw/assignments/TASK-210.yaml`
- blocked_by: `none`
- next_action: Production runs integrated `2.7.5` with TASK-210 preserved; complete its independent AgentCiCi plus CloudCC embed visual evidence and close TASK-210.

### TASK-207 - 前台主题一致性与视觉对齐全量治理

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md`
- task_status_path: `.claw/tasks/TASK-207.md`
- assignment_path: `.claw/assignments/TASK-207.yaml`
- blocked_by: `none`
- next_action: Done on `codex/TASK-207-frontend-theme-alignment-audit`; merge after review, with production release handled separately.

### TASK-206 - CloudCC 嵌入身份同步自动恢复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md`
- task_status_path: `.claw/tasks/TASK-206.md`
- assignment_path: `.claw/assignments/TASK-206.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.11`; monitor CloudCC session validation, identity mapping rejects and ticket/consume success rates.

### TASK-205 - CRM 经营分析 Skill 与产品销售演示数据

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-111-crm-business-analysis-skill.md`
- task_status_path: `.claw/tasks/TASK-205.md`
- assignment_path: `.claw/assignments/TASK-205.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.8 / 095094300a25`; monitor the deterministic CRM sales-rank route and expand only through governed high-level analysis tools.

### TASK-204 - 智能体构建说明与头像交互精修

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-110-agent-builder-guide-avatar-polish.md`
- task_status_path: `.claw/tasks/TASK-204.md`
- assignment_path: `.claw/assignments/TASK-204.yaml`
- blocked_by: `none`
- next_action: Done on `codex/TASK-204-agent-builder-avatar-polish`; integrate through the normal branch workflow.

### TASK-203 - 客户互动工作台全场景演示数据

- status: `in_progress`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-109-customer-workbench-comprehensive-demo-scenarios.md`
- task_status_path: `.claw/tasks/TASK-203.md`
- assignment_path: `.claw/assignments/TASK-203.yaml`
- blocked_by: `none`
- next_action: Extend the idempotent V2 seed, back up production, write CRM/AgentCiCi demo facts, and verify every queue and detail scenario as Owen/SalesA.

### TASK-202 - 用户级产品主题偏好

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-108-user-selectable-product-themes.md`
- task_status_path: `.claw/tasks/TASK-202.md`
- assignment_path: `.claw/assignments/TASK-202.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.6`; monitor theme switching, transparent structural wrappers and fixed avatar geometry.

### TASK-201 - 智能体构建页布局与模型治理收敛

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md`
- task_status_path: `.claw/tasks/TASK-201.md`
- assignment_path: `.claw/assignments/TASK-201.yaml`
- blocked_by: `none`
- next_action: Merged to `origin/main`; production release was not requested.

### TASK-200 - 多租户智能体评测控制面生产落地

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-106-multi-tenant-agent-evaluation-control-plane.md`
- task_status_path: `.claw/tasks/TASK-200.md`
- assignment_path: `.claw/assignments/TASK-200.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.4`; monitor evaluation coverage, P0/security regressions, stale results and release-gate outcomes.

### TASK-199 - 互动驱动的客户经营动作

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-105-interaction-driven-customer-actions.md`
- task_status_path: `.claw/tasks/TASK-199.md`
- assignment_path: `.claw/assignments/TASK-199.yaml`
- blocked_by: `TASK-198`
- next_action: Done in production `2.6.2`; monitor candidate precision, dismissal feedback, deduplication and conversion to confirmed CRM writes.

### TASK-198 - AI 动态客户信号与可解释评分升级

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-104-ai-dynamic-customer-scoring.md`
- task_status_path: `.claw/tasks/TASK-198.md`
- assignment_path: `.claw/assignments/TASK-198.yaml`
- blocked_by: `none`
- next_action: Done in production `2.6.1`; monitor pending-confidence rate, signal quality and score drift.

### TASK-197 - 客户互动档案、动态记忆与按需检索落地

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-103-customer-interaction-archive-memory-retrieval.md`
- task_status_path: `.claw/tasks/TASK-197.md`
- assignment_path: `.claw/assignments/TASK-197.yaml`
- blocked_by: `none`
- next_action: Done in production `2.5.11`; monitor archive growth, ACTIVE memory resolution and assistant evidence quality.

### TASK-191 - CloudCC 嵌入页重复刷新与客户信号并发修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-099-cloudcc-embed-remount-signal-idempotency.md`
- task_status_path: `.claw/tasks/TASK-191.md`
- assignment_path: `.claw/assignments/TASK-191.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.12`; monitor repeated CRM refresh and customer signal write errors.

### TASK-190 - CloudCC 嵌入端会话失效自动恢复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-098-cloudcc-session-refresh.md`
- task_status_path: `.claw/tasks/TASK-190.md`
- assignment_path: `.claw/assignments/TASK-190.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.9`; monitor CloudCC session refresh frequency and authentication failures.

### TASK-189 - 客户互动多模态采集与确认归集

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-097-multimodal-interaction-ingestion.md`
- task_status_path: `.claw/tasks/TASK-189.md`
- assignment_path: `.claw/assignments/TASK-189.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.8`; monitor extraction quality, processing latency and confirmed CRM timeline records.

### TASK-188 - 客户互动工作台标题与静态链接控件修复

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-096-customer-workbench-title-static-link.md`
- task_status_path: `.claw/tasks/TASK-188.md`
- assignment_path: `.claw/assignments/TASK-188.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.7`; retain the application title and static pointer states.

### TASK-187 - AI 应用壳层导航稳定性治理

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-095-ai-app-shell-navigation-stability.md`
- task_status_path: `.claw/tasks/TASK-187.md`
- assignment_path: `.claw/assignments/TASK-187.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.6`; reuse shell navigation geometry and menu semantics on future pages.

### TASK-186 - 产品控件去框化与客户互动工作台全页治理

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-094-customer-workbench-control-chrome-cleanup.md`
- task_status_path: `.claw/tasks/TASK-186.md`
- assignment_path: `.claw/assignments/TASK-186.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.5`; reuse the shared control primitives on future product pages.

### TASK-185 - 客户互动工作台 AI 助理展开模式

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-093-customer-assistant-expand-mode.md`
- task_status_path: `.claw/tasks/TASK-185.md`
- assignment_path: `.claw/assignments/TASK-185.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.4`; monitor expanded assistant usage and long-answer readability.

### TASK-184 - 客户互动工作台左侧队列横向裁切热修

- status: `done`
- priority: `critical`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-092-customer-workbench-ui-streaming.md`
- task_status_path: `.claw/tasks/TASK-184.md`
- assignment_path: `.claw/assignments/TASK-184.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.3`; monitor queue clipping at non-default browser zoom levels.

### TASK-183 - 客户互动工作台界面规范化与流式助理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-092-customer-workbench-ui-streaming.md`
- task_status_path: `.claw/tasks/TASK-183.md`
- assignment_path: `.claw/assignments/TASK-183.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.2`; monitor streaming completion, disconnect noise and assistant response quality.

### TASK-182 - 客户互动工作台生产闭环

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-182.md`
- assignment_path: `.claw/assignments/TASK-182.yaml`
- blocked_by: `none`
- next_action: Done in production `2.4.1`; monitor real-user CRM writes, assistant latency and recommendation quality.

### TASK-181 - 客户互动工作台客户列表排版修复

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-091-customer-workbench-account-list-alignment.md`
- task_status_path: `.claw/tasks/TASK-181.md`
- assignment_path: `.claw/assignments/TASK-181.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.9`; monitor customer workbench account list row alignment and truncation.

### TASK-180 - AI 应用页与客户互动工作台 UI 重构

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-090-ai-apps-workbench-ui-refactor.md`
- task_status_path: `.claw/tasks/TASK-180.md`
- assignment_path: `.claw/assignments/TASK-180.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.8`; monitor AI 应用悬浮菜单、客户互动工作台外层滚动条和演示组织工作台数据。

### TASK-179 - AI 听记实时发言人分离热修

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md`
- task_status_path: `.claw/tasks/TASK-179.md`
- assignment_path: `.claw/assignments/TASK-179.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.7`; monitor real multi-speaker meetings, configured Iflytek auto-selection, and explicit Aliyun fallback notices for unconfigured organizations.

### TASK-178 - CRM 嵌入客户互动工作台语音输入热修

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md`
- task_status_path: `.claw/tasks/TASK-178.md`
- assignment_path: `.claw/assignments/TASK-178.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.5`; monitor CRM embedded customer workbench microphone permission and ASR startup error reporting.

### TASK-175 - 客户互动工作台外层滚动与 CRM 主页按钮清理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-085-customer-workbench-scroll-cleanup.md`
- task_status_path: `.claw/tasks/TASK-175.md`
- assignment_path: `.claw/assignments/TASK-175.yaml`
- blocked_by: `none`
- next_action: Done in production `2.3.4`; monitor customer workbench platform/embed outer scrolling and CloudCC custom page `V3.0` component binding.

### TASK-174 - 数据洞察 AI 应用生产发布

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-084-data-insight-ai-app.md`
- task_status_path: `.claw/tasks/TASK-174.md`
- assignment_path: `.claw/assignments/TASK-174.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.3.2`; monitor 数据洞察 dashboard, demo org `REAL_CRM_DEMO` source labeling, and onechat DNS risk.

### TASK-173 - 客户互动工作台真实智能体助理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md`
- task_status_path: `.claw/tasks/TASK-173.md`
- assignment_path: `.claw/assignments/TASK-173.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.3.1`; monitor customer workbench real agent assistant, `/ws/asr` microphone path, and demo org `org2sva14i4udjmi2t4s`.

### TASK-172 - 双环境真实演示数据建设

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-082-demo-environment-real-data.md`
- task_status_path: `.claw/tasks/TASK-172.md`
- assignment_path: `.claw/assignments/TASK-172.yaml`
- blocked_by: `none`
- next_action: Done; monitor AgentCiCi `org2sva14i4udjmi2t4s` and CloudCC CRM `org0720f814430017229` demo data before customer demos.

### TASK-171 - 客户互动工作台生产就绪

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-171.md`
- assignment_path: `.claw/assignments/TASK-171.yaml`
- blocked_by: `none`
- next_action: Done through production release `2.2.7` and CloudCC pagecomponent V8 real CRM validation; continue tracking the `cc-customization-expert-msapi` bind pagecomponent write failure as a skill gap.

### TASK-170 - 安全规则平台与输入输出安全网关

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-080-security-rules-platform.md`
- task_status_path: `.claw/tasks/TASK-170.md`
- assignment_path: `.claw/assignments/TASK-170.yaml`
- blocked_by: `none`
- next_action: 已合并主线并通过本地集成回归；等待独立的已认证桌面与生产验收。

### TASK-169 - 独立数据清洗与智能标注平台能力

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-079-kb-data-quality-annotation.md`
- task_status_path: `.claw/tasks/TASK-169.md`
- assignment_path: `.claw/assignments/TASK-169.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.2.1`; monitor `/admin/data-quality`, data-quality API, and「知微画像」AI 应用.

### TASK-168 - ASR WebSocket 鉴权与线上语音入口修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-078-asr-websocket-auth-hotfix.md`
- task_status_path: `.claw/tasks/TASK-168.md`
- assignment_path: `.claw/assignments/TASK-168.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.12`; user should retest AI 听记 and chat microphone from the browser.

### TASK-167 - RAG 检索路由策略化改造

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-077-rag-router-policy.md`
- task_status_path: `.claw/tasks/TASK-167.md`
- assignment_path: `.claw/assignments/TASK-167.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.11`; monitor RAG Router trace metadata and KB retrieval behavior.

### TASK-166 - 产品功能类知识库触发与伪工具标签防护

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-076-product-kb-trigger-and-pseudo-tool-guard.md`
- task_status_path: `.claw/tasks/TASK-166.md`
- assignment_path: `.claw/assignments/TASK-166.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.10`; monitor product-feature KB retrieval traces.

### TASK-165 - 智能体绑定知识库运行时检索触发修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-075-agent-kb-runtime-retrieval.md`
- task_status_path: `.claw/tasks/TASK-165.md`
- assignment_path: `.claw/assignments/TASK-165.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.9`; monitor Agent-bound KB retrieval traces.

### TASK-164 - Qdrant 向量维度漂移修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-074-qdrant-dimension-repair.md`
- task_status_path: `.claw/tasks/TASK-164.md`
- assignment_path: `.claw/assignments/TASK-164.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.8`; monitor KB upload and vector indexing.

### TASK-163 - 邮件 ID 刷新重试与语音后续可用性修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-073-email-id-refresh-and-voice-followup.md`
- task_status_path: `.claw/tasks/TASK-163.md`
- assignment_path: `.claw/assignments/TASK-163.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.7`; user should retest the same email body continuation and voice input flow.

### TASK-162 - 连续确认后的邮件正文工具续执行

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-072-continuous-tool-execution-confirmation.md`
- task_status_path: `.claw/tasks/TASK-162.md`
- assignment_path: `.claw/assignments/TASK-162.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.6`; monitor confirmed email-body continuation behavior.

### TASK-161 - 对话邮件正文展示与语音输入识别修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-071-mail-body-and-voice-input-fix.md`
- task_status_path: `.claw/tasks/TASK-161.md`
- assignment_path: `.claw/assignments/TASK-161.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.5`; monitor dialog mail-body and voice-input behavior.

### TASK-159 - Chat session state tenant primary key hotfix

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-069-chat-session-state-tenant-key-hotfix.md`
- task_status_path: `.claw/tasks/TASK-159.md`
- assignment_path: `.claw/assignments/TASK-159.yaml`
- blocked_by: `none`
- next_action: Done in production release `2.1.4`; monitor chat logs and follow up on ACR push durability.

### TASK-158 - Agent runtime concurrency hardening

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-068-agent-runtime-concurrency-hardening.md`
- task_status_path: `.claw/tasks/TASK-158.md`
- assignment_path: `.claw/assignments/TASK-158.yaml`
- blocked_by: `none`
- next_action: Included in production release `2.1.3`; monitor runtime concurrency behavior with normal production traffic.

### TASK-156 - Agent Builder production readiness closure

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-066-agent-builder-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-156.md`
- assignment_path: `.claw/assignments/TASK-156.yaml`
- blocked_by: `none`
- next_action: Review TASK-156 production-readiness closure; focused backend integration, frontend build, real-backend desktop validation, and readiness/evaluation gate smoke passed.

### TASK-157 - Enterprise knowledge platform readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-067-enterprise-knowledge-platform-readiness.md`
- task_status_path: `.claw/tasks/TASK-157.md`
- assignment_path: `.claw/assignments/TASK-157.yaml`
- blocked_by: `none`
- next_action: Review TASK-157 enterprise KB closure; focused backend integration, frontend build, real-backend desktop validation, Rabbit/Qdrant smoke, and drift audit evidence passed.

### TASK-155 - 运营端前端页面 UI 整体美化

- status: `review`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-065-platform-console-ui-polish.md`
- task_status_path: `.claw/tasks/TASK-155.md`
- assignment_path: `.claw/assignments/TASK-155.yaml`
- blocked_by: `none`
- next_action: Review TASK-155 platform UI polish; task gates, frontend build, `git diff --check`, and desktop Playwright screenshots for all platform routes passed, with `/api/platform/audit/logs` still returning backend 500.

### TASK-154 - Credits metering production readiness sweep

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-154.md`
- assignment_path: `.claw/assignments/TASK-154.yaml`
- blocked_by: `none`
- next_action: Review TASK-154 Credits metering completion; assignment/login gates, Open API/KB/admin billing focused tests, and `git diff --check` passed.

### TASK-153 - Platform-governed Tavily, Iflytek, and OneKeyToken provider

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-153.md`
- assignment_path: `.claw/assignments/TASK-153.yaml`
- blocked_by: `none`
- next_action: Review TASK-153 changes; focused backend tests, frontend build, local API smoke, local service restart, and `git diff --check` passed.

### TASK-152 - AI 听记 credits and start-timeout hotfix

- status: `in_progress`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-152.md`
- assignment_path: `.claw/assignments/TASK-152.yaml`
- blocked_by: `none`
- next_action: Fix local-test defects where AI 听记 start can time out and successful AI 听记 usage does not consume organization credits; then run focused backend validation and local smoke.

### TASK-151 - RBAC and audit production readiness

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-064-rbac-production-readiness.md`
- task_status_path: `.claw/tasks/TASK-151.md`
- assignment_path: `.claw/assignments/TASK-151.yaml`
- blocked_by: `none`
- next_action: Done; release `2.1.2` is deployed and `/platform/audit` loads without the production `text ~~ bytea` error.

### TASK-150 - Knowledge Base production readiness

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-150.md`
- assignment_path: `.claw/assignments/TASK-150.yaml`
- blocked_by: `none`
- next_action: Review and merge TASK-150 production-readiness implementation; frontend build, desktop smoke, backend compile, assignment check, KB lifecycle integration, and Qdrant stack smoke all passed.

### TASK-149 - Knowledge Base DOCX upload parser

- status: `review`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-149.md`
- assignment_path: `.claw/assignments/TASK-149.yaml`
- blocked_by: `none`
- next_action: Review and merge `codex/TASK-149-kb-docx-upload-parser`; local KB lifecycle regression and static diff checks passed.

### TASK-146 - 观测与运维生产就绪收口

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-019-agent-observability-monitoring.md`
- task_status_path: `.claw/tasks/TASK-146.md`
- assignment_path: `.claw/assignments/TASK-146.yaml`
- blocked_by: `none`
- next_action: Review TASK-146 local changes; merge after normal integration gates. State validation has an existing TASK-143 line-budget blocker outside this task.

### TASK-148 - Production domain cutover

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-063-production-domain-cutover.md`
- task_status_path: `.claw/tasks/TASK-148.md`
- assignment_path: `.claw/assignments/TASK-148.yaml`
- blocked_by: `none`
- next_action: Monitor production traffic and update any external integrations still using retired hostnames.

### TASK-147 - WeCom customer-service connection test

- status: `review`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `.claw/tasks/TASK-147.md`
- assignment_path: `.claw/assignments/TASK-147.yaml`
- blocked_by: `none`
- next_action: Review TASK-147 changes, then run live WeCom callback smoke once real account details and filed callback domain are available.

### TASK-145 - Platform model provider governance

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-062-platform-model-provider-governance.md`
- task_status_path: `.claw/tasks/TASK-145.md`
- assignment_path: `.claw/assignments/TASK-145.yaml`
- blocked_by: `none`
- next_action: Included in the local `main` integration merge; run focused backend/frontend integration gates before marking done.

### TASK-144 - AgentCiCi public website restructure

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-061-agentcici-public-website-restructure.md`
- task_status_path: `.claw/tasks/TASK-144.md`
- assignment_path: `.claw/assignments/TASK-144.yaml`
- blocked_by: `none`
- next_action: Released to production in `2.1.3`; monitor website demo booking records.

### TASK-143 - Billing editions configurable in platform operations

- status: `review`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-143.md`
- assignment_path: `.claw/assignments/TASK-143.yaml`
- blocked_by: `none`
- next_action: Local `main` MR validation passed after unified Credits billing presentation; publish/update the Codeup MR for review and merge.

### TASK-142 - OpenAPI chat-messages SSE streaming

- status: `ready`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-060-openapi-chat-messages-sse-streaming.md`
- task_status_path: `.claw/tasks/TASK-142.md`
- assignment_path: `.claw/assignments/TASK-142.yaml`
- blocked_by: `TASK-140 may change the final public route shape`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-142-openapi-sse-streaming`, then implements true SSE streaming for OpenAPI `chat-messages`.

### TASK-141 - AI 听记本地 FunASR 实时转写

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-059-ai-minutes-local-asr.md`
- task_status_path: `.claw/tasks/TASK-141.md`
- assignment_path: `.claw/assignments/TASK-141.yaml`
- blocked_by: `none`
- next_action: `DEV-houyi` runs task-scoped `dev-login.py` on `codex/TASK-141-local-funasr-realtime-asr`, then implements the local FunASR realtime ASR sidecar and `/ws/asr?provider=local` integration.

### TASK-140 - Remove Agent ID from public OpenAPI routes

- status: `in_progress`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-058-openapi-agentless-endpoints.md`
- task_status_path: `.claw/tasks/TASK-140.md`
- assignment_path: `.claw/assignments/TASK-140.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` completes review fixes / service validation for `codex/TASK-140-openapi-agentless-endpoints`; task status is the progress source.

### TASK-139 - Agent list OpenAPI badge shows only first Agent

- status: `ready`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-139.md`
- assignment_path: `.claw/assignments/TASK-139.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-139-agent-list-openapi-badge`, then fixes list channel data and badge rendering.

### TASK-138 - OpenAPI docs copy cleanup

- status: `ready`
- priority: `medium`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-057-openapi-docs-copy-cleanup.md`
- task_status_path: `.claw/tasks/TASK-138.md`
- assignment_path: `.claw/assignments/TASK-138.yaml`
- blocked_by: `TASK-140 may change the final route examples`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-138-openapi-docs-copy-cleanup`, then updates OpenAPI docs copy.

### TASK-137 - Custom Agent delete action

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-056-custom-agent-delete.md`
- task_status_path: `.claw/tasks/TASK-137.md`
- assignment_path: `.claw/assignments/TASK-137.yaml`
- blocked_by: `none`
- next_action: code is complete on `codex/TASK-137-custom-agent-delete`; review/merge after backend integration rerun when local PostgreSQL is available.

### TASK-136 - Frontend auth token sync across tabs

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-055-frontend-auth-token-sync.md`
- task_status_path: `.claw/tasks/TASK-136.md`
- assignment_path: `.claw/assignments/TASK-136.yaml`
- blocked_by: `none`
- next_action: `DEV-fengchu` runs task-scoped `dev-login.py` on `codex/TASK-136-frontend-auth-token-sync`, then implements shared token sync.

### TASK-133 - Agent Builder no-model new-Agent model-config redirect

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-133.md`
- assignment_path: `.claw/assignments/TASK-133.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-133-agent-builder-new-agent-model-config-fix`; mark `done` after merge verification.

### TASK-132 - Agent Builder focused-agent skill binding refresh bugfix

- status: `review`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-132.md`
- assignment_path: `.claw/assignments/TASK-132.yaml`
- blocked_by: `none`
- next_action: PM/integration owner reviews and merges `codex/TASK-132-agent-builder-skill-refresh-bugfix`; mark `done` after merge verification.

### TASK-116 - Skill module completion and optimization

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-038-admin-skill-module-completion.md`
- task_status_path: `.claw/tasks/TASK-116.md`
- assignment_path: `.claw/assignments/TASK-116.yaml`
- blocked_by: `none`
- next_action: `DEV-wolong` runs task-scoped `dev-login.py`, closes P0 security/regression gaps, then continues P1/P2 work.

### TASK-115 - Knowledge base module maintenance

- status: `ready`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `.claw/tasks/TASK-115.md`
- assignment_path: `.claw/assignments/TASK-115.yaml`
- blocked_by: `none`
- next_action: `DEV-zhongda` runs task-scoped `dev-login.py`, executes the P0 hardening package, then continues P1/P2 work.

### TASK-114 - FEAT-037 SaaS billing usage ledger

- status: `ready`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-037-saas-billing-usage-ledger.md`
- task_status_path: `.claw/tasks/TASK-114.md`
- assignment_path: `.claw/assignments/TASK-114.yaml`
- blocked_by: `none`
- next_action: `MANAGER-001` runs task-scoped `dev-login.py` and continues the end-to-end billing ledger implementation.

## Backlog / Blocked

### TASK-096 - End-to-end CRM embed verification

- status: `blocked`
- priority: `high`
- owner_role: `qa-agent`
- spec_path: `docs/specs/FEAT-032-meeting-minutes-embed-sdk.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC iframe host smoke and ACR hotfix persistence are still open`
- next_action: Confirm the iframe host on the real CloudCC page, then repair ACR credentials and persist the deployed hotfix image.

### TASK-036 - Skill declarative API runtime

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-015-skill-declarative-api-runtime.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Real external API smoke still depends on TASK-023 runtime prerequisites`
- next_action: Close the runtime prerequisites, then finish real external API smoke and browser-level admin verification.

### TASK-023 - CloudCC runtime smoke unblock

- status: `blocked`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `CloudCC runtime credentials and local Aliyun API key are not yet verified`
- next_action: Rotate and verify `cc_username/cc_safetymark`, restore a usable local Aliyun API key, then rerun the real `/ai/chat` and CloudCC tool chain smoke.

### TASK-020 - Knowledge base lifecycle completion

- status: `blocked`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-008-knowledge-base-lifecycle-completion.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `User explicitly paused FEAT-008 continuation`
- next_action: Resume only when requested; restart from page-level regression on document/settings/chunk dialogs.

### TASK-070 - AgentCiCi market positioning and roadmap

- status: `todo`
- priority: `high`
- owner_role: `human`
- spec_path: `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `Awaiting a shaped follow-up request`
- next_action: Reuse FEAT-025 as the scope source when the next strategy or packaging task is opened.

### TASK-063 - AI native after-sales agent spec

- status: `todo`
- priority: `high`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-023-ai-native-after-sales-agent.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `WeCom customer-service account details and data mapping are not yet confirmed`
- next_action: Confirm `open_kfid`, CorpID/secret, Token/AESKey, run-as service user, and first-wave after-sales data sources before implementation resumes.

### TASK-007 - SaaS billing and packaging design

- status: `todo`
- priority: `medium`
- owner_role: `shared`
- spec_path: `docs/specs/FEAT-003-saas-billing-and-packaging.md`
- task_status_path: `none`
- assignment_path: `none`
- blocked_by: `none`
- next_action: If reopened, start with usage meter events, package/subscription entities, and the admin billing overview before any payment-provider work.

## Completed Tasks

### TASK-242 - 顶层租户 company_id 统一

- status: `done`
- priority: `critical`
- owner_role: `integration-agent`
- spec_path: `docs/specs/FEAT-135-company-id-unification.md`
- task_status_path: `.claw/tasks/TASK-242.md`
- assignment_path: `.claw/assignments/TASK-242.yaml`
- next_action: 已发布生产 `2.8.9 / 0194706`，V94/V95 成功且六服务健康；后续受控开户由运营账号按 company_id 契约验收。

### TASK-234 - 发布修订版本号上限调整为365

- status: `done`
- priority: `medium`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-132-release-version-patch-limit.md`
- task_status_path: `.claw/tasks/TASK-234.md`
- assignment_path: `.claw/assignments/TASK-234.yaml`
- blocked_by: `none`
- next_action: 修订段上限已调整为365；下次发布前仍按标准 dry-run 生成版本。

### TASK-224 - 生产发布构造器注入启动热修

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-129-release-startup-constructor-injection-hotfix.md`
- task_status_path: `.claw/tasks/TASK-224.md`
- assignment_path: `.claw/assignments/TASK-224.yaml`
- blocked_by: `none`
- next_action: 已发布 `2.8.3 / 651bc2294bee`；保留失败的 2.8.2 tag 作为不可变诊断证据。

### TASK-222 - 本地遗留分支审查与主线整合

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-127-local-branch-integration.md`
- task_status_path: `.claw/tasks/TASK-222.md`
- assignment_path: `.claw/assignments/TASK-222.yaml`
- blocked_by: `none`
- next_action: TASK-170 与 TASK-219 worktree 已整合到 main；安全规则迁移已从 V71 重编号为 V84，避免 Flyway 乱序。

### TASK-213 - 通用本体建模与语义查询平台 V1

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-118-general-ontology-modeling-platform.md`
- task_status_path: `.claw/tasks/TASK-213.md`
- assignment_path: `.claw/assignments/TASK-213.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.10 / f922b86f1884`; monitor health and restore a valid per-user CloudCC session before completing live CRM metadata discovery. Any ontology V2 expansion requires a separate task.

### TASK-212 - Skill DAG 只读治理闭环 Phase 1

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-117-skill-dag-governance-phase1.md`
- task_status_path: `.claw/tasks/TASK-212.md`
- assignment_path: `.claw/assignments/TASK-212.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.8 / 4814d2b9534d`; monitor health and DAG query latency, and scope any editable or Skill-to-Skill graph work as a separate Phase 2.

### TASK-211 - CRM 确定性回答真实流式输出纠偏

- status: `done`
- priority: `critical`
- owner_role: `backend-agent`
- spec_path: `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- task_status_path: `.claw/tasks/TASK-211.md`
- assignment_path: `.claw/assignments/TASK-211.yaml`
- blocked_by: `none`
- next_action: 生产 `2.7.7 / e47979167af8` 已通过协议、持久化、权限隔离、清理、防泄漏、干净日志及应用内 Browser 同气泡 partial/final、console、overflow 全部门禁；TASK-211 已关闭。

### TASK-208 - CRM 产品销售经营分析稳定性与深度治理

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- task_status_path: `.claw/tasks/TASK-208.md`
- assignment_path: `.claw/assignments/TASK-208.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.5 / be80eea665c0`; monitor CRM source-field coverage and keep the governed SalesA demo batch idempotent.

### TASK-209 - 运营平台登录页原图像素锁定复刻

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md`
- task_status_path: `.claw/tasks/TASK-209.md`
- assignment_path: `.claw/assignments/TASK-209.yaml`
- blocked_by: `none`
- next_action: Done in production `2.7.2 / ddcda0ef6111`; preserve the released background asset and transparent interaction layer in later releases.

### TASK-196 - 客户互动整理上下文与队列丢失修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-102-customer-workbench-context-stability.md`
- task_status_path: `.claw/tasks/TASK-196.md`
- assignment_path: `.claw/assignments/TASK-196.yaml`
- blocked_by: `none`
- next_action: Done in production `2.5.9`; monitor interaction confirmation latency and customer-context stability.

### TASK-195 - 客户互动时间线完整年份显示

- status: `done`
- priority: `high`
- owner_role: `frontend-agent`
- spec_path: `docs/specs/FEAT-081-customer-interaction-workbench.md`
- task_status_path: `.claw/tasks/TASK-195.md`
- assignment_path: `.claw/assignments/TASK-195.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.8` shows four-digit years in compact and full customer interaction timelines.

### TASK-194 - 全量客户名称搜索与产品输入焦点治理

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-101-global-customer-search-and-field-focus.md`
- task_status_path: `.claw/tasks/TASK-194.md`
- assignment_path: `.claw/assignments/TASK-194.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.6` searches all CloudCC Accounts visible to the current identity and applies the shared single-layer focus rule.

### TASK-193 - 客户队列默认按最近互动倒序

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-100-large-crm-organization-async-sync.md`
- task_status_path: `.claw/tasks/TASK-193.md`
- assignment_path: `.claw/assignments/TASK-193.yaml`
- blocked_by: `none`
- next_action: `none`; production `2.5.3` defaults both queues to recent-interaction descending order.

### TASK-192 - 大数据量 CRM 组织异步初始化与 504 修复

- status: `done`
- priority: `critical`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-100-large-crm-organization-async-sync.md`
- task_status_path: `.claw/tasks/TASK-192.md`
- assignment_path: `.claw/assignments/TASK-192.yaml`
- blocked_by: `none`
- next_action: Monitor sync duration and create a separate incremental-projection task before removing the 10,000-record ceiling.

### TASK-134 - AI minutes local audio upload and speaker diarization

- status: `done`
- priority: `high`
- owner_role: `fullstack-agent`
- spec_path: `docs/specs/FEAT-054-ai-minutes-local-audio-upload.md`
- task_status_path: `.claw/tasks/TASK-134.md`
- assignment_path: `.claw/assignments/TASK-134.yaml`
- blocked_by: `none`
- next_action: `none`; local upload, 百炼 Fun-ASR speaker diarization, transcript normalization, and Markdown `下载转写` are complete. The remaining whole-file reset was confirmed as a local-network limitation; online environment is normal.

### TASK-124 - FEAT-046 platform tenant manual provisioning and lifecycle split

- status: `done`
- priority: `critical`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md`
- task_status_path: `.claw/tasks/TASK-124.md`
- assignment_path: `.claw/assignments/TASK-124.yaml`
- blocked_by: `none`
- next_action: `none`; platform tenant list/detail split, manual provisioning, backend provisioning path, focused tests, and desktop QA are complete.

### TASK-135 - Clear default login account values

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/PROJECT-BASELINE.md`
- task_status_path: `.claw/tasks/TASK-135.md`
- assignment_path: `.claw/assignments/TASK-135.yaml`
- blocked_by: `none`
- next_action: `none`; assistant, admin, and platform login account inputs now start empty and passed static search, frontend build, and browser checks.

### TASK-131 - Platform account orgless auth context

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-053-platform-account-orgless-auth-context.md`
- task_status_path: `.claw/tasks/TASK-131.md`
- assignment_path: `.claw/assignments/TASK-131.yaml`
- blocked_by: `none`
- next_action: `none`; Codeup change/6 was merged with post-merge state, frontend, backend compile, script, and diff verification. Rerun `PlatformAuthIntegrationTest` later when local Docker/Postgres is available.

### TASK-130 - ACR release version governance and app version badge

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-052-acr-release-version-governance.md`
- task_status_path: `.claw/tasks/TASK-130.md`
- assignment_path: `.claw/assignments/TASK-130.yaml`
- blocked_by: `none`
- next_action: `none`; production releases now use `docs/production-release-runbook.md` and `scripts/release-acr.sh` for one canonical version across ACR tags, Git tag, backend metadata, frontend badge, and deploy env.

### TASK-129 - Admin login organization-selection alignment

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`
- task_status_path: `.claw/tasks/TASK-129.md`
- assignment_path: `.claw/assignments/TASK-129.yaml`
- blocked_by: `none`
- next_action: `none`; `/admin/login` now removes the orgId field, supports organization selection after account login, and passed desktop/mobile QA.

### TASK-127 - Merge remaining local branches into the current branch

- status: `done`
- priority: `high`
- owner_role: `project-manager`
- spec_path: `docs/specs/FEAT-047-local-branch-integration-pass.md`
- task_status_path: `.claw/tasks/TASK-127.md`
- assignment_path: `.claw/assignments/TASK-127.yaml`
- blocked_by: `none`
- next_action: `none`; remaining local branches are integrated and the dirty worktree has been restored on `codex/TASK-124-feat-046-platform-tenant-provisioning`.

## Maintenance Rules

- Keep each task card under 20 lines.
- Store only index fields here.
- Store current task details in `.claw/tasks/TASK-xxx.md`.
- Store old completed, superseded, and historical task cards in `.claw/task-archive.md`.
