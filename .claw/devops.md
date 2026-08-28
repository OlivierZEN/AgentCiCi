---
kind: devops
version: 4
updated_at: 2026-08-28T13:20:00Z
updated_by: codex
status: active
---

# DevOps

## 2026-08-28 TASK-342 生产 `2.8.67`

- 用户确认 UAT `2.8.67-beta.1` HUMAN 图片识别验收通过。冻结 UAT tag `2.8.67-beta.1^{}`、正式 tag `2.8.67^{}` 与生产运行提交均为 `2970bea75208a05d65c00c08333bc73cc072e607`，且远程 `main` 包含该提交；本地后续功能未混入候选。
- backend/frontend ACR index digest 分别为 `sha256:2b6be2564f0eef09f064e4ce345d585cc4bc1f3c00408d0f358ab8f82bfac615` / `sha256:6ec9501ec3cdfdf1118ab1ec9f647223ecfb843440603d83e054577c539dd6a4`，均含 linux/amd64，label 为 `2.8.67 / 2970bea75208`，未更新 `latest`。
- 完整回滚点 `/opt/cici/backups/20260828T130242Z-before-2.8.67` 共 14 项、351,001,019 bytes，含 Compose/env、PostgreSQL custom dump、KB、Qdrant 存储与原生 snapshot、旧应用镜像、数据计数、容器基线、回滚说明和 SHA-256 清单；均非空且 `0600`，dump catalog、tar、gzip 与清单通过。应用回滚目标 `2.8.66`，数据恢复需单独批准。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 保持 `5b4708835b05`、`e70d00527987`、`8289db5848cc`、`be0f28441f6e`。六容器 healthy/restart=0，backend health UP，运行 version/commit/label/digest 一致，Flyway V125 和 repeatable `demo example application` success，Nginx 有效。
- 公网首页、`/app`、`/embed/sisi`、DEMO 路由、SDK、DevAutopilot、Semattice 与 Keycloak smoke 通过；HTTP 根 301 到 HTTPS，匿名 `/auth/me`、`/ai/sessions`、`/skills` 和 Embed 附件 API 均为 JSON 401。
- 发布前后知识库计数均为 9/35/661，Qdrant 保持 1 collection/549 points。累计两个独立 50 秒稳定窗口内状态/restart 不变，backend severe=0，frontend 5xx/upstream=0。本候选未新增、启用或切换跨项目契约；生产登录态图片识别尚待 HUMAN。

## 2026-08-28 TASK-339 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@a64a1ede7d23` 以受管 release Dockerfile 构建为 `2.8.67-dev.a64a1ed`；镜像 ID 分别为 `sha256:1a9410567149` / `sha256:ff8fe07c50f4`，image label、容器环境、backend version API 和前端带版本资源一致。
- 只以现有 Compose 与 Git 忽略的官网 widget override `--no-deps --force-recreate backend cici-frontend` 替换两个无状态服务；两容器 healthy/restart=0，backend health=UP，frontend Nginx 有效。
- `https://cici.localhost/`、公开 widget、float embed 与稳定 SDK 均为 200；运行 CSS 含 Sisi 话筒 hover `background:transparent`。浏览器 computed style 证明默认/hover 均透明且无阴影，hover 仅把图标改为 CRM 蓝；console 0 error/warning。
- 近 5 分钟 backend severe=0、frontend 5xx/severe=0。PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice 和 DevAutopilot 容器 ID 未变化；本任务未新增或切换跨项目契约，未执行完整 stack verify。远程、UAT、生产、ACR 和 tag 未修改。

## 2026-08-28 TASK-338 UAT `2.8.67-beta.1`

- 冻结提交与 annotated tag 为 `2970bea75208a05d65c00c08333bc73cc072e607` / `2.8.67-beta.1`，远程 `main` 包含修复 `036c12a0d006`。backend/frontend ACR index digest 分别为 `sha256:927692d90475cabeded150a776e24d31a9dcbfd45f29273dd9d870de50aab74d` / `sha256:79a2f1e08967a0e85e65d73d5facafcecfc5ed8349c449007323d7acd958d49f`，均含 linux/amd64，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260828T104754Z-before-2.8.67-beta.1` 含 12 项、317,026,856 bytes 的 Compose/env、PostgreSQL、KB、Qdrant 存储与原生 snapshot、旧应用镜像、容器状态、回滚说明和 SHA-256 清单；均非空且 `0600`，dump catalog、tar、gzip 与清单通过。即时应用回滚目标为 `2.8.66-beta.3`，数据恢复需单独批准。
- UAT 只以非机密临时变量渲染版本；仅 `--no-deps --force-recreate backend frontend`。database、Redis、RabbitMQ、Qdrant ID 不变，六容器 healthy/restart=0；运行 version/commit/label/digest 一致，health UP、V125、Nginx、公开 smoke 和匿名 JSON 401 通过。
- 独立 30 秒稳定窗口 backend severe=0、frontend 5xx/upstream=0。本候选未新增或切换跨项目契约；登录态图片识别待 HUMAN，生产保持 `2.8.66 / e805c0ef7142`。

## 2026-08-28 TASK-337 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@beef1cedd4056` 以受管 release Dockerfile 构建为 `2.8.67-dev.beef1ce`；镜像 ID 为 `sha256:577085a682627aa3fc763d1c4c5aecc748f4b0d8b94b1a474f72399b33980274` / `sha256:b90c4bbc16e4eafaa75a794adc56fe4e3d81231243a32008e1602c12a7a8bd20`，镜像标签、容器环境、backend 版本 API 和前端资源一致。
- 仅以现有 Compose 和 Git 忽略的官网 widget override `--no-deps --force-recreate backend cici-frontend` 替换两项无状态服务；两者 healthy/restart=0，backend health=`UP`，frontend Nginx 配置有效。
- `https://cici.localhost/` 官网真实浮窗回读 `crm-blue`，附件入口 0、发送按钮靠右且 `33 × 33`，关闭 hover 背景与边框透明；真实发送后收到非空回复，console error/warning=0。
- `/`、公开 widget 配置、稳定 SDK 与 float embed 均为 200；近 10 分钟 backend severe=0、frontend 5xx=0。PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice 和 DevAutopilot 容器 ID 未变化。
- 本任务未新增或切换跨项目契约；标准 `./stack verify` 仍受既有 Semattice `config=1.0.7 / repository=1.0.8` 漂移约束。远程、UAT、生产、ACR 和 Git tag 未修改。

## 2026-08-28 TASK-336 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@036c12a0d006` 以受管 release Dockerfile 构建为 `2.8.67-dev.036c12a`；镜像 ID 为 `sha256:ef82f9a5648a724cce57175789d026b91fb24c6ac695d6d4faf40d8bfa4ea8e0` / `sha256:228feab79313e3e2832d81034d05a7a688c25edbcef003af9e2194934e24a1ee`，镜像标签、容器环境、backend 版本 API 和前端资源一致。
- 仅以现有 Compose `--no-deps --force-recreate backend cici-frontend` 替换两项无状态服务；两者 healthy/restart=0，backend health=`UP`，frontend Nginx 配置有效，`https://cici.localhost/app=200`。
- 已登录普通租户把用户原截图以剪贴板粘贴给思思，`qwen3.7-plus` 两轮分别返回图中的 `409` 与 `VISION_MODEL_REQUIRED`；浏览器 warning/error=0，backend 近 10 分钟无能力冲突或 severe 日志。
- PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice 和 DevAutopilot 容器 ID 均未变化且 restart=0。本任务未新增或切换跨项目契约；标准 `./stack version/verify` 仍受既有 Semattice `config=1.0.7 / repository=1.0.8` 漂移约束。远程、UAT、生产、ACR 和 Git tag 未修改。

## 2026-08-27 TASK-332 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@935728872674` 以受管 release Dockerfile 构建为 `2.8.67-dev.9357288`；镜像 ID 为 `sha256:a2471ce5fe9e6cf7fd9b5ef255dee308a428faf195eccd156418f0fda38698ed` / `sha256:020a61966c716fdd5f157126dfb1cd1d65b9835a997559c4df2afea8a7a48ce8`，镜像标签、容器环境、backend 版本 API 和前端带版本资源一致。
- 仅使用现有受管 Compose 依次 `--no-deps --force-recreate backend` 与 `cici-frontend`；两容器 healthy/restart=0，V124 `sisi embedded agent`、应用目录、会话表、稳定/版本 SDK 和正式页面回读通过。20 秒稳定窗口 backend severe=0、frontend HTTP 5xx/severe=0。
- PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice 和 DevAutopilot 未重建且 restart=0；直接执行受管 `scripts/verify.sh` 已通过完整基础设施、隔离、TLS、OIDC、健康/版本与匿名鉴权门禁。
- 标准 `./stack version/status/up` 继续被既有 Semattice 基础版本漂移 `config=1.0.7 / repository=1.0.8` 失败关闭；本任务未修改 local-stack 或 Semattice 仓库。浏览器正式路由无 Token 边界和 console 通过，真实 CloudCC 宿主换票与登录用户业务验收待 HUMAN。远程、UAT、生产、ACR 和 tag 均未修改。

## 2026-08-27 TASK-333 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@5f6ce44a` 构建为 `2.8.67-dev.5f6ce44`。Docker Hub `node:22-alpine` 拉取被本机凭据助手等待阻断后，未使用漂移基础镜像：前端改用已通过的宿主机 production build，backend 使用宿主机 Maven package；两者均在既有本地 JRE/nginx 运行镜像上覆盖最终 `main` 的 JAR、静态资源和受管配置，并重写 OCI version/revision 标签。
- backend/frontend 镜像 ID 为 `sha256:34c05e8f04e6a6f524f3d287115db168fd5910f737eeff0acce6139954bfbbf1` / `sha256:c97c5ec31c447473b86d2185484a9a3c9e2cfadd9cce1e022595164fcccce186`；容器环境、镜像标签和 backend `/system/version` 均回读 `2.8.67-dev.5f6ce44 / 5f6ce44a`。
- 仅以现有受管 Compose 依次 `--no-deps --force-recreate backend` 与 `cici-frontend`；两容器 healthy/restart=0，Nginx 配置有效，15 分钟 backend severe、frontend HTTP 5xx/nginx severe 均为 0。PostgreSQL repeatable migration `demo example application` 成功，数据库回读样例应用、版本和可选依赖完整；HTTPS 示例路由 200，匿名配置 API 为 JSON 401。
- Semattice、DevAutopilot、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 未重建；本次不新增/切换跨项目契约，按最小影响门禁未执行完整 `./stack verify`。标准 `./stack version/status` 仍被既有 Semattice 基础版本漂移 `config=1.0.7 / repository=1.0.8` 失败关闭，本任务未修改 local-stack 或 Semattice 仓库。
- 浏览器无可复用的本地平台登录态，受保护示例路由正确进入运营平台安全登录。HUMAN 登录后的列表、详情和单对象页面待确认；远程 `main`、UAT、生产、ACR 和 Git tag 均未修改。

## 2026-08-27 TASK-331 CloudCC pagecomponent V16 生产热修复

- 变更面仅为 CloudCC 高代码 pagecomponent：同一组件 ID `6a5628cee4b0a577cbba2088` 从 V15 发布到 V16，规范请求字段改为 `agentCompanyId`，仍兼容旧组件属性 `agentOrgId`。customPage `customer_interaction_workbench` 保持 V9 并继续引用同一 ID，注入验证返回 `issues=[]`，未执行不必要的 bind。
- 发布期间系统 DNS 把 CloudCC API 解析到会重置连接的节点；仅为当前 CLI 进程启用 Go resolver 后完成重读和发布，未修改 `/etc/hosts`、系统 DNS、代理或网络设置。
- 生产 CloudCC 登录态重载后显示“CloudCC CRM 已连接”，加载当前用户、客户队列、客户详情和 AI 助理；浏览器 error 日志为 0，未执行任何业务写操作。
- AgentCiCi 生产仍为 `2.8.66 / e805c0ef7142`，应用与状态服务均未重建。源代码修复提交 `ebea2febe1d8a15f3c802f48a7ab7dee480bedbd` 已进入远程 `main`；后端旧字段 alias 随 `2.8.67` 常规发布。
- 若组件需回滚，从修复前提交 `e8e3080987c0d0256b79658deacd4f0867ffe069` 重新打包并发布旧 Vue/UMD 到同一组件的新版本；customPage 引用无需改变。截图暴露的 CloudCC 安全标记不进入证据，需由管理员独立轮换。

## 2026-08-26 TASK-330 生产 `2.8.66`

- 用户确认 TASK-326/327/328/329 UAT HUMAN 验收，并授权按提供方到消费方顺序发布。Semattice 先以冻结 `1.0.7-beta.5 / 54f2ab93558f` 晋级正式 `1.0.7`；AgentCiCi 生产 SERVICE 对受保护模板端点的签名探测返回 7 对象/87 字段、state=applied 后，才继续 AgentCiCi 发布。
- AgentCiCi 冻结 UAT `2.8.66-beta.3^{}`、正式 tag `2.8.66^{}` 与运行提交均为 `e805c0ef7142b7446aef019c786107528cde34a1`。backend/frontend ACR index digest 为 `sha256:d892ff3b60c39bc690a48c71176005f6c2a12299288e16fd8606260375652557` / `sha256:289434e93eab541bdb96cb0a383443cb6280a67e72af1a9a17d206a0b6fcdab4`，linux/amd64，未更新 `latest`。
- 完整回滚点 `/opt/cici/backups/20260826T041149Z-before-2.8.66` 共 13 项，全部非空且 `0600`；PostgreSQL catalog、KB/Qdrant tar、Qdrant 原生 snapshot、旧应用镜像 gzip 和 SHA-256 清单通过。应用回滚恢复备份 `acr.env` 并只重建 `2.8.65` backend/frontend；数据恢复需要单独批准。
- 只 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 保持 `5b4708835b05`、`e70d00527987`、`8289db5848cc`、`be0f28441f6e`。六容器 healthy/restart=0，backend health UP，version/commit/label/digest 一致，Flyway V123，Nginx 有效。
- 公网首页、`/app`、`/admin/skills`、DevAutopilot integrated health、Semattice `1.0.7`、Keycloak discovery 和 HTTP 301 通过；匿名 `/auth/me`、`/skills`、技能导出 POST 为 JSON 401。运行 JS 回读导出进行中、非 JSON 响应和任务未就绪处理；100 秒稳定窗口 backend severe=0、frontend 5xx/upstream=0。
- 知识库计数发布前后保持 9/35/661，Qdrant 保持 549 points；四个状态服务未重建。
- 正式 `2.8.66` tag 完成后，发布脚本 dry-run 已把下一生产和首个 UAT 候选推导为 `2.8.67` / `2.8.67-beta.1`；不得再创建新的 `2.8.66-beta.N`。

## 2026-08-25 TASK-329 UAT `2.8.66-beta.3`

- 冻结提交与 annotated tag 为 `e805c0ef7142b7446aef019c786107528cde34a1` / `2.8.66-beta.3`，远程 `main` 与 tag 解引用一致并包含修复 `fada2e5f0b07`。backend/frontend ACR index digest 为 `sha256:a2d0b8a5b6ad618e5451348b84efd813fde62911c8e7ff6949291a3acd6c19b2` / `sha256:44796d4848f8b1071206a5ea0452d1b60368b3c465ebb8039a22f504e470785d`，均含 linux/amd64 manifest，未更新 `latest`。
- 完整回滚点 `/data/apps/agentcici/backups/20260825T110733Z-before-2.8.66-beta.3` 含 12 项、317,014,387 bytes 的 Compose/env、PostgreSQL dump、KB、Qdrant 存储与原生 snapshot、beta.2 旧应用镜像、容器状态、回滚说明和 SHA-256 清单；全部文件非空且 `0600`，dump catalog、归档、gzip 与清单验证通过。应用回滚目标 beta.2，数据恢复需单独批准。
- UAT 只以非机密临时变量渲染版本；`uat.secrets.env` 不含版本字段，HUMAN Semattice scopes 仍包含 `metadata.read,runtime.record.read`。仅 `--no-deps --force-recreate backend frontend`；database、Redis、RabbitMQ、Qdrant ID 不变，六容器 healthy/restart=0。
- backend health=`UP`，运行 version/commit/image label/digest 一致，Flyway V123，系统与 frontend Nginx 有效，Semattice/DevAutopilot active。两轮公开 smoke、管理页 200、匿名 `/auth/me`、`/skills`、导出 POST JSON 401 和运行 bundle 文案通过；切换期 1 次短暂 502 未在 3 分钟稳定窗口复现，稳定窗口 backend severe=0、frontend 5xx/severity=0。
- 本次没有新增、变更或启用跨项目契约。浏览器刷新后回到统一身份中心，未绕过认证；真实八文件 zip 下载待 HUMAN。生产、Semattice、DevAutopilot 和 Keycloak 均未发布或修改。

## 2026-08-25 TASK-329 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@fada2e5f0b07` 构建为 `2.8.66-dev.fada2e5`；镜像 ID 分别为 `sha256:eb27e47a6a16dbed8c23ae13a727b875184285764cdbf62a800dafaae3df76e1`、`sha256:f2be0fc6cc883257aa0f82741b5a170611bb0a5d17cd940c78b62671eb060a5d`，两项 image label、backend 版本 API 和运行 revision 一致。
- 仅以现有受管 Compose 执行 `--no-deps --force-recreate backend cici-frontend`；两容器 healthy/restart=0，`/actuator/health=UP`，frontend Nginx 有效，`https://cici.localhost/` 与 `/admin/skills` 为 200，匿名 `/auth/me`、`/skills` 为 JSON 401，运行 bundle 回读导出进行中文案，backend 近 5 分钟 severe 日志为 0。
- DevAutopilot、Semattice、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 容器 ID 均与更新前一致且 restart=0；本次不改变共享基础设施或跨项目契约，按最小影响门禁未执行完整 `./stack verify`。
- 本地浏览器进入统一 SSO 登录页，当前无可复用组织管理员会话；未绕过认证执行真实导出。HUMAN 登录后需复核 zip 下载、文件名和八文件内容。远程 `main` 未推送，UAT 仍运行未包含本修复的 `2.8.66-beta.2 / 525f0f610926`，生产未修改。

## 2026-08-21 TASK-327 / TASK-328 UAT `2.8.66-beta.2`

- 冻结提交与 annotated tag 为 `525f0f61092693b5b28c91386520dfa50b10a9d3` / `2.8.66-beta.2`；远程 `main` 包含候选提交。backend/frontend ACR index digest 分别为 `sha256:095c0d71d87dbac60521b7c0ee029604606a699287c661f6173becb140b5e35f` / `sha256:82155b279677fb5bb4cea2529f94697cf77a4def9a2ad990071dec37665aa79e`，均含 linux/amd64 manifest，未更新 `latest`。
- 完整回滚点 `/data/apps/agentcici/backups/20260821T064027Z-before-2.8.66-beta.2` 含 12 项、316,927,951 bytes 的受管配置、PostgreSQL custom dump、KB、Qdrant 存储与 1 个原生 snapshot、旧应用镜像、容器状态、回滚说明和 SHA-256 清单；全部文件 `0600`，dump catalog、归档、gzip 与清单校验通过。即时应用回滚目标为 `2.8.66-beta.1`，数据恢复仍需单独批准。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 分别保持 `d14ef639f035`、`db0945fd318c`、`4166b9909101`、`26aec0ef3a29`，六容器 healthy/restart=0。运行版本、commit、image label 与 digest 一致，backend health=`UP`，Flyway V123 成功，Nginx 有效；Semattice 与 DevAutopilot 保持 active，Keycloak 未修改。
- UAT 公开 smoke、匿名 `/auth/me=401`、部署安装页面 200、Agent Markdown `text/markdown + nosniff`、稳定 `document_id`、8 个编号章节和运行 bundle“运维中心”通过。企业微信移动页 200；带合法 `pageUrl` 的无会话 context 为 JSON 401，不存在入口 UUID 为 JSON 400。
- 缺少控制器声明为必填的 `pageUrl` 或 `entry` 时，通用异常映射仍返回 JSON 500；该畸形请求风险未造成认证绕过，不阻断本候选的已声明契约，但应在后续输入校验治理中改为 400。真实平台登录态导航、真实微信客服 OAuth/客户消息/状态 3 接管/人工无双发/原生跳转均待 HUMAN；生产及其他产品未发布。

## 2026-08-20 TASK-326 UAT `2.8.66-beta.1`

- 冻结提交与 tag 为 `2c9d3821b4588d067199b4842c4e7e12de07e8bd` / `2.8.66-beta.1`；backend/frontend ACR index digest 为 `sha256:314dbfb573ce703ab927c9cde1a5ecbc7349291116899f2931481e1418945d5f` / `sha256:6279ef657523fb0d001f2ebe43b3c2d43baa135e3636ac99f3e05a462d37e0b7`。
- 完整回滚点 `/data/apps/agentcici/backups/20260820T021002Z-before-2.8.66-beta.1` 含 12 项、316,840,438 bytes 的受管配置、PostgreSQL custom dump、KB、Qdrant 原生 snapshot/存储、旧应用镜像、容器状态、回滚说明和 SHA-256 清单；dump 恢复目录与清单均校验通过。
- 仅重建 backend/frontend；四个状态服务指纹保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy/restart=0、health UP、版本提交一致、Flyway V122、Nginx、公开 smoke、匿名 JSON 401 和错误日志门禁通过。
- 目标 AgentCiCi SERVICE 身份成功应用 Semattice `devautopilot.standard.v1`，回读 7 对象/87 字段、state=applied。应用回滚恢复上一 `2.8.65-beta.1` backend/frontend；数据恢复需单独批准。生产未修改。

## 2026-08-19 TASK-325 本地开发环境

- 验收纠错：本节原证据只覆盖 backend，不能证明 AgentCiCi 整体产品环境已更新。2026-08-19 用户截图与只读回查确认 frontend 仍为 `2.8.61-dev.1ad25d3 / 1ad25d3923de`，backend 为 `2.8.66-dev.77ce909 / 77ce9095f2bc`；当前属于混合指纹，整体本地版本门禁失败。
- 后续强制门禁：宣称产品环境已更新前，必须同时回读 backend/frontend 的 image ID、image label、容器环境版本/commit、backend 版本 API 和页面可见角标；任一不一致都只能报告单服务已更新，不能进入业务验收。

- AgentCiCi backend 从本地 `main@77ce9095f2bc` 构建为 `2.8.66-dev.77ce909`，镜像 ID `sha256:9b39d55c2ba4019c2a71d3709570f9a002d647a4b1bf897b0931d40f79cc383c`；image label、容器环境和内部 `/system/version` 的版本/commit 一致。
- 仅使用现有受管 Compose 执行 `--no-deps --force-recreate backend`；新容器 `bb9317f32896` healthy/restart=0、`/actuator/health=UP`，`https://cici.localhost/app=200`、匿名 `/auth/me=401 application/json`，DevAutopilot `/api/health` 为 integrated/ok 且 AgentCiCi/Semattice 均 true，启动后 severe 日志 0。
- frontend、DevAutopilot、Semattice、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 的容器 ID/创建时间保持原值。未发送真实产品经理确认，未执行项目改名或其他 Semattice 写入，UAT/生产未修改。
- `cc-local-stack ./stack version` 继续被既有 Semattice 基础版本漂移 `config=1.0.5 / repository=1.0.7` 失败关闭；本轮只声明 AgentCiCi backend 目标门禁，不声明完整 `./stack verify`，也未修改 local-stack 或 Semattice 仓库。

## 2026-08-19 TASK-324 本地开发环境

- AgentCiCi backend 从本地 `main@a9e3d1b0fc06` 构建为 `2.8.66-dev.a9e3d1b`，镜像 ID `sha256:437dc98af23f0764e341f5d9668380252aff80e9ffd17899fafb5b601832aa75`；image label、容器环境和内部 `/system/version` 的版本/commit 一致。
- 仅使用现有受管 Compose 和 runtime 执行 `--no-deps --force-recreate backend`；backend healthy/restart=0、`/actuator/health=UP`，`https://cici.localhost/app=200`、匿名 `/auth/me=401 application/json`，DevAutopilot `/api/health` 为 integrated/ok 且 AgentCiCi/Semattice 均 true，启动后 severe 日志 0。
- frontend、DevAutopilot、Semattice、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 保持原创建时间，未被本轮替换。未发送真实产品经理消息，未执行 Semattice 写入，UAT/生产未修改。
- `cc-local-stack ./stack status/version/up` 当前被既有 Semattice 基础版本漂移 `config=1.0.5 / repository=1.0.7` 失败关闭；本轮只声明 AgentCiCi backend 目标门禁，不声明完整 `./stack verify`，也未越权修改 local-stack 或 Semattice 仓库。

## 2026-08-19 INT-027 UAT DevAutopilot SERVICE 身份修复

- 变更前 root-only 配置备份位于 `/data/apps/agentcici/config-backups/20260819T095048Z-before-service-token-exchange-enable`，Compose、受管环境、容器 inspect 和 SHA-256 清单均非空、`0600` 且校验通过。
- UAT Compose 运行配置启用 `APP_AUTH_OIDC_SERVICE_TOKEN_EXCHANGE_ENABLED=true` 后仅重建 backend；四个状态服务容器指纹保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。变更时运行版本为 `2.8.62-beta.1 / 83b268870b8e`；并行候选发布后最终为 `2.8.65-beta.1 / 784ccd23e933`，开关仍为 true。
- HUMAN Owner/ORG_ADMIN 通过官方管理界面只为 Wukong SERVICE 增加 `identity.principal.sync`；最终 scope 为 `identity.principal.sync,runtime.record.create,runtime.record.read,runtime.record.update`，未轮换 Secret、Client ID 或调整容量。
- 最终六容器 healthy/restart=0，Nginx 有效，公开 smoke 和 30 秒日志稳定窗口通过。应用回滚为恢复上述备份 Compose 并只重建 backend；数据服务无需回滚。生产未修改。

## 2026-08-19 生产 `2.8.65`

- 冻结提交与正式 tag 为 `784ccd23e933` / `2.8.65`；backend/frontend ACR index digest 为 `sha256:4c4a1c4040872081777d6b3b7c60a5a6ca6892ff650d11545c9ab9e495d97039` / `sha256:37922c74ddc518500abe68b81e40e9b8ad8a96011185aef1e6cb094e6c828ae1`。
- 完整回滚点 `/opt/cici/backups/20260819T102115Z-before-2.8.65` 含 PostgreSQL custom dump、KB 文件、Qdrant 原生 snapshot/存储副本、`2.8.64` 两项旧镜像、受管部署配置、容器基线和 SHA-256 清单；dump 与清单均校验通过。
- 仅重建 backend/frontend；database、RabbitMQ、Redis、Qdrant 容器 ID 保持 `5b470883...`、`8289db58...`、`e70d0052...`、`be0f2844...`。六容器 healthy/restart=0，health UP，Nginx 有效，Flyway 最新 V122，首页 200、匿名 `/auth/me=401 application/json`，DevAutopilot integrated health 为 true/true。
- 登录态重试 `org5nszpgj99jaysxv6y` 成功，`orgl624a7r54pzp3e5zv` 回归通过；两者最终均 ACTIVE，知识库 9/35/661、29 文件、549 points 未变化。应用回滚恢复备份 `acr.env` 并重建 `2.8.64` backend/frontend；数据恢复需单独批准。

## 2026-08-19 UAT `2.8.65-beta.1`

- 冻结提交与 tag 为 `784ccd23e933` / `2.8.65-beta.1`；backend/frontend ACR index digest 为 `sha256:06159519de61f3a6a665c1440aae70042b527ac77ef7eb4ad499816eb1699812` / `sha256:b4fff4a7ce4f08e1fca12f70f7fd36d369375fb7cef1d8f09e39881398bbc95e`。
- 备份 `/data/apps/agentcici/backups/20260819T101657Z-before-2.8.65-beta.1` 清单与 PostgreSQL dump 校验通过；仅重建 backend/frontend，六容器 healthy，应用容器 restart=0，版本/commit 一致，公网首页 200，DevAutopilot integrated health 为 true/true。

## 2026-08-19 生产 `2.8.61`

- 冻结提交 `5b67f80de884` 已以正式 tag `2.8.61` 和 backend/frontend ACR index digest `sha256:1c6f3df7fa951e65f9bf1d6387133c5591374ee6db2419747a66ee517ec1270a` / `sha256:257b6eb3f7d4ac8b05a516af65b9bbf82408cc73d8767967a2a69f805e83e00b` 发布；运行 image labels 为 `2.8.61 / 5b67f80de884`。
- 完整回滚点 `/opt/cici/backups/20260819T081036Z-before-2.8.61` 包含受管 env/Compose/Nginx/certs、15,396,484-byte PostgreSQL custom dump、KB 归档和 29 项哈希、7,748,608-byte Qdrant snapshot、`2.8.60` 应用镜像归档及 SHA-256 清单，均为 root-only 并通过格式校验。
- 仅 pull/force-recreate backend/frontend；database、Qdrant、Redis、RabbitMQ ID 不变。六容器 healthy/restart=0，Flyway V122；知识库 9/35/661 核心计数、全部 KB 表行数、文件哈希与 Qdrant 549 points 不变。
- 应用回滚恢复备份 `acr.env` 并重建 `2.8.60` backend/frontend；V122 历史会话恢复只能在用户单独批准后使用发布前整库 dump，不能通过反向 migration。生产登录态业务验收待 HUMAN。

## 2026-08-19 UAT `2.8.61-beta.31`

- 冻结源码为本地/远程 `main@5b67f80de884`，不可变 tag `2.8.61-beta.31` 已推送；backend/frontend linux/amd64 ACR index digest 分别为 `sha256:3d3b4b8f580cb38e879089aa377b4c090dfeaa8fa064859cb7cde28cee025441`、`sha256:fe50d4c33f306abdcd29aa08100b6b1ccdbb2f4164676de10a379f3d9ee09c05`，未更新 `latest`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260819T065648Z-before-2.8.61-beta.31`：Compose、受管环境、PostgreSQL、KB、Qdrant、beta.30 两项旧镜像、容器状态和回滚说明共 12 项，均非空、`0600`，SHA-256 清单复核通过；应用回滚目标 beta.30，数据库/KB/Qdrant 恢复需单独批准。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 与重启计数保持不变。六应用/状态容器 healthy、restart=0，backend health=`UP`、版本/commit/RepoDigest 一致，frontend Nginx 配置有效。
- UAT 首页、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 和匿名 `/auth/me=401 application/json` 通过；30 秒稳定窗口 backend severe、frontend 5xx/upstream 均为 0。登录态业务验收仍待 HUMAN，生产未修改。

## 2026-08-18 TASK-321 / TASK-322 本地开发环境

- AgentCiCi backend 从干净代码提交 `e91b28d6befe` 构建为 `2.8.61-dev.e91b28d`，镜像 ID `sha256:3b664865350d51d50d5ebc076d5b79bc1b631108ab7600f5482c3c93c4af9033`；image label、容器内 `/system/version` 与 Git commit 一致。本地 `main` 的后续提交仅包含 `.claw` 与规格验证记录，不影响源码制品。
- 仅使用 `--no-deps --force-recreate backend` 替换 AgentCiCi backend；容器 healthy、restart=0，`/actuator/health=UP`，`https://cici.localhost/devautopilot/` 返回 `200 text/html`，启动后 severe 日志计数为 0。
- frontend、PostgreSQL、Redis、RabbitMQ、Nginx、Semattice 与 DevAutopilot 的容器 ID 前后不变且 restart=0；Keycloak 与 Qdrant 未重建。未执行真实租户 `initializations`，未修改 Agent Definition、业务数据、UAT 或生产。
- 本次是单 backend 提示组装和模板编译调整，按 local-stack 最短闭环执行目标门禁，未运行完整 `./stack verify`。已登录产品经理页面可读；真实“你好”消息待用户在发送动作前即时确认。

## 2026-08-18 TASK-319 UAT `2.8.61-beta.30`

