---
kind: feature-spec
feature_id: FEAT-023
title: AI Native After-Sales Agent
status: in_implementation
owner_role: shared
task_ids: TASK-063, TASK-066
related_decisions: FEAT-015, FEAT-018, FEAT-019, FEAT-021, FEAT-022
related_issues: none
updated_at: 2026-05-10T12:57:34Z
updated_by: ai
---

# FEAT-023 - AI 原生售后 Agent

## 背景与目标

截至 2026-05-08 的客服/售后自动化市场洞察显示，市场大致分为三类：

- 传统客服 / helpdesk / 联络中心平台加 AI，例如 Zendesk、Salesforce Service、ServiceNow、Freshdesk、智齿、Udesk。
- CRM / 联络中心 / 企业应用平台智能体化，例如 Agentforce Contact Center、Microsoft Dynamics、Amazon Connect、Genesys、NICE。
- AI Agent 原生或 AI-first 客服产品，例如 Sierra、Decagon、Forethought、Ada、Aisera、Intercom Fin、百度智能云客悦、腾讯云客服大模型机器人。

当前 AgentCiCi 已具备 Agent Builder、Skill 治理、声明式 API runtime、知识库 RAG、Agent Open API、飞书渠道、CloudCC 查询工具和运行 trace。它更适合成为 **AI 原生售后 Agent 层**，而不是从零实现完整传统客服平台。结合本轮方向调整，首版客户侧入口明确优先接入 **企业微信「微信客服」**，让最终用户在微信里完成售后沟通。

本功能目标是基于当前 AgentCiCi 建立第一版售后自动化能力：

- 以“售后服务 Agent”作为第一入口，默认由 AI 理解客户问题、检索售后知识、查询业务系统并给出可执行答复。
- 通过 Skill API / CloudCC 工具接入客户、订单、物流、工单、产品、设备和保修信息，首版以只读查询为主。
- 通过企业微信「微信客服」对接最终客户微信会话，AgentCiCi 在后台接收客服消息、调用售后 Agent、再把回复发回微信。
- 保留 Agent Open API 作为门户、官网组件、CRM 页面或其他外部售后入口的底层能力，但首版客户侧主入口以企业微信为准。
- 在需要人工处理、风险动作、缺少信息或低置信度时生成可交接的人工接管摘要。
- 将每次售后 Agent 运行纳入现有 trace、调用日志和后续计费事件体系。

## 范围

### In Scope

- 新增或配置一个售后服务 Agent 模板，覆盖售后问答、订单/工单/物流/保修查询和人工转接判断。
- 建立售后知识库使用约定，支持保修政策、退换货规则、维修流程、物流异常、发票、退款、产品手册和 FAQ。
- 通过 Skill 声明式 API 或现有 CloudCC 工具封装首批只读售后动作：
  - 查询客户档案。
  - 查询订单或合同。
  - 查询物流状态。
  - 查询已有售后工单或 case。
  - 查询产品、设备、序列号或保修信息。
- 定义售后 Agent 的运行时上下文：
  - `externalUser` 作为外部客户身份元数据。
  - `sessionId` 映射客户会话。
  - `metadata` 可传客户、订单、渠道、页面、来源系统等业务上下文。
- 新增企业微信「微信客服」渠道接入：
  - 配置企业微信回调 URL、Token、EncodingAESKey、CorpID、微信客服 secret 和客服账号 `open_kfid`。
  - 支持企业微信 GET 回调校验和 POST 加密事件解密。
  - 支持 `kf_msg_or_event` 事件后调用 `sync_msg` 拉取客户消息。
  - 支持调用 `send_msg` 把售后 Agent 回复发回微信客户。
  - 支持微信客户 `external_userid` 到 AgentCiCi `externalUser.id` 的映射。
