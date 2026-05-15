---
kind: feature-spec
feature_id: FEAT-032
title: Meeting Minutes Embed SDK
status: implementation
owner_role: fullstack-crm-embed
task_ids: TASK-090, TASK-091, TASK-092, TASK-093, TASK-094, TASK-095, TASK-096
related_decisions: FEAT-021, FEAT-029
related_issues: none
updated_at: 2026-05-14T09:42:30Z
updated_by: ai
---

# FEAT-032 - 会议纪要嵌入式 JS SDK 与 iframe 内核

## 背景与目标

当前 FEAT-029 已经在 AgentCiCi 助手工作台内实现“开始会议纪要”：浏览器麦克风录音、讯飞实时转写、说话人分离、AI 听记技能生成结构化纪要，并补充了客户拜访纪要、待办任务候选和 CRM 记录建议语义。

真实 CRM 使用场景中，会议纪要不应只存在于 AgentCiCi 工作台，而应嵌入到 CloudCC、Salesforce 或企业自研 CRM 的客户、线索、商机、活动、拜访任务等业务页面中。用户在 CRM 记录页点击“开始会议纪要”，右侧划出会议纪要工作面板，会议结束后将纪要、待办和 CRM 字段建议写回当前业务记录。

本功能目标是设计一套 **框架无关 JS SDK + iframe 内核**，并在 admin 管理端建立可复用的 **嵌入式智能应用** 管理入口：

- CloudCC 当前前端为 Vue 架构，SDK 必须能被 Vue 页面直接调用。
- SDK 不绑定 React、Vue 或 CloudCC 组件库，浏览器全局脚本即可运行。
- SDK 第一版内部创建 iframe，复杂会议纪要 UI 在 AgentCiCi embed 页面内运行。
- iframe 内核复用 FEAT-029 的 ASR、speaker、summary 和 `ai-meeting-notetaker` 运行时能力。
- CRM 页面只负责触发、传入上下文、接收结果事件和承载抽屉。
- 长期凭证不进入浏览器，SDK 只接收服务端签发的短期 `embedToken`。
- 组织管理员在 `/admin` 通过“嵌入式智能应用”菜单查看所有可嵌入外部系统的标准能力，统一管理启用状态、授权、接入说明、调试和调用日志。

## 范围

### In Scope

- 新增公开浏览器 SDK：`/sdk/meeting-minutes.js`。
- 新增嵌入式页面：`/embed/meeting-minutes`。
- 新增 admin 管理端“嵌入式智能应用”菜单，首个嵌入式智能应用为“会议纪要”。
- 嵌入式智能应用列表展示所有可嵌入应用的名称、状态、适用系统、接入方式、权限范围、最近调用和版本。
- 嵌入式智能应用详情页提供配置说明、SDK 地址、iframe 地址、token 签发接口、示例代码、允许域名、权限 scope、调试面板和调用日志入口。
- 新增短期 embed token 签发与校验能力。
- 新增 CRM 上下文模型，用于传递来源系统、对象类型、对象 ID、客户、联系人、商机、活动、参与人等信息。
- SDK 支持两种挂载模式：
  - `mode: "drawer"`：SDK 创建右侧抽屉和 iframe。
  - `mode: "inline"`：SDK 将 iframe 挂载到调用方传入的容器内。
- SDK 与 iframe 通过 `postMessage` 通信，双方都校验 origin。
- iframe 内核支持会议开始、实时转写、发言人编辑、结束生成纪要、CRM 写回候选预览。
- 新增写回确认接口，将纪要、待办、备注、字段建议写回 CloudCC CRM。
- 提供 CloudCC Vue 接入示例和通用原生 JS 接入示例。
- 产品 UI 继续遵守 `鎏金账房` product register：紧凑、暖象牙表面、墨色文字、香槟金结构线，避免营销化和装饰化。

### Out Of Scope

- 第一版不做 npm 包、ESM 构建、TypeScript 类型包发布到公有 registry。
- 第一版不直接渲染复杂 UI 到 CloudCC DOM，不与 CloudCC Vue 组件树共享状态。
- 第一版不支持离线录音文件上传、完整音频存储、多人协同编辑。
- 第一版不承诺在 iframe 外部暴露底层 ASR 流。
- 第一版不自动静默写回 CRM，必须由用户确认。
- 第一版不做 Salesforce 专属 UI 适配，只保留通用 CRM 上下文和后续扩展点。
- 第一版不改变 FEAT-029 已有助手工作台入口。

## 用户场景

- 销售在 CloudCC 商机详情页点击“开始会议纪要”，右侧打开 AgentCiCi 听记抽屉，会议结束后生成“本次沟通重点、客户异议、下一步行动、CRM 记录建议”。
- 客户成功经理在客户详情页开启会议纪要，AI 自动识别跟进事项，用户确认后创建 CloudCC 任务并把纪要写入活动记录。
- 售后负责人在服务工单页记录客户回访，会议纪要生成后把客户问题摘要、承诺事项和风险提示写回工单备注。
- 管理员希望同一套 SDK 后续也能嵌入自研 CRM 页面，只要后端能签发短期 embed token 并传入标准上下文。
- 组织管理员进入 `/admin` 的“嵌入式智能应用”菜单，看到“会议纪要”作为嵌入式智能应用，复制 CloudCC Vue 示例代码，配置允许域名，点击“调试”生成测试 token 并在沙箱面板中预览 iframe。
- 后续新增“客户摘要助手”“售后工单助手”“商机跟进建议”等可嵌入功能时，管理员仍从同一个“嵌入式智能应用”菜单进入，不需要到多个业务页面分别找配置。

