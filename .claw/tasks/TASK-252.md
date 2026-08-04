---
kind: task-status
task_id: TASK-252
status: in_progress
updated_at: 2026-08-04T14:05:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-252.yaml
spec_path: docs/specs/FEAT-145-unified-principal-identity-governance.md
---

# TASK-252 - 统一 Principal 身份与治理模型设计

## Current State

- Status: `in_progress`
- Next action: CloudCC `orgId` 契约代码、迁移和管理端已完成本地验证；等待用户授权后按生产运行手册发布，并完成受控租户连通性回归。公司切换的 A→B→A 页面回读仍待受权用户复核。旧 `company_id` 的浏览器缓存、异步响应、SSE 和工作台状态不得显示或写回新公司页面；不删除任何历史会话数据。
- Deployment scope: `deploy/docker-compose.acr.yml` 仅用于把独立 Keycloak provisioner 配置传入 backend；不修改服务拓扑、网络或证书。
- Blocked: 无。Keycloak Realm SMTP 已配置，人工 provisioning 已在受控部署环境启用；本次只修复邀请/重绑闭环，不创建未获业务授权的机器主体。

## Scope

- 覆盖人类主体、机器主体、Keycloak 身份绑定、公司成员、责任人、应用成员投影与生命周期。
- 实现 AgentCiCi Principal、受控人类邀请、Keycloak 绑定、机器主体/责任人、迁移、测试、发布与验收。
- Semattice 运行时改造在其独立仓库任务中实施，保持接口与事件契约一致。

## Evidence