- 复用 FEAT-021 Agent Open API 作为非企业微信外部入口和内部调用契约。
- 复用 FEAT-019 运行观测，售后运行记录必须能看到 RAG、工具调用、转人工建议、错误原因和耗时。
- 定义人工接管输出契约：客户问题、已确认事实、已查询系统、建议下一步、风险等级和需要人工处理原因。
- 定义第一版安全边界：查询类自动执行，写操作默认不开放或只输出建议，不直接执行退款、关单、改地址、发券等动作。
- 沉淀售后域的验收脚本与样例问题，便于人工回归。

### Out Of Scope

- 不从零实现完整 helpdesk、全渠道客服系统、电话联络中心、坐席排班、IVR、WFM 或 BPO 能力。
- 不替代 CloudCC、CRM、ERP、OMS、WMS 或现有工单系统的数据主权。
- 首版不实现复杂工单中心、SLA 编排、自动派单、客服绩效、质检报表或多坐席工作台。
- 首版不直接执行高风险写操作，例如退款、赔付、取消订单、修改收货地址、关闭工单、发放优惠券。
- 首版不做 OpenAI Chat Completions 兼容接口、公开开发者门户或客户自助注册。
- 首版不支持语音机器人、电话呼入、ASR/TTS 或多模态附件处理。
- 首版企业微信只支持「微信客服」客户消息文本接待，不做客户朋友圈、客户群群聊托管、企微内部应用群聊机器人或会话存档合规归档。
- 首版不自动转接企业微信人工坐席；只生成接管摘要。真实转接/升级专员服务可作为后续任务单独接入。

## 用户场景

- 客户在微信里通过企业微信「微信客服」询问“我的订单怎么还没发货”，企业微信回调 AgentCiCi，AgentCiCi 拉取消息、调用售后 Agent 查询订单和物流后，通过微信客服接口回复客户。
- 客户在官网或客户门户询问“我的订单怎么还没发货”，外部系统仍可通过 Open API 传入客户 ID、订单 ID 和 session，售后 Agent 查询订单和物流后答复。
- 客户询问“这个设备还在保修期内吗”，售后 Agent 根据序列号或订单信息查询设备/产品/保修对象，并引用保修政策知识库。
- 客户要求退货或退款，售后 Agent 先查询订单、退换货规则和状态，只给出资格判断和所需材料；实际退款动作进入人工处理或后续确认流程。
- 客户描述“产品坏了，需要维修”，售后 Agent 询问必要信息、查询保修与维修规则，并生成维修工单草稿或人工接管摘要。
- 内部客服在飞书或管理端追问某个客户的历史售后情况，售后 Agent 查询 CloudCC/CRM 中的客户、订单和工单摘要。
- 管理员在 `/admin/ops` 查看某次售后 Agent 调用，能定位 requestId、traceId、企业微信 `external_userid`、`open_kfid`、命中的知识库、调用的业务工具和失败原因。

## 现状与约束

### Verified Facts

- AgentCiCi 当前是 Java 21 + Spring Boot 后端、React + Vite 前端的多组织 AI 助手平台。
- 已有 Agent Builder、Agent 发布版本、Agent 渠道绑定和 Agent Open API。
- FEAT-021 已实现外部系统通过 API Key 调用 Agent，支持 run-as 用户、外部 session 映射、externalUser metadata、REST/SSE 和 trace 标记。
- FEAT-018 已将 RAG 调整为条件检索，适合售后场景中区分“知识问答”和“业务查询”。
- FEAT-015 已引入 Skill 声明式 API runtime，适合把售后业务 API 封装为模型只见参数、不见 URL/Header/Token 的确定性工具。
- 现有 CloudCC 工具已支持标准对象列表、自定义对象列表、字段列表和分页查询，但主要是通用对象查询，还没有售后域固定动作。
- FEAT-019 已有 `agent_run_trace`，可以记录模型、工具、知识库、技能、耗时和 Open API 来源。
- FEAT-022 已设计智能体工作量 credits，后续售后 Agent 的 Open API 调用、RAG、工具和模型消耗应纳入统一用量口径。
- 企业微信「微信客服」官方链路是：客户或接待人员发消息后，企业微信向企业配置的 URL 推送事件；企业服务收到事件后通过 `sync_msg` 主动读取具体消息。官方文档入口见 `https://open.work.weixin.qq.com/kf/doc/92512/93143/93304`。
- 企业微信发送客服消息使用 `/cgi-bin/kf/send_msg`，需要使用“微信客服”secret 获取的 access token；当微信客户主动发送消息后，企业可在 48 小时内最多发送 5 条消息，客户继续发送消息后可再次下发。
- 企业微信回调配置需要 URL、Token、EncodingAESKey，并要求服务同时支持 GET URL 有效性验证与 POST 加密业务数据接收。

