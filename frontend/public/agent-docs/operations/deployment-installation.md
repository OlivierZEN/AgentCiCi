---
document_id: agentcici.deployment-installation.v1
title: AgentCiCi、Semattice、Keycloak 部署安装指南
content_version: deployment-installation/v1
language: zh-CN
audience:
  - platform-operator
  - private-cloud-delivery
  - deployment-agent
canonical_ui_path: /platform/operations/deployment-installation
public_markdown_path: /agent-docs/operations/deployment-installation.md
security: no-secrets
---

# AgentCiCi、Semattice、Keycloak 部署安装指南

本指南用于受管私有化安装。它说明制品从哪里取得、如何安装、按什么顺序启动、如何验收和回滚。文中的 `<...>` 均为必须从当前客户交付清单或受管环境配置解析的占位符，不是可直接使用的值。

## 安全与事实源规则

- 三个产品独立版本、制品、数据、配置和回滚点，不使用一个全局平台版本。
- Token、口令、数据库 URL、Client Secret、私钥和 Docker auth 原文不得进入本文、仓库、截图、日志或命令历史。
- AgentCiCi 与私有 Semattice 制品只从授权交付渠道取得。没有 release manifest 时停止，不猜测 registry、namespace、tag、下载地址或镜像名称。
- Keycloak 使用官方 OCI 仓库，但精确版本与 digest 仍由本次交付清单锁定。
- 禁止使用 `latest`、`dev`、`unknown` 或可变制品完成可追溯安装。
- 所有部署动作先建立非空备份和回滚点；历史数据库 migration 不自动反向执行。
- 技术健康、安全边界与真实登录态业务验收分开报告。

## 01 部署全景

### 产品职责

| 产品 | 部署形态 | 独立状态 | 回滚单元 |
|---|---|---|---|
| AgentCiCi | backend、frontend 两个 OCI 镜像 | PostgreSQL、Redis、RabbitMQ、Qdrant | 恢复上一组双镜像，只重建应用容器 |
| Semattice | Linux amd64 二进制、静态资源、migration | 独立 PostgreSQL 与分离数据库角色 | `current` 切回上一不可变 release，不自动反向 migration |
| Keycloak | 官方 OCI 镜像或受控不可变安装包 | 独立 PostgreSQL、Realm、Client 和主题 | 恢复上一镜像/安装包与受控配置备份 |

### 默认启动顺序

1. 持久卷、数据库与 AgentCiCi 依赖服务。
2. Keycloak，验证 readiness、OIDC discovery 和 JWKS。
3. Semattice，验证 health、version、migration 和匿名鉴权边界。
4. AgentCiCi backend，等待 migration 和应用健康。
5. AgentCiCi frontend/edge，验证同源入口和 API 路由。
6. 使用真实 SERVICE 身份验证本次启用的跨产品契约。
7. 使用获授权 HUMAN 账号完成指定业务验收。

完成标志：每个产品都有独立版本、制品标识、配置修订、备份和回滚点。

## 02 制品来源

### 从哪里获取

| 产品 | 制品名称 | 来源 | 必须记录 |
|---|---|---|---|
| AgentCiCi | `cici-backend`、`cici-frontend` | CloudCC 授权的私有镜像仓库，坐标由客户 `release-manifest.env` 提供 | version tag、Git commit、OCI digest |
| Semattice | `semattice-linux-amd64`、静态资源、migration | Semattice 拥有方通过授权 release bundle 交付 | version、Git commit、SHA-256、migration checksum |
| Keycloak | `quay.io/keycloak/keycloak:<approved-version>` | Keycloak 官方 OCI 仓库 | 精确版本、OCI digest |

Semattice 当前受管生产交付不是 OCI 镜像。只有客户 release manifest 明确提供容器坐标时，才允许使用该坐标；不得自行构造 `semattice:<tag>`。

### 脱敏坐标模板

```dotenv
REGISTRY_HOST=<authorized-registry-host>
REGISTRY_NAMESPACE=<authorized-namespace>
AGENTCICI_VERSION=<immutable-version>

AGENTCICI_BACKEND_IMAGE=${REGISTRY_HOST}/${REGISTRY_NAMESPACE}/cici-backend:${AGENTCICI_VERSION}
AGENTCICI_FRONTEND_IMAGE=${REGISTRY_HOST}/${REGISTRY_NAMESPACE}/cici-frontend:${AGENTCICI_VERSION}
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:<approved-version>

SEMATTICE_RELEASE_ARCHIVE=<authorized-linux-amd64-release-archive>
SEMATTICE_RELEASE_SHA256=<sha256-from-release-manifest>
```

