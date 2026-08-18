---
kind: feature-spec
feature_id: FEAT-191
title: 受治理的内部租户应用注册中心
status: implemented
owner_role: fullstack-agent
task_ids: TASK-313
related_decisions: DEC-055, DEC-021, DEC-023, DEC-054
related_issues: none
updated_at: 2026-08-18T02:05:50Z
updated_by: codex
---

# FEAT-191 - 受治理的内部租户应用注册中心

## 背景与目标

当前运营平台租户应用中心分别写死 AgentCiCi、Semattice 和 DevAutopilot 卡片、读取接口与生命周期动作。现有 `ecosystem_trusted_application` 只管理 Keycloak HUMAN API Client 准入，不能表达一个可安装产品的版本、依赖、初始化和租户生命周期。

本功能建立受治理的内部租户应用注册中心。内部应用先登记不可变 `app_code`，再创建版本化清单、依赖和受控初始化声明；只有通过验证并正式发布的版本才进入租户应用中心。运营人员为租户开通应用时，平台解析依赖、固定应用版本并沿持久化步骤执行生命周期操作。

第一期已交付目录、版本、依赖、发布门禁、动态租户应用读取和现有三类应用兼容投影。用户验收版本配置后确认：运营控制台应同时承担平台部署拓扑的受管配置职责。当前切片继续交付运行连接、连接修订、连通性验证、版本绑定、通用持久化初始化执行器和标准 Provider 生命周期回调；DevAutopilot 已验证的专用开通 Saga 保持原写路径，通过兼容适配进入动态读模型。

## 已确认产品原则

- 本机制仅面向平台受控的内部应用，不是公开市场或第三方任意自助发布入口。
- 强依赖默认要求先开通；自动联动开通必须由应用版本声明允许，并由运营人员确认影响计划。
- 初始化只允许平台白名单能力、已发布依赖能力和标准 Provider 生命周期回调，禁止脚本、SQL 或数据库直连。真实服务地址只能由平台超级管理员在受管运行连接中配置、验证和启用，不能作为自由 URL 写入应用版本步骤。
- 已发布应用版本不可修改；升级必须创建新版本和显式租户升级操作。
- 受信生态应用与租户应用目录保持独立，前者是 API Client 准入，后者是产品安装和生命周期控制面。

## 范围

### 第一期 In Scope

- 新增内部应用、版本和依赖控制面模型。
- 运营平台创建应用草稿、编辑治理字段、创建版本、验证和发布。
- 发布门禁校验应用代码、语义版本、清单 schema、初始化原语、依赖存在性、版本约束和依赖环。
- 应用版本只保存 `provider_binding_key`、`launch_route_key` 等逻辑引用；平台运行连接可保存真实 Origin/Host/端口和动作路径，但必须独立于不可变版本、按环境分区、版本化审计并经服务端验证后启用。
- 凭据只保存 Secret 或证书引用；禁止在运行连接、应用版本、前端持久化状态或审计中保存 Client Secret、Token、私钥原文。
- 平台管理员可创建运行连接草稿、测试连通性、验证生命周期契约、启用新修订、停用连接和回读历史测试状态。
- 通用生命周期执行器持久化租户操作和步骤，解析已启用连接修订，由平台后端调用 Provider；浏览器不直接跨域调用目标应用。
- 动态租户应用聚合 API 返回已发布目录、当前租户 activation、依赖状态和可执行动作。
- 租户应用中心改为动态渲染，保留 AgentCiCi、Semattice 和 DevAutopilot 的现有开通/暂停/恢复行为。
- 新增“应用中心”运营页面，支持扫描、筛选、创建草稿和查看版本/依赖。
- 新增后端、前端、迁移、鉴权和兼容回归。

### 后续切片