## 现状与约束

### Verified Facts

- FEAT-029 已提供实时会议纪要基础能力：`/ws/asr`、讯飞 provider、speaker diarization、`POST /ai/meeting-minutes/summary`、`ai-meeting-notetaker`。
- FEAT-029 的 AI 听记标准技能已同步 CloudCCAI 听记语义，覆盖客户拜访会议纪要、待办任务候选、CRM 线索/商机/联系人建议和人工确认规则。
- FEAT-021 已设计并实现 Agent Open API 的 API Key、run-as、external user、调用日志和 trace 思路，可作为 embed token 安全模型的参考，但 embed token 不能复用长期 API Key。
- AgentCiCi 产品默认 register 为 `product`，嵌入页仍属于任务型产品界面。
- CloudCC 前端是 Vue 架构，因此 SDK 必须框架无关，不能假设 React runtime 存在。

### Constraints

- 浏览器麦克风权限要求 iframe 元素带 `allow="microphone"`，且父页面 CSP / permissions-policy 不能阻止麦克风。
- 第三方站点嵌入需要正确配置 `Content-Security-Policy: frame-ancestors`，只允许可信 CRM 域名。
- 如果 CloudCC 页面本身在 HTTPS 下运行，AgentCiCi embed 页面也必须 HTTPS。
- SDK 不能污染 CloudCC 全局样式，所有外层 DOM class 使用 `agentcici-meeting-` 命名空间。
- iframe 内核不能依赖父页面 CSS、JS、路由或登录态。
- 长期集成密钥、CloudCC API 凭证、讯飞 Secret、模型 API Key 都只能保留在服务端。

## 设计原则

- **框架无关**：SDK 使用 UMD/IIFE 浏览器脚本，挂到 `window.AgentCiCiMeeting`。
- **iframe 内核优先**：复杂 UI、录音、WebSocket 和纪要生成都在 AgentCiCi embed 页面内运行，避免 Vue/CSS 依赖冲突。
- **短期授权**：CRM 前端只拿短期 `embedToken`，后端校验 token 后恢复 org、user、CRM 上下文和允许动作。
- **用户确认写回**：AI 只能生成候选，写回 CRM 前必须展示清单并由用户确认。
- **来源可审计**：每个会议 session 都记录 source、objectType、objectId、externalUser、requestId 和 traceId。
- **嵌入式智能应用目录**：所有可嵌入能力先作为嵌入式智能应用注册，再对外提供 SDK、iframe、token scope、调试和日志。
- **可渐进增强**：第一版 iframe，后续可在不破坏 API 的情况下提供 npm 包、ESM、Web Component 或深度 CloudCC 组件适配。

## 方案设计

### 1. 嵌入式智能应用管理中心

Admin 管理端新增一级或二级菜单“嵌入式智能应用”，用于管理所有可嵌入其他系统的标准能力。会议纪要是第一项，后续可扩展客户摘要、商机跟进建议、售后工单助手、客服接管摘要等。

推荐信息架构：

```text
/admin/embed-apps
  ↓
嵌入式智能应用列表
  - 会议纪要
  - 客户摘要助手
  - 商机跟进建议
  - 售后工单助手
  ↓
/admin/embed-apps/{appCode}
  - 概览
  - 接入配置
  - SDK / iframe 说明
  - Token 签发与权限
  - 调试
  - 调用日志
```

列表字段：

| Field | Description |
|---|---|
| `name` | 嵌入式智能应用名称，例如“会议纪要”。 |
| `appCode` | 稳定代码，例如 `meeting-minutes`。 |
| `status` | `enabled`、`disabled`、`draft`。 |
| `embedMode` | `sdk_iframe`、`iframe_only`、`api_only`。 |
| `targetSystems` | `CloudCC`、`Salesforce`、`Custom CRM`。 |
| `requiredScopes` | `meeting:start`、`meeting:summary`、`crm:writeback` 等。 |
| `lastInvokedAt` | 最近调用时间。 |
| `version` | 当前稳定版本，例如 `1.0.0`。 |

详情页应包含：

- **概览**：应用用途、适用业务记录、启用状态、最近调用、错误率。
- **接入配置**：允许父页面域名、默认 run-as 用户、可用 CRM connector、权限 scope、token TTL。
- **SDK / iframe 说明**：版本化 SDK URL、当前稳定 SDK URL、iframe URL、CloudCC Vue 示例、原生 JS 示例、postMessage 事件说明。
- **Token 签发与权限**：签发接口、服务端鉴权方式、scope 表、过期策略和 origin 绑定说明。
- **调试**：选择 source、objectType、objectId、parentOrigin，生成一次性测试 token，在 admin 内嵌沙箱 iframe 验证加载、麦克风权限、postMessage 和 summary。
- **调用日志**：按 appCode、source、objectType、objectId、requestId、meetingSessionId、traceId、状态和时间筛选。

UI 规则：