### 交付包最低内容

```text
delivery/
├── release-manifest.env
├── image-lock.txt
├── checksums.sha256
├── compose/
├── systemd/
├── nginx/
├── migrations/
└── rollback.md
```

完成标志：所有制品都匹配 manifest 中的版本、commit 和 digest/SHA-256，没有可变 tag 或未授权下载源。

## 03 安装前准备

### 主机与容量

- Linux 与 CPU 架构符合交付清单。
- Docker/Compose 或 systemd 版本满足受管模板要求。
- 时钟同步、DNS、TLS、文件描述符、磁盘、内存和日志保留策略已核对。
- 数据、备份、release 和日志目录具有明确 owner、mode 和容量告警。

### 数据隔离

- AgentCiCi、Semattice、Keycloak 使用独立 PostgreSQL database 和 role。
- Semattice 的 migrator、control、runtime 身份分离；runtime/control 不拥有业务数据，也不能绕过行级隔离。
- Redis、RabbitMQ、Qdrant 只作为 AgentCiCi 受管依赖，不与其他项目私有状态混用。

### Secret

- 使用 root-only Secret 文件或正式 Secret 管理系统。
- Compose、systemd 和 Nginx 模板只引用变量名。
- 渲染后的含密配置、环境文件和 Docker auth 不输出到日志或工单。

### 变更单

记录目标环境、目标产品、目标版本、维护窗口、当前版本、备份位置、回滚目标、执行人和验收人。

完成标志：备份路径与回滚负责人明确，数据库/数据卷隔离已验证，受管 Secret 文件权限符合要求。

## 04 安装 Keycloak

1. 从官方仓库拉取交付清单批准的精确版本。
2. 回读镜像 RepoDigest，与 `image-lock.txt` 比对。
3. 创建独立数据库和最小权限 role。
4. 从受管 Secret 文件注入管理员 bootstrap、数据库和 TLS 配置。
5. 以 production 模式启动 Keycloak，不向公网暴露管理健康接口。
6. 使用幂等脚本创建或更新 Realm、HUMAN/服务 Client、scope、redirect URI、Web Origin、SMTP 与主题。
7. 验证 readiness、OIDC discovery、JWKS、授权码登录与错误 redirect。

```bash
docker pull "${KEYCLOAK_IMAGE}"
docker image inspect "${KEYCLOAK_IMAGE}" --format '{{json .RepoDigests}}'
docker compose --env-file <root-only-secret-file> config
docker compose --env-file <root-only-secret-file> up -d keycloak
```

停止条件：镜像 digest 不匹配、数据库不可恢复、Secret 权限不合格、discovery/JWKS 失败、Realm 导入需要覆盖未知现有配置。

完成标志：Keycloak 健康，discovery 与 JWKS 可读，受保护管理入口保持关闭，Realm/Client 配置可重复执行。

## 05 安装 AgentCiCi

1. 使用 pull-only 机器人账号登录客户 manifest 指定的私有仓库。
2. 拉取 backend/frontend 两个镜像，并分别回读 OCI digest、version 和 revision label。
3. 确认两个镜像来自同一冻结 commit；禁止 backend 与 frontend 版本混用。
4. 创建 AgentCiCi 独立 PostgreSQL database/role 以及 Redis、RabbitMQ、Qdrant 持久卷。
5. 先启动四项状态服务，完成健康检查和初始备份。
6. 注入 Keycloak/OIDC、数据库、队列、向量库、加密和跨应用契约配置。
7. 启动 backend，等待 migration 完成和健康通过。
8. 启动 frontend/edge，验证同源页面和 API 路由。

```bash
docker login "${REGISTRY_HOST}"
docker pull "${AGENTCICI_BACKEND_IMAGE}"
docker pull "${AGENTCICI_FRONTEND_IMAGE}"
docker compose --env-file <root-only-secret-file> config
docker compose --env-file <root-only-secret-file> up -d postgres redis rabbitmq qdrant
docker compose --env-file <root-only-secret-file> up -d backend frontend
```

常规 AgentCiCi 应用升级只替换 backend/frontend，不重建 PostgreSQL、Redis、RabbitMQ 或 Qdrant。

完成标志：双镜像版本、commit 和 digest 一致；容器健康、restart=0；migration 成功；匿名受保护 API 返回结构化 401。

## 06 安装 Semattice