- 已核对现有 `user_account`、V96 `account_external_identity`、V97 `public_id` 与 `company_member`：当前人类账户、OIDC 绑定和成员已分层，但邀请不创建 Keycloak 用户且直接激活。
- 已核对 Semattice 当前 JWT/JWKS verifier：资源服务可本地验证可信 issuer，JWKS 缓存五分钟，不逐请求回调 IdP；本规格在此基础上增加 HUMAN/SERVICE Principal 投影与本地授权校验。
- 本任务只改规格和项目状态，未运行代码测试。
- 已实现 V98 Principal/Identity/Service Principal 基座、受控邀请、首次 OIDC 激活和机器账户责任人 API；已在一次性 PostgreSQL 16 中验证 V1→V98 迁移与兼容映射。
- 已合并主分支并发布 `2.8.22 / 645b53f6ea58`；生产 Flyway V98/V99 成功，24 个既有全局账户与 24 条 Keycloak 身份绑定已回填。Keycloak 专用 `agentcici-provisioner` client 已创建并授予最小管理角色，未启用自动开户。
- 已发布机器 Keycloak client-credentials → 短期 Semattice OACT 交换边界，并完成 Semattice HUMAN/SERVICE Principal 本地投影发布；公开路由缺少 Bearer 返回 401，开关关闭返回 403。机器开户开关现独立于人类邮件邀请，不因 SMTP 缺失被代码耦合阻断。
- `a7cd78f88543` 已标记并发布为 `2.8.23`：后端/前端镜像 index digest 分别为 `sha256:82d4278d215ae1ac9adbcace14b9121c7bd9c84c520a2ca17712b560327928b0`、`sha256:0f6e22ebce5cf7e7fb3703ca568152dad4f12e27068b6cf7c70bb83faa3b451a`；发布前备份 `/opt/cici/backups/20260727-233807-before-2.8.23` 包含环境、PostgreSQL、KB 与 Qdrant。六容器均健康，backend `/system/version` 为 `2.8.23 / a7cd78f88543`，匿名边界与 OACT JWKS 均验证通过。
- `58a96d618207` 已标记并发布为 `2.8.24`：Compose 现显式传递 machine-provisioning 与 service-token-exchange 开关；后端/前端 index digest 分别为 `sha256:d2a1dcad568e3167e327e713c977ad2fc83a40cf1348ac4f46be1174a4f0043e`、`sha256:710971cde48ce1fdc59af837331a79d0eb1a42d428a87fa90bace2a496a49ca8`。备份 `/opt/cici/backups/20260727-234415-before-2.8.24` 完整；六容器健康，backend `/system/version` 为 `2.8.24 / 58a96d618207`，三项受控开关均明确为 false。
- 生产 provisioner secret 已按 Keycloak Client Secret rotation 写入 `/opt/cici/deploy/acr.env`，变更前配置备份为 `/opt/cici/backups/20260727-234937-before-machine-provisioning-enable`；backend 已重建且健康。使用该 secret 的 Keycloak `client_credentials` 返回有效 300 秒管理令牌；不输出令牌或密钥。机器开关现为 true，人类邀请和服务交换仍为 false。
- Keycloak Realm 已受管配置 SMTP；人工 invitation provisioning 已开启。实测 Keycloak 可以向受控收件人发出 `VERIFY_EMAIL` / `UPDATE_PASSWORD` Required Actions 邮件；不记录 SMTP 密码或邮件链接。
- 已定位并修复邀请的失效绑定缺口：旧实现把本地 `account_external_identity` 的存在直接当作远端用户可用，导致远端 Keycloak User 被删除时成员仍可能被置为 `ACTIVE`。新流程读取远端 `sub`、仅在未激活时重发邮件；远端缺失时严格验证重建/恢复归属，V102 同步修复重绑时 `principal_identity` 镜像主键冲突。
- 已发布生产 `2.8.41 / 3320ed77515d`，tag 与 ACR 后端/前端不可变镜像均已推送。发布前备份为 `/opt/cici/backups/20260804-113909-before-2.8.41-invitation-lifecycle`，环境、PostgreSQL、知识库与 Qdrant 归档均非空；仅重建 backend/frontend，六容器 healthy，Flyway V102=true，Nginx 校验通过，`https://x.agentcici.com/` 为 200、匿名 `/auth/me` 和 service-token 交换均为预期 401。Keycloak SMTP 配置已脱敏回读；未输出邮件凭据、Keycloak secret、JWT 或激活链接。
- 生产历史身份回填（2026-08-04）：盘点到 5 个 `ACTIVE` AgentCiCi 全局账户缺少 `account_external_identity`。已先备份 AgentCiCi PostgreSQL（`/opt/cici/backups/20260804-151145-before-keycloak-human-backfill/postgres.dump`）和 Keycloak Realm（`/opt/keycloak/backups/20260804-151150-before-agentcici-human-backfill/agentcici-realm.json`），随后为 5 个账户创建或复用 Keycloak User、写入 5 条唯一 issuer+subject 映射，并触发 `VERIFY_EMAIL` + `UPDATE_PASSWORD` 初始动作邮件。回读：活跃账户未绑定数为 0，5/5 Keycloak 用户启用且具备两个初始动作；未记录收件人、密码、令牌或邮件链接。Keycloak 用户自定义属性受当前 Realm 配置限制未持久化，AgentCiCi 的不可变 `issuer + subject` 映射仍是实际身份绑定事实源。
- 单账户身份重绑（2026-08-04）：经用户确认，已将一个 `ACTIVE` 全局账户从重复的、未完成初始化的 Keycloak User 重绑到其原有可用手机号登录 Keycloak User；只更新本地不可变 issuer+subject 映射，不重置密码、不删除或禁用任何 Keycloak User。更新前 PostgreSQL 备份已保存在受限生产备份目录；精确条件更新影响 1 条记录，目标 subject 没有其他 AgentCiCi 绑定，`principal_identity` 镜像由旧 subject 迁移至新 subject。AgentCiCi health=200、Keycloak active 且 OIDC discovery=200。
- 单账户初始化邮箱修复（2026-08-04）：经用户确认，已将 AgentCiCi 中已验证的邮箱补写入一个已绑定 Keycloak User，并重新触发 `VERIFY_EMAIL` + `UPDATE_PASSWORD` 邮件。修复前发现 AgentCiCi 邮箱格式正常、而 Keycloak `email` 字段为空，导致初始化链接返回“无效的电子邮件地址”。Keycloak 用户受限备份已保存；回读确认 email 已存在、用户启用、邮箱尚待验证、两个 Required Actions 就绪，SMTP 错误日志为 0。未记录邮箱、密码或邮件链接。
- 紧急缺陷设计（2026-08-04）：已确认后端会话和消息按 JWT `company_id` 查询，实际风险位于 `AssistantApp` 浏览器内存：工作台默认 session ID 跨公司可碰撞，缓存以 agent/session 而非 company 分区，且公司切换不清空旧状态或拒绝旧异步响应。规格已补齐“切换即失效、缓存 company 分区、旧响应静默丢弃、终止旧流”的实现与验收契约；本次不删除或迁移历史聊天数据。
- 紧急缺陷实现（2026-08-04）：`AssistantApp` 在认证载荷的 `companyId` 变化时递增内存作用域版本并清空会话、消息、工作台运行态、知识库、技能、快捷指令、监控与智能体投影；浏览器消息/工作台缓存使用 `companyId::sessionOrAgentId`，不改变 API/session ID。会话、工作台、知识库、智能体、技能、快捷指令与监控异步加载仅能在原作用域回写；旧公司流式回调会静默丢弃。`workbenchSessions` 定向测试和前端 TypeScript/Vite 生产构建通过，等待生产发布验收。
- 生产发布（2026-08-04）：`2.8.43 / 45b942c06b86` 的 backend/frontend 不可变镜像已推送并发布；index digest 分别为 `sha256:9fcfa8f2c72a5cb80ea6f5cdc68f7dd3a384bb590aed5fbbecb3c5a576e14610`、`sha256:8ad594eea01883e1e87901158c58bc3423d49bcb65738c2d65cf2f505f24d2f5`。发布前备份 `/opt/cici/backups/20260804-213816-before-2.8.43-company-switch-isolation` 的环境、PostgreSQL、知识库、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，`/system/version` 回读为 `2.8.43 / 45b942c06b86`，Nginx 通过，`x.agentcici.com`=200、匿名 `/auth/me`=401。线上前端工件含公司缓存键标记；受权用户的 A→B→A 页面回归仍作为人工验收项。
- CloudCC CRM 契约确认（2026-08-04）：CloudCC Token API 使用 `orgId`，实际 `POST /api/cauth/token` 已在受权凭据下返回 HTTP 200/result=true（未记录 token 或 secret）。当前 AgentCiCi 后端错误读取/发送配置 `companyId`；盘点显示多个租户还缺少该外部组织 ID 配置。规格与任务范围已扩展为“读新兼容旧、写新字段、Flyway 正向回填、请求使用 orgId、管理端收敛与脱敏连通性验证”，且明确不改 `integration_app.company_id`。
- CloudCC CRM 实现（2026-08-04）：运行时优先读取 `config.orgId`、兼容旧 `config.companyId`；网关发现与 `/api/cauth/token` 请求体均使用 CloudCC `orgId`。V104 从旧配置字段或 `orgapi_switch_address` URL 的 `orgId` 正向回填新键，不改 `integration_app.company_id`、不删除旧键。管理端只显示/保存 `orgId`，并掩码 CloudCC SecretKey；保存被掩码的密钥会保留现有值。`CloudccAccessTokenServiceTest`、backend compile、前端定向测试、前端 production build 与 diff check 已通过。

## Handoff

- 规格：`docs/specs/FEAT-145-unified-principal-identity-governance.md`。
- 分支：`main`。
