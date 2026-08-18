---
title: AgentCiCi 内部应用接入指南
document_id: agentcici.internal-application-integration.v1
document_version: 1.0.0
contract: tenant-application/v1
provider_contract: v1
audience:
  - application-developer
  - platform-operator
format: agent-readable-markdown
canonical_html: /platform/internal-applications/integration-guide
last_updated: 2026-08-18
---

# AgentCiCi 内部应用接入指南

本文档是供开发者和智能体读取的纯 Markdown 接入手册。它不依赖 JavaScript，不包含真实环境地址、租户数据或 Secret。涉及应用、运行连接、版本和租户状态的写操作，必须由具有 `PLATFORM_ADMIN` 权限的平台管理员在运营控制台中完成。

## 快速事实

- 应用版本保存稳定、跨环境一致的逻辑声明。
- 运行连接保存当前环境的 Provider 地址、路径、鉴权引用、超时和重试策略。
- 浏览器只调用 AgentCiCi 同源 API；Provider 请求由 AgentCiCi 后端发起。
- 平台只保存 Secret 引用，不能在表单、应用版本、日志或本文档中保存 Secret 原文。
- Provider 生命周期调用必须幂等，平台会固定应用版本和运行连接修订并保留 operation/step 审计。
- 已发布版本不可修改；运行连接修改产生新修订，不覆盖旧修订。

## 接入流程