### Inferred Requirements

- AgentCiCi 应定位为售后 AI Agent 层，优先连接已有业务系统，而不是先重建完整客服系统。
- 售后 Agent 必须显式区分“客户身份”和“run-as 内部执行用户”。客户身份进入 `externalUser`，执行权限仍由 API Key 绑定的 `runAsUserId` 承接。
- 售后域工具应尽量封装成明确动作，例如 `after_sales_query_order`，不要长期依赖模型自由组合通用 CloudCC 对象名和字段名。
- 若底层业务系统暂时不完整，首版可以先使用 CloudCC 对象查询 + 售后知识库实现 read-only MVP。
- 企业微信客户不是 AgentCiCi 内部用户，不应创建 `app_user`；应以 `external_userid` 作为 `externalUser.id`，用一个组织级 run-as 服务用户执行售后 Agent。
- 企业微信渠道有会话窗口限制，AgentCiCi 必须记录最近客户消息时间和发送次数，避免在超出 48 小时或 5 条窗口时继续尝试主动回复。

## 方案设计

### 1. 产品定位

AgentCiCi 不做传统客服平台本体，而做 AI 原生售后 Agent 层：

```text
微信客户 / 官网 / 客户门户 / CRM 页面 / 飞书
  ↓
企业微信「微信客服」回调 / Agent Open API / 内部工作台
  ↓
售后服务 Agent
  ↓
知识库 RAG + 售后 Skill API + CloudCC/CRM/工单系统工具
  ↓
答复客户 / 生成人工接管摘要 / 写入 trace 与调用日志
```

售后 Agent 的默认工作方式：

- 先识别问题类型：政策问答、订单物流、保修维修、退款退货、投诉升级、人工处理。
- 知识型问题检索售后知识库。
- 业务状态问题调用只读售后工具。
- 信息不足时追问必要字段。
- 高风险或不可自动处理时给出人工接管摘要。

### 2. 售后 Agent 模板

建议新增一个组织级 Agent 模板：

- `agent_id`: `after-sales-agent`
- 名称：`售后服务 Agent`
- 默认渠道：`wechat_kf`、`api`，可选 `web`、`feishu`
- 默认知识库：售后知识库集合
- 默认绑定技能：
  - `after_sales_policy_qa`
  - `after_sales_customer_lookup`
  - `after_sales_order_lookup`
  - `after_sales_ticket_lookup`
  - `after_sales_warranty_lookup`
  - `after_sales_handoff_summary`

系统提示词重点：

- 只基于知识库和工具结果回答可核验事实。
- 查询客户、订单、物流、保修、工单时优先调用工具，不凭空猜测。
- 对退款、赔付、关单、改地址、发券、取消订单等风险动作不得直接承诺已完成。
- 当信息不足时一次性询问最少必要字段。
- 转人工时输出结构化交接摘要。

### 3. 售后 Skill 与工具分层

首版建议优先用 Skill 声明式 API runtime 封装固定动作；CloudCC 通用对象工具作为过渡和兜底。

建议 Skill 工具：