- 冻结 AgentCiCi 本地/远程 `main` 和 annotated tag peeled commit 为 `39424a982068`。backend/frontend linux/amd64 ACR index digest 分别为 `sha256:5b102dd48d1920a569073403db8c3292c8206de5364c5852e3414026b8456767`、`sha256:6f7fe1aac99b740854448e764160b71a120be4be43ffe46f8aadb739a0424a52`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260818T093113Z-before-2.8.61-beta.30` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.29 两项旧镜像、容器状态、回滚说明和 SHA-256 清单；11 项工件全部非空、`root:root 0600`，dump、tar、gzip 和清单校验通过。即时应用回滚目标 beta.29；数据库恢复需单独批准。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 哈希前后均为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy/restart=0，backend health=`UP`、版本/commit/RepoDigest 一致，Flyway V122/V121/V120 成功，Nginx 有效。
- 发布前后两轮公开 smoke、`/app=200 text/html`、匿名 `/auth/me=401 application/json`、运行 beta.30 资产与组织弹层 CSS 指纹通过。90 秒稳定窗口 backend severe=0、frontend 5xx/upstream=0。
- 本候选没有新增、启用或切换跨项目契约；未代用户执行登录态组织切换视觉验收。生产及其他产品未部署。

## 2026-08-18 TASK-319 本地开发环境

- AgentCiCi frontend 从本地 `main@1ad25d3923de` 构建为 `2.8.61-dev.1ad25d3`；镜像 ID、version/revision label 均回读为 `sha256:d1aa950dc64daced7c21a870242ad5d71ff8ffa833eb15e574babe5c72b5b9ce / 1ad25d3923de`。
- 仅 force-recreate `cici-frontend`；容器 healthy、restart=0，Nginx 有效，`https://cici.localhost/app` 返回 `200 text/html`，运行 CSS 资产包含组织弹层自适应宽度规则。backend、数据库、Keycloak、Semattice 和 DevAutopilot 未重建。
- 本次为单前端样式调整，按 local-stack 最短闭环执行目标门禁，未运行完整 `./stack verify`。浏览器员工会话已过期并回到统一登录边界，未绕过认证；登录态截图待用户重新登录后补充。远端、UAT、生产未修改。

## 2026-08-18 TASK-316 / TASK-317 UAT `2.8.61-beta.29`

- 冻结 AgentCiCi `main`、远程 `origin/main` 和 annotated tag peeled commit 均为 `d2abc9c463b3`。backend/frontend linux/amd64 ACR index digest 分别为 `sha256:56983d2a5ba8d9d94a66c910d95446d008cd1392ae3a0497cbe513c4f3fff8df`、`sha256:cae5a754b957c13b7753478fa40a2c950c7fcfcfccf381006d7eae5c2b65f6b9`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260818T040400Z-before-2.8.61-beta.29` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.28 两项旧镜像、容器状态、回滚说明和 SHA-256 清单；全部工件非空且 `root:root 0600`，dump、tar、gzip 和清单校验通过。即时应用回滚目标为 `2.8.61-beta.28`；V122 数据恢复需要单独批准整库恢复。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 哈希发布前后均为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。V122 成功，六容器 healthy/restart=0，backend health=`UP`、版本/commit/digest 一致，Nginx 有效。
- 两轮公开 smoke、HTTP 301、应用中心、HTML 指南和 391 行 Markdown 地址均为 200；Markdown 返回 `text/markdown` 与 `nosniff`，匿名 auth/应用目录 API 为 JSON 401。90 秒稳定窗口 backend severe=0、frontend 5xx=0。
- 本候选没有创建、启用或切换真实 Provider 连接，也没有改变 Semattice、DevAutopilot 或 Keycloak 契约；未代用户执行登录态应用接入或会话业务验收。生产及其他产品均未部署。

## 2026-08-18 TASK-318 本地开发环境

- AgentCiCi backend 从本地 `main@1a1ab512782b` 构建为 `2.8.61-dev.1a1ab51`，镜像 ID `sha256:8eb9abf014a2cfa520381bac6e89cc33f5eb49e924638256a09971296ee59af1`，image label、`/system/version` 与提交一致。
- 仅 force-recreate backend；容器 healthy、restart=0，`/actuator/health=UP`，正式 `/platform/models` 路由 200，匿名 OneKeyToken 模型枚举 API 为 `401 application/json`，启动后 ERROR/FATAL/Exception 均为 0。数据库、frontend、Keycloak、Semattice 和 DevAutopilot 未重建。
- 本次是单服务日常调整，按 local-stack 最短闭环执行目标门禁，未运行完整 `./stack verify`。当前无平台管理员登录态，真实已保存 Key 的“全部模型”只读回读待用户登录后完成；提交进入本地主线并随本次同步推送远端，但未纳入冻结于 `d2abc9c4` 的 UAT beta.29，生产未修改。

## 2026-08-18 TASK-313 / TASK-315 UAT `2.8.61-beta.28`

- 冻结时 AgentCiCi 本地/远程 `main` 和 annotated tag peeled commit 均为 `242074e72a9e`；后续发布记录提交只更新文档。backend/frontend linux/amd64 ACR index digest 分别为 `sha256:99851d50ad5f9c6ae72b02edf23a1f2949b60f2842179b91becb1eb0f4801c10`、`sha256:e1231cd5366c6b4528569e665436374d8f28dde185f6ca018d5332d321c04953`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260818T020041Z-before-2.8.61-beta.28` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.27 旧镜像、容器状态和 SHA-256 清单；全部工件非空且 `0600`，数据库、tar、gzip 和清单校验通过。即时回滚目标为 `2.8.61-beta.27`。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 分别保持 `d14ef639f035`、`db0945fd318c`、`4166b9909101`、`26aec0ef3a29`。V121 成功，六容器 healthy/restart=0，backend health=`UP`、版本与 commit 一致，Nginx 有效。
- 两轮公开 smoke、HTTP 301、目标页面 200、匿名 auth/应用目录/连接 API JSON 401 与稳定窗口通过；backend severe error=0、frontend 5xx=0，前端资源名回读 beta.28。
- 本次没有创建/启用真实 Provider 连接，且 TASK-315 未改变既有模型或 Semattice Tool 协议，因此无新增启用的跨项目契约。未发送产品经理消息或确认写入，授权态业务验收待用户完成；生产、Semattice、DevAutopilot 和 Keycloak 均未部署。

## 2026-08-18 AgentCiCi 主线远端同步

- 用户授权提交、合并本地 `main` 并推送远程仓库；工作树在推送前干净，本地 `main` 包含 `origin/main@4b76898c`，双方无分叉。
- `git push origin main` 非强制快进成功，将远端 `main` 从 `4b76898c` 更新到 `a25cce7e`；同步历史包含 TASK-313 应用中心 `f56055e9/ded50c26`、命名调整和 TASK-315 实现/修复/验证提交。
- 本次只同步 AgentCiCi Git 主线；未创建或移动 tag，未构建/推送 ACR 镜像，未修改 UAT、生产、父仓、Semattice 或 DevAutopilot 仓库。

## 2026-08-17 TASK-315 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@cc4312edde85` 构建为 `2.8.61-dev.cc4312e`，镜像 ID 分别为 `sha256:440528145bcd3fa823f397d3cd1cacd676a237f2c1499dbb9de0fbd7e6daf1ae`、`sha256:defee9069ee87ea5e935e9f989f28b961b913477ca8a409001ebb3d39f703e32`，label version/revision 与提交一致。
- backend/frontend 均 healthy、restart=0；内部 `/system/version`、`/actuator/health=UP`、正式入口 200、匿名 API JSON 401 和完整 `cc-local-stack ./stack verify` 均通过。Flyway schema 保持 V121，启动无 ERROR。
- 受管 `./stack up` 也重建了当前本地 Semattice 与 DevAutopilot 制品；两者健康、restart=0，未改变源码、数据契约、远端、ACR、UAT 或生产。
- 已登录 Chrome `/app` 刷新后回读 `2.8.61-dev.cc4312e` 与产品经理入口；未代用户发送消息或执行写入。真实 Provider 非写入协议探测已通过，页面固定格式仍待用户验收。

## 2026-08-17 TASK-313 本地开发环境

- AgentCiCi backend/frontend 从本地 `main@f56055e921d2` 构建为 `2.8.61-dev.f56055e`，镜像 ID 分别为 `sha256:a20fc94534b0d6fbde16a5b886d72e64f3cef024785423802c9a388c55c3e0dc`、`sha256:ec0d0a72abd48a3f97a61f039277dff40f9e29f97ec672d3afd1dd20b60e21af`，label version/revision 与提交一致。
- Flyway 从 V120 成功执行 V121；backend/frontend 均 healthy、restart=0，backend `/system/version=2.8.61-dev.f56055e / f56055e921d2`、`/actuator/health=UP`。正式应用中心路由返回 200，匿名运行连接 API 返回 JSON 401。
- 完整 `cc-local-stack ./stack verify` 通过部署域名门禁、共享数据库隔离、TLS、OIDC、应用健康/version 和匿名鉴权边界；部署 JS 已回读运行连接、Base URL、依赖选择和连接修订文案。
- 浏览器正式入口进入运营平台登录边界且 console 无 error/warning；未绕过登录执行授权态业务写入。远端 main、UAT、生产、ACR 与 Git tag 均未修改。

## 2026-08-17 TASK-314 UAT `2.8.61-beta.27`

- 冻结 Git tag/commit 为 `2.8.61-beta.27 / e8dc3b3ad891`，远程 `main`、标签 peeled commit 和运行版本一致。完整备份 `/data/apps/agentcici/backups/20260817T104142Z-before-2.8.61-beta.27` 包含 beta.26 前后端镜像、Compose/受管环境、PostgreSQL、KB、Qdrant 与 SHA-256 清单，工件均非空且 `0600`。
- 仅重建 backend/frontend；database、Redis、RabbitMQ、Qdrant 的容器 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy/restart=0，backend `/system/version=2.8.61-beta.27 / e8dc3b3ad891`、health=`UP`、Nginx 有效。
- 平台管理员通过正式入口同步 `devautopilot.authorization.v4`，回读 4 个分配、verified/actual/stage 均成功。当前 ORG_ADMIN 的 7 对象 AI 表格读取和正式 AI 应用入口 DevAutopilot workspace 均通过；空列表表示租户尚无项目，不再是服务不可用。
- 六项公网 smoke、匿名 401、handoff 200、10 分钟 backend severe error/frontend 5xx 为 0。回滚仅恢复备份并将 backend/frontend 切回 `2.8.61-beta.26`，不删除合法授权事实；生产未修改。

## 2026-08-17 TASK-302 本地开发环境

- AgentCiCi frontend 从包含本任务提交 `a81e3b727bfb` 的最新代码主线 `main@2188e5760087` 的干净 Git 归档构建为 `2.8.61-dev.2188e57`，镜像 ID `sha256:01dfe0b6c6b37ff995e2f236d642ef74a9112eb13cec925e94868e7729eae6e4`；未把同工作树其他未提交文件纳入制品。此后的 main 变更仅为状态文档，不影响制品。
- 仅 force-recreate `cici-frontend`；容器 healthy/restart=0，Nginx 有效，`https://cici.localhost/platform/system-apis` 返回 200，部署 JS 包含“受信应用”。其他服务未因本次文案调整重建。
- 远端 main、UAT 和生产均未修改。授权态视觉回读待平台管理员登录后完成。

## 2026-08-17 TASK-312 UAT `2.8.61-beta.26`

- 冻结 Git tag/commit 为 `2.8.61-beta.26 / a322fd91324b`；远端 `main`、annotated tag peeled commit 与运行 commit 一致。backend/frontend ACR index digest 分别为 `sha256:e755bc30929beefdadd68090c6510a7717401a51223dffc04a5c9a6dd774504a`、`sha256:60ee57e4bd4b3498221f590c0e6bb3dcb2178a72c6392b817d8ba826b77173d7`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260817T093959Z-before-2.8.61-beta.26` 包含 Compose、受管 env、PostgreSQL、KB、Qdrant、beta.25 前后端镜像、旧容器/镜像指纹、回滚说明和 SHA-256 清单；全部非空、`0600`，dump、tar、gzip 与清单校验通过。即时应用回滚目标为 `2.8.61-beta.25`。
- 仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 哈希前后均为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy/restart=0，backend health=`UP`、Flyway 115 项校验且无需迁移、Nginx 有效。
- 公开首页、匿名 401、OIDC discovery、Semattice 与 DevAutopilot health 均通过；真实未登录浏览器访问 `/app` 无需点击即进入统一身份中心。受控失败态旧表单容器 0、按钮 0，浏览器无 error/warning；启动后严重错误与 frontend 5xx 均为 0。生产未修改。

## 2026-08-17 TASK-310 / TASK-311 UAT `2.8.61-beta.25`

- 冻结 Git tag/commit 为 `2.8.61-beta.25 / cc0e8078f5f5`；backend/frontend digest 分别为 `sha256:6685f273eaf74a09a7c3ef0082f308b208d7e5f44a96cee888259593a7657d24`、`sha256:f7eec819048d7678a94af44ce527a7482273a722af1ac05429297b87ff162b3a`，镜像 label 与运行版本一致。
- 完整备份 `/data/apps/agentcici/backups/20260817T034412Z-before-2.8.61-beta.25` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.24 前后端镜像、旧容器指纹和 SHA-256 清单；清单逐项通过。即时应用回滚目标为 `2.8.61-beta.24`，数据库 migration 采用向前修复，不反向删除 V118/V119。
- 仅 backend/frontend 使用 beta.25；database、Redis、RabbitMQ、Qdrant 的容器 ID 保持 `d14ef639f035`、`db0945fd318c`、`4166b9909101`、`26aec0ef3a29`。六容器 healthy/restart=0，health=UP，V118/V119、Nginx、公开 smoke、JSON 401/403 与 10 分钟错误日志稳定窗口通过。
- Semattice 提供方先行为 `1.0.5-beta.2 / 0be03d018ecd`，schema `22/22 ready`；本轮未在未指定租户上执行会写授权事实的 HMAC 模板调用。受权业务开通/恢复验收待完成，生产未修改。

## 2026-08-14 TASK-308 本地开发环境

- AgentCiCi backend 从本地 `main@95656c5b564d` 构建为 `2.8.61-dev.95656c5`，运行镜像 `sha256:99bc4b9938de4243c8a83f4648b0f6187f202a4b8e381116199172c56043ca79`。
- 仅重建 backend；容器 healthy/restart=0，容器内 `/system/version` 回读版本与 commit 一致，`/actuator/health=UP`。数据库、Semattice、Keycloak 和 AgentCiCi frontend 未重建。
- 与 DevAutopilot 完成真实设计驳回和完整 `./stack verify`。UAT/生产未修改；回滚 backend 不会删除已记录的评审事件。

## 2026-08-14 TASK-302 UAT `2.8.61-beta.22`

- 冻结 Git tag/commit 为 `2.8.61-beta.22 / 8522fefb52a2`；backend/frontend ACR index digest 为 `sha256:29e7449a0c88ff50ad17fb759091cc9b98a0cd95193fde8ebc59f6073337b145` / `sha256:96fb8e0040837e0067ae5fe57fb7b88167a0987ea814683bf52c3bc046915fe2`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260814T050143Z-before-2.8.61-beta.22` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.21 旧镜像、容器状态、回滚说明和 SHA-256 清单；PostgreSQL 回读、tar、gzip 与清单均通过，工件权限为 `0600`。即时应用回滚目标为 `2.8.61-beta.21`。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 保持运行。前后端 healthy/restart=0，`/system/version=2.8.61-beta.22 / 8522fefb52a2`、health=`UP`、Flyway V115、Nginx、公开 smoke、页面路由 200、匿名系统 API JSON 401 与稳定窗口错误计数均通过。生产未修改；真实受权页面和独立 Client/HUMAN 业务验收待完成。

## 2026-08-14 TASK-303 / TASK-304 / TASK-305 UAT `2.8.61-beta.21`

- 冻结 Git tag/commit 为 `2.8.61-beta.21 / 626f7e22c774`；backend/frontend linux/amd64 ACR index digest 为 `sha256:ab37b2621ce9800070bf05d3307ba531b46363a0d94a32b69539b1d15731b8d4` / `sha256:c9e24c55c92b8ede42d96c6c3c839d11a62a3cdfde9d50380c7f6575525fc291`，未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260814T021238Z-before-2.8.61-beta.21` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.20 旧镜像、容器状态、回滚说明和 SHA-256 清单；数据库、tar、gzip 与清单均校验通过，全部工件非空且 `0600`。即时应用回滚目标为 `2.8.61-beta.20`。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 的容器 ID 发布前后保持 `d14ef...`、`db094...`、`4166...`、`26aec...`，六容器 healthy/restart=0。
- `/system/version=2.8.61-beta.21 / 626f7e22c774`、health=`UP`、Flyway V111-V115 成功、Nginx 有效；六项公网 smoke、平台集成路由 200、匿名 API JSON 401 和稳定窗口通过，backend severe error=0、frontend HTTP 5xx=0。
- 运行前端制品已核验包含“最长 60 分钟”和 `3600000`。未配置或执行真实 60 分钟厂商任务，业务验收待受权平台管理员完成；生产未修改。

## 2026-08-14 TASK-304 本地开发环境

- 从 AgentCiCi 本地 `main@53715a337691` 构建 backend `2.8.61-dev.53715a3`，镜像 ID `sha256:7f60629c800bdf51ee793b1e2f8ad0d376867002e31607c823259bb1268aab01`。
- backend healthy/restart=0；与 DevAutopilot `1.0.4-dev.a819973` 完成需求确认、任务创建、actor/owner 回读和完整 `./stack verify`。
- 本条仅记录本地开发环境；UAT/生产未修改。回滚应用代码不自动删除已确认需求或已创建任务。

## 2026-08-14 TASK-303 本地开发环境

- 代码制品从本地 `main@e8275353e0d0e6dbdd85e68fb95e8d2ed1a7ff11` 构建为 `cc-aixone/agentcici-backend:local`，镜像 ID `sha256:b98eb8aec5d8101af860fad1c42d5f578602f0334e6234b733213d01030e8dfd`，label 版本/提交为 `2.8.61-dev.e827535 / e8275353e0d0`。
- 仅重建 backend；容器 healthy/restart=0，内部 `/system/version` 与 `/actuator/health=UP` 回读通过。数据库、Keycloak、Semattice 和 AgentCiCi frontend 未重建。
- 与 DevAutopilot TASK-036 完成完整 `./stack verify`，基础设施隔离、TLS、OIDC、应用版本/健康和匿名鉴权边界通过。
- 运行制品提交早于本条文档提交；本条不改变应用制品。真实登录态确认创建新需求仍待业务验收，UAT/生产未修改。

## 2026-08-14 TASK-302 UAT `2.8.61-beta.20`

- 冻结 Git tag/commit 为 `2.8.61-beta.20 / 1b6bb8f1974a`；backend/frontend linux/amd64 ACR index digest 为 `sha256:18c1e7c3c082ad475e3a4b714b96e3f3e385d08deaa6384ec5c944ba0143eb56` / `sha256:48520c667024f7d9e94f9d696c37eb089e0cca115c8a87d7b5f72df4a0180c56`，image label、tag 与运行版本一致；未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260814T002542Z-before-2.8.61-beta.20` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.19 旧镜像、发布前容器状态和 SHA-256 清单；数据库、tar 与 gzip 均通过读取校验，全部工件非空且 `0600`。即时应用回滚目标为 `2.8.61-beta.19`。
- 仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 哈希发布前后保持 `be954223201a867fbef7aff12e97786a136b9ab1e8011c5ab1a0ed968cd3477f`，六容器 healthy/restart=0。
- `/system/version=2.8.61-beta.20 / 1b6bb8f1974a`、health=`UP`、Flyway V115 成功、Nginx 有效；系统 API 与接入应用页面 200，匿名目录/受信应用 API 与生态公司 API 为 JSON 401，错误方法为 JSON 405。两轮六项公网 smoke 与稳定窗口通过，backend severe error=0、frontend 5xx=0。
- UAT 已具备 Keycloak HUMAN 直调代码、受信 Client 治理与公司上下文接口，但本次没有新登记独立 Keycloak Client 的受权凭据，未执行成功登录和 `X-Company-Id` 真实业务调用；生产未修改。

## 2026-08-13 TASK-302 UAT `2.8.61-beta.19`

- Git tag/commit 为 `2.8.61-beta.19 / 2343b9bbafd6`；backend/frontend ACR index digest 为 `sha256:36f9591b78b9f2c22f2dd5c435f0e2d1dbd693978c195dbe6f241c958184bda7` / `sha256:0958cbe7b5614c16548895c233afa828545c1be6775bfd160aefbb0bfb4de0a7`，均为 linux/amd64 且 image label 与 tag 对齐；未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260813T153050Z-before-2.8.61-beta.19` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.18 旧镜像、发布前容器状态和 SHA-256 清单；数据库归档、两个 tar 与旧镜像 gzip 均校验可读，全部工件非空且 `0600`。即时应用回滚目标为 `2.8.61-beta.18`。
- 仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 保持 `d14ef...`、`db094...`、`4166...`、`26aec...`，均 healthy/restart=0。
- backend/frontend healthy/restart=0；`/system/version=2.8.61-beta.19 / 2343b9bbafd6`、health=`UP`、Flyway V114 成功、Nginx 有效。六项 UAT 公网 smoke、系统 API 路由 200、匿名目录 API JSON 401 和稳定窗口通过，backend severe error=0、frontend upstream/HTTP 5xx=0。
- 运行前端制品已核验包含 Keycloak 原始 Token 不可直调、通用 HUMAN 交换端点未发布和新独立应用接入前置文案。没有可复用的平台管理员登录态，授权态视觉/交互验收待运营人员完成；生产未修改。

## 2026-08-12 TASK-291 / TASK-292 UAT `2.8.61-beta.17`

- Git tag/commit 为 `2.8.61-beta.17 / 9bf64d836810`；backend/frontend ACR index digest 为 `sha256:d4b2abe67e01467d7ec6184b1e38962b5dc1630826682ec449b0bd7bd1e67441` / `sha256:0d25185c30c61cc9813fcdd7cc593177cb007fcec1dec40ef6fe8e0c7f845548`，均为 linux/amd64 且 image label 与 tag 对齐；未更新 `latest`。
- 完整备份 `/data/apps/agentcici/backups/20260812T1350Z-before-2.8.61-beta.17` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant 和 beta.16 回滚说明；数据库归档与两个 tar 归档均校验可读，全部文件非空且 `0600`。即时应用回滚目标为 `2.8.61-beta.16`。
- 仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 的容器 ID 发布前后分别保持 `d14ef...`、`db094...`、`4166...`、`26aec...`，均 healthy/restart=0。
- backend/frontend healthy/restart=0；`/system/version=2.8.61-beta.17 / 9bf64d836810`、health=`UP`、Flyway 最近记录均成功、Nginx 有效。五项 UAT 公网 smoke、平台集成路由 200、匿名 API 401 和 30 秒稳定窗口通过，backend severe error=0、frontend 5xx=0。
- 前端资源已核验含代码解释器、联网搜索、网页抓取与两个新增工具标记。三项集成默认关闭且没有真实 UAT 厂商凭据，本次未执行连接测试或 Agent 会话调用；生产未修改。

## 2026-08-11 UAT ACR 持久 pull 登录

- UAT `121.199.37.225` 的 root Docker 配置已为 `op-registry.cloudcc.cn` 建立持久登录，仅用于 `cloudcc-ai-native/cici-backend` 与 `cici-frontend` 的镜像拉取。配置文件 owner/mode 为 `root:root 0600`；凭据值未读取、输出、写入仓库或部署文档。
- 当前主机回读前后端均为 `2.8.61-beta.15`，两个不可变镜像 manifest 均可通过该登录态读取；backend health=`UP`、两容器 `running/restart=0`，UAT 公网首页、匿名 401、OIDC discovery、Semattice version 和 DevAutopilot integrated health 均通过。
- Docker 提示该原生 config 无 credential helper，认证内容不应视为加密存储。凭据必须为该 registry/namespace 的专用 pull-only 机器人账户，按受管轮换策略更换；撤销时先 `docker logout op-registry.cloudcc.cn`，仅在确认没有其他需要保留的 registry 条目后删除对应 root Docker config。

## 2026-08-11 TASK-280 UAT `2.8.61-beta.9`

- Git tag/commit 为 `2.8.61-beta.9 / 500ea8981b7d`；backend/frontend ACR index digest 为 `sha256:6f55267840a0332eb5e027ca4dde3c304cefa947d90994724002e80a35395a37` / `sha256:6bebe2ae7f1a8dac4abe825e6ae6458646ba86cd7bcba0c6981b1c8c8a56b5df`。
- 完整备份 `/data/apps/agentcici/backups/20260811T061948Z-before-2.8.61-beta.9-task280` 共 10 项，均非空且 `0600`，包含 beta.8 前后端镜像、Compose、受管环境、PostgreSQL、KB 与 Qdrant；回滚目标为 beta.8。
- UAT 无 ACR 凭据落盘，linux/amd64 镜像经 SSH 压缩流导入。仅 force-recreate backend/frontend；四个状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- 六容器 healthy、restart=0，版本/镜像/Git SHA、health=`UP`、Flyway V109、Nginx、HTTPS 200、HTTP 301、匿名 401 与 30 秒稳定窗口通过，应用启动后错误计数为 0。
- 受权页面只读确认统一身份信息已从 CloudCC 内容区归位到成员整体信息区，待激活检查动作在任一页签可见，浏览器 0 error/warning；未执行身份或资料写操作。生产仍为 `2.8.60`。

## 2026-08-11 TASK-281 UAT `2.8.61-beta.8`

- Git tag/commit 为 `2.8.61-beta.8 / 9a37f5d6036a`；backend/frontend ACR index digest 分别为 `sha256:91c13d400dc7cae0a937395e874df62f61826f6d4646ed7599bca762df2407f6` 与 `sha256:86f1bd0d819666f1ee75e85dc556e1ce9fb26c2ea6351d7e2ef1504457a1970f`。
- 完整备份 `/data/apps/agentcici/backups/20260811T060100Z-before-2.8.61-beta.8-task281` 含 Compose、受管环境、PostgreSQL、KB、Qdrant 与 beta.7 前后端镜像，10 项均非空且 `0600`。仅重建 backend/frontend；四个状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- UAT 无 ACR 凭据落盘，linux/amd64 镜像经 SSH 压缩流导入。backend/frontend healthy、restart=0，health=`UP`，Nginx 有效，HTTPS=200、匿名 auth=401，稳定窗口错误计数为 0。
- 正式平台页面证明 Demo Company 缺 Owner 时只显示“待补齐”，应用中心仍可操作；“同步标准模板”成功将其从 6 对象升级为 7 对象。回滚只恢复 beta.7 应用与上述备份，不删除已发布 metadata 或业务记录。生产保持 `2.8.60`。

## 2026-08-11 TASK-280 / TASK-281 UAT `2.8.61-beta.7`

- Git tag/commit 为 `2.8.61-beta.7 / 4f7ae57f0aec`；backend/frontend ACR index digest 为 `sha256:5041319afd6316e2b2f777100860a906fccd4bd7487f29cc6d1f1b3736be4980` / `sha256:7bb6f8ca4557a836134836e6f67886b0b0abf4917fa9ef269df5e5bb7bf71db5`。该候选同时包含主线已提交的 TASK-281 字段续答修复和 TASK-280 激活状态协调修复。
- 发布前完整备份 `/data/apps/agentcici/backups/20260811T054429Z-before-2.8.61-beta.7-task280` 共 10 项，均非空且 `0600`；应用回滚目标为 `2.8.61-beta.6 / aeb40c4d25b7`。
- UAT 无 ACR 登录态，使用已核对 digest 的 linux/amd64 镜像经 SSH 压缩流导入。仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- 六容器 healthy、restart=0，health=`UP`，版本/镜像/Git SHA 一致，Flyway V109、Nginx、HTTPS 200、匿名 `/auth/me` 与激活接口 401 通过；30 秒稳定窗口启动后 backend/frontend 错误数为 0。
- 当前无可控 ORG_ADMIN 浏览器会话，未绕过认证调用激活状态同步接口；`18611892001` 仍为 pending，等待页面正式操作与独立浏览器登录回归。
- TASK-281 业务验收已在 beta.7 完成：全字段确认创建真实缺陷、短确认不写入、写后回读和 DevAutopilot 状态流转通过。beta.8 仅增加历史缺 Owner 租户的应用管理解耦，不改变该写入契约。

## 2026-08-11 TASK-281 UAT beta.4 技术发布与 beta.5 修正

- `2.8.61-beta.4 / 50ad506d39b8` 镜像 digest：backend `sha256:6005e7093f14e2ccb47fdb88e38e15208efc731962afbf2bb087712bfdd41ac1`，frontend `sha256:8d8b07cdf3e8498163c6f10af7003972a004a131d60270d2bc23d96bc5df5fcf`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260811T045726Z-before-2.8.61-beta.4-task281` 含 Compose、受保护环境、PostgreSQL、KB、Qdrant 与 beta.3 前后端镜像，全部非空且 `0600`。仅重建 backend/frontend，四个状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- beta.4 技术门禁通过，但业务补偿暴露未重放 metadata template；下一候选为 `2.8.61-beta.5`，仍复用上述备份作为 beta.3 回滚点并在新发布前增加 beta.4 镜像备份。
- beta.5 发布为 `2.8.61-beta.5 / 6473494fff8f`，备份 `/data/apps/agentcici/backups/20260811T051553Z-before-2.8.61-beta.5-task281` 含 8 项非空 0600 工件及 beta.4 回滚镜像；仅重建前后端，状态服务 ID 哈希不变。容器 healthy/restart=0、health/version/Nginx/匿名 401 通过。真实草案验收发现条件将来时误拦，beta.5 不作为最终业务验收版本；beta.6 只修正回执守卫语义。
- beta.6 发布为 `2.8.61-beta.6 / aeb40c4d25b7`，备份 `/data/apps/agentcici/backups/20260811T053156Z-before-2.8.61-beta.6-task281` 含 8 项非空 0600 工件和 beta.5 回滚镜像；仅重建前后端，状态服务哈希不变，health/version/Nginx 通过。续答确认格式业务验收失败，beta.6 保留为回滚点但不作为最终候选。

## 2026-08-11 TASK-281 UAT 候选门禁

- 下一候选固定为 `2.8.61-beta.4`，不得覆盖当前 `2.8.61-beta.3`，且不得在 Semattice `dev_defect` 提供方 UAT 就绪前先发消费方。
- 发布范围仅为 AgentCiCi backend/frontend；数据库、Redis、RabbitMQ、Qdrant 不应重建。发布前执行既有完整备份，发布后核验版本、health、匿名 401、错误日志和真实租户回执。
- 回滚可移除缺陷 Tool/前端回执展示，但成功声明硬门禁必须保留；生产仍为 `2.8.60`，本任务未获生产发布授权。

## 2026-08-11 TASK-279 UAT `2.8.61-beta.2`