1. [接入全景](#01-接入全景)
2. [接入前准备](#02-接入前准备)
3. [登记应用](#03-登记应用)
4. [Provider 契约](#04-provider-契约)
5. [鉴权与 Secret](#05-鉴权与-secret)
6. [运行连接](#06-运行连接)
7. [版本与初始化](#07-版本与初始化)
8. [应用依赖](#08-应用依赖)
9. [验证与发布](#09-验证与发布)
10. [租户开通](#10-租户开通)
11. [运行期运维](#11-运行期运维)
12. [排错与检查](#12-排错与检查)

## 01 接入全景

应用中心管理“某个应用如何被租户安全开通”，不是服务部署工具。

职责分工：

- 应用开发者：实现健康检查和生命周期 Provider，保证幂等、鉴权、租户隔离和可观测性。
- 平台管理员：登记应用，配置并启用运行连接，创建版本、声明依赖并发布。
- AgentCiCi：解析固定版本和连接修订，执行 Provider 回调，记录 operation/step 审计，成功后更新租户应用状态。

调用方向：

```text
浏览器 -> AgentCiCi 同源平台 API -> Provider
```

浏览器不会直接跨域调用 Provider，也不会持有 Provider Secret。

## 02 接入前准备

开始配置前确认：

- 应用代码稳定：小写字母开头，仅含小写字母、数字和连字符，长度 2–64。
- 已确定责任团队、告警联系人和 Provider 负责人。
- 应用资源以请求中的 `companyId` 作为租户隔离边界。
- 已确定是否提供平台内逻辑入口；入口只登记逻辑路由键，不登记环境域名。
- 已列出强依赖、可选依赖和最低版本。
- 初始化逻辑可以安全重试；相同幂等键不会创建重复资源。
- 已定义暂停、恢复、校准、升级和回滚语义。

完成标志：应用代码、责任团队、租户模型、依赖清单和 Provider 负责人均已确认。

## 03 登记应用

平台操作：

1. 进入“能力治理 -> 应用中心”，点击“登记应用”。
2. 填写 `appCode`、名称、简介、责任团队和租户模式。
3. 共享运行时但租户资源隔离的应用通常选择 `SHARED_RUNTIME_TENANT_ISOLATED`。
4. 只有应用提供受管入口时才配置入口方式和逻辑入口。
5. 不要在应用名称、简介或逻辑入口中填写服务地址。

登记应用只创建目录草稿，不会发布版本、调用 Provider 或为租户创建资源。

完成标志：应用详情显示“草稿”，应用代码、责任团队和租户模式正确。

## 04 Provider 契约

Provider 是应用向 AgentCiCi 暴露的服务端生命周期适配层。至少实现健康检查和开通接口；支持运行期治理时，再实现校准、暂停、恢复和升级。

### 动作语义

| operationType | 应用侧语义 | 成功状态 |
| --- | --- | --- |
| `ACTIVATE` | 创建或复用该租户的应用资源 | `ACTIVE` 或 `SUCCEEDED` |
| `RECONCILE` | 校准缺失或漂移的受管资源 | `ACTIVE` 或 `SUCCEEDED` |
| `SUSPEND` | 停止租户入口或执行能力，不删除数据 | `SUSPENDED` 或 `SUCCEEDED` |
| `RESUME` | 恢复既有租户资源 | `ACTIVE` 或 `SUCCEEDED` |
| `UPGRADE` | 将租户资源迁移到目标应用版本 | `ACTIVE` 或 `SUCCEEDED` |

### 健康检查响应

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "UP",
  "contractVersion": "v1"
}
```

### 生命周期请求体

```json
{
  "operationId": "8bf2d8aa-9ec4-4f54-9462-4b3d5f55d3fd",
  "idempotencyKey": "tenant-onboarding-20260818-001",
  "operationType": "ACTIVATE",
  "companyId": "org-example-001",
  "appCode": "sales-workbench",
  "applicationVersion": "1.0.0",
  "contractVersion": "v1",
  "dependencies": [
    {
      "appCode": "semattice",
      "versionConstraint": ">=1.0.0",
      "dependencyType": "REQUIRED_RUNTIME"
    }
  ],
  "stepCode": "tenant-bootstrap",
  "capability": "tenant.activate"
}
```

关键请求头：

```text
Idempotency-Key: <operationId>:<stepCode>
X-Correlation-Id: <operationId>
Content-Type: application/json
```

### 生命周期成功响应

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "ACTIVE",
  "resourceId": "tenant-resource-001",
  "providerRevision": "42"
}
```

Provider 响应必须是 JSON，`status` 只接受 `SUCCEEDED`、`ACTIVE` 或 `SUSPENDED`，响应体不得超过 1 MiB。非 2xx、无效 JSON 或其他状态会使步骤失败。

幂等要求：

- 业务幂等使用请求体的 `idempotencyKey`。
- 单步骤重试使用请求头 `Idempotency-Key: operationId:stepCode`。
- Provider 应持久化处理结果，并对重复请求返回同一业务结果。
- 相同幂等键但业务载荷冲突时应失败关闭，不得创建第二份资源。

完成标志：使用固定测试租户重复调用两次开通接口，不产生重复资源，并可按 `operationId` 查询完整日志。

## 05 鉴权与 Secret

支持的鉴权类型：

- `NONE`：仅适用于受强网络边界保护且已完成安全评审的内部端点。
- `BEARER_SECRET_REF`：AgentCiCi 将运行环境解析出的 Secret 作为 Bearer Token 发送。
- `HMAC_SHA256_SECRET_REF`：AgentCiCi 发送服务、应用、时间戳、随机数和签名头；生产级接入优先使用。

HMAC canonical string 的字段顺序固定：

```text
agentcici
POST
/internal/tenant-lifecycle/v1/activations
1787018400
4f913f26d34b452cb68d12310d76b7c9
<SHA256_HEX_OF_REQUEST_BODY>
```

计算规则：

1. 对原始 HTTP request body 计算 SHA-256，并输出小写十六进制。
2. 按上述顺序使用换行符连接字段。
3. 使用共享 Secret 对 canonical string 计算 HMAC-SHA256。
4. 输出小写十六进制签名。

相关请求头：

```text
X-Internal-Service: agentcici
X-Internal-App: <appCode>
X-Internal-Timestamp: <unix-seconds>
X-Internal-Nonce: <random-nonce>
X-Internal-Signature: <lowercase-hmac-sha256-hex>
```

Provider 必须校验时间窗、Nonce 防重放、应用代码和签名，并使用恒定时间比较签名。

运行连接表单只填写引用名，例如 `sales-workbench-hmac`。运维在目标环境中配置对应的 `app.platform.provider-secrets.<secret_ref>`。真实值不得进入应用版本、前端、审计、日志、截图或本文档。

完成标志：过期时间戳、重复 Nonce、错误应用代码和错误签名均被拒绝；正确签名可以通过。

## 06 运行连接

运行连接是部署拓扑控制面。真实 Base URL 可以由平台管理员在这里进行受控配置，但不会进入应用版本；版本只引用稳定的 `bindingKey`。

操作步骤：

1. 在应用详情点击“新建连接”。
2. 绑定键建议使用 `<app-code>.lifecycle`。
3. 选择网络范围：
   - `PUBLIC_HTTPS` 强制 HTTPS，且地址不能解析到私网、回环、链路本地或云元数据地址。
   - `PLATFORM_INTERNAL` 才允许平台内部可达的 HTTP 服务。
4. 填写 Base URL 和相对路径。路径必须以单个 `/` 开头，不能包含域名、查询串、片段或 `..`。
5. 配置 1–60 秒超时和 1–5 次最大尝试次数。
6. 选择鉴权类型并填写 Secret 引用，而不是 Secret 原文。
7. 点击“测试连接”。平台后端会解析地址、应用鉴权并请求健康接口。
8. 只有测试通过的修订才能启用。启用后该修订成为活动修订。

脱敏示例：

```text
bindingKey      sales-workbench.lifecycle
networkScope    PUBLIC_HTTPS
baseUrl         https://provider.example.test
healthPath      /internal/tenant-lifecycle/v1/health
activatePath    /internal/tenant-lifecycle/v1/activations
reconcilePath   /internal/tenant-lifecycle/v1/reconciliations
suspendPath     /internal/tenant-lifecycle/v1/suspensions
resumePath      /internal/tenant-lifecycle/v1/resumptions
upgradePath     /internal/tenant-lifecycle/v1/upgrades
```

修改地址、路径、鉴权引用、超时或重试策略会创建新修订，不覆盖旧修订。改变环境键或网络范围时必须创建新的 `bindingKey`。

完成标志：连接为“已启用”，活动修订号正确，最新测试为“通过”。

## 07 版本与初始化

应用版本描述所有环境都相同的能力：语义版本、Provider 连接逻辑键、初始化步骤和依赖。包含 Provider 回调时选择 `SAGA_V1`，并从已启用连接中选择 Provider。

```text
version             1.0.0
initializationEngine SAGA_V1
providerBindingKey   sales-workbench.lifecycle

stepCode             tenant-bootstrap
stepType             PROVIDER_CALLBACK
capability           tenant.activate
contractVersion      v1
```

字段含义：

- `stepCode`：版本内唯一的稳定标识，进入步骤审计和幂等请求头。
- `stepType`：新应用的通用执行器使用 `PROVIDER_CALLBACK`。
- `capability`：受限逻辑标识，例如 `tenant.activate`；不能填写 URL、脚本或文件路径。
- `contractVersion`：必须与活动连接修订的契约版本一致。

完成标志：版本草稿显示正确的 Provider 绑定、`SAGA_V1` 和初始化步骤。

## 08 应用依赖

依赖必须从已发布应用中选择。平台在版本验证时检查版本约束和依赖环，在租户开通时检查强依赖的实际运行状态。

依赖类型：

| dependencyType | 含义 | 适用场景 |
| --- | --- | --- |
| `REQUIRED_ACTIVATION` | 开通过程必须依赖 | 初始化需要依赖资源 |
| `REQUIRED_RUNTIME` | 应用运行期持续依赖 | 数据、授权或执行底座 |
| `OPTIONAL` | 缺失不阻断开通 | 只影响增强能力 |

开通策略：

- `REQUIRE_EXISTING`：要求租户已经开通依赖，影响最清晰，优先使用。
- `AUTO_PROVISION_ALLOWED`：声明允许编排器自动联动，但仍需运营人员明确确认影响计划。

版本约束支持：

- 精确版本：`1.2.3`
- 显式等于：`=1.2.3`
- 最低版本：`>=1.2.3`
- 任意版本：`*`

应用不能依赖自身，同一依赖不能重复声明，依赖图必须无环。

完成标志：每个强依赖都有明确类型、版本约束和开通策略，依赖图无环。

## 09 验证与发布

1. 点击“验证”。平台检查清单 schema、标识符、Provider 连接、契约版本、依赖版本和依赖环。
2. 验证通过后版本进入“已验证”。
3. 明确确认并发布。发布后版本不可修改，并成为新租户使用的默认版本。
4. 首个版本发布后应用目录进入“已发布”，租户应用中心才会显示该应用。

发布只开放目录和固定默认版本，不会调用 Provider，不会为任何租户创建资源，也不会静默升级已有租户。

完成标志：版本和应用目录均为“已发布”，默认版本号正确。

## 10 租户开通

进入“租户目录 -> 测试租户 -> 应用中心”，核对依赖状态后执行开通。

执行顺序：

1. 解析应用目录和默认发布版本，并固定版本。
2. 检查强依赖；不满足时失败关闭。
3. 固定当前活动运行连接修订。
4. 按初始化步骤调用 Provider。
5. 全部步骤成功后才将租户应用投影更新为 `ACTIVE`。

联调时同时观察：

- Provider 按 `X-Correlation-Id` 检索的日志。
- AgentCiCi operation/step 的状态、尝试次数、固定版本和连接修订。
- 租户应用卡片的实际状态。

完成标志：测试租户为 `ACTIVE`；Provider 资源唯一；operation 和全部 step 为 `SUCCEEDED`；重复提交相同幂等请求不会重复创建资源。

## 11 运行期运维

- `SUSPEND`：暂停租户应用入口或执行能力，不删除 Provider 业务数据；成功后状态为 `SUSPENDED`。
- `RESUME`：恢复同一租户的既有资源，不能重新创建一套资源。
- `RECONCILE`：安全补齐受管资源漂移，不覆盖租户不受管数据。
- `UPGRADE`：先发布新版本，再对目标租户显式升级；Provider 根据目标版本执行可重试迁移。

修改运行连接时，创建并测试新修订，再切换活动修订。已有 operation 继续使用其固定修订，不会在重试中切换地址或鉴权。

完成标志：每个支持的动作都有幂等实现、审计记录、负向测试和回滚方案。

## 12 排错与检查

### 常见错误

`PROVIDER_HEALTH_ADDRESS_REJECTED`

- 检查 `PUBLIC_HTTPS` 是否使用 HTTPS。
- 检查 DNS 是否解析到私网、回环、链路本地或元数据地址。
- 检查路径是否为安全相对路径。

`PROVIDER_HEALTH_SECRET_UNAVAILABLE`

- 确认表单填写的是引用名。
- 由运维确认当前环境存在 `app.platform.provider-secrets.<secret_ref>` 对应配置。
- 不要在表单或日志中粘贴 Secret 原文。

`PROVIDER_TIMEOUT` 或 `PROVIDER_UNREACHABLE`

- 从 AgentCiCi 后端所在网络检查 DNS、路由、端口和防火墙。
- 浏览器能访问不能证明平台后端能访问。

`PROVIDER_REJECTED`

- 确保返回 2xx JSON。
- 确保 `status` 是 `SUCCEEDED`、`ACTIVE` 或 `SUSPENDED`。
- 业务失败应返回非 2xx，并保留 Provider 内部关联号用于排错。

版本验证提示连接或依赖不满足

- 确认连接已测试并启用。
- 确认版本契约与活动连接修订一致。
- 确认依赖应用存在满足约束的已发布版本。
- 排除自依赖、重复依赖和依赖环。

### 发布前检查

- [ ] 健康检查从 AgentCiCi 后端所在网络可达。
- [ ] 鉴权正向与负向用例通过。
- [ ] 重复请求不产生重复资源。
- [ ] 租户数据按 `companyId` 隔离。
- [ ] 暂停不会删除业务数据。
- [ ] 依赖和版本约束已确认。
- [ ] 日志可以按 `operationId` 检索。
- [ ] 失败路径和回滚方案已演练。

## 安全边界

- 本文档可以公开读取，但不授予任何平台操作权限。
- 不向本文档、URL 参数、应用版本、前端或日志写入 Token、密钥、私钥或 Secret 原文。
- 示例地址使用 IANA 保留测试域名 `provider.example.test`，不能直接用于真实环境。
- 平台管理员仍需在目标环境内完成真实连接测试、启用、版本发布和测试租户开通验收。