| Skill | 动作 | 风险 | 首版执行 |
|---|---|---|---|
| `after_sales_customer_lookup` | 按手机号、客户号、外部 ID 查询客户 | LOW | 自动执行 |
| `after_sales_order_lookup` | 按订单号、客户 ID 查询订单与状态 | LOW | 自动执行 |
| `after_sales_logistics_lookup` | 查询发货、签收、异常物流 | LOW | 自动执行 |
| `after_sales_ticket_lookup` | 查询历史工单 / case | LOW | 自动执行 |
| `after_sales_warranty_lookup` | 查询产品、设备、序列号、保修期 | LOW | 自动执行 |
| `after_sales_refund_eligibility` | 判断退换货 / 退款资格 | MEDIUM | 自动查询，只输出判断 |
| `after_sales_ticket_draft` | 生成工单草稿或人工接管摘要 | MEDIUM | 首版只生成草稿 |

后续可扩展写操作：

- `after_sales_create_ticket`
- `after_sales_update_ticket`
- `after_sales_submit_refund_request`
- `after_sales_schedule_repair`

这些写操作必须进入二次确认、权限校验和审计。

### 4. 企业微信「微信客服」接入方案

企业微信客户侧首版建议使用「微信客服」而不是普通企业微信应用消息：

- 最终用户在微信里打开企业的微信客服入口，不需要先成为企业微信内部成员。
- 企业微信通过回调通知 AgentCiCi 有新消息。
- AgentCiCi 使用微信客服 access token 调用 `sync_msg` 拉取具体消息内容。
- AgentCiCi 将文本消息映射为售后 Agent 输入。
- AgentCiCi 使用 `send_msg` 把 Agent 输出发回客户微信会话。

推荐链路：

```text
微信客户发送文本
  ↓
企业微信回调 POST /wecom/kf/callback?msg_signature=...&timestamp=...&nonce=...
  ↓
AgentCiCi 校验签名并解密，识别 kf_msg_or_event、Token、OpenKfId
  ↓
AgentCiCi 调用 /cgi-bin/kf/sync_msg 拉取消息
  ↓
按 msgid 去重，提取 external_userid、open_kfid、msgtype、text.content
  ↓
sessionId = wecom-kf:{corpId}:{openKfId}:{externalUserId}
externalUser.id = external_userid
channel = wechat_kf
  ↓
调用 ChatOrchestratorService / 售后 Agent
  ↓
调用 /cgi-bin/kf/send_msg 回复文本或菜单消息
  ↓
写入 wecom 消息日志、agent_run_trace、后续 usage meter
```

首版只处理文本消息：

- `msgtype=text`：进入售后 Agent。
- 图片、语音、视频、文件、位置、链接：回复“当前版本先支持文字描述”，并可引导用户补充订单号、手机号、序列号等文本信息。
- 菜单消息：可作为满意度或“转人工”选择输入，但不在首版强依赖。

建议新增后端模块：

```text
backend/src/main/java/com/codehouse/ciciassistant/wecom/
├── api/WecomKfCallbackController.java
├── domain/WecomKfAccountEntity.java
├── domain/WecomKfConversationEntity.java
├── domain/WecomKfMessageEntity.java
├── service/WecomKfConfigService.java
├── service/WecomKfCryptoService.java
├── service/WecomKfTokenService.java
├── service/WecomKfMessageSyncService.java
├── service/WecomKfConversationService.java
└── service/WecomKfMessenger.java
```

与现有飞书链路的关系：

- 可复用飞书的异步事件处理模式、消息去重、`sessionId` 前缀、`ChatOrchestratorService.chat(...)` 调用方式。
- 企业微信和飞书的身份语义不同：飞书当前绑定内部用户；微信客服面向外部客户，默认不做配对，不创建内部用户。
- 企业微信渠道应使用组织级配置的 `runAsUserId` 或该客服账号绑定的服务用户执行 Agent。

### 5. 企业微信会话与身份映射

建议会话字段：

