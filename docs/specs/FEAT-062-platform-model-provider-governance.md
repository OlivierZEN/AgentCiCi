---
kind: feature-spec
feature_id: FEAT-062
title: Platform Model Provider Governance
status: implemented
owner_role: project-manager
task_ids: TASK-145, TASK-153, TASK-349, TASK-352
related_decisions: FEAT-003, FEAT-022, FEAT-037
related_issues: none
updated_at: 2026-09-01T14:51:11Z
updated_by: codex
---

# FEAT-062 平台统一模型厂商治理

## 背景

计费策略已确认：`Work Credits` 是组织可理解的智能体工作量口径，模型 token、平台代付资源、客户自有本地资源和第三方服务需要被平台统一归因和治理。若组织管理员自行配置模型厂商、API Key、base URL 和模型目录，平台无法稳定判断资源付款责任、模型档位、credit 折算和供应商可用性，也容易造成客户自有资源与平台代付资源混淆。

因此，模型厂商配置从组织后台上收至运营管理平台，由平台运营方统一配置和启停。组织管理员不再维护模型厂商凭据或模型目录，只在计费用量页查看当前版本、credits 余额、消耗和明细。

## 目标

- 关闭组织后台的模型厂商配置入口。
- 组织管理员默认只通过 `/admin/billing` 查看当前 credits 消耗、余额、quota 和 ledger。
- 平台运营在 `/platform/models` 统一管理模型厂商启停、API 地址、API Key、可用模型和模型目录。
- 平台运营在同一页面维护场景级模型路由，保留“不同场景使用不同模型”的运行时能力。
- 运行时模型、Agent Builder 基础模型候选和知识库 embedding 模型候选均来自平台统一模型厂商配置。
- 组织用户和组织管理员不能通过组织 API 修改模型厂商配置。

## 非目标

- 本任务不实现完整模型价格表、模型档位 rating 或 token 到 credits 的最终财务折算。
- 本任务不迁移历史组织级模型配置数据，只改变后续读写事实源。
- 本任务不改变 Agent、Skill、知识库的业务权限模型。
- 本任务不新增移动端布局或移动端测试。

## TASK-153 补充：平台统一第三方 AI 集成配置

用户反馈要求把科大讯飞实时转写与 Tavily Search 的集成配置从组织后台上收到运营管理端统一控制，并在模型厂商治理中增加 OneKeyToken token 中转站方案。依据来源为本地文档 `/Volumes/AISpace/AI/KB/cloudcc/onekeytoken-developer-integration-guide.md`，其中 OneKeyToken 生产 Base URL 为 `https://my.onekeytoken.com/v1`，采用 OpenAI Chat Completions 兼容协议，推荐默认模型为 `onekeytoken/auto`，服务端使用 `Authorization: Bearer <OneKeyToken Key>`，业务调用应使用唯一 `x-request-id` 并可按客户传 `x-customer-id`。

### 目标

- Tavily Search 与科大讯飞实时转写配置由运营平台统一维护，不再由组织管理员在 `/admin/integrations` 配置。
- 组织后台的集成应用页只保留组织自有或租户侧连接器，例如 CloudCC CRM、飞书机器人。
- 组织 token 对 Tavily 与讯飞集成写接口必须被拒绝，避免租户绕过运营治理。
- 运行时 Tavily 工具与讯飞实时 ASR 从平台治理作用域读取配置。
- `/platform/models` 增加 OneKeyToken 厂商，默认 API 地址 `https://my.onekeytoken.com/v1`。OneKeyToken 当前生产接入以 Chat Completions 为主，不承诺开放 OpenAI-compatible `/models` 枚举；平台模型列表使用接入指南中的静态模型目录，避免把网关 404 误报成候选模型异常。

### 非目标

- 不在本任务中接入 OneKeyToken 的应用级客户钱包、`x-customer-id` 归因或账单对账。
- 不迁移历史租户行中的 Tavily/讯飞密钥；后续由平台运营重新配置或另开迁移任务。
- 不改变 CloudCC CRM、飞书机器人等租户自有集成的组织后台配置归属。

### 后端设计

- `IntegrationAppService` 继续复用 `integration_app`，但为 Tavily 与讯飞定义 platform-managed app code。
- 平台端新增 `/platform/integrations`：
  - `GET /platform/integrations` 返回平台托管集成。
  - `PUT /platform/integrations/{appCode}` 更新启停、描述和配置。
  - `POST /platform/integrations/tavily/test` 测试 Tavily 连接。
- 组织端 `/integrations`：
  - 列表过滤掉平台托管集成。
  - 更新或测试平台托管集成返回 403，并提示由运营平台统一配置。
- `findRawConfig()` 与 `isEnabled()` 对平台托管 app code 自动解析到平台治理组织，运行时调用方仍传业务 `orgId` 以保持签名稳定。

### 前端设计