- Git tag/commit 为 `2.8.61-beta.2 / c66d9448c95b`；backend/frontend ACR index digest 为 `sha256:bf502768b299fb0cfa2f2b558c0fe866f788c63a9ab2dd96aed1c272d2e1b385` / `sha256:1f5cc632ad7b14d702bdf051d7d482130fe92df9d7743ee58a7e7ac5a9994ed4`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260811T033858Z-before-2.8.61-beta.2` 共 8 项，包含 Compose、受保护环境、旧前后端镜像、PostgreSQL、KB 与 Qdrant，均非空且为 `0600`。
- 仅重建 backend/frontend；database、Qdrant、RabbitMQ、Redis 容器 ID 分别保持 `d14ef...`、`26aec...`、`4166...`、`db094...`。V109、health、Nginx、HTTPS 与匿名鉴权边界通过。
- 后续 `2.8.61-beta.3 / 47affe4086e5` 是 `c66d9448c95b` 的后继版本，当前 UAT 已由该版本覆盖并包含 TASK-279。未为本任务回退 beta.3；最终业务验收在 beta.3 上完成。
- 回滚应用时恢复上述备份并仅重建 backend/frontend；V109 为向后兼容新增可保留。若回滚到只理解 `PRIMARY_OWNER` 的旧代码，必须先通过受管初始化恢复兼容绑定，禁止直接写库。

## 2026-08-11 TASK-280 UAT `2.8.61-beta.3`

- Git tag/commit 为 `2.8.61-beta.3 / 47affe4086e5`；backend/frontend ACR index digest 为 `sha256:90a91ac1509e9ada62f691ebb7c5eb6f99fdaa3f31e1d2d5bafdea970534cc62` / `sha256:a48cc331e49e7af4955e336af059739980d2d3da8c4b9b822c63a054a35f3714`。该版本沿主线包含已存在的 `2.8.61-beta.2` TASK-279 变更。
- 发布前完整备份 `/data/apps/agentcici/backups/20260811T034122Z-before-2.8.61-beta.3-task280` 的 Compose、受保护环境、旧前后端镜像、PostgreSQL、KB 与 Qdrant 工件均非空且为 `0600`；应用回滚目标为 `2.8.61-beta.1`。
- UAT 无 ACR 登录态，使用已核对 digest 的 linux/amd64 镜像经 SSH 压缩流导入，未复制或持久化 registry 凭据。仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- 六容器 healthy、restart=0，health=`UP`，版本/镜像/Git SHA 一致，Flyway V109 成功，Nginx 有效，HTTPS 200、HTTP 301，匿名 `/auth/me`、用户目录与身份协调接口为 401。30 秒稳定窗口内启动后应用错误计数为 0；启动期间前端曾在后端就绪前出现一次短暂 upstream refused，未持续。
- 本次发布未调用 `18611892001` 的真实身份协调接口、未发激活邮件或修改成员状态；真实 ORG_ADMIN 页面与登录回归仍待业务验收。

## 2026-08-11 TASK-278 UAT `2.8.61-beta.1`

- Git tag/commit 为 `2.8.61-beta.1 / d4b273af39c2`；backend/frontend ACR index digest 为 `sha256:be29c222ba8b6212a6d916d89c94e2301145f3196abeb866afe0d96048e59c57` / `sha256:d28768f068aba1644de93fec3ecf4ecdfcb356a0456f06da2057bc3768acdb4d`。
- 发布前备份 `/data/apps/agentcici/backups/20260811T021914Z-before-2.8.61-beta.1` 的 Compose、受保护环境、旧前后端镜像、PostgreSQL、KB 与 Qdrant 九项均非空且为 `0600`。回滚目标为 `2.8.60-beta.1`。
- 最终 Compose 渲染和容器环境回读 HUMAN scopes 均为 `metadata.read,runtime.record.read,runtime.record.create,runtime.record.update`，SERVICE scopes 保持 `identity.principal.sync,runtime.record.read,runtime.record.create,runtime.record.update`。
- 仅 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 哈希前后保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy，health=`UP`，版本/镜像/Git SHA 一致，Nginx 有效，首页 200、匿名 AI表格 401，启动 ERROR 计数 0。
- 受权租户浏览器回读 6 个 DevAutopilot 业务对象及真实空记录状态，原“业务数据服务暂时不可用”消失；浏览器 console error/warning 为 0。生产未修改。

## 2026-08-10 TASK-275 双租户 UAT 业务验收

- AgentCiCi UAT 运行版本保持 `2.8.60-beta.1 / 93a487f4e393`；本轮不重建 AgentCiCi 容器。第二租户 Owner 恢复、DevAutopilot activation、机器主体新增/改名/暂停/恢复均通过正式平台或租户管理接口执行，未直接写数据库。
- A/B 租户使用同一受控 HUMAN 身份切换组织验收；运行会话按当前组织签发，A/B 团队和 workspace 资源互不出现。第二租户 Semattice 参数覆盖负向仍锚定当前 tenant/company。
- 一次性机器 Secret 未读取、未保存或写入日志；第二租户测试 developer 最终保留为 active，名称为 `第二租户 UAT 开发者（已编辑）`。如需清理必须走租户机器主体正式生命周期，不通过数据库删除。

## 2026-08-10 TASK-276 / TASK-277 生产 `2.8.60`

- Git tag/commit：`2.8.60 / 451f797e61df`。ACR backend/frontend index digest 为 `sha256:1b4e96962c08900ae0372601b9a7fc99134615bcc0cd00aff36b5f102d8dba4a` / `sha256:859d23f4a65944161b22cc5a6cbeac2bc2db762a8f21a799eb490776491047c9`。
- 发布前完整备份 `/opt/cici/backups/20260810T122603Z-before-2.8.60-owner-identity` 的环境、PostgreSQL、KB 与 Qdrant 均非空且为 `0600`。
- 仅 pull/force-recreate backend/frontend；四个状态服务 ID 哈希发布前后保持 `88b03a2170ddc7acc3047e9ae42926298479174e218f956e226cfd4f2b9fbea7`。六容器 healthy，health=`UP`，Flyway 成功验证 104 项 migration 且 schema 保持 V108，Nginx 有效，`x.agentcici.com` HTTPS=200、HTTP=301，匿名 `/auth/me`、Owner 状态与协调接口均为 401，启动 ERROR 计数 0。
- 回滚目标为 `2.8.59`：恢复上述备份中的 `acr.env.before-release`，再仅重建 backend/frontend。目标 Owner 的协调尚未执行，应用回滚不涉及用户数据回滚。

## 2026-08-10 TASK-276 / TASK-277 UAT `2.8.60-beta.1`

- Git tag/commit：`2.8.60-beta.1 / 93a487f4e393`。ACR backend/frontend index digest 为 `sha256:a68027a8949a2ec315bc756caa7661e76fd347988158ece806d06ee3128ca06c` / `sha256:2da4ffdb68a4c1e83df589644282fb768644d3f865c61f8fb3cc646b616ce966`；UAT 容器镜像 ID 与两项 index digest 一致。
- 发布前完整备份 `/data/apps/agentcici/backups/20260810T113637Z-before-2.8.60-beta.1` 包含 Compose、root-only 环境、PostgreSQL、KB、Qdrant 和两项传输镜像，均非空且为 `0600`。
- 仅重建 backend/frontend；四个状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。两个应用容器 healthy，health=`UP`，104 个 migration 校验通过且 schema 保持 V108，Nginx 有效，首页 200，匿名 `/auth/me`、Owner 状态和协调接口均为 401，启动 ERROR 计数 0。
- 受权平台页面显示版本 `2.8.60-beta.1`；目标租户 Owner 为 `OWNER/ACTIVE`、统一身份“已绑定·可登录”，Semattice 与 DevAutopilot 均运行中，浏览器 0 error / 0 warning。生产未修改。
- 残余风险：启动日志仍提示 `SecretCipherService` 使用开发回退密钥；已登记独立 issue，不能把该 UAT 配置称为生产安全基线。应用回滚目标为 `2.8.59-beta.12`，不删除合法 Owner/应用状态。

## 2026-08-10 TASK-275 第二测试租户应用开通前置状态

- 通过 UAT 平台正式生命周期接口为 `orgvdd8xckmvc8r5yi6q` 开通 Semattice；权威 binding 为 `PROVISIONED`，不涉及镜像发布、容器重启或数据库直写。
- DevAutopilot 开通因缺少 active `OWNER/ORG_ADMIN` 失败关闭；唯一 Owner 尚处于 `PENDING_ACTIVATION`，activation 表无该租户记录。后续只允许在 Owner 邮件激活和首次 OIDC 登录完成后重试，不以平台身份或直接写库补造负责人。
- 本次状态变化仅新增该租户 Semattice provisioning；如需撤销，应走 Semattice 独立生命周期管理，不通过删除 AgentCiCi 数据回滚。

## 2026-08-10 TASK-275 UAT beta.11 产品经理领域 Skill 初始化

- 最终发布 `2.8.59-beta.11 / 4b0be4c4328e`；backend/frontend ACR index digest 为 `sha256:b50b79c8c0c7ecd82a2c93a6eb49a666c0edc176896793a54217a828f3e17c7b` / `sha256:558dfb03cf1b052d2820e553456d42cf15383b682a50fde59bdd7ade5b0956a2`。beta.10 先交付 Skill 绑定补偿，beta.11 增加完整 readiness 后覆盖为最终候选。
- beta.10 与 beta.11 发布前完整备份分别为 `/data/apps/agentcici/backups/20260810T094629Z-before-2.8.59-beta.10` 和 `/data/apps/agentcici/backups/20260810T095320Z-before-2.8.59-beta.11`；Compose、受保护环境、PostgreSQL、KB、Qdrant 均非空。传输归档已移入对应备份目录，未保存 ACR 凭据。
- 仅重建 backend/frontend；database、Redis、RabbitMQ、Qdrant 均持续 healthy。最终 backend/frontend healthy，内部 `/system/version=2.8.59-beta.11 / 4b0be4c4328e`、`/actuator/health=UP`，Nginx 有效，启动与正式补齐窗口无应用 ERROR。
- 已通过已登录平台会话执行正式 `initializations`，未直接写数据库。即时应用回滚目标为 beta.10；回滚不删除已经建立的 Skill binding、Skill version 或工作流版本，旧版本会按 readiness 正确显示需要补偿。

## 2026-08-10 TASK-276 UAT beta.6

- 发布 `2.8.59-beta.6 / 9563aa2e37cf`；backend/frontend ACR index digest 为 `sha256:85b6dd15e737f4328ec37e6ddeb2baeb9a1b337a3acde8d06593a5e0ec11baf1` / `sha256:b9fce4c004dbe0a9a7f2ce9e381f96df153161e1f0b5658facd1599d410ab55d`。
- 发布前完整备份 `/data/apps/agentcici/backups/20260810T082214Z-before-2.8.59-beta.6-task276` 的 Compose、受保护环境、PostgreSQL、KB 与 Qdrant 均非空且 `0600`。沿用无凭据落盘的 SSH 流式镜像导入，只重建 backend/frontend。
- UAT 6 容器 healthy，health=`UP`，`version/imageTag=2.8.59-beta.6`、`gitCommit=9563aa2e37cf`，Nginx 有效、首页 200、匿名租户 API 401、近 15 分钟应用错误计数 0。
- 正式 API 创建测试租户 `orgvdd8xckmvc8r5yi6q`；Owner 保持待激活，测试租户在用户完成邮件激活和首次登录前保留。回滚仅切回 beta.4 或 beta.3，不回滚数据库来删除已创建测试租户。

## 2026-08-10 TASK-276 UAT beta.4 与回滚点

- 发布 `2.8.59-beta.4 / 1d74f436ec7d`，backend/frontend ACR index digest 分别为 `sha256:426b59ed143ba5984478ce62bd49a9d718c98f819ee9d666eab77b55cc7bc97d` 与 `sha256:8c1057639b5c0127841dadebcd34e3b41ead81a9132b6a6492a98ab69d736adf`。
- 完整备份位于 `/data/apps/agentcici/backups/20260810T081112Z-before-2.8.59-beta.4-task276`，Compose、受保护环境、PostgreSQL、KB 与 Qdrant 工件均非空且权限为 `0600`。
- UAT 主机 ACR 登录态缺失，未复制凭据；将同一 `linux/amd64` 不可变镜像通过 SSH 流式 `docker load` 导入。只重建 backend/frontend，6 容器 healthy，health=`UP`、版本/commit/imageTag 一致、Nginx 有效、首页 200、匿名租户 API 401、启动错误计数 0。
- 真实租户创建因数据库 trigger `public_id` 未刷新而失败关闭，未遗留账户、标识、租户、成员或外部身份；beta.4 保持运行，下一 beta 仅重建 backend/frontend。回滚目标仍为 `2.8.59-beta.3`。

## 2026-08-09 TASK-275 Principal 初始化与生命周期 UAT 发布

- 最终候选为 `2.8.59-beta.3 / 5be204680e16`；backend/frontend ACR index digest 为 `sha256:c25b2a364105f10bd21e947c4f04548dd679ae5408fed64362264551d586cd02` / `sha256:fea65dc783afecd0691081a04a3b32ce49228cc61dd08d5817d1a75839cf9c83`。运行容器 healthy，版本接口与镜像 tag/commit 一致。
- 配置提交 `666d570` 将 HUMAN/SERVICE 的 `identity.principal.sync` 固化到版本化 UAT override；备份为 `/data/apps/agentcici/config-backups/uat-config-20260809T135729Z-666d570`。配置不含 Token/Secret，未扩大模板业务 scope。
- beta.3 发布前完整备份为 `/data/apps/agentcici/backups/20260809T140316Z-before-2.8.59-beta.3`，PostgreSQL、KB 与 Qdrant 归档均非空；只重建 backend/frontend，状态服务未重启。前一 beta.2 完整备份为 `/data/apps/agentcici/backups/20260809T134846Z-before-2.8.59-beta.2`。
- 正式初始化接口返回 200，发布后 AgentCiCi/Semattice/DevAutopilot 近 10 分钟错误扫描均为 0。回滚只切回 AgentCiCi 不可变镜像和对应 override；不得删除已成功建立的 Semattice Principal 投影。

## 2026-08-09 TASK-275 OACT activation 与 Semattice console UAT 发布

- UAT 已发布 `2.8.59-beta.1 / 94ceb612bd71`。backend/frontend ACR index digest 分别为 `sha256:cf86c56f6da8dcacb722f950c709f7aedaacf863a667621ca4b35c9e6659b13f`、`sha256:eee359c653a7c25d7e38fa7e4fc94620f6985f19653d7be5e89b4519a38f297c`；运行容器 healthy，内部版本回读一致。
- 发布前备份 `/data/apps/agentcici/backups/20260809T053537Z-before-2.8.59-beta.1` 包含非空 Compose、受保护环境、PostgreSQL、KB 与 Qdrant 工件。仅更新 backend/frontend；回滚恢复该备份并明确切回上一候选，不回滚 Semattice 或 DevAutopilot 数据。
- UAT 显式设置 `APP_SEMATTICE_CONSOLE_BASE_URL=https://uat.agentcici.com`；浏览器 console 入口必须落到同源 `/console/`。activation 专用 filter 只匹配官方 DevAutopilot activation 路径，并验证 issuer/audience/authorized party/company/tenant/principal 后才注入受信上下文。

## 2026-08-09 TASK-275 标准初始化与版本基线 UAT 发布

- UAT 已发布 `2.8.58-beta.1 / 4ffab5c43c0e`。backend/frontend ACR index digest 为 `sha256:a83273b7c7690270bdce9888be18b5cfb9dfaac88e727e8c25ca0848c6b3f6d8` / `sha256:0fe35f9adcd7b63979b00734fc736f6c33a34640f0626631b4e2f36e85d857c9`；运行两个容器的 image、`CICI_APP_VERSION` 与 backend `/system/version` 同为该版本/提交。
- 生产运行版本已为 `2.8.58`，因此测试发布以显式 `RELEASE_PRODUCTION_BASE=2.8.58` 生成 `2.8.58-beta.1`。发布脚本现在拒绝将测试版本回退到低于仓库最新 production tag 的基线；`uat.secrets.env` 不保存版本或 image tag，受管 UAT Compose 使用仓库的 ACR 覆盖层只重建 backend/frontend。
- UAT 主机无 ACR 拉取授权，已导入经本地 linux/amd64 构建核验的两项镜像，未复制或持久化 registry 凭据。发布前备份为 `/data/apps/agentcici/backups/20260809T050558Z-before-2.8.58-beta.1`，Compose、受保护环境文件、PostgreSQL、KB 与 Qdrant 工件均非空；database、Redis、RabbitMQ、Qdrant 未重启。
- 匿名 `POST /auth/devautopilot/handoff`=401、匿名 `POST /api/platform/tenants/{companyId}/applications/devautopilot/initializations`=401；后者说明 Nginx `/api/platform` rewrite 与控制器路径正确。正常平台管理员仅可显式补齐既有 activation；未伪造业务会话或创建主体。

## 2026-08-09 TASK-275 租户 handoff 与统一标识 UAT 发布

- 已按 test channel 发布 `2.8.57-beta.3 / 1b07df5c6f40`。backend/frontend ACR index digest 为 `sha256:490183ac35a30ab7f263383a7140bc387036383a39b1c6d6aec9a858ee644456` / `sha256:1310b43f6bae4a64d1ee912b67023fe3827b38611c8f1e84d18d9d534dcb7479`；UAT 两个容器均为该不可变 image 且 healthy，backend `/system/version` 回读同一 version、imageTag、commit。
- 发布前备份为 `/data/apps/agentcici/backups/20260809T033000Z-before-2.8.57-beta.3`，含 UAT Compose、root-only 环境文件和非空 PostgreSQL dump；仅重建 backend/frontend，未重启数据与消息基础设施。
- UAT 同机 gateway 的 `POST /auth/devautopilot/handoff` 匿名返回预期 `401`，`POST /openapi/v1/official/devautopilot/handoff/exchange` 缺 ticket 返回预期 `400`。正常租户用户 handoff 和 Semattice 业务数据回读需使用真实业务会话完成。
- 回滚：恢复该备份中的 Compose 与受保护环境文件，明确将 backend/frontend 切回 `2.8.57-beta.2` 后仅重建这两个容器；不得使用 `dev`、`uat` 或 `latest` 作为运行版本事实。

## 2026-08-09 TASK-275 机器主体新增与编辑 UAT 发布

- 已按 test channel 发布 `2.8.57-beta.2 / 2753d268acd9`。backend/frontend ACR index digest 分别为 `sha256:aa50caecfe55aaa8ac6c0b0e1f8494578a21966dda7f8fa0f20dec2303a92cdc` / `sha256:7da4fa653ff8b1de55ea183ea29a09b669708c7419eccd8100699f08179a6a37`；Git tag、运行时 `CICI_APP_VERSION`、前端 `VITE_CICI_APP_VERSION` 和镜像 tag 统一为同一版本。
- UAT 主机缺少 ACR 拉取凭据，已仅传入已验证的 linux/amd64 backend/frontend 工件并加载到本机 Docker；未复制或持久化 registry 凭据。发布前备份为 `/data/apps/agentcici/backups/20260809T025000Z-before-2.8.57-beta.2`，包含 Compose、受保护环境文件和非空 PostgreSQL dump。
- 仅强制重建 backend/frontend；database、Redis、RabbitMQ、Qdrant 未重启。两个应用容器 healthy，backend `health=UP`，`/system/version=2753d268acd9 / 2.8.57-beta.2 / 2.8.57-beta.2`，frontend `nginx -t` 通过，外部管理页为 200，匿名团队 API 为预期 401。
- 回滚：恢复上述备份中的 `docker-compose.uat.yml` 和 `uat.secrets.env`，仅重建 backend/frontend；或将两项 image tag 明确切回 `2.8.57-beta.1`。不得使用 `dev`、`uat` 或 `latest` 作为运行版本事实。

## 2026-08-09 TASK-275 DevAutopilot 租户团队职责收敛 UAT 发布

- 已按发布脚本的 test channel 发布 `2.8.57-beta.1 / e5c097adda5f`。backend/frontend ACR index digest 分别为 `sha256:3b642bf91ee54b9e6d36783ca958b032a88b0a1b8667961190d23bafc1c9d091` / `sha256:6f87671503319c8dc06be405fc137d3d6edb6fba90e258918500c6ac90b5bb3c`；Git tag、运行时 `CICI_APP_VERSION`、frontend `VITE_CICI_APP_VERSION` 和镜像 tag 统一为同一版本。
- UAT 原先错误地以本地 `cici-*:uat` 运行，同时 backend 写为 `2.8.57-beta.2`、frontend 退回 `dev`。现 Compose 显式指向上述不可变 ACR tag，版本接口返回 `e5c097adda5f / 2.8.57-beta.1 / 2.8.57-beta.1`。UAT 主机未配置 ACR 拉取凭据，因此从已验证的本地 ACR 工件加载镜像，未复制或持久化任何 registry 凭据。
- 发布前备份 `/data/apps/agentcici/backups/20260809T013059Z-before-2.8.57-beta.1` 含 Compose、受保护环境文件和非空 PostgreSQL dump；仅重建 backend/frontend，database、Redis、RabbitMQ、Qdrant 未重启。V108 成功，两个应用容器 healthy，匿名团队 API 为预期 401。
- 回滚：恢复该备份中的 UAT Compose/环境并仅重建 backend/frontend；V108 只是允许 activation 历史 initiator 为空，可安全保留。不得以 `dev` 或 `:uat` 作为版本事实。

## 2026-08-05 TASK-274 机器主体 scope 治理发布

- 已发布 AgentCiCi `2.8.57 / 750fb71ab47d`。ACR backend/frontend index digest 为 `sha256:4a3c552bc498fa9e4bef823b3e2c071d4b1e34a05b9e2a2ec590d1a2aa46c13b` / `sha256:1ad603f8e395c340b38f61616242be4076611c40fbb5309d31cc76ff171a2d02`。
- 发布前备份 `/opt/cici/backups/20260805-235439-before-2.8.57-task274-scope-governance` 包含非空 env、PostgreSQL dump、KB 和 Qdrant。仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 保持健康运行。
- 生产新增 `APP_AUTH_OFFICIAL_ACCESS_SEMATTICE_SERVICE_SCOPES`，允许受治理 SERVICE 使用 `runtime.record.delete`；HUMAN 默认 `APP_AUTH_OFFICIAL_ACCESS_SEMATTICE_SCOPES` 未加入该项。
- 六容器 healthy，backend `/actuator/health=UP`，frontend Nginx 配置有效，`https://x.agentcici.com/`=200，匿名 `/api/admin/service-principals`=401。管理页页脚回读版本 `2.8.57`。
- 回滚先用同一管理 API 将大乔恢复为发布前 4 项 scope；如需应用回滚，再恢复备份环境并将 backend/frontend 切回 `2.8.56`。无需轮换 Client Secret。

## 2026-08-05 TASK-273 Keycloak 生产人工运维交接

- SSO 主机为 `115.29.222.70`，Keycloak `26.7.0` 以 `keycloak.service` 运行；PostgreSQL `16.13` 以 `postgresql-16.service` 运行，监听仅限 `127.0.0.1:5432` / `::1:5432`。
- 实际 Keycloak PostgreSQL 连接值已仅保存在主机 `/root/agentcici-ops-handover/keycloak-postgres.env`（`0600 root:root`）；不得向 Git、工单、聊天或日志复制。`verify-keycloak-postgres.sh` 已完成真实非交互连接验证，且两项 systemd 服务均为 active。
- 长期人工运维说明见 `docs/keycloak-production-operations-handover.md`。人工接管必须使用人员自己的阿里云控制台与 SSH 公钥；自动化私钥不属于交接物。

## 2026-08-05 TASK-270 悟空机器凭据恢复

- Client ID 改名是 Keycloak client 与 AgentCiCi 身份记录的一致性操作，不需要重建 AgentCiCi 容器；DevAutopilot 仅需重启以重新读取 allowlist。
- 本次改名后原受管密钥被 Keycloak 拒绝，因此在受管 Keycloak 客户端上轮换一次 Client Secret，并仅写回悟空 `root:root 0600` 的生产凭据文件。旧密钥即时失效，未输出、未写入数据库、代码或日志。
- 生产验收为悟空新 Client ID 的 Keycloak → OACT → Semattice identity sync → CLI 身份/只读任务链路；旧 ID 负向认证失败。

## 2026-08-05 TASK-249 / 组织简档代理热修

- 生产 `x.agentcici.com/admin/company/profile` 曾落入 SPA 并返回 `200 text/html`；根因是 Nginx API 正则仅保留了旧 `admin/organization/(profile|export-jobs)`，没有当前 `admin/company/profile`。
- 已同步版本化 `nginx.cici.conf` 与 `nginx.cici.ssl.conf`，补齐精确 `admin/company/profile` 代理，同时保留生产已有 `admin/users` 与 `admin/service-principals` 白名单，避免覆盖配置时回归其他管理接口。
- 配置备份位于 `/opt/cici/backups/20260805-154049-before-task249-company-profile-proxy`。容器内 `nginx -t` 成功后仅热重载 Nginx；无镜像构建、无应用容器重建、无后端/数据库重启。回环、公网 IP/SNI 和 DNS 请求均为后端 `401 application/json`，frontend/backend healthy、backend health=`UP`。
## 2026-08-05 TASK-267 / 机器主体管理页面发布

- 已发布 AgentCiCi `2.8.50 / 82e1c249e622`。ACR backend/frontend index digest 为 `sha256:affd6eb08e2b65c0a5d33c2ca59dbe29e72208444b618714eab31a1e478dd20c` / `sha256:59e52f78a72dc11197ed9aa976f0dd21e319dabe2bb393d6ae189b871b3e35c0`。
- 发布前备份 `/opt/cici/backups/20260805-052058-before-2.8.50-machine-principals` 的 `acr.env.before-release`、`postgres.dump`、`kb-files.tgz`、`qdrant.tgz` 均非空。仅 pull/force-recreate `cici-backend` 与 `cici-frontend`，四个状态服务容器未重启。
- 六容器 healthy，backend `/actuator/health=UP`，`/system/version` 返回该版本和提交，Nginx 配置有效，`x.agentcici.com` HTTPS=200、HTTP=301；匿名 `/admin/service-principals` 为预期 401。未使用或输出任何 Client Secret；受权 ORG_ADMIN 真实会话验收待完成。

## 2026-08-04 TASK-265 / 产品经理评审 Tool 生产验收

- 当前生产 AgentCiCi `2.8.45 / 435ee0af6e2d` 保留并运行 query/create/review 三个正式 Tool、always-on Skill 与产品经理 SERVICE 显式绑定；生产数据库回读均为 enabled。
- 产品经理 SERVICE Principal `742daca1-ce58-49cc-9e53-530444ba1c47` 通过 `PRIMARY_OWNER` 委托执行评审，登录 HUMAN 只提供委托、确认和审批上下文。
- DEV Autopilot 正式任务 `019fcc18-756f-7782-a9e7-bf34e9c94670` 已完成设计与完成双 Gate 的线上闭环，最终状态 `已完成 / 100% / revision 13`。
- 回归包含设计前进度拒绝、开放阻塞完成拒绝、休息态开发者拒绝和产品经理冒用开发者 CLI 拒绝；未输出 OACT、client secret 或可复用凭据。

## 2026-08-03 TASK-263 / 产品经理正式能力与 SERVICE 执行

- 当前生产版本 `2.8.40 / f4011a8a3b79`；backend/frontend ACR index digest 分别为 `sha256:878940d5438dddce050adf1a495795b05b94059f13f78e63a7c62413fd322d1d`、`sha256:956d3834725d2dc75ed7be288d4b968be60bc29411aa888d7f007a6ec8ad112d`。
- `2.8.39 / 3d91cbdd583a` 首次发布 V101 与正式绑定；线上验收发现普通工具分支漏传 `agentId` 后按失败关闭返回 400，修复与回归测试后由 `2.8.40` 覆盖。
- 备份：`/opt/cici/backups/20260803-184554-before-2.8.39-task263` 包含 env、PostgreSQL、KB、Qdrant；补丁发布备份为 `/opt/cici/backups/20260803-105546-before-2.8.40-task263-patch`。
- 部署仅 pull/force-recreate backend/frontend；database `a18b6aae...`、Redis `b9b34aaf...`、RabbitMQ `c02ab1c...`、Qdrant `8a2cf4be...` 容器 ID 保持不变。
- 运行：backend/frontend healthy，health `UP`，版本接口为 `2.8.40 / f4011a8a3b79`，Nginx 配置有效，Flyway V101 成功。
- 公网：`https://x.agentcici.com/`、`https://x.agentcici.com/devautopilot/`、`https://semattice.agentcici.com/healthz` 均为 200。
- 回滚：应用可切回 `2.8.39`，V101 新增表可保留；但 `2.8.39` 不满足普通工具携带 Agent 身份的线上契约，不应作为正常运行目标。已由 SERVICE 创建的业务记录保留审计，不自动删除。

## 2026-08-01 TASK-262 / DEV Autopilot 研发身份与 HTTPS 入口

- 当前生产应用版本为 `2.8.38`；`cici-backend` 与 `cici-frontend` 均须保持 healthy。
- 生产启动 frontend 必须同时使用基础和 SSL override：`docker compose --env-file acr.env -f docker-compose.acr.yml -f docker-compose.acr.ssl.yml up -d --no-deps frontend`。只使用基础 Compose 会移除 443 映射并把 SSL 配置替换为 HTTP 配置。
- `/devautopilot/` 由版本化 `deploy/nginx.cici.conf` 与 `deploy/nginx.cici.ssl.conf` 动态解析同一 Docker 网络中的 `dev-autopilot:4177`；应用缺失时只让该路由失败，不阻断 AgentCiCi Nginx 启动。
- 开发者生产凭据位于 `/opt/devautopilot/secrets/developer.env`，必须为 `root:root 0600`。轮换只通过产品总监管理 API 获取一次性新 secret，原子替换文件后验证旧 secret 失败、新 secret 与 CLI 成功；禁止在终端、工单或 Git 中输出值。
- 生产生命周期演练和密钥轮换前均创建 `0600` 备份；当前轮换前备份为 `/opt/devautopilot/secrets/developer.env.backup.20260801T153334Z-before-lifecycle-rotation`。
- 健康检查：`https://x.agentcici.com/devautopilot/api/health` 应返回 `status=ok`、`mode=integrated`；`https://x.agentcici.com/.well-known/agentcici-oact-jwks.json` 应返回一枚 `kid=agentcici-oact-20260724` 的 RS256 公钥。

## CloudCC Embedded Asset

- 2026-07-14 TASK-206: `component-customer-workbench` pagecomponent V15 is active at ID `6a5628cee4b0a577cbba2088`; `customer_interaction_workbench` customPage V9 references that ID with `embedded=true` and `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`.
- Publish, dry-run binding, update and readback were executed only through `cc-customization-expert-msapi 2.1.279-msapi`. The real CRM injection page passed initial load and two consecutive refreshes with connected identity and customer data.

## Production Capacity Snapshot

- Observed read-only on 2026-05-12T10:08:10Z for `agentcici.com` ECS `47.97.119.160`: single Docker Compose host, 8 vCPU, 30GiB RAM, 40GiB root disk with 31GiB available; no backend container CPU or memory limit configured.
- Idle/low-load `docker stats` at observation time: `cici-backend` about 763MiB RSS-equivalent container memory and 0.09% CPU, `cici-frontend` about 9MiB, PostgreSQL about 40MiB, Qdrant about 42MiB, RabbitMQ about 118MiB, Redis about 3MiB; host memory used about 2.2GiB with 28GiB available.
- PostgreSQL observation at the same time: `max_connections=100`, `shared_buffers=128MB`, database size about 16MB; key live table estimates included `chat_message=581`, `chat_session=69`, `kb_chunk=310`, `agent_run_trace=14`, `audit_log=607`.
- Capacity inference from code/config: current deployment is suitable for pilot/small production traffic, but high-concurrency AI streaming depends on adding explicit executors, pool sizing, distributed rate limits, and queue/backpressure controls before scaling backend replicas.

## Latest Release

- 2.8.19 TASK-248/TASK-251 平台用户目录与全局公共编号 on 2026-07-26:
  - Git/发布：固定发布提交 `99d4cc3cb206`，annotated tag `2.8.19` 已推送；backend/frontend ACR index digest 分别为 `sha256:b9db2c4974aeebb63c38223189bd41eb9f17b8d875faa87de19d4c3ea9303b82`、`sha256:a44c54c6a8d7a0eaea547c3a557712fe881e641a4f1466d6fc98f781dbc7cab7`。`2.8.18` 因构建期间并发提交导致镜像提交与标签不一致，未部署，不能作为回滚目标。
  - 备份/部署：`/opt/cici/backups/20260726-220110-before-2.8.19` 的 env、PostgreSQL、KB、Qdrant 均非空。Compose 对基础设施也使用同一 tag，ACR 未提供对应基础设施 tag 时，将 ECS 上已验证的 `2.6.12` 基础设施镜像本地标记为 `2.8.19` 后成功重建六服务。
  - 验收：六服务 healthy，内网 health `UP`，版本 `2.8.19 / 99d4cc3cb206`，Flyway V97 为 `success=true`，生产 `user_account.public_id` 空值与格式不匹配均为 0，Nginx 有效；生产 IP/SNI 的 onechat 与 x HTTPS 均为 200，HTTP 为 301，匿名 `/auth/me` 为 401。onechat/x DNS 在本机与 ECS 都未解析，作为既有 DNS 风险保留；无受权平台账号，真实目录字段展示待人工复核。

- 2.8.16 TASK-245 Semattice 管理端切换 on 2026-07-25:
  - Git/发布：主线合并提交 `ac598745e588`；`scripts/release-acr.sh --dry-run --version 2.8.16` 与正式发布通过，annotated tag `2.8.16` 已推送。
  - 镜像：backend/frontend ACR index digest 分别为 `sha256:1b965955e81130e37f4001ab27bf33299219669f11f310cb0f8f425cafd5fcd8`、`sha256:a179fa0c7376f5849f4d46736e4527d7ec8031328b8d9027ffbc40b06a68f85e`。
  - 备份/部署：`/opt/cici/backups/20260725-092810-before-2.8.16-task245` 的 env、PostgreSQL、KB、Qdrant 均非空；仅 pull/force-recreate backend/frontend，四个状态服务保持运行。
  - 验收：六服务健康，health `UP`，版本 `2.8.16 / ac598745e588`，Nginx 有效；`x.agentcici.com` 与 `agentcici.com` 均为 200，匿名 `/auth/me` 与 `/auth/semattice/console` 均为预期 401。未使用真实管理员凭据，产品切换端到端交互待受权会话复核。