- 这是 `/admin/*` product surface，沿用 `鎏金账房`，不要做应用市场式营销卡片。
- 列表保持紧凑 CRUD 风格，状态和 scope 使用文本层级与 1px 分隔线，不用 chip 背景。
- 详情页用文本 tab，不用 pill、分段按钮或卡片化 tab。
- SDK 示例代码可以使用深墨色代码块，但页面整体仍是暖象牙产品界面。
- 调试 iframe 是明确的测试工作区，可以被边框框定；内部不要再套背景卡片。

### 2. 总体架构

```text
Admin 嵌入式智能应用配置启用会议纪要
  ↓
CloudCC Vue 页面
  ↓ 获取短期 embedToken
CloudCC 服务端 / 可信中间层
  ↓ POST /embed/v1/meeting-minutes/tokens
AgentCiCi 后端签发短期 token
  ↓
CloudCC Vue 调用 window.AgentCiCiMeeting.open(...)
  ↓
框架无关 JS SDK 创建 drawer + iframe
  ↓
/embed/meeting-minutes 读取 token 并初始化会议 session
  ↓
复用 FEAT-029 ASR WebSocket / AI 听记 / summary
  ↓
用户确认后 POST /embed/v1/meeting-sessions/{id}/writeback
  ↓
CloudCC connector 写回活动、任务、备注和字段建议
```

### 3. 标准嵌入式智能应用注册模型

建议新增 `embed_app_definition` 或复用平台标准定义表，记录嵌入式智能应用的元数据：

| Field | Description |
|---|---|
| `app_code` | 稳定代码，例如 `meeting-minutes`。 |
| `name` | 展示名，例如“会议纪要”。 |
| `description` | 管理端简述。 |
| `status` | 平台默认状态。 |
| `embed_mode` | `sdk_iframe`、`iframe_only`、`api_only`。 |
| `stable_sdk_url` | `/sdk/meeting-minutes.js`。 |
| `versioned_sdk_url` | `/sdk/meeting-minutes@1.0.0.js`。 |
| `embed_url` | `/embed/meeting-minutes`。 |
| `required_scopes_json` | 应用所需 scope。 |
| `supported_sources_json` | 支持 `cloudcc`、`salesforce`、`custom`。 |
| `default_token_ttl_seconds` | 默认 token TTL。 |
| `doc_json` | 接入文档片段、示例代码、事件表。 |

建议新增 `org_embed_app_config`：

| Field | Description |
|---|---|
| `org_id` | 组织。 |
| `app_code` | 嵌入式智能应用代码。 |
| `enabled` | 组织是否启用。 |
| `allowed_origins_json` | 允许嵌入的父页面 origin。 |
| `run_as_user_id` | 默认 run-as 用户。 |
| `source_bindings_json` | source 到 connector 的绑定配置。 |
| `scope_overrides_json` | 组织级允许 scope。 |
| `token_ttl_seconds` | 组织级 TTL。 |
| `created_at` / `updated_at` | 时间戳。 |

会议纪要在第一版作为内置标准嵌入式智能应用注册：

```json
{
  "appCode": "meeting-minutes",
  "name": "会议纪要",
  "embedMode": "sdk_iframe",
  "stableSdkUrl": "/sdk/meeting-minutes.js",
  "versionedSdkUrl": "/sdk/meeting-minutes@1.0.0.js",
  "embedUrl": "/embed/meeting-minutes",
  "requiredScopes": ["meeting:start", "meeting:summary", "crm:writeback"],
  "supportedSources": ["cloudcc", "salesforce", "custom"]
}
```

### 4. SDK 载入方式

CloudCC Vue 页面可以直接引入：

```html
<script src="https://autoservice.agentcici.com/sdk/meeting-minutes.js"></script>
```

SDK 暴露全局对象：

```js
window.AgentCiCiMeeting
```

版本查询：

```js
window.AgentCiCiMeeting.version
```

第一版仅保证浏览器全局脚本兼容。后续如需要 npm 包，必须保持相同 public API。

### 5. SDK Public API

#### open(options)

```js
const instance = window.AgentCiCiMeeting.open({
  token: embedToken,
  mode: 'drawer',
  width: 960,
  locale: 'zh-CN',
  theme: 'gilded-ledger',
  context: {
    source: 'cloudcc',
    objectType: 'Opportunity',
    objectId: '006xx000001',
    recordName: '华东区续费商机',
    customerName: '某某集团',
    participants: [
      { name: '张三', role: '客户联系人' },
      { name: '李四', role: '销售' }
    ]
  },
  callbacks: {
    onReady(event) {},
    onMeetingStarted(event) {},
    onTranscriptFinal(event) {},
    onSummaryGenerated(event) {},
    onWritebackSuccess(event) {},
    onError(event) {},
    onClose(event) {}
  }
})
```

Options:

| Field | Required | Description |
|---|---:|---|
| `token` | yes | 短期 embed token，由可信服务端签发或换取。 |
| `mode` | no | `drawer` 或 `inline`，默认 `drawer`。 |
| `container` | conditional | `inline` 模式必填，可以是 selector 或 DOM element。 |
| `width` | no | drawer 宽度，默认 `960`，最小 `720`，最大 `min(1120, viewport - 32)`。 |
| `locale` | no | 默认 `zh-CN`。 |
| `theme` | no | 第一版固定支持 `gilded-ledger`。 |
| `context` | no | CRM 上下文，最终以后端 token claims 为准。 |
| `callbacks` | no | SDK 事件回调。 |