- OACT audience、mTLS Profile 和外部 Secret Manager 的生产适配；首期执行器支持无需鉴权、环境 Secret 引用的 Bearer 与 HMAC-SHA256。
- 应用版本升级、弃用、退役和租户批量影响分析。
- 高风险 scope 独立安全审批和自动开通依赖的显式计划确认。

### Out Of Scope

- 不修改 DevAutopilot、Semattice 或其他子仓源码。
- 不自动迁移或重新初始化现有 DevAutopilot 租户数据。
- 不允许运营人员录入可执行脚本、SQL、数据库连接串或长期凭据原文。
- 不提供删除租户业务数据的“卸载”动作。
- 不新增移动端布局、移动端截图或移动端自动化测试。
- 不发布生产；UAT 已由用户在实现完成后单独授权，并以未启用真实 Provider 连接的源码候选发布。

## 控制面模型

### `internal_application`

- `app_code`：不可变稳定标识，格式 `^[a-z][a-z0-9-]{1,63}$`。
- `display_name`、`summary`、`icon_key`、`owner_team`：目录展示与责任人。
- `tenant_mode`：首期支持 `PLATFORM_BASE`、`SHARED_RUNTIME_TENANT_ISOLATED`。
- `catalog_status`：`DRAFT/PUBLISHED/SUSPENDED/RETIRED`。
- `trusted_app_code`：可选关联受信生态应用，只表达运行 Client 关联，不合并生命周期。
- `launch_mode`：`NONE/PLATFORM_ROUTE/SERVER_HANDOFF`。
- `launch_route_key`：后端可解析的逻辑路由键，不保存真实地址。

### `internal_application_version`

- `app_code + version` 唯一。
- `manifest_schema_version` 首期固定 `tenant-application/v1`。
- `manifest_json` 保存受校验声明，不允许域名、Secret、脚本或未知执行原语。
- `manifest_digest` 保存规范化内容 SHA-256。
- `version_status`：`DRAFT/VALIDATED/PUBLISHED/DEPRECATED/REVOKED`。
- 已发布记录不可原地更新。

### `internal_application_dependency`

- `dependency_app_code`、`version_constraint`。
- `dependency_type`：`REQUIRED_ACTIVATION/REQUIRED_RUNTIME/OPTIONAL`。
- `activation_policy`：`REQUIRE_EXISTING/AUTO_PROVISION_ALLOWED`。
- 同一版本的依赖图发布前必须无环。

### `internal_application_provider_connection`

- `binding_key`：应用范围内稳定逻辑键；应用版本仅引用该键。
- `environment_key`、`network_scope`：区分运行环境和 `PUBLIC_HTTPS/PLATFORM_INTERNAL` 网络边界。
- 已建立连接的 `environment_key` 与 `network_scope` 不允许被新草稿修订提前改写；需要改变部署边界时必须创建新的 `binding_key`，再由应用新版本显式绑定。
- `status`：`DRAFT/ACTIVE/DISABLED`；同一连接只有一个活动修订。
- 连接本身不保存可复用凭据，只保存 `secret_ref`、`tls_profile_ref` 等运行时引用。

### `internal_application_provider_connection_revision`

- 保存 Base URL、健康检查路径、五类生命周期动作路径、契约版本、鉴权类型、超时和重试策略。
- 修订不可原地覆盖；编辑产生新草稿修订，测试成功后才能原子切换为活动修订。
- 每次测试保存状态、时间、HTTP 状态、延迟和安全错误代码；不保存响应正文或远端内部异常。

### `tenant_application_operation` / `tenant_application_operation_step`

- 操作以 `(company_id, app_code, operation_type, idempotency_key)` 幂等。
- 固定目标应用版本和实际解析到的连接修订，记录 `PENDING/RUNNING/SUCCEEDED/FAILED`、请求摘要和安全错误码。
- Provider 回调步骤保存尝试次数、开始/完成时间和响应摘要；失败可用同一幂等键安全回读，不伪造成功。
- `ACTIVATE` 成功后写入通用 `tenant_application_activation` 投影；`SUSPEND/RESUME/RECONCILE` 只改变本租户应用生命周期，不删除业务数据。