- 2.8.15 TASK-247 平台全量个人用户目录 on 2026-07-24:
  - Git/发布：主线合并提交 `38cb22e3a587`；`scripts/release-acr.sh --dry-run` 与正式发布通过，annotated tag `2.8.15` 已推送。
  - 镜像：backend/frontend ACR index digest 分别为 `sha256:8e4fc950102a0c1173c8e97c545358b28533d5fea0c98a0aca533ee7c1ffd81d`、`sha256:7e0bf4f0ed12ecd644630ead048953a5428395e32da9abdd1ddd73a55c2ff080`。
  - 备份/部署：`/opt/cici/backups/20260724-222041-before-2.8.15-task247` 的 env、PostgreSQL、KB、Qdrant 均非空；仅 pull/force-recreate backend/frontend，四个状态服务保持运行。
  - 验收：六服务健康，health `UP`，版本 `2.8.15 / 38cb22e3a587`，Nginx 有效；`agentcici.com`、`/platform/registered-users` 与 `x.agentcici.com` 均为 200，匿名平台目录接口保持 401。未用平台账号，真实目录内容待受权复核。

- 2.8.14 TASK-246 租户详情路由标识兼容修复 on 2026-07-24:
  - Git/发布：主线合并提交 `6cee975539e4`；`scripts/release-acr.sh --dry-run` 和正式发布通过，annotated tag `2.8.14` 已推送。
  - 镜像：backend/frontend ACR index digest 分别为 `sha256:25e051c4bfb7f6f843bf595fec2163f3fc2c8790630be43474773c0cd7f06a0d`、`sha256:d118476d5b9967ee214336f115a987ca2b7d980fcdb1df28527bfe30ee41964d`。
  - 备份/部署：`/opt/cici/backups/20260724-212057-before-2.8.14-task246` 的 env、PostgreSQL、KB、Qdrant 均非空；仅 pull/force-recreate backend/frontend，四个状态服务保持运行。
  - 验收：六服务 healthy，health `UP`，版本 `2.8.14 / 6cee975539e4`，Nginx 有效；`agentcici.com`、`/platform/tenants` 与 `x.agentcici.com` 为 200。未用平台账号，受保护详情页的真实交互待受权复核。

- 2.8.13 TASK-244 OIDC 规范入口 state 修复 on 2026-07-24:
  - Git/发布：提交 `877337078ea8`，`scripts/release-acr.sh --dry-run --version 2.8.13` 与正式发布通过，annotated tag `2.8.13` 已推送。
  - 镜像：backend/frontend ACR index digest 分别为 `sha256:66e929c6aaee94e2ed13aa09a643f6aef2bb44c3e42c256891091d566f11ff0e`、`sha256:c77614e4c6216fc329962f8c23c971b354caeedf69074f999534a4653c3a6591`。
  - 备份/部署：`/opt/cici/backups/20260724-201945-before-2.8.13-oidc-canonical-entrypoint` 的 env、PostgreSQL、KB、Qdrant 均非空；仅 pull/force-recreate backend/frontend，四个状态服务保持运行。
  - 验收：backend/frontend 及四个状态服务健康，后端版本 `2.8.13 / 877337078ea8`，Nginx 有效，`x` HTTPS 为 200；主站 OIDC start 先 302 到 `x`，规范 `x` start 设置 host-only state Cookie 后跳转 Keycloak。真实用户完整登录待复验。

- 2.8.12 TASK-243 租户应用 Semattice 开通状态修复 on 2026-07-24:
  - Git/发布：提交 `6574f168234e`，`scripts/release-acr.sh --dry-run --version 2.8.12` 与正式发布通过，annotated tag `2.8.12` 已推送。
  - 镜像：backend/frontend ACR index digest 分别为 `sha256:5bd8801e66e93bb8628c2e725f56bb8b1f9d1cda2b98df23dff2dc7fb31e9c4b` 与 `sha256:3126c5115587ef36e9eb82012a014166a8760877695c31b5e9a90c466d31ccea`。
  - 备份/部署：`/opt/cici/backups/20260724-194153-before-2.8.12-semattice-status-fix` 的 env、PostgreSQL、KB、Qdrant 文件均非空；仅重建 backend/frontend，四个状态服务不重启。
  - 验收：后端 health `UP`，版本 `2.8.12 / 6574f168234e`；真实公司 binding 为 `PROVISIONED`，状态接口保持平台认证边界（匿名 `401`），`x.agentcici.com` 首页 `200`。
- 2.8.9 TASK-242 company_id identity unification on 2026-07-24:
  - Git/发布：主线提交 `0194706ffc7b`；新不可变 tag `2.8.9` 已推送。`2.8.7` 因 V60 遗留 `ORG` principal 的 CHECK 顺序失败，V94 事务已完整回滚；`2.8.8` 已成功写入 V94 但暴露旧 profile 的 `organization_size` 字段遗漏。二者均未成为健康交付版本。
  - 修复/验证：V94 先替换 `ck_agent_access_principal_type` 再把遗留 `ORG` 改为 `COMPANY`；V95 将 `company_profile.organization_size` 重命名为 `company_size`。全新 PostgreSQL 的 V1→V93→插入遗留授权→V95 测试、以及完整应用启动/Hibernate schema validation/health 均通过。
  - 镜像：backend/frontend index digest 分别为 `sha256:690eded9507a91c7e7e596266320c51af6e5f8a822d3f8c2c7ca1a733b1d1995` 与 `sha256:e4a83f72ea699668c8f874a1c966793ab62f412f76d5909e79e8ded0feb89e9d`。
  - 备份/部署：复用发布前备份 `/opt/cici/backups/20260724-134723-before-2.8.7-company-id`（env、PostgreSQL、KB、Qdrant 均非空）；只 pull/force-recreate backend/frontend，database、Redis、RabbitMQ、Qdrant 未重启。
  - 运行/公网：生产库 V94 后成功应用 V95；六服务 healthy，health `UP`，版本 `2.8.9 / 0194706ffc7b`。x HTTP 301/HTTPS 200、生产 IP/SNI onechat HTTPS 200、匿名 `/auth/me` 401；90 秒窗口 backend error 0。

- 2.8.5 FEAT-131 通用外部应用智能体记忆平台 on 2026-07-23:
  - Git/发布：主线提交 `02d380d10508beaf67c96993b9df55978d72072f`；`scripts/release-acr.sh --dry-run` 和 `--version 2.8.5` 成功，annotated tag `2.8.5` 已推送。
  - 镜像：backend/frontend index digest 分别为 `sha256:0936e7b4d0e3040cf907284b7edc41dc891b1091b73d247e1be734e6c5870e30` 与 `sha256:abc3417bcb95f42897abe6ba32a00df7244e20aef3892f9e84875a8c776619ce`。
  - 备份/部署：`/opt/cici/backups/20260723-115248-before-2.8.5-feat131-memory` 的 env/PostgreSQL/KB/Qdrant 均非空；backend/frontend 已重建至 2.8.5，database、Redis、RabbitMQ、Qdrant 未重启。
  - 运行/迁移：六服务 healthy，health `UP`，版本 `2.8.5 / 02d380d10508`；生产库从 V84 正向迁移 V85–V90 成功，Nginx 有效。
  - 公网：x HTTP 301/HTTPS 200，显式生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 401；稳定窗口 backend error 0、真实 Nginx 5xx 0。未使用未获授权的生产账号或 API Key 验证受保护记忆管理/OpenAPI 端点。

- 2.8.4 TASK-225 对话技能选择强制执行上下文与可观测性 on 2026-07-22:
  - Git/发布：主线 merge commit `2f2f1a013ec22f7e9cc52314c3707370a0d3978e`；`scripts/release-acr.sh --dry-run` 和正式发布通过，annotated tag `2.8.4` 已推送。
  - 镜像：backend/frontend index digest 分别为 `sha256:a173a2479309636f27f13fa5a0a2907f3b0893165f94a053c45dc19b50028002` 与 `sha256:0d94dc8d08d771a1297d09eb86f9d85834d68611a38b1a867cef7cd9e734e068`。
  - 备份/部署：`/opt/cici/backups/20260722-102713-before-2.8.4-task225-forced-skill-context` 的 env/PostgreSQL/KB/Qdrant 均非空；仅 pull/force-recreate backend/frontend，database、Redis、RabbitMQ、Qdrant 容器 ID 保持不变。
  - 运行/公网：六服务 healthy，health `UP`，版本 `2.8.4 / 2f2f1a013ec2`，Nginx 有效，x HTTP 301/HTTPS 200，显式生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 401；稳定窗口未见 backend ERROR。无授权组织会话，未创建真实业务对话验证所选技能状态。

- 2.7.12 TASK-217 智能体定时任务真实创建与链路事实纠偏 on 2026-07-21:
  - Git/发布：main 提交 `b20261d8b89b8813fbbcc75b541143e0563dc42d`；`scripts/release-acr.sh --dry-run` 通过，annotated tag `2.7.12` 已推送。
  - 镜像：backend index/amd64 `sha256:b2d1e4a053a6edadd6cdcefd481615a89258cd1821e02f3745f74031dd175b23` / `sha256:9b819a1b9949dd98d3db700bd36bacdeeef655be200f42288edb662ae089496b`；frontend index/amd64 `sha256:a3a6ff9734bb3f7da648a2003159289d26b704f6927fd48b06f665b7e205b616` / `sha256:52a0228d143371ac9e6da0570e047d387ac227656af12bdcfbe8cbf644b5ea8b`。
  - 备份/部署：`/opt/cici/backups/20260721-190058-before-2.7.12-task217-runtime-trace` 的 env/PostgreSQL/KB/Qdrant 均非空；只 pull/force-recreate backend/frontend，database、Redis、RabbitMQ、Qdrant 的容器 ID 未变化。
  - 运行/公网：六服务 healthy，health `UP`，版本 `2.7.12 / b20261d8b89b`，Nginx 有效，`x` HTTPS 200、显式生产-IP onechat HTTPS 200；发布窗口 backend/frontend error 和 Nginx 5xx 为 0。本机 onechat DNS 未解析，仍为已知外部解析风险。

- 2.7.11 TASK-215 链路追踪全文查看与复制及本地记录整合 on 2026-07-21:
  - Git/发布：main 合并提交 `281f35b2cb2f81a90ec7a41f72d28ce71eb6a52a`；dry-run 与正式 ACR 构建完成，annotated tag `2.7.11` 已推送。
  - 镜像：backend index `sha256:65bf3b101a9ee915fddf656ea5ebe53bc29bf3d27b01504b2321f77f6fce4290`、amd64 `sha256:dc156302579d7b35730aadc883bf7fdd7491d87d5cf1d079fd3ad1fc78eeb33f`；frontend index `sha256:27c38b70972f9ba1436285ac6eead35fbf3b936facfdf703ca09bba3aa29d902`、amd64 `sha256:8e4ce653bb3c251e73be79a6446f79b0d35aa8a36db2d52a65b0a94c1bb7616f`。
  - 备份：`/opt/cici/backups/20260721-181143-before-2.7.11-main-integration` 包含非空 `acr.env.before-release`、`postgres.dump`、`kb-files.tgz` 和 `qdrant.tgz`。
  - 部署：仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ 和 Qdrant 保持健康且继续运行 `2.6.12`。
  - 运行/公网：六服务 healthy，health `UP`，版本 `2.7.11 / 281f35b2cb2f`，Nginx 有效；x HTTP 301/HTTPS 200，生产-IP-resolved onechat HTTPS 200。生产浏览器确认管理员入口的登录边界且无 console error/warning；没有管理员凭据，Trace 展开/复制的线上交互不作虚假复验。

- 2.7.10 TASK-213 通用本体建模与语义查询平台 V1 on 2026-07-17:
  - Git/发布：PR #13 合并提交 `f922b86f1884ec5f7b7e1d97d3d0558202d0180f`；`scripts/release-acr.sh --dry-run --version 2.7.10 --production` 与正式发布均成功，annotated tag `2.7.10` 已推送。发布后的 assignment/状态与生成配置同步提交不改变生产镜像，生产版本接口仍正确指向发布合并提交。
  - 镜像：backend index `sha256:096f480677944eb8e0f263e562155c771f4e72d0bee6731a82a3b162937c3644`、amd64 `sha256:cdaeb804cd645afe6fa2498b9f06f14c24b6a4b33d4f8d9a8f538e66e79056d5`；frontend index `sha256:0f96d20bdf1727fc8cf6da57c0b49af7f9a8c213a91709fe8183bef7ef66ed3b`、amd64 `sha256:4cfae678067c31d9794fe8e1bf5b8739d6b95dfb3fba5aaec8dd921aa3a7a2df`。
  - 备份：`/opt/cici/backups/20260717-154253-before-2.7.10-task213-ontology`；env 1,646 bytes、PostgreSQL 3,010,000 bytes、KB 511,135 bytes、Qdrant 1,584,517 bytes，Nginx/Compose/状态/校验清单也非空；`pg_restore -l`、tar 列表与 SHA-256 校验通过。
  - 部署：只强制重建 backend/frontend。database `ce48f99872d8255e12c8b8255d5868d838f25a8390a5a454e93f0adc93a90b82`、Redis `3c387959346306ffb6309fdfe2d76cad519f365d6166cbdba521f9726d7ec1d4`、RabbitMQ `246a0aa352dfc8c9e4f348efa83197353d6fc5d6301018d68a7065618260c934`、Qdrant `96bf6c3cad9ca6f3e2d12ed2d3ae592f3476f05b32951296e8df5ba9a7290369` 与发布前完全一致并继续使用 `2.6.12`。
  - 运行/迁移：backend `e02c834c1de6b4ee3987af86f9e02d5ce46c1968c4601171e5565ed661ae0e7f`、frontend `22ad5424c27e2ddac071ff4e57774a283b7f5a38b9de073fb0172dae3f906511`；六服务 healthy，health `UP`，版本 `2.7.10 / f922b86f1884 / 2.7.10`。V82/V83 均 `success=true`，13 张 ontology 表、provenance 列/CHECK/唯一约束正确，Nginx 配置有效。
  - API：`project-delivery` 完成对象/字段发现、15/15 映射验证、候选编译、人工发布与幂等发布；不可变 v1 绑定草稿修订 6。只读语义查询返回 1 个项目、2 个关联任务和版本证据；跨组织返回 404，审计脱敏通过。组织/平台 token 双向隔离以及匿名 401 通过。
  - CloudCC 边界：`customer-operations` 在两个演示组织均以正确 package provenance 安装为草稿，但当前密码登录用户无法取得有效 CloudCC 当前用户会话，两次对象发现按设计返回 `502 DATA_SOURCE_UNAVAILABLE`；没有写回 CloudCC、损坏或发布草稿。
  - 桌面/稳定性：生产 1600×1000 列表、画布、映射、技术契约和版本历史通过，console warning/error 与外层横向溢出均为 0。480 秒/17 次采样中所有容器 ID、健康、restart 0、OOM=false、版本保持不变，backend `ERROR|Exception` 为 0；Nginx 只有上述两次预期 CRM 诊断 502，其他 5xx 为 0，最终语义查询继续通过。
  - 公网/回滚：`x.agentcici.com` HTTP 301 / HTTPS 200；显式生产 IP 的 `onechat` HTTPS 200，本机 DNS 风险不变。即时应用回滚点为健康 `2.7.9 / c04e992b3840`；V82/V83 可安全保留。

- 2.7.8 TASK-212 Skill DAG 只读治理闭环 Phase 1 on 2026-07-16:
  - Git/发布：PR #10 合并提交 `4814d2b9534d8ba70d560b1a8a9b9a3dbe390717`；`scripts/release-acr.sh --dry-run --version 2.7.8` 与正式发布均成功，annotated tag `2.7.8` 已推送。
  - 镜像：backend index `sha256:4bbc96d6857236ade2122d98c038d70f15cb0148c852553f472631af93eca38e`、amd64 `sha256:f15bde1851cb45ee217147e1ce419a5c4d78c2b2390903f578c025c6c88d13b2`；frontend index `sha256:ceff96941ae9402a25cf0a28ec9b7c69a2bb4d4da44c9b6848db2934addc30cf`、amd64 `sha256:1ebecff3346837c879c041d7f9559f5ac9526791d82fb08ea18e5fd47f3ce056`。
  - 备份：`/opt/cici/backups/20260716-011129-before-2.7.8-task212-skill-dag`；env 1,646 bytes、PostgreSQL 3,007,782 bytes、KB 511,135 bytes、Qdrant 1,584,517 bytes，全部非空。
  - 部署：仅 pull 并强制重建 backend/frontend。database `ce48f99872d8`、Redis `3c3879593463`、RabbitMQ `246a0aa352df`、Qdrant `96bf6c3cad9c` 与发布前容器 ID 完全一致并继续使用 `2.6.12`。
  - 运行态：六服务 healthy，health `UP`，版本 `2.7.8 / 4814d2b9534d`；Flyway V81 成功，两条 Skill 影响索引均 `indisvalid=true / indisready=true`，Nginx 配置有效。8 分钟稳定窗口 backend ERROR 0、frontend 精确 5xx 0。
  - API 验收：匿名 Agent DAG 401、组织 token Agent DAG 200、显式 `versionNo=50` 200、平台 token Agent DAG 403、组织 token 平台 DAG 403、平台 token 平台 DAG 200；请求时延约 0.16-0.21 秒。
  - 桌面验收：应用内 Browser 在 `1600 x 1000` 验证 Agent Builder 24 节点 / 32 边与平台 Skill 6 节点 / 9 边，真实 warning、缩放和节点详情均可用；两页无外层横向溢出，console warning/error 为 0。证据：`output/playwright/task212-prod-agent-skill-dag-2.7.8.png`、`output/playwright/task212-prod-platform-skill-dag-2.7.8.png`。
  - 公网/回滚：`x.agentcici.com` HTTP 301 / HTTPS 200；`onechat.agentcici.com` 的既有 DNS 解析风险仍在，显式生产 IP vhost 为 HTTP 301 / HTTPS 200。即时应用回滚点为 `2.7.7 / e47979167af8`；V81 仅新增索引，应用回滚时可安全保留。
  - 质量边界：TASK-212 后端聚焦 9 类 / 22 项、前端 18 文件 / 110 项、生产构建、package、干净库迁移与独立复审通过。完整后端诊断 341 项中的 3 failure / 7 error 属于既有平台身份、审计夹具、非空字段、模型配置与连接池基线，未宣称全量套件通过。

- 2.7.7 TASK-211 CRM 确定性回答真实流式与 OpenAPI 空白保真 on 2026-07-15:
  - Git 合并提交/标签：`e47979167af8` / `2.7.7`；PR #6 复用 18 字/18ms 服务端分片，PR #7 保留每个 OpenAPI delta 的首尾空白、换行与纯空白片段。
  - 镜像：backend index `sha256:315623e0ea90f087cf332acfc5b981efca91d493c814a0b8a2023a7b6433a475`、amd64 `sha256:9c6b10448df2a7f1bda6b37dfdaf09ec2eacc28bd050055afbf6150279af4ddc`；frontend index `sha256:515c760bc654c8e491a8914cf48a37397fe4c3200529b0df972d397e6b3f9f24`、amd64 `sha256:96d176f71a276962ba87be12f788ecf73c3d68009d7a9804077af12fa4a082ab`。
  - 备份：`/opt/cici/backups/20260715-091243-before-2.7.7-task211-openapi-whitespace`；env 1,646 bytes、PostgreSQL 2,925,720 bytes、KB 511,065 bytes、Qdrant 1,584,517 bytes，全部非空。
  - 部署：`2.7.7` 只强制重建 backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 保持不变并继续健康运行在 `2.6.12`。
  - 运行态：health `UP`，版本 `2.7.7 / e47979167af8`，Flyway 当前 V80，Nginx 有效，`x` HTTP 301/HTTPS 200，显式使用生产 IP 的 `onechat` HTTPS 200。最终成功 CRM 窗口为 backend ERROR 0、CRM failure 0、异常断连 0、精确 Nginx 5xx 0。
  - 协议验收：SalesA 5/5 流式调用各产生 133 个 delta，持续约 2.4 秒，最大 18 UTF-16 单元且持久化精确一致；blocking 与 SalesB 仅归一化截止时间后相同。OpenAPI blocking/streaming 均为 2,383 字，streaming 产生 133 个 message、逐字保留空白，并与 history/internal 正文一致。
  - 访问清理：临时 OpenAPI Key 已撤销且返回 401 `agent_api_key_invalid`；没有 ACTIVE Key 残留，原 channels/toolIds/knowledgeBaseIds 精确恢复。用户答案不含内部工具结果、原始 JSON、内部 ID 或凭据材料。
  - 桌面验收：应用内 Browser 使用 fresh SalesA 登录与 fresh CRM 会话，在 composer 仍禁用时捕获同一气泡 50 字 partial，完成后为 2,100 字且 composer 恢复可用；console error/warning 0、外层与工作台各级无横向溢出、Top 5/五层经营分析完整、内部工具结果泄漏 0。TASK-211 全部门禁已关闭。
  - 发布纠偏：`2.7.6 / 2055947aae07` 证明内部 SSE 分片有效，但丢失 41 个 OpenAPI 空白字符，随后立即回滚到 `2.7.5`。该版本仅保留为不可变失败验收证据，不是回滚目标；应用回滚目标仍为 `2.7.5 / be80eea665c0`。

- 2.7.5 TASK-208 CRM 产品销售经营分析稳定性与深度治理 on 2026-07-15:
  - Git merge commit/tag `be80eea665c0` / `2.7.5`; release line contains TASK-209 `2.7.2`, TASK-208 `2.7.3` and TASK-210 `2.7.4` as ancestors.
  - Images: backend index `sha256:0a79c77e5c9db8f4db00a7dc310264815de461c4caf9172d29cca062b29c1b1e`, amd64 `sha256:c99ec42f67abd451de6d2e6d371166b28850bfded128f687ccfd2d7c95ecd132`; frontend index `sha256:056e4fd4a064134f3bacce6827a3dbd3206ef6a442d93b50c104e05dbc6c86f4`, amd64 `sha256:cd7477395e25d58cca96b2d08f86a7a30c579cb927ab98e94c918d9f34ec69c7`.
  - Backup: `/opt/cici/backups/20260715-005545-before-2.7.5-task208-crm-analysis`; env 1,646 bytes, PostgreSQL 2,862,193 bytes, KB 510,994 bytes and Qdrant 1,584,517 bytes, all non-empty.
  - Deploy: only backend/frontend were force-recreated on `2.7.5`; database, Redis, RabbitMQ and Qdrant container IDs stayed unchanged and remain healthy on `2.6.12`.
  - Runtime: health `UP`, version `2.7.5 / be80eea665c0`, Nginx valid, `x` HTTP 301/HTTPS 200, and production-IP-resolved `onechat` OpenAPI blocking/streaming passed. Final clean window reports backend error 0, precise frontend 5xx 0 and CRM analysis error 0.
  - CRM: the governed TASK-205 batch completed 316 update-only writes with 316 owner changes and 88 Account relinks; post-write dry-run reports 0 pending updates, 0 creates and 0 duplicates. No metadata, role, profile or sharing rule was changed.
  - Acceptance: SalesA five fresh SSE sessions plus persistence, blocking, OpenAPI, desktop UI and SalesB comparison returned the expected Top 5 without raw JSON or internal tool names. Temporary OpenAPI key was revoked and the original agent channels were restored after validation.

- 2.7.4 TASK-210 客户互动工作台标准渠道图标 on 2026-07-15:
  - Git commit/tag `3206fdbc196f` / `2.7.4`; 时间线使用公开维护的微信品牌图标与独立 Lucide 业务来源图标，并修复重复 CRM event id 导致的 React key 冲突。
  - Images: backend index `sha256:c41eb2d6387e9ccca8c48b6fa0c3f5ddfb26a3b74442224badab5a97dd94bba5`, amd64 `sha256:9fc687f2d33b4645e19196016f097939045a21ba06ce68115b7f52e421b78a7d`; frontend index `sha256:ce61cab1d05b84469254d25c010b16b7d38f8fb8ea05a9ac44b00f907b25e272`, amd64 `sha256:0a1fe7ecfb05c82ee8b31a780f0ab6776a2367e1bea8ba90513884325f23d2f9`.
  - Backup: `/opt/cici/backups/20260715-003936-before-2.7.4-task210-standard-icons`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Runtime: backend/frontend healthy on `2.7.4`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.6.12`. Health `UP`, version `2.7.4 / 3206fdbc196f`.
  - Release boundary: this production line preserves TASK-209 but intentionally excludes TASK-208. TASK-208 final release must use `2.7.4` as a parent and publish a new immutable tag; do not deploy or rebuild `2.7.3`.

- 2.7.2 TASK-209 运营平台登录页原图像素锁定 on 2026-07-15:
  - Git commit/tag `ddcda0ef6111` / `2.7.2`; `/platform/login` 使用批准的 1672×941 原图资产作为默认态整页背景，并以透明语义交互层保留真实认证行为。
  - Images: backend index `sha256:f4ec61fc0532be5593a4cc6c3646906d026770ee56e55b5aebdea936c1d29979`, amd64 `sha256:3403aad868f7f06d08c6b6ac685fafd8b4f39ef3a0f5ab36dcfe35deac8e562f`; frontend index `sha256:2ae803bf615cbb84bf7ddf451716b0f94df452c2d94e6936e01eacf59a18e918`, amd64 `sha256:21ef8d647026f1ffb361c82cfb3230770da8b8cf1098fa314e4cef5cd9538eda`.
  - Backup: `/opt/cici/backups/20260715-001809-before-2.7.2-task209-reference-login`.
  - Runtime: backend/frontend healthy on `2.7.2`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.6.12`. Health `UP`, version `2.7.2 / ddcda0ef6111`.
  - Acceptance: production 1672×941 browser confirmed the approved background, transparent default overlays, usable post-input controls, zero horizontal overflow and zero console error/warning.
  - Superseded by production `2.7.4`; `2.7.3 / 85b92c2d1f63` images and tag exist but were intentionally never deployed.

- 2.6.11 TASK-206 CloudCC 当前会话嵌入 SSO 修复 on 2026-07-14:
  - Git commit/tag `c540988655cb` / `2.6.11`; pagecomponent uses `$CCDK.CCToken.getToken()` and backend validates the CRM session through `/api/user/getUserInfo` while retaining strict three-way identity matching.
  - Images: backend index `sha256:9be1120bc9a26e507068d75fbd5c9eb6db0e61ef24dc3785be9e9f8330bb5f4b`, amd64 `sha256:3694fa2545aeb136c234e9cc2ab7df64f684720f21b2ea25c424ed120eb82e69`; frontend index `sha256:ba57516fe20e08574f6b029e75f191cfb812caae29f8029454d1d981439822c5`, amd64 `sha256:4752c464acca6c864afda592e6769345173b4497ce9f0634a7f0e62168ba1079`.
  - Backup: `/opt/cici/backups/20260714-202718-before-2.6.11-task206-cloudcc-session-sso`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Runtime: backend/frontend healthy on `2.6.11`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.3.4`. Health `UP`, version `2.6.11 / c540988655cb`, Nginx valid and public routes return 200.
  - Acceptance: real CRM initial load plus two refreshes remained connected with customer data; Nginx recorded three HTTP 200 ticket requests and three HTTP 200 consume requests, with no backend session validation rejection.

- 2.6.8 TASK-205 CRM 经营分析确定性路由与高仿真数据 on 2026-07-14:
  - Git commit/tag `095094300a25` / `2.6.8`; platform-standard `crm-business-analysis` is pinned in `cici-system` published version 3, and product sales ranking intent is forced through `crm_product_sales_rank` before final language generation.
  - Images: backend index `sha256:27c985366695339a298ad3f6a333cd03827fc08fc334f9f1161242f584b7f2aa`, amd64 `sha256:ea08a7a86b8c64aa565ceef1ce768b0af367550e081a3ad6781d078b23811265`; frontend index `sha256:784504e1a57a5463d722a74941b0a15085ebf04bf2be08cef276cdb8eadfca0c`, amd64 `sha256:277a476b3cf0c1b495ab8202f3380674af0119794e7898172ce4dcda2964ed4f`.
  - Backup: `/opt/cici/backups/20260714-184006-before-2.6.8-task205-deterministic-routing`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Runtime: backend/frontend healthy on `2.6.8`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.3.4`. Health `UP`, version `2.6.8 / 095094300a25`, V80 unchanged, Nginx valid, post-acceptance backend errors and precise Nginx 5xx both zero.
  - Acceptance: five fresh production chats returned the same last-30-day quantity Top 5 (`X1 130`, `G5 110`, `S2 95`, `MP 75`, `PA 65`); backend logs contain exactly five skill-scoped `crm_product_sales_rank` calls and no atomic CRM discovery calls.
  - Release correction: `2.6.7 / a20be3195dcb` exposed that the pre-existing published agent snapshot did not include the new Skill and was immediately superseded. It is not a rollback target; use `2.6.6` if rollback is required.

- 2.6.6 TASK-202 主题视觉层级修复 on 2026-07-14:
  - Git commit/tag `4caaa4800b3d` / `2.6.6`; theme switching remains account-scoped while structural wrappers stay transparent and agent avatars keep fixed geometry across all eight themes.
  - Images: backend index `sha256:040c77eb89d4ee06b4e7ac615fa1e9bb44a4aecaf3f34a9453aa323c6351b20c`, amd64 `sha256:a57d540cab963a8c108b40471ef0a7cb025dc95aa8cdcc2f06db327ed0caa399`; frontend index `sha256:b8bed46b93bbcba24e9ad3e5face8ede291cb013a28f28de323579c1c6857982`, amd64 `sha256:efb42859509f6ebfe2bf58daa93d2af9bf8aa7ad25568e7915816b347892638d`.
  - Backup: `/opt/cici/backups/20260714-142848-before-2.6.6-task202-theme-visual-hotfix` contains non-empty env, PostgreSQL, KB and Qdrant artifacts.
  - Runtime: backend/frontend healthy on `2.6.6`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.3.4`. Health `UP`, version `2.6.6 / 4caaa4800b3d`, V80 successful, Nginx valid, stable backend errors and frontend 5xx both zero.
  - Public/browser: `x.agentcici.com` HTTP redirects 301 and HTTPS app returns 200; explicit production-IP smoke for `onechat.agentcici.com` returns 200. Authenticated 2048×1152 browser acceptance confirmed transparent structural layers, fixed 42×42 avatars without hover shadow/scale, zero outer overflow and zero console errors.

- 2.6.4 TASK-200 多租户智能体评测控制面 on 2026-07-14:
  - Git commit/tag `d88f4293759f` / `2.6.4`; V79 adds platform core, standard application, industry and tenant-private evaluation assets, deterministic multi-assertion runs, snapshots/comparison/staleness, release gates, Trace regression capture and quality issues.
  - Images: backend index `sha256:58983c43796896d05dc4a07059dedf1d10d26cdb6413567e7056e771a77b0388`, amd64 `sha256:fe378b7652eb52a3c2b58e3d43dfc68c00bbe16d3fa44d4011eea3aec0e5c846`; frontend index `sha256:0ffa36646860570eabe0f21cfe28514d2450608a11e981f04184971689fd2f90`, amd64 `sha256:187b2b7c3a13b518cea186187cc8e7e2a09dd7fc24a8b6b9b71cef4d54f33582`.
  - Backup: `/opt/cici/backups/20260714-075215-before-2.6.4-task200-nginx-hotfix` includes non-empty env, both Nginx configs, PostgreSQL, KB and Qdrant artifacts; initial pre-migration backup is `/opt/cici/backups/20260714-074613-before-2.6.3-task200-agent-evaluation`.
  - Runtime: backend/frontend healthy on `2.6.4`; database, Redis, RabbitMQ and Qdrant remain healthy on `2.3.4`. Health `UP`, version `2.6.4 / d88f4293759f`, V79 successful, Nginx valid, stable backend errors and frontend 5xx both zero.
  - Acceptance: tenant and platform evaluation APIs return JSON with role isolation in both directions; platform has one draft standard suite. Tenant AI quality, Builder evaluation/publish-channel separation and platform intelligent-agent quality passed at 1280x720 with no horizontal overflow or console error/warning.
  - Release correction: `2.6.3 / ca12a9ed804f` briefly applied V79 but exposed missing production Nginx proxy routes for evaluation APIs. It was immediately superseded by `2.6.4` and is not a rollback target; use `2.6.2` for rollback if required.
  - Public: `x.agentcici.com` HTTP redirects 301 and HTTPS root/new routes return 200; explicit production-IP smoke for `onechat.agentcici.com` returns 200 while the existing local DNS resolution gap remains.

- 2.6.2 TASK-199 互动驱动的客户经营动作 on 2026-07-12:
  - Git commit/tag `b87bbe43dd0d` / `2.6.2`; V78 adds source event/batch, action key, trigger type and validity to customer recommendations. Fixed first-open generation is removed in favor of evidence-backed candidates from confirmed interactions.
  - Images: backend index `sha256:e0f275c02d910b392c708cf8940da9ca30fe1eabc2b19e2469fb42259638ae60`, amd64 `sha256:b3d7e8a91be39e1e81402de72b333b652a210f1f21dc1260a64d454758b9cac7`; frontend index `sha256:73f5b0b427d1707ee8d4de5a6819169b0df755408a0747d7387ed8917731dc12`, amd64 `sha256:928eb9ce5665ac8a4740d0b010e6be11601d12f89f706eda20f24cc1616dcdca`.
  - Backup: `/opt/cici/backups/20260712-232657-before-2.6.2-task199-interaction-actions`; env, PostgreSQL, KB files and Qdrant archives are non-empty.
  - Runtime: backend/frontend healthy on `2.6.2`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.6.2 / b87bbe43dd0d`, V78 successful, Nginx valid and public root/workbench HTTP 200.
  - Real acceptance: batch `cib_554a1a6cc47e44d0afde91e1bbbd638e` generated event `cwi_f39777961d5df638a255caf7edd9308ffed0ed5c` and one pending action keyed `expansion:mobile-inspection`; evidence and validity rendered correctly, repeat confirmation was idempotent, and no CRM write was executed. Screenshot: `output/playwright/task199-prod-interaction-driven-action-2.6.2.png`.