Return instance:

```js
instance.close()
instance.destroy()
instance.updateContext(nextContext)
instance.postMessage(type, payload)
```

#### close()

关闭当前 SDK 创建的 drawer，但不强制结束会议。若 iframe 内仍在录音，SDK 先向 iframe 发送 `host:request-close`，由 iframe 展示确认或返回 `embed:can-close`。

#### destroy()

销毁 iframe、事件监听和 SDK 创建的 DOM。用于 Vue 组件 `beforeUnmount` / `onUnmounted`。

#### updateContext(context)

父页面记录切换或补充字段时调用。iframe 必须展示“上下文已更新”的轻提示，但已生成纪要中的引用上下文需要保持版本快照，避免写回串记录。

### 6. CloudCC Vue 接入示例

```vue
<template>
  <button type="button" @click="openMeetingMinutes">
    开始会议纪要
  </button>
</template>

<script setup>
import { onUnmounted, ref } from 'vue'

const meetingInstance = ref(null)

async function openMeetingMinutes() {
  const embedToken = await fetchEmbedTokenFromCloudccServer()

  meetingInstance.value = window.AgentCiCiMeeting.open({
    token: embedToken,
    mode: 'drawer',
    width: 960,
    context: {
      source: 'cloudcc',
      objectType: 'Opportunity',
      objectId: window.CloudCC?.recordId,
      recordName: window.CloudCC?.recordName,
      customerName: window.CloudCC?.accountName
    },
    callbacks: {
      onSummaryGenerated(event) {
        console.log('summary generated', event.summary)
      },
      onWritebackSuccess(event) {
        console.log('writeback success', event.result)
      }
    }
  })
}

onUnmounted(() => {
  meetingInstance.value?.destroy()
})
</script>
```

CloudCC 实际变量名以其页面开发规范为准；AgentCiCi 只要求传入稳定的 `objectType` 和 `objectId`。

### 7. iframe URL

SDK 创建的 iframe src：

```text
https://autoservice.agentcici.com/embed/meeting-minutes?token={embedToken}&sdkVersion=1.0.0&mode=drawer
```

iframe 属性：

```html
<iframe
  src="..."
  allow="microphone"
  title="AgentCiCi 会议纪要"
  class="agentcici-meeting-frame">
</iframe>
```

不得把长期 key、CloudCC accessToken、讯飞凭证或模型凭证放入 URL。

### 8. postMessage 协议

所有消息统一结构：

```json
{
  "source": "agentcici-meeting-sdk",
  "type": "embed:ready",
  "requestId": "req_01HX...",
  "sessionId": "meet_01HX...",
  "payload": {},
  "timestamp": "2026-05-14T02:58:09Z"
}
```

Host to iframe:

| Type | Description |
|---|---|
| `host:init` | 父页面初始化，传 SDK version、allowed origin 和上下文摘要。 |
| `host:update-context` | 父页面更新 CRM 上下文。 |
| `host:request-close` | 父页面请求关闭。 |
| `host:focus` | 父页面 drawer 打开后要求 iframe 聚焦。 |

Iframe to host:

| Type | Description |
|---|---|
| `embed:ready` | iframe 加载完成并通过 token 校验。 |
| `embed:meeting-started` | 会议 session 创建并开始录音。 |
| `embed:transcript-final` | 收到稳定转写片段。 |
| `embed:summary-generated` | AI 纪要生成完成。 |
| `embed:writeback-preview` | 写回候选生成。 |
| `embed:writeback-success` | CRM 写回成功。 |
| `embed:error` | 发生可恢复或不可恢复错误。 |
| `embed:close` | iframe 主动请求关闭。 |

安全要求：

- SDK 只接受来自 AgentCiCi embed origin 的消息。
- iframe 只接受 token claims 中允许的 parent origin。
- 消息 payload 需要按 type 做 schema 校验，未知 type 忽略。
- 错误消息不包含密钥、完整 token、CloudCC accessToken 或模型 provider 凭证。

### 9. Embed Token

短期 token 用于浏览器嵌入授权，不等同于 Agent Open API Key。

Token claims:

```json
{
  "typ": "embed_app",
  "iss": "agentcici",
  "aud": "agentcici-embed",
  "appCode": "meeting-minutes",
  "orgId": "demo-org",
  "userId": "u-10001",
  "source": "cloudcc",
  "objectType": "Opportunity",
  "objectId": "006xx000001",
  "parentOrigin": "https://crm.example.com",
  "permissions": ["meeting:start", "meeting:summary", "crm:writeback"],
  "expiresAt": "2026-05-14T03:13:09Z",
  "nonce": "..."
}
```

Rules:

- 有效期默认 15 分钟，最长 30 分钟。
- token 只能创建一个 active meeting session；重复使用返回已有 session 或拒绝，具体由后端实现选择。
- token 绑定 `appCode` 和 `parentOrigin`，iframe 与 SDK 均按应用和 origin 校验。
- token 绑定 CRM record，写回时不得由浏览器覆盖 `objectId`。
- token 不包含 CloudCC accessToken；写回由 AgentCiCi 服务端 connector 处理。

### 10. Token 签发接口

供可信服务端调用：

```http
POST /embed/v1/apps/{appCode}/tokens
Authorization: Bearer cici_ak_live_xxx
Content-Type: application/json
```