### 既有 activation

保留 `tenant_application_activation` 与 `tenant_application_resource`。第一期不改变 DevAutopilot 数据含义；聚合读取按 `(company_id, app_code)` 连接目录，并为 AgentCiCi 基础应用与 Semattice provisioning 生成兼容投影。

## 应用版本声明

首期表单不暴露自由 JSON 编辑器。后端接收结构化字段并生成规范化清单：

```json
{
  "schemaVersion": "tenant-application/v1",
  "providerBindingKey": "devautopilot.lifecycle",
  "initializationEngine": "SAGA_V1",
  "steps": [
    {"code": "metadata", "type": "DEPENDENCY_CAPABILITY", "capability": "semattice.template.apply", "contractVersion": "v1"},
    {"code": "product-manager", "type": "PLATFORM_CAPABILITY", "capability": "agent.blueprint.ensure", "contractVersion": "v1"},
    {"code": "activation", "type": "PROVIDER_CALLBACK", "capability": "tenant.activate", "contractVersion": "v1"}
  ]
}
```

首期允许的 `type` 只有 `PLATFORM_CAPABILITY`、`DEPENDENCY_CAPABILITY`、`PROVIDER_CALLBACK`。`capability`、`contractVersion` 和 `providerBindingKey` 只能使用受限标识符，不得是 URL 或文件路径；真实地址由 `providerBindingKey` 解析到当前环境的活动运行连接修订。

## 状态与发布门禁

应用目录：

```text
DRAFT → PUBLISHED → SUSPENDED → PUBLISHED
                  ↘ RETIRED
```

版本：

```text
DRAFT → VALIDATED → PUBLISHED → DEPRECATED
                         ↘ REVOKED
```

发布必须同时满足：

1. 应用治理字段完整，应用版本没有环境域名或凭据原文。
2. 版本清单 schema、步骤类型和标识符合法。
3. 所有依赖应用存在，版本约束可由其已发布版本满足。
4. 依赖图无环，应用不能依赖自身。
5. 包含 Provider 回调的版本必须引用当前应用已启用、测试成功且契约兼容的运行连接。
6. 一个应用同一时刻只有一个目录默认发布版本。
7. 发布审计记录操作者、版本、清单摘要、依赖摘要和连接键；不记录真实地址或凭据。

## 动态租户应用读模型

`GET /platform/tenants/{companyId}/applications` 返回：

- 应用目录信息与默认发布版本；
- `actualState/desiredState/healthState`；
- 当前租户固定的 installed version；
- 依赖清单、当前依赖状态与是否阻断；
- 初始化阶段、失败步骤、安全错误码和尝试次数；
- 可执行动作 `ACTIVATE/CONTINUE/RECONCILE/SUSPEND/RESUME/OPEN`；
- 产品专属 `managementRoute`，不返回环境 Origin。

兼容投影：

- AgentCiCi：租户基础应用，租户 ACTIVE 时为 `ACTIVE`。
- Semattice：读取既有 provisioning binding。
- DevAutopilot：读取既有 activation、初始化就绪与资源摘要。

## 前端信息架构

- “能力治理”新增“应用中心”，页面负责搜索、状态筛选、创建草稿和进入应用详情。
- 创建使用显式 modal，只录入基础治理字段；版本、依赖、验证和发布进入独立详情路由。
- 应用详情提供“版本与依赖 / 运行连接”两个受管工作区。运行连接支持真实地址、动作路径、鉴权引用、超时重试、测试、启用、停用和测试结果回读。
- 版本弹窗从当前应用的已启用运行连接中选择 Provider，不再自由输入逻辑键；依赖从已发布应用中选择，排除自身并展示默认版本。
- 租户应用中心改为动态列表。应用卡保留紧凑事实行和 1px 结构线，不嵌套卡片、不使用营销式 hero。
- 依赖阻断、初始化失败、版本和动作由 API 决定；页面不再用固定常量计算应用数量。
- DevAutopilot 专属“校准历史受理”等维护动作保留在其专属详情，不进入通用卡片协议。