| 字段 | 来源 | 用途 |
|---|---|---|
| `corpId` | 企业微信配置 / 回调 | 区分企业 |
| `openKfId` | 回调 `OpenKfId` / 消息 | 区分微信客服账号 |
| `externalUserId` | 微信客户 ID | 映射 `externalUser.id` |
| `sessionId` | AgentCiCi 生成 | 维持多轮上下文 |
| `runAsUserId` | AgentCiCi 配置 | 承接内部工具权限 |
| `agentId` | AgentCiCi 配置 | 默认 `after-sales-agent` |
| `lastCustomerMessageAt` | 消息时间 | 判断 48 小时回复窗口 |
| `replyCountInWindow` | AgentCiCi 计数 | 判断最多 5 条回复窗口 |

推荐内部 session：

```text
wecom-kf:{corpId12}:{openKfId12}:{hash20(externalUserId)}
```

推荐 Open API 风格上下文：

```json
{
  "sessionId": "wecom-kf:ww-demo:kf_xxx:user_hash",
  "message": "我的订单为什么还没有发货？",
  "externalUser": {
    "id": "wm_external_userid",
    "type": "wechat_customer",
    "metadata": {
      "corpId": "wwxxxx",
      "openKfId": "wkf_xxx",
      "source": "wecom_kf"
    }
  },
  "metadata": {
    "source": "wecom_kf",
    "channel": "wechat_kf",
    "openKfId": "wkf_xxx",
    "wecomMsgId": "msg_xxx"
  }
}
```

处理规则：

- `external_userid` 只进入客户上下文、消息日志和 trace，不写入 `app_user`。
- 同一 `corpId + openKfId + external_userid` 映射同一售后会话。
- 多个微信客服账号可以绑定不同 Agent 或不同 run-as 用户。
- `send_msg` 前必须检查窗口限制；超出窗口时记录失败并生成内部待处理事项，不继续重试刷屏。

### 6. 外部上下文契约

沿用 FEAT-021 Open API 请求结构，售后调用建议传入：

```json
{
  "sessionId": "portal-customer-001",
  "message": "我的订单为什么还没有发货？",
  "externalUser": {
    "id": "customer-001",
    "name": "张三",
    "type": "customer",
    "metadata": {
      "phoneMasked": "138****0000",
      "memberLevel": "gold"
    }
  },
  "metadata": {
    "source": "customer_portal",
    "orderId": "SO-20260508001",
    "channel": "web_widget"
  }
}
```

处理规则：

- `externalUser.id` 只用于客户上下文和日志，不映射为内部用户。
- `metadata.orderId`、`metadata.customerId`、`metadata.ticketId` 只作为候选上下文，工具仍需校验当前 run-as 权限和组织边界。
- 若请求没有客户或订单上下文，Agent 应追问必要字段，不应猜测。

### 7. 人工接管规则

以下情况必须建议转人工或生成待处理摘要：

- 客户要求退款、赔付、投诉升级、取消订单、改地址、关单等高风险动作。
- 工具返回失败、权限不足、数据冲突或未找到记录。
- 客户情绪明显激烈，或包含法律、监管、媒体曝光、重大事故等高敏信号。
- 知识库与业务系统结论冲突。
- 连续追问后仍缺少必要字段。

人工接管摘要输出结构：

```text
人工接管原因：
客户问题：
客户身份：
关联订单 / 工单 / 产品：
已查询系统：
已确认事实：
未完成事项：
建议下一步：
风险等级：
```

企业微信渠道下的人工接管建议：

- 首版只把结构化接管摘要回复给客户或发送到内部运维/飞书/管理端待处理列表。
- 后续可接入微信客服“升级服务”或会话状态变更，把客户转给专员或客户群。

### 8. 观测与运营

首版复用 `/admin/ops` 智能体运行观测，后续可新增售后专用筛选：