- 2.6.1 TASK-198 historical scoring evidence backfill on 2026-07-12:
  - Git commit/tag `ae6643c109a8` / `2.6.1`; confirmed archives missing the new scoring contract are lazily and idempotently converted to 60%-confidence pending evidence, while new-contract signals keep their original values.
  - Images: backend index `sha256:36efd141a73d5650810e9f3d25c742385f26012b112a5845a811aa758399ec84`, amd64 `sha256:ec3e493629e10e8a92f998c722a0abea084029d0d3dd24d60921a8c53bcb1398`; frontend index `sha256:f88f747357c8126d9bd403dd208437f940145d39205112358d57a42ab3492ab1`, amd64 `sha256:20099ee54c2a3a940ffb02e0b9c08018fe8934cb3eb2a9d331e50a7523ff0292`.
  - Backup: `/opt/cici/backups/20260712-195131-before-2.6.1-task198-history-backfill`; env, PostgreSQL, KB files and Qdrant archives are non-empty.
  - Backend/frontend run `2.6.1`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.6.1 / ae6643c109a8`, Nginx valid and public workbench HTTP 200.
  - Real demo and large-organization archives generated 2 and 8 pending signals respectively; both snapshots stayed at 50 with zero active signals. Repeated reads were idempotent, and the post-warmup backend scoring-error/Nginx-5xx scans were empty.

- 2.5.12 TASK-198 AI 动态客户信号与可解释评分 on 2026-07-12:
  - Git commit/tag `4adbd3bf2d3a` / `2.5.12`; V77 adds auditable AI evidence signals and versioned score snapshots, with confidence gating, lifecycle replacement, 90-day decay and five-dimensional scoring.
  - Images: backend index `sha256:58efb89a6c48505d8e94d797724a2207bab7f6acdeb5df21e8e9b1b74d705086`, amd64 `sha256:68ae75f21b77bd63e7e4ea6edc4b1d83ffd792f147018b568546c36175c1bafc`; frontend index `sha256:9fd8215c87319cf0b1b2259b7f0b99351cf993673fa174b603604b48ef70b53b`, amd64 `sha256:77d138450accd03c99314b5cb8459aabc003e6798167ca68b72d8db989228585`.
  - Backup: `/opt/cici/backups/20260712-192621-before-2.5.12-task198-dynamic-scoring`; four artifacts are non-empty.
  - Backend/frontend run `2.5.12`; four state services remain healthy on `2.3.4`. Health `UP`; V77 applied successfully; Nginx valid; public workbench HTTP 200.
  - AgentCiCi and real CloudCC embed both verified connected CRM data, the same dynamic neutral baseline and the score explanation drawer with zero outer overflow and zero browser errors/warnings. Screenshot: `output/playwright/task198-prod-cloudcc-score-drawer-2.5.12.png`.

- 2.5.11 TASK-197 客户互动档案、动态记忆与按需检索 on 2026-07-12:
  - Git commit/tag `d0ed7e4129cf` / `2.5.11`; V76 archives confirmed interaction batches, adds typed ACTIVE customer memory and bounds assistant context to recent/relevant evidence.
  - Images: backend index `sha256:d4ba55523711a534ce7ef37c676d8eb8505c27a6497b1a4363f675f59d0aeec9`, amd64 `sha256:a8793ab297a0a74cbde806ad29f739673a802ec069027d34355b8702d0b6fecb`; frontend index `sha256:9e154f5c605ccfbb999297f9e9f3a1935af86893781bfe15947069a4c78e2a89`, amd64 `sha256:a03f195ed0397a0484567b8fe7f403632c1d0e5a1e6b38224f87bb6ab373ca32`.
  - Final backup: `/opt/cici/backups/20260712-143215-before-release`; env, PostgreSQL, KB files and Qdrant archives are non-empty. Initial archive release backup is `/opt/cici/backups/20260712-141943-before-release`.
  - Backend/frontend run `2.5.11`; four state services remain healthy on `2.3.4`. Health `UP`; V76 applied successfully; backend startup has no migration error.
  - Production API verified archive detail, 10 ACTIVE memories and bounded assistant context (`recent=10`, `memory=8`, `evidence=7`, `history=false`). Production browser verified all three archive tabs and original-file preview with zero console errors/warnings. Screenshot: `output/playwright/task197-prod-archive-final-2.5.11.png`.

- 2.5.9 TASK-196 客户互动整理上下文稳定性 on 2026-07-12:
  - Git commit/tag `6c7e27181fbb` / `2.5.9`; interaction confirmation uses ordinary detail/queue reads, preserves selected customers outside the current result page, freezes modal Account context and constrains assistant mode navigation to explicit commands.
  - Images: backend index `sha256:e72350e9b5a92c811649f260791c63bd2120a11a25455b672c60648303716b7f`, amd64 `sha256:d83a6892a1d46cc8aafa130ccb8831f9eead29ad8d5abd7251cad171a051addd`; frontend index `sha256:5bb6554e4202e88fadec1eb7f0870bcf1766933da076eb1851706c0632bac45a`, amd64 `sha256:ee3e34ba4eec966e3080e6dfe225d313b0bd59f42e5f0d9b8bac6491a147521d`.
  - Backup: `/opt/cici/backups/20260712-124820-before-2.5.9-task196-context-stability`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend run `2.5.9`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.5.9 / 6c7e27181fbb`, Nginx valid and public workbench HTTP 200.
  - Real large-organization interaction confirmation retained the “奔驰” query, 4 search results and selected Mercedes-Benz customer across a 35-second poll; no `refresh=true`, browser error, backend target error or Nginx 5xx. Screenshot: `output/playwright/task196-prod-customer-context-stable-2.5.9.png`.

- 2.5.8 TASK-195 客户互动时间线完整年份显示 on 2026-07-12:
  - Git commit/tag `a016c165fd95` / `2.5.8`; compact and full customer timelines render `YYYY-MM-DD` and `HH:mm` on two lines, preserve invalid-source fallback, and keep the expanded date column, event icon and vertical axis aligned.
  - Images: backend index `sha256:fa59e23ec070d06708c07324895333fd33be60b2b94035152c25a728cacdd21b`, amd64 `sha256:93a6bd67479c9d51f96f7b7f2c53732bd11c89fce7ba1627b454c2f66c8ab6d5`; frontend index `sha256:580f5167a4c3cfe71488eb51f81478a5efa10dae7a1d370d1861849755440bc6`, amd64 `sha256:07246081a4c74f7daed5d4f2e0867474523de39dc11148f15e0de2fddab2ebe5`.
  - Backup: `/opt/cici/backups/20260712-120506-before-2.5.8-task195-no-wrap`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend run `2.5.8`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.5.8 / a016c165fd95`, public workbench HTTP 200, post-release target-error and Nginx 5xx scans are empty.
  - Real 22-event timeline showed both 2026 and 2023 labels, no internal date wrapping, zero-pixel icon/axis delta and no document overflow. Screenshot: `output/playwright/task195-prod-timeline-full-year-2.5.8.png`.
  - Release `2.5.7` was an intermediate validation build whose date labels could wrap at hyphens; it is superseded by `2.5.8` and is not a rollback target.

- 2.5.6 TASK-194 全量客户名称搜索与输入焦点治理 on 2026-07-12:
  - Git commit/tag `12c766bed77d` / `2.5.6`; CloudCC Account 名称搜索覆盖当前用户全部可见客户，不再受工作台模式、筛选和 10,000 条投影缓存限制；缓存外客户按需加载关联详情，页面按客户真实分类自动对齐新客户推进或老客户经营。
  - Images: backend index `sha256:bfa4ad2932c037000716213cc6df224483d863cce4a0332252fea5de77cfd59b`, amd64 `sha256:8c6918de52589c95bdac2cc7c83d9138484276454278a98a3693407bc2cd645d`; frontend index `sha256:661037ba5a6d1a7543122871f713b360e4e3ad9f3fa1311878d2598b498e56b6`, amd64 `sha256:e063dbc5fdd2adbe8d37ea271c2c2a0f855bdc3c353449ac0ee630ef7339f7ad`.
  - Backup: `/opt/cici/backups/20260712-112702-before-2.5.6-task194-global-search`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend run `2.5.6`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.5.6 / 12c766bed77d`, Nginx valid, public root/workbench HTTP 200, post-release backend target-error and Nginx 5xx scans are empty.
  - Real large-organization search returned the exact cache-external Account in 0.76 seconds and detail in 0.22 seconds; browser switched from new-customer entry to existing-customer operations and verified the shared single-layer field focus rule. Screenshot: `output/playwright/task194-prod-global-search-existing-mode-2.5.6.png`.
  - Releases `2.5.4` and `2.5.5` were intermediate TASK-194 validation builds superseded by mode-alignment release `2.5.6` and are not rollback targets.

- 2.5.3 TASK-193 客户队列最近互动倒序 on 2026-07-12:
  - Git commit/tag `c7af96a48092` / `2.5.3`; 新客户推进和老客户经营默认统一为 `interaction desc`，暂无互动时间的客户置后，相同时间按 account ID 稳定排序。
  - Images: backend index `sha256:2be33ef3be924aed10865cd273d44db4dbb3d2e71a0948fb46ec908a6971eb11`, amd64 `sha256:c4204bacb832bb39708cf6552d97bea9388d9ce820722c90e8486ed8ece13c47`; frontend index `sha256:c128d28bcd58917714d8bf8e8911bd2d566bb91cc48258b140a1c321eb9e8758`, amd64 `sha256:884a0c69b710878d1e6efab3fc96edeb7e770ddc0552a8dac02fa9f6750d4e21`.
  - Backup: `/opt/cici/backups/20260712-104207-before-2.5.3-task193-recent-order`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend run `2.5.3`; four state services remain healthy on `2.3.4`. Health `UP`, version `2.5.3 / c7af96a48092`, Nginx valid, workbench HTTP 200.
  - Real large organization default queries returned both new/existing first pages in descending timestamps with empty values last; post-release backend error and Nginx 5xx scans are empty.

- 2.5.2 TASK-192 大数据量 CRM 组织异步初始化 on 2026-07-12:
  - Git commit/tag `1c2084b5746c` / `2.5.2`; CRM 投影改为按组织成员隔离的后台单飞、10 分钟缓存和 stale-while-revalidate，首页不再同步等待大数据读取。
  - Images: backend index `sha256:287f46e2e748bee6b49db68d1001a6136770ec8adc8ae6508a002c73a9426aea`, amd64 `sha256:17e95d7e40469339195537786117f5903834a50941c08bad51841fa35aa7121a`; frontend index `sha256:ae284daf247695759e7e1961dd74db2aa3ecd8d1274cedcb28175aa8aae46b25`, amd64 `sha256:1784be25fd2e510f445b24fc47f07b46ea03e11fb54be9d7df5cbe35d67bcc16`.
  - Backup: `/opt/cici/backups/20260712-093803-before-2.5.2-task192-sync-state`; backend/frontend running `2.5.2`, four stateful services remain healthy on `2.3.4`; health `UP`, version `2.5.2 / 1c2084b5746c`, Nginx valid.
  - Real large organization cold-cache startup: four HTTPS requests returned HTTP 200 in 0.996-1.013 seconds with consistent `SYNCING`; background projection completed in 46.21 seconds with 10,000 accounts; READY queue returned in 0.68 seconds.
  - Post-release Nginx/backend scans contain no 504, upstream timeout or target exception. Release `2.5.1` was superseded by the sync-state consistency fix and is not a rollback target.

- 2.4.12 TASK-191 CloudCC 嵌入重复刷新与信号原子写入 on 2026-07-12:
  - Git commit/tag `4d00d417dcf3` / `2.4.12`; pagecomponent V11 handles delayed/reused host nodes, and customer signal UPSERT has a repository-level transaction.
  - Images: backend index `sha256:b60f4bead39d06831a846c3efbcf3368aba21e0b23d80fb3f6a7020cceede51c`, amd64 `sha256:f8c0bdd0a2bbf9fa801ef3e8b8947ae6cdca63ce10b0535b4eb111131524fabb`; frontend index `sha256:ba02632b8b61f812ca9b2244b89f319f0b6b4e9e3986af7a32016be8f089649e`, amd64 `sha256:3c06f289c786eeb7ccf8620e3321ea5f7f923d121af3f0acd6366b0fe2a94e3e`.
  - Backup: `/opt/cici/backups/20260712-001641-before-2.4.12-task191-transaction`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend recreated on `2.4.12`; state services remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.12 / 4d00d417dcf3`; Nginx valid.
  - Real CloudCC page passed three consecutive refreshes with CRM data and assistant history present; post-release target error scan is empty. Screenshot: `output/playwright/task191-prod-cloudcc-refresh-stable.png`.
  - Releases `2.4.10` and `2.4.11` were superseded during TASK-191 verification and are not rollback targets.

- 2.4.9 TASK-190 CloudCC 嵌入端会话失效自动恢复 on 2026-07-11:
  - Git commit/tag `052bf118fc1e` / `2.4.9`; added per-user single-flight Token acquisition, conditional rejected-token invalidation, HTTP-200 authentication-failure refresh/retry and explicit CloudCC gateway errors.
  - Images: backend index `sha256:1c525991db9a36db2fa01cef89b986ae0d3445c7a6a60fe56b6494acde68d2c6`, amd64 `sha256:cd32e1b44d57b2e100075b3e5a63125f924d78cda8047211e78505e61ab8f558`; frontend index `sha256:3a4cb09facf34ac7364a7524e622794345375b3774bf2c1a7b25211bb8812865`, amd64 `sha256:6a3d841ac61ae7b60154e4dd8b308fa2c4ea7e2904897c2c0445c8b7ffe239cc`.
  - Backup: `/opt/cici/backups/20260711-224930-before-2.4.9-task190-cloudcc-session`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend recreated on `2.4.9`; state services remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.9 / 052bf118fc1e`; Nginx valid; public routes `200`.
  - Production authenticated API: six concurrent startup requests returned 200; `CCAdmin` integration returned `CONNECTED`, visible accounts `110`; existing-customer queue returned `48` total and `12` first-page rows; post-smoke errors were empty.

- 2.4.8 TASK-189 客户互动多模态采集与确认归集 on 2026-07-11:
  - Git commit/tag `530ba01263b9` / `2.4.8`; added immutable multimodal batches, secured original assets, ASR/OCR/document extraction, structured AI analysis, recovery scheduling, human confirmation and the two-column capture/review UI.
  - Images: backend index `sha256:19f76c827aa8270839b7892e65ca8b4634237205e68a9957a85f6f21ae28b003`, amd64 `sha256:5614e2bedb9eb9a17bfb6922249125b5e865df6abd3b55a9c60031e685996b31`; frontend index `sha256:afb2db2b5962d574e808e33ce40ca8dd2fdaa36d3ddea66eef59cdc370fb7e37`, amd64 `sha256:2512dc750f0d35984d902470115f5531509aee85e7c48e404dc22b3460407f96`.
  - Backup: `/opt/cici/backups/20260711-161034-before-2.4.8-task189-multimodal-interaction`; env, PostgreSQL, KB and Qdrant artifacts are non-empty.
  - Backend/frontend recreated on `2.4.8`; state services remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.8 / 530ba01263b9`; V75 successful; Nginx valid; public routes `200`; stable error scan empty.
  - Production API: a real screenshot batch reached `READY`, authenticated original read returned `200`, confirmation appeared in the CRM-backed timeline, and repeated confirmation reused the same event.
  - Production browser: AgentCiCi and real CloudCC iframe opened the multimodal work area with no outer overflow or console errors. Screenshots: `output/playwright/task189-prod-platform-multimodal-2.4.8.png`, `output/playwright/task189-prod-cloudcc-multimodal-2.4.8.png`.

- 2.4.7 TASK-188 客户互动工作台标题与静态链接控件修复 on 2026-07-11:
  - Git commit/tag `14f8bbd4fdaa` / `2.4.7`. Added the application-level workbench title and removed all pointer-hover visual/geometric changes from the copy-link control.
  - Images: backend index `sha256:f2dc193e5d1af7c24ab339cec14b541f49aa6d15fcbfe528062bb0d2eb554aaf`; frontend index `sha256:b37c562b6962db8c5e10a26c3046be0b6eb33d2d6bbb005aabab069bec5bce4b`.
  - Backup: `/opt/cici/backups/20260711-140704-before-2.4.7-task188-title-static-link`; env `1646`, PostgreSQL `2594115`, KB `196338`, Qdrant `1574403` bytes.
  - Backend/frontend recreated on `2.4.7`; database, Redis, RabbitMQ and Qdrant remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.7 / 14f8bbd4fdaa`; Nginx valid; public routes `200`; stable logs clean.
  - Production browser: platform/embed title passed, copy-link default and hover styles were identical, copy success notice appeared, no outer overflow or console errors. Screenshot: `output/playwright/task188-prod-title-static-link.png`.

- 2.4.6 TASK-187 AI 应用壳层导航稳定性治理 on 2026-07-11:
  - Git commit/tag `f7f0e829b9cd` / `2.4.6`. Removed AI canvas shell padding, stabilized all primary rail states, decoupled app-menu opening from workspace switching, and standardized close/filter icons.
  - Images: backend index `sha256:b89ba70ab17a6c2c3ed278b6a7e128c07e5caf8607f7eceefa63571f88f205b4`; frontend index `sha256:f6e3ec216d54f32acb561cf98fa6bf918fad1311459edaa54a80b00bce238c92`.
  - Backup: `/opt/cici/backups/20260711-134559-before-2.4.6-task187-ai-app-shell`; env `1646`, PostgreSQL `2593822`, KB `196338`, Qdrant `1574403` bytes.
  - Backend/frontend recreated on `2.4.6`; database, Redis, RabbitMQ and Qdrant remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.6 / f7f0e829b9cd`; Nginx valid; public routes `200`.
  - Production browser: rail/app gap `0`, no rail hover geometry change, menu-only primary AI Apps behavior, concrete-app switching, frameless close and centered Lucide filters passed with no outer overflow or console errors. Screenshots: `output/playwright/task187-prod-shell-stable.png`, `output/playwright/task187-prod-ai-app-menu.png`.

- 2.4.5 TASK-186 产品控件去框化与客户互动工作台全页治理 on 2026-07-11:
  - Git commit/tag `b615cf417601` / `2.4.5`. Added shared frameless icon and mode-switch primitives, removed duplicate mode-switch frame, and blocked legacy raised-button chrome across the workbench.
  - Images: backend index `sha256:cfb559ea1d557485c0886661fb4a51bc8f5b2169407705f3eb35ea6df4b798d2`; frontend index `sha256:6b255faa24fa261439c0e574e3b75dfa2f8cea15f38052d23814bd577d3ea14f`.
  - Backup: `/opt/cici/backups/20260711-132342-before-2.4.5-task186-control-chrome`; env `1646`, PostgreSQL `2593585`, KB `196338`, Qdrant `1574403` bytes.
  - Backend/frontend recreated on `2.4.5`; database, Redis, RabbitMQ and Qdrant remained healthy on `2.3.4`. Six services healthy; health `UP`; version `2.4.5 / b615cf417601`; Nginx valid; public routes `200`; final 60-second logs clean.
  - AgentCiCi and real CloudCC iframe computed-style audits both returned zero button shadow/transform offenders and no outer overflow. Screenshots: `output/playwright/task186-prod-agent-control-chrome.png`, `output/playwright/task186-prod-cloudcc-control-chrome.png`.

- 2.4.4 TASK-185 客户互动工作台 AI 助理展开模式 on 2026-07-11:
  - Git commit/tag `f69d2191ed3b` / `2.4.4`. Removed Pin behavior; added standard panel expand/restore, equal-width track transfer, hidden/inert queue, stable center and reduced-motion handling.
  - Images: backend index `sha256:d0bc2a926d718b6de5ae267439c7f7877b03121e92c6b6b1ce97f6826cf6c136`; frontend index `sha256:43a1bcb9610029399690362861a90830662f71fda20b825f0b615a98e82d6d3c`.
  - Backup: `/opt/cici/backups/20260711-122835-before-2.4.4-task185-assistant-expand`; env `1646`, PostgreSQL `2593348`, KB `196338`, Qdrant `1574403` bytes.
  - Six services healthy; health `UP`; version `2.4.4 / f69d2191ed3b`; Nginx valid; public routes `200`; post-warmup logs empty; injection verification `issues=[]`.
  - Real CloudCC iframe: default queue/main/assistant `327/1213/327`, expanded `hidden/1213/653`, restored exactly. Screenshot `output/playwright/task185-prod-cloudcc-expanded.png`.

- 2.4.3 TASK-184 客户互动工作台左侧队列横向裁切热修 on 2026-07-11:
  - Git commit/tag: `3b18b8591e2c` / `2.4.3`. Search/settings use border-box, queue children are width-contained, and four filters use an adaptive grid with compact padding.
  - Images: backend index `sha256:a1e4a470d4e39df5dac3f5c7504849c679ebb28340045ac69401fb6c7fffb4d8`; frontend index `sha256:f569239f16997c19d090f8cd189d5dd097b83cd274c9235ab1004c266c31e34e`.
  - Backup: `/opt/cici/backups/20260711-121242-before-2.4.3-task184-queue`; env `1646`, PostgreSQL `2593100`, KB `196338`, Qdrant `1574403` bytes.
  - Six services healthy; health `UP`; version `2.4.3 / 3b18b8591e2c`; Nginx valid; public workbench/embed routes `200`; post-warmup errors empty.
  - Real CloudCC iframe measured queue `335/335`, filters `315/315`, accounts `315/315`; no filter label overflow. Skill CLI injection verification returned `issues=[]`.

- 2.4.2 TASK-183 客户互动工作台界面规范化与流式助理 on 2026-07-11:
  - Git commit: `49402ae8f3a0` on `main`; annotated tag `2.4.2` pushed. Scope: standard Lucide icons, single inline queue settings, explicit read-only demo status, removal of nonfunctional entries, SSE phases/deltas, safe Markdown and automatic latest-message following.
  - Images: backend index `sha256:7bf22552e8aaac27b65c627f87bf1acb863b6f5b87d2f726e76f870d47346f62`; frontend index `sha256:3af8fc2b046c91bca9055de48ccb6163a7f735f590bdffaceb3c6408dca0b0ea`.
  - Backup: `/opt/cici/backups/20260711-114126-before-2.4.2-task183-streaming`; env `1646`, PostgreSQL `2581668`, KB `196338`, Qdrant `1574403` bytes.
  - Deploy: backend/frontend recreated on `2.4.2`; stateful services remained healthy on `2.3.4` with local `2.4.2` aliases. Six services healthy; health `UP`; version `2.4.2 / 49402ae8f3a0`; Nginx valid; V72-V74 successful; post-warmup error scan empty.
  - Public and browser: x.agentcici.com root/workbench/embed routes `200` and HTTP redirect `301`; production SSE emitted 40 deltas without error. AgentCiCi and real CloudCC `CCAdmin` iframe both showed a processing state within 60ms, cleared input immediately, rendered Markdown and ended exactly at the latest message. Skill CLI injection check returned `issues=[]`.
  - Evidence: `output/playwright/task183-prod-workbench-streaming-2.4.2.png`, `output/playwright/task183-prod-settings-streaming-2.4.2.png`, `output/playwright/task183-prod-cloudcc-embed-streaming-2.4.2.png`.

- 2.4.1 TASK-182 客户互动工作台生产闭环与 AI 对话修复 on 2026-07-11:
  - Git commit: `146b6fde4ec2` on `main`; annotated tag `2.4.1` was pushed to origin. Releases `2.3.10`/`2.3.11`/`2.3.12` respectively delivered the production data path, existing-customer queue hotfix and CRM write idempotent recovery before this final UX release.
  - Scope: 发送客户助理消息时使当前语音会话失效并中止剩余 ASR 回调，保证输入框立即清空且不会被结束回调回填；用户消息、AI 回复和后续流式内容更新后自动滚到最新消息。
  - Quality gate: TASK-182 identity/assignment checks, focused backend tests, 54 frontend tests, Vite build, release dry-run, real CRM iframe conversation check, CloudCC injection verification, production backup/deploy/health/log/public smoke passed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.4.1`, index digest `sha256:1b5815b01200e0594521d82df0d25120463a911e324c3225c71cb4d3368c8e1d`, linux/amd64 manifest `sha256:cf69320faa46da1b158b58b64951b860f85b9da797d2c00f1a050586124bb311`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.4.1`, index digest `sha256:1205899908a43751b3da616c81bf9eb94ca3c0609cab0a75518faca03ba97906`, linux/amd64 manifest `sha256:c826e6792a6c9a7ecb79d207a62416c582316432d70b4ee15d9c0d0ccfbb2845`.
  - Backup: `/opt/cici/backups/20260711-083920-before-2.4.1-task182-assistant-conversation`; env `1648` bytes, PostgreSQL `2557047` bytes, KB `196338` bytes, Qdrant `1574403` bytes.
  - Deploy: backend/frontend were recreated on `2.4.1`; database, Redis, RabbitMQ and Qdrant remained healthy on running `2.3.4` containers, with local `2.4.1` aliases prepared for compose compatibility.
  - Verification: six services healthy; `/actuator/health=UP`; `/system/version` returned `version=2.4.1`, `imageTag=2.4.1`, `gitCommit=146b6fde4ec2`; V73/V74 successful; `nginx -t` passed; post-warmup error scans empty.
  - Public smoke: `https://x.agentcici.com/`, AgentCiCi workbench and CRM embed routes returned 200; HTTP redirected 301; production-IP resolved `https://onechat.agentcici.com/` returned 200.
  - Real CRM browser: composer cleared immediately after send and remained empty after reply; message view was exactly at bottom after a long AI response. `cc-customization-expert-msapi verify injectionPage` passed with `issues=[]`.

- 2.3.9 TASK-181 客户互动工作台客户列表排版修复 on 2026-07-10:
  - Git commit: `0c8f66e94d15` on `main`; annotated tag `2.3.9` was pushed to origin.
  - Scope: 修复 AI 应用页内客户互动工作台左侧客户列表排版混乱问题；队列标题字号规则不再误伤客户名称；客户行固定为标题/状态、负责人阶段/时间、标签、摘要四层结构，标签和摘要单行截断，避免半行裁切和行间重叠。
  - Quality gate: assignment/login gates, frontend build, compose config, local browser account-list check, release dry-run, production backup/deploy/health/public smoke, authenticated production browser account-list check for org `org2sva14i4udjmi2t4s`, customer workbench API smoke, and zero browser console errors passed. Backend code was unchanged; release script performed the standard backend package with skipped tests.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.9`, index digest `sha256:8e82c836f99847e88dee908601b6e8512c63355d13f725ecbafc5a2cde4a5f1c`, linux/amd64 manifest digest `sha256:9f36559e32786d2847ad70c467dcbfd9e1989ff3dce952cbb3ba96e17159fa1e`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.9`, index digest `sha256:4b56bfce1811ff18258ae8b0f2955e1dea6850d571c00d9dbe13103a1f9f572f`, linux/amd64 manifest digest `sha256:eed8c85e837599e4630bee093ad828b576967291cbda0d3acd4837dfcd568703`.
  - Backup directory: `/opt/cici/backups/20260710-141251-before-2.3.9-task181-account-list-alignment`, containing `acr.env.before-2.3.9`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` has `CICI_IMAGE_TAG=2.3.9` and `CICI_APP_VERSION=2.3.9`.
  - Deploy note: backend/frontend were pulled and force-recreated on `2.3.9`; database, Redis, RabbitMQ, and Qdrant remained healthy on existing `2.3.4` images. Existing healthy infra images were locally tagged as `2.3.9` before compose operations because compose shares `CICI_IMAGE_TAG`.
  - Verified after deploy: backend/frontend healthy; `/actuator/health=UP`; `/system/version` returned `version=2.3.9`, `imageTag=2.3.9`, `gitCommit=0c8f66e94d15`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` and `/app?aiApp=customer-workbench` returned HTTP 200; HTTP root redirected to HTTPS.
  - Browser smoke: production `https://x.agentcici.com/app?aiApp=customer-workbench` at `2048x1000` with org `org2sva14i4udjmi2t4s` returned account rows `6`, row height `104px`, no row-level overflow, no adjacent overlap, no document/body outer scrollbar, no visible right-edge scrollbar, and console error count `0`. Screenshot: `output/playwright/task181-prod-account-list-2.3.9.png`.
  - Authenticated API smoke: org `org2sva14i4udjmi2t4s` `/customer-workbench/accounts` returned `10` accounts; first detail returned timeline `3`, recommendations `2`, and `crmConnection.ready=true`.

- 2.3.8 TASK-180 AI 应用页与客户互动工作台 UI 重构 on 2026-07-10:
  - Git commit: `a811e974f203` on `main`; annotated tag `2.3.8` was pushed to origin.
  - Scope: AI 应用页常驻大列表改为点击一级侧栏触发的悬浮纵向窄列表；客户互动工作台在 AI 应用页内释放横向主区域，减少外框线和嵌套卡片感，收紧指标、tab、时间线、建议和 AI 助理密度；外层页面不显示滚动条，内部滚动区按交互显示局部滚动条。
  - Quality gate: assignment/login gates, local frontend build, static check, compose config, local browser workbench/flyout checks, release dry-run, production backup/deploy/health/public smoke, authenticated production browser workbench/flyout checks for org `org2sva14i4udjmi2t4s`, customer workbench API smoke, and zero browser console errors passed. Backend code was unchanged; release script performed the standard backend package with skipped tests.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.8`, index digest `sha256:7f96a6ac27afdd14e0eb3a34fb09ee2167cf62da481dbc1311f24d8317a7cccf`, linux/amd64 manifest digest `sha256:623f8d0719b3cc133dc2f9d3b8499efd5aa45cf297998c111f757ba7b7602c7a`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.8`, index digest `sha256:9f3d0f46f9027793989b19dbd8e24f4d0ed9c11bceba730683280a2b9424b009`, linux/amd64 manifest digest `sha256:279ba2a4d2b8af7b96bd18179b6d5a997ec9123cec82d21e1f0b990e955bcf58`.
  - Backup directory: `/opt/cici/backups/20260710-120051-before-2.3.8-task180-ai-apps-ui`, containing `acr.env.before-2.3.8`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` has `CICI_IMAGE_TAG=2.3.8` and `CICI_APP_VERSION=2.3.8`.
  - Deploy note: backend/frontend were pulled and force-recreated on `2.3.8`; database, Redis, RabbitMQ, and Qdrant remained healthy on existing `2.3.4` images. Existing healthy infra images were locally tagged as `2.3.8` before compose operations because compose shares `CICI_IMAGE_TAG`.
  - Verified after deploy: backend/frontend healthy; `/actuator/health=UP`; `/system/version` returned `version=2.3.8`, `imageTag=2.3.8`, `gitCommit=a811e974f203`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/`, `/app?aiApp=customer-workbench`, and `/app?aiApp=customer-workbench&embed=crm` returned HTTP 200; HTTP root redirected to HTTPS.
  - Browser smoke: production `https://x.agentcici.com/app?aiApp=customer-workbench` at `2048x1000` with org `org2sva14i4udjmi2t4s` returned no persistent `.cici-ai-apps__list`, no document/body horizontal or vertical overflow, no visible right-edge scrollbar, floating AI 应用 menu with `5` items, and console error count `0`. Screenshots: `output/playwright/task180-prod-workbench-demo-org2-2.3.8.png`, `output/playwright/task180-prod-flyout-demo-org2-2.3.8.png`.
  - Authenticated API smoke: org `org2sva14i4udjmi2t4s` `/customer-workbench/accounts` returned `10` accounts; first detail returned timeline `3`, recommendations `2`, and `crmConnection.ready=true`.