## 安全与审计

- 目录写操作要求 `PLATFORM_ADMIN`；租户开通/暂停继续允许现有 `PLATFORM_ADMIN/PLATFORM_OPERATOR` 边界。
- `manifest_json` 按字段白名单构建，并执行域名、URL、Secret 名称、脚本语法和未知步骤扫描。
- 真实地址只进入平台部署拓扑表；前端制品不得硬编码环境地址，浏览器只能通过同源平台 API 配置、测试和读取受权结果。
- 服务端校验 URL scheme、Host、网络范围、元数据地址、动作相对路径、超时和重试上限；`PUBLIC_HTTPS` 必须使用 HTTPS，`PLATFORM_INTERNAL` 才允许内部 HTTP。
- Secret 通过运行环境映射解析；API 只接受和返回 `secret_ref`，不接受、回显或审计 Secret 原文。
- 平台只保存外部资源 ID、版本和摘要，不复制提供方业务数据。
- 所有写操作写入 `PlatformAuditService`；审计不含 Token、Secret、请求正文或远端内部错误。

## 兼容与迁移

- V120 创建目录、版本和依赖表，并以幂等 SQL seed AgentCiCi、Semattice、DevAutopilot 及其首个发布版本。
- 现有 `tenant_application_activation` 不重写、不补造操作记录。
- 现有 DevAutopilot 专用 API 保持可用；动态聚合 API 调用同一服务读取状态。
- 前端先切换动态读模型，生命周期写操作继续分发到现有专用接口。
- 只有第二个新应用可以完全通过目录和标准回调开通后，才允许废弃 DevAutopilot 专用编排代码。

## 验收标准

1. 平台管理员可以创建应用草稿、运行连接、版本和依赖，验证后发布；运营角色不能修改目录或连接。
2. 草稿、无有效发布版本和暂停目录的应用不作为可开通应用展示。
3. 应用版本中的 URL/域名/Secret、非法连接地址、未测试连接、缺失依赖、版本不满足和依赖环全部失败关闭。
4. 租户应用中心从聚合 API 动态显示三类既有应用，状态与现有专用接口一致。
5. DevAutopilot 的开通、继续、同步、暂停和恢复保持原行为；本切片不重新初始化既有租户。
6. 页面覆盖加载、空目录、连接草稿/测试/启用、依赖选择与阻断、初始化失败、运行中、暂停和错误反馈。
7. 新应用通过已启用连接完成至少一次真实 Provider `ACTIVATE` 回调，操作、步骤、连接修订和通用 activation 可回读；失败不写 ACTIVE。
8. 后端定向测试、生产 package、前端全量测试和 production build 通过。
9. 从本地 `main` 构建受影响服务并更新 `https://cici.localhost/`；回读路由、容器健康、重启次数和版本指纹。
10. 受权平台管理员完成真实桌面端目录和租户应用中心截图与关键交互验收；无授权会话时明确记录待验收，不能伪造成功。

## 回滚

- 回滚应用代码时保留 V120 目录数据；旧代码忽略新增表，现有三应用专用读取与写入仍可工作。
- 动态页面故障时可回滚前端到固定卡片版本，不修改任何 activation 或提供方资源。
- 目录版本发布不自动改动已开通租户；回滚默认版本只影响后续新开通计划。
- 不通过删除目录、activation 或提供方业务数据完成回滚。
- UAT `2.8.61-beta.28 / 242074e72a9e` 已完成技术发布，V121、页面路由、匿名连接 API JSON 401、六容器健康与稳定日志通过；本轮未创建或启用真实 Provider 连接，授权态连接与 ACTIVATE 仍待业务验收。
