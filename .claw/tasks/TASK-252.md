---
kind: task-status
task_id: TASK-252
status: in_progress
updated_at: 2026-07-27T15:42:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: .claw/assignments/TASK-252.yaml
spec_path: docs/specs/FEAT-145-unified-principal-identity-governance.md
---

# TASK-252 - 统一 Principal 身份与治理模型设计

## Current State

- Status: `in_progress`
- Next action: 机器开户已可独立使用；需指定首个服务的 `company_id`、稳定 client/service 名称、精确 scope 与有效人类 PRIMARY owner 后创建并完成 OACT exchange。配置 Realm 受管 SMTP 后，再开启人类邀请灰度。
- Deployment scope: `deploy/docker-compose.acr.yml` 仅用于把独立 Keycloak provisioner 配置传入 backend；不修改服务拓扑、网络或证书。
- Blocked: Keycloak `agentcici` Realm 尚未配置 SMTP；人类 `provisioning` 继续关闭。机器开户已具备 provisioner secret 与受控开关，但首个机器主体需要业务指定目标公司、服务名、scope 与人类 PRIMARY owner，不能由系统自行猜测。

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

## Handoff

- 规格：`docs/specs/FEAT-145-unified-principal-identity-governance.md`。
- 分支：`main`。