- `/admin/integrations` 不展示 Tavily 与讯飞，只保留组织级集成。
- `/platform/integrations` 复用现有集成卡片与配置弹窗，纳入运营控制台导航。
- `/platform/models` 厂商列表中新增 OneKeyToken，展示默认文档链接、API 地址和推荐模型目录。

### 验收标准

- 组织后台集成应用页不显示 Tavily 和讯飞。
- 组织 token 更新 `/integrations/tavily` 或 `/integrations/iflytek_asr` 返回 403。
- 平台 token 可在 `/platform/integrations` 查看、更新 Tavily 和讯飞，并可测试 Tavily。
- Tavily/讯飞运行时配置读取平台治理作用域。
- `/platform/models` 列表包含 `onekeytoken`，默认地址为 `https://my.onekeytoken.com/v1`，推荐/静态模型含 `onekeytoken/auto`、`deepseek-chat`、`qwen3.5-flash`。

## 权限与事实源

- 平台事实源：`model_provider_config` 继续复用现有表结构，但写入平台治理组织 `app.auth.bootstrap-platform-account.governance-org-id`，默认 `demo-org`。
- 组织后台：
  - 不展示 `/admin/models` 导航。
  - `/admin/models` 路由重定向到 `/admin/billing`。
  - `/models/providers/**` 组织侧写接口返回 403。
- 平台后台：
  - 新增 `/platform/models` 页面。
  - 新增 `/platform/models/providers`、`/platform/models/providers/{providerCode}`、检测、拉取模型和已选模型接口。
  - 新增 `/platform/models/routes` 和 `/platform/models/routes/{sceneCode}`，由平台角色维护场景模型路由。
  - 所有接口必须使用 `@RequirePlatformRole`。

## 运行时规则

- `ModelProviderService.credentialsForProvider(orgId, providerCode)` 改为读取平台统一厂商配置，不读取组织自己的 provider 配置。
- `agentBaseModels(orgId)` 和 `embeddingModelOptions(orgId)` 改为读取平台统一可用模型。
- `providerCode`、`modelName` 仍可作为 Agent 或知识库配置字段保存，但候选列表由平台控制。
- 运行时模型选择顺序：
  1. 先读取平台治理作用域下的场景路由，当前首批场景为 `chat`、`skill-authoring`、`meeting-minutes`、`customer-insight`。
  2. 场景路由存在且模型仍在平台已选模型目录中时直接使用该路由。
  3. 场景路由缺失或失效时，若 Agent 自身模型偏好仍在平台已选模型目录中，则使用 Agent 偏好。
  4. 仍未命中时，退回平台已选模型目录中的第一个模型。
- 组织侧历史 `org_model_config` 不再作为运行时事实源；平台场景路由复用该表结构但写入平台治理组织。
- 私有化部署下，平台可配置本地模型或客户自有模型作为 `customer_paid` 资源；本任务只建立治理入口，不做强扣费。

## UI 设计

本次属于产品 register 的平台治理页面。视觉延续 `鎏金账房`：暖象牙底、墨色文字、紧凑密度、香槟金结构线；不引入新品牌视觉、深色命令中心或营销页式 hero。

平台模型页面采用左侧厂商列表 + 右侧配置面板 + 场景模型路由 + 已选模型列表的结构，保持与现有平台工具治理页一致的扫描节奏。组织后台不提供替代配置说明页，避免给组织管理员造成“仍可配置”的暗示。

## 验收标准

- 组织后台导航不再出现“模型”。
- 访问 `/admin/models` 会进入 `/admin/billing`。
- 组织 token 调用 `/models/providers/{providerCode}`、`/models/providers/{providerCode}/check`、`/models/providers/{providerCode}/models/fetch`、`/models/providers/{providerCode}/selected-models` 均被拒绝。
- 平台 token 可以在 `/platform/models` 查看、更新、检测、拉取并维护已选模型。
- 平台 token 可以配置 `chat` 等场景路由；运行时优先使用有效场景路由，而不是无条件取平台已选模型的第一个。
- Agent Builder 和知识库 embedding 候选模型来自平台统一配置。
- 后端 focused tests 和前端 build/unit checks 通过。

## TASK-349 补充：讯飞实时语音转写纳入模型厂商治理列表

### 背景与目标

早期接入的科大讯飞实时语音转写已经由平台级 `iflytek_asr` 集成记录保存凭据，并由 `/ws/asr` 运行链路读取，但运营入口仍位于通用“集成配置”，与“模型厂商治理”中已经声明的 `realtime-asr` 能力割裂。平台运营无法在统一厂商列表中看到和维护这项模型能力。

本次把科大讯飞作为“实时语音转写”能力厂商放入 `/platform/models` 的“模型厂商治理”列表。页面负责读取、保存和校验原有平台托管记录，不新建第二份模型厂商凭据，不迁移运行时事实源。

### 设计与边界