- 来源：`wechat_kf`、`open_api`、`web`、`feishu`
- Agent：`after-sales-agent`
- 外部客户 ID
- 企业微信 `open_kfid`
- 企业微信消息 ID
- 工具调用成功率
- RAG 命中知识库
- 转人工原因
- 错误码和耗时

后续可加入售后指标：

- 自动解决率。
- 转人工率。
- 知识缺口问题数。
- 平均处理耗时。
- 工具失败率。
- 微信客服消息发送失败率。
- 超出 48 小时 / 5 条窗口的待人工处理数。
- 每日 Open API 调用量和 credits 消耗。

## 接口与数据影响

### API

首版客户侧优先新增企业微信「微信客服」渠道接口：

- `GET /wecom/kf/callback`
  - 企业微信 URL 有效性校验。
  - 校验 `msg_signature` / `timestamp` / `nonce`。
  - 解密 `echostr` 并返回明文。
- `POST /wecom/kf/callback`
  - 接收企业微信加密事件。
  - 校验签名、解密 XML。
  - 识别 `kf_msg_or_event` 后异步调用 `sync_msg`。
- 内部调用企业微信：
  - `GET /cgi-bin/gettoken`
  - `POST /cgi-bin/kf/sync_msg`
  - `POST /cgi-bin/kf/send_msg`

同时继续复用已有 Open API 作为非企业微信入口：

- `POST /openapi/v1/agents/{agentId}/chat`
- `POST /openapi/v1/agents/{agentId}/chat/stream`
- `GET /openapi/v1/agents/{agentId}/health`

可能新增的管理端配置接口：

- 售后 Agent 模板创建 / 初始化接口，或通过现有 Agent Builder 手工创建。
- 售后样例问题和回归脚本接口，首版可只沉淀为文档和测试 fixture。

暂不新增公开客户工单 API，避免提前扩张成 helpdesk 平台。

### 数据

首版可以不新增数据库表，优先利用：

- `agent_definition`
- `agent_workflow_version`
- `agent_kb_binding`
- `agent_tool_binding`
- `agent_channel_binding`
- `skill_definition`
- `skill_version`
- `skill_api_tool`
- `knowledge_base` / `kb_document` / `kb_chunk`
- `agent_api_credential`
- `agent_api_call_log`
- `agent_run_trace`

企业微信渠道需要新增配置与消息日志表：

- `wecom_kf_account`
  - `org_id`
  - `corp_id`
  - `open_kfid`
  - `name`
  - `secret_cipher`
  - `token`
  - `encoding_aes_key_cipher`
  - `agent_id`
  - `run_as_user_id`
  - `enabled`
- `wecom_kf_conversation`
  - `org_id`
  - `corp_id`
  - `open_kfid`
  - `external_userid`
  - `session_id`
  - `agent_id`
  - `run_as_user_id`
  - `last_customer_message_at`
  - `reply_count_in_window`
  - `status`
- `wecom_kf_message`
  - `org_id`
  - `msg_id`
  - `corp_id`
  - `open_kfid`
  - `external_userid`
  - `direction`
  - `msg_type`
  - `content_summary`
  - `trace_id`
  - `send_status`
  - `created_at`

如果 Phase 2 进入工单闭环，可新增：

- `support_ticket`
- `support_ticket_event`
- `support_handoff_queue`
- `support_customer_context_cache`

但这些不属于首版实现范围。

### 配置

建议新增售后 Agent 种子配置或导入包：

- 售后服务 Agent 定义。
- 售后 Skill 模板。
- 售后知识库样例分类。
- 企业微信「微信客服」配置说明。
- 售后 Open API 文档片段作为非企业微信入口补充。

## 任务拆分

- `TASK-063 AI native after-sales agent spec`
  - 输出本 feature spec。
  - 明确定位、边界、首版范围和与现有 FEAT 的关系。

建议后续任务：

- `TASK-064 After-sales agent template seed`
  - 新增售后 Agent 模板、默认提示词、默认渠道和绑定关系。