Request:

```json
{
  "appCode": "meeting-minutes",
  "source": "cloudcc",
  "parentOrigin": "https://crm.example.com",
  "user": {
    "externalUserId": "cloudcc-user-001",
    "displayName": "李四"
  },
  "context": {
    "objectType": "Opportunity",
    "objectId": "006xx000001",
    "recordName": "华东区续费商机",
    "customerName": "某某集团",
    "participants": [
      { "name": "张三", "role": "客户联系人" }
    ]
  },
  "permissions": ["meeting:start", "meeting:summary", "crm:writeback"],
  "ttlSeconds": 900
}
```

Response:

```json
{
  "success": true,
  "data": {
    "embedToken": "eyJ...",
    "expiresAt": "2026-05-14T03:13:09Z",
    "embedUrl": "https://autoservice.agentcici.com/embed/meeting-minutes"
  }
}
```

鉴权建议：

- 可信服务端使用 Agent Open API Key 或单独的 Embed Integration Key 调用 token 签发接口。
- 首版可复用 FEAT-021 的 API Key 管理能力，但 scope 必须区分 `embed:meeting`.
- `runAsUserId` 由 key 配置或 CloudCC 用户映射决定。
- 管理端调试面板也调用同一签发服务，但必须标记 `debug=true` 并写入调用日志。

### 11. Meeting Session 数据模型

建议新增 `meeting_session`：

| Field | Description |
|---|---|
| `id` | meeting session id。 |
| `org_id` | 组织。 |
| `user_id` | run-as 或映射后的 AgentCiCi 用户。 |
| `source` | `cloudcc`、`salesforce`、`custom`。 |
| `app_code` | 标准嵌入式智能应用代码，第一版为 `meeting-minutes`。 |
| `object_type` | CRM 对象类型。 |
| `object_id` | CRM 记录 ID。 |
| `record_name` | CRM 记录名快照。 |
| `customer_name` | 客户名称快照。 |
| `parent_origin` | 父页面 origin。 |
| `status` | `CREATED`、`RECORDING`、`SUMMARIZING`、`READY_TO_WRITEBACK`、`WRITTEN_BACK`、`FAILED`、`CANCELED`。 |
| `context_json` | CRM 上下文快照，脱敏后保存。 |
| `summary_markdown` | AI 生成纪要。 |
| `writeback_preview_json` | 写回候选。 |
| `writeback_result_json` | CRM 写回结果。 |
| `trace_id` | AI 听记 trace。 |
| `created_at` / `updated_at` | 时间戳。 |

转写段落可第一版前端内存持有并在生成纪要时提交；如要支持刷新恢复，再新增 `meeting_transcript_segment`。

### 12. Embed Runtime API

创建 session：

```http
POST /embed/v1/apps/meeting-minutes/sessions
Authorization: Bearer {embedToken}
```

生成纪要：

```http
POST /embed/v1/apps/meeting-minutes/sessions/{sessionId}/summary
Authorization: Bearer {embedToken}
```

写回预览：

```http
POST /embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback-preview
Authorization: Bearer {embedToken}
```

确认写回：

```http
POST /embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback
Authorization: Bearer {embedToken}
```

写回请求只允许选择后端已生成的候选项：

```json
{
  "selectedItemIds": ["summary-note", "task-1", "field-stage-suggestion"]
}
```

浏览器不得直接提交任意 CloudCC API payload。后端根据 session、token claims 和候选项执行 connector 写回。

### 13. CRM 写回候选

AI 纪要生成后，系统把结果拆成可确认候选：

```json
{
  "items": [
    {
      "id": "summary-note",
      "type": "note",
      "label": "写入会议纪要",
      "target": {
        "source": "cloudcc",
        "objectType": "Opportunity",
        "objectId": "006xx000001"
      },
      "content": "本次沟通重点..."
    },
    {
      "id": "task-1",
      "type": "task",
      "label": "创建跟进任务",
      "content": "下周三前发送报价方案",
      "dueDate": "2026-05-20"
    },
    {
      "id": "field-next-step",
      "type": "field_suggestion",
      "label": "更新下一步动作",
      "fieldApiName": "next_step__c",
      "proposedValue": "发送报价方案并约定技术评审"
    }
  ]
}
```

写回策略：

- `note` / `activity`：低风险，用户确认后可写。
- `task`：中风险，需要展示负责人和截止日期。
- `field_suggestion`：高风险，默认只作为建议；若允许写入，必须显示原值和新值。
- 删除、关单、退款、赔付、改金额等高风险写动作不在第一版支持。

### 14. Embed UI 设计

物理场景：销售或客户成功在 CRM 记录页边看客户信息边开会，需要在右侧窄空间低干扰记录、确认和写回。

布局：

- SDK drawer 默认宽度 960px，移动或窄屏时回落为全屏。
- iframe 内部顶部为紧凑 session bar：记录名、客户、状态、结束按钮。
- 主体桌面为左右两列：
  - 左侧实时转写，发言人标签、partial、final 段落。
  - 右侧 AI 纪要、待办和写回候选。
- 窄屏为上下单列。
- 面板内部只用 1px 分隔线和文本层级，不使用行卡片、chip 背景、选中背景、阴影或装饰块。

状态：