- 2.3.7 TASK-179 AI 听记实时发言人分离热修复 on 2026-07-10:
  - Git commit: `01a5df8cb919` on `main`; annotated tag `2.3.7` was pushed to origin.
  - Scope: AI 听记会议实时 ASR 改为自动 provider 选择；已配置讯飞的组织启用 `role_type=2` 角色分离，未配置组织继续使用阿里云实时转写并显示明确降级提示；普通语音输入保持阿里云行为。
  - Quality gate: assignment/login gates, 7 backend provider/parser tests, 7 frontend ASR/transcript tests, frontend build, compose config, local backend health, local desktop start/stop flow, release dry-run, and production browser smoke passed. Full backend baseline ran 212 tests with 12 failures and 7 errors outside TASK-179; fixture drift and PostgreSQL connection exhaustion are recorded in `.claw/test-report.md`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.7`, index digest `sha256:49cd893480060b5bfe996d0051539485f29ed5309bc399f06dd4deeb83d13a28`, linux/amd64 manifest digest `sha256:b129f11ffe390a8e64034edf1671f4ebf85ae07a57589b21750a3dac00987116`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.7`, index digest `sha256:f6a9e969f83fe94971e7202f80ee23f33b334fbe1f60788516d2ef5ecf2efe4e`, linux/amd64 manifest digest `sha256:5cc3052da66ba3a89865269d5dc4767f36ff09719c76ba4494b710557922b658`.
  - Backup directory: `/opt/cici/backups/20260710-113712-before-2.3.7-task179-ai-minutes-speaker`, containing `acr.env.before-2.3.7`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` has `CICI_IMAGE_TAG=2.3.7` and `CICI_APP_VERSION=2.3.7`.
  - Deploy note: backend/frontend were pulled and force-recreated on `2.3.7`; database, Redis, RabbitMQ, and Qdrant remained healthy on existing `2.3.4` images.
  - Verified after deploy: backend/frontend healthy; `/actuator/health=UP`; `/system/version` returned `version=2.3.7`, `imageTag=2.3.7`, `gitCommit=01a5df8cb919`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/` and `/app` returned HTTP 200; HTTP root redirected to HTTPS.
  - Browser smoke: production demo organization displayed `2.3.7`; AI 听记 entered recording without Iflytek setup errors or Aliyun diarization fallback, proving configured Iflytek auto-selection; stop released recording and console error count was zero.
  - Known log noise: two non-ASR stale-session `Session not found` 404 events appeared after browser smoke; no ASR/Iflytek failure was present.

- 2.3.6 TASK-177 数据洞察仪表盘 UI 热修复 on 2026-07-10:
  - Git commit: `aac3080c103c` on `main`; annotated tag `2.3.6` was pushed to origin.
  - Scope: 数据洞察 AI 应用移除顶部无效旧 CRM 系统信息条，避免出现组织/时间/币种/销售云主页等与数据洞察无关内容；收紧仪表盘网格和卡片边界，防止表格与风险列表向外漂出；客户洞察继续保持独立应用结构。
  - Release method: because production tag `2.3.5` had already been used by TASK-178 and the main worktree contained unrelated TASK-178 dirty changes, release was built from a clean detached worktree at `aac3080c103c` with `./scripts/release-acr.sh --version 2.3.6`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.6`, index digest `sha256:cd7d4718def63a97b11c5d233611eb994f0ecc0161bfa07639f882a98f492202`, linux/amd64 manifest digest `sha256:f77c0d55938e57f7cf5975b99b4ddf8d53d7d11ed974f569d8c1eac4b9ce8daf`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.6`, index digest `sha256:0552ff05c868a0cdd4f07f95bcd127b39082fde237a955d75750487e5c1352a3`, linux/amd64 manifest digest `sha256:1e9c63b8d51701aeabd3ccc22330fd9fb25cac8198585a20ab4a472a895de451`.
  - Backup directory: `/opt/cici/backups/20260710-083717-before-2.3.6-task177-data-insight-ui-hotfix`, containing `acr.env.before-2.3.6`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.3.6` and `CICI_APP_VERSION=2.3.6`.
  - Deploy note: backend/frontend images were pulled from ACR and recreated with `--no-deps --force-recreate`; database, Redis, RabbitMQ, and Qdrant remained healthy on existing `2.3.4` infra containers.
  - Verified after deploy: backend/frontend healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.3.6`, `imageTag=2.3.6`, and `gitCommit=aac3080c103c`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=data-insight` returned HTTP 200; `http://x.agentcici.com/` redirected to HTTPS.
  - Authenticated data insight smoke: demo org `org2sva14i4udjmi2t4s` returned `/ai/data-insights/dashboard` with `sourceMode=REAL_CRM_DEMO`, customers `10`, leads `6`, open opportunities `8`, orders `18`, risk rows `8`.
  - Browser smoke: production `https://x.agentcici.com/app?aiApp=data-insight` at `2048x1000` switched `销售业绩` / `客户` / `商机` / `订单回款`; `.cici-data-board=1`, `.cici-data-board__bar=0`, `.cici-customer-insight=0`, hero count `0`, no old CRM context text, no document/main/board/grid horizontal overflow, and no card leakage. Screenshot: `output/playwright/task177-prod-data-insight-2.3.6.png`.

- 2.3.5 TASK-178 CRM 嵌入客户互动工作台语音输入热修复 on 2026-07-10:
  - Git commit: `aac3080c103c` on `main`; annotated tag `2.3.5` was pushed to origin.
  - Scope: 修复 CloudCC CRM 嵌入客户互动工作台 AI 助手语音输入点击后误报“未识别到有效的语音内容”的问题；pagecomponent iframe 增加麦克风授权，ASR 启动失败不再被空完成回调覆盖。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.3.5`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.5`, index digest `sha256:5c060d77ccfcea496e5b28669e8bd76fc95b2624d95ed85f6cf9561c16cfc808`, linux/amd64 manifest digest `sha256:340d95bf65e5613a9dadff56319153929d4450afa4f961ca699dbfabf7c417f9`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.5`, index digest `sha256:e524eef3155551b675a48a1db986222c95a5625520fd319b5ed16f98b5e004bf`, linux/amd64 manifest digest `sha256:951d3004765e63b3e824579fa08a4eb609e5c6d7ec74b5573c79e42de2bb37ce`.
  - Backup directory: `/opt/cici/backups/20260710-083254-before-2.3.5-task178-crm-workbench-voice`, containing `acr.env.before-2.3.5`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.3.5` and `CICI_APP_VERSION=2.3.5`.
  - Deploy note: backend/frontend images were pulled from ACR and force-recreated onto `2.3.5`; stateful service containers remained healthy on their prior infra images.
  - Verified after deploy: backend/frontend healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.3.5`, `imageTag=2.3.5`, and `gitCommit=aac3080c103c`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm` returned HTTP 200.
  - CloudCC CRM publish: pagecomponent `component-customer-workbench` was published as id `6a503defe4b0a577cbba1f8a`, apiName `custc_202607y6ji407v`, version `10`; custom page `customer_interaction_workbench` was updated to id `6a503e1ee4b0a577cbba1f8b`, `renderVersion=V4.0`, component ref id `6a503defe4b0a577cbba1f8a`; `cloudcc verify injectionPage` passed with no issues.
  - Browser smoke: local UMD CRM-host simulation confirmed iframe `allow="microphone; clipboard-write"` and no outer right scrollbar; production embed page with mocked microphone denial kept the startup failure notice and did not show the empty speech notice. Screenshots: `output/playwright/task178-local-umd-microphone-allow.png`, `output/playwright/task178-prod-embed-mic-denied-debug.png`.

- 2.3.4 TASK-176 数据洞察与客户洞察解耦热修复 on 2026-07-10:
  - Git commit: `22f91cc38a3e` on `main`; annotated tag `2.3.4` was pushed to origin.
  - Scope: 恢复“客户洞察”为独立客户洞察项目/报告编辑应用；新增独立“数据洞察”AI 应用和 `/ai/data-insights/dashboard` API；移除错误挂在 `/ai/customer-insights/dashboard` 下的仪表盘，避免两个应用继续混淆。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.3.4`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.4`, index digest `sha256:d6c895eae9cbf0f2bb44ca697166682bcc15df4a26f30550920f56b481190836`, linux/amd64 manifest digest `sha256:0b1c752758bf3a4f42ae63f026b583e8e9b99ff1df45cbc0f52eb316c854877a`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.4`, index digest `sha256:93cd48b9fb61584b2c8fb4a6dd0f0a342111862162984763dd5598f4d06f1239`, linux/amd64 manifest digest `sha256:35fdd4fdb9c949b7f2f05979fc71b34f9047ad550b35479b33cf55fb02c86f3a`.
  - Backup directory: `/opt/cici/backups/20260710-080814-before-2.3.4-task176-data-insight-decoupling`, containing `acr.env.before-2.3.4`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.3.4` and `CICI_APP_VERSION=2.3.4`.
  - Deploy note: backend/frontend images were pulled from ACR and compose was force-recreated onto `2.3.4`; stateful service volumes were preserved. Infra services also restarted under locally tagged `2.3.4` aliases because compose uses the shared image tag.
  - Verified after deploy: all six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.3.4`, `imageTag=2.3.4`, and `gitCommit=22f91cc38a3e`; frontend `nginx -t` passed; recent backend log scan had no error matches beyond normal Flyway startup info.
  - Public smoke: `https://x.agentcici.com/`, `https://x.agentcici.com/app?aiApp=data-insight`, and `https://x.agentcici.com/app?aiApp=customer-insight` returned HTTP 200; `http://x.agentcici.com/` redirected to HTTPS.
  - Authenticated smoke: demo org `org2sva14i4udjmi2t4s` returned org name `智能体平台演示环境`; `/ai/data-insights/dashboard` returned `sourceMode=REAL_CRM_DEMO`, customers `10`, leads `6`; removed `/ai/customer-insights/dashboard` returned HTTP 404.
  - Browser smoke: production `https://x.agentcici.com/app?aiApp=data-insight` loaded with real production login at 1620x920; `.cici-data-board=1`, `.cici-customer-insight=0`, hero count `0`, no horizontal overflow. Production `https://x.agentcici.com/app?aiApp=customer-insight` loaded with `.cici-customer-insight=1`, `.cici-data-board=0`, no horizontal overflow. Screenshots: `output/playwright/task176-prod-data-insight-2.3.4.png`, `output/playwright/task176-prod-customer-insight-2.3.4.png`.
  - TASK-175 forward-inclusion note: customer workbench scroll cleanup released in `2.3.3` is included in this `2.3.4` production frontend. Post-release authenticated browser smoke for `/app?aiApp=customer-workbench` and `/app?aiApp=customer-workbench&embed=crm` returned `documentScrollable=false`, `bodyScrollable=false`, and `hasCrmHomeButton=false`; screenshots: `output/playwright/task175-prod-platform-workbench-2.3.4.png`, `output/playwright/task175-prod-embed-workbench-2.3.4.png`.
  - TASK-175 CloudCC CRM note: pagecomponent `component-customer-workbench` was published as id `6a50377ce4b0a577cbba1f86`, apiName `custc_202607YmKkL7PO`, version `9`; custom page `customer_interaction_workbench` was updated to id `6a503a55e4b0a577cbba1f87`, `renderVersion=V3.0`, and now references that component id. The successful customPage update required stringified `pageContent`; object-array payloads and `bind pagecomponent` returned CloudCC `500`.

- 2.3.2 TASK-174 数据洞察 AI 应用生产发布 on 2026-07-10:
  - Git commit: `d144149168ea` on `main`; annotated tag `2.3.2` was pushed to origin.
  - Scope: 智能体平台 AI 应用列表新增/升级“数据洞察”，面向 CRM 潜在客户、商机、客户、合同订单和销售业绩展示精细仪表板；演示组织优先使用 CRM-backed aggregate data，其他无数据组织使用明确标注的 Mock fallback。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.3.2`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.2`, index digest `sha256:bbde5cd14b60298ae2eae403e05be056c3dad0ac1842736ec517dba495af612c`, linux/amd64 manifest digest `sha256:3d8ca58e3a0ac295f522639f6cb3ec9e3de0b876eb21cf425d1a035246031929`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.2`, index digest `sha256:c54004dd9d37610dce33ada518b026fa9e796eafb1945bb535faa3411040699d`, linux/amd64 manifest digest `sha256:71dba59506b3a34c01499ce05489e24d920f7ec2ff22153300ffe02dd88b2936`.
  - Backup directory: `/opt/cici/backups/20260710-072126-before-2.3.2-task174-data-insight`, containing `acr.env.before-2.3.2`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.3.2` and `CICI_APP_VERSION=2.3.2`.
  - Deploy note: backend/frontend images were pulled from ACR and all six services were force-recreated onto `2.3.2`; ECS infra images were locally tagged as `2.3.2`. An initial compose run inherited stale shell env `2.3.1` and was immediately corrected by rerunning compose with cleared interpolation env, after which rendered images and running containers all showed `2.3.2`.
  - Verified after deploy: all six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.3.2`, `imageTag=2.3.2`, and `gitCommit=d144149168ea`; frontend `nginx -t` passed; recent backend log scan had no error matches.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=customer-insight` returned HTTP 200; `http://x.agentcici.com/` redirected to HTTPS; production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200. Local DNS still could not resolve `onechat.agentcici.com`, matching the existing DNS risk.
  - Authenticated data insight smoke: demo org `org2sva14i4udjmi2t4s` returned org name `智能体平台演示环境`; `/ai/customer-insights/dashboard` returned `sourceMode=REAL_CRM_DEMO`, customers `10`, leads `6`, open opportunities `8`, visible accounts `8`, funnel rows `6`, and risk rows `5`.
  - Browser smoke: production `https://x.agentcici.com/app?aiApp=customer-insight` loaded with real production login at 1620x920; `数据洞察` and `CRM 演示数据` were visible, main panel `scrollWidth=clientWidth=1306`, and dashboard `offenderCount=0`. Screenshot: `output/playwright/task174-prod-data-insight-2.3.2.png`.

- 2.3.1 TASK-173 customer workbench real agent assistant hotfix on 2026-07-10:
  - Git commit: `ff9b9cc7cc4a` on `main`; annotated tag `2.3.1` was pushed to origin.
  - Scope: 客户互动工作台右侧 AI 助理接入真实 `cici-system` 智能体编排和大模型调用，底部麦克风复用 `/ws/asr` 阿里云实时 ASR；同时修复真实演示 org 下工作台智能体 session id 超过 `chat_session_state.session_id varchar(64)` 的生产 smoke 问题。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.3.1`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.3.1`, index digest `sha256:4329dfcd50e9e13b84609437de911c69b55e876ed85567e008b2bc9f9b80e676`, linux/amd64 manifest digest `sha256:d5ff96b7570d2f1b7f92e307a572280f99a410799ebebe87644ca26ceedec960`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.3.1`, index digest `sha256:26fa409d1abc1ce147c14bbb8ff850a21bbf43ee6d57dde068384b61d7acf036`, linux/amd64 manifest digest `sha256:37f5dc73b64c794ffcba26fbcd67c1946b58f86ffa076447b092b35b96190618`.
  - Backup directory: `/opt/cici/backups/20260710-065556-before-2.3.1-task173-session-id-hotfix`, containing `acr.env.before-2.3.1`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.3.1` and `CICI_APP_VERSION=2.3.1`.
  - Deploy note: backend/frontend images were pulled from ACR and force-recreated onto `2.3.1`; ECS infra images were locally tagged as `2.3.1`, while running database/redis/rabbitmq/qdrant containers remained on healthy `2.2.10` instances.
  - Verified after deploy: backend/frontend containers run `2.3.1`; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.3.1`, `imageTag=2.3.1`, and `gitCommit=ff9b9cc7cc4a`; frontend `nginx -t` passed; recent backend log scan after authenticated smoke had no error matches.
  - Public smoke: `https://x.agentcici.com/`, `https://x.agentcici.com/app?aiApp=customer-workbench`, and `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm` returned HTTP 200; `http://x.agentcici.com/` redirected to HTTPS; production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200.
  - Authenticated demo smoke: login org `org2sva14i4udjmi2t4s` / mobile `13900009999` returned org name `智能体平台演示环境`; `/customer-workbench/accounts` returned 10 CRM-backed accounts; first account `北京智造科技有限公司` had timeline `3`, recommendations `2`, and `crmConnection.ready=true`; `/customer-workbench/assistant` returned a real model answer with `agentId=cici-system`, `runId=run-12ad6af6-c0be-44b9-a85f-166ff727c06a`, model `deepseek-v4-pro`, and 55-character session id.
  - Superseded release note: `2.2.12` for commit `82e32845ecc2` built, tagged, and deployed, with backup `/opt/cici/backups/20260710-064953-before-2.2.12-task173-real-agent-assistant`; authenticated assistant smoke then returned HTTP 500 due session id length overflow, so `2.2.12` was superseded immediately by `2.3.1` and should not be used as rollback target.

- 2.2.11 TASK-171 customer workbench queue-row layout hotfix on 2026-07-09:
  - Git commit: `d251a2661602` on `main`; annotated tag `2.2.11` was pushed to origin.
  - Scope: 修复客户互动工作台客户列表错位。生产根因是 `lastInteraction` 为互动摘要句子但被当作右侧 `time` 列渲染，固定行高和换行标签导致列表项内容溢出和视觉叠压；前端已改为 `updatedAt` 显示紧凑时间，`lastInteraction` 显示为行内两行摘要，并稳定行网格。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.11`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.11`, index digest `sha256:21deac5bab876122d1efa7044458f37d7941e44bc9826a0ce0dbe06fedde3264`, linux/amd64 manifest digest `sha256:d00f64f76508925f9d47f0f8cbc9771f2ec646172b39393a6816be9dd4242bd1`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.11`, index digest `sha256:6fded834c5cea51396686998eb3ddf69819c0ba69728cc3692175df6364014ca`, linux/amd64 manifest digest `sha256:78071167a7a6094fc1a3cd7c2131f51f558bc6e10e92555ece00a3c7a02fe281`.
  - Backup directory: `/opt/cici/backups/20260709-232920-before-2.2.11-task171-workbench-queue-layout`, containing `acr.env.before-2.2.11`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.11` and `CICI_APP_VERSION=2.2.11`.
  - Deploy note: backend/frontend images were pulled from ACR and force-recreated onto `2.2.11`; ECS infra images were locally tagged as `2.2.11`, while the running database/redis/rabbitmq/qdrant containers were left on their already healthy `2.2.10` instances to avoid unnecessary stateful restarts.
  - Verified after deploy: backend/frontend containers run `2.2.11`; all six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.11`, `imageTag=2.2.11`, and `gitCommit=d251a2661602`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/`, `https://x.agentcici.com/app?aiApp=customer-workbench`, `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`, and production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200. Local DNS still could not resolve `onechat.agentcici.com`, matching the existing DNS risk.
  - Browser smoke: production customer workbench loaded with real production login at 1620x812; `embed=crm` returned `hasOuterRail=false`, `outsideCount=0`, `rowOverlaps=[]`, `bodyOverflow=false`, `workbenchBottomVisible=true`, and `chatScrollbarVisible=false`; normal AI app route returned `hasOuterRail=true`, `outsideCount=0`, `rowOverlaps=[]`, `bodyOverflow=false`, and `chatScrollbarVisible=false`. Screenshots: `output/playwright/task171-prod-2.2.11-queue-layout.png`, `output/playwright/task171-prod-2.2.11-queue-layout-app.png`.

- 2.2.10 TASK-171 Agent platform customer workbench visual repair on 2026-07-09:
  - Git commit: `8a003121df0c` on `main`; annotated tag `2.2.10` was pushed to origin.
  - Scope: 修复 Agent 平台 AI 应用页内客户互动工作台在 `2.2.9` 后的视觉比例问题：隐藏重复的工作台内品牌/面包屑，恢复三栏比例，避免客户标签竖排，并让右侧 AI 客户助理对话区无可见滚动条。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.10`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.10`, index digest `sha256:d52997b7c5145ab9e42170cedc43c87b54af8ac0f4cf11bab8e1e7292a2ecc93`, linux/amd64 manifest digest `sha256:9894d1257205969a315b118b0f4ee65e29a17ce3fa52db967af78a0cd9651507`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.10`, index digest `sha256:b14bf2b878e24bb54accbb1346cdeee07393ac08832d43dc9f02c00f6eacef80`, linux/amd64 manifest digest `sha256:61308bfd176b343d6ead05c9e7f4eb5ee4bd1685d71254cc0d5098f65d537fb5`.
  - Backup directory: `/opt/cici/backups/20260709-225131-before-2.2.10-task171-workbench-visual-repair`, containing `acr.env.before-2.2.10`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.10` and `CICI_APP_VERSION=2.2.10`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged from the previous release to `2.2.10` because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.10`, `imageTag=2.2.10`, and `gitCommit=8a003121df0c`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/`, `https://x.agentcici.com/app?aiApp=customer-workbench`, `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`, and production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200.
  - Browser smoke: production Agent platform customer workbench loaded with real production login; new-customer mode returned `heroCount=0`, `outerOverflow=false`, `brandVisible=false`, `chatScrollbarVisible=false`, `verticalBadges=[]`, and bottom panel visible; existing-customer mode returned `hasExistingQueue=true`, `hasRiskPanel=true`, `outerOverflow=false`, `chatScrollbarVisible=false`, and `bottomVisible=true`. Screenshots: `output/playwright/task171-agent-prod-2.2.10-visual-repair.png`, `output/playwright/task171-agent-prod-2.2.10-existing-visual-repair.png`.
  - Note: frontend logs contained transient upstream connection-refused entries during the service restart window for an existing `/ai/sessions/stream` client; services were healthy and later smoke tests passed.

- 2.2.9 TASK-171 Agent platform customer workbench layout hotfix on 2026-07-09:
  - Git commit: `093c8fc85951` on `main`; annotated tag `2.2.9` was pushed to origin.
  - Scope: Agent platform AI 应用页内的客户互动工作台移除重复外层介绍栏，并把工作台约束到剩余视口高度；页面外层不再出现滚动条，客户队列、中心内容和 AI 客户助理对话使用局部滚动。
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.9`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.9`, index digest `sha256:3c138e3bbece540d781054cfcc367fb5d1dfe5ac39b7865565035dff82956058`, linux/amd64 manifest digest `sha256:306d160ce2988df544780faeda41b2f5b9382bb520c679cb6302ebf18cdca9ea`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.9`, index digest `sha256:31fb790e12f29be005cdb33c3d080f1616483efb5b836b860a4dc0eb4a30aecd`, linux/amd64 manifest digest `sha256:996cd8876b1eab4e066972b244aedd8afc17d79d2d14f76600d2a0dac4f84505`.
  - Backup directory: `/opt/cici/backups/20260709-220743-before-2.2.9-task171-workbench-layout`, containing `acr.env.before-2.2.9`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.9` and `CICI_APP_VERSION=2.2.9`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged from the previous release to `2.2.9` because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.9`, `imageTag=2.2.9`, and `gitCommit=093c8fc85951`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/`, `https://x.agentcici.com/app?aiApp=customer-workbench`, `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`, and production-IP resolved `https://onechat.agentcici.com/` returned HTTP 200.
  - Browser smoke: production Agent platform customer workbench loaded with real production login; `.cici-ai-apps__hero` count was `0`, document/body had no outer scrollbar, the workbench bottom was visible, and customer accounts, center content, and AI chat were local scroll regions. Screenshot: `output/playwright/task171-agent-prod-2.2.9-no-outer-scroll.png`.
  - Note: frontend logs contained transient upstream connection-refused entries during the service restart window for an existing `/ai/sessions/stream` client; services were healthy and later smoke tests passed.

- 2.2.7 TASK-171 CRM clean embed hotfix on 2026-07-09:
  - Git commit: `78fa13dd1185` on `main`; annotated tag `2.2.7` was pushed to origin.
  - Scope: CloudCC CRM embeds only the customer interaction workbench body via `/app?aiApp=customer-workbench&embed=crm`, removing the AgentCiCi platform rail, AI 应用列表, and pagecomponent outer header from the embedded experience.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.7`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.7`, index digest `sha256:f4f420668947d1b63f0f57792aa191361e64ea174aa50bd3f514e547089cbbfa`, linux/amd64 manifest digest `sha256:eeb1c1d344f0bbc8be36169b361bb3bfe2d66f06f7c6b873e1609b2a163f43d2`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.7`, index digest `sha256:68bbbb2b3881b7d50568bd8933eacf6df7e6bb612a9f1c2e4a766b795d86317f`, linux/amd64 manifest digest `sha256:d6cb7c349d2cbb02261b5047bd0ad8860a255a1ada8059825118fc5f446835b2`.
  - Backup directory: `/opt/cici/backups/20260709-151814-before-2.2.7-task171-clean-embed`, containing `acr.env.before-2.2.7`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.7` and `CICI_APP_VERSION=2.2.7`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged from `2.2.6` to `2.2.7` because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.7`, `imageTag=2.2.7`, and `gitCommit=78fa13dd1185`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm` returned HTTP 200.
  - CloudCC publish and real CRM smoke: `cc-customization-expert-msapi` published pagecomponent V8 id `6a4f4be8e4b0a577cbba1f70`, apiName `custc_202607F3INXE0S`; real CRM runtime loaded `component-customer-workbench-V8.0.js`; iframe used `embed=crm&ssoTicket=...`; iframe DOM returned `hasRail=false`, `hasAiApps=false`, `hasEmbedded=true`; screenshot `output/playwright/task171-cloudcc-clean-embed-v8.png`.
  - Remaining skill gap: `cloudcc bind pagecomponent ...` still returns `系统发生异常`; runtime succeeds by loading latest V8 by component name, but customPage readback remains stale and should be fixed in `cc-customization-expert-msapi`.