- `TASK-065 After-sales readonly skill APIs`
  - 封装客户、订单、物流、工单、保修只读 Skill API。
- `TASK-066 WeCom customer service channel`
  - 新增企业微信「微信客服」回调、消息拉取、消息发送、配置存储和会话映射。
- `TASK-067 After-sales handoff summary`
  - 增加转人工规则和结构化交接摘要输出契约。
- `TASK-068 After-sales observability filters`
  - 在管理端观测页支持售后 Agent、外部客户 ID、转人工原因筛选。
- `TASK-069 After-sales Open API smoke`
  - 用 API Key 从非企业微信外部会话调用售后 Agent，验证 session、externalUser、trace 和只读工具。

## 验收标准

首版交付完成时：

- 管理员能创建或初始化 `after-sales-agent`，并绑定售后知识库和只读售后 Skill。
- 微信客户能通过企业微信「微信客服」向售后 Agent 发送文本消息，并收到 AgentCiCi 生成的售后回复。
- 企业微信渠道能正确处理 URL 校验、回调解密、`sync_msg` 拉取、消息去重、`send_msg` 回复和失败记录。
- 企业微信客户身份映射为 `externalUser.id=external_userid`，同一微信客户能持续使用同一 AgentCiCi 会话上下文。
- 非企业微信外部系统仍能通过 Agent Open API 调用售后 Agent，并携带 `externalUser`、`sessionId` 和业务 metadata。
- 常见售后问题能触发正确链路：
  - 政策问题触发 RAG。
  - 订单/物流/工单/保修问题触发对应只读工具。
  - 信息不足时追问必要字段。
  - 高风险请求生成转人工摘要，不承诺已执行。
- `/admin/ops` 能看到该调用对应的 trace、工具调用、知识库命中、企业微信 open_kfid / external_userid / msg_id 或 Open API requestId。
- 权限边界成立：外部客户身份不创建内部用户，不可越过 Key 绑定 Agent、run-as 用户和组织边界。
- 发送窗口边界成立：超出企业微信 48 小时 / 5 条限制时不继续主动发送，记录为待人工处理或发送失败。

建议验证问题：

- “我的订单 SO-001 为什么还没发货？”
- “这个序列号 SN-001 还在保修期吗？”
- “我要退货退款，现在能不能处理？”
- “之前报修过的工单现在处理到哪一步了？”
- “物流显示异常，帮我看看怎么处理。”
- 企业微信文本回调：“我的订单 SO-001 为什么还没发货？”
- 企业微信非文本回调：发送图片或语音时，系统应提示当前先支持文字描述。

建议验证命令：

- 后端售后 Skill/API 相关集成测试。
- 企业微信回调签名、解密、消息去重、`sync_msg` 和 `send_msg` mock 集成测试。
- `AgentOpenApiIntegrationTest` 扩展售后场景。
- `frontend npm run build`，仅当新增管理端 UI 时需要。
- `git diff --check`。

## 风险与回滚

### 风险

- 业务系统对象和字段不稳定，模型使用通用 CloudCC 查询时可能选错对象或字段。
- 知识库质量不足会导致政策回答不完整。
- 写操作若过早开放，可能造成退款、关单、改地址等业务风险。
- 外部客户身份和内部 run-as 用户混淆会导致权限审计不清。
- 若所有售后渠道都在首版接入，范围会膨胀为客服平台重建。
- 企业微信回调加解密、token 缓存、`sync_msg` 游标、消息去重和发送窗口如果处理不好，会出现重复回复、漏消息或无法回复客户。
- 企业微信 `send_msg` 存在 48 小时 / 5 条窗口限制，不能把它当作无限制主动营销或无限客服通知通道。

### 降级

- 首版只开放只读查询和人工接管摘要。
- 写动作全部降级为“建议 / 草稿 / 待人工确认”。
- 如果售后固定 Skill API 未准备好，先使用 CloudCC 对象查询作为内部试运行工具，不对客户直出高风险结论。
- 如果企业微信外部链路不稳定，先在内部工作台、飞书或 Open API mock 渠道进行灰度。
- 如果企业微信发送窗口关闭，降级为生成内部待处理摘要，不主动继续发消息。