- `idle`：等待启动。
- `permission`：请求麦克风权限。
- `recording`：实时转写中。
- `summarizing`：生成纪要中。
- `ready_to_writeback`：展示候选。
- `writing_back`：写回中。
- `done`：写回或仅保存完成。
- `error`：配置、权限、网络、ASR、模型、写回失败。

交互：

- 关闭时若正在录音，先弹出 iframe 内确认，不由 SDK 直接销毁。
- 写回确认使用明确的 footer primary action，取消使用 secondary。
- 错误态保留转写内容和上下文，允许重试生成或重新写回。

### 15. Security Headers

Embed 页面响应头建议：

```http
Content-Security-Policy: frame-ancestors https://crm.example.com https://*.cloudcc.com;
Permissions-Policy: microphone=(self "https://crm.example.com")
Referrer-Policy: strict-origin-when-cross-origin
X-Content-Type-Options: nosniff
```

SDK 脚本响应头建议：

```http
Cache-Control: public, max-age=300
Content-Type: application/javascript; charset=utf-8
```

生产环境应提供版本化脚本：

```text
/sdk/meeting-minutes@1.0.0.js
/sdk/meeting-minutes.js
```

`/sdk/meeting-minutes.js` 指向当前稳定版，版本化 URL 用于 CloudCC 页面锁定版本。

## 接口与数据影响

### Backend

- 新增标准嵌入式智能应用定义与组织级配置模型：`embed_app_definition` / `org_embed_app_config`，或等价实现。
- 新增 embed token 签发与校验服务。
- 新增 meeting session 持久化表和 repository。
- 新增 `/embed/v1/apps/{appCode}/tokens`。
- 新增 `/embed/v1/apps/meeting-minutes/sessions/**`。
- 新增 admin 嵌入式智能应用 API：列表、详情、组织配置、允许域名、调试 token、调用日志。
- 扩展会议纪要 summary prompt，注入 CRM context 和 writeback target。
- 扩展 CloudCC connector 写回能力，支持 note/activity、task 和字段建议。
- 扩展 trace metadata：`channel=embed`、`source=cloudcc`、`objectType`、`objectId`、`meetingSessionId`。

### Frontend

- 新增 `/admin/embed-apps` 嵌入式智能应用列表页。
- 新增 `/admin/embed-apps/:appCode` 详情页，包含概览、接入配置、SDK/iframe 说明、Token 签发、调试和调用日志。
- 新增 `/embed/meeting-minutes` route。
- 从现有 FEAT-029 drawer 抽取可复用 meeting runtime component 或复用 transcript helpers。
- 新增 `public/sdk/meeting-minutes.js` 或构建产物生成任务。
- SDK 使用命名空间样式，避免污染父页面。

### Deployment

- Nginx 需要代理：
  - `/embed/`
  - `/sdk/`
  - `/embed/v1/`
  - `/admin/embed-apps` 对应前端路由和后端 API 前缀。
  - `/ws/asr`
- 若生产域名使用 `autoservice.agentcici.com`，CloudCC iframe allowlist 和 CSP 必须同步。

## 任务拆分

### TASK-090 Design

- status: completed
- owner_role: product-architecture
- output: 本规格文档。

### TASK-091 Embed token and session backend

- status: completed
- owner_role: backend-crm-embed
- depends_on: TASK-090
- scope: 标准嵌入式智能应用定义、组织级配置、token 签发、校验、meeting session 表、embed runtime API、trace metadata。

### TASK-092 Admin embedded apps management UI

- status: completed
- owner_role: frontend-admin-product
- depends_on: TASK-091
- scope: `/admin/embed-apps` 列表、详情、接入说明、允许域名配置、调试面板和调用日志入口。
- completed_at: 2026-05-14T04:56:11Z
- output:
  - 新增管理端菜单“嵌入式智能应用”与 `/admin/embed-apps`、`/admin/embed-apps/:appCode` 路由。
  - 新增 `AdminEmbedAppsPage`，包含应用目录、详情文本 tab、概览、接入配置、SDK/iframe 说明、调试 token 和最近 session 日志。
  - 后端 admin API 补充 `POST /embed/v1/admin/apps/{appCode}/debug-token` 与 `GET /embed/v1/admin/apps/{appCode}/sessions`。
  - Vite 与部署 Nginx 均补 `/embed/v1/` 后端代理，避免管理端和未来 embed runtime API 被 SPA fallback 吞掉。
  - 已按 `鎏金账房` product register 做桌面与移动截图检查。

### TASK-093 Embed page and shared meeting UI

- status: completed
- owner_role: frontend-product-embed
- depends_on: TASK-091
- scope: `/embed/meeting-minutes`、复用 FEAT-029 会议转写 UI、嵌入态布局、postMessage client。
- completed_at: 2026-05-14T07:14:45Z
- progress:
  - 已新增共享 `MeetingMinutesPanel`，工作台会议抽屉与嵌入页共用同一 UI 组件。
  - 已新增 `/embed/meeting-minutes` 路由与 iframe 内核，接入短期 token context、session 创建、ASR、summary、writeback-preview/writeback skeleton 和 postMessage bridge。
  - admin 调试 tab 已接入 iframe 预览。
- output:
  - `/embed/meeting-minutes` 可使用短期 embed token 初始化 session，并复用共享会议纪要面板。
  - admin 调试 tab 可生成一次性 debug token 并在内嵌 iframe 中预览会议纪要 ready 状态。
  - 嵌入页桌面/移动视觉 QA 已完成，截图保存在 `output/playwright/`。