- 2.2.6 TASK-171 CloudCC SSO seed hotfix on 2026-07-09:
  - Git commit: `3ed80e1873bf` on `main`; annotated tag `2.2.6` was pushed to origin.
  - Scope: org-scoped customer workbench demo seed IDs after CloudCC SSO org `org2sva14i4udjmi2t4s` exposed a duplicate `customer_workbench_snapshot.public_id` collision.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.6`; backend/frontend linux/amd64 images and Git tag were pushed.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.6`, index digest `sha256:23093c7de619dd88f1b5fe1fdc67cb17e9ceb03f03728095f3ee007bdfe2c49c`, linux/amd64 manifest digest `sha256:8ef7c7c92b6629f07c9675dac9999c42fc2a4f6d8c85f7cc76003b5472acb4ff`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.6`, index digest `sha256:32e06acd70ac623cfbb761496db4d990c0d0d38740c09a5f5b2062299418bbc5`, linux/amd64 manifest digest `sha256:b870534d4f890cd760ff23106912f79e71a50066b3d88480f45342cbde6eb06d`.
  - Backup directory: `/opt/cici/backups/20260709-131149-before-2.2.6-task171-cloudcc-sso-seed`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.6` and `CICI_APP_VERSION=2.2.6`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.2.6` because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.6`, `imageTag=2.2.6`, and `gitCommit=3ed80e1873bf`; frontend `nginx -t` passed.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=customer-workbench` returned HTTP 200.
  - Real CRM embedded smoke: CloudCC CRM page loaded `component-customer-workbench-V7.0.js`; iframe used an AgentCiCi `ssoTicket`; SSO ticket, consume, auth/me, customer list, and customer detail requests all returned HTTP 200; screenshot `output/playwright/task171-cloudcc-sso-final.png`.

- 2.2.3 TASK-171 customer interaction workbench release on 2026-07-08:
  - Git commits: main workbench `5a4633dd0409`, proxy hotfix image commit `f0ec47509bde`, and source SSL vhost sync commit `0271e52` pushed to `origin/main`; annotated tags `2.2.2` and `2.2.3` were pushed to origin.
  - Scope: 客户互动工作台 AI 应用、V72 customer workbench schema and seeded demo data, `/customer-workbench/*` APIs, built-in `customer-interaction-workbench` skill, CloudCC pagecomponent/html/customPage/menu/app binding, and production HTTPS proxy for the workbench API.
  - Release method: `./scripts/release-acr.sh --dry-run`, `./scripts/release-acr.sh --version 2.2.2`, then hotfix `./scripts/release-acr.sh --version 2.2.3`; local Docker Desktop credential helper was bypassed by using an isolated Docker config copied from ECS registry auth.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.3`, index digest `sha256:a38b7b680b5669aac18e344d8ac4e0bb61ecda3f03945760a668d73e93adf807`, linux/amd64 manifest digest `sha256:1479fa5ee0abf2613dc93bdd7c008be211362357553b6d433f479e27496e0013`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.3`, index digest `sha256:51eae6feea4c10af3cab007ae7b9a05a2d6002e8a909f0306210e8d6daf62d60`, linux/amd64 manifest digest `sha256:7526d7d3d683327c94a7dd44f9cf7250d583e8281710a9f6ae78cfcae8ff6c6a`.
  - Backup directories: `/opt/cici/backups/20260708-021020-before-2.2.2-task171-customer-workbench` and `/opt/cici/backups/20260708-021708-before-2.2.3-customer-workbench-proxy`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.3` and `CICI_APP_VERSION=2.2.3`.
  - Deploy note: backend/frontend images were pulled from ACR; infra images were locally tagged as `2.2.3`; SSL vhost config `deploy/nginx.cici.ssl.conf` was synced and reloaded so `/customer-workbench/*` proxies to backend over HTTPS.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.3`, `imageTag=2.2.3`, and `gitCommit=f0ec47509bde`; frontend `nginx -t` passed; Flyway v72 had already applied during `2.2.2` boot.
  - Public smoke: `https://x.agentcici.com/` and `https://x.agentcici.com/app?aiApp=customer-workbench` returned HTTP 200; unauthenticated `/auth/me` returned expected HTTP 401; login `demo-org / 13900009999` succeeded; `/customer-workbench/accounts` returned 12 demo accounts, detail returned timeline/recommendations, and assistant returned a risk summary.

- 2.2.1 TASK-169 data quality and intelligent annotation platform release on 2026-07-07:
  - Git commit: `65364b4460c9` on `main`; annotated tag `2.2.1` was pushed to origin.
  - Scope: standalone `/admin/data-quality` data cleaning and intelligent annotation platform, `/data-quality/*` backend API, V70 quality/annotation schema, KB adapter as first data source, and independent「知微画像」AI app alongside existing「客户洞察」.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.2.1`; backend/frontend linux/amd64 images were pushed to ACR with both `2.2.1` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.2.1`, index digest `sha256:150076910eb43c79346f9ea3a16e01d1153896d95445a96d710126ad0b74f655`, linux/amd64 manifest digest `sha256:9e37580e17cd64721678d37d0d3f443d2951aaf9afa4480953a9c17942a9be4b`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.2.1`, index digest `sha256:8aeead0da9ac48b14f3311eba6bc3816af2991765baf6ef6171706280950f2c3`, linux/amd64 manifest digest `sha256:6ce87fb63a2ad35c9c6a131c1ad6f54b259e1336e3dd0a2637f4366f6f5da77c`.
  - Backup directory: `/opt/cici/backups/20260707-141611-before-2.2.1-task169-data-quality`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.2.1` and `CICI_APP_VERSION=2.2.1`.
  - Deploy note: backend/frontend images were pulled from ACR and containers were force-recreated onto `2.2.1`; current ECS infra images were locally tagged as `2.2.1` before compose because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.2.1`, `imageTag=2.2.1`, and `gitCommit=65364b4460c9`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200; unauthenticated `/auth/me` returned expected HTTP 401; login succeeded; authenticated `/auth/me`, `/agents`, `/skills`, and `/admin/agents/run-logs?limit=10` returned HTTP 200; `/admin/data-quality` SPA returned HTTP 200.
  - Known DNS note: direct workstation DNS for `onechat.agentcici.com` still failed to resolve, matching the previous DNS risk; explicit production-IP and server-local vhost smokes returned HTTP 200.

- 2.1.12 ASR WebSocket auth hotfix on 2026-07-03:
  - Git commit: `caf4baf90575` on `main`; annotated tag `2.1.12` was pushed to origin.
  - Scope: TASK-168 production AI 听记 and chat-window microphone failures where `/ws/asr?token=...` returned `401 Authentication required` before the WebSocket handler could validate query token.
  - Root cause: browser WebSocket sends JWT as query token, while `TenantContextFilter` required an `Authorization` header for `/ws/asr`; frontend waits for WebSocket open before `getUserMedia`, so the microphone never started.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.12`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.12` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.12`, index digest `sha256:535de23f21d852945b045a4c0d80b8a9a5e38eca7108bba1ea7afac14baecc22`, linux/amd64 manifest digest `sha256:011382d277870698d809c937b48278ddedd25e35dbbd43ff09e0725928e365c3`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.12`, index digest `sha256:2c6a775426132b81da57d7dcf651bc350324092630390172a551a9165fb68c97`, linux/amd64 manifest digest `sha256:b4d3b82a8ccd4a1809f4c26007b4424f1502df2ca1817dbc73053d716f3c7f58`.
  - Backup directory: `/opt/cici/backups/20260703-150359-before-2.1.12-asr-websocket-auth`, containing `acr.env.before-release` and `postgres.dump`. The current ECS layout did not have `data/kb-files` or `data/qdrant` directories to archive.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.12` and `CICI_APP_VERSION=2.1.12`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.12` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.12`, `imageTag=2.1.12`, and `gitCommit=caf4baf90575`; frontend `nginx -t` passed; recent backend ASR/WebSocket/error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200 and unauthenticated `/auth/me` returned expected HTTP 401.
  - Live ASR smoke: real login token opened `wss://x.agentcici.com/ws/asr?token=...`, received `status=connected`, sent `start`, and received `status=started`.

- 2.1.10 product-feature KB trigger and pseudo-tool guard hotfix on 2026-07-03:
  - Git commit: `b635a8fb11fd` on `main`; annotated tag `2.1.10` was pushed to origin.
  - Scope: TASK-166 production trace where `CloudCC 产品都有什么功能` had `rag_context_count=0`, `knowledge_base_names_json=[]`, `tool_call_count=0`, and final answer was literal `<search_knowledge ... />`.
  - Root cause: default-KB retrieval trigger still missed product/function/company-introduction questions, and the runtime tool-boundary prompt did not forbid model-emitted pseudo XML knowledge-search tags.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.10`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.10` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.10`, index digest `sha256:7cfb6730c6ffa020df56666bc520d7d33bef97d0729c9336aed719da190aeec2`, linux/amd64 manifest digest `sha256:fbae8c0b1a82abc6ee5b05a457e10b8f7badba11ba6c0c6b523db6efc672ec80`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.10`, index digest `sha256:fd75287bbff5e85813fe2457d662a6857e096bbe60a9f427ef944106529f2d5b`, linux/amd64 manifest digest `sha256:b10d2fc1fde30ccf88cad15b53c51e69f4de17f6880a0ac6404a8381ee413c15`.
  - Backup directory: `/opt/cici/backups/20260703-083603-before-2.1.10-product-kb-trigger`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.10` and `CICI_APP_VERSION=2.1.10`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.10` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.10`, `imageTag=2.1.10`, and `gitCommit=b635a8fb11fd`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200 and unauthenticated `/auth/me` returned expected HTTP 401 after backend warmup.

- 2.1.9 Agent-bound KB retrieval trigger hotfix on 2026-07-03:
  - Git commit: `01fb981fed61` on `main`; annotated tag `2.1.9` was pushed to origin.
  - Scope: TASK-165 production trace where customer success Agent had an ACTIVE bound knowledge base but `CloudCC私有云部署注意事项有哪些` skipped RAG with `本轮输入未满足知识库检索条件`.
  - Root cause: `SkillResolverService` resolved default KB ids, but `ChatOrchestratorService.shouldUseKnowledgeRetrieval(...)` only triggered default-KB retrieval for a conservative whitelist and missed deployment/private-cloud/notice/best-practice style questions.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.9`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.9` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.9`, index digest `sha256:9e8d77c0710f7a388918d10b1e1b3fc000d310af022a2db1429ee0a21cc22841`, linux/amd64 manifest digest `sha256:bd61d60376eb9dd83f787cd822d47e74fa5d243d2380b33826d11c0b305cdfe5`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.9`, index digest `sha256:7eba7c681395574a59da828cf1b62f08607dde80f49be2767a86ad96b776139c`, linux/amd64 manifest digest `sha256:961e16662d619d7de56cfcb546b9b2a64cb6c000ed19c9fe9b0b7763ea56e01e`.
  - Backup directory: `/opt/cici/backups/20260703-001552-before-2.1.9-agent-kb-trigger`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.9` and `CICI_APP_VERSION=2.1.9`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.9` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.9`, `imageTag=2.1.9`, and `gitCommit=01fb981fed61`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200 and unauthenticated `/auth/me` returned expected HTTP 401.

- 2.1.8 Qdrant vector dimension repair hotfix on 2026-07-02:
  - Git commit: `4cfba0b836e8` on `main`; annotated tag `2.1.8` was pushed to origin.
  - Scope: TASK-164 production KB upload bug where Qdrant collection `cici_kb_chunk` expected vector dimension `16` while current KB embedding output and DB metadata are `1024`, causing Markdown uploads to fail with `Qdrant upsert failed ... expected dim: 16, got 1024`.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.8`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.8` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.8`, index digest `sha256:28887773d8666d1606b2be18d11220a7c48ca5b2db1d914e1c9c300bb6c4514e`, linux/amd64 manifest digest `sha256:190942bfc1378cda4ed0463e387b007b0b4b23320d2b4435657bc4fe8fe67552`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.8`, index digest `sha256:de6901b1c854993cb7b161583be47deff2aba1902b0aa0a89d767c1b5aa6b3c2`, linux/amd64 manifest digest `sha256:405e479a96d7f419e52aa11042af6f6787a6a16f7e0f4a1d0020212a1c005ee5`.
  - Backup directory: `/opt/cici/backups/20260702-230957-before-2.1.8-qdrant-dimension-repair`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.8` and `CICI_APP_VERSION=2.1.8`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.8` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Qdrant repair: after backup, backend was stopped; collection `cici_kb_chunk` was deleted and recreated with `vectors.size=1024`; 311 active chunks were backfilled from PostgreSQL into Qdrant and DB `vector_id` values were refreshed.
  - Reported document recovery: failed production document `id=11`, `01-cloudcc-company-overview.md`, was requeued and became `PUBLISHED` with 6 active chunks.
  - Verified after deploy: backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.8`, `imageTag=2.1.8`, and `gitCommit=4cfba0b836e8`; Qdrant collection vector size is `1024`; Qdrant point count and active searchable chunk count are both `316`; recent Qdrant/dimension error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200 and unauthenticated `/auth/me` returned expected HTTP 401.

- 2.1.7 stale POP3 email-body retry and voice follow-up hotfix on 2026-06-26:
  - Git commit: `ee84bc85c7be` on `main`; annotated tag `2.1.7` was pushed to origin.
  - Scope: TASK-163 production dialog bug where a confirmation to read email body could use a stale POP3 `messageId`, fail with “没有找到 messageId”, and stop instead of refreshing the email id; voice input could remain blocked by a stale chat loading state after the answer appeared complete.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.7`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.7` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.7`, index digest `sha256:6058589bf31b769112f0a9d861bbd49fdda9568e13c7f16d5f528723687313b4`, linux/amd64 manifest digest `sha256:5e5f50925c1cf34810dfdaeb8ed04309ea1ccf0086d584981406260deef3dbb7`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.7`, index digest `sha256:6f8c84bfec739fbc29fab0fda7638516d694c35fad0d69376279ee8c0677b02f`, linux/amd64 manifest digest `sha256:59675ed8086d210cc7b14d3c3feafc89e7fcf8a84e6c9e9c44afee64297df253`.
  - Backup directory: `/opt/cici/backups/20260626-172221-before-2.1.7`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.7` and `CICI_APP_VERSION=2.1.7`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.7` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.7`, `imageTag=2.1.7`, and `gitCommit=ee84bc85c7be`; frontend Nginx config test passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200, HTTP endpoint redirected to HTTPS, and unauthenticated `/auth/me` returned expected HTTP 401.

- 2.1.6 continuous email-body task execution hotfix on 2026-06-26:
  - Git commit: `f88be9f89335` on `main`; annotated tag `2.1.6` was pushed to origin.
  - Scope: TASK-162 production dialog bug where user confirmation such as “是的” after a single email result caused the assistant to say “让我读取正文内容” but no `email_get_message` tool call was executed.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.6`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.6` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.6`, inspect digest `sha256:3d6003add537003c211730aaf6cd1e1b32607a68685b52fff1b89f03e5b5ee89`, linux/amd64 manifest digest `sha256:93c4bb901ce182590bbfcf3103d3371383e3bf66725c79d192594d46b0ad6a29`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.6`, inspect digest `sha256:a829b4d7111c7c8bce821329015602da52b8d7632fe67fa8a3577c17a4fd2445`, linux/amd64 manifest digest `sha256:eb41b5f73718977d19edb2650d1737cce9a1989f61b68098923bd6cac92e404d`.
  - Backup directory: `/opt/cici/backups/20260626-143306-before-2.1.6`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.6` and `CICI_APP_VERSION=2.1.6`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.6` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.6`, `imageTag=2.1.6`, and `gitCommit=f88be9f89335`; frontend Nginx config test passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200, HTTP endpoint redirected to HTTPS, and unauthenticated `/auth/me` returned expected HTTP 401.

- 2.1.5 dialog mail-body and voice-input hotfix on 2026-06-26:
  - Git commit: `947e47ddbe5a` on `main`; annotated tag `2.1.5` was pushed to origin.
  - Scope: TASK-161 production dialog bug where email body requests stopped at `email_search` results and voice input could report `未识别到有效语音内容` when frontend ASR parsing or stop finalization missed returned text.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.5`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.5` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.5`, inspect digest `sha256:32acbd0013928896c6afbe6596b1a502976d7c9ba4e9c44216f6bc9c4bec908f`, linux/amd64 manifest digest `sha256:fd0b7c0b0dedb58bc9a47a184b012c2a6bde8065d72226087176a6da57a0c02f`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.5`, inspect digest `sha256:ee40b1c309df2e7764845de474eb7f50a7d5b432e5faaee4b720ff183c58f6f7`, linux/amd64 manifest digest `sha256:1281dcc7825334deabddc2a6559d976a2e0e196795619806ed0962e0c22d7a6a`.
  - Backup directory: `/opt/cici/backups/20260626-135931-before-2.1.5`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.5` and `CICI_APP_VERSION=2.1.5`.
  - Deploy note: backend/frontend images were pulled from ACR successfully; ECS infra images were locally tagged as `2.1.5` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.5`, `imageTag=2.1.5`, and `gitCommit=947e47ddbe5a`; frontend Nginx config test passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200, HTTP endpoint redirected to HTTPS, and unauthenticated `/auth/me` returned expected HTTP 401.

- 2.1.4 chat session state tenant-key hotfix on 2026-06-26:
  - Git commit: `d40d53d0a228` on `main`; annotated tag `2.1.4` was pushed to origin.
  - Scope: TASK-159 production chat failure where `chat_session_state` had a single-column `session_id` primary key while application access is tenant-scoped by `session_id + org_id`.
  - Release method: required dry-run passed and resolved `2.1.4`; normal `./scripts/release-acr.sh --version 2.1.4` was attempted twice but ACR/registry push stalled after backend layer push and before manifest/tag completion. To restore production chat, backend/frontend artifacts were copied to ECS and images were built locally on the ECS host under the canonical image names/tags.
  - Backend image on ECS: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.4`, local image id `0131f3dcd944`.
  - Frontend image on ECS: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.4`, local image id `a410d430f10b`.
  - Backup directory: `/opt/cici/backups/20260626-124138-before-2.1.4`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.4` and `CICI_APP_VERSION=2.1.4`.
  - Deploy note: backend/frontend images plus infra image aliases are present locally on ECS as `2.1.4`; the `2.1.4` image set still needs ACR durability follow-up because registry push did not complete from this workstation.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.4`, `imageTag=2.1.4`, and `gitCommit=d40d53d0a228`; frontend Nginx config test passed.
  - Database verification: Flyway latest row is `69|chat session state tenant primary key|true`; `chat_session_state` primary key columns are `session_id, org_id`; rollback insert proof for another org using `workbench:cici-system` succeeded.
  - Chat smoke: org login succeeded; `/ai/chat` with `sessionId=workbench:cici-system` returned HTTP 200 and `success=true`; no new `chat_session_state_pkey` logs appeared after the smoke.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200; HTTP endpoint redirects to HTTPS; recent backend error scan was empty.

- 2.1.3 public demo booking release on 2026-06-24:
  - Git commit: `916ee5f48d7a` on `main`; annotated tag `2.1.3` was pushed to origin.
  - Scope: TASK-144 public website follow-up, removing the screenshot-marked homepage hero CTA button group and wiring every public demo form to the real operations appointment records.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.3`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.3` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.3`, inspect digest `sha256:1efef95e23217006deae26760d0c006d1e6ba8faba4d09ea2d962cdeda2677d1`, linux/amd64 manifest digest `sha256:f8658ff9076c782b4b7024a55ad33497144eef6f5b27b68417812d6aea48cd11`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.3`, inspect digest `sha256:2d0f9025855c6d10bc164fa28bd03fac72f754c525d4441f317ca0556a1042ab`, linux/amd64 manifest digest `sha256:142ea9891921fcd881202438a21447f3b370fe5a345ae95e0d195f3d6344fef4`.
  - Backup directory: `/opt/cici/backups/20260624-111422-before-2.1.3`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.3` and `CICI_APP_VERSION=2.1.3`.
  - Deploy note: ECS infra images were locally tagged as `2.1.3` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.3`, `imageTag=2.1.3`, and `gitCommit=916ee5f48d7a`; Flyway latest migration remains `68|agent runtime concurrency hardening|true`; frontend Nginx config test passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200 and the HTTP endpoint redirected to HTTPS; `onechat.agentcici.com` direct DNS still returned NXDOMAIN, while explicit production-IP HTTPS resolve returned HTTP 200.
  - API/browser smoke: org login and core org APIs passed; platform login, skills, tools, and `/api/platform/audit/logs?limit=100` passed; browser production homepage showed zero hero CTA buttons and header demo access; production demo submission created operations appointment record `id=8`, company `线上发布验证 REL213-1782271046262`, sourcePath `/global/docs`.

- 2.1.2 platform audit hotfix on 2026-06-23:
  - Git commit: `06288ee6403b` on `main`; annotated tag `2.1.2` was pushed to origin.
  - Scope: TASK-151 platform audit query fix for production `/api/platform/audit/logs` HTTP 500 caused by nullable keyword `LIKE` binding being inferred as `bytea`.
  - Release method: `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.2`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.2` and `latest`.
  - Release note: the first non-explicit `./scripts/release-acr.sh` attempt failed before tag/image completion because GitHub tag lookup reset and Docker Hub `eclipse-temurin:21-jre` metadata returned EOF; final release used explicit version `2.1.2` and a local backend-image tag as the JRE base to avoid Docker Hub metadata EOF.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.2`, inspect digest `sha256:9fcdb4ce2941120d1a25643db49858eca98739d777cf71f23bf000ff45d2427f`, linux/amd64 manifest digest `sha256:b1b390aa5d844676864f7457ea444e3e19f302d70fddf1629ba7310d64985c5f`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.2`, inspect digest `sha256:0b543efd2680c47ad6579994bfe09f8f7af3877768c4e8bee4cf99d2b6c5d32b`, linux/amd64 manifest digest `sha256:081e93418f85fae9210aeda3c2bb1b1d453b707455dbbffcae5aa107900011af`.
  - Backup directory: `/opt/cici/backups/20260623-100637-before-2.1.2`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.2` and `CICI_APP_VERSION=2.1.2`.
  - Deploy note: ECS infra images were locally tagged as `2.1.2` before compose up because Compose uses the shared `CICI_IMAGE_TAG` for all six services.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.2`, `imageTag=2.1.2`, and `gitCommit=06288ee6403b`; Flyway latest migration remains `68|agent runtime concurrency hardening|true`; frontend Nginx config test passed.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200; `onechat.agentcici.com` direct DNS still returned NXDOMAIN, while explicit production-IP HTTPS resolve returned HTTP 200.
  - API/browser smoke: org login and core org APIs passed; platform login, skills, tools, and `/api/platform/audit/logs?limit=100` passed; browser `/platform/audit` loaded with version `2.1.2` and no loading-failure fallback.

- 2.1.1 production-readiness release on 2026-06-22:
  - Git commit: `17ec11b404a8` on `main`; annotated tag `2.1.1` was pushed to origin.
  - Scope: Agent Builder production readiness gate/evaluation, enterprise KB readiness, and Agent runtime concurrency hardening.
  - Release method: `./scripts/release-acr.sh --dry-run` then `./scripts/release-acr.sh`; backend/frontend linux/amd64 images were pushed to ACR with both `2.1.1` and `latest`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.1`, inspect digest `sha256:1eabbcaf629f0951c47cd66ddcfebe1d195d73d135d144292ddaa47dd836afc4`, linux/amd64 manifest digest `sha256:274aa1505147f368f15245349f766662399ad1b30d5af6204c8b1f00093ee9f3`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.1`, inspect digest `sha256:eb6fcc503d1928e77d5a6ce4f2d993bcd5533fd2c279bcf5ce5dd310c4d36ee9`, linux/amd64 manifest digest `sha256:71326a6ea0c336bfc6d0a86b9025c27ff6e0802d3ded72b436b75df1171790e1`.
  - Backup directory: `/opt/cici/backups/20260622-212252-before-2.1.1`, containing `acr.env.before-release`, `postgres.dump`, `kb-files.tgz`, and `qdrant.tgz`.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.1` and `CICI_APP_VERSION=2.1.1`.
  - Deploy note: `docker compose up -d` initially could not resolve `cici-qdrant:2.1.1` from ACR because Compose uses the shared `CICI_IMAGE_TAG` for all six services; resolved by locally tagging the current ECS infra images as `2.1.1` before rerunning compose.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.1`, `imageTag=2.1.1`, and `gitCommit=17ec11b404a8`; Flyway latest migration is `68|agent runtime concurrency hardening|true`; frontend Nginx config test passed.
  - Public smoke: `https://x.agentcici.com/` returned HTTP 200; `onechat.agentcici.com` server-local and explicit production-IP HTTPS vhost smoke returned HTTP 200, but direct DNS returned NXDOMAIN from the current resolver.
  - API smoke: org login and core org APIs passed; platform login, skills, and tools passed; `/api/platform/audit/logs` still returns backend 500 from the known audit query issue.

- Production domain cutover on 2026-06-01:
  - Scope: stopped the application HTTPS vhost for `agentcici.com`, `www.agentcici.com`, and `autoservice.agentcici.com`; enabled production service on `onechat.agentcici.com` and `x.agentcici.com`.
  - Changed file deployed to ECS: `/opt/cici/deploy/nginx.cici.ssl.conf`.
  - Backup directory: `/opt/cici/backups/20260601-153012-before-domain-cutover`, containing `nginx.cici.ssl.conf.before-domain-cutover`.
  - Deploy command used on ECS: copy updated `deploy/nginx.cici.ssl.conf`, then `docker exec cici-frontend nginx -t` and `docker exec cici-frontend nginx -s reload`.
  - Verified after deploy: `cici-frontend` remained healthy; `https://onechat.agentcici.com/` and `https://x.agentcici.com/` returned HTTP 200; both new hosts proxied `/auth/me` to backend JSON; backend health returned `UP`.
  - Retired host verification: `https://agentcici.com/` and `https://autoservice.agentcici.com/` returned empty replies from the default HTTPS server; `https://www.agentcici.com/` did not resolve from this workstation.

- 2.0.B1 customer insight / Open API online test release on 2026-05-15:
  - Git commit: `0c291df` on `origin/main`.
  - Release tag in `/opt/cici/deploy/acr.env`: `2.0.B1-customer-insight-20260515-161832`.
  - Scope: customer insight AI app, AI apps workspace follow-ups, Open API CORS and builtin skill resource robustness, API call-log detail UI, KB embedding model settings, and meeting-minutes SDK/page refinements.
  - Deployment method: ACR credentials remain unreliable, so backend/frontend artifacts were copied to ECS and Docker images were built locally on the ECS host; infra images were tagged locally with the same `CICI_IMAGE_TAG`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.0.B1-customer-insight-20260515-161832`, image id `sha256:6996e499dcab71f408cfe068901292d556e826436c86a96781137c34dd6144e8`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.0.B1-customer-insight-20260515-161832`, image id `sha256:e9f5fc0152790533fec500ccfe321897deee37c8f3cb1410ab617ad04bcc0403`.
  - Backup directory: `/opt/cici/backups/20260515-161843-before-2.0.B1-customer-insight-20260515-161832`, containing env, compose/nginx deploy files, and `postgres.dump`.
  - Deploy command used on ECS: copy updated compose/nginx files into `/opt/cici/deploy`, update `/opt/cici/deploy/acr.env` `CICI_IMAGE_TAG`, then `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d`.
  - Verified after deploy: six compose services healthy; backend `/actuator/health` returned `UP`; Flyway latest rows were `52|kb embedding model settings|true`, `51|customer insight ai app|true`, and `50|embed app backend|true`; frontend Nginx config test passed; server-local HTTPS vhost smoke for `autoservice.agentcici.com` returned HTTP 200 for `/`, `/sdk/meeting-minutes.js`, and Open API preflight; fixed-password login succeeded; `GET /ai/customer-insights/catalog` returned HTTP 200 with 26 modules.
  - Durability follow-up: repair ACR credentials and push/recreate this tag in ACR; until then this release image set is present on the ECS host but not guaranteed recoverable from registry after host/image cleanup.

- 2.0.B1 Open API CORS hotfix on 2026-05-15:
  - Release tag in `/opt/cici/deploy/acr.env`: `2.0.B1-openapi-cors-20260515-1518`.
  - Scope: backend Open API CORS only; `/openapi/v1/**` now has a dedicated CORS filter and production env `APP_AGENT_OPEN_API_CORS_ALLOWED_ORIGINS=*`, allowing browser preflight from all origins for `Authorization`, `Content-Type`, `X-Cici-Api-Key`, `Idempotency-Key`, and related headers.
  - Source control basis: clean temporary worktree from `HEAD 6e6868c`; only Open API CORS backend/config/deploy files were patched before packaging.
  - Build: temporary worktree `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiCorsConfigTest test` succeeded; `backend mvn -q -Dmaven.repo.local=.m2 -DskipTests package` produced `cc-cici-assistant-backend-0.0.1-SNAPSHOT.jar`.
  - Deployment method: because ACR credentials are still not reliable, the jar and deploy compose were copied to ECS and the backend image was built locally on the ECS host.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.0.B1-openapi-cors-20260515-1518`, image id `sha256:5c904053aa0efc74d2e9673d6815d7fdb18d1a96765ab1e1be2c0f0d0681cf89`.
  - Current frontend/database/redis/rabbitmq/qdrant images were also tagged with `2.0.B1-openapi-cors-20260515-1518` so the compose-wide `CICI_IMAGE_TAG` remains resolvable locally.
  - Backup directory: `/opt/cici/backups/20260515-152355-before-openapi-cors`, containing `acr.env.before-openapi-cors`, `docker-compose.acr.yml.before-openapi-cors`, and `postgres.dump`.
  - Deploy command used on ECS: update `/opt/cici/deploy/acr.env`, then `docker compose --env-file acr.env -f docker-compose.acr.yml -f docker-compose.acr.ssl.yml up -d --no-deps backend`.
  - Verified after deploy: `cici-backend` healthy on image `2.0.B1-openapi-cors-20260515-1518`; `GET http://127.0.0.1:8080/actuator/health` returned `UP`; server-local HTTPS vhost preflight for `autoservice.agentcici.com` returned HTTP 200 with `Access-Control-Allow-Origin: *` for both `https://cnbh01.cloudcc.cn` and `https://example.anywhere`; all six compose services remained healthy.
  - Workstation direct public curl to `https://autoservice.agentcici.com/...` still returned the previously observed connection reset; server-local Host-header smoke remains the trusted verification path for this known network issue.

- 2.0.B1 SDK 404 hotfix on 2026-05-15:
  - Release tag in `/opt/cici/deploy/acr.env`: `2.0.B1-sdk404fix-20260515-1103`.
  - Scope: frontend SDK static assets only; the hotfix fixes `meeting-minutes.js` origin resolution so CloudCC parent pages create iframe URLs under `https://autoservice.agentcici.com/embed/meeting-minutes` instead of the CloudCC host.
  - Source control basis: clean temporary worktree from `HEAD 6e6868c`; only `frontend/public/sdk/meeting-minutes.js` and `frontend/public/sdk/meeting-minutes@1.0.0.js` were patched in the release worktree before build.
  - Build: temporary worktree `frontend npm install` then `npm run build` succeeded; Vite chunk-size warning remained.
  - ACR note: Docker login using current `/opt/cici/deploy/acr.env` ACR credentials failed with `unauthorized: authentication required` both locally and on ECS, so the image could not be pushed to ACR during this session.
  - Deployment method: ECS-local Docker build produced `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.0.B1-sdk404fix-20260515-1103`, image id `sha256:7d575b6ea5f2c8e08a63a3c883b287b143cbe6cf0d62a9ba668f7bdd8426be51`; existing local `2.0.B1` backend/database/redis/rabbitmq/qdrant images were tagged with the same hotfix tag so the single compose `CICI_IMAGE_TAG` can resolve locally.
  - Backup directory: `/opt/cici/backups/20260515-110703-before-2.0.B1-sdk404fix-20260515-1103`, containing `acr.env.before-sdk404fix`.
  - Deploy command used on ECS: update `CICI_IMAGE_TAG` in `/opt/cici/deploy/acr.env`, then `docker compose --env-file acr.env -f docker-compose.acr.yml -f docker-compose.acr.ssl.yml up -d --no-deps frontend`.
  - Verified after deploy: `cici-frontend` healthy, backend and infrastructure containers remained healthy, `docker exec cici-frontend nginx -t` passed, and server-local HTTPS vhost smoke for `autoservice.agentcici.com` returned HTTP 200 for `/sdk/meeting-minutes.js`, `/embed/meeting-minutes`, and `/`.
  - Durability follow-up: refresh/fix ACR credentials and push or recreate this hotfix tag in ACR; until then the hotfix image is present on the ECS host but not guaranteed recoverable from registry after host/image cleanup.

- 2.0.B1 online test release on 2026-05-14:
  - Git commit: `44550bd` on `origin/main`.
  - Annotated tag: `2.0.B1`, message `嵌入式智能应用`.
  - ACR backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.0.B1`, digest `sha256:cbcd877d4372481c832dda3fe9f73448cc46d9d9a9e57327386fb7c86497429f`.
  - ACR frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.0.B1`, digest `sha256:596d56a4d4226cfa9c75618cacde717a28e85bb94d63e66bcf698d95303aef62`.
  - ACR infra image aliases: `cici-database:2.0.B1`, `cici-redis:2.0.B1`, `cici-rabbitmq:2.0.B1`, and `cici-qdrant:2.0.B1` point to the previously verified `V1.9` linux/amd64 manifests.
  - ECS backup directory: `/opt/cici/backups/20260514-195316-before-2.0.B1`, including PostgreSQL dump, `acr.env`, and compose/nginx deploy config.
  - Remote env: `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.0.B1`.
  - Deploy command used on ECS: `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml pull && docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d`.
  - Flyway operational note: first backend boot failed because production `flyway_schema_history` had old V18 checksum `-603872790` while current code resolves V18 as `1633949654`. After the release backup, `flyway_schema_history.version=18` was updated to `1633949654`; backend then started and applied V49/V50. This mirrors the local production-dump repair needed earlier on 2026-05-14.
  - Verified after deploy: six compose services healthy, backend `/actuator/health` returned `UP`, latest Flyway row `50|embed app backend|true`, frontend Nginx config test passed, and server-local HTTPS vhost smoke returned HTTP 200 for `autoservice.agentcici.com`, `/sdk/meeting-minutes.js`, `/embed/meeting-minutes`, `agentcici.com`, and `www.agentcici.com`.
  - Workstation direct public curl still returned connection reset for `https://agentcici.com/` and `https://autoservice.agentcici.com/`; server-local Host-header checks passed and should remain the trusted deployment smoke for this known network path until the external reset is separately diagnosed.

## Verified Local Environment

- Verified on 2026-05-13T12:01:23Z:
  - Docker CLI: 29.4.3 installed with Homebrew.
  - Docker Compose CLI plugin: 5.1.3 installed with Homebrew and enabled through `~/.docker/config.json`.
  - Lima: 2.1.1 installed with Homebrew.
  - Local Docker daemon: Lima Alpine VM `cici-docker`, created from `template:alpine` with 4 CPUs, 8GiB memory, 60GiB disk, and writable `/Volumes/AISpace` mount.
  - VM Docker packages: Docker Engine 29.1.3 and Compose v2.40.3 installed inside `cici-docker`.
  - Java: OpenJDK 21.0.11 installed with Homebrew at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
  - Maven: 3.9.15 installed with Homebrew.
  - Node.js: v22.22.2 available from Hermes.
  - npm: 10.9.7 available from Hermes.
  - Note: Docker Desktop and Colima were attempted first, but large VM/DMG downloads were repeatedly reset by the current network. The verified local path for this machine is Lima `cici-docker`.

- Workspace path update on 2026-05-13T07:57:15Z:
  - Current local project root: `/Volumes/AISpace/codehouse/cc-agentcici`.
  - Updated hardcoded local restart paths in `restart-services.sh` and the detached `screen` commands below.

- Verified on 2026-04-01T13:14:19Z:
  - Java: OpenJDK 21.0.10 available
  - Maven: 3.9.14 available
  - Node.js: v25.8.0 available
  - npm: 11.11.0 available

## Verified Commands

- Compose responsibility boundary:
  - Root `docker-compose.yml` is local development infrastructure only. It starts PostgreSQL, Redis, RabbitMQ, and Qdrant for host-run Maven/Vite development.
  - Root `docker-compose.yml` is intentionally incomplete and must not be treated as the one-click application deployment file.
  - Complete server deployment uses `deploy/docker-compose.acr.yml` through `./scripts/deploy-acr.sh`, with the six ACR images under `op-registry.cloudcc.cn/cloudcc-ai-native`.
- Backend build/test:
  - `cd backend && mvn -Dmaven.repo.local=.m2 test`
  - Verified on 2026-04-01T13:44:22Z
- Backend build/test with local profile:
  - `cd backend && mvn -Dmaven.repo.local=.m2 -Dspring.profiles.active=local test`
  - Verified on 2026-04-01T13:44:22Z
- Frontend install/build:
  - `cd frontend && npm run build`
  - Verified on 2026-04-01T13:52:54Z
- Local infrastructure:
  - `docker compose up -d`
  - `docker compose ps` shows `postgres` and `redis` healthy
  - Verified on 2026-04-01T13:33:38Z
- Full quality gate:
  - `./scripts/quality-check.sh` — backend `mvn test` (default profile), frontend build, optional Qdrant smoke if `localhost:6333` is up
  - Verified on 2026-04-02T12:00:00Z
- Backend package (skip tests):
  - `cd backend && mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Verified on 2026-04-01T15:07:00Z
- Local backend runtime (alternate port):
  - `cd backend && mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments="--server.port=8081 --app.kb.vector-store=memory"`
  - Verified on 2026-04-01T15:23:34Z
- Full local demo (Docker + backend + E2E + Vite):
  - `./scripts/run-full-demo.sh`
  - Verified on 2026-04-02 (E2E PASSED; assistant UI `/`, admin UI `/admin/login` on port 5173)
- Local backend runtime (current verified command):
  - `cd backend && mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
  - Verified on 2026-04-17T03:30:04Z
  - Smoke result:
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`
- Local frontend dev server (current verified command):
  - `cd frontend && npm run dev`
  - Verified on 2026-04-17T03:30:04Z
  - Smoke result:
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`
  - Dev proxy (updated 2026-05-14): `vite.config.ts` / `vite.config.js` forward `/agents`, `/skills`, `/feishu`, `/wecom`, and `/embed/v1` to `VITE_BACKEND_TARGET` or `http://127.0.0.1:8080` so Agent Builder, channel callbacks, embedded app admin/runtime APIs, and related APIs are not answered by Vite as static 404.
- Local LAN access for development:
  - Verified on 2026-05-15T07:09:02Z from the host machine using Wi-Fi IP `192.168.0.105`.
  - Frontend Vite already has `server.host: "0.0.0.0"` in `frontend/vite.config.ts`; `npm run dev` listens on `*:5173`.
  - Spring Boot local profile listens on `*:8080`; no `server.address` override is configured.
  - Verified smoke:
    - `GET http://192.168.0.105:5173/` -> `HTTP 200`
    - `GET http://192.168.0.105:8080/actuator/health` -> `HTTP 200`
  - Other LAN devices should use `http://<host-lan-ip>:5173/`. If macOS blocks the connection, allow incoming connections for the Node/Vite process and Java/Maven backend process in Firewall settings.
- Local service restart from Codex desktop with PostgreSQL:
  - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant`
  - `screen -dmS cici-backend /bin/zsh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici/backend && exec mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local > /tmp/cici-backend.log 2>&1'`
  - `screen -dmS cici-frontend /bin/zsh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici/frontend && exec npm run dev > /tmp/cici-frontend.log 2>&1'`
  - Verified production-to-local PostgreSQL overwrite on 2026-05-14T10:48:18Z:
    - Stopped local backend screen before restoring to avoid active PostgreSQL connections.
    - Backup directory: `output/db-sync-20260514-184504/`.
    - Local pre-overwrite backup: `output/db-sync-20260514-184504/local-before-prod-sync.dump`.
    - Production dump copied from ECS `cici-database`: `output/db-sync-20260514-184504/prod-cici-assistant.dump`.
    - Restore path: drop/recreate local `cici_assistant`, `pg_restore` production dump, run local Flyway repair for V18 checksum drift, then migrate current code to V50.
    - Verification: local backend `/actuator/health` -> `UP`; frontend `HEAD /` -> `200`; latest Flyway `50|embed app backend|true`; local counts `user_account=16`, `organization_member=16`, `knowledge_base=2`, `kb_document=6`, `kb_chunk=310`; password login works for `13900009999/szyd1234` and `13800138111/szyd1234`.
    - Scope note: PostgreSQL only. KB files volume and Qdrant vectors were not overwritten.
  - Verified on 2026-05-07T12:19:29+08:00:
    - backend log: active profile `local`, database `jdbc:postgresql://localhost:5432/cici_assistant (PostgreSQL 16.13)`, Flyway schema version `40`
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`
  - Verified restart on 2026-05-09T08:53:35+08:00:
    - `screen` sessions: `cici-backend`, `cici-frontend`
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`
    - Local legacy PostgreSQL note: if the old database has `app_user` but not FEAT-024 account tables, first repair Flyway V8/V9 checksums, then backfill `user_account`, `account_login_identifier`, and `organization_member` from `app_user` while preserving `app_user.id` as the member ID so historical `user_id` references remain valid.
  - Verified restart on 2026-05-11T15:24:14+08:00:
    - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` kept all local infrastructure containers running.
    - Old listeners on ports `8080` and `5173` were stopped before restart.
    - Detached `screen` sessions: `3261.cici-frontend`, `3258.cici-backend`.
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
  - Verified restart on 2026-05-11T20:45:16+08:00:
    - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` kept all local infrastructure containers running.
    - Old `cici-backend` / `cici-frontend` screen sessions and listeners on ports `8080` and `5173` were stopped before restart.
    - Detached `screen` sessions: `72258.cici-backend`, `72261.cici-frontend`.
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
  - Verified restart on 2026-05-11T23:50:10+08:00:
    - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` kept all local infrastructure containers running.
    - Old `cici-backend` / `cici-frontend` screen sessions and listeners on ports `8080` and `5173` were stopped before restart.
    - Detached `screen` sessions: `35525.cici-backend`, `35528.cici-frontend`.
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
  - Verified restart on 2026-05-12T00:29:27+08:00:
    - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` kept all local infrastructure containers running.
    - Old `35525.cici-backend` / `35528.cici-frontend` screen sessions were stopped; stale Java listener on `8080` was killed after the screen wrapper exited.
    - Detached `screen` sessions: `59117.cici-backend`, `59119.cici-frontend`.
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
  - Verified restart on 2026-05-12T07:30:23+08:00:
    - `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant` kept all local infrastructure containers running.
    - Old `59117.cici-backend` / `59119.cici-frontend` screen sessions and listeners on ports `8080` / `5173` were stopped before restart.
    - Detached `screen` sessions: `67822.cici-backend`, `67824.cici-frontend`.
    - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
  - Verified backend restart on 2026-05-09T14:23:43+08:00 for FEAT-024 v43:
    - Stale Java process on port `8080` had to be killed after the detached screen wrapper exited; then `cici-backend` was restarted with the same screen command.
    - Flyway validated 44 migrations and reported schema version `43`; `/actuator/health` returned `{"status":"UP"}`.
    - Platform API smoke with platform token: `GET http://127.0.0.1:8080/platform/tenants` -> success with `demo-org`.
    - Vite proxy smoke during browser verification: `/api/platform/tenants` and `/api/platform/tenants/demo-org/retention` -> `200`.
  - Verified backend restart on 2026-05-09T16:04:16+08:00 for FEAT-024 v44:
    - Stopped stale `cici-backend` screen, killed the remaining old `CiciAssistantApplication --spring.profiles.active=local` Java process on port `8080`, then restarted `cici-backend` with the same screen command.
    - Flyway validated 45 migrations, migrated PostgreSQL schema from `43` to `44`, and `/actuator/health` returned `{"status":"UP"}`.
    - Platform API smoke with platform token: `GET /platform/tenants/demo-org/retention` returned `exportJobs` and legal hold execution fields; dry-run `POST /platform/tenants/demo-org/purge-jobs` returned `manifestVersion=v2`, `unsupportedCount=2`, `totalRows=2302`.
  - FEAT-024 purge worker productionization knobs added on 2026-05-09:
    - Migration `V46__organization_purge_worker_lease.sql` adds worker lease and dead-letter columns to `organization_purge_job`.
    - Optional config: `app.lifecycle.purge-worker-id` can pin a stable worker ID for an instance; if omitted, the app generates `{hostname}-{uuid}` at boot.
    - Optional config: `app.lifecycle.purge-worker-lease-minutes` defaults to `60` and is clamped to at least `5`; stale `RUNNING` jobs whose `lock_expires_at` has passed are marked `DEAD_LETTER` for manual inspection rather than automatically re-executed.
    - Existing scheduler knobs remain `app.lifecycle.purge-worker-delay-ms` and `app.lifecycle.purge-worker-initial-delay-ms`, both defaulting to `30000`.
  - Codex desktop note: `./restart-services.sh` can pass health checks while running, but its background child processes may be reaped when the command session exits. Use detached `screen` sessions when the local service must stay running after the command returns.
- Local service bootstrap with Lima Docker VM on 2026-05-13T12:01:23Z:
  - One-time host tooling:
    - `brew install docker docker-compose colima openjdk@21 maven`
    - `mkdir -p ~/.docker` and configure `~/.docker/config.json` with `cliPluginsExtraDirs: ["/opt/homebrew/lib/docker/cli-plugins"]`.
  - One-time Lima VM setup:
    - `limactl start --tty=false --name=cici-docker template:alpine --set '.cpus=4' --set '.memory="8GiB"' --set '.disk="60GiB"' --set '.mounts += [{"location":"/Volumes/AISpace","mountPoint":"/Volumes/AISpace","writable":true}]'`
    - `limactl shell cici-docker sudo sh -lc 'apk update && apk add docker docker-cli-compose curl bash shadow'`
    - `limactl shell cici-docker sudo sh -lc 'rc-update add docker default >/dev/null 2>&1 || true; service docker start; addgroup owenmacbook docker >/dev/null 2>&1 || true'`
    - If `limactl shell cici-docker docker ps` still says permission denied after adding the group, close the cached SSH control master once: `ssh -F ~/.lima/cici-docker/ssh.config -O exit lima-cici-docker`.
  - Local infrastructure start:
    - `limactl shell cici-docker sh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici && docker compose pull postgres redis rabbitmq qdrant'`
    - `limactl shell cici-docker sh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici && docker compose up -d --remove-orphans postgres redis rabbitmq qdrant'`
    - Verified images: `postgres:16`, `redis:7`, `rabbitmq:3-management`, `qdrant/qdrant:v1.12.6`.
    - Verified host ports: `5432`, `6379`, `5672`, `15672`, and `6333` reachable on `127.0.0.1`.
  - Backend start:
    - `screen -dmS cici-backend /bin/zsh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici/backend && export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH" && exec mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local > /tmp/cici-backend.log 2>&1'`
    - Verified backend screen: `39939.cici-backend`.
    - Verified backend health: `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - Verified fresh PostgreSQL Flyway bootstrap applied 49 migrations, latest `V49__ai_meeting_notetaker_cloudcc_prompt.sql`.
  - Frontend start:
    - `cd frontend && npm install` was run once to repair native optional dependencies after macOS rejected stale Rollup/fsevents `.node` modules.
    - `xattr -dr com.apple.quarantine frontend/node_modules` was run to clear Gatekeeper quarantine on native npm modules.
    - `screen -dmS cici-frontend /bin/zsh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici/frontend && exec npm run dev > /tmp/cici-frontend.log 2>&1'`
    - Verified frontend screen: `41592.cici-frontend`.
    - Verified frontend smoke: `HEAD http://127.0.0.1:5173/` -> `HTTP/1.1 200 OK`.
- Local infra status:
  - `docker compose ps`
  - With Lima `cici-docker`, use `limactl shell cici-docker sh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici && docker compose ps'`.
  - Vector retrieval uses **Qdrant** on host `6333` only; legacy `cici-milvus` container removed (2026-04-19).
- ACR one-click deployment:
  - Canonical production release runbook: `docs/production-release-runbook.md`.
  - `cp deploy/acr.env.example deploy/acr.env`
  - Edit `deploy/acr.env` for ACR credentials, production passwords, JWT secret, model API key, and ports.
  - `./scripts/deploy-acr.sh`
  - Manual equivalent:
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml pull`
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml up -d`
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml ps`
  - Verified on 2026-05-07: compose config, deploy script syntax, mounted frontend Nginx config syntax, and target diff whitespace checks passed.
  - Note: backend/frontend ACR images currently require `CICI_PLATFORM=linux/amd64` on arm64 hosts.
- ACR backend/frontend image build and push:
  - Canonical command: `./scripts/release-acr.sh`; use `./scripts/release-acr.sh --dry-run` first to see the next generated version.
  - The script uses one version for `cici-backend:<version>`, `cici-frontend:<version>`, Git tag `<version>`, backend `CICI_APP_VERSION`, and frontend `VITE_CICI_APP_VERSION`.
  - Default version train is `2.0.B<n>`; after `2.0.B2`, the next generated release is `2.0.B3`.
  - Deploy with both `CICI_IMAGE_TAG=<version>` and `CICI_APP_VERSION=<version>` in `deploy/acr.env`; `latest` is only a convenience alias, not the release identity.
  - Codex desktop note from V1.7: when using a temporary Docker config for ACR auth, symlink `/Users/owenspace/.docker/cli-plugins` into that temp config before running buildx, otherwise Docker cannot find the `buildx` plugin.
  - Last pushed on 2026-05-07:
    - backend digest `sha256:82732586c707a9f0083fcc02191b16ed7b7345c8c0ad59988b65052ce7e00863`
    - frontend digest `sha256:a70521fa3f651bec5fe32e1eaf5c698e5587a2e5de84f1acfb9e4a00ac33b9be`
- ECS deployment `onechat.agentcici.com` / `x.agentcici.com`:
  - Host: `root@47.97.119.160`, key `/Volumes/AISpace/datafiles/ecs-key/cc-cici-ecs.pem`
  - Remote root: `/opt/cici`
  - Compose:
    - `/opt/cici/deploy/docker-compose.acr.yml`
    - `/opt/cici/deploy/docker-compose.acr.ssl.yml`
  - Env: `/opt/cici/deploy/acr.env` (`600`, not in repo)
  - Certs:
    - `/opt/cici/deploy/certs/agentcici.com.pem`
    - `/opt/cici/deploy/certs/agentcici.com.key`
  - Public verification:
    - `https://agentcici.com/` -> `200`, FEAT-027 Chinese suite website
    - `https://www.agentcici.com/` -> `200`, FEAT-027 Chinese suite website
    - `https://autoservice.agentcici.com/` -> `200`, product login surface
    - `POST /auth/password/login` fixed-password smoke -> `200`
  - Read-only connectivity check on 2026-05-13T12:09:23Z:
    - SSH with `/Volumes/AISpace/datafiles/cc-cici-ecs.pem` as `root@47.97.119.160` succeeded.
    - Docker is available and compose config under `/opt/cici` declares six services: `rabbitmq`, `redis`, `database`, `qdrant`, `backend`, and `frontend`.
    - Actual running/all container list showed only five services: `cici-frontend`, `cici-database`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant`; `cici-backend` was absent.
    - `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml ps -a backend` returned no backend row.
    - `docker exec cici-frontend nginx -t` failed because upstream host `backend` could not be resolved.
    - Server-local `curl --http1.1 -k -H "Host: agentcici.com" https://127.0.0.1/` returned the `AgentCiCi` static page, but public `https://agentcici.com/` from the workstation returned connection reset during this check.
  - Autoservice login 502 recovery on 2026-05-13T12:13:50Z:
    - Recovery command: `cd /opt/cici && docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d backend`.
    - `cici-backend` started from `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:V1.9`; startup logs showed Flyway schema version `48` up to date and Tomcat on `8080`.
    - Backend health: `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`.
    - Frontend Nginx: `docker exec cici-frontend nginx -t` -> success; then `docker exec cici-frontend nginx -s reload`.
    - Compose status: six services healthy (`cici-backend`, `cici-frontend`, `cici-database`, `cici-redis`, `cici-rabbitmq`, `cici-qdrant`).
    - Server-local Nginx smoke with `Host: autoservice.agentcici.com`: `/` -> `HTTP 200` title `AgentCiCi`; `POST /auth/password/login` -> `HTTP 200` with `success:true`.
  - Full local data sync on 2026-05-07:
    - Remote pre-sync backup directory: `/opt/cici/backups/20260507-123416-full-local-sync/`.
    - Backup contents include PostgreSQL dump, `acr.env.before-sync`, knowledge-base files tar, and Qdrant volume tar.
    - Local PostgreSQL business data restored to remote `cici-database` with `pg_restore --data-only`; remote Flyway schema history was preserved.
    - Post-restore SQL re-encrypted local Tavily and email secrets for the remote `APP_SECURITY_SECRET_KEY`, and rewrote `kb_document.storage_path` from the local workspace path to `/app/data/kb-files/...`.
    - Synced knowledge-base files into `cici-acr_cici_kb_files`; removed macOS `._*` resource fork files after extraction.
    - Rebuilt Qdrant `cici_kb_chunk` collection from local point export; remote `points_count=161`.
    - Verified row counts match local for key tables: `app_user=13`, `agent_definition=4`, `skill_definition=23`, `integration_app=3`, `model_provider_config=5`, `email_account=1`, `mcp_server=1`, `knowledge_base=2`, `kb_document=6`, `kb_chunk=310`, `chat_session=61`, `chat_message=556`, `user_quick_command=3`.
    - Public smoke after sync: `/agents=4`, `/skills=13`, `/integrations=3`, `/models/providers=5`, `/kb=2`, `/me/agents/run-logs=2`, Tavily stored-key test ok, `/api/platform/skills=11`, `/api/platform/tools=13`.
  - Nginx API proxy repair on 2026-05-07:
    - `deploy/nginx.cici.conf` and `deploy/nginx.cici.ssl.conf` must match API roots without requiring a trailing slash.
    - `/api/platform` and `/api/platform/*` must rewrite to backend `/platform` and `/platform/*`.
    - Management APIs now include `/admin/users` and `/admin/agents`; `/admin/agents/run-logs` powers the organization-level Agent observability view under `/admin/ops`.
    - FEAT-021 Agent Open API requires `/openapi/` to proxy to the backend with buffering disabled, long read timeout, and forwarded host/IP/proto headers for REST and future SSE calls.
    - FEAT-032 Embedded Apps requires `/embed/v1/` to proxy to the backend with buffering disabled and long read timeout; `/embed/*` without `/v1/` remains a frontend SPA route for the iframe page.
    - FEAT-023 WeCom customer service callback requires `/wecom` to proxy to the backend so `GET/POST /wecom/kf/callback` is reachable from Enterprise WeChat.
    - FEAT-023 WeCom customer service account configuration API requires `/admin/wecom` to proxy to the backend; `/admin/wecom/kf-accounts` stores CorpID, Token, encrypted Secret, encrypted EncodingAESKey, `open_kfid`, `agent_id`, and `run_as_user_id`.
    - Verified remotely with `docker exec cici-frontend nginx -t`, `nginx -s reload`, `GET /agents`, `GET /skills`, `GET /integrations`, `GET /models/providers`, `GET /api/platform/skills`, and `GET /api/platform/tools`.
  - Local admin observability restart on 2026-05-07T23:32:43Z:
    - Symptom: local `GET http://127.0.0.1:8080/admin/agents/run-logs?limit=10` returned JSON 404 while `http://127.0.0.1:5173/admin/ops` showed no run logs.
    - Cause: the Java process listening on `8080` was started before the admin run-log controller was available in the running classes.
    - Restart command:
      - `screen -dmS cici-backend /bin/zsh -lc 'cd /Volumes/AISpace/codehouse/cc-agentcici/backend && export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && exec mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local > /tmp/cici-backend.log 2>&1'`
    - Verified:
      - `GET http://127.0.0.1:8080/actuator/health` -> `{"status":"UP"}`
      - `GET http://127.0.0.1:8080/admin/agents/run-logs?limit=10` with org-admin token -> JSON with 10 rows.
      - `GET http://127.0.0.1:5173/admin/agents/run-logs?limit=10` through Vite proxy -> JSON with 10 rows.
      - Browser `http://127.0.0.1:5173/admin/ops` after login shows 26 log records and 3 real traces.
  - Operational note:
    - ACR infra tags were rebuilt as linux/amd64 because the ECS is x86_64 and previous infra tags were arm64-only.
  - V1.7 production release on 2026-05-08:
    - Release tag: `V1.7`.
    - Backup directory: `/opt/cici/backups/20260508-082523-before-v1.7`.
    - Backend image digest: `sha256:f2c73badec939387bd53a83ab1bc944d0b7a4a182337943aa7c8bbc7282aca35`.
    - Frontend image digest: `sha256:7adfa06e8d95046131d82d09c966e0a03035cac278c4af994df7e4e6a7093370`.
    - `CICI_IMAGE_TAG=V1.7` applies to all six compose services; infra images `cici-database`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant` required `V1.7` manifest aliases from their existing `latest` tags before compose could pull the full stack.
    - Verified remotely: six containers healthy, backend health `UP`, frontend `nginx -t` success, Flyway latest version `41|agent open api|t`, backend recent logs contained no `error|exception|failed|using dev fallback` matches.
    - Verified publicly on current domains: `https://agentcici.com/` and `https://www.agentcici.com/` serve the public website; `https://autoservice.agentcici.com/` serves the product login surface.
  - V1.7 AgentCiCi domain cutover on 2026-05-09:
    - Backup directory: `/opt/cici/backups/20260509-210523-before-agentcici-v1.7-domain-account`.
    - Backend image digest: `sha256:2336525817b5f8e43adc2f737510d5679a0d99607fc848c5a5e23ae4c8a6c2d4`.
    - Frontend image digest: `sha256:879dbd9c589c6387143ac1a04a9bf041b500cf90cbb8fe288659442a59b5cd49`.
    - Nginx SSL server names now declare `agentcici.com`, `www.agentcici.com`, and `autoservice.agentcici.com` as the AgentCiCi production hosts.
    - Production Flyway repair was required because the live database had historical V1/V8/V9 checksums from the pre-account-table schema. The repair updated checksums to the V1.7-resolved values, then V42-V46 migrated successfully.
    - Production account backfill created `user_account`, `account_login_identifier`, and `organization_member` from legacy `app_user`, preserving `app_user.id` as `organization_member.id` so historical member/user references remain stable.
    - Verified remotely: six containers healthy, backend health `UP`, frontend `nginx -t` success, Flyway latest version `46|organization purge worker lease|t`, account table counts `13/13/13`.
    - Verified publicly: `https://agentcici.com/` and `https://www.agentcici.com/` serve the Chinese AutoService website; `https://autoservice.agentcici.com/` serves the product login surface; fixed-password org-admin login smoke returned `demo-org`, member id, account id, and `ORG_ADMIN` / `PLATFORM_ADMIN` roles.
  - V1.8 suite website release on 2026-05-10:
    - Release tag: `V1.8`.
    - Release note: `综合官网`.
    - Source release completion fix on 2026-05-10T15:13:18Z: `git push origin HEAD:main` and `git push origin V1.8` completed after the initial ECS/ACR publish; remote `main` and `V1.8^{}` now both point to `1b2ea27c55660d094174a1544199157f8ba8321d`.
    - Backup directory: `/opt/cici/backups/20260510-213605-before-v1.8-suite-site`.
    - Backend image digest: `sha256:ad86b98c3f01fed15f5716da3c33a9344e7780488f8367c8f6dca704f5e12754`.
    - Frontend image digest: `sha256:7a08acd0ff945f13a66a780db1a5776fc9feac35013493258ed049b729c75f6f`.
    - Because `CICI_IMAGE_TAG` applies to all six compose services, `cici-database`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant` received `V1.8` manifest aliases from their existing `latest` tags before remote pull.
    - Route change: `agentcici.com` and `www.agentcici.com` root now render FEAT-027 `SuiteLanding siteOverride="china"`; `autoservice.agentcici.com` remains the product login surface; `/autoservice/cn` remains available as the AutoService product site.
    - Verified remotely: six containers healthy, backend health `UP`, frontend `nginx -t` success, Flyway latest version `47|account profile and password|true`.
    - Verified publicly with Playwright: `https://agentcici.com/` and `https://www.agentcici.com/` render the FEAT-027 Chinese suite website with title `AI 治理平台 | 企业 AI 客户运营套件`; `https://autoservice.agentcici.com/` renders the AgentCiCi product login surface and its reservation link points to `https://agentcici.com/#demo`.
    - Note: macOS `curl` with LibreSSL reset during external TLS handshake, but OpenSSL HTTP checks and Playwright browser navigation succeeded for the same public host.
  - V1.9 built-in AI meeting notes release on 2026-05-12:
    - Release tag: `V1.9`.
    - Release note: `内置AI听记`.
    - Git commit: `8f1f26b9dee3ce4d5249070348110ad591fce8e6`; annotated tag `V1.9` points to this commit and was pushed to GitHub.
    - Backup directory: `/opt/cici/backups/20260512-110444-before-v1.9-ai-meeting-notes`.
    - Backend image digest: `sha256:8b09586cb68c1314d85f341ebf27a3ccfe257ce6eb988c9357ffdee7b8d559e7`.
    - Frontend image digest: `sha256:fab76d0ab47d0dfee855dbdb7ff46b60653dbb7144cf5d2f72787049f6265a63`.
    - Because `CICI_IMAGE_TAG` applies to all six compose services, `cici-database`, `cici-redis`, `cici-rabbitmq`, and `cici-qdrant` received `V1.9` manifest aliases from their existing `latest` tags before remote pull.
    - Data sync: local non-KB PostgreSQL data restored over production after stopping backend/frontend; excluded `flyway_schema_history`, `knowledge_base`, `kb_document`, `kb_chunk`, `kb_document_metadata`, `kb_metadata_field`, `kb_retrieval_log`, and `agent_kb_binding`. KB files volume and Qdrant volume were backed up but intentionally not overwritten.
    - Post-restore secret handling: 4 imported encrypted fields were re-encrypted with the production `APP_SECURITY_SECRET_KEY`.
    - Restore note: local-only table entries `chat_attachment` and `user_quick_phrase` were present in the local dump but absent from production schema; `pg_restore` ignored those table entries while restoring existing tables.
    - Verified remotely: six containers healthy, backend health `UP`, frontend `nginx -t` success, Flyway latest version `48|file backed builtin skills|true`.
    - Verified data boundary: `knowledge_base=2`, `kb_document=6`, `kb_chunk=310`, and `agent_kb_binding=2` were unchanged after sync.
    - Verified API smoke: fixed-password org-admin login succeeded; `/agents=5`, `/skills=15`, `/integrations=4`, `/models/providers=5`, `/me/agents/run-logs=1`; platform-admin smoke returned `/platform/skills=13` and `/platform/tools=13`.
    - Verified publicly with Playwright: `https://agentcici.com/` title `AI 治理平台 | 企业 AI 客户运营套件`; `https://autoservice.agentcici.com/` title `AgentCiCi`.

## DEV Autopilot 研发身份凭据运维

- DEV Autopilot 研发身份凭据：悟空使用 `/opt/devautopilot/secrets/developer.env`，后羿使用 `/opt/devautopilot/secrets/developer-houyi.env`；两者必须保持 `root:root 0600`。任何轮换都应通过 AgentCiCi 受治理机器主体 API，且不得把 secret/OACT 写入日志或 Git。
- DEV Autopilot 生产显式 allowlist 为 `dev-autopilot-developer,dev-autopilot-developer-houyi`；应用准入不能替代 AgentCiCi active SERVICE/owner 与 Semattice 角色/PDP。

## Pending Verification

- Latest production release on 2026-07-03:
  - Release tag: `2.1.11`.
  - Git commit: `845a5fbaa2f2`.
  - Scope: TASK-167 RAG retrieval router policy hardening; RAG trace metadata now includes `ragTriggerReason`, `ragMatchedCategory`, `ragMatchedTerm`, and `ragPolicyVersion`.
  - Backup directory: `/opt/cici/backups/20260703-092849-before-2.1.11-rag-router-policy`.
  - Backend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-backend:2.1.11`, index digest `sha256:1cede18463ab90be0384e4cec8ea62e34058663eab5485a5c282b63ccd657fea`, linux/amd64 manifest digest `sha256:892cc2a4857cffc43f5d5943903b35d26da4b55e02c476e0b45f2cdefed2a99c`.
  - Frontend image: `op-registry.cloudcc.cn/cloudcc-ai-native/cici-frontend:2.1.11`, index digest `sha256:b71cded862207f41c56c8644234a225afd92f35a14d61d2a81144bffe4cbf88d`, linux/amd64 manifest digest `sha256:7bdbae26a44383d10c3162a4d8af07e0e6eebf0fb11c6f065f24a6f45fe56eb0`.
  - Verified remotely: six compose services healthy; backend `/actuator/health` returned `UP`; `/system/version` returned `version=2.1.11`, `imageTag=2.1.11`, `gitCommit=845a5fbaa2f2`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Verified publicly: `https://x.agentcici.com/` returned `200`; unauthenticated `/auth/me` returned expected `401`.

- Qdrant container + `scripts/verify-qdrant-stack.sh`; full app E2E with `app.kb.vector-store=qdrant` (default in `application-local.yml`).

## 2026-08-10 UAT `2.8.59-beta.5` - DevAutopilot PM Agent 发布补偿

- Git tag/commit：`2.8.59-beta.5 / 0edfc3567f854425305fdb0165ac3c600bddd5cd`。
- ACR index digest：backend `sha256:6690ad74814ee308c4a42ccb300031474c5eb9cf00c7952babd84b9e7d082216`；frontend `sha256:5068c400597ea2ca83faeb3ca9938884f8bf6c75b6ade6267480b1361b9668e6`。
- 发布前备份：`/data/apps/agentcici/backups/20260810T092900Z-before-2.8.59-beta.5`；Compose、UAT secrets、PostgreSQL dump、KB 与 Qdrant 备份均非空。
- UAT 主机未持有 ACR 登录态，使用精确 linux/amd64 镜像离线传输并加载；仅 force-recreate backend/frontend，PostgreSQL、Redis、RabbitMQ、Qdrant 容器 ID 哈希保持不变。
- 发布后：backend/frontend 与四个状态服务 healthy，`/actuator/health={"status":"UP"}`，`/system/version=2.8.59-beta.5 / 0edfc3567f85`，Nginx 配置有效。
- 既有租户业务资源不会随容器升级隐式变更。目标租户 PM Agent 仍未发布且无 `web` 渠道，必须由平台管理员调用 `/api/platform/tenants/org00000000000000001/applications/devautopilot/initializations`；不得直接写数据库。完成后应只读回读 `published_version_id`、`web` binding，并刷新员工首页验证可见性。

## 2026-08-10 UAT `2.8.59-beta.7` - 初始化就绪态修复

- Git tag/commit：`2.8.59-beta.7 / 7e309a39394d`；backend/frontend ACR index digest 分别为 `sha256:430408e61eca2056a442e36c6a754233824f6ba3153ab3eb908a2e39ba9c0ae8` 与 `sha256:96280e57c6da595969a2460b29216b0a4c176a0bd3b06b89ccc4f9bb837aadc1`。
- 发布前备份：`/data/apps/agentcici/backups/20260810T083412Z-before-2.8.59-beta.7`，Compose、UAT secrets、PostgreSQL、KB、Qdrant 六项均非空。
- 仅重建 backend/frontend；四个状态服务容器 ID 哈希发布前后均为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。两个应用容器 healthy，health `UP`，版本、镜像和 Git commit 一致，Nginx 配置有效。
- 首次 ACR 推送在鉴权连接处被远端重置且未创建 tag，原版本安全重试后成功。UAT 主机仍以离线精确 linux/amd64 镜像加载方式部署，不保存 ACR 凭据。
- 平台管理员真实补齐被“平台已选模型为 0”阻断。该环境配置必须通过平台模型治理页面补齐有效凭据、模型目录和 chat 路由；不得用数据库直写或无模型发布规避。

## 2026-08-10 UAT `2.8.59-beta.8` - OneKeyToken 已验证模型入目录

- Git tag/commit：`2.8.59-beta.8 / 8213646b4fa3`。
- ACR index digest：backend `sha256:fbed0dae9f1195cc69f5617c683226d8bba7a65910d612bf337339d8ccd4257e`；frontend `sha256:6d78c8cecf334152fea904a97fb1902c715e37d2f6fc40c07748897596c86df1`。linux/amd64 manifest digest 分别为 `sha256:4a6d840fc9f82356ef62de97669192cca7ed6f7b20c11a4ad20922b31e482fd8` 与 `sha256:badfa5067bd97f5f6342d5cf2d5e3d0e6b624eb0a64bc4568c3d4fdcb546e034`。
- 发布前备份：`/data/apps/agentcici/backups/20260810T084734Z-before-2.8.59-beta.8`；Compose、UAT secrets、PostgreSQL dump、KB 与 Qdrant 备份均非空。
- UAT 主机继续使用精确 amd64 镜像离线加载，仅重建 backend/frontend；PostgreSQL、Qdrant、RabbitMQ 容器 ID 哈希发布前后均为 `8868a49fbf84497e4da99ba3ef6af95b6ad206d507d5304d958cc5aec974cd77`。
- backend/frontend healthy，后端 health=`UP`，`/system/version` 回读 `2.8.59-beta.8 / 8213646b4fa3`，Nginx 配置有效。平台模型和租户初始化均通过受权 UI/API 完成，不保存或回读 API Key、Client Secret 或 OACT。

## 2026-08-10 UAT `2.8.59-beta.9` - OneKeyToken 自动路由语义修正

- Git tag/commit：`2.8.59-beta.9 / 534a3baff64e`。
- ACR index digest：backend `sha256:e829bfffbaacb958ec9479607c38b41d375dd0adea8d301ca0f31042980d7886`；frontend `sha256:470fa51d907bae73830ea0209a8dc932ee51992b804c110cad50d2226bb213e7`。linux/amd64 manifest digest 分别为 `sha256:06762fc79ea9374ffbe6653df1cb48593000c8269e4aac9c9f967ebc9df26189` 与 `sha256:8a59e0a37d2c732f221fe690474638894254a19cdd6633ce08f31432f2425980`。
- 发布前备份：`/data/apps/agentcici/backups/20260810T091307Z-before-2.8.59-beta.9`；Compose、UAT secrets、PostgreSQL dump、KB 与 Qdrant 备份均非空。
- 仅重建 backend/frontend；状态服务容器 ID 哈希发布前后均为 `8868a49fbf84497e4da99ba3ef6af95b6ad206d507d5304d958cc5aec974cd77`。两个应用容器 healthy，health=`UP`，版本/镜像/Git commit 一致，Nginx 配置有效。
- 受权平台 UI 已完成配置纠正；只读数据库回读 `selectedModels=[onekeytoken/auto]`，五个场景路由均为 OneKeyToken `onekeytoken/auto`，不存在 `qwen3.5-flash` 目录或固定路由。

## 2026-08-10 UAT `2.8.59-beta.12` - DevAutopilot 只读身份目录授权

- Git tag/commit：`2.8.59-beta.12 / b070676f411a`。ACR index digest：backend `sha256:1941ae7da12b821a01782336b4500da4d6b09ae61a5fbc77b9043db48e3b7087`；frontend `sha256:81d84e6592215b46a5032ce3b3eb4cbbf70f5d5444f2d8f5693b43f7777ed3fe`。
- 发布前备份：`/data/apps/agentcici/backups/20260810T103604Z-before-2.8.59-beta.12`，Compose、UAT secrets、PostgreSQL、KB 与 Qdrant 均非空。精确 amd64 镜像离线加载，仅重建 backend/frontend；四个状态服务 ID 哈希发布前后保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- backend/frontend healthy，容器网络 health=`UP`，版本/镜像/Git commit 一致，Nginx 有效，近期错误匹配为 0。DevAutopilot handoff 仅追加 `identity.principal.read`，不追加 `authorization.manage`；真实员工新 handoff 已验证目录读取和 active/suspended 状态。
