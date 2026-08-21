---
kind: feature-spec
feature_id: FEAT-200
title: 运维中心部署安装在线指南
status: implemented
primary_project: agentcici
task_ids: TASK-328
related_integrations: none
updated_at: 2026-08-21T06:23:00Z
updated_by: codex
---

# FEAT-200 - 运维中心部署安装在线指南

## 背景与目标

运营控制台现有信息架构覆盖能力治理、运营管理、风险与质量和平台偏好，但缺少面向交付与运维人员的安装资料入口。AgentCiCi、Semattice 和 Keycloak 的制品形态、启动顺序与回滚方式并不相同，继续依赖聊天记录或历史命令容易造成镜像标签漂移、Secret 泄露、数据库误共用和跨产品联动回滚。

本功能新增一级板块“运维中心”及目录“部署安装”，在认证后的运营控制台中提供连续在线文档，并公开同源结构的 Markdown 版本供 Agent 和自动化工具只读获取。

## 用户与场景

- 平台运营人员：确认三个产品的制品来源、职责边界、部署顺序和验收证据。
- 私有化交付人员：依据客户交付清单完成主机准备、配置注入、安装和回滚演练。
- 自动化 Agent：通过稳定 Markdown 地址读取脱敏、结构化、无需 JavaScript 的安装说明。

## 范围

### In Scope

- 在 `/platform/*` 一级导航新增“运维中心”，子目录为“部署安装”。
- 新增认证路由 `/platform/operations/deployment-installation`。
- 在线文档覆盖部署拓扑、制品来源、基础设施前置条件、Keycloak、AgentCiCi、Semattice、集成启动顺序、验收和回滚。
- AgentCiCi 使用 backend/frontend 两个受管 OCI 镜像；Semattice 明确当前受管制品是 Linux amd64 二进制与静态资源包，不把不存在的容器镜像当作事实；Keycloak 使用官方 OCI 镜像。
- AgentCiCi 与私有 Semattice 制品从授权交付清单获取；页面只展示占位坐标和取得方式，不固化部署环境域名或凭据。
- 新增无需登录和 JavaScript 的稳定 Markdown 路径 `/agent-docs/operations/deployment-installation.md`。
- Markdown 提供机器可读 front matter、章节编号、命令模板、安全边界和验收清单。

### Out of Scope

- 不执行 UAT、生产或客户环境部署，不创建主机、数据库、Realm、Client 或用户。
- 不发布、推送或复制任何镜像、二进制、配置包或凭据。
- 不修改 Semattice、Keycloak 或父级部署编排事实源。
- 不在业务前端源码中写入 UAT、生产、本地或客户私有化域名、Host、端口和 Secret。
- 不新增移动端适配或移动端验收。

## 信息架构与视觉方向

- register：`product`。
- 方向：“单页运行手册”。标题区给出适用范围、文档版本和 Markdown 入口；左侧粘性章节目录；中间为连续编号正文。
- 沿用“鎏金账房”主题 token、紧凑密度、墨色正文和香槟金结构线。使用事实表、定义列表、步骤清单和代码块，不使用卡片宫格、营销 hero、装饰图片或厚侧边强调线。
- 页面进入时只保留 `.platform-main` 一个长文档滚动容器，直接章节锚点应定位到目标内容。

## 部署事实与边界

- 三个产品独立版本、制品、数据和回滚点，不使用一个全局平台版本。
- AgentCiCi 的交付制品是 `cici-backend` 与 `cici-frontend` 两个不可变 OCI 镜像，坐标来自客户交付清单；禁止使用 `latest` 作为可追溯安装依据。
- Semattice 当前受管生产形态是带版本、commit 与 SHA-256 的 Linux amd64 二进制、静态资源和迁移集合，通过不可变 release 目录与 `current` 原子切换；没有交付清单时不得自行猜测 OCI 镜像。
- Keycloak 镜像来自官方 `quay.io/keycloak/keycloak` 仓库，但精确版本仍由本次交付清单锁定。
- PostgreSQL 数据库与 role 按产品隔离；AgentCiCi 的 Redis、RabbitMQ、Qdrant 只服务 AgentCiCi；Keycloak 使用独立数据库。
- Secret 只进入目标主机 root-only 受管配置或正式 Secret 管理系统，文档、前端、截图、日志和命令历史中只出现变量名。
- 默认顺序为基础设施与数据库、Keycloak、Semattice、AgentCiCi backend、AgentCiCi frontend/edge；每个阶段通过健康和鉴权边界后再继续。
- 技术健康、版本/制品回读和匿名鉴权负例不等于登录态业务验收，文档必须分别列出。

## 验收标准

1. 平台导航出现一级“运维中心”，展开后可进入“部署安装”，目标路由保持激活态。
2. 在线文档完整覆盖 AgentCiCi、Semattice、Keycloak 的制品来源和部署安装步骤。
3. 在线正文明确说明 Semattice 当前不是受管 OCI 镜像交付，避免虚构镜像来源。
4. 页面示例不包含真实环境域名、IP、Token、Secret、私钥路径或可复用凭据。
5. Markdown 路径无需登录即可返回完整文档，并具有稳定 `document_id`。
6. Markdown 与在线页包含相同的 8 个一级章节，自动化测试逐章校验。
7. 导航定向测试、文档测试、前端全量测试和 production build 通过。
8. 从本地 `main` 构建前端并更新 `https://cici.localhost/`；回读页面路由、Markdown MIME、安全响应头、容器健康、重启次数、版本与前端制品指纹。
9. 使用真实桌面浏览器检查导航、完整页面、锚点、Markdown 入口、hover/focus 和长代码块溢出。

## 回滚

- 回滚导航项、路由、在线页、CSS 和公开 Markdown 文件即可。
- 该页面是只读文档，不修改运行配置、数据库或租户状态；回滚不需要数据迁移。