### TASK-094 Framework agnostic browser SDK

- status: completed
- owner_role: frontend-sdk
- depends_on: TASK-093
- scope: `/sdk/meeting-minutes.js`、drawer/inline mode、生命周期 API、Vue 示例。
- completed_at: 2026-05-14T07:22:59Z
- output:
  - 新增 `/sdk/meeting-minutes.js` 与 `/sdk/meeting-minutes@1.0.0.js`。
  - SDK 暴露 `window.AgentCiCiMeeting.version/open(...)`，支持 drawer/inline mode、生命周期方法、origin-gated postMessage 和 callbacks。
  - 已用同源 Playwright smoke 验证 inline、drawer、mobile drawer、`updateContext()`、`close()` 和 `destroy()`。

### TASK-095 CloudCC writeback connector

- status: completed
- owner_role: backend-cloudcc-integration
- depends_on: TASK-091
- scope: writeback preview、note/activity、task、field suggestion、人工确认和错误回滚。
- completed_at: 2026-05-14T09:42:30Z
- output:
  - 新增 `CloudccMeetingWritebackConnector`，复用组织 CloudCC CRM 集成配置和 run-as 用户绑定，通过 `CloudccAccessTokenService` 获取 CloudCC `accessToken`。
  - 确认写回使用 CloudCC One OpenAPI `/openApi/common`，提交 `insert` / `update` / `delete` 服务名、`objectApiName` 和 JSON array 字符串 `data`。
  - `writeback-preview` 由后端根据 session summary 和 signed CRM context 生成候选：`summary-note`、纪要行动项 task、以及 token context 中的 field suggestion。
  - `writeback` 只接受 `selectedItemIds`，并校验 ID 必须存在于已持久化 preview；浏览器不能覆盖 objectId、objectType 或 CloudCC payload。
  - 写回成功后 session 标记 `WRITTEN_BACK`；失败时记录 `FAILED` result，session 保持 `READY_TO_WRITEBACK`，并对本轮已成功插入的 note/task 执行 CloudCC `delete` 回滚。
  - 嵌入页前端将 `writeback.status=FAILED` 显示为错误态。

### TASK-096 End-to-end CRM embed verification

- status: in_progress
- owner_role: qa-integration
- depends_on: TASK-092, TASK-093, TASK-094, TASK-095
- scope: admin 嵌入式智能应用调试台、本地模拟父页面、CloudCC Vue 示例页、麦克风权限、desktop/mobile 截图、写回 smoke。
- progress:
  - 2026-05-15T02:51:53Z 已修复 CloudCC 页面打开 AI 听记时报 404 的 iframe URL 生成问题。根因是 SDK 在 `open()` 点击回调中读取 `document.currentScript`，真实 CloudCC/Vue 场景下该值为空，于是回退到 CloudCC 父页面 origin 并生成 `https://yundong.lightning.cloudcc.cn/embed/meeting-minutes`。现两份公开 SDK 均改为加载时缓存脚本 origin，模拟验证会生成 `https://autoservice.agentcici.com/embed/meeting-minutes?...`。
  - 2026-05-15T03:07:36Z 已将 SDK origin 修复发布到线上 ECS，frontend 容器为 `cici-frontend:2.0.B1-sdk404fix-20260515-1103`；服务器本地 Host `autoservice.agentcici.com` 下 `/sdk/meeting-minutes.js`、`/embed/meeting-minutes` 与 `/` 均 HTTP 200，线上 SDK 内容已包含 `SDK_ORIGIN`。当前 ACR 凭据返回 unauthorized，hotfix 镜像尚未推送到 ACR，需要后续持久化。

## 验收标准

- CloudCC Vue 页面能通过 `<script>` 引入 SDK，并调用 `window.AgentCiCiMeeting.open(...)` 打开会议纪要 drawer。
- 组织管理员能在 `/admin/embed-apps` 看到“会议纪要”标准嵌入式智能应用，并进入详情页查看接入说明、SDK URL、iframe URL、scope、token 签发说明和 CloudCC Vue 示例。
- 管理员能配置允许父页面 origin、默认 run-as 用户、token TTL 和启用状态。
- 管理员能在嵌入式智能应用详情页使用调试面板生成测试 token，并在 admin 内预览 iframe 加载与 postMessage 事件。
- SDK 不依赖 React、Vue、CloudCC 全局组件或父页面 CSS。
- iframe 带 `allow="microphone"`，真实浏览器中能请求麦克风权限。
- iframe 与父页面 postMessage 双向通信正常，且校验 origin。
- 短期 token 过期、origin 不匹配、objectId 不匹配时拒绝初始化或写回。
- 嵌入页能创建会议 session、实时转写、生成 AI 纪要，并展示写回候选。
- 用户确认后，后端通过 CloudCC connector 写回选中的候选项。
- 所有 CRM 写回都有 trace / audit 信息，能定位 meetingSessionId、objectType、objectId 和结果。
- 嵌入页桌面和移动视口符合 `鎏金账房` product UI，截图检查无横向溢出、文字遮挡、内部卡片堆叠或按钮语汇漂移。

## 风险与回滚