- 模型厂商列表新增“科大讯飞”，能力类型固定为 `realtime-asr`，配置字段沿用既有 App ID、Access Key ID、Access Key Secret、Realtime URL、语言和领域。
- 保存动作继续写入平台治理作用域的 `integration_app(iflytek_asr)`；Access Key Secret 继续加密存储且只返回掩码。
- `/platform/integrations` 不再重复列出讯飞实时转写，避免两个运营入口维护同一记录；Tavily、代码解释器、联网搜索和网页抓取保持不变。
- `meeting-realtime-asr` 场景路由可选择一个固定的 `iflytek-realtime-asr` 适配器候选；该候选只代表已实现的讯飞流式协议，不进入通用模型目录，也不复制凭据。
- `/ws/asr` 保留两条明确的产品路径：对话框实时听写调用固定阿里云 `paraformer-realtime-v2` 协议，API Key 只从模型厂商治理中的阿里云记录读取；AI 听记显式请求 `iflytek` 并以 `meeting-realtime-asr` 路由为权威配置。
- AI 听记选中讯飞时，从原有 `integration_app(iflytek_asr)` 读取凭据并携带 `role_type=2` 启用发言人区分；对话框的 `speakerDiarization` 参数不能将请求隐式切到讯飞。
- 配置检测只验证启用状态、必填凭据与 `wss` 地址结构，不声称已完成真实讯飞网络调用。真实能力验收必须发起一次实时语音识别并收到转写结果。
- 不把讯飞伪装成 OpenAI-compatible 模型目录，不提供“全部模型”、人工能力确认或其他通用模型场景操作；仅在专用 `meeting-realtime-asr` 场景暴露固定受管适配器候选。

### 验收标准

- 平台 token 调用 `GET /platform/models/providers` 能看到 `iflytek_asr`，并回读 `providerKind=realtime-asr`、启停状态、能力和脱敏配置。
- 平台 token 可通过 `PUT /platform/models/providers/iflytek_asr` 保存配置，Secret 不回传明文，运行时仍能从原有平台托管记录读取。
- 平台 token 可通过模型厂商入口执行配置校验，缺少凭据或非 `wss` 地址时失败关闭；通过时返回 `runtimeProbeRequired=true`。
- `GET /platform/integrations` 不再返回 `iflytek_asr`，组织侧仍不可见、不可写。
- `/platform/models` 显示“科大讯飞 / 实时语音转写”，提供专用凭据表单，不显示“全部模型”和通用模型目录操作。
- 启用且凭据完整时，`meeting-realtime-asr` 场景显示“实时语音转写 · 科大讯飞”，平台管理员可选中并保存；运行时路由必须实际进入讯飞 WebSocket 适配器。
- 历史上已保存为 `voice-asr=iflytek_asr` 的平台路由自动迁移到 `meeting-realtime-asr`，并删除错误的对话场景讯飞选择；不修改或复制 Secret。
- 后端聚焦测试、前端聚焦测试、前端 production build 与 `git diff --check` 通过。

## TASK-352 补充：实时听写运行协议与结束状态收敛

### 用户问题与根因

- AI 听记无法产生实时文字，停止后持续显示“正在结束”；对话框话筒也只显示“实时听写中”但不出字。
- 当前平台记录把官方讯飞主机保存为 `wss://office-api-ast-dx.iflyaisol.com/`，缺少实时转写大模型协议路径 `/ast/communicate/v1`。既有配置检查只验证 `wss` 结构，无法发现该错误。
- 浏览器在业务 WebSocket 打开后立即申请麦克风并显示听写中，没有等待讯飞上游 `started`；上游启动失败或一直未就绪时形成假录音态。
- 讯飞官方最后一帧以 `data.ls=true` 标识。既有解析器在把 `data` 解包为 payload 后仍只检查嵌套 `payload.data.*`，且空最后帧会提前返回，导致 `finished` 丢失。
- 前端完成回调只依赖浏览器 WebSocket `close`；上游最终帧、异常关闭或关闭事件延迟时，AI 听记和对话框状态不能确定收敛。

### 设计与验收

- 对官方主机的空路径或根路径在保存、读取、校验和运行时统一规范为官方实时转写大模型地址；自定义 `wss` 兼容地址保持原值。
- 浏览器必须收到后端转发的上游 `started` 后才申请麦克风、发送音频和显示听写中；错误、关闭或 8 秒无 `started` 时退出启动流程并展示明确失败信息。
- 识别 `payload.ls=true`、兼容嵌套 `data.ls` / `status=2`，最后帧无文字也必须发送一次 `finished`。
- 停止时先停止音频、按序发送 end；收到 `finished` 立即收敛，未收到时 1.5 秒关闭兜底也必须且只执行一次完成回调。
- AI 听记与对话框共用同一 `useAsrVoiceInput` 录音生命周期，但不共用厂商路由：AI 听记显式走讯飞并启用发言人区分，普通对话听写显式或默认走阿里云。
- 错误、最终帧和异常关闭均不得永久停在 recording/stopping。
- 技术验收覆盖 URL 规范化、官方最后帧、上游 ready/error、全量前端测试、production build、backend package 和本地正式环境指纹；真实麦克风转写仍由 HUMAN 最终接受。
