---
kind: task-status
task_id: TASK-241
status: blocked
updated_at: 2026-07-23T08:50:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-241.yaml
spec_path: docs/specs/FEAT-134-agentcici-semattice-controlled-provisioning.md
---

# TASK-241 - AgentCiCi / Semattice 受控开户绑定

## Scope

- AgentCiCi 维护组织有效性与 Semattice 绑定的唯一事实；Semattice 只能对已存在、可开通的 AgentCiCi `org_id` 创建公司投影。
- 实现双向 HMAC 服务认证、五分钟时间窗、nonce 防重放、幂等 reservation 与完成回写。
- 平台运营端可主动发起；其他受信外部系统直接向 Semattice 受控入站接口发起，但仍须通过 AgentCiCi 验证。

## Non-goals

- 不传输、存储或记录共享密钥；不接受用户 JWT 作为服务认证；不通过前端直接开户。
- 不迁移既有组织 ID，不改变组织生命周期、业务 RBAC 或生产数据。

## Progress

- 已完成 V93 reservation binding、严格 `org_id` 与幂等键校验、回写状态机、审计和 Semattice-only HMAC 验证。
- 已完成受控平台触发器；它签名调用 Semattice，Semattice 仍回调 AgentCiCi reservation，因此不构成绕过校验的第二条开户路径。
- 生产 Compose 已声明两个方向的密钥注入变量；真实值只保留在受限环境文件。

## Verification

- `mvn -q -Dtest=InternalHmacVerifierTest,SematticeProvisioningServiceTest test`、`mvn -q -DskipTests package`、前端 `npm run build`、Compose config 与 diff 检查均通过。
- 2026-07-24 本机真实跨服务验收：以 `admin@cloudcc.com` 的运营平台令牌创建新的 AgentCiCi 组织 `orgc9h2xs5puanlbykmc`，再经 `POST /platform/tenants/{orgId}/semattice-provisionings` 开通。Semattice 在独立 PostgreSQL 16 控制/运行角色下回调 reservation 与 completion；两侧均记录同一 `company_id` 和 tenant UUID `22369429-94c0-5dc2-ad04-600673f62829`，状态分别为 `PROVISIONED` 与 `active`。同一幂等键重试返回同一 tenant UUID，Semattice `tenant_operation` 仍为一条成功记录。临时本机 HMAC 未写入仓库或项目状态。
- 同日浏览器端到端验证：真实运营端登录后，在新建组织 `orgnuctqa4lpdn9zz1qx` 的“租户应用”页面点击“开通 Semattice”。成功提示显示企业身份绑定完成，应用计数从 1 变为 2，卡片状态变为“运行中”，按钮变为禁用的“已开通”。
- `mvn -q test` 被共享 `agentcici_test` 的既有 Flyway V81 checksum mismatch 阻断；未修改历史 migration 或执行 repair。
- AgentCiCi 已发布内测版 `2.8.5-beta.3 / bef088d5769c`；V93 已在生产正向执行，backend/frontend 健康，HMAC endpoint 的未签名请求返回 403。
- Semattice 上线试验发现其生产库尚未显式执行 migration 13，且运行服务不持有 migrator URL；已原子回滚 Semattice 到上一健康 release，未留下失败开户路径。

## Handoff

- 本任务与 Semattice `TASK-026` 必须采用同一接口契约、错误码与签名规范；任一端未通过质量门不得单独发布。
- 继续条件：在 Semattice ECS 以专用 `semattice_migrator` 身份显式执行 migration 13，并核验 schema history；之后重新部署已构建制品、重试相同失败 smoke idempotency key 并完成成功开户验收。