- **麦克风权限被父页面阻止**：SDK 检查 iframe permission 能力，embed 页面显示明确错误；文档提示 CloudCC 页面需允许 microphone。
- **CloudCC CSP 阻止 iframe 或脚本**：提供固定域名、CSP 配置清单和版本化脚本 URL。
- **token 泄露**：短期、绑定 origin、绑定 record、单 session 使用，过期后不可重放。
- **写回错误记录**：后端以 token claims 和 session 快照为准，浏览器不得覆盖写回目标。
- **SDK 版本升级影响 CRM 页面**：提供版本化 URL，CloudCC 可锁定 `meeting-minutes@1.0.0.js`。
- **父页面关闭导致录音丢失**：SDK close 先通知 iframe；iframe 负责确认、停止录音和保留已转写内容。

回滚方式：

- 停用 embed token 签发 scope 或相关 API Key。
- 从 CloudCC 页面移除 SDK 按钮或改回普通跳转。
- 保留 FEAT-029 工作台内会议纪要功能，不受嵌入 SDK 回滚影响。

## 实现进展

- 2026-05-14T02:58:09Z 完成设计文档，明确第一版采用框架无关浏览器 JS SDK + iframe 内核，面向 CloudCC Vue 页面嵌入，并定义 token、postMessage、UI、写回和任务拆分。
- 2026-05-14T03:55:50Z 补充 admin 管理端“嵌入式智能应用”菜单设计，将会议纪要定义为首个可嵌入标准能力，并增加应用目录、组织级配置、接入说明、调试面板、调用日志和后续 `TASK-092` 管理端实现任务。
- 2026-05-14T04:07:12Z 用户确认正式命名为“嵌入式智能应用”；文档同步将管理端路由收敛为 `/admin/embed-apps`，技术对象继续使用 `embed_app` / `org_embed_app_config`。
- 2026-05-14T04:22:00Z 完成 `TASK-091` 后端实现：新增 `embed_app_definition`、`org_embed_app_config`、`meeting_session` 迁移与 JPA 模型，注册 `meeting-minutes`，实现 admin 配置 API、短期 embed token 签发、token 校验、session 幂等创建、summary runtime 和写回预览骨架；普通 JWT、Open API Key 与 embed token 在 `TenantContextFilter` 中已隔离。真实 CloudCC 写回 connector 保留给 `TASK-095`。
- 2026-05-14T04:56:11Z 完成 `TASK-092` 管理端实现：新增 `/admin/embed-apps` 页面、菜单和路由，接入后端列表/详情/配置 API，补充 admin debug token 与最近 session 日志 API，并补齐 Vite 与部署 Nginx 的 `/embed/v1/` 代理。
- 2026-05-14T06:54:12Z 推进 `TASK-093` 嵌入页实现：新增共享会议面板、`/embed/meeting-minutes` iframe 内核、runtime API 接线和 postMessage bridge，并把 admin 调试 token 接入 iframe 预览。当前代码与 API smoke 通过；截图 QA 因本机 headless Playwright/Vite 加载和系统截图权限问题尚未完成，任务保持 in_progress。
- 2026-05-14T07:14:45Z 完成 `TASK-093` 嵌入页与共享会议 UI 视觉 QA：`/embed/meeting-minutes` 桌面/移动、admin debug iframe 桌面/移动截图均通过，嵌入页在 iframe 容器中不再被 1360px drawer media rule 限宽。
- 2026-05-14T07:22:59Z 完成 `TASK-094` 框架无关浏览器 SDK：新增 `/sdk/meeting-minutes.js` 与 `/sdk/meeting-minutes@1.0.0.js`，暴露 `window.AgentCiCiMeeting.version/open(...)`，支持 drawer/inline mode、生命周期 API、origin-gated postMessage 和 callback 分发；Playwright smoke 覆盖 inline/drawer/mobile/lifecycle。
- 2026-05-14T09:42:30Z 完成 `TASK-095` CloudCC writeback connector：服务端生成 note/task/field suggestion 候选，确认写回只允许选择已持久化 preview ID；后端通过 CloudCC `/openApi/common` 执行 `insert` / `update`，失败时记录 `FAILED` result 并对已插入 note/task 调用 `delete` 回滚；PostgreSQL `EmbedAppIntegrationTest` 覆盖成功写回、未知候选拒绝和失败回滚。

## 交接说明

- 下一步进入 `TASK-096` 端到端 CRM 嵌入验证：admin 调试台、本地模拟父页面、CloudCC Vue 示例页、麦克风权限、desktop/mobile 截图和写回 smoke。
- `TASK-092` 已提供 debug token 和最近 session 日志；`TASK-093` 已把 admin 内 iframe 沙箱预览接入调试 tab。
- 前端 `TASK-093` 已从 FEAT-029 抽取共享会议面板；后续改会议 UI 时优先维护 `frontend/src/meeting/MeetingMinutesPanel.tsx`。
- SDK 第一版保持小而稳：只创建 drawer/iframe、做 postMessage 桥接和生命周期清理，不承载业务 UI。
- CloudCC 真机联调前必须确认父页面域名、CSP、麦克风权限，以及 CloudCC Note/Task/目标对象实际字段名；如字段名与默认 `Note.name/body/parentid`、`Task.subject/description/whatid/activitydate` 不一致，应通过 token signed context 的 `writeback` 映射覆盖。