1. 从授权 release bundle 获取 Linux amd64 二进制、静态资源和 migration。
2. 校验 version、Git commit、SHA-256 和 migration checksum。
3. 建立独立 PostgreSQL database，并分离 migrator、control、runtime 身份。
4. 备份当前数据库、环境文件、systemd unit、静态资源和 `current` 指向。
5. 使用冻结 release 携带的 migration 集合执行正向迁移并回读 schema version。
6. 创建新的不可变 release 目录并安装二进制和静态资源。
7. 原子更新 `current`，重载 systemd 并重启 Semattice。
8. 验证 health、version、NRestarts、匿名 401/403、OIDC 登录和实际 SERVICE 身份契约探测。

```bash
sha256sum -c checksums.sha256
install -d -m 0755 <release-root>/<release-id>
install -m 0755 semattice-linux-amd64 <release-root>/<release-id>/semattice
ln -sfn <release-root>/<release-id> <current-link>
systemctl daemon-reload
systemctl restart semattice
```

停止条件：SHA-256 不匹配、migration 集合与版本不一致、数据库角色边界缺失、匿名请求命中 404/SPA HTML、目标 SERVICE 身份 401/403/超时。

完成标志：systemd active、NRestarts=0；版本/commit/SHA-256 一致；schema 完整；匿名边界和服务身份探测通过。

## 07 集成与启动

### OIDC 信任

- AgentCiCi 与 Semattice 只信任环境配置声明并经校验的 issuer、audience、JWKS 和 authorized party。
- HUMAN 浏览器会话不能替代 SERVICE 身份。
- redirect URI 和 Web Origin 由部署配置生成和审查，不写入业务源码。

### 跨产品契约

对本次新增、变更、启用或切换的契约：

1. 提供方实现提交进入提供方远程主线。
2. 提供方目标运行版本包含该提交。
3. 匿名或无签名请求命中预期 401/403，而不是 404 或 SPA HTML。
4. 目标消费方真实 SERVICE 身份成功调用，并校验最低响应形状。
5. 消费方再发布或启用对应功能。
6. 从消费方正式入口完成聚合回读和跨系统 E2E。

已有但本次未改变、未启用的可选契约不能虚报完成，也不应阻断独立产品源码安装。

### Edge 与 TLS

- 域名、Origin、Host、端口和证书只存在于受管部署配置。
- 先运行 edge 配置校验，再 reload。
- API 健康必须验证响应类型和 JSON 形状，不能把 SPA fallback 的 HTML 200 当作成功。

完成标志：版本/契约/身份矩阵完整，服务身份探测带 correlation ID 且可回读，没有用 HUMAN 页面或伪造签名替代。

## 08 验收与回滚

### 四层证据

| 层级 | 必须回读 | 不能替代 |
|---|---|---|
| 制品 | version、commit、digest/SHA-256、配置修订、migration | 页面角标或 tag 名 |
| 运行 | health、restart/NRestarts、错误日志、依赖、稳定窗口 | 单次 HTTP 200 |
| 安全 | 匿名 401/403、discovery/JWKS、真实 SERVICE 探测 | HUMAN 登录页或 mock |
| 业务 | 指定租户、真实登录态、关键业务结果、跨租户负向 | 技术 smoke |

```bash
docker compose ps
docker inspect <container> --format '{{.Image}} {{.RestartCount}} {{.State.Health.Status}}'
systemctl is-active keycloak semattice
systemctl show semattice -p NRestarts
```

健康与版本 URL、预期状态码和响应 JSON 形状必须来自当前客户 release manifest，不从历史环境复制。

### 回滚

- AgentCiCi：恢复发布前受管配置与上一组 backend/frontend 镜像，只重建应用容器；保留 PostgreSQL、Redis、RabbitMQ、Qdrant。
- Semattice：将 `current` 切回上一不可变 release，恢复匹配的环境、systemd 和静态资源备份；数据库 migration 不自动反向执行。
- Keycloak：恢复上一镜像或安装包、数据库与受控 Realm/主题配置备份；再次验证 readiness、discovery 和 JWKS。
- 只回滚失败产品，不联动回滚其他产品或删除数据目录。

### 最终报告

逐产品列出：检查或变更内容、version/commit/artifact、备份与回滚点、migration、健康、重启数、鉴权边界、稳定窗口、真实业务验收状态和未解决风险。

完成标志：制品可追溯、运行健康、安全边界通过、回滚可执行；真实业务验收已完成或明确标记为 HUMAN pending。