### 回滚

- 停用 `after-sales-agent` 的 `wechat_kf` channel。
- 在企业微信后台关闭对应客服账号的 API 管理或回调 URL。
- 停用 `after-sales-agent` 的 `api` channel。
- 撤销或停用售后 Agent 的 API Key。
- 禁用售后 Skill 或平台 runtime tool。
- 回退到人工客服或现有 CloudCC/CRM 工单流程。

## 实现进展

- 状态：in_progress。
- 已完成项：
  - 已结合客服/售后自动化市场洞察和当前 AgentCiCi 系统能力，明确 AgentCiCi 的售后方向是 AI 原生 Agent 层，而不是完整 helpdesk 重建。
  - 已定义首版范围、售后 Agent 模板、Skill/API 分层、外部上下文契约、人工接管规则、观测指标、验收标准和风险边界。
  - 已按最新方向补充企业微信「微信客服」首版接入方案，明确回调、`sync_msg`、`send_msg`、`external_userid` 映射、会话窗口和消息日志模型。
  - 已完成 `TASK-066` 企业微信「微信客服」渠道基础实现：`V42__wecom_kf_channel.sql`、`/wecom/kf/callback` GET/POST、SHA1 签名校验、AES-CBC 解密、`sync_msg` / `send_msg` 客户端、`wecom-kf:*` 会话映射、消息日志、48 小时 / 5 条窗口检查和 `wechat_kf` trace 标记。
  - 已新增内置 `after-sales-agent` 售后服务 Agent 种子，用于企业微信微信客服外部客户会话；当前阶段只基于绑定知识库和客户文字描述沟通，不查询/操作 CRM、订单、客户档案、工单或物流系统。
  - 已实现企业微信 `wecom-kf:*` 会话知识库优先运行策略：有默认知识库时默认触发 RAG，且不加载 CRM/CloudCC/邮件/外部搜索等业务工具定义，避免微信客服客户消息触发业务系统操作。
  - 已新增组织管理 API `/admin/wecom/kf-accounts`，支持写入企业微信微信客服 CorpID、Secret、Token、EncodingAESKey、`open_kfid`、`agent_id`、`run_as_user_id`，其中 Secret 与 EncodingAESKey 加密落库，响应不回显密钥。
  - 已新增企业微信「微信客服」可视化管理端配置页面 `/admin/channels/wechat-kf`：组织管理员可查看账号列表、创建/更新客服配置、启停账号、选择售后 Agent 与 run-as 服务用户，并复制企业微信后台需要填写的回调 URL。页面路由避开 `/admin/wecom` API 代理前缀。
- 未完成项：
  - 尚未封装售后只读 Skill API。
  - 尚未做真实 CloudCC/CRM 售后对象映射。
  - 尚未执行企业微信真实回调 smoke 或 Open API 售后调用 smoke。

## 交接说明

- 下一位接手者先看 FEAT-021、FEAT-015、FEAT-018 和本 spec。
- 继续实现前需要确认：
  - 售后数据主系统是 CloudCC、独立工单系统，还是其他 CRM/ERP/OMS。
  - 客户、订单、物流、工单、产品、设备、保修这些对象的真实 API 和字段。
  - 企业微信接入使用「微信客服」的哪个客服账号 `open_kfid`，是否需要多个客服账号绑定不同 Agent。
  - 企业微信渠道的 run-as 服务用户是谁，以及 `after-sales-agent` 应绑定哪些售后知识库。
  - 客户门户、官网组件、CRM 页面、飞书是否仍作为首版并行入口，还是排到企业微信之后。
  - 是否允许 Phase 2 新增 AgentCiCi 自有 `support_ticket` 表，或必须完全使用外部工单系统。
