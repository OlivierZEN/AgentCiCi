---
kind: test-report
version: 4
updated_at: 2026-09-01T14:46:51Z
updated_by: codex
status: active
last_run_at: 2026-09-01T14:46:51Z
last_run_status: passed_task_351_local_runtime_pending_provider_refresh
---

# Test Report

## 2026-09-01 TASK-351 OneKeyToken 自动路由视觉能力同步

- 状态：`passed_task_351_local_runtime_pending_provider_refresh`。OneKeyToken `onekeytoken/auto` 同图直调已成功，网关回读 `request_type=vision`、`model_used=qwen3.7-plus`；AgentCiCi 409 根因是平台校验把 auto 的可信能力硬编码为仅 `text`。
- 实现：OneKeyToken Chat Completions 活性校验成功后，使用同一组有效草稿/已存配置读取 `/models`，复用既有 `capabilities` / `input_modalities` 解析器并以 `provider_catalog` 保存能力；不按下游实际模型名猜测能力，目录失败继续失败关闭。
- 聚焦集成：隔离 PostgreSQL 16 上，`PlatformModelProviderIntegrationTest` 的 OneKey 草稿凭据能力同步与场景候选两个目标方法 `2/2` 通过；断言 `/chat/completions` 与 `/models` 均使用草稿 key、已存 key 未被覆盖、`onekeytoken/auto=[text,vision]` 且 vision 门禁为 true。
- 相邻回归：`ModelProviderServiceTest,ChatAttachmentServiceTest,ChatOrchestratorServiceModelIdentityTest` 通过；`mvn -q -DskipTests package` 和 `git diff --check` 通过。
- 测试边界：默认测试库不可达；完整 `PlatformModelProviderIntegrationTest` 在显式关闭既有 OACT 配置漂移后有 4 项通过，唯一失败是无关组织登录用例因 OACT 被关闭返回 403，因此仅声明本任务目标方法通过，不声明整类通过。
- 本地主线与制品：实现提交 `653e5e1d7993` 已进入本地 `main`；宿主机同提交 package 的 JAR SHA-256 为 `ee35da41dafb12b9f8be4e153871ba3cf5890431547825b688de3ba48b2f21ec`，运行镜像为 `sha256:6dc27f9b6183`，OCI/容器/version API 均回读 `2.8.68-dev.653e5e1 / 653e5e1d7993`。
- 运行门禁：backend health=`UP`、container healthy/restart=0，`https://cici.localhost/=200`；只有 backend ID 从 `4eefc48f4891` 变为 `646053c55528`，frontend 与 PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 均未替换。
- 刷新前证据与边界：数据库仍为 `onekeytoken/auto=[text]`、证据来源 `provider_catalog`；已登录平台页已停在“检测”按钮前。该点击会把已存 API Key 发送到已配置的 OneKeyToken 端点，待用户即时确认后执行并回读 `text + vision`，随后再做真实图片对话。远程、UAT、生产未修改。

## 2026-09-01 TASK-348 对外售前智能体与 Mary 演示

- 状态：`passed_task_348_local_technical_and_browser`。服务端状态机、公开能力隔离、联系方式线索、访问摘要和再访选择完成；目标租户 Mary 通过正式 API 发布 v4，未直写数据库。UAT/生产未修改，当前证据不替代外部 HUMAN 业务接受。
- 自动化与构建：后端 `WebsitePresalesLifecycleServiceTest,SisiEmbedRuntimeServiceTest,PublicWebWidgetServiceTest` 通过，`mvn -q -DskipTests package` 通过；前端全量基线 `61 files / 338 tests`、最终契约聚焦 `3/3`、production build、环境域名门禁和 `git diff --check` 通过。默认全量 Maven suite 因本机 `localhost:5432` 不可用而中止，不记为通过。
- 配置：`org3gxskla32gln3bvop / 客服-Mary` 回读为 Website-only、STRICT/COPILOT、`public-presales-v1`、零工具、零知识库、零可选技能，发布版本 v4；Website 发布启用且配置固定售前欢迎语。本地只有 Owner/Admin 两个 ACTIVE 成员，故演示保留既有 Owner 运行映射；公开运行时仍强制零工具、零用户记忆、零附件，生产必须换成专用最小权限成员或 SERVICE Principal。
- 真实售中售后链路：账号无法登录问题直接进入 `SERVICE_REDIRECTED` 并返回登录 CloudCC 后提交在线工单的固定引导；`agent_run_trace` 前后数量不变，证明模型与工具均未调用。
- 真实联系方式链路：含手机号的售前请求进入 `COMPLETED`、`contactCaptured=true`、`canSend=false`；线索仅一条，密文非空且不含手机号明文，聊天消息也不含原始号码并包含脱敏值。
- 真实再访链路：同一访客第二次访问创建不同聊天会话并进入 `AWAITING_CHOICE`；同一 visit 刷新复用当前会话；`START_NEW` 后为 `ACTIVE` 且继承摘要为空。此前摘要只以脱敏结构化文本供选择性继续，原始历史不自动注入。
- 真实售前链路：产品咨询完成一次正常模型回答并提出一个资格澄清问题；Trace 为 `model_calls=1 / tool_calls=0 / rag=0`。
- 本地主线与制品：实现 `1ffc9092`、选择态修复 `c5726b28`、输入提示修复 `6fa12d73` 均进入本地 `main@6fa12d731b84827bf2897b7df4023b28245daa5a`。backend/frontend 运行 `2.8.68-dev.6fa12d7`，镜像分别为 `sha256:e21e740ea5a0500de9155e79d3eee3d2aa82dbdd678891c11b2ffb17175213bc` / `sha256:6b17aca2350c292c5113d611bd8b8a19a7b00e93034150860f0b533e17b57fcc`，label/revision 一致、healthy/restart=0；Flyway V129 成功，`/system/version`、官网、Embed、公开 widget 和匿名 401 边界通过。
- 浏览器：正式门户加载 Mary 启动器并展开；DOM 回读 `hasMary=true / hasResume=true / hasClosedCard=false / hasCorrectPlaceholder=true / hasSupportBoundary=true`，确认再访选择态不误显示完成卡片，输入框明确要求先选择，售中售后边界可见。
- 构建边界：release Dockerfile 的容器内 Maven 构建因 Maven Central DNS/超时失败；随后从同一干净 commit 在宿主机完成 Maven package，并使用等价 release runtime stage 生成 backend 镜像，最终镜像 revision 与运行 commit 一致。完整 `./stack verify` 未通过既有 Semattice `config=1.0.7 / repository=1.0.8` 漂移门禁，不把定向产品验证写成完整全栈门禁通过。
- 工单入口边界：本地部署未配置 HTTPS 工单 URL，因此当前只显示登录 CloudCC 后提交在线工单的文字引导，不渲染外链按钮；生产启用时由部署环境提供并由后端校验。

## 2026-09-01 TASK-350 门户统一身份完整注销自动化

- 状态：`passed_task_350_local_technical_pending_human_logout`。根因是门户退出只清 AgentCiCi 本地 Token，未终止 Keycloak SSO；访客态自动 OIDC 随即使用仍有效的 SSO 会话静默重登。
- 实现：OIDC callback 以加密服务端会话保留 ID/Refresh Token，浏览器只持有 HttpOnly/Secure/SameSite=Lax 随机会话 Cookie；同源 `/auth/oidc/logout` 一次性消费会话并生成含 `id_token_hint`、`client_id`、固定同源 `/app` 回跳的 RP-Initiated Logout，前端退出先清业务状态再硬跳转该入口。
- 后端：`KeycloakOidcLoginServiceTest,OidcLoginStateStoreTest,AuthControllerTest` 共 12 项通过；`mvn -q -DskipTests package` 通过。默认全量 suite 在既有 `KnowledgeBaseLifecycleIntegrationTest` 初始化时连接 `localhost:5432` 被拒，17 项环境错误后中止，不记为全量通过。
- 前端：聚焦 6 项、全量 `62 files / 339 tests`、production build 通过；build 仅保留既有大 chunk warning。`git diff --check` 与业务/前端源码真实环境域名扫描通过。
- 本地配置：父仓 `e8f8705 / INT-031 / TASK-021` 令受管 Keycloak 配置脚本精确写入同源 `/app`；脚本重复执行后完整 Client JSON 回读 `post.logout.redirect.uris=https://cici.localhost/app`，其他 Client 未改。
- 本地主线与制品：注销实现提交 `07414618` 已包含于本地 `main@6d0f9523da8c`；backend/frontend 均构建为 `2.8.68-dev.6d0f952`，镜像分别为 `sha256:b1c3ea0f2452` / `sha256:ea928d869e08`，运行 label/revision 一致、healthy/restart=0。PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 容器 ID 未变化。
- HTTP 与日志：`/system/version` 回读 commit/version 一致；无浏览器 Cookie 的 `/auth/oidc/logout` 返回 302 到 Keycloak end-session，包含 `client_id` 与固定 `/app`，并清除 HttpOnly/Secure/SameSite=Lax Cookie；近 10 分钟 backend error 与 frontend 结构化 5xx 均为 0。
- HUMAN 边界：应用内浏览器当前停在本地 Keycloak 登录页；真实登录、点击门户退出并确认停留统一登录页尚未执行，不能用无登录态 302 代替。

## 2026-09-01 TASK-349 讯飞实时语音转写模型厂商治理

- 状态：`passed_task_349_iflytek_route_candidate_pending_live_asr`。用户授权态截图证明厂商已配置但 `voice-asr` 候选为 0；根因是场景只允许阿里云、候选只读通用模型表，且 WebSocket 运行时硬拒绝非阿里云路由。
- 实现：`4c057b6e2cc8` 将启用且凭据完整的原 `integration_app(iflytek_asr)` 投影为固定 `iflytek-realtime-asr` 候选，只用于 `voice-asr`；通用模型目录不包含讯飞，Secret 事实源不变。`/ws/asr` 按平台路由选择讯飞，并恢复音频发送、停止、关闭和完成态。
- 自动化：`PlatformModelProviderIntegrationTest#configuredIflytekAdapterCanBeSelectedForRealtimeAsrRoute` 为 `1/1`，`RealtimeAsrProviderSelectionTest` 为 `4/4`，`ModelProviderServiceTest` 为 `1/1`；backend package 与 `git diff --check` 通过。聚焦 Spring 用例使用隔离 PostgreSQL 16，因既有 OACT 测试配置漂移显式关闭该无关能力；默认完整类不记为通过。
- 本地主线与制品：修复进入本地 `main`；backend 运行 `2.8.68-dev.4c057b6 / 4c057b6e2cc8`，镜像 `sha256:52ee9e301d`、OCI label、容器环境与版本 API 一致，health UP、restart=0、近 10 分钟 severe=0。
- 最小影响：只替换 backend，其他容器 ID 均未变化；frontend 保持既有制品，因为本轮没有前端源码变化。`https://cici.localhost/` 与 `/platform/models/providers` 为 200。
- 浏览器：Chrome 既有平台登录态回读实时语音行“已验证候选：1 个”，下拉 enabled 且包含“实时语音转写 · 科大讯飞”；桌面布局正常，console error/warning=0。未代平台管理员保存路由，也未请求麦克风权限或发送音频；真实讯飞网络握手和转写结果仍待 HUMAN。
- 全栈边界：未执行受既有 Semattice `config=1.0.7 / repository=1.0.8` 漂移阻断的完整 `./stack verify`；不把定向产品验证写成完整全栈门禁通过。UAT、生产、远程、ACR 与 tag 未修改。

## 2026-09-01 TASK-347 Web 浮窗内部状态过滤自动化

- 状态：`passed_task_347_widget_status_filter_local_pending_user_review`。Website 浮窗渲染边界按 `mode=float` 统一过滤内部 busy label，保留无文字三点等待动效；完整 `page` 嵌入仍可展示既有阶段。
- 自动化：`SisiEmbedPage.test.ts` 聚焦 `8/8`，覆盖“正在选择所需工具”和“正在安全执行工具”在 float 模式返回不可见，同时 page 模式保留“正在生成回复”；前端全量 `60 files / 334 tests` 通过。
- 构建：frontend production build 与 `git diff --check` 通过；build 仅保留既有大 chunk warning。
- 本地主线与运行：实现 `629e35db6b89` 已进入本地 `main`；frontend 从该提交构建为 `2.8.68-dev.629e35d`，镜像 `sha256:065bb91e5394`，容器 healthy/restart=0。backend 未修改，保持 `2.8.68-dev.1908000 / 19080005f847`、health UP；PostgreSQL、Redis、RabbitMQ、Qdrant 容器 ID 未变化且 restart=0。
- 路由与制品：官网和 float Embed 均为 200，部署 JS `index-BIMVIX2F-2.8.68-dev.629e35d.js` 含版本指纹；Nginx 配置有效，近 10 分钟结构化 5xx=0。
- 浏览器：float shell=1、可见 `.bubble-thinking__label`=0、“正在选择所需工具”=0、“正在安全执行工具”=0；桌面截图布局正常，console error/warning=0。
- HUMAN 边界：本次浏览器未代用户发送新的模型消息；真实等待周期是否只出现三点动效仍待用户发送一条新消息目视接受。自动化已覆盖 float/page 分流，但不把该技术证明替代 HUMAN 感知验收。

## 2026-08-31 生产微信客服回调签名复核

- 状态：`passed_wecom_kf_callback_replay_pending_platform_save`。企业微信两条真实验证请求的完整参数到达生产 backend，但在租户重新保存配置前返回403；配置保存后，在服务器内部重放同一企业微信签名请求返回 `200 text/plain`、18 bytes，未输出签名、密文或正文。
- 官方连接：生产登录态“测试连接”回读通过，access-token缓存成功刷新，证明 CorpID、API管理应用Secret与企业微信官方端点可用；该租户账号保持启用，Agent与服务用户绑定存在。
- 运行回归：AgentCiCi `2.8.67 / 2970bea75208`，frontend/backend healthy/restart=0、Nginx配置有效、backend health UP，AgentCiCi首页/匿名401、DevAutopilot、Semattice健康/匿名401和Keycloak discovery均通过。最近ERROR仅来自本次缺少必填签名参数的负向诊断请求。
- 验收边界：当前配置已满足服务端回调验证；企业微信管理后台因站点安全策略不能由agent自动点击，需HUMAN重新保存并由一条新的外部200回调确认平台状态。尚无真实客户消息、sync_msg或send_msg业务验收，不将内部重放等同于完整客户对话。

## 2026-08-31 企业微信可信域名生产校验

- 状态：`passed_wecom_domain_verification_production_technical`。变更前确认 HTTP 404、HTTPS SPA HTML 假200；变更后 `http://agentcici.com/WW_verify_k3ew8Iachbzg5pIw.txt` 与 HTTPS 同路径均为 `200 text/plain`、16 bytes，响应 SHA-256 `29980dcc2e72150f56de749b3c6b1a27d46216ec1f261b3173898512d7976bdd` 与用户文件完全一致。
- 配置与恢复：候选及运行中 Nginx 配置均通过 `nginx -t`，当前配置 SHA-256 为 `85f706c41e6567369420e5659f9409cc999e82d7c25fa7473d3614bf3f2c3112`；发布前配置 SHA-256 为 `7d95f4b2982473dce689a8bc895a9cd62ce64d9489140135e5c624d11c91d523`，备份目录 `/opt/cici/backups/20260831T081349Z-before-wecom-domain-verification` 的两个文件均非空、root-owned、0600。
- 运行回归：frontend/backend 保持生产 `2.8.67 / 2970bea75208`、容器 ID 不变、healthy/restart=0，backend health=`UP`；AgentCiCi 首页、匿名401、DevAutopilot、Semattice健康/匿名401和Keycloak discovery 的生产只读 smoke 全部通过，frontend 近5分钟 severe=0。
- HUMAN 边界：已完成服务端域名归属技术条件；Chrome 中虽有已登录的企业微信管理页，但站点安全策略禁止自动化接管，因此未执行或宣称后台最终“保存/校验”操作，需管理员手动点击。

## 2026-08-31 TASK-347 Web 浮窗真实流式

- 状态：`passed_task_347_local_streaming_pending_user_review`；确定性直答、流式安全门禁、显式 buffered 降级、阶段提示、自动化、构建、同提交本地制品和真实浏览器增量均通过，UAT/生产未修改。
- 后端：`ToolPlanningIntentRouterTest,GuardedAssistantStreamTest,SafetyGatewayServiceTest,DeliveryWriteReceiptGuardTest,ChatOrchestratorServiceModelIdentityTest,ChatOrchestratorSseErrorTest,AgentRunTraceServiceTest` 通过；其中编排集成断言供应商首 delta 在 provider 返回前已进入 emitter，且普通咨询只有一次模型调用。
- 构建：backend `mvn -q -DskipTests package`、前端 60 files / 333 tests、production build 与 `git diff --check` 通过。依赖默认数据库的 `AdminBillingIntegrationTest` 因本机无可用数据库持续等待后中止，未记为通过。
- 真实 Trace：demo `org3gxskla32gln3bvop / sales-agent` 通过公开 widget/token/SSE 链路完成；`total=10.385s, model_call_count=1, tool_call_count=0, firstProviderDelta=8.246s, firstClientDelta=8.320s, outputMode=streaming`，共 9 个 delta、172 字且首 delta 早于 done。
- 浏览器：正式 `https://cici.localhost/` 浮窗发送后约 285ms 显示“正在理解问题”；本次模型首段 14.749s、完成 18.616s，中间约 3.867s 多次增长，最终 424 字、输入恢复、console error/warning=0。
- 环境边界：backend/frontend 为 `2.8.68-dev.1908000 / 19080005f847` 且 healthy/restart=0。完整 `./stack verify` 仍被任务外 Semattice `config=1.0.7 / repository=1.0.8` 漂移失败关闭，不将定向产品验证扩大为全栈门禁通过。

## 2026-08-31 TASK-346 OpenAPI 文件附件统一运行时

- 状态：`passed_task_346_uat_technical_pending_authenticated_openapi_image_acceptance`；真实二进制存储、共享附件桥接、URL 导入、失败关闭、本地运行和 UAT 技术门禁通过，真实 OpenAPI Key 下的真实模型图片识别待 HUMAN 授权验收。
- 后端聚焦：`SafeRemoteFileFetcherTest,AgentOpenApiAttachmentServiceTest,AgentOpenApiConversationServiceTest,ChatAttachmentServiceTest` 通过；覆盖 HTTPS/私网拒绝、PNG 实际字节落盘与 SHA 路径、四层作用域、共享附件 ID、图片 data URL content block 和对话编排。
- 控制器/迁移：隔离 `postgres:16.9-alpine` 从空库成功执行 125 个迁移到 Flyway V128；`shouldExposeConversationApiChatMessagesHistoryFeedbackAndFiles` 与 `shouldImportHttpsFileIdempotentlyAndUseInlineUrlThroughAttachmentRuntime` 通过，证明 multipart/URL 幂等记录都能在创建内部会话后写入有外键约束的共享附件并传给 OpenAPI runtime。
- 构建：`mvn -q -DskipTests package`、前端 `npm test -- --run`（60 files / 332 tests）、`npm run build` 和 `git diff --check` 通过；前端 build 仅保留既有大 chunk warning。
- 全类边界：`AgentOpenApiIntegrationTest` 17 项中 16 项通过；唯一失败 `shouldReplacePlaceholderChatRouteWithConfiguredBaseModelForOpenApiChatMessages` 期望 `aliyun-bailian`、实际为 `mock`，与附件新增断言无关。未把完整类记为通过，也未在本任务中修改该既有模型路由行为。
- 安全边界：远端 URL 仅 HTTPS，不透传调用方 Header/Cookie，初始地址和每次重定向逐跳解析并拒绝本机、私网、link-local、CGNAT、metadata、保留/文档地址；下载和读取均限制 15 MiB。实现仍运行在应用进程内，不把 DNS 预检描述为独立网络沙箱。
- 本地主线与运行：功能提交 `3b34e3198938` 已进入本地 `main`；并行主线推进后从包含该提交的最新代码主线 `40a27a2b2983` 构建 backend/frontend，运行 `2.8.68-dev.40a27a2`。两容器 healthy/restart=0，backend health=`UP`，Flyway `128:openapi attachment runtime:true`，首页 200，匿名 `/openapi/v1/parameters` 返回 `401 agent_api_key_missing`，近 5 分钟 backend/frontend severe=0。
- 环境边界：仅最小重建 backend/frontend；PostgreSQL、Redis、RabbitMQ、Qdrant ID 保持 `fe24af69`、`2a889418`、`78a141ae`、`000ccca4` 且 restart=0。未执行受既有 Semattice 版本漂移影响的完整 stack verify；本任务无新增跨项目契约。
- HUMAN 边界：当前没有可读取的明文 OpenAPI Key。浏览器已有登录态，但创建临时 Key 是凭据写操作，未在无明确授权时执行；因此没有生成真实模型 trace，也不把 mocked/runtime content-block 自动化记为真实模型业务验收。
- 发布前重跑：MCP/附件/DEMO migration 聚焦测试、隔离 PostgreSQL 16.9 的两条 OpenAPI 控制器链路与 125 个迁移、backend package、前端 60 文件/332 项、production build、Compose 渲染和 diff check 通过。默认全量 backend suite 因本机没有默认 `localhost:5432/agentcici_test` 而持续重连，已中止且不记为通过；已有完整 OpenAPI 类仍保持 16/17 的如实边界。
- UAT：`2.8.68-beta.3 / a5bbb1140864` 的 tag、远程 main、镜像 label/revision 与运行版本一致；backend/frontend digest 为 `sha256:b81e8c5aeb96d4710fa15349d5bceeb1c88aef2b7a2e65bdab3c49acdbc2c4d9` / `sha256:2412ab89ad5c6717db544aca05d2c8fe7483b3292c25c5cb792c32ed4846ae26`。V127/V128 与 repeatable migration success，六容器 healthy/restart=0，四状态服务 ID 不变。
- UAT 公开与稳定性：首页 HTTPS 200、HTTP 301、匿名 `/auth/me` 和 `/openapi/v1/parameters` 为 JSON 401，Keycloak discovery、Semattice 和 DevAutopilot integrated health 通过；30 秒窗口 backend severe=0、Nginx error=0、真实 5xx=0。宽松正则初始命中的 5 行经 Nginx 状态列复核仅为 200/401，不记为 5xx。
- 契约边界：发布前后 UAT `tenant_application_mcp_binding` 有效绑定数均为 0；本候选包含 MCP-only 源码变化但没有在 UAT 启用或切换该跨项目契约，不宣称 DevAutopilot MCP UAT 功能已交付。生产未修改。

## 2026-08-31 TASK-333 DEMO 真实运行连接本地技术验收

- 状态：`passed_task_333_local_runtime_pending_human_visual`；真实连接持久化、迁移、自动化、构建、同提交制品、数据库与匿名鉴权边界通过，登录态视觉待 HUMAN。
- 自动化：`InternalApplicationProviderConnectionServiceTest,TenantApplicationCatalogServiceTest` 与 backend package 通过；前端聚焦 2 文件/7 项、全量 60 文件/332 项、production build、域名扫描和 diff check 通过，build 仅保留既有大 chunk warning。
- 空库迁移：一次性 PostgreSQL 16.9 成功校验/执行 125 个迁移至 V128；`DemoExampleApplicationFlywayMigrationTest` 回读唯一连接为 `demo-example.lifecycle / DRAFT / activeRevisionId=null / r1 / https://service.example.test / HMAC_SHA256_SECRET_REF / NOT_TESTED`，并确认已发布版本仍为 `NONE / providerBindingKey=null`。
- 本地主线与制品：代码提交 `40a27a2b2983` 已进入本地 `main`；backend/frontend 均运行 `2.8.68-dev.40a27a2 / 40a27a2b2983`，镜像为 `sha256:d55b923c4c56c7d21585595b89f88d304605b5d0c7a8b755d0d84bd6085081e4` / `sha256:1c56768632094ad37978e9bcfffb8fadf5a88828ae634954aff794f25cd89ef1`，healthy/restart=0。
- 运行回读：本地 Flyway 成功重跑 repeatable migration；数据库回读连接及 r1 与断言一致。目标 HTTPS 路由 200，部署 JS 含 `运行连接实际配置 / demo-example.lifecycle / NOT_TESTED`，匿名连接 API 为 JSON 401，最近 Nginx 5xx 为 0。
- 最小影响：只替换 backend/frontend；PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 容器 ID 与部署前一致。标准 `./stack verify` 仍会被既有 Semattice `config=1.0.7 / repository=1.0.8` 漂移失败关闭，本任务未修改该产品。
- 浏览器边界：应用内浏览器与 Chrome 均到达本地“运营平台登录”页；未读取浏览器存储、未代填凭据。详情计数 1 和示例页 20 项字段仍待 HUMAN 登录后目视确认。远程、UAT、生产未修改。

## 2026-08-31 TASK-345 DevAutopilot MCP-only 解耦回归

- 状态：`passed_task_345_local_technical_human_dialogue_not_replayed`；目录、精确绑定运行、发布、身份和真实业务数据技术闭环通过。浏览器未代 HUMAN 发送新对话消息，该步骤不是技术证据的替代品。
- 自动化：`ToolOrchestratorServiceTest,ToolControllerTest,AgentCompileSkillDagTest` 通过；前端全量 `60 files / 331 tests`、最终发布门禁聚焦 `31/31` 和 production build 通过，仅有既有大 chunk warning。质量脚本受环境变量覆盖而尝试连接外部 PostgreSQL，持续重连后人工中止，未记为通过。
- Source/Runtime：实现 `4d3cf32a`、加载竞态修复 `81c74925` 均进入本地 main；backend/frontend 从干净 `main@81c749253528` 构建为 `2.8.68-dev.81c7492`，镜像 label/revision 一致，两个容器和 DevAutopilot 均 healthy/restart=0。
- 数据与目录：Flyway V127 success；`platform_tool_definition` 六个旧工具计数 0；登录态工具页内置原生工具为 18（原 24）；Agent Builder 六项研发工具均显示 MCP 和声明风险，租户应用绑定 ACTIVE、缓存 ready/6。
- 发布：刷新研发产品经理详情且不点击版本历史时，“发布版本”自动可用；正式 UI 发布 v2 成功，数据库回读 v1 ARCHIVED、v2 PUBLISHED，六项白名单仍启用。
- 身份与业务：匿名 MCP JSON 401；Keycloak `pm-autopilot-daqiao` SERVICE 经 AgentCiCi 短时 OACT 交换调用 `semattice_project_delivery_query`，返回 `isError=false`，Semattice 实时汇总为 1 个项目、1 个活动任务、1 个未关闭缺陷，项目 `DAS-A2AFD106 / 企业级智能体平台CCAgent / 规划中`。
- 全栈门禁：`./stack verify` 按既有 Semattice 发布线漂移 `config=1.0.7 / repository=1.0.8` 失败关闭；没有修改该任务外配置，不把定向验证写成完整门禁通过。本任务未修改 UAT、生产、ACR 或远程分支。

## 2026-08-31 TASK-344 UAT 首页 Web 浮窗

- 状态：`passed_task_344_uat_widget_pending_avatar_upload`；不可变 UAT 候选、完整备份、最小部署、目标租户官方发布链路、首页默认入口、Token 正负例和真实非空模型回复通过。系统图片头像尚未上传，当前使用 Agent Definition 名称派生的“售”回退，不能把该边界记为图片头像验收通过。
- Source/Artifact：候选冻结时本地/远程 `main`、annotated tag `2.8.68-beta.2^{}` 与运行提交均为 `b9a0dfb7007c`；后续治理证据提交只记录发布事实、不改变制品。backend/frontend ACR index digest 为 `sha256:d8b978396038941e145c06e82c590d82c9ba8d69942e78e6f3870d13709c9208` / `sha256:a24f134031892457af8b360e5fb986924be51ca4208ebd2ceb21337c6d7b4a57`，未更新 `latest`。
- Recovery：完整备份 `/data/apps/agentcici/backups/20260831T031524Z-before-2.8.68-beta.1` 的 PostgreSQL、KB、Qdrant、旧镜像、Compose/env 和 SHA-256 已验证；追加保留 `uat.secrets.env.pre-homepage-widget` 与 `docker-compose.uat-acr.override.pre-beta2.yml`。应用回滚目标 `2.8.67-beta.1`，数据恢复需单独授权。
- 租户发布：已登录页面回读 `CloudCC Agentic Test / orgickjr6icm6l2zitpn`；通过官方 Agent Builder 把既有 `sales-agent` 命名为“售前跟进智能体”，精确允许 `https://uat.agentcici.com`，选择唯一 ACTIVE Owner 作为 demo 运行成员，保存、编译并发布 `v1`。非 demo 环境仍应替换为专用 RUN-only 成员。
- 公开安全：默认公开配置返回“售前跟进智能体 / 咨询售前”；正确 Origin Token 为 200，JWT 仅有 `chat:read/chat:write`，并绑定 website、租户、Agent、运行成员、上下文和过期时间；保留测试域错误 Origin 返回 403。公开配置不含图片头像正文时返回空头像，由客户端使用名称回退。
- 浏览器：UAT 首页默认折叠启动器可见；展开后为 CRM 蓝，附件按钮为 0，话筒背景透明、发送按钮位置正常。真实问题“50 人销售团队统一管理客户跟进并自动生成售前建议”返回完整实施方案；console error/warn 为 0。
- 头像边界：展开态标题、欢迎消息和回复均显示 Agent Definition 名称回退“售”，不再显示固定思思身份；折叠启动器在无图片时仍使用 SDK 默认 `Ci`。两次受控文件选择均被 Chrome 扩展拒绝，系统 UI 捕获也不可用；未读取浏览器 Token、Cookie 或绕过权限，图片头像留待 HUMAN 在官方上传入口完成。
- Runtime：只重建 backend/frontend 完成 `beta.2`，启用首页键时只重建 backend；六容器 healthy/restart=0，四状态容器 ID 与发布前逐项一致，health UP、version/commit、Nginx、公开 smoke、匿名 401 和最近错误计数 0 通过。生产未修改。

## 2026-08-28 TASK-343 Web 浮窗系统智能体头像

- 状态：`passed_task_343_local_runtime_pending_user_review`；数据权威来源、安全边界、自动化、同提交本地制品和真实浏览器头像回读通过，等待用户目视确认。
- 自动化：后端 `PublicWebWidgetServiceTest,SisiEmbedRuntimeServiceTest` 与 package 通过；前端聚焦 3 文件/34 项、最终聚焦 2 文件/30 项、全量 60 文件/328 项与 production build 通过；两个 SDK `node --check`、环境域名门禁和 diff check 通过。build 仅有既有大 chunk warning。
- 数据与安全：demo `org3gxskla32gln3bvop / sales-agent / 客服-Mary` 公开配置返回 11,707 字符 WebP data URL，不返回 `companyId/runAsUserId`；JWT 断言不含头像正文。website 会话按 Token 绑定的公司/Agent 回读启用智能体，CloudCC 固定身份链路不变。
- Runtime：实现 `9191e5a3eacf` 已进入本地 main；backend/frontend 为 `2.8.68-dev.9191e5a`，最终镜像 `sha256:a62be82f3008` / `sha256:d528b5cf6cd8`，healthy/restart=0，health UP、Nginx 有效，官网、float embed、稳定 SDK、公开 widget 均为 200。
- 浏览器：官网启动器含 1 张 256×256 WebP 系统头像；展开浮窗后标题栏与 3 条智能体消息共 4 张均为同一 256×256 WebP，名称 `Mary`，无通用 `Ci/M` 图片回退。
- 治理：`validate-state.py .claw` 已执行，输出没有 TASK-343/FEAT-204 新 finding；全局仍因既有历史任务未归档、旧状态/front matter 与 references 索引债务返回 1，本任务未跨范围清理历史事实。
- 边界：第一次 release Docker 构建因 Docker Hub 拉取 `node:22-alpine` 连接重置失败且未替换容器；改用同一 main 的已验证主机构建产物和既有 JRE/Nginx 基础镜像完成最小重建。远程、UAT、生产、ACR 与 tag 未修改。

## 2026-08-28 TASK-342 生产 `2.8.67`

- 状态：`passed_task_342_production_technical_pending_human_image_acceptance`；用户已确认 UAT HUMAN 验收通过，冻结源码、不可变正式制品、备份、最小切换、运行指纹、迁移、健康、公开/匿名、数据守恒和累计 100 秒稳定窗口通过。
- Source：`2.8.67-beta.1^{}`、`2.8.67^{}` 与生产运行 commit 均为 `2970bea75208`，远程 `main@2be977b09fcb` 包含该提交；本地后续 TASK-337/339/340/341 均未进入候选。
- Artifact：backend/frontend linux/amd64 index digest 为 `sha256:2b6be2564f0eef09f064e4ce345d585cc4bc1f3c00408d0f358ab8f82bfac615` / `sha256:6ec9501ec3cdfdf1118ab1ec9f647223ecfb843440603d83e054577c539dd6a4`；label 为 `2.8.67 / 2970bea75208`，未更新 `latest`。
- Recovery：完整备份 `/opt/cici/backups/20260828T130242Z-before-2.8.67` 共 14 项、351,001,019 bytes；全部非空且 `0600`，PostgreSQL catalog、KB/Qdrant tar、Qdrant 原生 snapshot、旧镜像 gzip 与 SHA-256 清单通过。应用回滚目标 `2.8.66`，数据恢复另行授权。
- Runtime：只重建 backend/frontend；四状态服务 ID 不变。六容器 healthy/restart=0，health UP，version/commit/image/digest 一致，Nginx 有效；Flyway V125 与 repeatable migration success。
- Edge：生产首页、`/app`、`/embed/sisi`、DEMO 路由和 SDK 为 200；DevAutopilot、Semattice、Keycloak smoke 通过；HTTP 301；匿名 `/auth/me`、`/ai/sessions`、`/skills` 和 Embed 附件 API 为 JSON 401。
- Data：知识库表发布前后均为 9/35/661，Qdrant 均为 1 collection/549 points。累计 100 秒内六容器状态/restart 不变，backend severe=0，frontend 5xx/upstream=0。
- 验收边界：UAT 图片识别 HUMAN 接受由用户明确确认；本轮没有创建或使用生产登录凭据，生产真实图片上传与模型识别仍待已登录 HUMAN，不以技术门禁替代。

## 2026-08-28 TASK-339 裸图标按钮透明背景治理

- 状态：`passed_task_339_local_runtime_pending_user_review`；根因、共享原语、跨页面迁移、静态门禁、同提交制品和官网真实视觉回读通过，等待用户目视确认。
- 根因：Sisi 输入工具栏 hover 直接把 `--sisi-panel` 设为按钮背景，在 CRM 蓝主题下得到截图中的 `#e8f0fa` 浅蓝块；公共 `cici-product-icon-button` 同时允许 `#faf4e8` hover/focus 背景，因此同类局部覆盖可能跨页复发。
- 自动化：聚焦 `iconButtonContract + SisiEmbedPage + theme` 3 文件/22 项通过；前端全量 59 文件/324 项通过；production build、`DESIGN.json` 解析、裸图标 CSS 扫描、环境域名门禁和 diff check 通过。build 仅有既有大 chunk warning。
- 主线与制品：实现 `a64a1ede7d23` 已进入本地 `main`；backend/frontend 镜像分别为 `sha256:1a9410567149` / `sha256:ff8fe07c50f4`，运行均为 `2.8.67-dev.a64a1ed / a64a1ede7d23`，healthy/restart=0。
- 浏览器：官网浮窗展开后，话筒默认 computed style 为透明背景、background-image none、box-shadow none；真实鼠标 hover 的 `hovered=true`，背景与阴影保持不变，图标由 `rgb(23,35,61)` 变为 `rgb(22,119,210)`。发送按钮、历史非空回复正常，console 0 error/warning。
- 环境：`/`、公开 widget、float embed 与稳定 SDK 均为 200，Nginx 配置有效；近 5 分钟 backend severe=0、frontend 5xx/severe=0。仅替换 backend/frontend，八个基础设施/兄弟产品容器 ID 不变。
- 治理校验：本任务新增 TASK/规格/front matter 均可读；全局 `validate-state.py .claw` 仍被仓库既有历史时间格式、旧规格状态/缺字段、完成任务未归档与 references 索引债务阻断，本任务未批量修改历史事实。
- 边界：未修改 Web Token、会话、附件或语音服务端契约；远程、UAT、生产、ACR 与 tag 未修改。

## 2026-08-28 TASK-338 UAT `2.8.67-beta.1`

- 状态：`passed_task_338_uat_human_accepted`；冻结源码、不可变制品、备份、最小切换、运行指纹、迁移、健康、公开/匿名和稳定窗口通过，用户随后明确确认登录态图片识别 HUMAN 验收通过。
- Source：修复 `036c12a0d006` 与验证记录 `2970bea75208` 已进入远程 `main`；annotated tag `2.8.67-beta.1^{}` 和运行 commit 均为 `2970bea75208`。本地后续 TASK-337 提交未进入本候选。
- 构建门禁：冻结工作树前端 58 文件/318 项、production build，后端附件/模型身份聚焦测试与 package、`git diff --check` 通过。
- Artifact：backend index digest `sha256:927692d90475cabeded150a776e24d31a9dcbfd45f29273dd9d870de50aab74d`，frontend index digest `sha256:79a2f1e08967a0e85e65d73d5facafcecfc5ed8349c449007323d7acd958d49f`；均含 linux/amd64，label 为 `2.8.67-beta.1 / 2970bea75208`，未更新 `latest`。
- Recovery：完整备份 `/data/apps/agentcici/backups/20260828T104754Z-before-2.8.67-beta.1` 共 12 项、317,026,856 bytes；文件非空且 `0600`，PostgreSQL catalog、KB/Qdrant tar、Qdrant 原生 snapshot、旧应用镜像 gzip 与 SHA-256 清单通过。应用回滚目标 `2.8.66-beta.3`，数据恢复需另行授权。
- Runtime：只 `--no-deps --force-recreate backend frontend`；database、Redis、RabbitMQ、Qdrant ID 不变。六容器 healthy/restart=0，backend health UP，version/commit/image/digest 一致，Nginx 有效；Flyway V125 success，随后 repeatable `demo example application` success。
- Edge：UAT 首页、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 通过；`/app`、`/embed/sisi`、DEMO 路由与 SDK 为 200，匿名 `/auth/me`、`/ai/sessions`、`/skills` 和 Embed 附件 API 均为 JSON 401。
- 稳定：独立 30 秒窗口六容器状态/restart 不变，backend severe=0，frontend 5xx/upstream=0。本候选没有新增、切换或启用跨项目契约；生产未修改。
- 验收边界：发布执行过程未使用或创建 UAT 登录凭据、未代替用户执行真实图片会话；实际业务结果由用户后续明确确认，不把技术门禁当作该确认。

## 2026-08-28 TASK-337 官网 Web 浮窗 CRM 标准蓝与输入区修正

- 状态：`passed_task_337_local_runtime_pending_user_review`；代码、自动化、同提交制品、官网真实浮窗、视觉对照与非空回复通过，等待用户目视确认。
- 自动化：`SisiEmbedPage.test.ts` 6 项、前端全量 58 文件/320 项、production build、两个 SDK `node --check`、业务前端新增行环境域名扫描和 `git diff --check` 通过；build 仅保留既有大 chunk warning。
- 主线与制品：实现 `beef1cedd4056` 已进入本地 `main`；backend/frontend 镜像分别为 `sha256:577085a682627aa3fc763d1c4c5aecc748f4b0d8b94b1a474f72399b33980274` / `sha256:b90c4bbc16e4eafaa75a794adc56fe4e3d81231243a32008e1602c12a7a8bd20`，label、容器环境、版本 API 和页面资源均为 `2.8.67-dev.beef1ce / beef1cedd4056`；两容器 healthy/restart=0。
- 视觉与交互：官网启动器回读白底、CRM 蓝标记和蓝灰结构线；浮窗根主题为 `crm-blue`。附件按钮为 0；语音在左、发送在右且为 `33 × 33`；可用发送态为 `#1677d2`。关闭按钮 hover 背景/边框透明、图标转蓝；键盘焦点轮廓保留。
- 真实回复：官网浮窗发送“请用一句话确认Web浮窗发送功能正常”后返回“Web浮窗发送功能运行正常。”，页面 console `0 error / 0 warning`；设计对照 `final result: passed`。
- 环境：backend health=`UP`，frontend Nginx 配置有效，`/`、`/public/website-widget`、`/sdk/sisi@1.1.0.js` 和 `/embed/sisi?mode=float` 均为 200；近 10 分钟 backend severe=0、frontend 5xx=0。PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot ID 摘要发布前后同为 `cbb50a0424ec...`。
- 边界：运行时继续使用 Git 忽略的官网 widget override；未修改 local-stack 跟踪文件。标准全栈门禁仍受任务外 Semattice `config=1.0.7 / repository=1.0.8` 漂移约束；远程、UAT、生产未修改。

## 2026-08-28 TASK-336 图片识别视觉能力范围修复

- 状态：`passed_task_336_local_runtime_pending_user_review`；根因、红/绿回归、附件/模型身份聚焦测试、同提交制品和已登录真实图片会话通过，等待用户目视确认。
- 运行配置只读证据：平台治理组织当前 `chat=aliyun-bailian/qwen3.7-plus`；该模型能力含 `vision` 且来源为 `operator_confirmation`，普通业务组织没有自己的模型能力配置。
- 红/绿测试：新增普通租户调用平台受信视觉模型用例，修复前得到 `false`，将能力读取范围对齐平台治理组织后通过。测试使用独立 `agentcici_task336_test` 数据库完成 V1-V125 和 repeatable migration，结束后已删除。
- 聚焦回归：`ChatAttachmentServiceTest,ChatOrchestratorServiceModelIdentityTest` 共 57 项通过；`mvn -q -DskipTests package`、`git diff --check` 通过。
- 主线与制品：实现 `036c12a0d006` 进入本地 `main`；backend/frontend 镜像分别为 `sha256:ef82f9a5648a724cce57175789d026b91fb24c6ac695d6d4faf40d8bfa4ea8e0` / `sha256:228feab79313e3e2832d81034d05a7a688c25edbcef003af9e2194934e24a1ee`，label、容器环境、版本 API 和页面资源均为 `2.8.67-dev.036c12a / 036c12a0d006`；两容器 healthy/restart=0。
- 真实业务链路：已登录普通租户把用户原截图连续两次通过剪贴板粘贴给思思；运行模型显示 `qwen3.7-plus`，分别准确返回图中的 `409` 和 `VISION_MODEL_REQUIRED`，没有再出现 HTTP 409 能力冲突。浏览器 warning/error=0，后端近 10 分钟没有 `VISION_MODEL_REQUIRED`、ERROR、Exception 或 failed 日志。
- 环境：`https://cici.localhost/app=200`，backend health=`UP`，frontend Nginx 配置有效；PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot ID 未变化且 restart=0。
- 边界：完整 `ModelProviderServiceIntegrationTest` 的 6 项组合运行有 1 项既有测试顺序污染失败，前序平台配置残留 `platform-chat-model` 使“目录为空”断言失败；本次目标方法单独绿测，不把整类宣称通过。标准全栈治理校验仍受历史任务/规格格式债务及 Semattice 版本漂移约束；远程、UAT、生产未修改。

## 2026-08-28 TASK-335 Web 浮窗流式回复空白修复

- 状态：`passed_task_335_local_runtime_pending_user_review`；代码、自动化、同提交制品、真实官网对话、消息持久化、Trace 和浏览器错误门禁通过，等待用户目视确认。
- 根因同构：后端 `ChatOrchestratorService.safeSendDelta` 和 OpenAPI 测试均发送 `{text}`；旧 Embed 消费方只读取 `content/delta`，因此 SSE 已成功、模型正文已持久化，但页面累积内容仍为空并触发兜底。
- 自动化：`SisiEmbedPage.test.ts` 4 项通过，新增规范 `text` 与纯文本/`content`/`delta` 兼容覆盖；前端全量 58 文件/318 项、production build、环境域名扫描与 `git diff --check` 通过。build 仅保留既有大 chunk warning。
- 主线与制品：实现 `d47afb41c66d` 已进入本地 `main`。backend/frontend 镜像分别为 `sha256:f19364266f020314cc0958cf79ae74f98e4b2c09ed577d8a0e79dc025bf5496f` / `sha256:6168caf9e3ee23a4b7527db7d28d321a7fce72bb5b3d813142d90be5f0739390`，label、容器环境、版本 API 和页面资源均为 `2.8.67-dev.d47afb4 / d47afb41c66d`；两容器 healthy/restart=0。
- 真实业务链路：官网正式浮窗发送截图原问题“我需要一个能跟客户在线沟通的智能软件”，页面展示 678 字完整回复，包含核心信息、功能对比与下一步建议；数据库最新 user=18 字、assistant=678 字，Trace 为 `sisi_embed/COMPLETED/model_call_count=2/tool_call_count=0`。
- 浏览器与稳定性：刷新到新制品后对话完整显示，不包含“本次未返回文字内容。”；console error/warning=0。backend 启动后无 ERROR/Exception/Failed；PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 持续运行且未重建。
- 边界：标准全栈门禁仍受任务外 Semattice `config=1.0.7 / repository=1.0.8` 漂移约束，本次未修改该产品或 local-stack 既有跟踪改动。远程、UAT、生产未修改。

## 2026-08-28 TASK-334 Web 浮窗与官网售前智能体

- 状态：`passed_task_334_local_runtime_pending_user_review`；代码、自动化、demo 发布、公开安全门禁、真实模型会话、运行制品与浏览器验收通过，用户视觉确认和非 demo 最小身份治理待完成。
- 自动化：前端全量 58 文件/316 项、production build；后端 `PublicWebWidgetServiceTest`、两个 CORS 注册测试、`AgentOpenApiCorsConfigTest`、`TenantContextFilterTest` 与 package 通过；SDK `node --check`、环境域名扫描、`git diff --check` 通过。带数据库的旧集成测试默认连接宿主机测试端口失败，未宣称通过；部署后的 V125 与真实链路补足运行证据。
- 主线与制品：实现 `d407a999`、公开路由 `87fcdead`、CORS/版本路由 `ee4a59a6` 均进入本地 `main`，远程未推。backend/frontend 镜像 ID 为 `sha256:5fdb6259da35af73ee8bd8fbaa48515e3a34d5317cf7f741cc3e5ead1259d1cb` / `sha256:4ed6e9e4291e9821e1de9cb335f9ceffc9c3702048d35e0b422064abc162c5cf`，label 均为 `2.8.67-dev.ee4a59a / ee4a59a62c51`。
- demo 数据：`org3gxskla32gln3bvop / sales-agent` 通过真实 compile/bindings/publish-configs/publish API 生成 v1，readiness `blocked=false`，发布 `PUBLISHED`、Web=true；现存警告为无知识库和未配置评测集。只使用既有唯一 ACTIVE OWNER，未创建/激活账号；非 demo 前需替换为 RUN-only 成员。
- 公开契约：V125 success；配置 200 且无 company/runAs；允许来源预检 200，错 Origin 403；正确来源签发 600 秒 Token，权限仅 `chat:read/chat:write`；该 Token 访问普通 `/agents` 为 401。`sisi@1.1.0.js` 与 `/embed/sisi` 200，Nginx 配置有效。
- 真实业务链路：website session 回读 `sales-agent / 售前跟进智能体 / source=website`；发送“不调用工具”的售前能力问题后模型成功回答，历史 2 条消息，执行日志 `CHANNEL:SUCCESS:sales-agent`。
- 本地环境：backend/frontend 只按最小影响重建，healthy/restart=0；`/system/version` 与前端资源为 `2.8.67-dev.ee4a59a`。浏览器启动器默认折叠、点击展开、标题/欢迎语正确，console 0 error/warn。PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 容器 ID 保持不变且 restart=0。
- 编排边界：受管 `./stack version/init/verify` 被本任务外 Semattice `config=1.0.7 / repository=1.0.8` 漂移失败关闭，未修改该产品或夹带 local-stack 现有脏改动；本轮使用 Git 忽略 runtime override 精确注入默认 widget key。UAT、生产、ACR、tag 未修改。

## 2026-08-27 TASK-332 思思嵌入式智能应用本地技术验收

- 状态：`passed_task_332_local_technical_pending_cloudcc_human`；代码、身份负向、迁移、SDK、页面/浮窗、运行制品和本地技术环境通过，真实 CloudCC 宿主的换票与登录用户业务链路待 HUMAN。
- 自动化：一次性 PostgreSQL 16.9 空 schema 迁移至 V124，`EmbedAppIntegrationTest` 3 项通过；`ChatAttachmentServiceTest + TenantContextFilterTest` 共 16 项、模型身份聚焦测试和 backend production package 通过。最终本地 `main` 前端全量 58 文件/314 项、production build、SDK 语法/稳定版一致、域名门禁和 diff check 通过；build 仅保留既有大 chunk warning。
- 主线与制品：实现 `935728872674de7cdcb0178ad976f22150a2c66d` 进入本地 `main`。backend/frontend 镜像为 `sha256:a2471ce5fe9e6cf7fd9b5ef255dee308a428faf195eccd156418f0fda38698ed` / `sha256:020a61966c716fdd5f157126dfb1cd1d65b9835a997559c4df2afea8a7a48ce8`，标签、容器环境、backend `/system/version` 和前端版本资源均为 `2.8.67-dev.9357288 / 935728872674`。
- 运行与迁移：只 force-recreate backend/frontend；两者 healthy/restart=0。Flyway `124|sisi embedded agent|true`，目录 `sisi|思思|ENABLED|1.0.0`，`sisi_embed_session` 存在；`/embed/sisi` 和两个 SDK 为 200，SDK 均 7,178 bytes 且 SHA-256 同为 `74faff9552825d0cc610d416006bccda08dc0c4ad35c568698ae79fb9e9693fc`。完整受管 `scripts/verify.sh` 通过，证明基础设施、数据库隔离、TLS、OIDC、健康/版本和匿名授权边界。
- 浏览器：正式 `https://cici.localhost/embed/sisi` 无 Token 时显示身份校验边界、三栏界面、固定“思”形象和“CloudCC 身份安全接入”，输入/附件/语音保持禁用，console 0 error/warning；页面、408px 浮窗和真实发送交互此前也已通过 Chromium 验证。
- 稳定与最小影响：20 秒后 backend severe=0、frontend HTTP 5xx/severe=0；PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 未重建且 restart=0。标准 `./stack version/status/up` 仍被既有 Semattice `config=1.0.7 / repository=1.0.8` 失败关闭，本轮未越权修改该产品；直接执行同一受管完整验证脚本已通过。
- 边界：远程 `main` 仍停留在 `6a73435d`，本地领先 4 个提交；未 push、未打 tag、未写 ACR、未修改 UAT/生产。真实 CloudCC API Key 换票、已登录用户对话、语音/附件和高风险动作回读不能由本地匿名页面替代。

## 2026-08-27 TASK-333 DEMO 示例应用本地技术验收

- 状态：`passed_task_333_local_technical_pending_human_visual`；代码、自动化、迁移、数据库、镜像、运行版本、HTTPS 路由和匿名边界通过，登录态视觉/交互待 HUMAN。
- 自动化：`TenantApplicationCatalogServiceTest` 2 项通过；前端聚焦 3 文件/15 项、全量 57 文件/312 项通过；`mvn -q -f backend/pom.xml -DskipTests package` 与 production build 通过，build 仅有既有大 chunk 警告；`git diff --check` 通过。
- 迁移与数据：Flyway 日志显示 repeatable migration `demo example application` 成功执行。数据库回读应用为 `demo-example / DEMO示例应用 / PUBLISHED / PLATFORM_BASE / PLATFORM_ROUTE / demo-example.page / 1.0.0 / NONE`；唯一依赖为 `semattice >=1.0.0 / OPTIONAL / AUTO_PROVISION_ALLOWED`，没有写入 Provider 运行连接或 Secret。
- 本地主线与制品：实现提交 `62ad71d4`、迁移顺序修订 `5f6ce44a` 均进入本地 `main`，远程未推送。backend/frontend 镜像分别为 `sha256:34c05e8f04e6a6f524f3d287115db168fd5910f737eeff0acce6139954bfbbf1` / `sha256:c97c5ec31c447473b86d2185484a9a3c9e2cfadd9cce1e022595164fcccce186`，标签均为 `2.8.67-dev.5f6ce44 / 5f6ce44a`。
- 运行回读：backend/frontend 容器环境、镜像 ID 与 backend `/system/version` 一致，两容器 healthy/restart=0，`/actuator/health=UP`。`https://cici.localhost/platform/internal-applications/demo-example/example=200`，首页引用带版本指纹的 JS/CSS，运行 JS 包含 `DEMO示例应用`；匿名目录 API 返回 JSON 401。15 分钟窗口 backend severe=0、frontend HTTP 5xx=0、nginx severe=0。
- 浏览器边界：未登录浏览器访问示例页后到达“运营平台安全登录”；未读取或代填平台凭据。登录态应用中心列表、详情入口和单对象页面待 HUMAN 目视确认，不以静态 200、数据库或自动化替代。
- 治理校验：新规格状态已修正为合法值；全局 `validate-state.py .claw` 仍受仓库既有历史时间、状态、归档和 references 债务阻断，本任务未批量修改历史事实。UAT/生产未修改。

## 2026-08-27 TASK-331 CloudCC 工作台 SSO company ID 契约修复

- 状态：`passed_task_331_production_cloudcc_runtime`；生产故障已修复，代码、自动化、构建、CloudCC 发布回读和受权登录态运行验证通过。
- 生产故障同构：2026-08-27 10:52:52 CST 的真实 `/auth/cloudcc-sso/ticket` 返回 400；无凭据同结构探针返回 `agentCompanyId must not be blank`。生产组件 V15 的 `vueData.propObj` 仍为 `agentOrgId`；后端 DTO 要求 `agentCompanyId`。
- 数据边界：目标租户与匹配成员各 1 条，成员 ACTIVE，CloudCC 用户名和安全标记字段非空；未读取、输出或复制安全标记值。请求在 DTO 校验阶段失败，账号绑定不是当前失败点。
- 前端：定向 `CloudccEmbedSso.test.ts` 8 项通过；全量 56 个测试文件、309 项通过；TypeScript/Vite production build 与 UMD `node --check` 通过，仅有既有 bundle-size warning。
- 后端：`AuthControllerTest` 3 项通过，覆盖规范 `agentCompanyId` 和旧 `agentOrgId` 反序列化；`mvn -q -DskipTests package` 通过。
- CloudCC：`cc-customization-expert-msapi 2.2.28-msapi` provider doctor 选择严格 MSAPI；pagecomponent dry-run 通过，只打包受管组件源码、配置和预构建 bundle，并排除 env、token cache 与 `.claw-local`。发布将同一组件 ID `6a5628cee4b0a577cbba2088` 从 V15 更新为 V16；customPage 保持 V9 并继续引用该 ID，`verify injectionPage --stale-policy warning` 返回 `status=passed, issues=[]`，无需额外 bind。
- 静态门禁：`git diff --check` 和 `cc-local-stack/scripts/check-no-environment-domains.sh` 通过。首次从子仓错误路径调用域名脚本返回不存在，改用受管 `cc-local-stack` 入口后通过。
- 治理校验：`validate-state.py .claw` 首次发现 FEAT-201 状态值不在枚举，已改为合法 `draft`；复核仍受既有 goals 时间格式、历史规格 front matter/status、完成任务未归档和 references 索引债务阻断，不归因于 TASK-331。
- Git：修复提交 `ebea2febe1d8a15f3c802f48a7ab7dee480bedbd` 已推送，发布时本地 `main` 与 `origin/main` 相同。
- 生产登录态：重载目标 CloudCC 自定义页后，iframe 保持在 AgentCiCi 工作台，显示“CloudCC CRM 已连接”，加载当前用户、客户队列、客户详情和 AI 助理；原“身份信息不完整”和 SSO 拒绝连接均消失，浏览器 error 日志为 0。未通过页面执行业务写操作，因此该结果是受权运行技术验证，不替代用户最终业务确认。
- 发布边界：AgentCiCi 生产仍为 `2.8.66 / e805c0ef7142`，未重建 backend/frontend 或状态服务。后端 `JsonAlias` 已在 `main`，等待下一次 `2.8.67` 标准发布；组件回滚来源为修复前提交 `e8e3080987c0d0256b79658deacd4f0867ffe069`。

## 2026-08-26 TASK-330 生产发布

- 状态：`passed_task_330_production_release`。用户确认 TASK-326/327/328/329 UAT HUMAN 验收；生产按 Semattice 提供方、SERVICE 契约探测、AgentCiCi 消费方的顺序完成。
- Semattice 生产回读 `1.0.7 / 54f2ab93558f`；AgentCiCi 生产 SERVICE 真实签名探测返回 7 对象/87 字段、state=applied。AgentCiCi 正式制品从 UAT 冻结提交 `e805c0ef7142` 构建，两项 ACR digest、linux/amd64 manifest 与正式 Git tag 回读一致。
- 生产完整备份 13 项、格式与 SHA-256 清单通过；只重建 backend/frontend，四个状态服务 ID 未变。六容器 healthy/restart=0，health UP、V123、Nginx、运行版本/commit/digest、公开 smoke、匿名 JSON 401 和 100 秒稳定日志窗口通过。
- 生产运行 bundle 包含技能导出进行中、非 JSON 响应和任务未就绪处理；知识库 9/35/661 与 Qdrant 549 points 保持不变。UAT HUMAN 验收与生产技术回读分别记录，不以匿名探针替代人工结论。

## 2026-08-26 TASK-329 HUMAN 验收与 TASK-330 生产预检

- 状态：`passed_task_329_uat_human_reported_production_blocked`。用户确认 UAT 技能导出人工测试通过；该结论完成 TASK-329，但不自动覆盖同一候选内其他任务的验收。
- 生产只读 smoke 六项通过。生产 AgentCiCi 现场为 `2.8.65 / 784ccd23e933`；六容器 healthy/restart=0，backend health UP、Flyway V122、frontend Nginx 有效，近 15 分钟 backend severe 与 frontend 5xx 均为 0；四个状态服务运行指纹已记录且未改变。
- 跨项目停止条件：候选代码在 `DevAutopilotTenantApplicationService` 中要求 7 对象/87 字段并拒绝 7×86；生产 Semattice 公网版本为 `1.0.6 / 6579ded320ad`，项目发布事实为 7×86，UAT 7×87 提供方为 `1.0.7-beta.5 / 54f2ab93558f`。未获 Semattice 生产发布授权，未执行提供方写入或 SERVICE 模板应用。
- HUMAN 边界：TASK-326 缺陷业务闭环、TASK-327 真实微信客服链路、TASK-328 登录态运维文档交互仍为 pending。当前没有创建 `2.8.66` tag/正式镜像/生产备份，也没有修改生产 env、容器、数据库或其他产品。

## 2026-08-25 TASK-329 管理后台技能导出代码回归

- 状态：`passed_task_329_uat_technical_pending_authenticated_download`；UAT 只读复现、代码修复、自动化、本地环境和 UAT beta.3 技术门禁通过，登录态真实 zip 下载待 HUMAN。
- UAT 只读复现：公开 smoke 六项通过；登录态自定义已发布技能 `POST /skills/137/exports=400`，页面错误为 `Export package validation failed: manifest format mismatch`，15 秒内没有 download 事件。UAT 运行版本为 `2.8.66-beta.2 / 525f0f610926`；未修改 UAT 配置、镜像、容器或数据，生产未访问或修改。
- 后端聚焦：`SkillPackageServiceTest` 2 项通过，覆盖模型试图改写 `format/formatVersion/packageId/skill version/publish status` 时由服务端覆盖，以及非对象 manifest 失败关闭。
- 前端：全量 56 个测试文件、308 项通过；新增 4 项覆盖 READY job、后端校验原因、非 JSON 网关响应和未就绪 job。TypeScript/Vite production build 通过，仅保留既有 chunk-size warning。
- 构建与静态：`mvn -q -DskipTests package`、`git diff --check` 通过。
- 集成测试边界：宿主机执行被 `localhost:5432` 连接拒绝；一次性 PostgreSQL 执行已完成 119 项迁移到 V123 和 JPA 初始化，但现有测试 OACT 配置在 Context 或密码登录前置阶段失败，导出断言仍未执行，因此不声明 `SkillGovernanceIntegrationTest` 通过。
- 本地主线与制品：实现提交 `fada2e5f0b07fa2bcd0ac08da735acb8eb82a064` 已进入本地 `main`。backend/frontend 镜像 ID 分别为 `sha256:eb27e47a6a16dbed8c23ae13a727b875184285764cdbf62a800dafaae3df76e1`、`sha256:f2be0fc6cc883257aa0f82741b5a170611bb0a5d17cd940c78b62671eb060a5d`；两者 label 均为 `2.8.66-dev.fada2e5 / fada2e5f0b07`。
- 本地运行：仅 `--no-deps --force-recreate backend cici-frontend`；两容器 healthy/restart=0，backend `/actuator/health=UP`、`/system/version=2.8.66-dev.fada2e5 / fada2e5f0b07`，frontend Nginx 有效，运行 JS 包含“正在整理并生成通用技能包”。`https://cici.localhost/` 与 `/admin/skills` 为 200，匿名 `/auth/me`、`/skills` 为 JSON 401，近 5 分钟 backend severe 日志为 0。
- 最小影响：DevAutopilot、Semattice、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和 Nginx 容器 ID 与更新前一致且 restart=0；本次不涉及共享基础设施或跨项目契约，未执行完整 `./stack verify`。
- 本地 HUMAN 边界：本地浏览器正确进入统一 SSO 登录页，当时没有可复用本地组织管理员会话；未伪造登录或绕过 `@RequireOrgAdmin`。该阶段自定义已发布技能的真实 zip 下载、文件名和八文件内容未验收，生产未修改。
- 远程与候选：发布前 local/tracking/remote `main` 均为 `e805c0ef7142b7446aef019c786107528cde34a1`、ahead/behind=`0/0`；annotated tag `2.8.66-beta.3` 解引用到同一提交并包含 `fada2e5f0b07`。backend/frontend ACR index digest 为 `sha256:a2d0b8a5b6ad618e5451348b84efd813fde62911c8e7ff6949291a3acd6c19b2` / `sha256:44796d4848f8b1071206a5ea0452d1b60368b3c465ebb8039a22f504e470785d`，linux/amd64，未更新 `latest`。
- UAT 备份与回滚：`/data/apps/agentcici/backups/20260825T110733Z-before-2.8.66-beta.3` 共 12 项、317,014,387 bytes、全部非空且 `0600`；PostgreSQL dump catalog、KB/Qdrant tar、1 个 Qdrant 原生 snapshot、beta.2 应用镜像 gzip 和 SHA-256 清单通过。应用回滚目标 beta.2，数据恢复仍需单独批准。
- UAT 运行：仅 backend/frontend 被替换；PostgreSQL、Redis、RabbitMQ、Qdrant ID 保持发布前值。六容器 healthy/restart=0，backend health=`UP`，版本、commit、image label/digest 一致，Flyway V123，系统与 frontend Nginx 有效，运行 bundle 包含导出进行中文案。两轮 readonly smoke 通过；`/admin/skills=200 text/html`，匿名 `/auth/me`、`/skills`、`POST /skills/137/exports` 均为 JSON 401。启动切换期有 1 次 502，稳定 3 分钟窗口为 200/302/401 且 backend severe=0、frontend 5xx/severity=0。
- UAT HUMAN 边界：原 UAT 管理员页在刷新 beta.3 后被统一身份中心要求重新登录；未读取凭据或绕过认证。真实自定义技能导出 zip 下载、文件名和八文件内容仍待 HUMAN。候选未新增、变更或启用跨项目契约；生产及其他产品未发布。

## 2026-08-21 TASK-327 / TASK-328 UAT `2.8.66-beta.2`

- 状态：`passed_uat_2_8_66_beta_2_technical_pending_human_acceptance`；冻结 tag/commit、不可变镜像、备份、运行健康、迁移、公开路由和安全负向通过，两个功能的真实登录态/渠道业务验收待 HUMAN。
- Git 与制品：本地、tracking、远程 `main` 推送后均为 `525f0f61092693b5b28c91386520dfa50b10a9d3`，ahead/behind=`0/0`；annotated tag `2.8.66-beta.2` 解引用为同一提交并被远程 main 包含。backend/frontend ACR index digest 为 `sha256:095c0d71d87dbac60521b7c0ee029604606a699287c661f6173becb140b5e35f` / `sha256:82155b279677fb5bb4cea2529f94697cf77a4def9a2ad990071dec37665aa79e`，均含 linux/amd64 manifest，未更新 `latest`。
- 备份与回滚：`/data/apps/agentcici/backups/20260821T064027Z-before-2.8.66-beta.2` 共 12 项、316,927,951 bytes，全部 `0600`；PostgreSQL custom dump catalog、KB/Qdrant tar、1 个 Qdrant 原生 snapshot、旧应用镜像 gzip 和 SHA-256 清单通过。应用回滚目标 beta.1，数据恢复需单独批准。
- 运行：仅重建 backend/frontend；四个状态服务 ID 发布前后不变。六容器 healthy/restart=0；版本、commit、image label/digest 一致，backend health=`UP`，Flyway V123，frontend 与系统 Nginx 有效，Semattice/DevAutopilot active，Keycloak 未修改。
- TASK-328：部署安装页 200；公开 Markdown 200、`text/markdown`、`nosniff`、稳定 `agentcici.deployment-installation.v1`、8 个编号章节；运行 bundle 包含“运维中心”。正式平台登录态导航、章节锚点和新窗口仍待 HUMAN。
- TASK-327：移动页 200；带合法同源 `pageUrl` 且无 session 的 context 为 `401 application/json / mobile session is required`，不存在入口 UUID 为 `400 application/json`。UAT 未配置或调用真实微信客服账号/Secret/OAuth/客户消息/接管；真实业务链路待 HUMAN。
- 稳定性：两轮 UAT readonly smoke 覆盖 AgentCiCi 首页、匿名 auth、Keycloak discovery、Semattice health/version 与 DevAutopilot integrated health；30 秒窗口 backend severe=0、frontend HTTP 5xx=0、frontend severe=0。
- 已知风险：缺少控制器声明为必填的 `pageUrl` 或 `entry` 时，Spring 参数异常被通用 handler 映射为 JSON 500；没有认证绕过，且正式契约负向为 401/400。本候选不因该畸形输入风险回滚，后续应统一映射为 400。生产及其他产品未发布。

## 2026-08-21 TASK-328 运维中心部署安装在线指南

- 状态：`passed_task_328_frontend_technical_pending_authenticated_visual_acceptance`；导航、在线文档、Agent Markdown、前端构建、本地 frontend 制品与技术路由通过，正式认证路由桌面复核待平台运营账号重新登录。
- 源码与内容：提交 `01c678d015b4` 新增“运维中心 → 部署安装”、`/platform/operations/deployment-installation`、8 章在线手册和 `/agent-docs/operations/deployment-installation.md`；`1aff699e97cb` 修复 1280px 桌面制品表横向溢出。两项均进入本地 `main`，未推送远端。
- 文档边界：AgentCiCi 明确使用 backend/frontend 两个授权 OCI 镜像；Semattice 明确当前受管交付为 Linux amd64 二进制、静态资源与 migration，不虚构 OCI 镜像；Keycloak 使用官方 `quay.io/keycloak/keycloak:<approved-version>`。私有 registry、环境坐标和 Secret 均为受管占位符。
- 自动化：导航与文档定向 2 个测试文件、24 项通过；前端全量 55 个测试文件、304 项通过；TypeScript/Vite production build 通过，仅保留既有 bundle-size warning；`git diff --check` 与 `check-no-environment-domains.sh` 通过。
- 本地 frontend：从本地 `main@1aff699e97cb` 构建 `2.8.66-dev.1aff699`，镜像 ID `sha256:ba016899fd9086d3cb11cded37198dceb2afb6753acb5ace3499dd330c7c4f5c`；容器 ID `7ea2c6961115`，healthy/restart=0，Nginx 配置有效，近 5 分钟 HTTP 5xx 与 error/emerg/crit/alert 为 0。
- 路由与制品：正式 HTTPS 页面路由 200；Markdown 200、`text/markdown`、`X-Content-Type-Options: nosniff`、8 个编号章节和稳定 `document_id`；运行 JS bundle 回读“运维中心”。backend、Semattice、DevAutopilot 均继续 healthy；本次未重建这些服务。
- 版本边界：backend 未受本次纯前端功能影响，继续为 `2.8.66-dev.a6427a9 / a6427a94548d`、healthy/restart=0。因前后端 commit 不同，本证据只声明 frontend 单服务更新，不声明 AgentCiCi 整产品同提交；本次不涉及共享基础设施或跨项目契约，未执行完整 `./stack verify`。
- 桌面视觉：通过临时未提交预览入口渲染同一生产 React 组件与 CSS；1280×720 检查顶部、制品来源和验收回滚三段，主区域无横向溢出、唯一长文滚动容器、章节锚点正确、表格和代码块可读、console 0 error/warn。临时预览文件已删除。
- 治理校验：`validate-state.py .claw` 已执行；TASK-328、FEAT-200、当前状态与本轮时间格式未产生新错误。全局仍因既有 goals 时间格式、历史 feature front matter/status、完成任务未归档和 owner role 债务返回非零，本任务未批量改写历史事实。
- HUMAN 边界：应用内浏览器和 Chrome 现有运营平台登录态均已过期，正式认证路由被正确重定向到 `/platform/login`；未读取或填写凭据，登录后的真实导航点击、锚点和 Markdown 新窗口仍待 HUMAN 复核。UAT、生产、ACR 和其他子仓均未修改。

## 2026-08-21 TASK-327 微信客服手机端人工接管（本地技术验收）

- 状态：`passed_task_327_local_technical_pending_real_wecom_human_acceptance`；代码、确定性回归、全新数据库迁移、JPA 启动和本地 main 全栈技术门禁通过，真实企业微信业务验收待 HUMAN。
- 后端聚焦：`WecomKf*Test` 共 8 个测试类、22 项通过，0 failure/error/skipped。覆盖消息 `origin=3/4/5`、人工消息不触发模型、客户明确转人工、发送前 revision fence、接管写后回读、revision 冲突、幂等重放、跨租户拒绝、独立自建应用 Secret、OAuth state 与 Secure/HttpOnly Cookie。
- 前端：全量 54 个测试文件、297 项通过；TypeScript/Vite production build 通过，仅保留既有 bundle-size warning。移动页没有聊天输入框，接管仅在 `SUCCEEDED + readback_state=3` 后调用企业微信原生 `navigateToKfChat`。
- 迁移与运行：专用临时 PostgreSQL 16 数据库从空库完整应用 119 项迁移到 V123，JPA `ddl-auto=validate` 通过，临时 backend 正常启动；回读 `remote_service_state/target_state=integer`、`state_revision=bigint`、`operation_id=uuid`，临时库验证后已删除。
- 构建与静态：`mvn -q -DskipTests package`、`git diff --check` 通过。完整 Maven 套件仍在既有 `KnowledgeBaseLifecycleIntegrationTest` 连接宿主机 `localhost:5432` 阶段持续重试并人工停止，未进入断言；不据此宣称后端全量通过。
- 协议核验：按企业微信官方当前文档复核状态 0–4、`origin=3/4/5`、接待人员 `status=0` 和 `openKfId + externalUserId` 原生跳转字段。真实 OAuth、真实接待人员列表、微信客户消息、状态 3 接管和原生会话跳转仍为 HUMAN pending，未用 mock 冒充。
- 本地主线与制品：功能提交 `a6427a94548d84feac601f45ac7efc544cbce651` 已 fast-forward 进入本地 `main`。backend/frontend 运行版本均为 `2.8.66-dev.a6427a9`、revision `a6427a94548d`；镜像 ID 分别为 `sha256:a9b13cf387862d8cb561da2032e741e1828ebe251abd0b5d42165d75c1a496f1` 和 `sha256:cd65a06af20fdd78049219dd81906a164c270a36d2604a5f8fea4ad37478d231`。
- 本地全栈：受管 `./stack up` 完成并由其内置 `./stack verify` 通过环境域名源码门禁、共享数据库隔离、TLS edge、OIDC discovery、应用健康/版本和匿名授权边界。该受管命令刷新了四个应用容器；最终 backend/frontend/Semattice/DevAutopilot 全部 healthy、restart=0。AgentCiCi backend 启动日志显示 V123 成功且无 ERROR/Exception。
- 运行回读：`/mobile/wechat-kf=200`、首页 `200`；匿名移动上下文为 JSON `401`，无效入口为 JSON `400`，匿名微信客服管理 API 为 JSON `401`。页面资源为 `index-DTN9-mHu-2.8.66-dev.a6427a9.js` 与 `index-BZQn4cEg-2.8.66-dev.a6427a9.css`；开发库回读 V123 success，微信客服账号/已启用移动账号/接管操作均为 0。
- 移动视觉：390×844 受控浏览器用 mock API 仅验证 UI，三种权威状态、统计与筛选、44px 主动作、当前坐席和文字化 fence 状态可读；首次点击“强制接管”后按钮变为“确认强制接管”并展示 AI 发送阻断说明。mock 未计入真实业务验收。
- 治理校验：`validate-state.py .claw` 已聚焦确认 TASK-327/FEAT-199 无新增错误；全局仍因仓库既有历史任务归档、旧规格状态/front matter 与旧时间格式债务返回非零，本任务未批量改写历史事实。
- 待验收边界：当前没有真实微信客服账号、Secret 或接待人员配置，未执行真实手机 OAuth、客户消息、状态 3 写后回读、人工回复无 AI 双发或原生会话跳转；未发布 UAT/生产，也未推送远端。

## 2026-08-20 TASK-326 UAT `2.8.66-beta.1`

- 状态：`passed_task_326_uat_technical_and_service_contract`；登录态缺陷业务验收待完成。
- 冻结提交、远程 `main` 与标签解引用均为 `2c9d3821b458`；backend/frontend 不可变 ACR index digest 分别为 `sha256:314dbfb573ce703ab927c9cde1a5ecbc7349291116899f2931481e1418945d5f`、`sha256:6279ef657523fb0d001f2ebe43b3c2d43baa135e3636ac99f3e05a462d37e0b7`。
- 仅重建 backend/frontend；六容器 healthy/restart=0、health UP、版本提交一致、Flyway V122、Nginx、公开 smoke、匿名 JSON 401 和错误日志门禁通过，四个状态服务未重建。
- 12 项备份工件、SHA-256 清单和 PostgreSQL custom dump 恢复目录通过；目标 SERVICE 身份模板探测返回 7×87、state=applied。生产未修改。

## 2026-08-20 TASK-326 DevAutopilot 7×87 模板消费契约

- 状态：`passed_task_326_7x87_consumer_contract`。
- `DevAutopilotTenantApplicationReadinessTest` 14 项通过，0 failure/error/skipped；覆盖精确 `shape-7x87` 幂等键、7×87 正例、历史 7×86 失败关闭和旧 6 对象失败关闭。
- `DevAutopilotActivationRecoveryIntegrationTest` 在当前本机条件下 1 项 skipped、无 failure/error；backend package 与 `git diff --check` 通过。
- `validate-state.py .claw` 仍只报告既有历史 front matter、旧任务归档和状态枚举债务，没有 TASK-326/FEAT-198 新错误；本任务不扩大范围修复历史治理债务。
- Semattice 提供方 UAT `1.0.7-beta.5 / 54f2ab93558f` 已发布，AgentCiCi 与 DevAutopilot UAT 候选仍待发布。

## 2026-08-19 TASK-325 项目改名确定性执行与可信回执

- 状态：`failed_task_325_product_version_fingerprint_mismatch`；代码与 backend 单服务门禁通过，整体产品环境版本门禁失败。
- 根因回归：覆盖截图精确确认语句与旧协议不一致、update dispatcher 缺失、旧回执不能满足可信门禁、写后数据仍是旧值四个缺口。
- 安全与执行：覆盖普通自然语言/结构字段/非法数值拒绝，update Tool 不进入自由模型定义，只有服务端确定性确认可调度；写入使用当前 revision 和稳定幂等键，响应与写后查询都核对 record ID、revision 和目标值。
- 结果语义：成功回执按统一字段渲染，不再显示“内部字段已隐藏”；写后查询漂移失败关闭，重复确认返回带真实回读的幂等 NOOP。
- 后端定向：`AliyunBailianClientTest`、`DevAutopilotDialogueDecisionServiceTest`、`ToolOrchestratorServiceTest`、`ChatOrchestratorServiceModelIdentityTest`、`DeliveryWriteReceiptGuardTest`、创建/修改/删除/转派 Tool 与受理校准共 10 个测试类、97 项通过，0 failure/error/skipped。
- 构建与静态：`mvn -q -DskipTests package`、`git diff --check` 通过。
- 状态校验：`validate-state.py .claw` 仍被已有 goals 时间格式、历史规格 front matter/状态、旧完成任务未归档等存量债务阻断；输出没有 TASK-325、FEAT-192 或本轮时间格式错误，本任务未扩大范围修复历史治理债务。
- 本地运行：backend 单服务为 `2.8.66-dev.77ce909 / 77ce9095f2bc`，镜像 ID `sha256:9b39d55c2ba4019c2a71d3709570f9a002d647a4b1bf897b0931d40f79cc383c`，healthy/restart=0、health UP；这些证据只证明 backend，不证明整个产品版本一致。
- 失败证据：frontend 容器和镜像仍为 `2.8.61-dev.1ad25d3 / 1ad25d3923de`，用户可见角标与只读 `docker inspect` 一致。前后端基础版本和 commit 混合，因此撤销此前本地产品环境已完成的结论。标准 `./stack version` 另被既有 Semattice `config=1.0.5/repository=1.0.7` 漂移阻断。
- 业务边界：没有执行截图中的项目改名或其他 Semattice 写入；必须先修复混合版本并完成前后端联合回读，之后才能进行 HUMAN 对话验收。UAT、生产、DevAutopilot 与 Semattice 未部署。

## 2026-08-19 TASK-324 产品经理上下文澄清回归

- 状态：`passed_task_324_local_technical_pending_human_dialogue`。
- 根因回归：截图首轮文案精确对应结构化需求草案缺少 `title/pm_assessment` 时的固定回退；新回归覆盖结构化模型已给出聚焦问题、低置信但上下文明确、以及字段仍不完整三个分支，均保留原需求并只询问影响验收的产品选择。
- 后端定向：`AliyunBailianClientTest`、`DevAutopilotDialogueDecisionServiceTest`、`DeliveryWriteReceiptGuardTest`、`ChatOrchestratorServiceModelIdentityTest`、创建/删除/转派 Tool 和历史受理校准共 8 个测试类、82 项通过，0 failure/error/skipped。
- 构建与静态：`mvn -q -DskipTests package`、`git diff --check` 通过。
- 状态校验：`validate-state.py .claw` 仍被已有 goals 时间格式、历史规格 front matter/状态、旧完成任务未归档等存量债务阻断；输出没有 TASK-324、FEAT-192 或本轮时间格式错误，本任务未扩大范围修复历史治理债务。
- 本地运行：`main@a9e3d1b0fc06` 构建为 `2.8.66-dev.a9e3d1b`，镜像 ID `sha256:437dc98af23f0764e341f5d9668380252aff80e9ffd17899fafb5b601832aa75`；image/容器环境/`system/version` 指纹一致，backend healthy/restart=0、health UP，正式入口 200、匿名 JSON 401、DevAutopilot integrated=true/true，启动 severe 日志 0。
- 编排边界：仅重建并替换 backend，其他容器创建时间保持原值。标准 `./stack version` 被既有 Semattice 基础版本 `config=1.0.5/repository=1.0.7` 漂移失败关闭，未修改 cc-local-stack 或 Semattice；因此本轮只声明目标服务门禁，不声明完整 `stack verify`。
- 业务边界：未发送真实产品经理消息或执行任何 Semattice 写入；首轮模型答复仍待 HUMAN 在新会话重试。UAT、生产、DevAutopilot 和 Semattice 未部署。

## 2026-08-19 INT-027 UAT DevAutopilot SERVICE 身份修复

- 状态：`passed_int_027_uat_service_identity`。
- 运行配置：最终 UAT `2.8.65-beta.1 / 784ccd23e933` 回读 `APP_AUTH_OIDC_SERVICE_TOKEN_EXCHANGE_ENABLED=true`；六容器 healthy/restart=0，四个状态服务容器指纹未变化。
- 权限：Wukong SERVICE 最终 scope 精确为 `identity.principal.sync,runtime.record.create,runtime.record.read,runtime.record.update`；匿名 OACT 交换返回 401，未扩大其他 Principal 权限。
- CLI：`doctor` ready，兼容性 `1.0.4-beta.8 / cli/v2`，Wukong identity active；容量 `0/2`、可用 `2`；任务查询成功返回空队列，不再出现 Keycloak/OACT 认证错误。
- 运行门禁：公开六项只读 smoke、Nginx 校验、backend health UP 与 30 秒稳定窗口通过；backend severe=0、frontend 5xx=0。生产未修改。

## 2026-08-19 TASK-323 UAT、生产与真实租户恢复

- 状态：`passed_task_323_uat_production_and_business_recovery`。
- UAT `2.8.65-beta.1 / 784ccd23e933`：backend/frontend healthy、restart=0，版本回读一致；备份 `/data/apps/agentcici/backups/20260819T101657Z-before-2.8.65-beta.1` 的 SHA-256 与 PostgreSQL dump 列取校验通过，公网首页 200，DevAutopilot integrated health 为 true/true。
- 生产 `2.8.65 / 784ccd23e933`：backend/frontend digest 为 `sha256:4c4a1c4040872081777d6b3b7c60a5a6ca6892ff650d11545c9ab9e495d97039` / `sha256:37922c74ddc518500abe68b81e40e9b8ad8a96011185aef1e6cb094e6c828ae1`；六容器 healthy/restart=0，health UP，Nginx 有效，Flyway 118 项且最新 V122，匿名 `/auth/me` 为 JSON 401，15 分钟 ERROR/FATAL/Exception 为 0。
- 登录态业务验收：`org5nszpgj99jaysxv6y` 重试后 DevAutopilot 为运行中/ACTIVE/semattice 已就绪；`orgl624a7r54pzp3e5zv` 保持运行中/已完成。两者均已开通 3、待处理 0；数据库均为 ACTIVE/ACTIVE、资源 2、标准 PM scope 3、标准 Agent 已发布且 Web 渠道启用。
- 数据保护：生产发布前后知识库均为 9 个知识库、35 个文档、661 个 chunk、29 个文件和 549 个 Qdrant points；四个状态服务容器 ID 未变化。

## 2026-08-19 TASK-323 DevAutopilot 机器身份默认 scope

- 根因：生产未注入模板 PM/developer scope，构造器把缺省值归一化为空列表，开通在 `PRODUCT_MANAGER_READY` 创建 SERVICE Principal 时返回“机器账户至少需要一个 scope”。
- 修复：空或纯空白配置回退到服务端固定的 `runtime.record.read/create/update` 最小集合；显式非空配置仍经规范化后使用。
- 验证：聚焦 readiness/recovery 测试通过；全新 PostgreSQL 16 完整执行 118 项 Flyway migration 后恢复 Saga 集成测试通过；`mvn -DskipTests package` 通过。
- 全量 backend 测试在既有 `KnowledgeBaseLifecycleIntegrationTest` 默认数据源不可达处持续重试，未进入断言并人工终止；未据此宣称全量通过。

## 2026-08-19 AgentCiCi 生产 `2.8.61`

- 正式 tag `2.8.61^{}`、UAT `2.8.61-beta.31^{}` 与运行 revision 均为 `5b67f80de884`；backend/frontend 正式 ACR index digest 已回读，未更新 `latest`。
- 发布前 PostgreSQL custom dump 可由 `pg_restore` 列取，KB 29 文件归档/逐文件 SHA-256、Qdrant 549 points 原生 snapshot 和 `2.8.60` 两项旧镜像均通过校验。
- Flyway V122 成功；清理后旧非 UUID session 为 0，唯一新 `openapi` session 是 V122 后创建的服务端 UUID。全部知识库相关表行数、KB 文件清单和 Qdrant points 发布前后一致。
- 仅 backend/frontend 重建；database/Qdrant/Redis/RabbitMQ 容器 ID 不变，六容器 healthy/restart=0，backend health UP，公网首页 200、匿名 `/me` 为 JSON 401，启动后 severe 日志为 0。
- 状态：`passed_agentcici_2_8_61_production_technical_gate`；生产登录态产品经理对话、页面视觉和真实模型调用仍需 HUMAN 业务验收。

## 2026-08-19 AgentCiCi UAT `2.8.61-beta.31`

- 状态：`passed_agentcici_2_8_61_beta_31_uat_technical_gate`。
- 源码/制品：本地与 `origin/main`、tag peeled commit 均为 `5b67f80de884`；backend/frontend linux/amd64 ACR digest 与运行容器一致，未更新 `latest`。
- 运行/边界：仅 backend/frontend 重建，四个状态服务 ID/重启计数不变；health/version、Nginx、匿名 JSON 401、UAT 六项公开 smoke 和 30 秒稳定日志门禁通过。
- 恢复：备份 `/data/apps/agentcici/backups/20260819T065648Z-before-2.8.61-beta.31` 12 项非空工件及 SHA-256 清单通过；HUMAN 登录态业务验收待完成，生产未修改。

## 2026-08-18 TASK-322 Agent Definition 对外身份一致性

- 状态：`passed_task_322_local_technical_pending_real_greeting`。
- 身份聚焦回归：`SkillPromptAssemblerTest` 2 项、`SkillResolverPinnedRuntimeBoundaryTest` 1 项、`SkillDefinitionServicePlatformSnapshotTest` 1 项、`MeetingMinutesServiceTest` 1 项、`ChatOrchestratorServiceModelIdentityTest` 43 项，共 48 项通过，0 failure/error/skipped。
- 身份断言：平台基础提示不再包含 `You are CiCi`；统一运行时提示从 `AgentDefinition.name` 注入唯一对外名称，要求自我介绍精确使用该值，并明确角色、Skill、工具、模型和 `agentId` 均不得覆盖名称。默认内置 Agent 使用自身 Definition 名称 `思思（CiCi）`。
- 合并回归：连同 TASK-321 模板分层用例共 10 个测试类、65 项通过；后端 `mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 本地运行：提交 `e91b28d6` 构建为 `2.8.61-dev.e91b28d`；backend healthy/restart=0，health=UP，正式 DevAutopilot 路由 200，启动后 severe 日志 0，其他服务未重建。
- 业务边界：已登录产品经理页面只读可用；真实“你好”发送待用户即时确认。未修改 UAT、生产、DevAutopilot 或 Semattice。

## 2026-08-18 TASK-321 产品经理初始化模板分层

- 状态：`passed_task_321_scoped_code_with_shared_db_integration_blocked`。
- 分层回归：`DevAutopilotProductManagerAgentPublisherTest` 2 项、`DevAutopilotProductManagerTemplateTest` 1 项、`DevAutopilotTenantApplicationReadinessTest` 11 项、`AgentCompileSkillDagTest` 2 项、`SkillResolverServiceTest` 1 项、`SkillDefinitionServicePlatformSnapshotTest` 1 项，共 18 项通过，0 failure/error/skipped。
- 断言覆盖：新建模板分别写入标准系统提示词和 8 步流程 Spec；存量补偿校准两者；Spec IR 识别 query/intake/planning/create/update/delete/transfer/review/acceptance/handoff；Skill 独有工具进入 Spec IR 和 workflow code，但不写回 Agent 静态绑定。
- 构建与静态：后端 `mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 扩展集成边界：`OrchestratorIntegrationTest` 在 Spring 上下文启动后持续重试本机共享 PostgreSQL，未进入目标断言；等待 60 秒后主动停止，退出 130。不修复共享测试数据库，也不把本次聚焦通过扩写为完整集成套件通过。
- 运行边界：随 `main@e91b28d6` 更新本地 backend，版本、health、restart、正式路由和服务隔离门禁通过；未执行真实租户 `initializations`，未修改 UAT、生产、DevAutopilot 或 Semattice。

## 2026-08-18 TASK-319 UAT `2.8.61-beta.30`

- 状态：`passed_agentcici_2_8_61_beta_30_uat_technical_gate`。
- Source/制品：冻结时本地与远程 `main`、annotated tag peeled commit 和运行 commit 均为 `39424a982068`；backend/frontend linux/amd64 ACR index digest 分别为 `sha256:5b102dd48d1920a569073403db8c3292c8206de5364c5852e3414026b8456767`、`sha256:6f7fe1aac99b740854448e764160b71a120be4be43ffe46f8aadb739a0424a52`，未更新 `latest`。
- 构建门禁：`release-acr.sh --dry-run --channel test --no-latest` 生成同一候选；后端 production package、前端 TypeScript/Vite production build、镜像推送与 manifest inspect 通过，Git tag 已推送。
- 备份与回滚：`/data/apps/agentcici/backups/20260818T093113Z-before-2.8.61-beta.30` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.29 两项旧镜像、容器状态、回滚说明和 SHA-256 清单，共 11 项、约 303MB；全部非空、`root:root 0600`，dump、tar、gzip 和清单校验通过。即时应用回滚目标 beta.29；数据库恢复需单独批准。
- 运行门禁：仅 backend/frontend 重建，四个状态服务 ID 哈希发布前后均为 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。六容器 healthy/restart=0，health=`UP`，版本/commit/RepoDigest 一致，Flyway V122/V121/V120 成功，Nginx 有效；运行 CSS 包含 `width:fit-content`、`text-overflow:clip`、`overflow-wrap:anywhere`。
- 公网与稳定性：发布前后两轮 UAT 首页、Keycloak discovery、Semattice health/version 和 DevAutopilot integrated health 均通过；`/app=200 text/html`，匿名 `/auth/me=401 application/json`。90 秒稳定窗口 backend severe=0、frontend 5xx/upstream=0。
- 状态治理：`git diff --check` 通过；全仓 `validate-state.py .claw` 仍因既有历史规格 front matter/status、旧时间格式、终态任务未归档和归档数量债务返回 1，输出无 TASK-319 finding，本轮未越界清理历史事实。
- 边界：本候选没有新增、启用或切换跨项目契约；未代用户登录或执行组织切换业务操作，真实长组织名称下的视觉、hover/focus 与用户接受待平台用户完成。生产未修改。

## 2026-08-18 TASK-319 远程主线同步与 UAT 发布预检

- 状态：`passed_task_319_remote_main_and_uat_preflight_with_deploy_blocked`。
- Source：本地 `main@e1ecaeec42c9` 已快进推送至 `origin/main`，推送后 ahead/behind=`0/0`；远程主线包含实现 `1ad25d39` 与验证记录。工作树干净，`git diff --check origin/main..main` 通过。
- UAT 只读基线：AgentCiCi 首页 200、匿名 `/auth/me` 401、Keycloak discovery 200、Semattice health/version 200、DevAutopilot integrated health 200。
- 候选计划：`release-acr.sh --dry-run --channel test --no-latest` 生成 `2.8.61-beta.30 / e1ecaeec42c9 / linux/amd64`，计划只推不可变 backend/frontend tag 并创建 annotated Git tag；dry-run 未执行构建、推镜像或 tag 写入。
- 阻塞：当前会话没有可读 `CICI_SAAS_SSH_IDENTITY_FILE`，无法回读 UAT 主机当前运行 commit/镜像、确认回滚点、创建完整备份或执行受管 Compose 单产品切换。遵循发布停止条件，未写 UAT；生产未修改。身份可用后必须按届时远程 `main` 重新 dry-run，不能沿用本条作为实际发布证据。

## 2026-08-18 TASK-319 组织切换弹层完整显示组织名称

- 状态：`passed_task_319_local_technical_with_authenticated_visual_pending`。
- 自动化：`theme.test.ts` 12/12 通过，断言弹层 `fit-content`、`192px` 最小宽度、桌面视口上限及名称不含 `ellipsis/nowrap`；前端全量 53 个测试文件、294 项全部通过。
- 构建与静态：TypeScript/Vite production build、`DESIGN.json` 解析和 `git diff --check` 通过；仅保留既有大 chunk warning。
- 本地主线与运行：实现提交 `1ad25d3923de` 已进入本地 `main`；frontend 镜像 `sha256:d1aa950dc64daced7c21a870242ad5d71ff8ffa833eb15e574babe5c72b5b9ce`，label/version/revision 为 `2.8.61-dev.1ad25d3 / 1ad25d3923de`。仅 `cici-frontend` 重建，healthy/restart=0；`/app=200 text/html`、Nginx 有效，运行 CSS 资产包含自适应宽度规则。
- 视觉边界：浏览器中既有员工会话已过期，刷新后按预期进入统一登录页；未读取或代填凭据，也未绕过认证。真实组织数据下的弹层截图、hover/focus 与用户视觉接受仍待重新登录后完成；远端、UAT、生产未修改。

## 2026-08-18 AgentCiCi UAT `2.8.61-beta.29`

- 状态：`passed_agentcici_2_8_61_beta_29_uat_technical_gate`。
- Source/制品：本地与远程 `main`、annotated tag peeled commit 和运行 commit 均为 `d2abc9c463b3`；backend/frontend ACR index digest 为 `sha256:56983d2a5ba8d9d94a66c910d95446d008cd1392ae3a0497cbe513c4f3fff8df`、`sha256:cae5a754b957c13b7753478fa40a2c950c7fcfcfccf381006d7eae5c2b65f6b9`，未更新 `latest`。
- 构建门禁：发布 dry-run 正确生成 beta.29；后端 `-DskipTests package` 和前端 TypeScript/Vite production build 通过。首次实际发布在隔离 worktree 缺少 `node_modules` 时于 `tsc` 前安全失败，未创建镜像、tag 或 UAT 写入；按锁文件 `npm ci` 后使用同一冻结版本成功重试。
- 备份与回滚：`/data/apps/agentcici/backups/20260818T040400Z-before-2.8.61-beta.29` 的 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.28 两项旧镜像和 SHA-256 清单全部非空、`0600` 且校验通过。应用回滚目标 beta.28；V122 清空的测试会话只可经单独批准整库恢复。
- 运行门禁：仅 backend/frontend 重建；四个状态服务 ID 哈希不变。V122=`true`，六容器 healthy/restart=0，health=`UP`、版本/commit/digest 一致，Nginx 有效；90 秒稳定窗口 backend severe=0、frontend 5xx=0。
- 公网与鉴权：两轮 UAT 首页、Keycloak discovery、Semattice health/version 和 DevAutopilot integrated health 通过；HTTP→HTTPS 301，应用中心、HTML 指南和 Markdown 地址为 200，Markdown 为 391 行 `text/markdown` 且 `nosniff`，匿名 auth/应用目录为 `401 application/json`。
- 边界：本次没有新增、启用或切换跨项目契约，未代用户执行登录态应用接入或新会话业务验收；生产未修改。

## 2026-08-18 TASK-318 OneKeyToken 按 Key 枚举可用模型

- 状态：`passed_task_318_scoped_with_authenticated_catalog_pending`。
- 真实网关负例：无效 Bearer Key 调用 `GET https://my.onekeytoken.com/v1/models` 返回 `401 application/json`，响应错误码 `unauthorized`，确认目标路由存在且鉴权语义与说明一致；未使用、输出或保存真实 Key。
- 聚焦回归：`ModelProviderServiceTest` 通过，覆盖保存 Key 的 Bearer 头、`GET /v1/models`、JSON Accept、两个模型解析、401 Key 轮换提示、403 账号/应用/`model:invoke` scope 提示及错误不泄露 Key；backend compile/package 与 `git diff --check` 通过。
- Spring/空库边界：一次性 PostgreSQL 16 从 V1 成功迁移 118 项至 V122；目标控制器测试在执行前被既有 `OfficialAccessTokenService` 测试配置漂移阻断，错误为启用 OACT 时 issuer/key ID/Semattice scopes 不完整。临时数据库容器已自动停止并删除；未修改迁移或共享测试库。
- 本地运行：提交 `1a1ab512` 已进入本地 main；backend 镜像 `sha256:8eb9abf014a2cfa520381bac6e89cc33f5eb49e924638256a09971296ee59af1`，label/version/revision 为 `2.8.61-dev.1a1ab51 / 1a1ab512782b`。仅 backend 重建，healthy/restart=0，health=`UP`，正式模型页 200，匿名枚举 API 为 `401 application/json`，启动后 ERROR/FATAL/Exception 均为 0。
- 业务边界：Chrome 与内置浏览器均无可复用平台管理员会话，因此没有绕过鉴权读取保存 Key；真实“全部模型”数量与名称待平台管理员登录后只读确认。未保存目录选择；提交进入本地主线并随本次同步推送远端，但未纳入冻结于 `d2abc9c4` 的 UAT beta.29，生产未修改。

## 2026-08-18 TASK-317 服务端 UUID 会话身份与历史完整性

- 状态：`passed_task_317_local_session_identity_and_history`。
- 根因：前端稳定键 `workbench:<agent>` 被直接用作全局 `chat_session.id`；同一键跨租户碰撞后，Trace/消息可观测但会话列表按租户/用户过滤为 0。
- 后端：相关 5 个测试类、test compile 和 `mvn -q -DskipTests package` 通过；独立 Spring 集成测试因本机未提供 `agentcici_test` 数据库未运行，租户/迁移门禁改由真实本地栈验证。
- 前端：全量 53 个测试文件、292 项、TypeScript 与 production build 通过；`git diff --check` 通过。
- 实库：V122 成功且 Flyway 回读 `success=true`；会话测试数据清空，UUID 检查、`(id, company_id)` 唯一性、渠道/source key 约束以及消息/状态/附件三项 `(session_id, company_id)` 复合外键生效。`workbench:*`、非 UUID 会话和三类孤儿记录均为 0。
- 登录态业务链路：目标租户 `org0gtwzqvxell4gly8s / CC DevAutopilot1` 自动创建首个会话，点击“新对话”创建第二个会话；刷新后历史列表仍为 2 条。数据库回读两个不同 UUID，均为 `web / USER / source_key=NULL`。
- 本地主线与运行：实现 `0b34fb65` 已进入本地 main；运行代码制品 `2.8.61-dev.0cd8887` 包含该提交，backend/frontend healthy、restart=0；完整 `cc-local-stack ./stack verify` 通过。后续 `ce7f8800` 仅更新验证文档，不影响制品。
- 边界：未代用户发送模型消息；会话创建、租户归属、UUID 持久化及刷新回显已验证。UAT、生产未授权、未修改。

## 2026-08-18 TASK-316 应用中心在线接入指南与阅读缺陷修复

- 状态：`passed_task_316_local_runtime_and_authenticated_visual`。
- 内容契约：12 个章节完整覆盖接入全景、准备、登记、Provider 生命周期请求与响应、HMAC canonical string、Secret 引用、连接、版本、依赖、发布、租户开通、运行期运维和排错检查；示例只使用保留测试域名，不包含真实环境地址或凭据。
- 定向验证：指南测试 5 项通过，覆盖章节顺序、真实生命周期字段、HMAC 顺序、机器 Markdown 的 12 章一致性、关键安全边界和环境域名负向门禁；初始入口回归 3 文件/26 项亦保持通过。
- 全量前端：53 个测试文件、293 项全部通过；TypeScript 与 Vite production build 通过，仅保留既有 chunk-size warning；`git diff --check` 通过。
- 桌面视觉与交互：使用用户已有的本地登录会话只读复核真实平台壳层。直接加载 `#connection` 后主区域 `scrollTop=3672.5`、目标顶部约 30px；浏览器文档 `scrollTop=0` 且 html/body 均为 `overflow:hidden`，页面只保留运营主区域一个滚动容器。代码块 `pre` 计算样式为 `rgb(36,31,26)` 背景、`rgb(245,236,220)` 文字、`0px` 边框，浅底浅字缺陷消失。
- 智能体地址：`/agent-docs/internal-applications/integration-guide.md` 为 391 行/15377 字节纯 Markdown，包含 `document_id`、契约版本、12 章目录、请求/响应/HMAC/连接/版本/依赖/排错示例；本地边缘返回 `200 text/markdown`、`X-Content-Type-Options: nosniff`、no-store，不要求登录或 JavaScript。
- 本地主线与制品：原始实现 `94f4e6bc` 及修复 `1f1d816c`、`4c368db3`、`0cd88875` 已进入本地 main；最终 frontend 镜像为 `2.8.61-dev.0cd8887`，revision `0cd888752cbb`、healthy/restart=0。backend 未受本次前端/静态文档修复影响。
- 本地运行：HTML 指南与 Markdown 路由均返回 200，frontend Nginx 配置校验通过；完整 `cc-local-stack ./stack verify` 通过域名门禁、共享数据库隔离、TLS、OIDC、应用健康/版本和匿名授权边界。
- 发布边界：远端、UAT、生产未修改。

## 2026-08-18 AgentCiCi UAT `2.8.61-beta.28`

- 状态：`passed_agentcici_2_8_61_beta_28_uat_technical_gate_with_business_acceptance_pending`。
- Source/制品：远程 `main` 包含冻结提交 `242074e72a9e`，annotated tag peeled commit、backend/frontend image label 和运行版本均为该提交；ACR index digest 为 backend `sha256:99851d50ad5f9c6ae72b02edf23a1f2949b60f2842179b91becb1eb0f4801c10`、frontend `sha256:e1231cd5366c6b4528569e665436374d8f28dde185f6ca018d5332d321c04953`，未更新 `latest`。
- 质量门禁：本次候选相关后端 12 个测试类共 87 项通过，0 failure/error/skipped；前端全量 52 个文件、289 项通过；production package/build 与 `git diff --check` 通过。后端全量套件仍不声明通过，沿用已记录的共享 PostgreSQL 历史债务边界。
- 备份与回滚：`/data/apps/agentcici/backups/20260818T020041Z-before-2.8.61-beta.28` 的 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.27 两项旧镜像和 SHA-256 清单均非空、`0600` 且通过读取校验；回滚目标 beta.27，仅重建 backend/frontend。
- 运行门禁：仅 backend/frontend 重建；database、Redis、RabbitMQ、Qdrant ID 保持不变。V121 成功，117 条 migration 成功；六容器 healthy/restart=0，health=`UP`、版本/commit/digest 一致，Nginx 有效，backend severe=0、frontend 5xx=0。
- 公网与鉴权：两轮 UAT 首页、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 全部通过；HTTP→HTTPS 301，`/app`、应用中心和系统 API 页面为 200，匿名 auth、应用目录和连接 API 均为 `401 application/json`。
- 契约与业务边界：候选新增通用 Provider 连接能力但没有创建或启用真实连接，TASK-315 复用既有模型与 Semattice Tool 契约；本次无新增/切换/启用的跨项目契约。未代用户发送产品经理对话、确认创建或写入 Semattice，真实 UAT 业务验收待已登录用户完成；生产未修改。

## 2026-08-17 TASK-315 产品经理结构化语义判定与标准输出

- 状态：`passed_task_315_local_runtime_with_authenticated_dialogue_pending`。
- 后端定向：`AliyunBailianClientTest`、`DevAutopilotDialogueDecisionServiceTest`、`DeliveryWriteReceiptGuardTest`、`ChatOrchestratorServiceModelIdentityTest`、创建/删除/转派 Tool 与历史受理校准 8 个测试类共 78 项通过，0 failure / 0 error。
- 覆盖：思考模型唯一工具 `auto` 判定、非思考命名工具协议重试、动作相关字段校验、结构化项目字段别名、隐含创建、否定表达、固定草案、低置信度澄清、研发查询工具裁剪、确认协议、伪成功阻断和 CRM 非误伤。
- 构建与静态：`mvn -q -DskipTests package`、`git diff --check` 通过。
- 全量边界：后端全量测试曾进入本机共享 PostgreSQL 不可用重试，未进入全部业务断言后手动中止；未修改或 repair 共享测试库，因此不声明全量套件通过。
- 真实模型协议：本地平台现用 `deepseek-v4-flash-0731` 的非写入 Provider 探测均返回唯一 `resolve_devautopilot_dialogue` Tool；隐含创建为 `CREATE_DRAFT/PROJECT` 且名称正确，否定解释为 `OTHER`。未创建聊天或 Semattice 记录。
- 本地主线与运行：`main@cc4312edde85` 构建为 `2.8.61-dev.cc4312e`；backend/frontend 镜像 ID 分别为 `sha256:440528145bcd3fa823f397d3cd1cacd676a237f2c1499dbb9de0fbd7e6daf1ae`、`sha256:defee9069ee87ea5e935e9f989f28b961b913477ca8a409001ebb3d39f703e32`，label revision 一致、healthy/restart=0。
- 运行门禁：内部 `/system/version` 回读 `cc4312edde85`、`/actuator/health=UP`；正式入口 200，完整 `cc-local-stack ./stack verify` 通过，启动后无本任务相关 ERROR。
- 浏览器边界：已登录 Chrome `/app` 回读新版本与产品经理入口；根据浏览器代表性通信边界未代用户发送消息。页面固定草案验收待用户完成；UAT、生产未修改。

## 2026-08-17 TASK-313 内部租户应用注册中心本地验证

- 状态：`passed_task_313_provider_lifecycle_local_with_authorized_business_acceptance_pending`。
- 后端：`InternalApplicationRegistryServiceTest`、`InternalApplicationProviderConnectionServiceTest`、`GenericTenantApplicationLifecycleServiceTest`、`TenantApplicationCatalogServiceTest` 定向通过，production package 通过；覆盖公网 HTTPS/内部网络边界、Metadata 拒绝、Secret 引用、活动连接稳定元数据、版本/连接契约门禁、依赖状态、真实本机 HTTP Provider 初始化回调和既有三应用兼容投影。
- 前端：全量 52 个测试文件、289 项通过；TypeScript 与 Vite production build 通过，仅有既有大 chunk warning。运行连接工作区、真实 Base URL/动作路径、版本连接选择和应用依赖选择器已进入制品。
- 全量边界：后端全量套件启动后持续等待本机未启动的共享 PostgreSQL，尚未进入业务断言；为避免无效等待手动中止，exit 130。本轮没有 repair 或改写共享测试库，定向测试与 package 无失败。
- 状态文件：全仓 `.claw` validator 被既有历史规格 front matter、旧任务归档等债务阻断，未报告 FEAT-191/TASK-313；本轮未扩张范围修复历史状态。
- 本地主线与迁移：实现提交 `f56055e921d2` 已进入本地 `main`；Flyway 实际从 V120 迁移至 V121，创建连接、修订、operation 与 step 表。
- 本地运行：backend/frontend 均为 `2.8.61-dev.f56055e`，镜像 revision `f56055e921d2`、healthy/restart=0；容器内 `/system/version` 和 `/actuator/health=UP`、正式页面路由 200、匿名连接 API JSON 401，完整 `cc-local-stack ./stack verify` 通过。部署 JS 回读“运行连接”“服务 Base URL”“添加依赖”“新建连接修订”。
- 浏览器边界：正式路由正确进入平台登录页，console 无 error/warning；未读取存储、猜测密码或绕过鉴权。授权态创建/测试/启用真实连接、发布版本和目标租户 ACTIVATE 仍待平台管理员业务验收。
- 发布边界：仅更新本地开发测试环境；远端 main、UAT、生产均未修改。
- 命名调整：用户界面的“租户应用目录”已统一改为“应用中心”，路由/API/内部 catalog 不变；`PlatformShell`、`PlatformInternalApplicationsPage`、`PlatformTenantApplicationsPage` 共 27 项通过，production build 通过。

## 2026-08-17 TASK-302 受信应用命名统一

- 状态：`passed_task_302_trusted_application_naming_local_with_authorized_visual_pending`。
- 前端验证：`PlatformSystemApisPage.test.ts` 10/10 通过；全量 52 个测试文件、287 项通过；TypeScript 与 Vite production build 通过，仅保留既有大 chunk warning；`git diff --check` 通过。
- 状态治理：全仓 `.claw` validator 仍被既有历史规格 front matter、旧完成任务未归档等债务阻断；错误清单未指向本次 FEAT-183/TASK-302 变更，本轮未扩张范围清理历史状态。
- 本地主线与制品：代码提交 `a81e3b727bfb` 已进入本地 `main`。为避开同工作树中其他未提交改动，前端镜像从包含该提交的最新代码主线 `2188e5760087` 的干净 Git 归档构建；镜像版本 `2.8.61-dev.2188e57`、revision `2188e5760087`。此后的 main 变更仅为状态文档，不影响制品。
- 本地运行：仅重建 `cici-frontend`；目标路由返回 200，Nginx 校验通过，frontend healthy/restart=0，运行 JS `index-Ppo-xn5B-2.8.61-dev.2188e57.js` 已回读“受信应用”。
- 浏览器边界：访问目标路由正确进入平台登录页，当前无平台管理员登录态，未伪造授权态视觉结论。远端 main、UAT、生产均未修改。

## 2026-08-17 AgentCiCi UAT `2.8.61-beta.26`

- Source 与制品：本地/远端 `main`、annotated tag peeled commit、backend/frontend image label 及运行版本均为 `a322fd91324b`；ACR index digest 为 backend `sha256:e755bc30929beefdadd68090c6510a7717401a51223dffc04a5c9a6dd774504a`、frontend `sha256:60ee57e4bd4b3498221f590c0e6bb3dcb2178a72c6392b817d8ba826b77173d7`。
- 发布前门禁：公开 UAT 六项只读 smoke 通过；当前 beta.25 六容器健康、前后端 restart=0；磁盘 180G、内存 25Gi 可用；受管 env 与 Docker pull 配置均为 root:root 0600。发布 dry-run 正确生成 beta.26，未更新 `latest`。
- 备份与回滚：`/data/apps/agentcici/backups/20260817T093959Z-before-2.8.61-beta.26` 全部工件非空且 0600；PostgreSQL dump 在 PostgreSQL 容器内通过 `pg_restore -l`，KB/Qdrant tar、beta.25 镜像 gzip 和 SHA-256 清单均通过。回滚目标为 beta.25，仅重建 backend/frontend。
- 运行验收：状态服务 ID 哈希发布前后不变；六容器 healthy/restart=0；backend health `UP`、版本/commit 回读一致；Flyway 成功校验 115 migrations 且 schema 无需迁移；Nginx 有效。公开首页、OIDC、Semattice 与 DevAutopilot health 通过，匿名管理 API 为 JSON 401，HTTP→HTTPS 301。
- 本次业务路径：真实未登录桌面浏览器访问 `/app` 后没有任何点击即进入 UAT 统一身份中心；受控 OIDC 失败态只保留主视觉与最小错误提示，旧表单容器 0、按钮 0，浏览器 0 error / 0 warning。启动稳定窗口 backend severe=0、frontend 5xx=0。
- 边界：本次没有跨项目契约增量、迁移或配置切换；未执行账号密码登录或租户业务写入。生产未修改。

## 2026-08-17 AgentCiCi UAT `2.8.61-beta.25`

- 状态：`passed_agentcici_2_8_61_beta_25_uat_technical_gate`。
- 冻结关系：远程 `main`、annotated tag `2.8.61-beta.25^{}` 与运行 commit 均为 `cc0e8078f5f5`；该候选包含 TASK-310 Owner 身份复用和 TASK-311 可恢复开通。
- 提供方门禁：Semattice UAT 为 `1.0.5-beta.2 / 0be03d018ecd`，`/healthz` 回读 schema `current=22 / required=22 / ready=true`；内部授权模板无签名 POST 为 `403 application/json / UNAUTHORIZED`，不是 404 或 SPA HTML。
- 制品与迁移：backend/frontend 均使用不可变 beta.25 镜像，image label 版本与 revision 一致；Flyway V118、V119 为 success。完整备份 `20260817T034412Z-before-2.8.61-beta.25` 的 Compose、PostgreSQL、KB、Qdrant、beta.24 前后端镜像、旧容器指纹均通过 `SHA256SUMS`。
- 运行稳定：仅应用容器处于 beta.25；database、Redis、RabbitMQ、Qdrant 保持 10 天运行。六容器 healthy/restart=0，backend health=UP，Nginx 校验通过；10 分钟 backend/frontend/Semattice 错误计数均为 0。
- 公网与鉴权：UAT 首页、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 全部通过；匿名 Owner 解析与 DevAutopilot activation 均为 JSON 401，Semattice 授权模板为 JSON 403。
- 验收边界：真实 HMAC 授权模板调用会写入指定租户的授权事实。本轮未指定 UAT 测试租户，未伪造 HUMAN 会话或制造业务写入；首次开通、失败后同键恢复、模板摘要和资源不重复仍待受权平台管理员验收。生产未修改。

## 2026-08-17 TASK-311 DevAutopilot 可恢复开通本地候选验证

- 持久化状态机：开通阶段固定为 `PROVISIONING → METADATA_READY → PRODUCT_MANAGER_READY → PRINCIPALS_READY → AUTHORIZATION_READY → ACTIVE`；5 分钟租约防止并发重复执行，失败保存 `failed_stage`、稳定错误码和尝试次数，同一幂等键从最后检查点恢复。
- 真实 PostgreSQL 16：Flyway 从已有 V118 升级 V119 后回读全部恢复字段；首次授权故障持久为 `FAILED / AUTHORIZATION_READY / SCHEMA_MIGRATION_REQUIRED / attempt=1`，同一幂等键重试后成为 `ACTIVE / attempt=2`，元数据、产品经理主体和两项资源均未重复创建。首次从本地 `main` 部署时还捕获到 V117.1 低于已应用 V118 的顺序冲突，未启用 Flyway `outOfOrder`，而是将本任务迁移永久前移到 V119。
- 后端定向：`DevAutopilotTenantApplicationReadinessTest`、`DevAutopilotHandoffServiceTest`、`DevAutopilotExecutionAuthorizationServiceTest`、`SematticeDevAutopilotAuthorizationClientTest` 通过；`mvn -q -DskipTests package` 通过。
- 前端：`PlatformTenantApplicationsPage.test.ts` 7/7 通过，production build 通过，仅有既有 chunk-size warning；失败/执行中状态显示持久阶段和安全错误码，并以稳定幂等键执行重试。
- 全量边界：`mvn test` 仍被共享 `agentcici_test` 既有 Flyway V81 checksum 漂移和无关 Tavily Secret 测试阻断；未 repair 共享测试库，定向任务回归和 clean package 均通过。
- 状态：`passed_task_311_local_candidate_with_known_full_suite_debt`；本地开发环境已更新，UAT/生产尚未发布。
- 本地运行：`main@0c56f468b8f8` 已构建为 `2.8.61-dev.0c56f46`，V118/V119 成功，backend/frontend healthy、restart=0，完整 `./stack verify` 通过；登录后运营页面回读同一版本且现有 DevAutopilot 为“运行中 / 初始化已完成”。该页面业务回读与失败恢复集成测试分开记录；UAT/生产尚未发布。

## 2026-08-17 TASK-310 新租户 Owner 全局身份复用

- 状态：`passed_task_310_local_with_authorized_visual_pending`。
- 身份与幂等：后端定向 2 个测试类、11 项通过，覆盖同一手机号/邮箱账号复用、跨账号冲突、新账号、公共编号、已有账号统一身份协调、已激活身份不重复 provisioning，以及同键结果重放；`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 前端：定向 4 项、全量 50 个测试文件/279 项、production build 通过，仅保留既有大 chunk warning。弹窗已改为“租户信息 → Owner 身份 → 确认开通”，已有用户默认、结果脱敏、新用户先预检、冲突不写入。
- 后端全量边界：`mvn -q test` 的 Spring 启动型用例在断言前被共享 `agentcici_test` 既有 Flyway V81 checksum 漂移阻断（applied `2112500543`、local `379982424`）；本轮未 repair。新增定向测试无失败。
- 本地主线与运行：提交 `4e11acc1` 已进入本地 `main`；backend/frontend 均从该提交构建为 `2.8.61-dev.4e11acc`。Flyway 从 V117 成功前进至 V118；backend/frontend 均 healthy、restart=0，backend `/system/version` 回读 commit `4e11acc14f2d`，完整 `cc-local-stack ./stack verify` 通过。
- 浏览器边界：正式 `https://cici.localhost/platform/tenants` 正确进入运营平台登录页，console 无 error/warn；当前没有可复用的已登录平台会话，未绕过鉴权，因此授权态的已有用户复用、新用户预检、冲突提示与最终开通仍待运营人员验收。
- 发布边界：仅更新本地开发测试环境；UAT/生产未授权、未修改。

## 2026-08-14 TASK-309 对话连续粘贴图片本地端到端验证

- 状态：`passed_task_309_local_e2e_with_known_full_suite_debt`
- 后端定向：`ChatAttachmentServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 共 47 项通过，覆盖签名检测、MIME/内容拒绝、20 MiB 超限预检、10 张额度、归属失败关闭、多模态请求组装和既有聊天编排回归；`mvn -q -DskipTests package` 通过。
- 前端：新增附件规则 3 项通过；全量 50 个测试文件、278 项通过；production build 通过，仅保留既有大 chunk warning。
- 全量后端边界：`mvn -q test` 运行 810 项后为 24 failures / 201 errors / 3 skipped；Spring 集成上下文的主要共同根因是共享 `agentcici_test` 已存在的 Flyway V81 checksum 漂移（applied `2112500543`、local `379982424`），本轮未执行 repair。另有既有模型调用断言失败，因此全量套件不计为本任务通过。
- 静态与主线：后端 clean compile、前端 TypeScript/build 与 `git diff --check` 通过；`a9d838b6`、`b7e03a56`、`aaf9706b` 已进入本地 `main`。
- 实库迁移：V116 创建附件表后，真实启动发现 `slot_no SMALLINT` 与 JPA `int` 校验不一致；未改已执行迁移校验和，新增 V117 向前转换为 `INTEGER`。V116/V117 均回读成功，backend healthy/restart=0。
- 开发环境：页面版本 `2.8.61-dev.aaf9706`；frontend healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过域名门禁、共享数据库隔离、TLS、OIDC、应用健康/版本和匿名鉴权边界。
- 已登录桌面浏览器：两张真实 PNG 上传后均显示缩略图和“已就绪”，删除与替换成功；删除最后一张后队列消失、提示改为“已删除图片”且不残留旧计数。选择非 vision 模型发送返回 `409 VISION_MODEL_REQUIRED`，当前页保留文本和附件供恢复；刷新后该失败消息与图片均未落库。系统剪贴板图片注入不由当前浏览器自动化接口提供，Ctrl/Command+V 图片提取由 `chatAttachments.test.ts` 定向测试覆盖。
- 发布边界：仅更新 `https://cici.localhost/` 本地开发测试环境；UAT/生产未授权、未修改。

## 2026-08-14 TASK-308 任务评审驳回授权修复

- 红灯复现：更新后的 `DevAutopilotExecutionAuthorizationServiceTest` 在实现前因 `TASK_REVIEW` 未请求 `identity.principal.sync` 失败，证明旧固定 scope 与实际评审调用链不一致。
- 后端验证：`mvn -q -Dtest=DevAutopilotExecutionAuthorizationServiceTest test` 与 `mvn -q -DskipTests package` 通过；`git diff --check` 通过。
- 本地运行：backend 从本地 `main@95656c5b564d` 构建为 `2.8.61-dev.95656c5`，镜像 `sha256:99bc4b9938de4243c8a83f4648b0f6187f202a4b8e381116199172c56043ca79`，healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过。
- 真实业务：HUMAN 二次确认后委托产品经理 SERVICE，以原始意见成功产生 `design_changes_requested / changes_requested`；任务由 `设计待确认 / revision 4` 更新为 `设计驳回 / revision 5`。UAT/生产未修改。
- 状态：`passed_local_task_review_rejection_e2e`。

## 2026-08-14 TASK-308 DevAutopilot 任务评审委托授权

- 后端定向：`mvn -q -Dtest=DevAutopilotExecutionAuthorizationServiceTest test` 通过；新增用例固定验证 `TASK_REVIEW` 只签发 `runtime.record.read/create/update`。
- 本地主线与运行：提交 `44f4a6f9` 已进入本地 `main`；backend/frontend 运行 `2.8.61-dev.44f4a6f`，healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过。
- 消费联调：DevAutopilot `3d4ad9e` 的 59 项 Node 测试通过，包含 HUMAN 明确确认委托给产品经理 SERVICE 的 HTTP 回归。已登录真实页面显示任务提交版本和二次确认门禁。
- 业务边界：只打开并返回评审二次确认，没有提交批准或驳回；CLI 回读任务仍为 `设计待确认 / revision 4`。UAT/生产未修改。

## 2026-08-14 TASK-307 复制确认与 DEV 版本修复

- 运行日志：截图对应会话在 `semattice_project_delivery_transfer` 后仍调用只读 query 并开始 LLM 流；该证据说明精确确认没有被解析，不能归因于浏览器缓存或用户未确认。
- 后端验证：`mvn -q -Dtest=SematticeProjectDeliveryTransferToolServiceTest,DeliveryWriteReceiptGuardTest test`、`mvn -q -DskipTests package` 与 `git diff --check` 通过。新增用例覆盖带反引号和中文句号的复制确认；已识别确认的能力缺失直接失败关闭。
- 本地开发环境：backend/frontend 同从本地 `main@107ac0440dc3` 构建，镜像 label 均为 `2.8.61-dev.107ac04`；backend `/system/version` 与首页资源 `index-BOe72A75-2.8.61-dev.107ac04.js` 回读一致，两个容器 healthy/restart=0，完整 `cc-local-stack ./stack verify` 通过。
- 验收边界：未使用或伪造登录会话，未重放确认、未修改任务 owner；UAT/生产未修改。真实业务转派待用户在已登录会话再次确认。

## 2026-08-14 TASK-307 确认式转派降级修复

- 故障证据：精确确认已调用 `semattice_project_delivery_transfer`，但旧编排随后调用只读 query 并把模型回复发送给用户；因此既没有写入，也错误索要 Principal ID。
- 修复验证：`mvn -q -Dtest=SematticeProjectDeliveryTransferToolServiceTest,DeliveryWriteReceiptGuardTest test`、`mvn -q -DskipTests package` 与 `git diff --check` 均通过。定向用例覆盖缺少 `runtime.record.transfer` 时的明确失败提示，以及 transfer 成功必须拥有真实回读收据。
- 本地开发环境：backend 从 AgentCiCi 本地 `main@d2047edfa615` 构建为 `2.8.61-dev.d2047ed`；backend 与 Semattice 均 healthy、restart=0，完整 `cc-local-stack ./stack verify` 通过。
- 验收边界：浏览器控制端没有可用已登录会话，未重发最终确认，也未修改任务 owner。若 scope 未同步，系统会明确失败关闭；scope 同步后须由用户重新发送精确确认，成功结果必须显示 Semattice owner/revision 回读。UAT/生产未修改。

## 2026-08-14 TASK-307 产品经理按名称自动识别并确认式转派

- 后端定向：`mvn -q -Dtest=DevAutopilotDeveloperAssignmentServiceTest,SematticeProjectDeliveryTransferToolServiceTest test` 通过；`mvn -q -DskipTests package` 通过。
- 提供方验证：Semattice `go test ./...` 通过，受治理 transfer 保持 owner-only 更新、revision/audit/idempotency，并保留任务所属数据组织。
- 真实登录会话：输入“把鲁班的任务都转交给哪吒”后，界面自动识别 Developer Profile，返回鲁班→哪吒、1 项待开始任务及精确确认口令；未发送确认口令，未产生 owner 写入。
- 本地开发环境：AgentCiCi backend `2.8.61-dev.9cce47c`、Semattice `1.0.3-dev.81685db`，均 healthy/restart=0；`cc-local-stack ./stack verify` 通过。
- 验收边界：当前未由组织管理员为产品经理 SERVICE 同步 `runtime.record.transfer`，因此真实转派尚未执行；开发者 SERVICE 不可获得该 scope。UAT/生产未修改。

## 2026-08-14 TASK-306 产品经理受治理删除闭环

- 授权同步入口：组织管理员在“机器主体”明确确认“同步交付授权”两次，均返回“研发交付授权模板已同步”。服务端只重新应用当前团队的固定授权模板，未创建成员、轮换密钥或改动业务记录。
- 回收站实证：产品经理 SERVICE 已逐条删除 5 个已确认 ID，所有返回均包含原 ID、`revision=2`、回收站语义与独立关联号；未在任一失败后继续后续写入。
- 重建实证：`REQ-6F34ECF3` 已仅创建 `019ffeb0-88a0-739f-afcb-6e667e9d2572`，P2、鲁班、队列第 1 项、待开始、实际消耗 0.0h；任务详情显示功能验证与实现设计、代码开发、本地测试与开发环境验证、发布 UAT、UAT 验证、用户验收六阶段。
- 代码验证：后端定向 5 个测试类通过；管理端定向 2 个测试文件/5 项和 production build 通过，只有既有 chunk-size warning。backend package 已在本地镜像构建中通过。
- 本地开发环境：backend `2.8.61-dev.23ec0a6`、frontend `2.8.61-dev.aafbdbb`，均 healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过。UAT/生产未修改。

## 2026-08-14 TASK-302 受信应用运营界面可读性修整

- 前端实现：目录表格使用专属固定列宽及最右独立动作列，`编辑` 和 `启用/停用` 在桌面端不再被裁切；新增与编辑 modal 统一字段和 Scope 选项高度，表单主输入为 16px、标签和说明为 14px，原有保存、停用、Scope 和审计逻辑未改。
- 定向验证：`npm test -- --run src/platform/pages/PlatformSystemApisPage.test.ts`，10 项通过。
- 前端回归：`npm test -- --run`，49 个测试文件、275 项通过；`npm run build` 通过，仅保留既有大 chunk warning；`git diff --check` 通过。
- 本地开发环境：`cici-frontend` 从 AgentCiCi 本地 `main@8522fefb52a2` 构建为 `2.8.61-dev.8522fef`，镜像 revision 对齐，healthy/restart=0；`https://cici.localhost/platform/system-apis/applications` 返回 200，`cc-local-stack ./stack verify` 通过。
- 授权态视觉边界：可控浏览器只有运营平台登录页，未通过任何账号、Token 或伪造会话访问受信应用列表；真实保存行为已由用户截图确认，后续可由平台管理员在 UAT 继续复核。
- UAT 发布：不可变候选 `2.8.61-beta.22 / 8522fefb52a2` 已发布，backend/frontend ACR index digest 为 `sha256:29e7449a0c88ff50ad17fb759091cc9b98a0cd95193fde8ebc59f6073337b145` / `sha256:96fb8e0040837e0067ae5fe57fb7b88167a0987ea814683bf52c3bc046915fe2`。发布前备份 `/data/apps/agentcici/backups/20260814T050143Z-before-2.8.61-beta.22` 已校验；只重建应用容器，运行版本、health、Flyway V115、Nginx、公开 smoke、页面路由 200、匿名 API JSON 401 和稳定窗口通过。真实独立 Client/HUMAN 调用和受权视觉验收待完成。

## 2026-08-14 TASK-306 产品经理删除 scope 角色隔离

- 后端定向：`ServicePrincipalServiceTest`、`SematticeProjectDeliveryDeleteToolServiceTest`、`ToolOrchestratorServiceTest`、`DeliveryWriteReceiptGuardTest` 通过；production package 与 `git diff --check` 通过。
- 角色门禁：DevAutopilot `product_manager` SERVICE 可选择 `runtime.record.delete`；developer SERVICE 请求该 scope 时服务端拒绝。
- 本地运行：backend 为 `2.8.61-dev.26809b8 / 26809b8a07b7`，包含功能提交 `96c97bbc`，healthy、restart=0；完整 `./stack verify` 通过。
- 受权页面：大乔PM候选包含 delete，哪吒候选不包含 delete；未勾选或提交权限变更。
- 验收边界：人工显式授权、5 条旧任务回收站删除和单任务重建尚未执行；UAT/生产未修改。

## 2026-08-14 TASK-302 受信应用保存时间映射修复

- 根因证据：UAT `2.8.61-beta.21` 只读日志连续三次记录 `conversion to class java.time.Instant from timestamptz not supported`，异常位于 `EcosystemApplicationTrustService.mapRow`，发生在 `upsert` 写入后的 `requireByAppCode` 回读阶段；事务回滚与页面 500 一致。
- 后端定向：`mvn -Dtest=EcosystemApplicationTrustServiceTest test`，4 项通过；新增用例覆盖受信应用保存后以 `Timestamp` 回读 `created_at` / `updated_at` 并转换为 `Instant`。
- 生产包：`mvn -DskipTests package` 通过；`git diff --check` 通过。
- 本地主线与运行：功能提交 `d9f7bc009aab` 已进入本地 `main`；因随后合入独立鉴权提交，backend 最终从当时最新 `main@26809b8a07b7` 构建并运行 `2.8.61-dev.26809b8`，其中包含本修复。镜像 revision 与运行版本一致，healthy/restart=0，匿名受信应用接口返回 `401 application/json`，启动后未出现同类时间转换异常。其他状态服务和产品未重建。
- 完整本地技术门禁：`cc-local-stack ./stack verify` 通过域名扫描、数据库隔离、TLS、OIDC、应用健康/版本和匿名授权边界。全仓 `validate-state.py .claw` 仍因既有历史规格 front matter/status、旧时间格式、终态任务仍在 Active Tasks 与归档数量债务返回 1；输出未报告 TASK-302、FEAT-183 或本轮三个状态文件的新错误，本轮未越界清理历史治理债务。
- 验收边界：当前无可复用的平台管理员登录态，授权态页面真实保存待运营人员复测；UAT 仅做只读诊断，未发生发布或配置变更。
- 状态：`passed_with_authorized_visual_pending`。

## 2026-08-14 TASK-302 受信应用代码校验反馈

- 交互：`ccsales web` 即时提示“应用代码不能包含空格，请使用连字符（-）分隔，例如 ccsales-web”；长度、首字符和非法字符分别返回具体原因，保存门禁复用同一校验结果。
- 可访问性：错误输入设置 `aria-invalid=true`，通过 `aria-describedby` 关联内联帮助，并以 `role=alert` 暴露动态错误。
- 前端：定向 `PlatformSystemApisPage.test.ts` 1 文件/10 项、全量 49 文件/275 项、`npm run build` 和 `git diff --check` 通过；构建仅保留既有 chunk-size warning。
- 本地主线与运行：功能提交 `2daa18ef4df8` 已进入本地 `main`；`cici-frontend` 从该提交构建为 `2.8.61-dev.2daa18e`，容器 healthy/restart=0，目标路由 200，部署 JS 含新增提示。
- 完整门禁：`./stack verify` 通过域名、共享数据库隔离、TLS、OIDC、应用健康/版本和匿名鉴权边界。浏览器实际访问受保护路由后进入平台登录页，未绕过登录，因此授权态视觉交互待运营人员验收；UAT/生产未修改。
- 状态：`passed_with_authorized_visual_pending`。

## 2026-08-14 TASK-303 / TASK-304 / TASK-305 UAT `2.8.61-beta.21`

- Git/制品：本地与远程 `main` 同步于 `626f7e22c774`，annotated tag `2.8.61-beta.21` 指向该提交。backend/frontend linux/amd64 ACR index digest 为 `sha256:ab37b2621ce9800070bf05d3307ba531b46363a0d94a32b69539b1d15731b8d4` / `sha256:c9e24c55c92b8ede42d96c6c3c839d11a62a3cdfde9d50380c7f6575525fc291`；未更新 `latest`。
- 备份：`/data/apps/agentcici/backups/20260814T021238Z-before-2.8.61-beta.21` 包含 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.20 旧镜像、发布前状态、回滚说明和 SHA 清单；工件均非空、`0600` 且数据库/tar/gzip/SHA 读取校验通过。
- 部署：仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 保持 `d14ef...`、`db094...`、`4166...`、`26aec...`，六容器均 healthy/restart=0。
- 运行门禁：`/system/version=2.8.61-beta.21 / 626f7e22c774`，backend health=`UP`，Flyway 最新五条 V111-V115 均成功，frontend Nginx 配置有效。
- 外部与稳定性：六项公网只读 smoke 通过；`/platform/integrations`=200，匿名 `/api/platform/integrations`=`401 application/json`。部署 JS 包含“最长 60 分钟”和 `3600000`；稳定窗口 backend severe error=0、按 Nginx status 字段统计 frontend HTTP 5xx=0。
- 状态：`passed_with_uat_business_acceptance_pending`。未配置或调用真实长达 60 分钟的厂商任务；即时应用回滚目标为 beta.20，生产未修改。

## 2026-08-14 TASK-303 / TASK-304 DevAutopilot 委托产品经理执行闭环

- `mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest test`、`mvn -q -Dtest=DevAutopilotExecutionAuthorizationServiceTest test` 与 `mvn -q -DskipTests package` 通过。
- 真实 `REQ-6F34ECF3` 完成需求确认和 5 项任务创建；Semattice 审计 actor 全部为 primary 产品经理 SERVICE，任务 owner 全部为 active 开发者鲁班。
- backend 运行 `2.8.61-dev.53715a3`、healthy/restart=0；完整 `./stack verify` 通过。
- 状态：`passed_local_end_to_end`；UAT/生产未修改。

## 2026-08-14 TASK-305 平台长任务集成超时上限

- 契约：代码解释器、联网搜索与网页抓取默认超时仍为 120 秒、最小 10 秒，可配置上限统一从 180 秒提升为 `3,600,000 ms`（60 分钟）；既有配置不自动变更。
- 后端：`mvn -q -Dtest=ManagedWebToolServiceTest,SandboxCodeInterpreterServiceTest test` 通过，覆盖 `3,600,000` 接受和 `3,600,001` 拒绝；`mvn -q -DskipTests package` 通过。
- 前端：`AdminIntegrationsPage.test.ts` 5/5 通过；`npm run build` 通过，仅保留既有 chunk-size warning。页面数字输入 `min=10000`、`max=3600000`，提示明确最长 60 分钟。
- 命令更正：仓库无 `backend/mvnw`，首次 wrapper 命令退出 127 且未运行测试；随后按项目实际入口使用系统 Maven，结果通过。
- 本地主线与制品：功能提交 `4f7aca02bf85` 已进入本地 main；backend/frontend 运行 `2.8.61-dev.4f7aca0`，版本 API、容器环境与资源文件名一致，部署 JS 回读“允许 10000–3600000（最长 60 分钟）”。当前 main 后续 `18f28b04` 仅含其他任务验收文档。
- 运行态：backend/frontend healthy/restart=0。Compose 依赖图也重新创建 Semattice 与 DevAutopilot 应用容器；共享 PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak 和边缘 Nginx 未重建，完整 `./stack verify` 通过。
- 状态：`passed`。本地技术闭环完成；未配置或调用真实长达 60 分钟的厂商任务，UAT/生产未修改。

## 2026-08-14 TASK-302 UAT `2.8.61-beta.20`

- Git/制品：远程 `main` 已同步；annotated tag `2.8.61-beta.20` 指向 `1b6bb8f1974a`。backend/frontend linux/amd64 ACR index digest 为 `sha256:18c1e7c3c082ad475e3a4b714b96e3f3e385d08deaa6384ec5c944ba0143eb56` / `sha256:48520c667024f7d9e94f9d696c37eb089e0cca115c8a87d7b5f72df4a0180c56`，镜像 label 与运行 `/system/version` 一致；未更新 `latest`。
- 备份：`/data/apps/agentcici/backups/20260814T002542Z-before-2.8.61-beta.20` 的 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.19 旧镜像、发布前状态和 SHA 清单均非空且为 `0600`；数据库归档、两个 tar、旧镜像 gzip 和 SHA 清单均验证通过。
- 部署：仅 force-recreate backend/frontend，24 秒内恢复 healthy；四个状态服务 ID 哈希保持 `be954223201a867fbef7aff12e97786a136b9ab1e8011c5ab1a0ed968cd3477f`，六容器 healthy/restart=0。
- 运行门禁：backend health=`UP`、Flyway V115 成功、受信应用表存在、frontend Nginx 有效且部署制品包含生态公司入口。系统 API/接入应用路由 200，匿名平台 API、公司列表和正确的公司上下文 POST 为 JSON 401，错误 GET 为 JSON 405。
- 外部与稳定性：UAT 首页、匿名 auth、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 两轮通过；稳定窗口 backend severe error=0、frontend 5xx=0。
- 状态：`passed_with_uat_business_acceptance_pending`。没有可用于验收的新独立 Keycloak Client 与 HUMAN 用户凭据，未执行真实成功登录、公司列表/上下文及后续 `X-Company-Id` 业务调用；即时应用回滚目标为 beta.19，生产未修改。

## 2026-08-14 TASK-302 Keycloak HUMAN 跨应用直调

- 契约：已登记内部应用直接携带 Keycloak `access_token` 调用 `/openapi/v1/ecosystem/companies` 与 `/openapi/v1/ecosystem/company-context`；不再建设应用专用 handoff 或第二套长期 HUMAN Token。公司级后续请求继续使用同一 Token 与 `X-Company-Id`。
- 安全边界：验证 RS256、Issuer、有效期、`typ=Bearer`、`aud=agentcici-api`、`azp`；`azp` 必须命中 ACTIVE 受信 Client 和接口 Scope，`(issuer, sub)` 必须映射 ACTIVE HUMAN 账号，公司及成员关系逐请求校验。未知 Client、停用、Scope 缺失、错误 Audience 和非成员均失败关闭。
- 平台治理：V115 新增受信内部应用表；平台管理员可以通过独立“接入应用”列表与编辑弹窗登记 Client ID、Scope 和状态，不保存 Client Secret，变更写入平台审计。
- 后端：`mvn -q -Dtest=KeycloakOidcLoginServiceTest,EcosystemHumanApiServiceTest,EcosystemApplicationTrustServiceTest,SystemApiCatalogServiceTest test` 共 13 项通过；`mvn -q -DskipTests package` 通过。
- 前端：全量 49 文件/272 项通过；`npm run build` 通过，仅保留既有 chunk-size warning。
- 状态：本地提交、V115 与 `cici.localhost` 技术验收均已完成；后续 UAT 证据见 `2.8.61-beta.20` 条目。

## 2026-08-13 TASK-302 UAT `2.8.61-beta.19`

- Git/制品：远程 `main` 与本地同步；annotated tag `2.8.61-beta.19` 指向 `2343b9bbafd6`。backend/frontend linux/amd64 ACR index digest 为 `sha256:36f9591b78b9f2c22f2dd5c435f0e2d1dbd693978c195dbe6f241c958184bda7` / `sha256:0958cbe7b5614c16548895c233afa828545c1be6775bfd160aefbb0bfb4de0a7`，镜像 label 与运行 `/system/version` 一致；未更新 `latest`。
- 备份：`/data/apps/agentcici/backups/20260813T153050Z-before-2.8.61-beta.19` 的 Compose、受管环境、PostgreSQL、KB、Qdrant、beta.18 旧镜像和校验清单均非空且为 `0600`；数据库归档、两个 tar 和旧镜像 gzip 均通过读取校验。
- 部署边界：只 force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 保持 `d14ef...`、`db094...`、`4166...`、`26aec...`，六容器均 healthy/restart=0。
- 运行门禁：backend health=`UP`，Flyway 最新至 V114 且 success，frontend Nginx 配置有效；UAT 首页、匿名 auth、Keycloak discovery、Semattice health/version、DevAutopilot integrated health 均通过。
- 功能边界：`/platform/system-apis/agentcici` 返回 200，匿名 `/api/platform/system-apis` 返回 `401 application/json`；部署 JS 包含 Keycloak 原始 Token 不可直调、通用 HUMAN 交换端点未发布和新独立应用接入前置文案。
- 稳定窗口：backend severe error=0、frontend upstream error=0、HTTP 5xx=0；即时应用回滚目标为 `2.8.61-beta.18`。
- 状态：`passed_with_authorized_visual_pending`；未使用平台管理员会话执行授权态视觉/交互验收，生产未修改。

## 2026-08-13 TASK-302 HUMAN 鉴权与新应用接入说明

- 文案边界：明确 Keycloak 原始 `access_token` / `id_token` 不能直接调用公司 API；目录使用 `Bearer AgentCiCi Ecosystem HUMAN Token`，并说明账号、ACTIVE 成员关系、当前公司、audience/scope 等受控上下文。
- 现有调用链：文档给出 `/auth/oidc/login?return_to=${SAME_ORIGIN_RETURN_PATH}`、一次性回调票据和 `/auth/oidc/complete?ticket=${OIDC_COMPLETION_TICKET}` 的服务端兑换方式；公司切换成功后必须用响应中的新 HUMAN Token 替换旧 Token 并清理租户缓存。
- 新应用边界：同源扩展复用 AgentCiCi 服务端会话；新独立应用必须先登记 Keycloak Client、平台应用激活/信任并建设应用专用 handoff 或受治理交换契约；当前不公布不存在的通用 HUMAN Token 交换地址。机器应用使用 SERVICE/OACT，不得调用 HUMAN 公司 API。
- 后端：`mvn -q -Dtest=SystemApiCatalogServiceTest test` 与 `mvn -q -DskipTests package` 通过。
- 前端：定向 1 文件/8 项、全量 49 文件/272 项、`npm run build` 和 `git diff --check` 通过；环境域名门禁通过。
- 本地开发环境：功能提交 `99ae151b5ce0` 已进入本地 `main`；backend/frontend 为 `2.8.61-dev.99ae151`、revision=`99ae151b5ce0`、healthy/restart=0。目标深链返回 200，匿名目录 API 返回 `401 application/json`，完整 `./stack verify` 通过。
- 浏览器：受保护深链正确跳转运营平台登录页；当前无可复用登录态，未绕过认证，授权态抽屉与完整文档视觉验收待运营人员完成。
- 状态：`passed_with_authorized_visual_pending`；UAT/生产未修改。

## 2026-08-13 TASK-302 公司上下文系统 API 公布

- 实现：AgentCiCi 目录从 6 项增至 8 项，新增 `agentcici.organization.list` 与 `agentcici.organization.switch`；对应现有 `GET /auth/companies`、`POST /auth/switch-company`，未改变 Controller、鉴权或切换逻辑。
- 契约：公司列表从已验证 HUMAN token 推导账号和当前公司；切换必须重新校验目标公司的 ACTIVE 成员关系，成功后返回新的公司上下文令牌，调用方需替换旧令牌并清理租户缓存。
- 后端：`mvn -q -Dtest=SystemApiCatalogServiceTest test` 与 `mvn -q -DskipTests package` 通过。
- 前端：目录定向 1 文件/7 项、全量 49 文件/271 项与 production build 通过；请求说明会按 HUMAN、SERVICE、OACT、HMAC 分别显示鉴权示例。
- 门禁：首个 API ID `agentcici.company.*` 在纯文本上命中 `agentcici.com` 域名扫描，已改为 `agentcici.organization.*`；未放宽扫描规则，`check-no-environment-domains.sh` 与完整 `./stack verify` 均通过。
- 本地开发环境：backend/frontend 从本地 `main@6444bbcf` 构建为 `2.8.61-dev.6444bbc`，镜像 revision 均为 `6444bbcfd256`、healthy/restart=0；页面 200、匿名目录 API `401 application/json`，运行 backend 制品回读包含两个新 ID、路径与 HUMAN 令牌类型。
- 状态：`passed_with_authorized_visual_pending`；UAT/生产未修改。

## 2026-08-13 TASK-302 系统 API 目录加载修复

- 根因复现：`GET /platform/system-apis` 返回 `200 text/html` SPA 页面；真正的浏览器 API `GET /api/platform/system-apis` 匿名返回 `401 application/json`。
- 代码：提交 `b5d189a1` 使用统一 `PLATFORM_API_BASE`，显式 `Accept: application/json`，并把 HTML/非 JSON 回应转换为可操作的版本一致性提示。
- 前端：定向 1 文件/5 项、全量 49 文件/269 项通过；`npm run build` 与 `git diff --check` 通过，仅保留既有 chunk-size warning。
- 本地开发环境：backend/frontend 从本地 `main@b5d189a1` 构建为 `2.8.61-dev.b5d189a`，均 healthy/restart=0；目标页面 200、匿名目录 API 为 `401 application/json`，完整 `./stack verify` 通过。
- 启动说明：首次全栈启动被无关 DevAutopilot 容器一次性退出 143 中断；该容器随后自行恢复为 healthy/restart=0，启动前端后完整门禁通过，未修改或提交 DevAutopilot 源码。
- 状态：`passed_with_authorized_visual_pending`；UAT/生产未修改。

## 2026-08-13 TASK-301 知识库 PDF 上传门禁修复

- 413 根因：最外层本地 Nginx 日志回读 `client intended to send too large body: 2199033 bytes`；AgentCiCi 产品 Nginx 100 MB、Spring 256 MB 和知识库 25 MB 均未获得处理机会。
- 边缘修复：`cc-local-stack@5c1f8a7` 在 AgentCiCi TLS server 设置 100 MB，并将有效配置回读加入 `./stack verify`。2 MiB multipart 探针经 `cici.localhost` 返回 AgentCiCi JSON 401 而非 413；完整 verify 通过。
- 错误体验：AgentCiCi `5fa2ee3` 对 413 显示“确认不超过 25 MB；符合限制时检查网关上限”的中文处置建议；错误框使用完整 2px 深红边、浅红底、深红标题/正文和 `role=alert`。部署 CSS 已回读 `cici-inline-feedback--danger` 与对应 OKLCH 深红色。
- 前端验证：上传策略与反馈测试 2 文件/8 项通过；production build 与 diff check 通过。
- 真实业务文件：`AgentCiCi企业级智能体生产与协作平台-产品介绍v5.pdf` 为 2,198,684 字节，经 `cici.localhost` 正式 upload/publish 均 HTTP 200，最终 PUBLISHED、25 个切片并保留在知识库。
- 本地运行：前端 `2.8.61-dev.ea1f6ab` 包含 `5fa2ee3` 修复，frontend/edge/backend 均 healthy、restart=0，目标路由 200；当前 `main@3548a92` 比制品仅多 TASK-302 文档记录。UAT/生产未修改。
- 复开根因：允许 PDF 后，页面仍只依赖 3 秒 toast；上传、发布、响应解析和异步索引缺少完整异常捕获及稳定最终态。用户选择的 PDF 未产生上传请求时，页面会表现为“无任何反馈”。
- 反馈修复：提交 `d3cf1b7` 增加持久上传状态和安全响应解析，覆盖文件检查、上传、提交索引、解析/索引、成功与失败；处理中禁用文件选择，成功仅在目标文档实际 `PUBLISHED` 后显示并包含切片数。
- 前端验证：`AdminKnowledgeUploadPolicy.test.ts` 与 `AdminKnowledgeUploadFlow.test.ts` 共 2 文件/7 项通过；`npm run build` 与 `git diff --check` 通过，仅保留既有 chunk-size warning。
- 本地制品：仅重建 `cici-frontend`，版本 `2.8.61-dev.d3cf1b7`，镜像 `sha256:5cad00e13f2374c502f3f7b8938ce883bb6fb23dff639c88a732ff48d5606128`；frontend/backend 均 healthy、restart=0，`https://cici.localhost/admin/kb`=200，部署 JS/CSS 文件名包含相同版本。
- 已登录 Chrome 业务验收：真实选择 `TASK-301-upload-feedback.pdf` 后捕获“处理中…”和“正在解析并建立索引”，最终页面持续显示“文档上传成功……已生成 1 个切片并可用于检索”，表格状态“可用”；数据库独立回读 `application/pdf / PUBLISHED / 1`。测试文档经正式 DELETE API 清理，刷新后知识库恢复原有 1 个有效文档。
- 根因：管理端在扩展名为 `pdf` 时把后端 `pdfPolicy` 能力说明作为 toast 并直接返回，未发起 `/kb/documents/upload`；后端已实现文本型 PDFBox 解析和明确失败路径。
- 前端：新增统一上传预检，PDF 与其他允许扩展名一并放行；超限和不支持扩展名仍失败关闭。`npm test -- --run src/admin/pages/AdminKnowledgeUploadPolicy.test.ts` 为 1 文件/3 项通过，`npm run build` 通过，仅保留既有 chunk-size warning。
- 后端：上传策略改为中文文本型 PDF 说明；`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 集成边界：共享测试库在断言前被既有 Flyway V81 checksum 漂移阻断，未 repair。隔离 PostgreSQL 16 已从 V1 成功迁移至 V114，但现有 `shouldExposeUploadPolicyAndIndexTextPdf` fixture 未建立当前 `knowledge-embedding` 所需的平台可用模型，创建知识库时失败，未进入 PDF 断言；不声明该用例通过。
- 本地主线与制品：修复提交 `cabaebc7c641` 已进入本地 `main`；backend/frontend 从该提交构建为 `2.8.61-dev.cabaebc`。后端 `/system/version` 回读 `cabaebc7c641`，前端 JS/CSS 文件名回读相同版本。
- 运行资源：只替换 AgentCiCi backend/frontend；镜像分别为 `sha256:909f232deef8868f3c089cc8546d2cc88c4fe96f2411048ab0180c6e92d81e7d`、`sha256:597e6702fb6312775da922675f4bc9ec0e472467a18051e3da87403ad1048ef0`，两个容器均 healthy/restart=0，`https://cici.localhost/admin/kb` 路由返回 200。
- 真实 PDF 链路：生成并渲染检查 1 页文本型 PDF，正式 `/kb/documents/upload` 返回 UPLOADED，正式 publish 进入 MQ 后最终 PUBLISHED、1 个切片、无解析错误。随后通过正式 delete API 清理，返回 `cleanupStatus=COMPLETED`、`deletedChunks=1`，知识库有效文档/有效切片恢复 0/0；临时 PDF 与 PNG 已删除。
- 身份边界：自动化浏览器没有已登录 HUMAN 会话，访问 `/admin/kb` 正确进入 SSO；验收使用本地短时受控生态 HUMAN token 调用与页面相同的 API，未输出或持久化 Secret，未伪造已登录页面点击证据。
- 状态：`passed`（代码、制品、运行和真实文本型 PDF 上传/发布/清理通过）；UAT/生产未修改。

## 2026-08-13 TASK-298 模型能力确认非 JSON 响应修复

- 根因：截图中的 `Unexpected token '<'` 表明网关或未同步后端返回了 HTML，页面直接执行 `res.json()`，将协议异常暴露给平台运营人员。
- 修复：确认和撤销调用显式声明 `Accept: application/json`，使用共享安全解析器处理响应；非 JSON 回应显示 HTTP 状态与“刷新并确认前后端版本一致”的处置提示，不泄露 HTML 内容。
- 前端：`npm test -- --run src/platform/pages/PlatformModelsPage.test.tsx` 为 1 文件/6 项通过，覆盖 HTML 405 错误文案；`npm run build` 通过，仅保留既有 chunk-size warning；`git diff --check` 通过。
- 本地开发环境：仅重建 `cici-frontend`，版本 `2.8.61-dev.4da7a3b`；backend 为 `2.8.61-dev.b8bf4d3`。两个目标容器均为 `healthy/restart=0`，`/platform/models`=200，能力确认 API 匿名请求回读 `401 application/json`，完整 `./stack verify` 通过。
- 状态：`passed_with_authorized_business_acceptance_pending`；UAT/生产未修改。

## 2026-08-13 TASK-298 人工确认简化与目录先选模型

- 代码：提交 `1979c62` 将确认请求收敛为模型与能力，移除文档 URL、证据引用及其校验/展示；全部模型目录允许直接选择，场景候选仍只接受已确认且能力/协议兼容的模型。
- 后端：`mvn -q -DskipTests compile`、`mvn -q -DskipTests package` 和 `git diff --check` 通过。
- 前端：`npm test -- --run src/platform/pages/PlatformModelsPage.test.tsx` 为 1 文件/5 项通过；`npm run build` 通过，仅保留既有 chunk-size warning。
- 集成边界：`ModelProviderServiceIntegrationTest,PlatformModelProviderIntegrationTest` 在执行断言前被共享 `agentcici_test` 的 Flyway V81 checksum 漂移阻断（已应用 `2112500543`，本地解析 `379982424`）。未执行 repair、未修改历史迁移；本轮用例已完成编译。
- 本地开发环境：backend/frontend 从本地 `main@1979c6291dbf` 构建为 `2.8.61-dev.1979c62`；目标容器均为 `healthy/restart=0`，镜像 revision/version 一致。`/platform/models`=200，匿名 `/api/platform/models/providers`=401，部署前端制品包含“可直接加入平台目录，能力在后续确认”，完整 `./stack verify` 通过。
- 状态：`passed_with_integration_environment_blocker`；UAT/生产未修改，受权业务交互待验收。

## 2026-08-13 TASK-298 场景模型能力过滤与推荐说明

- 后端：`mvn -q -DskipTests package` 通过；`git diff --check` 通过。
- 前端：`npm test -- --run src/platform/pages/PlatformModelsPage.test.tsx` 为 1 文件/4 项通过；`npm run build` 通过，仅保留既有 chunk-size warning。
- 集成边界：`ModelProviderServiceIntegrationTest,PlatformModelProviderIntegrationTest` 在执行断言前被共享 `agentcici_test` 的 Flyway V81 checksum 漂移阻断（已应用 `2112500543`，本地解析 `379982424`）。未执行 repair、未修改历史迁移；本任务新增的可信能力过滤用例已编译，但待测试库基线恢复后执行。
- 覆盖：模型能力只接受远程目录或受控检测持久化结果；场景读取仅返回能力/协议兼容候选，路由写入及运行时二次拒绝未知或不兼容模型；路由页显示必需能力、推荐原则、候选数量与空状态引导。
- 本地开发环境：backend/frontend 已从本地 `main@1df52acc8860` 构建为 `2.8.61-dev.1df52ac`；两个目标容器 `healthy/restart=0`，镜像 label、容器环境和部署 JS/CSS 指纹均回读同一版本/提交。完整 `./stack verify` 通过；匿名桌面深链进入平台登录边界，console 0 error/warn。
- 状态：`passed_with_integration_environment_blocker`（静态、package、前端测试、生产构建和本地栈部署通过；Spring 集成被既有测试库 Flyway 基线阻断）。UAT/生产未修改。

## 2026-08-13 TASK-297 统一模型调用治理本地开发环境发布

- 制品来源：backend/frontend 均由本地 `main@7be07dd2a4d8` 构建，OCI revision=`7be07dd2a4d8`、version=`2.8.61-dev.7be07dd`。
- 发布范围：仅强制重建 AgentCiCi `backend` 与 `cici-frontend`；Semattice、DevAutopilot、Keycloak、PostgreSQL、Redis、RabbitMQ、Qdrant 与 Nginx 未重建。
- 运行回读：两个目标容器均为 `healthy`、restart=0；`https://cici.localhost/` 返回包含 `2.8.61-dev.7be07dd` 的 JS/CSS 制品指纹。
- 全栈技术验收：`./stack verify` 通过，覆盖部署域名门禁、共享数据库隔离、TLS、OIDC discovery、应用健康/版本与匿名授权边界。
- 边界：本地开发环境已更新；未发布 UAT 或生产，真实厂商凭据及场景业务验收仍待在 UAT 按发布流程完成。
- 状态：`passed`；UAT 业务验收未进行。

## 2026-08-13 TASK-296 DevAutopilot 历史受理字段纠正

- 初次受控纠正通过平台维护弹窗写入 revision 2，但独立回读发现 Markdown `---` 被兜底解析为 `--` 第六条验收，分类理由也混入了产品经理分析；未把接口自报成功当作最终验收。
- 最终解析规则将 `classification_reason` 与 `pm_assessment` 分离，并统一拒绝不含 Unicode 字母或数字的数组项；真实故障同构测试包含 Markdown 分隔线。
- AgentCiCi：`SematticeProjectDeliveryWriteToolServiceTest,DevAutopilotIntakeReconciliationServiceTest,DeliveryWriteReceiptGuardTest,ChatOrchestratorServiceModelIdentityTest` 通过；`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 正式纠正：平台返回 revision 3 / digest `04b27d83078e`；Semattice 只读回读确认分类理由、4 条分析、5 条验收、4 条开发者验证项与确认前草稿一致，`--` 不存在。
- 幂等与审计：第二次相同校准返回“已核验一致”，数据库 revision 保持 3；`platform_audit_log` 保存会话、原确认人、revision 3 和完整 64 位摘要。
- DevAutopilot：详情抽屉新增独立“分类理由”，37/37 Node 测试、JS 语法和 diff check 通过；部署脚本回读包含 `intake.classification_reason`。
- 本地环境：AgentCiCi `2.8.61-dev.78ebeae`、DevAutopilot `1.0.4-dev.32e95a9`；相关容器 healthy/restart=0，两次完整 `./stack verify` 通过域名门禁、数据库隔离、TLS、OIDC、应用健康/版本和匿名授权边界。
- 边界：DevAutopilot 直达 URL 无租户 OACT 时按设计拒绝读取；未用平台管理员冒充租户业务成员。UAT/生产未修改。
- 状态：`passed`。

## 2026-08-12 TASK-294 DevAutopilot 受理草稿字段保真

- 根因证据：本地 Semattice 记录 `REQ-6F34ECF3` 的标题与原始报告正确，但摘要、验收标准和开发者验证项已在 AgentCiCi 调用前退化为通用占位内容；Semattice 只是按输入持久化。
- 代码：可见草稿兜底解析新增产品经理分析、需求验收标准、变更影响、缺陷复现/预期/实际结果及开发者验证表格提取；只有可见草稿没有具体事实时才使用通用默认值。
- 同构回归：使用截图中的原始需求草稿，精确断言 4 条分析、5 条验收标准和 4 条开发者验证项进入原生字段及 `intake` 审计信封。
- 测试：`SematticeProjectDeliveryWriteToolServiceTest,DeliveryWriteReceiptGuardTest,ChatOrchestratorServiceModelIdentityTest` 通过；`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- 本地主线与制品：功能提交 `f7798d1` 合并为 `main@87fae991dbb7`；backend 镜像 label、运行环境和 `/system/version` 均回读 `2.8.62-dev.87fae99 / 87fae991dbb7`，容器 healthy/restart=0。
- 运行门禁：完整 `./stack verify` 通过域名源码扫描、基础设施/数据库隔离、TLS、OIDC、应用健康/版本与匿名鉴权；Semattice 等状态服务未重建且全部 restart=0。
- 边界：没有改写既有错误记录 `REQ-6F34ECF3`，避免绕过正式能力破坏审计；平台管理员会话不能替代租户产品经理真实对话。
- 状态：`passed_with_authorized_business_acceptance_pending`（代码、构建、运行和故障同构字段断言通过；真实租户新记录回读待业务用户完成）。

## 2026-08-12 TASK-293 DevAutopilot 授权初始化

- 定向测试：`DevAutopilotTenantApplicationReadinessTest`、`SematticeDevAutopilotAuthorizationClientTest` 通过；后端 `mvn -q -DskipTests package` 通过。
- 本地主线与制品：功能提交 `38f8598` 合并为 `main@41740bdd55e6`；backend/frontend 镜像及页面版本均为 `2.8.62-dev.41740bd`，两个容器 healthy、restart=0。
- 迁移与栈验证：Flyway V111 成功；`./stack verify` 通过基础设施、数据库隔离、TLS、OIDC、健康/版本和匿名鉴权边界。
- 正式补齐：平台管理员在租户页执行 `initializations` 成功，卡片由“待补齐”变为“已完成”；activation 回读 `devautopilot.authorization.v1`、digest 长度 64、role/set/assignment=4/4/4、verified_at 非空。
- 幂等：再次执行“同步标准模板”成功，页面保持“已完成”，Semattice 固定资源和 4 个有效分配未重复；未直接写数据库或绕过平台授权。
- 状态：`passed`（本地开发环境代码、部署、正式业务补偿和跨库回读通过；UAT/生产未修改）。

## 2026-08-12 TASK-292 平台联网搜索与网页抓取集成

- UAT 发布：远端 `main@9bf64d8`、tag `2.8.61-beta.17` 与两个 ACR linux/amd64 镜像的 version/revision label 一致；运行 `/system/version` 回读同一版本和提交。
- UAT 运行：backend/frontend healthy/restart=0，health=`UP`，Flyway 最近 5 条记录均成功，Nginx 配置有效；database、Redis、RabbitMQ、Qdrant 的容器 ID 与发布前一致且 restart=0。
- UAT 边界：五项公共 smoke 通过，`/platform/integrations`=200、匿名 `/api/platform/integrations`=401；beta.17 前端资源包含 `managed_web_search`、`managed_web_extractor`、代码解释器、联网搜索和网页抓取标记。
- 稳定窗口：修正 Nginx access log 字段解析后，backend severe error=0、frontend 5xx=0；首次统计的 3 条“5xx”为错误读取响应字节数字 `552`，原始状态码均为 200/401，已排除真实故障。
- 后端定向：`ManagedWebToolClientTest,ManagedWebToolServiceTest,SandboxCodeInterpreterClientTest,SandboxCodeInterpreterServiceTest,ToolOrchestratorServiceTest` 共 16 项通过；覆盖精确工具声明、reasoning 不投影、独立密钥加密/掩码、API Host 与抓取 URL 门禁、未配置失败关闭、目录和执行器。
- 后端构建：`mvn -q -DskipTests package` 通过；`git diff --check` 通过。
- Spring 集成边界：`PlatformIntegrationGovernanceIntegrationTest` 在应用启动前被共享测试库既有 Flyway V81 checksum 漂移阻断；未 repair、未修改历史迁移，本任务无数据库迁移。
- 前端：定向 2 文件/5 项通过；完整 46 文件/249 项通过；生产构建通过，仅有既有 chunk size warning。
- 安全：API Key 未进入仓库/日志/测试；API Host 仅接受 HTTPS `*.maas.aliyuncs.com`；抓取目标拒绝明显本地、私网、链路本地、组播、`.local`、非 80/443 端口与用户信息。
- 主线：功能提交 `9a8cb9a` 已合并，治理收尾后远端 `main` 固定发布提交为 `9bf64d8`；UAT tag 指向该提交。
- 本地环境：backend/frontend 均从 `main@1f362c7` 构建为 `2.8.62-dev.1f362c7`；镜像 label、容器环境和 `/system/version` 均回读 `1f362c7d86d8`，两容器 healthy/restart=0。
- 运行验证：`./stack verify` 通过；`https://cici.localhost/platform/integrations`=200，匿名 `/api/platform/integrations`=`401 application/json`；部署 JS 包含两条检测端点与当前版本。
- 浏览器：目标路由按预期进入平台登录边界，console 0 error/warning；当前无受权平台会话，未绕过登录。
- 业务边界：两项集成默认关闭且没有真实 API Key，真实百炼连接与 Agent 会话业务调用待平台管理员完成。
- 状态：`passed_with_vendor_business_acceptance_pending`

## 2026-08-12 TASK-291 平台代码解释器集成与内置工具

- 后端定向：`SandboxCodeInterpreterClientTest,SandboxCodeInterpreterServiceTest,ToolOrchestratorServiceTest` 共 10 项通过；覆盖 Responses 请求体只含 `code_interpreter`、reasoning 不投影、密钥加密/掩码、URL SSRF 门禁、未配置失败关闭和运行时目录/执行器。
- 后端构建：`mvn -q -DskipTests package` 通过，`git diff --check` 通过。
- Spring 集成边界：`PlatformIntegrationGovernanceIntegrationTest` 启动时被共享测试库既有 Flyway V81 checksum 漂移阻断；未执行 repair、未修改历史迁移，本功能没有数据库迁移。
- 前端定向：平台集成 2 文件/4 项通过；完整前端 46 文件/248 项通过。
- 前端构建：`npm run build` 通过，仅保留既有 chunk size warning。
- 安全边界：无真实 API Key 进入仓库、日志或测试输出；API Host 默认留空且只接受百炼按地域提供的 `*.maas.aliyuncs.com` HTTPS 业务空间地址。
- 主线与本地环境：功能提交 `0c58cfb` 已合并本地 `main@8f76e39`；backend/frontend 从该主线构建，版本 `2.8.62-dev.8f76e39`，两容器 healthy/restart=0，目标路由 200、匿名平台接口 `401 application/json`，镜像 label 和运行环境提交均为 `8f76e39abedf`。
- 浏览器：目标路由按预期进入平台登录边界，console 0 error/warning；当前无受权平台会话，未绕过登录。
- 业务边界：集成默认关闭且本地未配置真实 API Key，因此真实百炼连接与 Agent 会话业务调用待平台管理员完成。
- 状态：`passed_with_vendor_business_acceptance_pending`

## 2026-08-12 TASK-290 管理端新增成员公共编号回读修复

- UAT 诊断：实际运行 `2.8.61-beta.16 / aef334205280`，health=`UP` 且公共 smoke 全部通过；故障目标手机号、邮箱的 account/identifier/member/identity 计数均为 0，证明失败事务完整回滚。
- 根因：账号 insert 后的 Repository `findById` 命中 JPA 一级缓存，无法取得 PostgreSQL 触发器生成的 `public_id`；Keycloak provisioning 因此在任何远端写入前失败。
- 修复：`AdminUserService` 在 `saveAndFlush` 后显式 `EntityManager.refresh`，回归测试断言 provisioning 前公共编号已存在且不再调用同事务 `findById`。
- 测试：`AdminUserServiceTest,CompanyProvisioningServiceTest,KeycloakIdentityProvisioningServiceTest` 共 21 项通过；后端 `mvn -DskipTests package` 与 `git diff --check` 通过。
- 本地主线：提交 `ab1b02c` 已进入本地 main；从该提交构建 `2.8.62-dev.ab1b02c`，镜像 `sha256:3bd08dcfdc2eaf847d72e5b5228b668fdad17ee3660c9d9d2bb1c11f8de32009`。
- 本地运行：只重建 backend；health=`UP`、healthy/restart=0、edge 200、匿名 `/api/admin/users` 401、启动错误计数 0，其他服务 ID 保持不变。
- 边界：未发布 UAT/生产，未发送邀请邮件或创建测试成员；真实业务新增需在发布后使用受权管理员和专用测试邮箱验收。
- 状态：`passed_with_uat_business_acceptance_pending`

## 2026-08-12 TASK-289 平台集成卡片主线恢复

- 根因：`main@4459993` 仍请求 `/platform/integrations`，首次 TASK-288 平台修复只存在于隔离分支，后续 main 构建覆盖本地镜像后再次返回 SPA HTML。
- 代码：修复以 `0d55564` 进入本地 main；平台使用 `/api/platform/integrations`，非 JSON 不再静默空白。
- 测试：定向 2 文件/3 项、完整前端 46 文件/247 项通过；`npm run build` 通过，仅保留既有 chunk size warning。
- 本地：镜像/容器资源 `sha256:4293a3bbdbf3fa452603c009d3f767d245c523b9fc985ff4ce1768d44580f195`，版本 `2.8.62-dev.0d55564`；容器 healthy/restart=0，目标路由 200，部署 JS 包含正确 API 和错误状态，匿名 API 返回 `401 application/json`。
- 边界：当前证据证明代码、制品、路由和鉴权入口；受权页面卡片视觉由用户刷新后确认。UAT/生产未修改。
- 状态：`passed_with_authorized_visual_pending`

## 2026-08-12 TASK-288 产品经理可见缺陷草案确认恢复

- 根因证据：故障会话的草稿为“缺陷受理草稿”Markdown 表格且无 `DEV_AUTOPILOT_INTAKE_V1`；确认前后后端日志均为 `toolCount=0`，没有 Semattice 调用错误，证明失败发生在确认意图恢复阶段。
- 回归：使用故障同构原始描述和表格草稿，短确认恢复为 `create_defect`；父项目=`DAS-A2AFD106`、标题、P2、medium、待开发者验证、conversation ID 与逐字原始描述均断言通过。
- 编排：补充“缺陷/需求/变更受理草稿”的待处理上下文识别；无真实 `SEMATTICE_LIVE` record ID、revision、correlation 和 readback 时仍禁止宣称成功。
- 测试：`SematticeProjectDeliveryWriteToolServiceTest,DeliveryWriteReceiptGuardTest,ChatOrchestratorServiceModelIdentityTest` 通过；`mvn -q -DskipTests package`、`git diff --check` 通过。
- 本地运行：backend 修复镜像 healthy、restart=0，domain gate 与 stack verify 通过；浏览器登录因当前本地 Keycloak 不接受已知手机号测试凭据而停止，未重置账号或绕过身份门禁。
- 状态：`passed_with_business_acceptance_pending`

## 2026-08-11 TASK-287 技能治理 V5 正式 React 落地

- 定向测试：`npm test -- --run src/platform/PlatformShell.test.ts src/platform/pages/PlatformSkillsPage.test.ts` -> 2 文件 / 18 项通过。
- 完整前端：`npm test` -> 44 文件 / 244 项通过。
- 构建：`npm run build` -> TypeScript 与 Vite 生产构建通过；仅保留既有 chunk size warning。
- 静态检查：目标 `git diff --check` 通过；统一技能导航对 `/skills`、策略、详情、编辑、预览和策略编辑深链选中态均有测试覆盖。
- 本地运行：PostgreSQL/Redis/RabbitMQ/Qdrant 启动，backend local profile 启动并迁移本地 schema 到 V110；`/actuator/health={"status":"UP"}`，Vite 5173 返回 HTTP 200。
- 浏览器边界：匿名打开 `/platform/skills` 正确重定向 `/platform/login`。当前无受权平台会话，未输入或绕过凭据，因此正式页面桌面截图、console 和交互视觉 QA 仍待登录后复核；V5 原型既有 1280×720 设计 QA passed。

## 2026-08-11 TASK-285 核心策略包列表预设原型 V5

- 范围：保留 V4 全部技能治理结构与交互，只把核心策略包首页从单对象摘要调整为未来可扩展列表。
- 列表：展示“平台核心安全策略、数据出境策略、模型调用策略、工具执行策略”4 行；当前 1 个生效、3 个规划中，没有新建策略包或发布规划项的动作。
- 功能边界：生效策略“管理”进入 `#policy/edit`，策略名称、适用范围、策略说明、提示片段、人工移交规则及保存草稿动作完整；规划项“说明”只显示未启用反馈且保持 `#skills/policies`。
- 浏览器：`1280 × 720` CSS viewport，`clientWidth=scrollWidth=1265`；4 个列表行、3 个规划态；console 只有 Vite/React 开发信息，`0 error / 0 warning`。
- 构建：`npm run build` 通过；`npm run test:sites` 4/4 通过；作用域 `git diff --check` 通过。
- 治理校验：`validate-state.py .claw` 仍被仓库既有旧时间格式、历史规格状态/front matter、活动区已完成任务和归档数量债务阻断；输出没有 TASK-285/FEAT-172 新错误，本轮未跨范围清理历史状态。
- 视觉：`screenshots/v5-01-policy-package-list.jpg` 已检查；与 V4 单对象页组成 `screenshots/v5-policy-list-comparison.jpg` 同屏对比，无待处理 P0/P1/P2。
- 边界：只修改本地高保真原型及设计事实，不新增后端策略包/API/运行时逻辑，不修改正式 React 页面，不部署 UAT/生产。
- 状态：`passed_with_product_review_pending`

## 2026-08-11 TASK-285 技能治理合并原型 V4

- 范围：侧栏将“技能目录、策略与版本、依赖与影响”合并为“技能治理”；能力治理保留“技能治理、模型配置、平台集成、工具目录”四项，后三项页面与路由不在原型调整范围。
- 首页：技能列表与核心策略包为同层入口；核心策略包首页展示当前 v1、17 项适用技能、三类规则摘要和独立编辑入口。
- 抽屉：技能速览宽 `880px`，通过“概览 / 技能版本 / 依赖与影响”三个只读页签渐进展示；抽屉内 `input/select/textarea=0`。
- 完整性：版本页签保留 v4 草稿、v3 当前生效、v2 稳定回滚点、v1 归档及预览/查看/设为回滚点动作；依赖页签保留 6 个 Agent、3 个工作流、1 个历史引用、0 个阻断项及三条生产引用。
- 独立页面：技能编辑继续保留治理设置、模板内容、能力边界、本版说明和全部字段；草稿预览保持只读独立页面；核心策略编辑保留五类字段和保存策略草稿动作。
- 浏览器：`1280 × 720` CSS viewport，关键页面 `clientWidth=scrollWidth=1265`；搜索“经营”只返回“CRM 经营分析”；模型配置冻结提示不改变 `#skills`；console `0 error / 0 warning`。
- 构建：`npm run build` 通过；`npm run test:sites` 4/4 通过；`git diff --check` 通过。
- 设计 QA：`prototypes/capability-governance-v2/design-qa.md` 为 `final result: passed`，六张 V4 截图覆盖首页、核心策略、三个抽屉页签和独立编辑。
- 边界：仅本地高保真原型与治理文档，未修改正式 React/API，未重建本地业务容器，未部署 UAT/生产。
- 状态：`passed_with_product_review_pending`

## 2026-08-11 TASK-285 技能治理渐进式 HTML 原型 V3

- 范围边界：能力治理恢复“技能目录、策略与版本、依赖与影响、模型配置、平台集成、工具目录”六项；后三项点击只提示保持现状，路由和当前页面不变。
- 技能路径：技能列表 → `520px` 只读抽屉 → 独立技能编辑；编辑页没有目录表格或抽屉，四个步骤覆盖治理设置、模板内容、能力边界、本版说明。
- 策略路径：“策略与版本”先展示技能模板版本/核心策略包列表，再进入独立技能版本历史或核心策略编辑页。
- 依赖路径：“依赖与影响”先展示技能影响列表，再通过摘要抽屉进入完整依赖页。
- 浏览器：`1280 × 720` CSS viewport；关键页面 `scrollWidth=clientWidth=1265`，无外层横向溢出；搜索“经营”只返回“CRM 经营分析”；console `0 error / 0 warning`。
- 交互：详情抽屉、编辑四步骤、草稿预览、策略类型切换、策略编辑、依赖抽屉和完整依赖页通过；模型配置冻结提示保持 `#skill/dependencies` 不变。
- 构建：`npm run build` 通过；`npm run test:sites` 4/4 通过；`git diff --check` 通过。
- 治理校验：`validate-state.py .claw` 仍被仓库既有旧时间格式、历史规格 front matter/status 和已完成任务归档债务阻断；输出没有 TASK-285/FEAT-172 错误，本轮未跨范围批量清理历史状态。
- 设计 QA：`prototypes/capability-governance-v2/design-qa.md` 为 `final result: passed`；桌面视口截图覆盖技能目录和完整技能编辑页。
- 边界：仅本地高保真原型与治理文档，未修改正式 React/API，未更新本地业务容器，未部署 UAT/生产。
- 状态：`passed_with_product_review_pending`

## 2026-08-11 TASK-285 能力治理渐进式 HTML 原型 V2

- 用户反馈：V1 三栏常驻工作台被明确否决，原因是把目录、编辑、版本、依赖、风险和动作继续堆在同一页面。V2 保留 V1 证据但不沿用其结构。
- 核心路径：技能列表 → `520px` 只读抽屉 → 独立治理配置/版本/依赖/发布预览；模型厂商列表 → 只读抽屉 → 独立配置；场景模型路由独立页面。
- 浏览器：`1600 × 1000`；列表、编辑和模型路由状态均为 `clientWidth=1600 / scrollWidth=1600`；console `0 error / 0 warning`。
- 渐进边界：抽屉无 `input/select/textarea`；进入 `#skill/config` 后列表和抽屉均不存在；预览页无编辑控件，只展示差异和发布检查。
- 交互：搜索“经营”返回 1 项“CRM 经营分析”；清空搜索后“待检查”筛选返回 1 项“Semattice 研发交付管理”；核心策略包进入独立 `#skills/policy` 页面；版本、依赖、返回和模型厂商编辑路径通过。
- 构建：`npm run build` 通过；`npm run test:sites` 4/4 通过；`git diff --check` 通过。
- 治理校验：`validate-state.py .claw` 仍因仓库既有旧时间格式、已完成任务未归档和完成任务保留数量债务返回 1；过滤输出没有 TASK-285/FEAT-172 错误，本轮未跨范围清理历史状态。
- 设计 QA：全页和聚焦对比板已检查；修复平台账号换行与核心策略包错误路由后，`prototypes/capability-governance-v2/design-qa.md` 为 `final result: passed`。
- 边界：仅本地原型与设计文档，未修改正式 React/API，未部署 UAT/生产。
- 状态：`passed_with_product_review_pending`

## 2026-08-11 TASK-285 能力治理高保真 HTML 原型

- 原型：`prototypes/capability-governance-v1/index.html`，单文件 HTML/CSS/JS，无外部运行依赖；本地通过 `http://127.0.0.1:4179/` 访问。
- 信息架构：验证“治理总览、技能治理、模型治理、工具与集成”四个入口；技能治理统一目录、治理配置、版本管理、依赖影响和核心策略包，模型治理保留“厂商与模型、场景路由”。
- 桌面布局：`1600 × 1000` 下 body `clientWidth=1600`、`scrollWidth=1600`；技能与模型三栏为 `326 / 654 / 304 px`，未出现原页面右侧空洞或外层横向滚动。
- 交互：技能搜索“经营”仅保留“CRM 经营分析”；版本/依赖、场景路由、平台集成页签均成功切换；主要原型动作有可见反馈。
- 视觉证据：`screenshots/01-overview.png`、`02-skills.jpg`、`03-models.jpg`、`04-tools-integrations.jpg` 已逐张检查。
- 浏览器：console `0 error / 0 warning`；临时测试视口已恢复默认。
- 治理校验：`git diff --check` 通过；`validate-state.py .claw` 仍因仓库既有历史任务归档、旧规格状态/front matter 与旧时间格式债务返回 1，输出没有 TASK-285/FEAT-172 字段或状态错误，本轮未跨范围批量改写历史事实。
- 边界：仅交付本地原型和设计文档，未修改生产 React/API，未重建本地业务容器，未部署 UAT/生产。
- 状态：`passed_with_product_review_pending`

## 2026-08-11 TASK-284 运营控制台模型配置导航去重

- 导航契约：能力治理下只有一个“模型配置”入口，默认进入 `/platform/models/providers`；`/platform/models`、`/platform/models/providers` 和 `/platform/models/routes` 均保持该入口选中。
- 功能边界：`PlatformModelsPage` 的“模型厂商治理”“场景模型路由”页签、既有子路由与配置请求未修改。
- 定向测试：`PlatformShell.test.ts` 与 `PlatformModelsPage.test.tsx` 共 8 项通过。
- 完整前端：Vitest 44 文件/237 项通过；`npm run build` 通过，仅有既有 bundle-size warning；`git diff --check` 通过。
- 本地环境：`cc-local-stack` 从当前工作树定向重建 `cici-frontend`；运行容器 healthy、restart=0，厂商与路由深链均为 200，部署 JS 包含“模型配置”和两个原有页签文本，且不再包含“模型厂商与目录”。其他本地服务未重建，UAT/生产未修改。
- 浏览器边界：本地真实路由按认证设计重定向 `/platform/login`；未伪造平台认证或使用凭据，因此受权页面的桌面截图、页签点击和 console 复核待平台账号登录后完成。
- 状态：`passed_with_visual_acceptance_pending`

## 2026-08-11 本地 Demo Company company_id 初始化修复

- 根因：`AuthBootstrapData` 与 `OrchestratorBootstrapData` 启动初始化曾将 `demo-org` 写死；平台租户路由要求 `^org[a-z0-9]{17}$`，导致每次后端启动都可能重新创建无效租户。
- 修复：初始化改为读取 `app.auth.bootstrap-platform-account.governance-company-id`；本地配置固定为 `org00000000000000001`，非本地 profile 保留显式 legacy 默认以避免影响既有 UAT/生产租户；平台模型、集成、计费和前端组织回退同步收敛，测试 profile 显式保留 `demo-org` 作为历史 fixture。
- 本地验证：后端 `mvn -Dmaven.repo.local=.m2 -DskipTests package`、前端 Vitest 42 文件/231 项、前端生产构建、DevAutopilot Node 35 项和语法检查均通过。
- 运行验证：本地全栈重建后 `./stack verify` 通过；backend health=`UP`；重启 backend 后 `company` 表仍只有 `org00000000000000001|Demo Company|ACTIVE`，旧 `demo-org` 在 `company`、`company_model_config`、`tool_definition` 中均为 0，规范初始化数据均归属新 ID。
- 状态：`passed`

## 2026-08-11 UAT ACR 持久登录配置验证

- 变更：UAT root Docker 配置为 `op-registry.cloudcc.cn` 建立持久登录；配置文件已验证 `root:root 0600`，未读取或输出认证值。当前主机原本无 Docker auth config，因此未覆盖既有 registry 登录。
- 验证：当前 `cici-backend`、`cici-frontend` 均为 `2.8.61-beta.15`，两个 manifest 可读取；backend health=`UP`，两容器均为 `running/restart=0`。
- 公网：UAT 首页=200、匿名 `/auth/me`=401、Keycloak discovery=200、Semattice version=200、DevAutopilot health=200。
- 安全边界：Docker 原生 config 未配置 credential helper，凭据视为 root-only 高敏感配置；仅允许专用 pull-only 机器人账户，禁止复制进 Compose、Git、日志或前端。撤销操作是 `docker logout op-registry.cloudcc.cn`，删除配置前需确认没有其他 registry 条目。

## 2026-08-11 TASK-280 UAT `2.8.61-beta.9` 信息归位验收

- 本地：身份页面定向 4 项、完整 Vitest 42 文件/232 项和生产构建通过；仅保留既有 bundle-size warning。
- 发布：dry-run 锁定 `2.8.61-beta.9 / 500ea8981b7d`；ACR backend/frontend index digest 为 `sha256:6f55267840a0332eb5e027ca4dde3c304cefa947d90994724002e80a35395a37` / `sha256:6bebe2ae7f1a8dac4abe825e6ae6458646ba86cd7bcba0c6981b1c8c8a56b5df`。
- 运行：六容器 healthy、restart=0，health=`UP`、Flyway V109、Nginx、HTTPS 200、HTTP 301、匿名 auth/用户目录/激活接口 401 通过；30 秒稳定窗口 backend/frontend 错误计数为 0，四个状态服务 ID 哈希未变。
- 页面：受权 UAT 浏览器页脚为 beta.9；待激活成员的统一身份状态、说明与“检查激活状态”位于成员整体信息区，切到 CloudCC 页签后仍在页签上方，CloudCC 只显示用户名、安全标记与保存操作；console error/warning 为 0。
- 操作边界：页面验收只读，未点击检查、修复或保存；`18611892001` 的独立浏览器登录回归仍待 Demo Company 完成。
- 状态：`passed_with_business_acceptance_pending`

## 2026-08-11 TASK-281 INT-009 UAT 业务验收

- 真实对话正向：第二租户产品经理先生成缺陷草案，补充“父项目：智能体平台”后输出唯一全字段精确确认文本；确认前 `dev_defect=0`，确认后返回 `BUG-11164588`、独立 record ID、revision=1 和 correlation，前端显示“Semattice 写入成功回执”。
- 执行身份：Semattice 回读 `created_by` 对应 `service/active/研发产品经理`，authority source=`agentcici`；项目 record ID 与缺陷 record ID 不同。
- 负向：新会话仅发送“确认提交此缺陷”时要求补齐所有字段，缺陷总数仍为 1；草案、字段补充和短确认均未伪写入。
- 消费方：DevAutopilot `1.0.4-beta.3` 显示同一缺陷全部字段；分配第二租户开发者并确认后状态 `confirmed`、revision=2，Semattice 回读一致。
- 双租户：beta.8 将 Owner 404 降为独立治理告警，Demo 应用管理可继续；正式同步后两个租户均为 7 对象，Demo/第二租户缺陷计数为 0/1。两个正式 handoff 分别显示 0 条与 1 条缺陷，未读取浏览器存储或暴露 OACT。
- 前端：目标页 5/5、完整 42 文件/230 项和生产构建通过；仅保留既有 bundle-size warning。完整 Maven 的本机 PostgreSQL/Hikari 未完成边界仍不改写为全绿。
- 状态校验：`validate-state.py .claw` 仍被历史任务归档上限、旧规格状态/front matter 和旧时区格式债务阻断；输出没有 TASK-281/FEAT-169 字段或状态错误，本轮未跨范围批量改写历史记录。
- 状态：`passed`

## 2026-08-11 TASK-280 邮件激活状态协调修复

- UAT 只读诊断：目标账号只有 Demo Company 一条 `ORG_ADMIN/PENDING_ACTIVATION` 成员；external identity 存在且 subject 与 Keycloak 用户一致。Keycloak 用户唯一、enabled、emailVerified，required actions 为空且已有 password credential；目标用户当前 Keycloak session 数为 0。
- 日志：同一浏览器先有平台管理员 OIDC 302 成功，随后一条目标认证流程以 `authentication_expired` 返回 500；没有目标成员成功回调，因此原首次登录激活事务未发生。
- 修复：受控激活入口在远端仍待激活时重发邮件；远端已激活时同步成员为 `ACTIVE`，保持角色/资料/绑定不变并记录 `company_member.identity_activation_synced` 脱敏审计。
- 后端：`AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest,AuthServiceTest` 通过，`mvn -q -DskipTests package` 通过。
- 前端：完整 Vitest 42 文件/229 项通过，生产构建通过；仅保留既有 bundle-size warning。
- UAT 技术发布：`2.8.61-beta.7 / 4f7ae57f0aec` 六容器 healthy、restart=0，health=`UP`，Flyway V109、Nginx、HTTPS 200、匿名 auth 与新接口 401 通过；30 秒稳定窗口启动后错误数为 0，四个状态服务 ID 哈希未变。
- 业务边界：当前没有可控 ORG_ADMIN 浏览器会话，未绕过认证调用正式同步接口；目标成员仍为 pending，等待管理员点击“检查激活状态”及独立浏览器登录回归。
- 状态：`passed_with_business_acceptance_pending`

## 2026-08-11 TASK-281 beta.4 UAT 初始化缺口

- 技术发布：`2.8.61-beta.4 / 50ad506d39b8` 前后端 healthy、restart=0，health/version/Nginx/匿名 401 通过；四个状态服务 ID 哈希保持不变。
- 正式操作：第二租户 `initializations` 返回 ACTIVE、initializationReady=true、3 个资源，产品经理 Agent/Skill 补偿成功。
- 失败边界：代码复核确认补偿入口没有调用 Semattice template apply，`initializationReady` 也不校验 activation 的 metadata shape；因此不能证明老租户已有 `dev_defect`，beta.4 不记为业务通过。
- 修复验证目标：beta.5 必须证明正式补偿返回 7 对象/83 字段对应的新 metadata version，并在 Semattice 控制台/运行时回读 `dev_defect`。
- beta.5 本地修复：`DevAutopilotTenantApplicationReadinessTest` 与可信回执相关 5 个后端测试类通过，覆盖 7/83 成功回写和 6/60 失败关闭；平台应用页定向 4 项测试与 `npm run build` 通过，新增幂等“同步标准模板”入口。首次前端定向命令误带 `frontend/` 前缀导致 Vitest 未找到文件，修正为 `src/...` 后通过；该命令错误不计产品失败。
- beta.5 UAT：正式同步第二租户后，Semattice 权威库回读 metadata sequence=5、published、7 对象/83 字段，`dev_defect`=23 字段；产品经理/开发者 SERVICE 与 Owner HUMAN 投影均 active。真实未确认 Bug 请求未写入，但草案因“确认后成功提交”被守卫误拦，记为业务失败而非通过。
- beta.6 本地：`DeliveryWriteReceiptGuardTest` 新增将来时草案正例和混合草案/虚假完成态反例；与写入、初始化、Skill 相关 6 个测试类全部通过。完成态成功声明仍须真实回执。
- beta.6 UAT：未确认 Bug 请求已正常生成草案；补充“父项目：智能体平台”后，模型输出“确认提交此缺陷/允许提交缺陷”，与服务端完整确认契约不一致，故未调用 Tool、未写入记录，业务验收失败。beta.7 增加近期待补充草案和字段续答路由测试。

## 2026-08-11 TASK-281 缺陷可信写入回执本地验证

- 后端定向：`DeliveryWriteReceiptGuardTest,SematticeProjectDeliveryWriteToolServiceTest,SematticeProjectDeliveryToolServiceTest,SkillResolverServiceTest,DevAutopilotProductManagerAgentPublisherTest` 通过；覆盖无回执禁报成功、缺陷确认、`runtime.record.get` 写后回读、字段/revision/correlation 校验和确定性回复。
- 前端：完整 Vitest 42 个文件、228 项通过；`npm run build` 通过，仅保留既有 bundle-size warning。回执只消费服务端 `semattice_project_delivery_create` 的结构化 `tool_result`，不从模型正文推断成功。
- 完整 Maven：执行后停留在本机 PostgreSQL/Hikari 连接重试，人工停止退出 130；不记为完整后端套件通过。
- 状态：`partial`（本地目标链路通过；等待 Semattice 提供方 UAT 后执行 AgentCiCi UAT 正负向与租户隔离）。

## 2026-08-11 TASK-279 DevAutopilot 委托授权与双主体 UAT 验收

- 数据与迁移：空 PostgreSQL 16 从 V1 到 V109 共 105 项 migration 全部成功；UAT V109=`success`，`tenant_application_member_role` 存在，两条 DevAutopilot 执行绑定均为 `TENANT_APP_ROLE`。
- 后端定向：`AgentServicePrincipalExecutionServiceTest`、`ChatOrchestratorSseErrorTest`、`DevAutopilotTenantApplicationReadinessTest` 以及 Semattice 查询/创建/评审工具测试通过，覆盖应用角色矩阵、负责人/调用者分离、初始化补偿和 SSE 结构化结束。
- 前端定向：应用角色管理与聊天预检 2 个文件、3 项通过；此前关联 4 个文件、8 项回归通过，`npm run build` 通过。
- 完整套件边界：完整 Maven 套件已尝试，15 份集成报告因本机 PostgreSQL `localhost:5432` 拒绝连接未完成，不能记为全量通过；真实 UAT PostgreSQL、Flyway 与业务链路已另行验收。
- UAT 技术验收：首发 `2.8.61-beta.2 / c66d9448c95b`，仅重建 backend/frontend；备份 8 项非空且为 `0600`，四个状态服务 ID 不变，容器、health、Nginx、HTTPS、匿名 401 和启动日志通过。当前 `2.8.61-beta.3 / 47affe4086e5` 是该提交后继版本并保持 V109 成功。
- UAT 业务验收：第二租户 OWNER 查询 Semattice 项目成功；Demo Company 的非机器负责人 `ORG_ADMIN` 查询成功并返回本租户 0 项目。审计记录 `delegationPolicy=TENANT_APP_ROLE`、`appRole=APP_ADMIN`，实际 actor 与 `ownerPrincipalId` 不同。
- 管理端验收：“管理应用调用权限”独立弹窗显示机器负责人和 ORG_ADMIN 自动 APP_ADMIN、普通 ORG_USER 默认不允许调用；本轮只读未改角色，浏览器 error/warning 为 0。
- 状态校验：全仓 validator 仍因既有任务板归档上限、历史终态卡片、旧规格状态/front matter 和旧时区格式债务退出 1；输出不含 `TASK-279` 或 `FEAT-167` finding，未越界批量改写历史记录。
- 状态：`passed`

## 2026-08-11 TASK-280 组织成员统一身份修复入口

- 后端定向：`mvn -q -Dtest=AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest test` 通过，覆盖身份缺失协调、资料/角色保持、手机号确认、已绑定拒绝、幂等回放及既有 Keycloak provisioning。
- 后端构建：`mvn -q -DskipTests package` 通过。
- 前端定向：`AdminUsersPage.test.ts` 与 `adminApi.test.ts` 共 3 项通过；完整 Vitest 41 个文件、225 项通过。
- 前端构建：`npm run build` 通过，仅保留既有 bundle-size warning。
- 静态检查：`git diff --check` 通过。
- UAT 技术验收：`2.8.61-beta.3 / 47affe4086e5` 六容器 healthy；health=`UP`、Flyway V109、Nginx、HTTPS 200/HTTP 301 通过，匿名 `/auth/me`、用户目录和身份协调接口均为 401。
- 稳定性：backend/frontend 30 秒内保持 healthy、restart=0；后端启动后 ERROR/Exception=0，前端启动后 error=0。启动期间曾有一次后端未就绪导致的短暂 upstream refused，后端就绪后未复现。
- 状态校验：全仓 validator 仍因历史任务板归档、旧规格状态/front matter 和旧时间格式债务退出 1；输出不含 `TASK-280`、`FEAT-168` 或本轮更新时间 finding，未越界批量改写历史记录。
- 验收边界：未执行真实 Keycloak 发信、未调用目标成员修复、未修改 `18611892001`；真实 ORG_ADMIN 页面与登录闭环仍待业务验收。
- 状态：`passed_with_business_acceptance_pending`

## 2026-08-11 TASK-278 AI表格 UAT scope 回归修复预检

- 根因回读：UAT `2.8.60-beta.1` 实际 HUMAN scopes 与旧 Compose 默认一致，缺少 Semattice `metadata.version.get-current` 必需的 `metadata.read`；服务器没有受管 env 覆盖该项。
- 配置回归：新 Compose 渲染 HUMAN scopes 为 `metadata.read,runtime.record.read,runtime.record.create,runtime.record.update`，SERVICE scopes 保持独立。
- 自动化：Shell 语法、发布版本测试（含缺 scope 的失败关闭用例）、`AiTableDataServiceTest`、`OfficialAccessTokenServiceTest`、`2.8.61-beta.1` UAT dry-run 与 `git diff --check` 均通过。
- UAT 发布：`2.8.61-beta.1 / d4b273af39c2` 前后端 healthy，health=`UP`，版本/镜像一致，Nginx 有效，首页 200、匿名 AI表格 401、启动 ERROR 计数 0；四个状态服务 ID 哈希未变。
- 业务回读：受权租户页面实时读取 6 个 DevAutopilot 已发布对象，选择“变更”对象返回真实 0 记录空状态；原“无法读取业务对象”错误消失，console error/warning 为 0。
- 状态：`passed`

## 2026-08-10 TASK-275 双租户最终业务验收

- `owner/application`：第二租户 Owner 经正式恢复接口复用现有激活 HUMAN；同一 DEMO 身份可切换 A/B 组织。第二租户 DevAutopilot 开通返回 ACTIVE/initializationReady，自动创建已发布 `研发产品经理` Agent、PM SERVICE 和 Semattice 投影，初始 developer=0。
- `tenant-admin`：第二租户 ORG_ADMIN 通过独立 modal 新增并重命名 developer、选择同租户 HUMAN 负责人；一次性 Secret 未读取、未保存。暂停时 AgentCiCi=`已暂停`、Semattice=`suspended`、DevAutopilot=`休息 · 不可派单`，恢复后 active/waiting 且派单按钮可用。
- `isolation`：A 的 workspace 保持 2 名开发者；B 只返回本租户 0 项目、1 名 developer 和 `研发产品经理`。B 的 Semattice 会话显式传入 A 的 `company_id` 仍只返回 B 的 company 与成员。
- `agent`：第二租户员工首页显示 `研发产品经理`；`onekeytoken/auto` 对创建项目请求按 DevAutopilot 领域语义追问并准备草案，不再误答 CRM。正式 handoff 至 DevAutopilot `1.0.4-beta.2` 后动态名称、开发者与空项目事实一致。
- 状态：`passed`

## 2026-08-10 TASK-276 / TASK-277 Owner 身份生产发布验收

- 发布前门禁：Owner/身份 4 类定向后端测试通过，后端 package、前端 4 项测试与 `2.8.60` 生产构建、Compose config、版本规则测试及 `git diff --check` 均通过；仅保留既有 bundle-size warning。完整 Maven 套件未因本次发布重新扩写为全量通过。
- 发布身份：Git tag、ACR 与运行时统一为 `2.8.60 / 451f797e61df`；backend/frontend index digest 分别为 `sha256:1b4e96962c08900ae0372601b9a7fc99134615bcc0cd00aff36b5f102d8dba4a`、`sha256:859d23f4a65944161b22cc5a6cbeac2bc2db762a8f21a799eb490776491047c9`。
- 发布安全：备份 `/opt/cici/backups/20260810T122603Z-before-2.8.60-owner-identity` 四项非空且 `0600`；只重建 backend/frontend，四个状态服务 ID 哈希未变。
- 运行验收：六容器 healthy，health=`UP`，Flyway 104 项验证成功且 V108 无迁移，Nginx 有效，HTTPS 200/HTTP 301，匿名 `/auth/me`、Owner 状态及协调接口均为 401，启动 ERROR 计数 0。
- 业务边界：当前可控浏览器访问生产平台被正常重定向到登录页，Chrome 会话不可用；未绕过认证。目标 Owner 的真实协调仍需受权 PLATFORM_ADMIN 完成。
- 状态：`passed`

## 2026-08-10 TASK-276 / TASK-277 Owner 身份协调与启动死锁恢复

- 后端定向测试 `KeycloakIdentityProvisioningServiceTest` 11/11、`PlatformTenantOwnerRecoveryServiceTest` 4/4、`PlatformTenantOwnerIdentityServiceTest` 6/6、`PlatformTenantOwnerProvisioningTest` 5/5 通过，合计 26 项，Failures=0、Errors=0。
- `mvn -q -DskipTests package` 通过。常规协调只作用于当前唯一 Owner；异常恢复只在没有有效 Owner 时接受已完成 OIDC 激活的账号，使用悲观锁串行所有权变更，相同目标幂等，不设置密码、不删除旧 Owner。
- 前端 `PlatformTenantApplicationsPage.test.ts` 4/4 与 `npm run build` 通过；构建仅有既有 bundle-size 提示。页面显示脱敏 Owner 身份状态，创建/协调通过独立确认 modal 完成，未在应用卡片中长期堆放表单。
- 完整 `mvn -q test` 在 `KnowledgeBaseLifecycleIntegrationTest` 初始化阶段因本机 PostgreSQL 未启动持续重试，人工停止并退出 130；不能记为全量套件通过，发布后以 UAT 真实 PostgreSQL、Keycloak、权限和浏览器链路补验。
- `git diff --check` 通过；状态校验器仍报告迁移前历史 task/spec/front matter 债务，本次 FEAT-165/166 已进入 `verified`，未批量改写无关历史。

- UAT `2.8.60-beta.1 / 93a487f4e393` 已完成不可变发布与真实恢复验收。完整本地 Maven 套件未补跑成功的限制仍按上条保留，不扩写为全量测试通过。
- 状态：`passed`

## 2026-08-10 TASK-275 第二租户 Semattice 开通与 DevAutopilot 前置门禁

- 受权平台管理员在 UAT `2.8.59-beta.11` 的第二测试租户页面点击“开通 Semattice”，页面返回“Semattice 已开通，并已完成企业身份绑定”；应用汇总从 1 变为 2，卡片状态为运行中。
- 随后点击“开通 DevAutopilot”返回 `DevAutopilot activation requires an active tenant ORG_ADMIN`。该结果符合标准模板需要同租户 active HUMAN owner 的安全与业务前置条件。
- UAT 只读数据库回读：主测试租户有 3 个 ACTIVE 成员及 ACTIVE DevAutopilot activation；第二测试租户只有 1 个 `OWNER/PENDING_ACTIVATION`，Semattice binding 为 `PROVISIONED`，且 DevAutopilot activation 为 0。失败请求没有留下半初始化 activation。
- 自动化回归 `DevAutopilotHandoffServiceTest,OfficialDevAutopilotActivationFilterTest,SematticeProjectDeliveryToolServiceTest,SematticeProjectDeliveryWriteToolServiceTest,SematticeProjectDeliveryReviewToolServiceTest,AgentServicePrincipalExecutionServiceTest` 共 13 项，Failures=0、Errors=0。
- 状态：`partial`。仍需第二租户 Owner 完成邮件激活/首次 OIDC 登录，以及 Demo Company 正常员工完成产品经理截图原句对话回归。

## 2026-08-10 TASK-276 新租户 Owner OIDC provisioning 本地验证

- 先以旧实现执行 `PlatformTenantOwnerProvisioningTest`，统一认证场景因仍要求本地初始密码而失败，复现根因；实现后 5/5 通过，覆盖 Keycloak provisioning、邮箱必填、本地兼容模式、既有统一账号复用与远端失败关闭。
- `PlatformTenantOwnerProvisioningTest,AdminUserServiceTest,KeycloakIdentityProvisioningServiceTest`、后端 `-DskipTests package`、前端平台租户共享逻辑 3/3 与生产构建通过；前端仅有既有 bundle-size 提示。
- UAT `2.8.59-beta.4 / 1d74f436ec7d` 的健康、版本、Nginx、首页 200 与匿名租户 API 401 通过；真实创建因同事务内 JPA 未刷新 PostgreSQL trigger 生成的 `public_id` 而失败关闭。失败后账户、标识、租户、成员、外部身份精确计数均为 0。
- 已增加 `CompanyProvisioningServiceTest`，证明新账号 `saveAndFlush -> EntityManager.refresh -> 登录标识` 的顺序；该测试与 5 项 Owner 测试、相关身份服务回归及后端打包通过。待下一 beta 真实重验。
- UAT `2.8.59-beta.6 / 9563aa2e37cf` 真实重验通过：租户 `orgvdd8xckmvc8r5yi6q`、账户与两类登录标识唯一，成员为 `OWNER/PENDING_ACTIVATION`，外部 subject 与 Keycloak User 一致，本地 PASSWORD credential=0；Keycloak enabled、邮箱未验证且 Required Actions 同时含 `VERIFY_EMAIL`/`UPDATE_PASSWORD`。
- beta.6 的 6 容器 healthy，health=`UP`，版本/commit/imageTag 一致，Nginx 有效、首页 200、匿名租户 API 401、backend/frontend 近 15 分钟错误计数 0。Owner 尚未点击邮件，首次登录与成员 `ACTIVE` 回读保持 `partial`。
- 额外发现 Realm User Profile 未声明两个 ownership 属性，Keycloak 将其静默丢弃；不阻断当前 subject 绑定与邮箱激活，但严格恢复路径会失败关闭，已单列 issue，不以一次性 UAT patch 掩盖。
- 历史 `PlatformTenantLifecycleIntegrationTest` 报告时间为 2026-08-09，失败原因是本机 PostgreSQL `localhost:5432` 未启动，不作为本次结果；真实数据库/Keycloak 正向链路待 UAT 验收。

- 状态：`partial`（系统侧通过，等待 Owner 邮件激活与首次登录）

## 2026-08-09 TASK-275 Principal 权威状态与初始化补偿

- `mvn -q -Dtest=OfficialAccessTokenServiceTest,ServicePrincipalServiceTest test` 与 `mvn -q -DskipTests package` 通过；完整 `mvn test` 仍受本机 PostgreSQL 不可达限制，不记为全量通过。
- 受管初始化先暴露 UAT scope 缺少 `identity.principal.sync`，随后暴露历史 HUMAN owner 无统一身份绑定；两项均修复在受管源码/配置。普通 HUMAN 业务 OACT 仍拒绝无统一身份账号，仅 server-only Principal bootstrap 令牌例外且 scope 固定为单项 sync。
- UAT `2.8.59-beta.3 / 5be204680e16` backend/frontend healthy，内部版本精确一致；正式 `POST .../initializations` 返回 200。资源回读为 PM Agent/SERVICE active、鲁班 active、墨子 suspended。
- 发布后 10 分钟 AgentCiCi error 计数为 0；匿名团队与 DevAutopilot workspace 均为预期 401。第二租户隔离未执行，状态保持 `partial`。

- 状态：`partial`

## 2026-08-09 TASK-275 OACT activation 与完整 UAT 链路

- `OfficialAccessTokenServiceTest`、`OfficialDevAutopilotActivationFilterTest`、前端管理域名范围测试、版本规则测试及构建检查通过；发布提交为 `94ceb612bd71`。
- UAT `2.8.59-beta.1` backend/frontend healthy，内部 `/system/version` 回读 version/imageTag=`2.8.59-beta.1`、gitCommit=`94ceb612bd71`。
- 目标租户真实 ORG_ADMIN 链路：handoff=200、DevAutopilot consume=200、workspace=200、team=200、Semattice console=200。console URL 为 `https://uat.agentcici.com/console/` 且 fragment 含短期 OACT；未输出令牌。
- team 精确回读 4 项 ACTIVE：产品经理 AGENT/SERVICE 各 1、developer 2。第二租户隔离未执行，不把单租户通过扩写为完整多租户验收。

## 2026-08-09 TASK-275 标准 PM 初始化、handoff 与 UAT 版本基线

- `backend/frontend`：`mvn -q -DskipTests compile`、`mvn -q -Dtest=DevAutopilotHandoffServiceTest test`、`npm test -- --run src/assistant/AssistantApp.test.ts` 与 `npm run build` 均通过；前端仅有既有 bundle-size 提示。新增 activation 编排以同租户 OWNER/ORG_ADMIN 推导初始负责人，缺少二者时失败关闭。
- `DevAutopilot`：`npm test` 24/24 通过，包含 `/?handoff=...` 静态根路径解析；`node --check src/server.js` 与 `node --check public/app.js` 通过。UAT `1.0.2-beta.3 / 2e1596c139f2` 的 handoff 入口为 `200 text/html`，不再返回静态 404。
- `UAT-release`：按生产 `2.8.58` 基线发布 AgentCiCi `2.8.58-beta.1 / 4ffab5c43c0e`；backend/frontend healthy，health=`UP`，版本/imageTag/commit 一致。匿名 handoff=401、匿名 `/api/platform/.../initializations`=401，均为预期授权边界。
- `UAT-boundary`：自动化浏览器未附着正常平台或 ORG_ADMIN 会话，故未调用真实补齐初始化、未创建主体或业务数据。平台管理员补齐既有 activation、正常租户 handoff 及双租户 Semattice 隔离仍为待验收项。

- 状态：`partial`

## 2026-08-09 TASK-275 机器主体新增与编辑交互修订

- `backend-focused`：`mvn -q -f backend/pom.xml -Dtest=ServicePrincipalServiceTest test` 通过。覆盖同租户有效 HUMAN owner 校验；创建保留当前 ORG_ADMIN 为操作审计 actor，而主体责任人可独立选择。
- `frontend-focused/build`：`npm --prefix frontend test -- --run src/admin/pages/AdminServicePrincipalsPage.test.ts src/admin/adminApi.test.ts` 通过（2 files / 5 tests）；`npm --prefix frontend run build` 通过。创建表单已从列表/详情画布移除，新增和编辑均为带遮罩的可访问 modal；仅有既有 bundle-size 提示。
- `static-check`：`git diff --check` 通过。尚未执行 UAT 发布或正常 ORG_ADMIN 写入，故未创建业务主体或读取/输出 Secret。
- `release/UAT`：`scripts/release-acr.sh --dry-run --channel test` 后发布 `2.8.57-beta.2 / 2753d268acd9`；backend/frontend ACR index digest 为 `sha256:aa50caecfe55aaa8ac6c0b0e1f8494578a21966dda7f8fa0f20dec2303a92cdc` / `sha256:7da4fa653ff8b1de55ea183ea29a09b669708c7419eccd8100699f08179a6a37`。UAT backend/frontend 均为该 tag，`/actuator/health=UP`，`/system/version` 的 commit/version/imageTag 一致，Nginx 配置通过；外部管理页为 `200 text/html`，匿名团队 API 为预期 `401`。
- `UAT-boundary`：当前自动化浏览器未附着可用的正常 ORG_ADMIN 标签页，故未执行真实新增/编辑写操作；该业务验收仍需受权租户会话完成。

- 状态：`passed`

## 2026-08-09 TASK-275 租户自助团队管理与安全链路收敛

- `backend-compile`：`cd backend && mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 通过。`mvn -q -Dmaven.repo.local=../.m2 -Dtest=ServicePrincipalServiceTest,ServicePrincipalTokenExchangeServiceTest test` 通过。
- `frontend-focused/build`：`npm test -- --run src/admin/adminApi.test.ts src/admin/pages/AdminServicePrincipalsPage.test.ts` 通过 4/4；`npm run build` 通过。仅有既有 bundle-size 提示。
- `devautopilot-runtime`：独立仓库 `npm test` 通过 22/22，包含 activation snapshot 的 tenant、PM Agent 与 SERVICE enrollment 负向验证；已通过独立 beta UAT 发布入口上线 `1.0.2-beta.1 / 1204ab74d375`，运行健康为 integrated/ok，未使用生产发布脚本。
- `state-validation`：`agentic-project-guidelines` validator 仍因历史 task board 的终态卡片留在 Active 区等 brownfield 债务退出 1；输出不含 `TASK-275` 或 `FEAT-164` finding。DevAutopilot validator 只报告既有 `FEAT-004` 使用历史 `superseded` status。两者都未在本次批量重写。
- `release/UAT`：`scripts/release-acr.sh --dry-run --channel test` 与正式发布完成，annotated tag/commit 为 `2.8.57-beta.1 / e5c097adda5f`。backend/frontend ACR index digest 为 `sha256:3b642bf91ee54b9e6d36783ca958b032a88b0a1b8667961190d23bafc1c9d091` / `sha256:6f87671503319c8dc06be405fc137d3d6edb6fba90e258918500c6ac90b5bb3c`；UAT 以同一 tag 重建 backend/frontend，`/system/version` 的 commit/version/imageTag 一致，前端工件包含 `2.8.57-beta.1`。
- `backup/migration`：发布前备份位于 `/data/apps/agentcici/backups/20260809T013059Z-before-2.8.57-beta.1`，包含 Compose、受保护环境文件和非空 PostgreSQL dump；Flyway V108 成功。仅 backend/frontend 被重建，四个状态服务未重启。
- `UAT-boundary`：backend/frontend healthy；匿名 `GET /api/admin/devautopilot/team`=401。自动化浏览器未继承正常 ORG_ADMIN 会话，未创建 PM、开发者或 Secret；该正向流程与双租户隔离留给受权业务会话验收。

- 状态：`partial`

## 2026-08-05 TASK-274 机器主体 scope 治理与生产验收

- `backend-focused`：`mvn -q -Dtest=ServicePrincipalServiceTest,OfficialAccessTokenServiceTest test` 通过；覆盖完整替换、SERVICE allowlist、跨企业/撤销/allowlist 外拒绝、脱敏审计，以及 SERVICE-only 删除 scope 不扩散到 HUMAN。
- `frontend-focused/build`：`npm test -- AdminServicePrincipalsPage.test.ts` 通过 3/3，`npm run build` 通过；仅有既有 bundle-size 提示。Compose config 与 `git diff --check` 通过。
- `full-backend-limit`：`mvn -q test` 因本机 PostgreSQL 不可达在 Hikari 重试阶段停止，不能记为全量通过；定向契约测试和生产健康验收均通过。
- `release`：Git tag/commit 为 `2.8.57 / 750fb71ab47d`；backend/frontend ACR index digest 为 `sha256:4a3c552bc498fa9e4bef823b3e2c071d4b1e34a05b9e2a2ec590d1a2aa46c13b` / `sha256:1ad603f8e395c340b38f61616242be4076611c40fbb5309d31cc76ff171a2d02`。
- `backup/deploy`：备份 `/opt/cici/backups/20260805-235439-before-2.8.57-task274-scope-governance` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend。六容器 healthy、backend health=`UP`、Nginx 配置有效、x HTTPS=200，匿名管理 API=401。
- `authorization`：受权 ORG_ADMIN 管理页回读大乔为原 4 项加 `runtime.record.delete`；悟空、后羿、哪吒均保持原 4 项。平台审计 `id=77` 为 `service_principal.scopes_updated`，不含秘密。
- `oact/semattice`：新 OACT 绑定目标 SERVICE、company `org5nszpgj99jaysxv6y` 和 tenant，删除 scope 存在；Semattice 空输入探测返回 HTTP 400 / `VALIDATION_FAILED`，而非 403，审计 `audit:req-task274-delete-scope-smoke-20260805`。未提供记录 ID，未删除数据。

- 状态：`passed`

## 2026-08-05 项目治理技能迁移验证

- 状态：`partial`
- README、AGENTS 当前托管声明已统一为 `agentic-project-guidelines` `3.10.0`；当前声明扫描未发现旧技能引用。
- `agentic-project-guidelines` validator 对 README/AGENTS 的 guidance error 为 0，证明当前技能名称、托管标记和安装来源均有效。
- 全量状态校验为 `partial`：仍有 202 条迁移前历史问题，包括 Active 区终态任务 84、旧 feature status 58、缺 YAML front matter 12、旧时区时间戳 22、缺 front matter 字段 19、完成任务保留量 1、其他格式 6。
- 按 Brownfield Adoption 规则不批量改写历史；后续修改相关任务或规格时渐进修正。本次未运行产品代码测试。

## 2026-08-05 TASK-272 管理端设置页深链刷新修复（生产 2.8.56）

- `merge/release`：修复提交 `903efee` 已由合并提交 `564fb9fbfd8d` 进入 `main`；Git annotated tag `2.8.56` 与 backend/frontend ACR index digest `sha256:b9fad83dc1ed0710844a78c645c56bf6b82922047b82f3f7dc2d1b62f1ab12e6` / `sha256:e767ed0177ecd7f599897caad741b2a90f7e7002bc21bb148ae8e912dfb60e89` 一致。
- `quality-gates`：前端定向 Vitest 4 files / 52 tests、完整 `mvn -q -Dmaven.repo.local=.m2 test`、前端 TypeScript/Vite 生产构建、Compose config、HTTP Nginx `nginx -t` 与 `git diff --check` 通过。前端构建仅输出既有 bundle-size 提示。
- `backup/deploy`：发布前四类备份位于 `/opt/cici/backups/20260805-184240-before-2.8.56` 且均非空；生产六容器 healthy，backend health=`UP`、`/system/version=2.8.56 / 564fb9fbfd8d`、线上 `nginx -t` 通过，近期 backend/frontend 启动错误扫描均为 0。
- `route-boundary`：公网 x HTTP 为 301、HTTPS 首页为 200；匿名 HTML 请求 `/admin/service-principals` 现为 `200 text/html` 且包含 SPA `#root`，未出现 `Authentication required`；匿名 `/api/admin/service-principals` 仍为预期 `401 application/json`。`onechat.agentcici.com` DNS 无法解析，记录为既有入口风险。

- 状态：`passed`

## 2026-08-05 TASK-270 悟空 Client ID 切换闭环

- 受治理改名后，悟空新 Client ID `dev-autopilot-developer-wukong` 可完成 Keycloak Client Credentials 和 AgentCiCi OACT 交换；旧 ID 返回预期 Keycloak 认证失败。
- Semattice 可信 OACT 同步将既有悟空 SERVICE principal 原位协调为新 Client ID，`identity.principal.sync` 审计为 `succeeded`；角色、负责人和业务历史未重建。
- 悟空自身 CLI 的 `identity status --human` 和只读 `tasks list --human` 均成功；未使用人类、产品经理或其他机器身份替代，也未输出密钥或 OACT。

## 2026-08-05 TASK-271 组织切换全称悬浮提示

- `frontend-focused`：`npm test -- src/assistant/AssistantApp.test.ts` 通过（1 test）。
- `frontend-build`：`npm run build` 通过（TypeScript + Vite）；仅有既有 bundle-size 提示。
- `scope`：仅为现有当前组织入口和组织名称补充原生 `title` 提示，不改变组织来源、组织切换、管理后台入口、菜单样式或主题。
- `production-2.8.54`：Git tag/commit 为 `2.8.54 / 9a0fe88bf59f`；backend/frontend ACR index digest 为 `sha256:36d870c55ad8234e2a193823cc4b71153feaabc54b6422ec3a547b648657198e` / `sha256:103cdf2c5cecd864e81d7ec17a832bdc89d7c6def820235b2083e807166cb91c`。发布前备份 `/opt/cici/backups/20260805-155805-before-2.8.54-org-tooltip` 的环境、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本正确、Nginx 配置通过、`x.agentcici.com` HTTPS=200、HTTP=301。线上页面返回新前端 `assets/index-MIoTtAf-.js`，工件含“当前组织：”提示文本。未使用或伪造受权会话，真实菜单悬浮验收待用户回读。

- 状态：`passed`

## 2026-08-05 TASK-249 组织简档代理热修

- `frontend-build`：`npm --prefix frontend run build` 通过（TypeScript + Vite）；仅有既有 bundle-size 提示。
- `production-nginx`：同步两份版本化 Nginx 配置后，`docker exec cici-frontend nginx -t` 通过并完成热重载。服务器回环、显式公网 IP/SNI 与 DNS 域名的匿名 `GET /admin/company/profile` 均返回 `401 application/json;charset=ISO-8859-1`，不再返回 SPA `200 text/html`；前端/后端容器 healthy，backend health=`UP`。
- `scope`：未构建镜像、未重启后端或数据库、未触碰 Keycloak、身份或业务数据。受权组织管理员页面数据由既有接口返回，未伪造登录会话。

- 状态：`passed`

## 2026-08-05 TASK-268 Semattice 本体第一、第二阶段本地集成

- `backend-focused`：`mvn -q -Dtest=SematticeOntologyLifecycleServiceTest,OntologyPublishServiceTest,SematticeOntologyAdapterTest,SematticeOntologyContractCompilerTest,SematticeOntologyHttpGatewayTest test` 通过。覆盖当前已发布元数据发现、受限单对象记录查询、最小公开连接器配置、稳定对象/字段/关系编译、服务端 OACT 与幂等调用边界，以及首次发布幂等、变更影响模拟、候选取消、独立审批激活、AgentCiCi 版本同步、非破坏性安全回滚与远端漂移阻断。
- `frontend-contract/build`：`npm test -- --run src/admin/ontology/ontologyWorkbenchContract.test.ts`（8 tests）与 `npm run build` 通过。运行治理标签有完整 tab/tabpanel 语义，连接 Semattice 后会阻断原本直发 AgentCiCi 版本的入口，改走受控编译、独立审批与激活路径。未取得受权会话，未伪造真实跨系统读写或桌面浏览器验收。
- `persistence-integration-limit`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OntologyPersistenceIntegrationTest test` 因本机 PostgreSQL 未配置而在 Spring 数据源初始化超时，16 个测试均未执行。测试已更新为验证 V105 追加的三张 `ontology_semattice_*` 表；不得将该环境失败记作验证通过，发布前需在隔离 PostgreSQL 实际跑 Flyway 与此断言。
- `production-limit`：未执行 V105、未配置或调用真实 Semattice 租户，也未触发 metadata 发布；该提交仅完成本地实现与单元测试，生产验收需单独发布授权。
- `destructive-change-limit`：字段从 active 到 deprecated、hidden、purging、tombstone 的退役状态链尚未进入 AgentCiCi 业务模型，当前删除会失败关闭，不调用 `metadata.changeset.purge`。不得把这一安全限制表述为破坏性清除已交付。

- 状态：`passed`

## 2026-08-05 TASK-267 管理 SPA 路由与接口路径冲突修复

- `frontend-focused`：`npm --prefix frontend test -- --run src/admin/adminApi.test.ts src/admin/pages/AdminServicePrincipalsPage.test.ts` 通过（2 files / 3 tests）；断言用户与机器主体浏览器 API 都使用 `/api/admin/...`，不与 `/admin/...` SPA 路由冲突。
- `frontend-build`：`npm --prefix frontend run build` 通过（TypeScript + Vite）；仅保留既有 bundle-size 提示。
- `nginx-route-smoke`：Nginx 1.27 配置语法通过。用生产构建前端工件挂载的临时 Nginx 对 `GET /admin/service-principals` 返回 `200 text/html` 且包含 SPA `#root`，不再转发为后端认证 JSON。该临时容器已停止；生产发布和真实受权硬刷新验收待执行。

- 状态：`passed`

## TASK-252 - Keycloak 统一密码与邀请落地

- `identity-gate`：`dev-login.py` 与 `check-assignment.py` 均确认 MANAGER-001 对新增密码启动入口、邀请配置校验、过滤器与定向测试拥有当前任务授权。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=TenantContextFilterTest,AuthControllerTest,KeycloakOidcLoginServiceTest,KeycloakIdentityProvisioningServiceTest test` 通过。覆盖 OIDC 启用时拒绝本地历史密码写入、`UPDATE_PASSWORD` 的 state/nonce/PKCE 参数、邀请回跳只能是 `/app`，以及新的密码启动路由无需现有应用会话即可到达控制器。
- `frontend-focused`：`npm test -- --run MyEmailAccountsModal.test.ts oidcAutoRedirect.test.ts` 通过（2 files / 6 tests）；个人档案删除旧密码提交，保留明确的“前往统一账号中心修改密码”动作。
- `build/static`：`npm run build`、`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config` 与 `git diff --check` 通过。Vite 仅输出既有 bundle 大小提示。
- `production-2.8.52`：Git tag/commit 为 `2.8.52 / 8c9ce75884c5`；backend/frontend ACR index digest 为 `sha256:5ba1dc5a167bc8605d539587d18e2ddbc9a9b97ddb29db0c50dd9493677b34df` / `sha256:a6d2667ebcc8c0dd42b70415d88ff09c4123a09a267ef78ab144d0952f87685d`。最终发布前备份 `/opt/cici/backups/20260805-143617-before-2.8.52-oidc-password-route` 的环境、PostgreSQL、KB 与 Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本接口/Nginx 通过、x HTTPS=200/HTTP=301。公网 `/auth/oidc/password?return_to=/app` 实测返回 Keycloak 302，验证 `kc_action=UPDATE_PASSWORD`、`prompt=login`、PKCE、state、nonce 及 callback redirect URI；未使用真实用户密码、令牌或激活链接。首次 `2.8.51` 曾发现该路由被过滤器返回 401，已由 2.8.52 的过滤器测试与线上 smoke 覆盖修复。

## TASK-267 - 机器主体管理页面

- `frontend-focused`：`npm test -- AdminServicePrincipalsPage.test.ts` 通过（2 tests）。覆盖 lifecycle 中文呈现、暂停主体不得轮换密钥以及人类负责人展示。
- `frontend-build`：`npm run build` 通过（TypeScript + Vite）；仅保留既有 bundle size 提示。
- `service-principal-contract`：`mvn -q -Dtest=ServicePrincipalServiceTest test` 通过。既有服务契约继续保证轮换产生的新密钥不进入审计记录，并拒绝跨企业的主体操作。
- `secret-display-boundary`：代码审阅确认列表接口只消费无密钥的投影数据；轮换成功的 `clientSecret` 只存放于页面内存，切换主体/确认已保存均立即清除，未使用 localStorage、URL 参数、日志或埋点。
- `diff-check`：`git diff --check` 通过。
- `production-2.8.50`：backend/frontend ACR index digest 为 `sha256:affd6eb08e2b65c0a5d33c2ca59dbe29e72208444b618714eab31a1e478dd20c` / `sha256:59e52f78a72dc11197ed9aa976f0dd21e319dabe2bb393d6ae189b871b3e35c0`。发布前备份 `/opt/cici/backups/20260805-052058-before-2.8.50-machine-principals` 的环境、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，`/actuator/health=UP`、版本为 `2.8.50 / 82e1c249e622`、Nginx 通过，`https://x.agentcici.com/`=200、HTTP 根路径=301。匿名 `/admin/service-principals` 为 401 `Authentication required`，符合组织管理员保护契约。无受权管理员浏览器会话，未伪造主体或密钥操作。

## TASK-266 - AI表格业务对象实时列表

- `backend-focused`：`mvn -q -Dtest=AiTableDataServiceTest test` 通过（2 tests）。覆盖当前成员短期 OACT、已发布对象目录、无原始租户/令牌浏览器输入、仅使用已索引文本字段的前缀查询、服务端 limit 上限和记录游标回传。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `frontend-build`：`npm run build` 通过，TypeScript 与 Vite 生产构建成功；仅保留既有 bundle 大小提示。
- `frontend-auth-header`：`npm test -- src/assistant/AiTableBusinessObjectList.test.ts` 通过（2 tests），验证目录请求复用 `authFetch(LS_ASSISTANT_TOKEN, ...)` 并使受保护 API 的 401 错误可见。
- `frontend-full`：`npm test` 通过，34 个测试文件 / 208 项测试。
- `diff-check`：`git diff --check` 通过。
- `routing-contract`：两份版本化 Nginx 与 Vite 配置均有 `/ai-table` 后端代理；本地 Vite 请求 `/ai-table/catalog` 返回后端代理连接失败的 `500 text/plain`，不是 SPA `index.html`，证明路由未被 fallback 吞掉。`docker compose -f deploy/docker-compose.acr.yml config` 通过。
- `browser-limit`：Playwright CLI 的本机 Chromium 对 `127.0.0.1:5174/app` 直接返回工具环境的 `chrome-error`/HTTP 500，而同一地址 curl 为 HTTP 200；已停止无效重试，未把它记录为产品页面失败。先前已确认的桌面高保真截图仍保留为视觉基线；生产将以受权会话完成真实数据回读，不伪造登录或业务数据。
- `full-backend-limit`：`mvn -q test` 进入既有 `KnowledgeBaseLifecycleIntegrationTest` 后等待未配置的本机 PostgreSQL（Hikari 重复连接），未在本任务中完成全量结果；聚焦测试和编译均通过，发布后按 runbook 验证生产 Flyway/健康与受权业务路径。
- `production-2.8.48`：Git tag/commit 为 `2.8.48 / 3bde866470b3`；backend/frontend ACR index digest 为 `sha256:7bcea486aac0612e168208b08698cf1297e4290512fc6639f3150d8ddb0d60ad` / `sha256:3f1666e3a1c54d9cce51f2659c20589fe2a5851dd06d803030288064fa4deac0`。发布前备份 `/opt/cici/backups/20260805-000608-before-2.8.48-ai-table-live` 的 `acr.env.before-release`、PostgreSQL、KB 和 Qdrant 均非空。仅重建 backend/frontend；六容器 healthy，`/actuator/health=UP`，版本接口返回 `2.8.48 / 3bde866470b3`，Nginx `-t` 通过，`x.agentcici.com` HTTPS=200、HTTP=301。经 x HTTPS 虚拟主机请求 `/ai-table/catalog` 返回受保护 API 的 401 JSON，与 `/auth/me` 一致，不再落入 SPA fallback。生产 OACT 已启用，数据平台地址已配置，逗号分隔 scopes 含 `metadata.read`、`runtime.record.read`。本会话没有成员登录 Cookie/测试账号，未伪造真实记录回读；`onechat.agentcici.com` 仍无法 DNS 解析，未作为发布成功依据。
- `production-2.8.49`：Git tag/commit 为 `2.8.49 / 760776a354f5`；backend/frontend ACR index digest 为 `sha256:eb931697527bcdfbc8486a7a23910c0f37ddd8b8be0bfd3a356b9499e8ce576c` / `sha256:69f7573a3bbfb9b2f7b41638905e539e866bd95721cd81fbc7a26de2f796f209`。发布前备份 `/opt/cici/backups/20260805-075621-before-2.8.49-ai-table-auth` 的环境、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，`/actuator/health=UP`、版本为 `2.8.49 / 760776a354f5`、Nginx 配置通过。`x.agentcici.com` HTTPS=200、HTTP=301，线上页面返回新前端工件 `assets/index-mhRX_a5B.js`。AI表格目录匿名调用为 401 `Authentication required`、无效 Bearer 为 401 `Invalid or expired token`，符合受保护 API 契约。受权成员数据回读待真实登录会话完成；`onechat.agentcici.com` DNS 仍不可解析，未作为发布成功判定。
- `production-2.8.47`：Git tag/commit 为 `2.8.47 / aeeb24f9ea66`；backend/frontend ACR index digest 为 `sha256:28980489578b0bdc50d148941056154833d96c8fc16e5afb0aa8d6dcedeba686` / `sha256:fc36895b5063c30665edbf2a419564d56ff54fa81172318918eec463322133a5`。发布前备份 `/opt/cici/backups/20260804-233730-before-2.8.47` 的环境、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务 healthy，`/actuator/health=UP`、版本接口为 `2.8.47 / aeeb24f9ea66`、Nginx 配置通过。`https://x.agentcici.com/`=200、HTTP 根路径=301、匿名 `/auth/me`=401；`onechat.agentcici.com` DNS 仍不可解析，未作为发布成功判定。

## TASK-265 - DEV Autopilot 研发交付评审 Tool 生产闭环

- `formal-bindings`：生产数据库回读确认 query/create/review 三个 Tool 均 enabled，Skill `semattice-project-delivery-management` 为 always-on，产品经理 SERVICE Principal 以 `PRIMARY_OWNER` 委托执行。
- `delivery-e2e`：正式任务 `019fcc18-756f-7782-a9e7-bf34e9c94670` 完成领取、设计 v1 提交、产品经理驳回、设计 v2 提交与批准、40%/90% 进度、1.5h 工时、阻塞上报/解除、commit/test_report 产物、完成申请与批准；最终 `已完成 / 100% / revision 13`。
- `state-gates`：设计批准前进度上报返回 `DESIGN_REQUIRED`；存在开放阻塞时完成申请返回 `OPEN_BLOCKERS_EXIST`。
- `identity-boundaries`：哪吒休息态投影无法使用开发者 CLI；产品经理凭据使用开发者 CLI 返回 403。
- `post-release-smoke`：完成审批后开发者 CLI 仍可回读任务最终状态，AgentCiCi、DEV Autopilot 与 Semattice 生产健康检查通过。

## TASK-255 - 应用未登录态自动跳转 SSO（合并验证）

- `frontend-focused`：`npm --prefix frontend test -- --run src/assistant/oidcAutoRedirect.test.ts` 通过（1 file / 5 tests）；普通 guest 触发跳转，OIDC/CloudCC 回调票据、已有会话、登录提交和重复尝试均不触发。
- `frontend-build`：`npm --prefix frontend run build` 通过；仅保留既有 bundle 大小提示。
- `diff-check`：`git diff --check` 通过。
- `production-2.8.46`：Git tag/commit 为 `2.8.46 / 42d81ceccd46`；backend/frontend ACR index digest 为 `sha256:d75441e1aaa11f83bafff1b1062723757b8ad8ef9443ae4b88ea393bb8215d5a` / `sha256:9b3c8ab956756817648694cfa705431f2c670ca2aa0386d0a38ac37b60bb9e7a`。发布前备份 `/opt/cici/backups/20260804-233209-before-2.8.46` 的环境、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务 healthy，`/actuator/health=UP`、版本接口为 `2.8.46 / 42d81ceccd46`、Nginx 配置通过。`https://x.agentcici.com/`=200、HTTP 根路径=301、匿名 `/auth/me`=401；`onechat.agentcici.com` DNS 仍不可解析，未作为发布成功判定。

## TASK-252 - 公司切换会话隔离（发布前）

- `frontend-focused`：`npm test -- --run src/assistant/workbenchSessions.test.ts src/assistant/AssistantApp.test.ts` 通过（2 files / 4 tests）；缓存键测试确认同一工作台 API session 在不同公司形成不同浏览器内存键。
- `frontend-build`：`npm run build` 通过（TypeScript + Vite）；仅保留既有 bundle 大小提示。
- `isolation-contract`：认证 `companyId` 切换会递增作用域版本、同步清空公司级内存状态；会话/消息、工作台、知识库、智能体、技能、快捷指令与监控的异步响应在作用域不一致时不回写，工作台流式回调也会丢弃。服务器 API session ID 保持不变，未删除或迁移任何历史会话。
- `production-2.8.43`：Git tag/commit 为 `2.8.43 / 45b942c06b86`；backend/frontend ACR index digest 为 `sha256:9fcfa8f2c72a5cb80ea6f5cdc68f7dd3a384bb590aed5fbbecb3c5a576e14610` / `sha256:8ad594eea01883e1e87901158c58bc3423d49bcb65738c2d65cf2f505f24d2f5`。发布前备份 `/opt/cici/backups/20260804-213816-before-2.8.43-company-switch-isolation` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，backend health=UP、版本接口为 `2.8.43 / 45b942c06b86`、Nginx 配置通过、线上工件含公司缓存键标记、`https://x.agentcici.com/`=200、匿名 `/auth/me`=401。受权用户 A→B→A 实际界面复核待完成，未伪造登录会话。
- `cache-and-realtime-hardening`：截图显示浏览器仍在执行 `2.8.42`，生产容器则为 `2.8.44`，确认为旧入口缓存。工作台初始消息/运行态已按公司键初始化；会话 SSE、轮询、延迟审批与语音回调均在作用域失效后静默退出。定向 Vitest 通过（2 files / 5 tests），生产构建通过；仅保留既有 bundle-size 提示。
- `production-2.8.45`：Git tag/commit 为 `2.8.45 / 435ee0af6e2d`；backend/frontend ACR index digest 为 `sha256:6f5c077947c8d2e51f7b6549affea0764166f7ab0d3aa876c4600b1f5d0c3a5b` / `sha256:856c1df5b9e521ea56ce98e86e8c725d8d88c4e83d670e5111d9b84abfb434bd`。发布前备份 `/opt/cici/backups/20260804-225342-before-2.8.45-tenant-isolation` 四项均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本正确、实际 SSL Nginx `-t` 通过。`/app` 明确返回 `no-store`，当前哈希 JS 返回 immutable，旧 JS 返回 404，根路径=200、匿名 `/auth/me`=401。真实受权 A→B→A 页面回归待用户刷新后复核。

## TASK-252 - CloudCC CRM orgId 契约（发布前）

- `external-contract`：受权参数按 CloudCC 文档字段 `username/safetyMark/clientId/secretKey/orgId/grant_type=password` 调用已返回 HTTP 200、`result=true` 且有 accessToken；未输出、存储或写入 token/SecretKey。
- `backend-focused`：`mvn -q -f backend/pom.xml -Dmaven.repo.local=backend/.m2 -Dtest=CloudccAccessTokenServiceTest test` 通过（3 tests）；覆盖当前用户 session 校验、并发 token 合并，以及 Token JSON 使用 `orgId` 且不含旧 `companyId`。
- `backend-compile`：`mvn -q -f backend/pom.xml -Dmaven.repo.local=backend/.m2 -DskipTests compile` 通过。
- `frontend`：`npm --prefix frontend test -- --run src/assistant/AssistantApp.test.ts` 与 `npm --prefix frontend run build` 通过；仅保留既有 bundle-size 提示。
- `production-2.8.44`：Git tag/commit 为 `2.8.44 / 4690e58cc154`；backend/frontend ACR index digest 为 `sha256:28fe55de36010179b92a4203eabca6998030e9fbefc40f0da660cad5bf9a6b68` / `sha256:0c73ece9d1c2846bd2d616323bdf633f49643fefbfa52e54b8caee3b8afd7996`。发布前备份 `/opt/cici/backups/20260804-220929-before-2.8.44-cloudcc-orgid` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六容器 healthy，health=UP、版本正确、Nginx 通过、`x.agentcici.com`=200、匿名 `/auth/me`=401。V104=true，CloudCC 集成 5/6 已有 `orgId`；未配置的香港大学保留为未配置状态，未伪造凭据或连通结果。

## TASK-265 - DEV Autopilot 研发交付评审 Tool（发布前）

- `backend-focused`：`SematticeProjectDeliveryToolServiceTest`、`SematticeProjectDeliveryWriteToolServiceTest`、`SematticeProjectDeliveryReviewToolServiceTest`、`ToolOrchestratorServiceTest`、`SkillResolverServiceTest` 全部通过；覆盖 6 个已发布对象、已决提交不再误列待评审、产品经理 SERVICE read/create/update OACT、稳定幂等键、禁止身份/令牌/目标覆写、Tool 编排及 Skill 显式绑定。
- `backend-package/static`：`mvn -q -f backend/pom.xml -DskipTests package` 与 `git diff --check` 通过。
- `state-validation-limit`：全仓状态 validator 仍包含早于本任务的 hot-index、旧任务状态/时间格式和历史规格 frontmatter 债务；本任务保持既有边界，未将历史债务误报为本次实现失败。
- `production-pending`：V103、正式 Tool/Skill 回读及真实设计/验收评审闭环将在 2.8.42 发布后补录。

## TASK-252 - Keycloak 邀请开通闭环修复（发布前）

- `identity-gate`：MANAGER-001 的 SSH 持钥、Git 身份、`TASK-252/main` 与实现、迁移、测试、规格、状态文件范围均由 `dev-login.py` / `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=KeycloakIdentityProvisioningServiceTest,PrincipalIdentityGovernanceIntegrationTest test` 通过；覆盖新建用户、失效 remote `sub` 重建并重绑、已存在待激活 User 的邮件重发、同名但 account 属性冲突拒绝，以及已激活用户不发送重置邮件。
- `member-governance`：`AdminUserServiceTest` 通过，覆盖已停用成员的重复邀请不会恢复成员状态、不会触发 Keycloak 账户处理或发送凭据设置邮件。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `fresh-postgresql-v102`：本机 PostgreSQL 15 临时库从 V1 正向迁移至 V102（98 项）通过；`PrincipalIdentityGovernanceIntegrationTest` 验证新增 HUMAN Principal、legacy Keycloak mirror 与更新同一 `account_external_identity.id` 后的 subject 重绑。测试库和临时登录角色已删除，未接触业务库或生产库。
- `full-integration-limit`：默认完整 Spring 鉴权测试仍依赖未配置的 `127.0.0.1:5432/agentcici_test`，启动阶段连接超时；已停止该环境重试，不将其误报为本次代码失败。发布前将以生产 Flyway 与受权邀请路径完成实际验收。
- `production-2.8.41`：Git commit/tag 为 `3320ed77515d / 2.8.41`；backend/frontend ACR manifest 已 inspect。发布前备份 `/opt/cici/backups/20260804-113909-before-2.8.41-invitation-lifecycle` 的环境、PostgreSQL、KB、Qdrant 均非空；仅 backend/frontend 重建，六容器 healthy，backend health `UP`、版本接口为 `2.8.41 / 3320ed77515d`、Flyway V102=true、Nginx 校验通过。`https://x.agentcici.com/` 为 200，匿名 `/auth/me` 与缺 Bearer 的 service-token exchange 为预期 401。Realm SMTP 已脱敏回读为 SSL/465，未输出秘密。

## TASK-264 - 研发身份花名与新增开发者生产验收

- `identity-authority`：AgentCiCi 权威库事务回读为 Oliver / 大乔 / 悟空 / 后羿，四名 Principal 均 active；产品总监继续绑定全局用户 `18611892001`，三名 SERVICE 的 PRIMARY owner 均为 Oliver。
- `machine-provisioning`：通过既有受治理管理 API 创建后羿 SERVICE `2678bbfb-a234-4912-bfef-47d912ce9e34`，public ID `S2026XS877MF3`，client `dev-autopilot-developer-houyi`；一次性 secret 原子写入 `/opt/devautopilot/secrets/developer-houyi.env`，保持 `root:root 0600`，未输出到终端、日志或 Git。
- `approval-and-projection`：独立审批 `9e5783ea-7713-462f-8388-24b763eca4a0` 由不同于申请人的组织管理员批准；四名 Principal 均经短时 OACT 同步 Semattice，后羿绑定现有开发者角色与研发交付部 primary membership。
- `console-api`：使用真实短时控制台 Session 调用 Semattice members/overview，精确返回 Oliver、大乔、悟空、后羿以及 4 members / 3 roles / 1 organization / 5 objects / 42 fields。
- `cli-e2e`：悟空和后羿各自机器凭据执行 DEV Autopilot `tasks list --human` 成功；大乔产品经理凭据返回退出码 3、`FORBIDDEN`。公网 DEV Autopilot health 为 HTTP 200、`mode=integrated`。
- `state-validation`：TASK-264 新增文件与写入范围有效，`git diff --check` 通过；全仓状态 validator 仍因早于本任务的 hot-index 超长、旧任务状态/时间格式和历史规格 frontmatter 债务退出 1，本任务未越界修复无关历史。

## TASK-263 - 显式 Tool/Skill 与 SERVICE 执行生产验收

- `focused/backend`：`ChatOrchestratorServiceModelIdentityTest`、Agent SERVICE 授权、Semattice 读写 Tool、Tool 编排、OACT、Skill Resolver 等聚焦测试通过；编译、test-compile、package 与 `git diff --check` 通过。
- `migration`：独立 PostgreSQL 16 从 V1 正向迁移至 V101，共 97 项迁移成功；身份治理集成测试通过。
- `full-suite-diagnostic`：325 项测试中 2 项既有非本任务失败（并发 workspace create 返回 500/200 而非 200/409；旧 Skill governance fixture 预期 200 实得 401），0 error、3 skipped；TASK-263 聚焦与迁移回归均通过，未虚报全量绿色。
- `state-validation`：TASK-263/FEAT-155 与 assignment 无新增 finding；全仓校验仍因既有 hot-index 超长、历史时间格式、旧完成任务仍位于 Active Tasks 和旧规格状态/frontmatter 债务退出 1，本任务未越界改写无关历史记录。
- `explicit-bindings`：生产 API 回读产品经理 Agent 的 2 个 Tool、1 个 always-on Skill 和 SERVICE 执行主体；未输出 client secret、JWT 或 OACT。
- `query-e2e`：对“现在有哪些项目在执行”真实调用查询 Tool，返回 4 个项目、1 个执行中项目、2 个活跃任务和 8.0 小时；Trace 完成且 AgentCiCi 委托审计指向产品经理 SERVICE。
- `write-e2e`：未确认消息由大模型生成完整草案，Trace 工具数 0；精确确认后工具数 1，创建 `DAS-941C43CF`。Semattice `runtime.record.create` actor 为 `742daca1-ce58-49cc-9e53-530444ba1c47`，记录 owner 为“DEV Autopilot 产品经理”。
- `release/public`：最终版本 `2.8.40 / f4011a8a3b79`；backend/frontend healthy，状态服务容器 ID 不变，health `UP`、Nginx 有效；AgentCiCi 根路径、DEV Autopilot 和 Semattice health 均为 HTTP 200。

## TASK-262 - DEV Autopilot 受治理机器身份生产验收

- `backend-focused`：机器主体管理服务定向测试与发布基线随 `2.8.38` 通过；生产 backend/frontend 均为 `2.8.38` 且 healthy，Flyway 主体治理表已可回读。
- `identity-readback`：产品总监全局 mobile 精确为 `18611892001`，account/member、OWNER/ACTIVE 与两台 SERVICE 的 PRIMARY owner 一致；两台 SERVICE 均为 ACTIVE，public ID 与 client ID 稳定。
- `lifecycle-e2e`：审批 `f1591286-71bb-49ed-b874-80a7c7640fa9` 下执行开发者 Semattice 投影暂停/恢复与 AgentCiCi 主体暂停/恢复；两次暂停后的 CLI 均失败，两次恢复后的 `tasks get` 均成功。
- `credential-rotation`：生产轮换开发者 client secret；旧 secret 无法取得 Keycloak token，新 secret 可取得 token、交换 OACT 并读取任务；`/opt/devautopilot/secrets/developer.env` 保持 `root:root 0600`，未输出 secret/token。
- `negative-authorization`：开发者创建项目与读取主体目录均为 HTTP 403；永久撤销能力由定向测试覆盖，未对当前生产开发者执行不可逆操作。
- `edge-regression`：发现 frontend 只用基础 Compose 重建后未监听 443；恢复 `docker-compose.acr.ssl.yml` 后 80/443 均监听，版本化 Nginx 配置 `nginx -t` 通过，公网 `/devautopilot/` 与 `/devautopilot/api/health` 均为 HTTP 200，OACT JWKS 为 200。
- `state-validator`：TASK-262/FEAT-154 与完成任务上限未产生错误；仓库级校验仍因大量既有历史规格 frontmatter/status、旧 active/done 卡片和 README/AGENTS 技能块缺失而失败，本任务未扩散修复这些无关历史状态。

## TASK-261 - 创建意图改由大模型语义理解

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、任务分支和三份实现/测试路径经 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest,ChatOrchestratorServiceModelIdentityTest test` 通过。创建候选覆盖截图原句和“名称叫”表达，确认消息不进入草案路由；模型指令断言完整语义理解、不得正则抽取、零工具/零写入及精确确认格式。
- `backend-package/static`：`mvn -q -DskipTests package` 与 `git diff --check` 通过。
- `release-2.8.34`：dry-run 与正式发布成功；Git tag/commit 为 `2.8.34 / 84c814b19fe0`。backend/frontend ACR index digest 分别为 `sha256:57fe1b7207af855c42e07607c7e8b1433871b9b53b4d13fef82ae3611c5e3320`、`sha256:b91a5b46391f74d24551fd8d2c667dd4440951486cdc885548f8bcbe94fce5d3`。
- `production-backup/deploy`：发布前备份 `/opt/cici/backups/20260731-223300-before-2.8.34-task261-model-intent` 的 env、PostgreSQL、KB、Qdrant 均非空；只强制重建 backend/frontend，四个状态服务容器 ID 保持不变。六容器健康，health `UP`，版本为 `2.8.34 / 84c814b19fe0`，Nginx 校验通过，近期启动错误为 0。
- `public-smoke`：`https://x.agentcici.com/` 与 `/devautopilot/` 为 200，匿名 `/auth/me` 为预期 401。
- `live-model-understanding`：目标租户受权会话用截图原句“帮我创建一个新项目：AgentCiCi企业级智能体平台”得到完整项目名和精确确认文本；响应模型为 `onekeytoken/auto`。生产 Trace 为 `model_call_count=1`、`tool_call_count=0`、`WAITING_CONFIRMATION`，证明答复来自模型且未执行工具。
- `semattice-zero-write`：使用当前成员短期 OACT 调用线上 `runtime.record.query` 成功，`dev_project` 共 2 条，目标名称 `AgentCiCi企业级智能体平台` 为 0 条；未输出或保存可复用令牌。

## TASK-260 - 研发项目名称自然语言提取修复

- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryWriteToolServiceTest test` 通过，新增断言覆盖“现在创建一个研发项目名称叫：AgentCiCi企业级智能体平台”完整提取及确认指令。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `production-2.8.33`：发布前四类备份非空；backend/frontend 切换为 2.8.33 后六容器健康，health `UP`、版本接口为 `2.8.33 / b680c961b8f6`。线上原句返回完整项目名草案，未执行写入。

## TASK-259 - 研发交付产品经理确认式创建项目、需求与任务

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、任务分支和全部实现/测试/状态路径均由 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=SematticeProjectDeliveryToolServiceTest,SematticeProjectDeliveryWriteToolServiceTest,ToolOrchestratorServiceTest test` 通过。覆盖无确认仅返回项目草案、精确确认调用 `runtime.record.create`、OACT Bearer 传递、租户参数拒绝和既有原生工具回归。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `state-validation`：本任务前端和任务状态格式已通过任务范围检查；全量 `validate-state.py` 仍报告既有历史规格/任务板格式债务，未改写不在本任务范围内的历史文档。
- `production-2.8.32`：backend/frontend 不可变镜像均已 inspect；发布前备份的 `acr.env`、PostgreSQL、KB、Qdrant 均非空。仅重建 backend/frontend，六容器健康，`/actuator/health` 为 `UP`，版本接口为 `2.8.32 / 2e42ed3ec926`。
- `live-confirmation-gate`：线上受权会话对“现在创建一个棕榈地的研发项目”仅返回草案与精确确认指令；“确认创建项目：棕榈地”创建 `DAS-00B30667`。继续确认创建 `REQ-02F5F798 / 项目启动工作台` 与“搭建项目启动页”均成功。智能体实时查询显示 1 个需求、1 个任务；Semattice 回读确认需求 `project_id`、任务 `project_id` / `requirement_id` 均指向刚创建记录。未输出或保存可复用令牌。
- `agent-configuration`：目标租户 `dev-autopilot-pm` 的持久化系统提示词已更新为确认式创建规则；更新结果为单条命中，不含凭据或令牌。

## TASK-257 - DEV Autopilot 启动器入口

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`codex/TASK-257-dev-autopilot-launcher-entry` 分支以及菜单、测试、规格与状态文件范围均经 `dev-login.py` / `check-assignment.py` 返回 `allowed`。
- `frontend-focused`：`npm test -- --run src/assistant/AssistantApp.test.ts` 通过（1 文件 / 1 test），覆盖“DEV Autopilot / 研发交付 / 研”与固定独立应用 URL。
- `frontend-regression/build`：`npm test` 通过（32 文件 / 199 tests）；`npm run build` 和 `git diff --check` 通过。构建仅输出既有 Vite 大 bundle 提示。
- `production-2.8.28`：主线提交 `f2814efc3a07` 与 annotated tag `2.8.28` 已推送；backend/frontend ACR index digest 分别为 `sha256:18c2794a28050552c0797e48cec507637b4de72d1036f3529f18b16a291ef31a` / `sha256:9f20dd10b58b467c6265aeab0037eb1c272cdf4791139ea81387ea4188d3b0a1`。发布前备份 `/opt/cici/backups/20260731-090547-before-2.8.28-task257` 的 env、PostgreSQL、KB、Qdrant 均非空；只重建 backend/frontend，六容器健康，backend `health=UP`、版本为 `2.8.28 / f2814efc3a07`、Nginx 校验成功。新版前端静态资源含 DEV Autopilot；`https://x.agentcici.com/` 和 `https://x.agentcici.com/devautopilot/` 均为 200，匿名 `/auth/me` 为预期 401。生产浏览器无会话时正确显示统一登录边界；没有伪造用户凭据，已登录菜单的最终视觉点击验收交由正常业务会话完成。

## TASK-254 - company_id 迁移完整性审计与遗留修复

- `identity/assignment`：`check-assignment.py` 与 `dev-login.py` 均返回 `allowed`；MANAGER-001 的 SSH 持钥、Git 身份、任务分支及账单/脚本/状态文件范围已验证。
- `static-audit`：账单 Java、账单测试、E2E、Qdrant smoke 和演示 SQL 的定向扫描未发现 `member.org`、`org_id`、`orgId`、`AGENT_ORG_ID` 或 `ORG_ID` 遗留。全仓非迁移、非测试、非前端运行路径扫描仅剩历史设计文档与 CloudCC 内置技能文档的外部 `orgId` 契约，未作为 AgentCiCi 顶层企业字段修改。
- `syntax-and-compile`：`bash -n scripts/e2e-local-business.sh scripts/verify-qdrant-stack.sh`、Python AST 解析 `scripts/seed-demo-environment.py`、`mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 和 `git diff --check` 通过。
- `integration-limit`：`nc -z 127.0.0.1 5432` 返回不可达；为避免将环境连接失败误报为功能结果，未执行会在 Flyway/Hikari 初始化阶段阻塞的 `AdminBillingIntegrationTest`。数据库恢复后应补跑该定向测试，验证组织管理员计费用量页面。
- `production-2.8.25`：主线合并提交 `105cc666a958` 与 Git annotated tag `2.8.25` 已推送；发布前备份 `/opt/cici/backups/20260729-202816-before-2.8.25-task254` 的 `acr.env`、PostgreSQL、KB、Qdrant 均非空。仅重建 backend/frontend，六服务健康，backend `health=UP`、版本为 `2.8.25 / 105cc666a958`、Nginx 校验成功、`https://x.agentcici.com/` 为 200；匿名 `/auth/me` 与 `/admin/billing/overview` 均为预期 401。`onechat.agentcici.com` 仍无法 DNS 解析，作为既有入口风险保留。

## TASK-252 - FEAT-145 统一 Principal 身份与治理

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`feature/TASK-252-unified-principal` 分支和授权范围经 `dev-login.py` 返回 `allowed`。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `fresh-postgresql`：一次性 PostgreSQL 16 容器执行 V1→V96、插入历史账户、再迁移 V97/V98；`UserAccountPublicIdIntegrationTest` 通过。该过程发现并修复了 `user_account → principal` 外键早于 AFTER trigger 检查的问题，最终采用 `DEFERRABLE INITIALLY DEFERRED`，确保新账户与其 HUMAN Principal 在同一事务内一致提交。
- `principal-mapping`：`PrincipalIdentityGovernanceIntegrationTest` 通过，验证新 `user_account` 自动创建 `HUMAN:ACTIVE` Principal，且后续 `account_external_identity` 写入会镜像为 `principal_identity/HUMAN_USER`。
- `oidc-regression`：`KeycloakOidcLoginServiceTest` 3/3 通过。`AuthFlowIntegrationTest` 未通过：共享测试库连接阶段连续超时，Spring Context 未创建，17 项均为同一环境错误；未改写历史迁移、未对共享库执行 repair，使用隔离 PostgreSQL 完成 V98 迁移验证。
- `production-2.8.20`：backend/frontend ACR index digest 分别为 `sha256:9b6493264ce20ab256ad0dd3f2ca0a4fb434d2307e6a0121ecafc08165bb27bc`、`sha256:f51cbde8f06d0ca6933d5a6b747a4b4762c013ec060db6b5fad7790dcd44b429`；发布前备份 `/opt/cici/backups/20260727-225300-before-2.8.20-feat145` 的 env、PostgreSQL、KB、Qdrant 均非空。六服务 healthy，版本为 `2.8.20 / 8db900e4efc2`，Flyway V98 成功，生产 Principal/HUMAN/Identity 计数均为 24；`x.agentcici.com` 为 200、匿名 `/auth/me` 为 401、OIDC start 302 到 Keycloak。`onechat.agentcici.com` DNS 不可解析，未作为当前发布成功依据。Keycloak Realm 尚无 SMTP，自动开户 feature flag 保持 false。
- `machine-oact-contract`：mvn compile、OfficialAccessTokenServiceTest 和 KeycloakOidcLoginServiceTest 通过。后者以本地 JWKS 服务器签发 RS256 client-credentials token，验证交换边界必须解析受信 Keycloak iss、sub 与 azp；SERVICE OACT 覆盖 service principal、PRIMARY owner、client ID 与 scope claim，未输出 bearer 或 secret。
- `machine-provisioning-decoupling`：`KeycloakIdentityProvisioningServiceTest`、`OfficialAccessTokenServiceTest`、`KeycloakOidcLoginServiceTest` 与 backend compile 通过。验证机器开户不要求人类邀请 redirect URI、仅人类开户要求该 URI、机器开关关闭时 `createServiceClient` fail closed。生产只检测到 provisioner Client ID，未检测到其 secret，且 Keycloak host 无 SMTP listener/MTA；因此未启用任一自动开户开关。
- `production-2.8.23`：backend/frontend ACR index digest 分别为 `sha256:82d4278d215ae1ac9adbcace14b9121c7bd9c84c520a2ca17712b560327928b0`、`sha256:0f6e22ebce5cf7e7fb3703ca568152dad4f12e27068b6cf7c70bb83faa3b451a`；发布前备份 `/opt/cici/backups/20260727-233807-before-2.8.23` 的 env、PostgreSQL、KB、Qdrant 均非空。backend/frontend 已按 `--force-recreate --no-deps` 切换，六容器健康，`/system/version` 为 `2.8.23 / a7cd78f88543`，Flyway V98/V99 为 true；`x.agentcici.com` 200、匿名 `/auth/me` 401、交换端点无 Bearer 401 / 伪造 Bearer 403、Keycloak discovery 与 OACT JWKS 200。机器、人类 provisioning 与交换开关继续 fail closed。
- `production-2.8.24`：Compose flag contract 以 `docker compose config` 验证，并发布 backend/frontend index digest `sha256:d2a1dcad568e3167e327e713c977ad2fc83a40cf1348ac4f46be1174a4f0043e` / `sha256:710971cde48ce1fdc59af837331a79d0eb1a42d428a87fa90bace2a496a49ca8`。备份 `/opt/cici/backups/20260727-234415-before-2.8.24` 四项均非空；线上实际运行 `2.8.24 / 58a96d618207`，六容器健康，V98/V99=true，machine-provisioning、service-token-exchange、人类 provisioning 均为 false。匿名边界与交换 401/403 继续通过。
- `machine-provisioning-live`：Keycloak provisioner secret 经轮换后通过受限 stdin 写入部署环境，配置备份 `/opt/cici/backups/20260727-234937-before-machine-provisioning-enable` 存在。backend 重建后健康；以部署环境中的 confidential client 进行 client_credentials 得到有效 300 秒令牌，未打印 token 或 secret。机器开关为 true；人类 provisioning 与服务交换开关均仍为 false。
- `fresh-postgresql-v99`：一次性 PostgreSQL 16 执行 V1 至 V99 后 PrincipalIdentityGovernanceIntegrationTest 通过；确认 service_principal_scope 已创建，新增 HUMAN Principal 和 legacy Keycloak binding mirror 均保持兼容。临时容器 cici-feat145-pg 已删除。
- `semattice-principal-projection`：Semattice Go 全量测试、vet、module verify、Linux amd64 CGO-free 构建和 diff check 通过；不可变 release `/opt/semattice/releases/20260727T151437Z-console` 后服务 active、edge health 为 200、匿名 console API 与 capability invoke 均为预期 401、Nginx 校验通过。
- `exchange-route-correction`：2.8.21 发布后发现生产 Nginx 未代理 /public 前缀，公网 POST 落入前端为 405，而 backend loopback 为预期 401。未越权修改不在 TASK-252 范围内的 Nginx 配置；端点改为既有安全代理前缀 /openapi/v1/official/service-token，前置 token-isolation filter 与 controller 同步更新，compile 与定向安全测试再次通过。
- `production-2.8.22`：backend/frontend ACR index digest 分别为 `sha256:b30ee3a7045668e810f4bc02f8d84097c869d667620f7ae834aef419c9787928`、`sha256:5be82d0316eb08a7104b3d6c79ae07e02c6d52075a7c9c1fb20e9d1c26f8da96`；发布前备份为 `/opt/cici/backups/20260727-232515-before-2.8.22`。六容器 healthy，backend health 为 UP，版本为 `2.8.22 / 645b53f6ea58`，Flyway V98/V99 均为 true；匿名 auth/me 为 401，服务交换端点缺 Bearer 为 401、带任意 Bearer 在 feature flag 关闭时为 403，证明公网代理与 fail-closed 边界生效。Keycloak discovery 继续为 200；未配置 SMTP、OACT signing 或受权 service client，未开启 provisioner/机器交换。
- `frontend`：`npm run build` 通过；仅有既有 Vite 大 chunk 提示。

## TASK-251 - 全局用户公共编号

- `identity/assignment`：MANAGER-001 的 SSH 持钥、Git 身份、`codex/TASK-251-global-user-public-id` 分支和迁移/账户/平台目录/测试/状态代表路径经 `dev-login.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest,UserAccountPublicIdIntegrationTest test` 通过；目录投影测试覆盖 `publicId` 返回，未配置临时数据库时迁移集成用例按设计跳过。
- `fresh-postgresql`：新建后删除 PostgreSQL 16 容器，从 V1 迁移至 V96 后插入 `created_at=2024` 的历史账户，再迁移 V97；断言回填 `U2024[A-Z0-9]{8}`。随后插入 2026 账户，断言触发器生成 `U2026[A-Z0-9]{8}`、两者不重复，直接更新 `public_id` 被不可变触发器拒绝。命令：`USER_ACCOUNT_PUBLIC_ID_MIGRATION_TEST_URL=... mvn -q -Dtest=UserAccountPublicIdIntegrationTest test`。
- `frontend`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 通过（2 tests）；`npm run build` 通过，仅有既有 Vite 大 chunk 提示。
- `browser-desktop`：本地 Vite 页面以 Playwright mock 的平台角色和目录响应在 1280px 桌面态验证。用户行显示“用户编号：U2026A7K29MXQ”，未破坏既有五列表格、搜索、分页或主题，控制台 error 为 0。截图为 `frontend/.playwright-cli/page-2026-07-26T13-00-48-305Z.png`，属于本地未提交验收产物。
- `production-2.8.19`：用户授权发布后，后端/前端 ACR index digest 分别为 `sha256:b9db2c4974aeebb63c38223189bd41eb9f17b8d875faa87de19d4c3ea9303b82`、`sha256:a44c54c6a8d7a0eaea547c3a557712fe881e641a4f1466d6fc98f781dbc7cab7`；发布前备份 `/opt/cici/backups/20260726-220110-before-2.8.19` 的 env、PostgreSQL、KB、Qdrant 均非空。六服务健康，内网 health 为 `UP`、版本为 `2.8.19 / 99d4cc3cb206`、V97 成功，公共编号空值和格式不匹配均为 0；生产 IP/SNI 的 onechat/x HTTPS 均为 200，匿名 `/auth/me` 为 401。无受权平台账号，真实目录展示保留为人工验收项。
- `main-regression`：合入后 `mvn -q -Dtest=PlatformRegisteredUserServiceTest,UserAccountPublicIdIntegrationTest test`、`mvn -q -DskipTests compile`、前端目录定向测试（1 文件 / 3 tests）与 `npm run build` 均通过；前端构建仅有既有 chunk-size warning。

## TASK-250 - MCP HTTP 会话复用修复

- `identity/assignment`：`MANAGER-001` 的 SSH 持钥、Git 身份、`codex/TASK-250-mcp-session-propagation` 分支及 MCP 源码/测试/状态代表路径经 `dev-login.py` 与 `check-assignment.py` 返回 `allowed`。
- `backend-focused`：`mvn -q -Dmaven.repo.local=../.m2 -Dtest=McpClientTest test` 通过（1 test）。本地 HTTP 伪 MCP 服务以 SSE 返回 `initialize` JSON-RPC 结果与 `Mcp-Session-Id`；断言请求顺序为 initialize、initialized 通知、tools/list、tools/call，后三步复用同一会话，所有请求使用 `MCP-Protocol-Version: 2025-03-26`，tools/call 保留 `Bearer user-jwt`。测试也确认 MCP Server 配置中的陈旧会话/协议头不会污染新初始化。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `production-limit`：未改动 Semattice、MCP Server 配置或生产环境；真实 `cc-semattic-mcp` 刷新和工具调用留待生产发布后以受权会话复核。
- `main-merge`：用户于 2026-07-25 授权后，`4958bc1 fix(mcp): reuse streamable HTTP session` 已以快进方式推送至 `origin/main`；未构建镜像、未部署生产。

## TASK-248 - 平台注册用户目录展示已加入组织

- `identity/assignment`：MANAGER-001 的 SSH challenge-response、任务分支及代表性后端、前端、测试和状态文件均经 `dev-login.py` 与 `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过；覆盖账户一行、零/一/多组织、按组织 ID 去重、无效成员关系过滤，以及一次批量成员查询。
- `backend-compile`：`mvn -q -DskipTests compile` 通过；成员批量读取使用单条 `join fetch` 查询预加载账户与组织，避免组织名称读取产生 N+1 查询。
- `frontend-focused`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 通过（1 file / 2 tests）；覆盖全量账户目录文案和多组织名称/无组织文案格式化。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；构建仅有既有 Vite chunk-size warning。
- `browser-limit`：本地桌面端访问 `/platform/registered-users` 按预期跳转 `/platform/login`，控制台 error 为 0，并保存登录边界截图 `output/playwright/task248-platform-registered-users-auth-boundary.png`。本会话没有受权平台账号，未使用或伪造凭据，因此真实受保护目录内容和列宽待后续受权会话复核。
- `production-2.8.19`：与 TASK-251 同版发布；后端/前端镜像、备份、六服务健康、Nginx 与公网匿名边界均已复核。无受权平台账号，真实目录中的“已加入组织”列仍待人工复核。
- `main-regression`：合入后的 `mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 与 `npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts` 均通过（前端 1 文件 / 2 tests）。

## TASK-247 - 平台全量个人用户目录

- `backend-focused`：`mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过（2 tests）；覆盖全局账户目录查询、关键词裁剪和分页参数，服务仅调用不联结成员表的 `searchRegisteredAccounts`。
- `frontend-focused`：`npm test -- --run src/platform/pages/PlatformRegisteredUsersPage.test.ts src/platform/pages/platformTenantsShared.test.ts` 通过（2 files / 4 tests）；覆盖全平台目录文案与既有租户路由回归。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；构建仅有既有 Vite chunk-size warning。
- `browser-limit`：本地路由会按预期进入平台登录边界；本会话没有受权平台账号，未使用或伪造凭据，因此真实受保护目录桌面交互待后续复核。
- `main-merge/release`：主线合并提交 `38cb22e3a587`，annotated tag `2.8.15` 已推送。合并后 `mvn -q -Dmaven.repo.local=.m2 test`、前端定向测试、`npm run build`、Compose 配置和 `git diff --check` 均通过。
- `production-2.8.15`：backend/frontend ACR index digest 分别为 `sha256:8e4fc950102a0c1173c8e97c545358b28533d5fea0c98a0aca533ee7c1ffd81d`、`sha256:7e0bf4f0ed12ecd644630ead048953a5428395e32da9abdd1ddd73a55c2ff080`。备份 `/opt/cici/backups/20260724-222041-before-2.8.15-task247` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、版本 `2.8.15 / 38cb22e3a587`、Nginx 有效；`agentcici.com`、`agentcici.com/platform/registered-users` 与 `x.agentcici.com` 均 HTTP 200，匿名平台注册用户接口为预期 401。

## TASK-245 - 前台会话内置组织管理入口

- `identity/assignment`：MANAGER-001 的 SSH 身份、TASK-245 分支以及实现、设计、状态文件代表路径均返回 `allowed`；扩展 README、设计方案与 DESIGN.json 范围后已先提交并推送授权变更。
- `frontend-focused`：`npm test -- --run src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（3 files / 18 tests），覆盖助手管理员与 OWNER 会话接管、普通成员拒绝及后台导航守卫/主题既有回归。
- `frontend-build/static`：`npm run build` 与 `git diff --check` 通过；帮助中心和官网导航已移除旧的独立后台登录链接；构建仅有既有 Vite 大 chunk 警告。
- `browser-desktop`：1280×900 本地浏览器访问 `/admin/login` 直接重定向到 `/app` 的统一登录界面，控制台 error/warning 为 0。
- `main-merge-frontend`：合并 `origin/main` 后重新执行同一组 18 项定向测试与生产构建，均通过；仅保留既有 Vite 大 chunk 警告。
- `manual-acceptance-limit`：本会话没有真实组织管理员凭据，未伪造“管理后台”菜单的同组织进入、跨组织 `/auth/switch-company`、普通成员无入口/直达拒绝，以及后台返回后助手会话保留的端到端结果；这些为 review 阶段的真实会话验收项。
- `semattice-switch/frontend`：`npm test -- --run src/admin/adminAuthScope.test.ts src/admin/adminSession.test.ts` 通过（2 files / 6 tests）；覆盖产品下拉的 Semattice 端点、固定 fragment 跳转、菜单文案以及 OACT 不写入浏览器存储的静态边界。
- `semattice-switch/backend`：`mvn -q -Dtest=OfficialAccessTokenServiceTest test` 通过；覆盖 RS256 OACT 的 scope 投影、管理员最小 `audit.read` 补充及缺失统一身份绑定时 fail closed。
- `semattice-switch/build`：`npm run build` 通过；仅保留既有 Vite 大 chunk 警告。`git diff --check` 通过。
- `semattice-switch/manual-acceptance-limit`：没有使用或请求真实组织管理员凭据，未伪造 OACT 签发、Semattice 实际进入、普通成员 403 或浏览器返回 AgentCiCi 的端到端结果；这些保留为 review 阶段的真实会话验收项。
- `main-merge/release`：`ac598745e588` 已合并并推送 `main`，annotated tag `2.8.16` 已推送。合并后 OACT 定向测试、后端编译、前端生产构建、Compose 配置与 `git diff --check` 通过；前端仅有既有 Vite chunk-size warning。
- `production-2.8.16`：backend/frontend ACR index digest 分别为 `sha256:1b965955e81130e37f4001ab27bf33299219669f11f310cb0f8f425cafd5fcd8`、`sha256:a179fa0c7376f5849f4d46736e4527d7ec8031328b8d9027ffbc40b06a68f85e`。备份 `/opt/cici/backups/20260725-092810-before-2.8.16-task245` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、版本 `2.8.16 / ac598745e588`、Nginx 有效；`x.agentcici.com` 与 `agentcici.com` 均 HTTP 200，匿名 `/auth/me` 与 `/auth/semattice/console` 均为预期 401。
- `menu-fix/production-2.8.17`：`adminAuthScope.test.ts` 3 项通过，新增断言确保产品弹层使用 `left: 0` 且不再以 `right: 0` 锚定；前端生产构建和 diff 检查通过。backend/frontend ACR index digest 分别为 `sha256:e214c1f8f27f2a08832b35f8b7a17328e8179bb63653ce706009aeabee5f4cbd`、`sha256:17adbe6607c7b04d89201f2a3fd72b75853e0a421d7bd354f2dbb78d49a10f75`。备份 `/opt/cici/backups/20260725-104037-before-2.8.17-task245-menu-fix` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康，health `UP`、Nginx 有效、`x.agentcici.com` 为 200，匿名 `/auth/me` 与 `/auth/semattice/console` 均为预期 401。

## TASK-246 - 租户详情路由标识兼容修复

- `identity/assignment`：`dev-login.py .claw --task TASK-246 --branch codex/TASK-246-tenant-detail-route ...` 返回 `allowed`，SSH 身份、分支与四个前端实现/测试文件及任务文档范围均已验证。
- `frontend-focused`：`npm test -- --run src/platform/pages/platformTenantsShared.test.ts` 通过（3/3），覆盖租户目录和开户结果的旧 `orgId` 归一，以及 `undefined`/空标识不生成详情路由。
- `frontend-build`：`npm run build` 通过；仅有既有 Vite chunk-size warning。
- `static`：`git diff --check` 通过。
- `browser-limit`：本任务未使用或伪造平台运营账号；无效参数的请求前阻断由组件实现与路由标识定向测试覆盖，受登录保护的真实页面交互待合并后以受权账号复验。
- `main-merge/release`：`6cee975539e4` 已合并并推送 `main`，annotated tag `2.8.14` 已推送。合并后 `npm test -- --run src/platform/pages/platformTenantsShared.test.ts src/admin/adminSession.test.ts src/admin/adminNavigationGuard.test.ts src/theme/theme.test.ts` 通过（4 files / 21 tests）；`npm run build`、`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config` 与 `git diff --check` 通过。
- `production-2.8.14`：backend/frontend ACR index digest 分别为 `sha256:25e051c4bfb7f6f843bf595fec2163f3fc2c8790630be43474773c0cd7f06a0d`、`sha256:d118476d5b9967ee214336f115a987ca2b7d980fcdb1df28527bfe30ee41964d`。备份 `/opt/cici/backups/20260724-212057-before-2.8.14-task246` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务 healthy，`/actuator/health` 为 `UP`、`/system/version` 为 `2.8.14 / 6cee975539e4`，Nginx 有效；`agentcici.com`、`agentcici.com/platform/tenants` 和 `x.agentcici.com` 均 HTTP 200。

## TASK-244 - OIDC 统一入口 state 修复

- `identity/assignment`：`dev-login.py .claw --ssh-key ... --developer MANAGER-001 --task TASK-244 --branch codex/TASK-244-oidc-canonical-entrypoint ...` 返回 `allowed`；SSH 私钥持有、Git 身份、分支与三份实现/测试文件范围均已验证。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=KeycloakOidcLoginServiceTest test` 通过（3/3）；覆盖主站跳转至 callback 规范 host、规范/相似/畸形 host 判断，以及 callback state 不匹配仍 fail closed。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `user-acceptance-limit`：未使用或请求真实用户凭据；须由用户从 `agentcici.com` 发起真实 SSO，验证 Keycloak callback 生成一次性 ticket 并进入应用。
- `production-2.8.13`：`scripts/release-acr.sh --dry-run` 与正式构建/推送通过；backend/frontend ACR index digest 分别为 `sha256:66e929c6aaee94e2ed13aa09a643f6aef2bb44c3e42c256891091d566f11ff0e`、`sha256:c77614e4c6216fc329962f8c23c971b354caeedf69074f999534a4653c3a6591`。备份 `/opt/cici/backups/20260724-201945-before-2.8.13-oidc-canonical-entrypoint` 的 env、PostgreSQL、KB、Qdrant 均非空；仅重建 backend/frontend，六服务健康。后端返回 `2.8.13 / 877337078ea8`，Nginx 配置有效，`x` HTTPS 为 200；`agentcici.com/auth/oidc/login` 已 302 至 `x.agentcici.com/auth/oidc/login`，仅后者设置 `CICI_OIDC_STATE` 并跳转 Keycloak。

## TASK-243 - Keycloak 统一身份与官方应用访问

- `identity/assignment`：`dev-login.py .claw --developer MANAGER-001 --task TASK-243 --branch codex/TASK-243-keycloak-unified-auth ...` 返回 `allowed`；SSH 私钥持有、Git 身份、任务分支和认证/迁移/部署代表路径均已校验。
- `backend-focused`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OfficialAccessTokenServiceTest test` 通过（2/2）；覆盖 RS256 OACT 的 issuer/audience/公司/租户/scope/membership claims、JWKS 公钥投影以及无 Keycloak 外部身份绑定时 fail closed。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `frontend`：安装锁定依赖后 `npm run build` 通过；仅保留既有 Vite 大 chunk 警告。
- `tenant-applications-status`：`SematticeProvisioningServiceTest` 通过；覆盖已持久化 `PROVISIONED` binding 的读取和无 binding 时显式 `NOT_PROVISIONED`。前端生产构建通过，页面会在加载时请求受平台角色保护的状态接口，并将 `RESERVED` 渲染为“开通中”。
- `production-2.8.12`：`scripts/release-acr.sh --dry-run --version 2.8.12` 和正式发布成功；backend/frontend ACR index digest 分别为 `sha256:5bd8801e66e93bb8628c2e725f56bb8b1f9d1cda2b98df23dff2dc7fb31e9c4b`、`sha256:3126c5115587ef36e9eb82012a014166a8760877695c31b5e9a90c466d31ccea`。生产备份 `/opt/cici/backups/20260724-194153-before-2.8.12-semattice-status-fix` 的 env、PostgreSQL、KB、Qdrant 文件均非空；backend/frontend 均已切换 `2.8.12`，健康检查 `UP`，`/system/version` 返回 `2.8.12 / 6574f168234e`。真实 binding 读取为 `PROVISIONED|93ff0c87-a626-529e-b8cf-195825df2488`；新状态接口匿名访问为预期 `401`，公网首页为 `200`。
- `shared-environment-limit`：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OfficialAccessTokenServiceTest,AuthFlowIntegrationTest test` 的 17 个 AuthFlow 集成用例未启动，根因是共享测试库已有 Flyway V81 checksum 不一致（已应用 `2112500543`，本地 `379982424`）；TASK-243 未修改 V81、未执行 repair，独立 OACT 单元与编译结果不等同于完整认证集成通过。

## 主线合并验证 - TASK-240 / TASK-241

- `conflict-resolution`：TASK-240 是 TASK-241 的祖先，只合并后者即可包含两项功能；冲突均以主线 `company_id` 契约为准，未恢复 `org_id` 字段或接口。
- `focused-backend`：`AgentPlanExecCanaryServiceTest`、`AgentRuntimeModeRouterTest`、`AgentTaskReflectServiceTest`、`AgentRuntimeOperationsMetricsTest`、`SematticeProvisioningServiceTest`、`InternalHmacVerifierTest`、`TenantContextFilterTest` 与 `ChatOrchestratorServiceModelIdentityTest` 通过。
- `fresh-postgresql`：临时 PostgreSQL 16 从空库完整迁移 V1→V95；`AgentMemoryFlywayMigrationTest` 与 `AgentTaskRuntimeIntegrationTest` 5/5 通过，覆盖公司隔离的 Plan-Exec/Reflect、跨公司拒绝及 V94/V95 身份迁移。
- `environment-limit`：默认共享测试库仍因历史 Flyway V81 checksum 漂移无法启动；未修改 V81 或执行 repair，使用隔离库完成验证。

## Production Release 2.8.9 - TASK-242 / FEAT-135

- `migration`：新建后删除的 PostgreSQL 16 临时库先迁移 V1→V93，插入真实形状的 `agent_access_grant(principal_type='ORG')`，再迁移 V94/V95；断言 principal 为 `COMPANY`、`company_profile.company_size` 存在且无旧字段。
- `application-startup`：同一类全新 V1→V95 数据库以完整 AgentCiCi 应用启动，Hibernate schema validation 通过，`/actuator/health` 返回 `UP`。
- `production`：`2.8.9 / 0194706ffc7b` 后端和前端均运行健康；日志确认 V95 成功，版本与 health 正确。x HTTP 301、x HTTPS 200、生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 401；90 秒 backend `ERROR|Exception|Application run failed` 为 0。

## TASK-242 - 顶层租户 `company_id` 统一

- `identity/assignment`：`check-assignment.py .claw --developer MANAGER-001 --task TASK-242 --branch agent/TASK-242-company-id-unification --git-username OwenZheng-Cloud --require-developer-scopes` 返回 `allowed`。
- `backend/package`：`mvn -q -DskipTests package` 通过；包含 `company_id` 全量字段、Company 根实体/成员关系、JWT、平台生命周期和 Semattice reservation binding 的编译验证。
- `frontend/build`：`npm run build` 通过；仅有既有 Vite 大 chunk 警告。
- `fresh-postgresql`：新建后删除的 PostgreSQL 16 临时库执行 V1→V94，`AgentMemoryFlywayMigrationTest`、`TenantContextFilterTest`、`PlatformTenantLifecycleIntegrationTest`、`SematticeProvisioningServiceTest`、`AuthFlowIntegrationTest` 与 `RbacProductionReadinessIntegrationTest` 全部通过。断言 0 个 `org_id` 列、至少 131 个 `company_id` 列、根表为 `company` / `company_member`，并验证只带旧 `org_id` 的 JWT 被 401 fail closed。
- `static`：`git diff --check` 通过；迁移只新增 V94，未修改 V1–V93。临时数据库容器已删除。
- `state-limit`：`validate-state.py .claw` 仍被仓库既有历史状态文件格式、过期 Active Tasks、旧规格状态和值班索引超长阻断；本任务新增 FEAT-135 已使用合法 `verified` 状态及 UTC 时间，不修改无关历史档案。

## TASK-239 - 混合智能体运行时 P5：Trace 运行执行投影与多主题界面

- `identity/assignment`：MANAGER-001 的任务级 SSH 身份、任务分支及后端/前端/主题/状态代表性文件授权检查均返回 `allowed`。
- `backend-focused`：`AgentRunTraceServiceTest`、`AgentTaskReflectServiceTest`、`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 通过；覆盖精确运行关联、同组织回读、最小脱敏步骤证据、空态、P2–P4 与 Chat 回归。
- `fresh-postgresql-integration`：新建后删除 PostgreSQL 16 临时库完整迁移 V1→V92，`AgentTaskRuntimeIntegrationTest` 通过；临时数据库已删除。
- `frontend`：`AdminAgentRunMonitor.test.tsx` 3/3 通过，`npm run build` 通过；仅保留既有 Vite 大 chunk 提示。
- `browser-desktop`：使用受权组织管理员在新建后删除的 V1→V92 最小事实库登录 `/admin/ops`，完成关联 Trace 的运行总览、两步时间线、`gilded` 与 `galaxy` 同构主题、证据展开和“已复制脱敏后的详情内容”状态验收；两个主题均测得 `scrollWidth=1280`、`innerWidth=1280`、`overflow=false`。证据截图为 `output/playwright/task239-gilded-runtime-execution.png`、`task239-galaxy-runtime-execution.png`，均为忽略的本地验收产物。
- `browser-independent-observation`：同一最小库的既有审计日志面板调用 `/ops/audit/logs?limit=80` 返回 500，产生 2 条 console error；Trace 运行执行读取、展开与复制不产生错误。该接口不在 TASK-239 授权修改范围内，未掩盖或归因给 P5。
- `environment-limit`：默认共享 `agentcici_test` 的 Flyway V81 checksum 漂移未修复、未执行 repair；隔离库验证不等同于全量套件通过。

## TASK-238 - 混合智能体运行时 P4：受控 Reflect 与评测门禁

- `identity/assignment`：TASK-238 的任务级 SSH 身份门禁、分支与代表性实现、迁移、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentTaskReflectServiceTest`、`AgentEvaluationAssertionEngineTest`、`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 共 57 项通过。覆盖默认关闭/精确白名单、组织与 Agent 一致、成功计划/步骤、审查轮次、确认阻断、稳定模式/审查/零写入断言及既有 Chat 回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V92；`AgentTaskRuntimeIntegrationTest` 5/5 通过，确认 V92 审查记录、`REFLECT_GATE` 运行事件与跨组织拒绝。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`test-compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移（数据库 `2112500543`，本地 `379982424`）未修复、未 repair；隔离库验证不等同于全量套件通过。

## TASK-237 - 混合智能体运行时 P3：规则优先模式路由

- `identity/assignment`：TASK-237 的任务级 SSH 身份门禁、分支与代表性实现、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentRuntimeModeRouterTest`、`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 共 48 项通过。覆盖默认关闭和精确白名单回退、Direct/ReAct/Plan-Exec 的稳定原因码、确认续执行保留、敏感意图仅标记确认/风险、P2 未启动回退既有 ReAct，以及聊天模型身份回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V91；`AgentTaskRuntimeIntegrationTest` 4/4 通过，验证 Spring 完整装配与既有 P1/P2 运行事实。
- `backend-compile/static`：`mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`、`test-compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 的既有 Flyway V81 checksum 漂移（数据库 `2112500543`，本地 `379982424`）未修复、未 repair；隔离库验证不等同于全量套件通过。

## TASK-236 - 混合智能体运行时 P2：Chat/OpenAPI 受限灰度

- `identity/assignment`：TASK-236 的任务级 SSH 身份门禁与代表性实现、迁移状态、测试和治理文件范围检查均返回 `allowed`。
- `backend-focused`：`AgentPlanExecCanaryServiceTest` 与 `ChatOrchestratorServiceModelIdentityTest` 合计 44 项通过。覆盖默认关闭不创建运行、精确 Agent 匹配、固定 `RETRIEVE → SYNTHESIZE` 无工具计划、既有 Web 聊天回归。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库完整迁移 V1→V91；`AgentTaskRuntimeIntegrationTest` 4/4 通过，新增用例确认 P2 canary 的两步任务均成功、运行终态为 `SUCCEEDED` 且存在真实事件。
- `backend-compile/static`：`mvn -q -DskipTests test-compile`、`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `environment-limit`：默认共享 `agentcici_test` 仍在应用初始化前因既有 Flyway V81 checksum 漂移失败（数据库 `2112500543`，本地 `379982424`）；本任务未修改历史迁移或执行 repair，隔离库验证不等同于全量套件通过。

## TASK-235 - 混合智能体运行时 P1：计划状态机基础

- `identity/assignment`：MANAGER-001 的任务级身份门禁、分支与实体、服务、迁移、测试、状态文件代表路径授权均返回 `allowed`。
- `backend-compile`：`JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 通过。
- `fresh-postgresql-integration`：新建后删除的 PostgreSQL 16 临时库从 V1 全量迁移至 V91；`TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/agentcici_task235_test ... -Dtest=AgentTaskRuntimeIntegrationTest test` 通过，3 个测试、0 failure、0 error、0 skipped，覆盖依赖推进/事件、非法计划与过期版本、失效租约恢复。
- `shared-environment-limit`：默认 `agentcici_test` 在应用初始化前因既有 Flyway V81 checksum 不一致失败（数据库 `2112500543`，本地 `379982424`）；本任务未修改 V81、未执行 Flyway repair，隔离库验证不等同于全量套件通过。
- `static`：`git diff --check` 通过；临时数据库已强制断开连接并删除。

## TASK-234 - 发布修订版本号上限调整为365

- `versioning`：`bash scripts/test-release-versioning.sh` 通过，在临时 Git 远端验证 `2.8.364 → 2.8.365`、`2.8.365 → 2.9.1`、`2.12.365 → 3.0.1`，并拒绝 `2.8.366`。
- `dry-run`：`./scripts/release-acr.sh --dry-run --version 2.8.365 --production` 成功生成对应发布计划；`2.8.366` 被校验拒绝且错误提示声明修订段范围为 `1-365`。
- `static`：两个脚本 `bash -n` 与 `git diff --check` 通过；未执行生产发布。

## Production Release 2.8.5 - FEAT-131

- `release`：`scripts/release-acr.sh --dry-run` 与 `--version 2.8.5` 成功；backend/frontend ACR index digest 分别为 `sha256:0936e7b4d0e3040cf907284b7edc41dc891b1091b73d247e1be734e6c5870e30` 与 `sha256:abc3417bcb95f42897abe6ba32a00df7244e20aef3892f9e84875a8c776619ce`，Git annotated tag `2.8.5` 已推送。
- `backup`：线上备份 `/opt/cici/backups/20260723-115248-before-2.8.5-feat131-memory` 的 `acr.env.before-release`、`postgres.dump`、`kb-files.tgz` 与 `qdrant.tgz` 均非空。
- `production`：仅重建 backend/frontend，四个状态服务保持运行；六服务健康。后端 `/system/version` 返回 `2.8.5 / 02d380d10508`，Flyway V85–V90 均成功，Nginx 配置校验通过。
- `smoke`：`x` HTTP 301、`x` HTTPS 200、生产 IP/SNI 的 onechat HTTPS 200，匿名 `/auth/me` 为预期 401；稳定观察窗口 backend error 0、真实 Nginx 5xx 0。未持有受权生产测试账号或 API Key，因此未创建真实主体或调用受保护 OpenAPI 记忆路径。

## TASK-233 - 通用记忆人工管理与生产就绪审计

- `backend-focused`：11 个通用记忆定向回归、后端编译与 `git diff --check` 通过；覆盖可信上下文、受控语义检索、候选审核、OpenAPI 阻塞/流式、Trace/评测状态、两份独立适配契约、撤销/主体删除/过期和跨 Agent 拒绝。生命周期删除同时脱敏证据引用。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V90，并验证候选与已生效记录各自存在 `agent_id` 归属列。
- `fresh-platform-integration`：另一个新建后删除的 PostgreSQL 16 临时库执行 `PlatformTenantLifecycleIntegrationTest`，6/6 通过。实际写入 API 记忆绑定后，dry-run 计数、脱敏导出、real purge 与残留行校验均通过；绑定通过所属凭据的组织关系处理，不假定其存在 `org_id`。
- `environment-limit`：默认共享测试库的历史 V81 checksum 漂移仍未修复、未执行 repair；全新库验证用于隔离该既有环境问题，未将其表述为全量套件通过。

## TASK-232 - 通用记忆审核 API 与质量门禁

- `backend-focused`：候选审核、可信运行时 Trace、记忆 Flyway 与 `MEMORY_CONTEXT_STATE` 评测断言测试通过；V89 验证 `memory_candidate.agent_id` 存在，重复审核仍安全拒绝。
- `backend-compile/static`：后端编译与 `git diff --check` 通过。
- `adapter-contract`：`GenericExternalMemoryAdapterContractTest` 使用两份独立的通用凭据绑定验证应用、主体类型、命名空间和内部会话不串读；禁用绑定不进入可信记忆作用域。

## TASK-231 - 通用记忆生命周期与组织清理闭环

- `identity/assignment`：MANAGER-001 已通过 TASK-231 身份门禁与 memory/platform/迁移/测试/状态代表路径授权检查。
- `backend-focused`：`mvn -q -Dtest=MemoryLifecycleServiceTest,MemorySemanticRetrievalServiceTest,ExternalMemoryContextServiceTest,AgentMemoryFlywayMigrationTest test` 通过。覆盖主体删除立即撤销并脱敏、向量删除失败仍不可读取、过期清理、legal hold 阻断及既有授权回读边界。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V88，并断言通用记忆与凭据绑定表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。
- `platform-integration-baseline`：`mvn -q -Dtest=PlatformTenantLifecycleIntegrationTest test` 未进入用例：共享测试库已有 Flyway V81 checksum `2112500543` 与当前文件 `379982424` 不一致，Spring Context 启动即失败。该既有环境基线未被本任务改动；未修复历史迁移或执行 repair。

## TASK-230 - 受认证凭据记忆上下文绑定

- `backend-focused`：绑定测试锁定可信应用、主体类型、身份等级、命名空间和内部会话 ID 都来自服务端；客户端只能提供外部主体标识。空会话、绑定缺失或禁用均不进入记忆作用域；OpenAPI 阻塞/流式和可信作用域回归通过。
- `binding-governance`：受控配置服务验证凭据必须属于当前组织及 Agent，应用代码、主体类型、身份等级和命名空间逐项校验且规范化；重复配置更新同一绑定，禁用保留审计链并令运行时安全降级。读取、配置和禁用 API 与既有 OPENAPI 权限边界一致。
- `memory-isolation`：`ExternalMemoryContextServiceTest` 验证同主体不同应用、scope、时效和敏感级别的过滤；外部运行时只返回 `NORMAL` 记忆，`INTERNAL`/`SENSITIVE` 不会进入提示词。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V88，并断言凭据绑定表与六张通用记忆表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 与 `git diff --check` 通过。

## TASK-229 - 通用可信运行时记忆上下文

- `backend-focused`：可信上下文只在显式作用域内、组织与最终 Agent 同时匹配时组装提示词；作用域关闭后不残留。Trace 元数据只记录注入/数量/截断状态。`ChatOrchestratorServiceModelIdentityTest` 与语义检索回归共同通过。
- `backend-compile/static`：后端编译与 `git diff --check` 通过。

## TASK-228 - 通用记忆受控语义检索

- `backend-focused`：语义检索测试验证向量命中只有在关系库上下文已授权时才返回；未授权命中不会回读记录；邮件与令牌文本在 embedding 前脱敏，索引失败不向调用方传播。候选审核测试验证审核成功会触发最佳努力索引。定向测试与编译通过。
- `fresh-flyway`：新建后删除的 PostgreSQL 16 临时库从 V1 成功迁移至 V87，并验证 `memory_vector_fragment`。

## TASK-226 - 通用主体记忆 Phase 1 核心

- `identity/assignment`：MANAGER-001 的 SSH challenge-response、任务分支 `codex/TASK-226-agent-memory-core` 和 memory/迁移/测试/状态代表路径经 `dev-login.py` 与 `check-assignment.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=ExternalMemoryContextServiceTest,AgentMemoryFlywayMigrationTest test` 通过；覆盖跨 `applicationCode` 主体隔离、`SUBJECT_SHARED`/`CONVERSATION`/`AGENT_PRIVATE`/`DOMAIN_NAMESPACE` scope 过滤、已过期记录排除、只读上下文不隐式创建外部主体，以及提示词预算边界。
- `fresh-flyway`：使用仅由环境变量提供连接信息的新建 PostgreSQL 16 临时库，执行 `AGENT_MEMORY_MIGRATION_TEST_URL=... AGENT_MEMORY_MIGRATION_TEST_USERNAME=... AGENT_MEMORY_MIGRATION_TEST_PASSWORD=... mvn -q -Dtest=AgentMemoryFlywayMigrationTest test`；成功从 V1 迁移至 V85，并断言 `memory_subject`、`memory_record`、`memory_conversation_snapshot` 存在。验证后临时库已删除。为兼容既有 V81 非事务并发索引，测试显式关闭 PostgreSQL transactional lock，与项目集成测试配置一致。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `static`：通用核心、V85 和定向测试未出现外部应用或领域耦合标识；`git diff --check` 通过。尚未接入外部应用、Chat 编排器、向量索引或生产发布。

## TASK-227 - 通用记忆候选、证据与时效治理

- `identity/assignment`：MANAGER-001 的 TASK-227 身份门禁、任务分支与 memory/迁移/测试/状态代表路径通过 `dev-login.py` 和 `check-assignment.py`。
- `backend-focused`：`mvn -q -Dtest=ExternalMemoryContextServiceTest,MemoryCandidateGovernanceServiceTest,AgentMemoryFlywayMigrationTest test` 通过；候选不能以可读取状态提交，显式审核才会创建 `ACTIVE` 记录，重复审核被拒绝。
- `fresh-flyway`：新建且验证后删除的 PostgreSQL 16 临时库成功从 V1 全量迁移至 V86；断言主体、记录、会话快照、候选与证据五张通用记忆表存在。
- `backend-compile/static`：`mvn -q -DskipTests compile` 和 `git diff --check` 通过；未接入外部应用、自动长期写入、向量索引或生产发布。

## FEAT-131 - 通用外部应用智能体记忆平台（设计规格）

- `identity`：`MANAGER-001` 的 SSH challenge-response、Git 身份和本次规格/状态/验证记录路径经技能包 `dev-login.py` 验证为 `allowed`。
- `static`：已核对规格前置元数据、19 个设计章节、范围与交接说明；设计已更正为 Agent CC 面向任意外部应用的通用平台能力，FollowUp 仅为参考接入方。已跟踪文件及新增规格的 `git diff --check` 均通过。`validate-state.py .claw` 仍被仓库既有热状态超长、历史时区格式、终态任务仍在 Active Tasks、旧规格状态枚举和旧任务/assignment 格式等问题阻断；本次新增 FEAT-131 使用校验器要求的 `draft` 状态和 UTC 时间格式。该变更仅新增设计基线与项目状态，没有执行运行时代码、数据库迁移、前端构建、真实渠道或生产验证。

## TASK-225 - 对话技能选择的强制执行上下文与可观测性

- `identity/assignment`：`MANAGER-001` 的 TASK-225 SSH challenge-response、Git 身份、签名指纹和全部实现/测试/状态代表路径经 `dev-login.py` 验证为 `allowed`。
- `backend-focused`：`mvn -q -Dtest=AgentRunTraceServiceTest,SkillPromptAssemblerTest test` 通过；覆盖所选技能只注入自身业务流程与输出契约、其他业务技能不进入提示词，以及 Trace 保存请求/有效技能码、`FORCED` 状态、强制上下文原因和实际激活结果。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `frontend`：`npm test` 28 个文件、187 项断言全部通过；`npm run build` 通过，仅保留既有 Vite 大 chunk 提示。
- `browser/static`：本地桌面浏览器可加载应用且 console error 为 0；当前无组织用户授权会话，未冒充完成受保护的工作台/Trace 交互验收。`git diff --check` 通过。
- `release`：主线 `2f2f1a013ec2` 已合并并推送；`scripts/release-acr.sh --dry-run`、ACR backend/frontend `2.8.4` 构建/推送/inspect 和 Git annotated tag 均成功。backend/frontend index digest 分别为 `sha256:a173a2479309636f27f13fa5a0a2907f3b0893165f94a053c45dc19b50028002` 与 `sha256:0d94dc8d08d771a1297d09eb86f9d85834d68611a38b1a867cef7cd9e734e068`。
- `production`：备份 `/opt/cici/backups/20260722-102713-before-2.8.4-task225-forced-skill-context` 的 env/PostgreSQL/KB/Qdrant 均非空；仅拉取并强制重建 backend/frontend，四个状态服务容器 ID 未变化。六服务健康，health `UP`，`/system/version` 为 `2.8.4 / 2f2f1a013ec2`，Nginx 校验通过；x HTTP 301/HTTPS 200、生产 IP/SNI onechat HTTPS 200，匿名 `/auth/me` 为预期 401，稳定窗口无 backend ERROR。

## TASK-224 - 生产发布构造器注入启动热修

- `production-failure/rollback`：`2.8.2` 已推送且 V84 成功应用，但 `AuditService` 与 `PlatformAuditService` 存在两个未标注构造器，Spring 无法选择注入入口，backend 重启；已立即将 `acr.env` 与 backend/frontend 回滚到健康的 `2.8.1`，六服务 health 正常。
- `backend-focused`：`mvn -q -Dtest=AuditServiceSecurityTest,PlatformAuditServiceTest test` 通过。新增 `AnnotationConfigApplicationContext` 回归，验证两个审计服务均由 Spring 注入 `SecurityRedactionService`。
- `backend-package`：`mvn -q -DskipTests package` 通过。
- `frontend`：热修未改前端；main 前端树 `npm run build` 通过，保留既有 Vite 大 chunk 警告。
- `compose/static`：Compose 配置和 `git diff --check` 通过。
- `release`：`2.8.3` tag 指向 `651bc2294bee`；backend/frontend ACR index digest 分别为 `sha256:382e10658dd3d066e0add5cd98804cab8d48877bd1eec51342ea02a1bb08b46a` 与 `sha256:9dd889eb547d0dac2a2feabe05678fa22c634652e40f4380bd3b2372cdef43b0`。
- `backup/deploy`：备份 `/opt/cici/backups/20260722-095910-before-2.8.3-task224-startup-hotfix` 的 env/PostgreSQL/KB/Qdrant 均非空；仅拉取并重建 backend/frontend，四个状态服务保持运行。
- `production`：六服务均 healthy；`/actuator/health` 为 `UP`，`/system/version` 返回 `2.8.3 / 651bc2294bee`，V84 为 success，Nginx 配置通过。`x` HTTP 301、HTTPS 200；显式生产 IP/SNI 的 onechat HTTPS 200，匿名 `/auth/me` 为预期 401。切换窗口出现 3 条 upstream 未就绪日志，稳定后 backend 无 ERROR/构造器异常。

## TASK-223 - 定时任务周期解析越界修复

- `identity/assignment`：`MANAGER-001` 的 SSH challenge-response、TASK-223 分支和实现/测试/状态代表路径均通过 `dev-login.py` 与 `check-assignment.py`，0 finding。
- `backend-focused`：`mvn -q -Dtest=UserWorkflowServiceTest test` 通过，覆盖“每天 09:00”生成 `0 0 9 * * *` 且计算非空下一次执行时间，以及“每天下午 3点30分”生成 `0 30 15 * * *`。
- `backend-compile`：`mvn -q -DskipTests compile` 通过。
- `static`：`git diff --check` 通过；未执行生产发布或用户会话写入。

## TASK-222 - 本地遗留分支审查与主线整合

- `identity/assignment`：`MANAGER-001` 的 SSH challenge-response 与 TASK-222 代表文件/代码范围均通过 `dev-login.py` 和 `check-assignment.py`。
- `merge`：TASK-160、TASK-203、TASK-204、TASK-210 均在专用整合分支完成合并；冲突保留当前 `main` 时间线，历史 `.claw` 快照未回填。
- `frontend`：`npm test -- AgentBuilderShell.test.ts` 25/25 通过；`npm run build` 通过，保留既有 Vite 大 chunk 警告。
- `backend`：`mvn -Dtest=GlobalExceptionHandlerTest test` 2/2 通过，覆盖 `ResponseStatusException` 的 404/403 状态与消息映射；`MultitenantIsolationIntegrationTest` 主、测试代码编译成功，但启动 Spring 上下文时被共享测试库的 Flyway V81 checksum 不匹配阻断（数据库 `2112500543`，本地 `379982424`），未执行 repair。
- `script/static`：`python3 -m py_compile scripts/seed-demo-environment.py` 与 `git diff --check` 通过。
- `TASK-170 source`：安全规则分支的 `SecurityRedactionServiceTest`、`SafetyGatewayServiceTest`、`SecurityRulesServiceTest`、`AuditServiceSecurityTest`、`PlatformAuditServiceTest`、`ChatOrchestratorServiceModelIdentityTest` 定向测试及 `mvn -q -DskipTests package` 通过；前端生产构建通过。
- `TASK-219 source`：`theme`、`PlatformBillingPage`、`PlatformSkillsPage` 共 20 项前端定向测试与前端生产构建通过。
- `integrated regression`：TASK-170/TASK-219 合并后，同一组 7 个后端测试类共 56 项通过，`mvn -q -DskipTests package` 通过；上述 3 个前端测试文件共 20 项及 `npm run build` 通过。安全迁移由 V71 重编号为 V84，以匹配已到 V83 的主线迁移时间线；`git diff --check` 通过。

## TASK-218 - 厂商模型目录能力边界

- 授权：`MANAGER-001` 的 SSH 持钥、TASK-218 分支和后端、前端、测试、规格、状态文件范围均通过 `dev-login.py` 与 `check-assignment.py`。
- 后端：`mvn -q -DskipTests compile` 通过；全新临时 PostgreSQL 16 从空库成功应用 79 个迁移至 V83，`PlatformModelProviderIntegrationTest` 通过。覆盖 OneKeyToken 检测不回填样例模型、`models/fetch` 返回 `count=0`、空 `models/modelDetails`、`catalogSource=unavailable` 与 `remoteFetchSupported=false`。
- 前端：`npm test -- --run PlatformModelsPage.test.tsx` 2/2 通过，覆盖未开放远程枚举时的明确空态；`npm run build` 成功转换 1,948 个模块，仅保留既有大 chunk 提示。
- 静态检查：`git diff --check` 通过。未执行生产发布或远程凭据调用。

## TASK-221 - 组织管理端全页面主题一致性治理（本地验收）

- `identity/assignment`: `check-assignment.py` 从仓库根目录返回 `allowed`，确认 Admin 工具页、共享主题层和主题契约测试都在 TASK-221 授权范围内。
- `static-audit`: 覆盖 `/admin/*` 路由清单与共享浮层选择器。主题层将共享模态与遮罩、组织/用户弹窗、技能二级页/发布框/行菜单、业务本体工作台、运维与观测、嵌入应用和计费面板映射到当前 `--theme-*`；工具卡片不再存在内联类别渐变或固定色。
- `frontend-focused`: `npm run test -- --run src/theme/theme.test.ts` 通过，1 个测试文件 / 11 项测试；新增契约锁定 Admin 弹窗、折叠行菜单、二级页和工具卡的主题继承。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过。
- `browser`: 当前本地 Browser 无可用管理员认证态，无法进入 `/admin/*` 查看当前蓝色主题，未伪造截图或视觉结果。已登录管理员应按 FEAT-126 路由清单复核页面主体、弹窗、轻量菜单和折叠详情。

## TASK-220 - 用户会话工作台浮层与操作面主题收敛（本地验收）

- `identity/assignment`: `dev-login.py` 的 SSH challenge-response 以及 TASK-220 的 `check-assignment.py` 均返回 `allowed`，0 finding。
- `frontend-focused`: `npm run test -- --run src/theme/theme.test.ts` 通过，1 个测试文件 / 9 项测试；新增契约确认快捷指令菜单、弹窗、当前会话项和遮罩只走当前主题 token。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过；快捷指令与技能菜单、快捷指令弹窗、输入区操作、会话选中行和会话操作菜单均由 `--theme-*` token 覆盖，蓝色主题不再读取鎏金账房固定颜色。

## TASK-219 - 运营管理端信息架构与独立主题重构（租户应用中心）

- `frontend-focused`: `npm test -- --run src/theme/theme.test.ts src/platform/pages/PlatformBillingPage.test.ts src/platform/pages/PlatformSkillsPage.test.ts` 通过，3 个文件、20 项断言全部通过。
- `frontend-build`: `npm run build` 通过；Vite 保留既有大 chunk 警告，无 TypeScript 错误。
- `identity/assignment`: MANAGER-001 的 SSH challenge-response、GitHub 身份、TASK-219 分支与租户页面、样式、规格、任务状态和测试报告路径均经 `dev-login.py` 验证为 `allowed`。
- `frontend-build`: `npm run build` 通过，转换 1,949 个模块；TypeScript 无错误，仅保留既有 Vite 大 chunk 警告。
- `static`: `git diff --check` 通过。
- `browser`: 使用仅本机的脱敏 fixture 响应进入受保护 `/platform/tenants/org5nszpgj99jaysxv6y`，没有读取或写入生产。Playwright 在 `1920 × 1080`、`crm-blue` 主题下完成全页视觉检查，确认页面只保留租户身份与 AgentCiCi、Semattice 两张应用卡片，正文不存在“保留策略”“组织导出”或“预演与销毁记录”，且无横向溢出；点击 Semattice 开通后成功提示、运行中状态、已开通汇总 1→2 及已开通禁用态均正确。截图是本机临时证据，不纳入版本控制。
- `routing`: 点击 AgentCiCi 卡片进入 `/platform/tenants/org5nszpgj99jaysxv6y/applications/agentcici`，展示“AgentCiCi 应用生命周期”与原有保留、导出、预演、销毁治理；页面无横向溢出，返回按钮指向租户应用页。

## TASK-214 - OneKeyToken 实时凭据检测修复（生产发布）

- 授权：`MANAGER-001` 的本地身份门禁和 TASK-214 文件范围检查通过。
- 后端：`mvn -q -DskipTests compile` 通过；在全新临时 PostgreSQL 16 中运行 `PlatformModelProviderIntegrationTest#onekeyTokenCheckUsesUnsavedDraftCredentialsForLiveChatCompletionsValidation` 通过，覆盖草稿凭据、Chat Completions 契约、401 拒绝、非持久化和不回显密钥。
- 前端：`npm test -- PlatformModelsPage.test.tsx`（1/1）和 `npm run build` 通过；保留既有的大 chunk 警告。
- 编排与发布门禁：`docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config`、`git diff --check`、`./scripts/release-acr.sh --dry-run --production` 均通过；`2.7.12` 已被并发 tag 占用，脚本按规则生成 `2.8.1`。
- 镜像与标签：生产 `2.8.1 / 9bc8510cbede` 已推送。backend index `sha256:2a4526e84b7cff51e2b374c49012e5d8bc9cc4d4aef4e767a25e022aa65e6b0b`；frontend index `sha256:22b8c092ab27d4a51ccb65267758dcba0dd99a8448af1dc94e295cbc0b0f2c82`。
- 生产备份与部署：备份 `/opt/cici/backups/20260721-190903-before-2.8.1-task214-onekeytoken` 的环境文件、PostgreSQL、知识库和 Qdrant 归档均非空；只重建 backend/frontend。数据库、Redis、RabbitMQ、Qdrant 容器 ID 保持不变。
- 生产验收：`/actuator/health` 返回 `UP`，`/system/version` 返回 `2.8.1 / 9bc8510cbede`，Nginx 配置检查通过。首次仅使用基础 Compose 重建时未加载生产机既有 TLS 覆盖，443 映射短暂缺失；已用 `docker-compose.acr.ssl.yml` 与现有证书重新创建前端并恢复。frontend healthy，`x.agentcici.com` 与显式生产 IP 的 `onechat.agentcici.com` HTTPS 均返回 200；`POST /api/platform/models/providers/onekeytoken/check` 在未认证状态返回 401，证明新受保护路由已上线。

## TASK-217 - 智能体定时任务真实创建与链路事实纠偏（已发布）

- `backend`: `mvn -q -DskipTests compile` 通过。
- `backend`: `mvn -q -Dtest=ToolOrchestratorServiceTest,ChatOrchestratorServiceModelIdentityTest,AgentRunTraceServiceTest,AgentWorkflowRuntimeSkillGovernanceTest test` 通过，覆盖当前 Agent 上下文的定时任务工具暴露/分发、缺少周期时不触发模型或工具、原 CRM 调用链兼容、Trace 节点和 Skill 治理。
- `backend-full-diagnostic`: `mvn -q test` 未通过；共享本地测试库的 Flyway V81 checksum 与仓库不一致（数据库 `2112500543`，本地 `379982424`），导致 Spring 集成上下文无法启动。未执行 repair，聚焦回归不受影响。
- `static`: `git diff --check` 通过。
- `release`: `./scripts/release-acr.sh --dry-run` 通过；`2.7.12 / b20261d8b89b` 的 ACR backend/frontend 镜像可 inspect，Git annotated tag 已推送。backend index/amd64 为 `sha256:b2d1e4a053a6edadd6cdcefd481615a89258cd1821e02f3745f74031dd175b23` / `sha256:9b819a1b9949dd98d3db700bd36bacdeeef655be200f42288edb662ae089496b`，frontend 为 `sha256:a3a6ff9734bb3f7da648a2003159289d26b704f6927fd48b06f665b7e205b616` / `sha256:52a0228d143371ac9e6da0570e047d387ac227656af12bdcfbe8cbf644b5ea8b`。
- `production`: 备份 `/opt/cici/backups/20260721-190058-before-2.7.12-task217-runtime-trace` 的 env/PostgreSQL/KB/Qdrant 分别为 1,648 / 3,263,430 / 511,201 / 1,584,517 bytes，均非空。仅重建 backend/frontend，四个状态服务容器 ID 不变；六服务健康，health `UP`，版本 `2.7.12 / b20261d8b89b`，Nginx 有效，`x` HTTPS 200、显式生产-IP onechat HTTPS 200，发布窗口 backend/frontend error 和 Nginx 5xx 均为 0。
- `business-path`: 当前无组织用户授权登录态，未代用户创建测试任务；首次真实用户创建应核验非空 trigger 和 nextFireAt，并在下一次调度后核验 Tavily execution。

## TASK-214 OneKeyToken 实时凭据检测本地验收（2026-07-21）

- `identity/assignment`: MANAGER-001 的 SSH 持钥、签名指纹、GitHub 身份、TASK-214 分支与后端、前端、测试、规格和状态代表文件均经 `dev-login.py`、`check-assignment.py` 验证为 `allowed`，0 finding。
- `backend-focused/fresh-db`: 独立临时 PostgreSQL 16 从空库成功应用 79 个迁移至 V83；`PlatformModelProviderIntegrationTest#onekeyTokenCheckUsesUnsavedDraftCredentialsForLiveChatCompletionsValidation` 通过。用例验证草稿 Key 而非已保存 Key 用于 `POST /v1/chat/completions`、Bearer 鉴权、唯一 `x-request-id`、`onekeytoken/auto`、`stream=false`、401 拒绝与 Key 不回显；测试容器已删除。
- `backend-compile`: `mvn -q -DskipTests compile` 通过。默认共享测试库因既有 Flyway V81 checksum 不一致无法启动，未 repair 或修改共享库，改用上述隔离库作为真实测试证据。
- `frontend`: `npm test -- PlatformModelsPage.test.tsx` 1/1 通过，覆盖检测请求使用修剪后的未保存表单草稿；`npm run build` 成功转换 1,948 个模块。仅保留既有 Vite 大 chunk 提示。
- `browser`: 本地 Vite 与 Playwright CLI 在桌面浏览器打开 `/platform/models`，未认证状态正确重定向到平台登录页并完成可访问性快照；模型配置的认证态由 MockMvc 集成测试覆盖。无移动端范围。
- `static`: `git diff --check` 通过；无 Key、Authorization 值、完整上游响应或可复用凭据进入规格、测试断言、审计或状态文件。

## TASK-215 链路追踪全文查看与复制（2026-07-21）

- `backend`: `mvn -q -Dtest=AgentRunTraceServiceTest test` 通过；新 Trace 将 220 字节点摘要与最多 12,000 字的脱敏管理员详情分离，测试确认密码和手机号不进入可复制文本。
- `frontend`: `npm test` 17 个测试文件、88 项通过；`npm run build` 通过，仅保留既有大 chunk 提示。
- `browser`: 本地 `1280 × 720` 管理员 Trace 页面以受控响应验证默认摘要、原位展开/收起、详情滚动区、复制成功反馈和 keyboard 可访问名称；最终 console error/warning 为 0，未见横向溢出。截图：`.playwright-cli/page-2026-07-21T09-35-01-950Z.png`。
- `release`: `./scripts/release-acr.sh --dry-run` 与 `2.7.11` ACR 镜像构建/inspect 成功；annotated tag `2.7.11` 指向 `281f35b2cb2f` 并已推送。backend index/amd64 为 `sha256:65bf3b101a9ee915fddf656ea5ebe53bc29bf3d27b01504b2321f77f6fce4290` / `sha256:dc156302579d7b35730aadc883bf7fdd7491d87d5cf1d079fd3ad1fc78eeb33f`；frontend 为 `sha256:27c38b70972f9ba1436285ac6eead35fbf3b936facfdf703ca09bba3aa29d902` / `sha256:8e4ce653bb3c251e73be79a6446f79b0d35aa8a36db2d52a65b0a94c1bb7616f`。
- `production`: `/opt/cici/backups/20260721-181143-before-2.7.11-main-integration` 的 env/PostgreSQL/KB/Qdrant 分别为 1,648 / 3,264,738 / 511,201 / 1,584,517 bytes，均非空；backend/frontend 更新至 `2.7.11`，四个状态服务保持 `2.6.12`。六服务 healthy，health `UP`，版本 `2.7.11 / 281f35b2cb2f`，Nginx 有效，x HTTP 301/HTTPS 200，生产-IP-resolved onechat HTTPS 200。
- `production-browser`: 管理路由正确重定向到独立管理员登录页，console error/warning 为 0；当前会话没有管理员凭据，未把受保护 Trace 交互冒充为已在线重复验收。

## TASK-213 通用本体 V1 本地与生产验收（2026-07-17）

- `identity/assignment`: MANAGER-001 SSH 持钥、签名指纹、GitHub 身份、TASK-213 分支及本次 provenance 增量涉及的 18 个源码、V83、测试、规格和状态路径经 `dev-login.py` 与 `check-assignment.py` 校验均返回 `allowed`，验证项包括 developer record、持钥证明和 assignment scope，0 finding。
- `tdd`: 既有发布阻塞修复的 RED/GREEN 证据保持有效。终局 provenance 加固先让前端同一管理员、完全同元数据但 `MANUAL` 的工作区错误命中，并让后端因缺少指纹/来源字段编译失败；最小实现后聚焦测试转绿。数据库随后新增 MANUAL 携带包字段、REFERENCE_PACKAGE 空包 ID、63 位短指纹和 64 位大写指纹反例；旧 CHECK 如预期仅在空包 ID 用例失败，V83 增加非空包 ID 条件后四组反例全部转绿。指纹测试独立读取实际 classpath JSON 原始 bytes 计算 SHA-256，确认摘要、加载结果、安装落库和管理 API 使用同一 64 位小写值。V82 未修改。
- `frontend-full`: `npm test` 为 26 个文件 / 177 项全部通过；`npm run build` 成功转换 1,948 个模块，仅保留既有大 chunk 提示。
- `backend-focused/fresh-db`: 最终全新专用 PostgreSQL 从空库成功应用 79 个迁移至 V83；`flyway_schema_history` 中 V82/V83 均 `success=true`，ontology 表仍为 13 张。`OntologyPersistenceIntegrationTest` 16/16、`OntologyPlatformIntegrationTest` 14/14、`OntologyManagementServiceTest` 6/6、`OntologyReferencePackageServiceTest` 3/3，合计 39/39，0 failure / 0 error / 0 skipped。`mvn -q -DskipTests package` 通过。调试库、旧候选库和最终库均强制删除，`pg_database` 回读 `task213_provenance%` 为 0；未 repair 或复用共享测试库。
- `backend-expanded-final`: 签名提交 `d589ad1` 后另建 `task213_verify_d589ad1`，从零应用到 V83 并运行 10 个相关类：本体持久化 16、校验 9、编译 5、AI 提案 36、语义查询 22、CloudCC 适配 10、本体平台 14、租户生命周期 6、管理服务 6、参考包 3，合计 127/127，0 failure / 0 error / 0 skipped。测试后删库并回读该库计数为 0；同一 HEAD 再次通过前端 177/177、1,948 模块生产构建和后端 package。
- `browser-auth-timing`: 真实浏览器延迟组织资料响应 5 秒，在响应返回前退出；等待旧响应结束后仍位于 `/admin/login`，`cici_admin_token` 为 `null`，旧组织信息未重新出现。
- `browser-unmount-timing`: 真实浏览器延迟创建工作区响应，在 POST 已发出后确认侧栏离开；旧响应结束后仍位于 `/admin/data-quality`，工作区未重新挂载，记录到的 `/admin/ontologies/{id}/**` 后续请求为 0。
- `browser-compile-a11y`: 真实浏览器进入草稿修订 7 的技术预览，实际 POST body 为 `{"expectedRevision":7}`，响应 `sourceDraftRevision=7` 后展示只读契约；6 个工作区 tab 与 3 个技术 tab 的 `aria-controls` 均命中真实面板，非活动面板保留 `hidden` IDREF。1600×1000 截图完成，当前验证会话 console error/warning 为 0。
- `browser-mapping-galaxy`: 在 Galaxy 主题复现“技术预览 → 数据映射 → 删除映射形成脏状态 → AI 提案”：技术预览只读取 mappings（1 次 / catalog 0 次）；首次进入映射页继续读取完整 catalog（累计 mappings 2 次 / catalog 1 次）；删除后切 AI 页请求计数不再增加，页面继续显示“有未保存修改”，生成和应用提案均禁用并给出先保存映射提示。差异只显示 `业务对象“项目”`，不显示 `concept:project`。Galaxy 实际计算 warning 为 `rgb(230, 183, 95)`，1600×1000 的 document/body 横向溢出均为 0，console error/warning 为 0。
- `browser-final-d589ad1`: 全新 1600×1000 会话验证列表、领域向导、三节点两关系画布与检查器；删除映射后跨页签保留脏状态，校验/发布/AI 生成/应用均禁用；侧栏离开取消后仍留在 `/admin/ontology` 且脏状态保留，确认后才离开；技术预览 POST 为 `{"expectedRevision":4}` 且响应 `sourceDraftRevision=4` 被接受，全部 tab `aria-controls` 都命中真实面板；校验绿灯后才启用人工发布，发布弹窗默认焦点为“取消”，版本 1 不可变详情可读。验收会话 console error/warning、document/body 横向溢出均为 0。截图为 `output/playwright/ontology-v1/ontology-final-{list,wizard,workbench,technical,publish-confirm,versions}.png`。
- `static`: `git diff --check`、`jq empty DESIGN.json` 通过；暖色主题 warning `#7a4b00` 达到普通文本 4.5:1 门槛，Galaxy 使用主题 warning `#e6b75f`，在 canvas/surface/muted/strong/warning-soft 五类暗色背景上的对比度依次为 9.84 / 8.98 / 8.14 / 6.78 / 6.40:1。
- `state-validator`: `validate-state.py .claw` 仍因 130 条既有历史规格/任务基线 finding 退出 1；输出中 `TASK-213.md`、`FEAT-118` 与 V83 命中为 0，本轮未越界修复历史状态。
- `independent-review`: 最终安全与规格两路只读复审对 `d589ad1` 均返回 Approved，Critical 0 / Important 0。规格侧仅保留 mounted RouterProvider + deferred Promise 测试债，安全侧仅建议将修改/目录/发布扩展为参数化跨租户 404 测试；两项均为 Minor，不阻塞合并与发布。
- `merge/release`: PR #13 已合并为 `f922b86f1884ec5f7b7e1d97d3d0558202d0180f`；`./scripts/release-acr.sh --dry-run --version 2.7.10 --production` 与正式发布均成功，annotated tag `2.7.10` 已推送。backend index/amd64 digest 为 `sha256:096f480677944eb8e0f263e562155c771f4e72d0bee6731a82a3b162937c3644` / `sha256:cdaeb804cd645afe6fa2498b9f06f14c24b6a4b33d4f8d9a8f538e66e79056d5`；frontend 为 `sha256:0f96d20bdf1727fc8cf6da57c0b49af7f9a8c213a91709fe8183bef7ef66ed3b` / `sha256:4cfae678067c31d9794fe8e1bf5b8739d6b95dfb3fba5aaec8dd921aa3a7a2df`。
- `backup/deploy`: `/opt/cici/backups/20260717-154253-before-2.7.10-task213-ontology` 的 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,010,000 / 511,135 / 1,584,517 bytes；Nginx、Compose、状态与 SHA-256 清单也非空且校验通过。仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant ID 逐项与发布前完全一致并继续运行 `2.6.12`。
- `production-migration/runtime`: 生产 V82/V83 均 `success=true`，checksum 分别为 `-1084439350` / `-147714050`；ontology 表数 13，provenance 列、CHECK 与 `uq_ontology_workspace_org_key` 均正确。六服务 healthy，health `UP`，版本为 `2.7.10 / f922b86f1884 / 2.7.10`，Nginx 配置有效。
- `production-project-delivery`: 真实生产完成对象/字段发现、15/15 映射验证、候选编译、人工发布和重复发布幂等校验；不可变 v1 绑定草稿修订 6，发布详情不回显数据源配置/示例数据。explain 生成 `projects + contains-task` 计划；execute 返回 1 个“语义平台一期”项目与 2 条任务，证据版本/总数为 1/1。另一组织返回 404 `ONTOLOGY_NOT_FOUND`；查询审计包含 `REDACTED` 且不包含过滤值明文。
- `production-cloudcc-boundary`: `customer-operations` 在 `demo-org` 与真实 CRM 演示组织均以有效 package ID/64 位小写指纹安装为草稿；两名密码登录用户当前都不能取得有效 CloudCC 当前用户会话，对象发现各返回一次 `502 DATA_SOURCE_UNAVAILABLE`。草稿保持修订 1、未校验、未发布，失败未修改 CloudCC 或影响 INLINE_SAMPLE/手工建模能力。
- `browser-production`: Playwright CLI 在生产 1600×1000 验证列表、3 节点/2 关系画布、15 条已验证映射、候选 v2 技术预览、线上 v1/来源修订 6 版本历史和全部工作区/技术 tab IDREF；document/body 横向溢出与 console error/warning 均为 0。截图位于 `output/playwright/ontology-prod-2.7.10/ontology-{list,workbench-canvas,workbench-mapping,workbench-technical-json-schema,workbench-technical-graphql,workbench-versions}.png`。
- `stable-window`: 2026-07-17T08:11:52Z 至 08:19:52Z 共 480 秒、17 次 30 秒采样；六容器始终 healthy、restart 0、OOM=false，全部容器 ID不变，health/version 固定，backend 生命周期 `ERROR|Exception` 为 0。Nginx 最终恰好为上述两次预期 CRM discover 502，其他 5xx 为 0；08:20:47Z 最终语义查询仍为 HTTP 200、rows 1、关联任务 2、证据版本/总数 1/1。
- `dev-proxy-followup`: 合并后复核发现被跟踪的生成态 `frontend/vite.config.js` 未随 TypeScript 事实源提交；生产镜像构建时 `tsc -b` 已生成正确配置，线上不受影响。assignment 先在签名提交 `ef50ecc` 中扩展并推送授权，再机械同步生成文件；`npm run build` 转换 1,948 个模块成功，直接 `npm run dev` 请求 `/admin/ontologies` 命中代理并因本机 8080 未启动返回预期 proxy 500，而不是 SPA 200。
- `public/rollback`: `x.agentcici.com` HTTP 301 / HTTPS 200，匿名本体/语义查询返回 401；本机仍无法解析 `onechat.agentcici.com`，显式生产 IP vhost HTTPS 200。应用即时回滚点为 `2.7.9 / c04e992b3840`，V82/V83 可安全保留。

## TASK-201 智能体构建页右栏说明移除与双栏对齐增量验收（2026-07-16）

- `identity/assignment`: MANAGER-001 SSH challenge-response 登录、GitHub 身份、TASK-201 分支与前端源码/测试/样式/规格/状态代表路径均返回 `allowed`。
- `tdd`: 新增“右栏只承载系统提示词且不显示模型治理说明”的布局契约测试；旧实现先因仍包含说明节点与样式按预期失败，移除说明后聚焦测试 22/22 通过。
- `frontend-full`: `npm test` 为 18 个文件 / 110 项全部通过；`npm run build` 成功转换 1,938 个模块，仅保留既有大 chunk 提示；`git diff --check` 通过。
- `browser-local`: 应用内 Browser 在 1600×1000 验证左右编辑列均为 745.5px × 682.5px，top 均为 220.6953125、bottom 均为 903.1953125；系统提示词与左侧发布备注输入底边均为 897.1953125。模型治理说明节点为 0，document/body 横向溢出均为 0，console error/warning 为 0。
- `merge/release`: PR #11 合并为 `c04e992b38407097db448d52ea5c5e8b6473f7fc`；`scripts/release-acr.sh --dry-run` 与正式 `2.7.9` 发布均通过，annotated tag 已推送。backend index/amd64 digest 为 `sha256:420477ea503cb3f1bb6eb357b426d7e139d947427b5ba5cff46d168e02b9a3c5` / `sha256:74b3f03701058f07cace1504e20deaa5101dc16c88d6dd7ea549d24308e07c3a`；frontend 为 `sha256:7f5dddaad2846d83cfb102a4519860ea11dc571d21a0d79d0a83d227f185ae5a` / `sha256:86ba71f4985b2c880fdf90a81ddcfbc13803eab9585f77361df57cd4aad71b2e`。
- `backup/deploy`: `/opt/cici/backups/20260716-161644-before-2.7.9-task201-alignment` 中 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,009,740 / 511,135 / 1,584,517 bytes，全部非空。仅 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器 ID 与发布前完全一致并继续运行 `2.6.12`。
- `production-runtime`: 六服务 healthy，health `UP`，`/system/version` 返回 `2.7.9 / c04e992b3840`，Nginx 配置有效；发布后窗口 backend ERROR/Exception 0、frontend 精确 5xx 0。
- `browser-production`: 线上 1600×1000 “客户成功” Agent 编辑页显示版本 2.7.9；左右编辑列均为 612.5px × 604px，top/bottom 分别同为 227.1953125 / 831.1953125，系统提示词与发布备注输入底边同为 825.1953125。模型治理说明节点与文案均为 0，document/body 横向溢出均为 0，当前生产 console error/warning 为 0；截图为 `/Users/owenmacbook/.codex/visualizations/2026/07/14/019f5df9-6f52-7dd0-975c-cb6ad90d6d69/agent-builder-production-2.7.9/agent-builder-alignment.png`。
- `public/rollback`: `x.agentcici.com` HTTP 301 / HTTPS 200；本机仍无法解析 `onechat.agentcici.com`，显式生产 IP vhost 为 HTTPS 200。即时应用回滚点为 `2.7.8 / 4814d2b9534d`。
- `scope`: 未改变 `draft.model`、模型路由、API、数据库、主题 token、生命周期页签或移动端范围。

## TASK-212 Skill DAG Phase 1 本地与生产验收（2026-07-16）

- `tdd/backend-focused`: 先以缺失/脏版本引用、current 指针错配、标准边语义、平台 token 越权、运行时治理快照、Skill 版本变更指纹、历史显式/缺失版本、下游当前 KB/移交边界污染、1,001 条影响上限和 V81 重试安全形成红灯，再完成实现；9 个测试类共 22 项通过，0 failure / 0 error / 0 skipped。真实 `SkillResolverService` 与调试 Runtime 均断言 pinned runtime 不包含当前可变 Skill 边界。
- `http-security`: 真实 Spring/MockMvc 和本地 API smoke 均验证匿名 Agent 图 401、组织 token 读取 Agent 图 200、平台 token 读取平台图 200、平台 token 访问 Agent 图 403、组织 token 访问平台图 403；显式 `versionNo=1` 返回 200。示例 Agent 图为 5 节点 / 5 边 / 0 warning。
- `migration/performance`: 独立干净 PostgreSQL 从空库应用 77 个迁移至 V81；Flyway 明确以 `[non-transactional]` 执行。随后在两个索引已存在时重执行迁移 SQL，`DROP INDEX CONCURRENTLY` / `CREATE INDEX CONCURRENTLY` 全部成功，工作流引用与当前绑定索引最终均 `indisvalid=true / indisready=true`。两类影响查询最多读取 1,001 条并只展示 1,000 条，SkillVersion 使用组织内批量加载。
- `frontend`: `npm test -- --run` 为 18 个文件 / 110 项全部通过；`npm run build` 成功，仅保留既有大 chunk 提示。覆盖分层布局、关系详情、空态 warning、缩放适配、Agent/Skill 选择加载写门禁、目标操作进行中选择锁定、异步回写序号校验、请求快照和调试解析链。
- `backend-package`: `mvn -DskipTests package` 通过并生成可执行 JAR。
- `backend-full-diagnostic`: 独立数据库完整 Maven 诊断汇总为 341 项、3 failure / 7 error；失败位于既有 AutoService 平台身份、PlatformBilling 审计夹具、SkillGovernance 鉴权、AdminOrganizationProfile 非空字段、MeetingMinutes 模型配置及连接池耗尽后的 ChatSession/ModelProvider 上下文，不包含 TASK-212 聚焦测试，未作为全绿门禁。
- `browser-local`: 应用内 Browser 在 `1600 x 1000` 验证平台 Skill 影响图与 Agent Builder 的 Agent → Workflow Version → Skill → Skill Version → Tool 关系、`COMPILED_AS` / `PINS_SKILL_VERSION` / `USES_SKILL` 节点详情、缩放控制和调试 Skill 解析链；console error/warning 为 0，页面外层横向溢出为 0。
- `independent-review`: 三轮只读复审发现并推动修复历史显式缺失版本、影响查询与索引、前端正反向选择竞态及 V81 重试问题；最终复核 Critical / Important / Minor 均为 0，`Ready to merge: Yes`。
- `merge/release`: PR #10 合并为 `4814d2b9534d8ba70d560b1a8a9b9a3dbe390717`；`scripts/release-acr.sh --dry-run --version 2.7.8` 与正式发布均通过，Git tag `2.7.8` 已推送。backend index/amd64 digest 为 `sha256:4bbc96d6857236ade2122d98c038d70f15cb0148c852553f472631af93eca38e` / `sha256:f15bde1851cb45ee217147e1ce419a5c4d78c2b2390903f578c025c6c88d13b2`；frontend 为 `sha256:ceff96941ae9402a25cf0a28ec9b7c69a2bb4d4da44c9b6848db2934addc30cf` / `sha256:1ebecff3346837c879c041d7f9559f5ac9526791d82fb08ea18e5fd47f3ce056`。
- `backup/deploy`: `/opt/cici/backups/20260716-011129-before-2.7.8-task212-skill-dag` 中 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 3,007,782 / 511,135 / 1,584,517 bytes，全部非空。仅 pull/force-recreate backend/frontend；database `ce48f99872d8`、Redis `3c3879593463`、RabbitMQ `246a0aa352df`、Qdrant `96bf6c3cad9c` 与发布前 ID 一致。
- `production-runtime`: 六服务 healthy，health `UP`，`/system/version` 返回 `2.7.8 / 4814d2b9534d`；Flyway V81 成功，两条影响索引均 `indisvalid=true / indisready=true`，Nginx 配置有效。8 分钟稳定窗口 backend ERROR 0、frontend 精确 5xx 0。
- `production-api`: 真实生产 API 返回匿名 Agent 图 401、组织 token Agent 图 200、显式 `versionNo=50` 200、平台 token Agent 图 403、组织 token 平台图 403、平台 token 平台图 200；Agent 图为 24 节点 / 32 边 / 3 warning，平台图为 6 节点 / 9 边 / 4 warning，请求时延为 0.16-0.21 秒。
- `browser-production`: 应用内 Browser 在 `1600 x 1000` 验证 Agent Builder 与平台标准技能真实 DAG、缩放和节点详情。两页 `document.scrollWidth == clientWidth == 1600`，平台 main 无横向溢出，console warning/error 为 0；截图为 `output/playwright/task212-prod-agent-skill-dag-2.7.8.png` 与 `output/playwright/task212-prod-platform-skill-dag-2.7.8.png`。
- `public`: `x.agentcici.com` HTTP 301 / HTTPS 200；公共解析器仍无法解析 `onechat.agentcici.com`，与既有 DNS 风险一致，显式生产 IP vhost 为 HTTP 301 / HTTPS 200。
- `gates`: MANAGER-001 SSH 身份门禁、assignment 代表路径、Flyway V81、`git diff --check`、签名提交、PR 合并、生产备份、不可变发布、线上 API/browser smoke 与回滚点记录均完成；应用即时回滚点为 `2.7.7 / e47979167af8`，V81 索引可安全保留。
- `state-validator`: 全仓 `validate-state.py` 仍因 129 条既有历史状态/规格基线退出 1，但输出中 `TASK-212`、`FEAT-117` 与 V81 命中为 0；未在本任务中越界清理旧记录。

## TASK-211 2.7.6 失败回滚与 2.7.7 生产协议验收（2026-07-15）

- `2.7.6-go/no-go`: `2.7.6 / 2055947aae07` 的 SalesA 5 次内部 SSE 均为 133 个 delta、最大 18 字符、唯一 done 且与持久化精确一致；但 OpenAPI streaming 只有 2,342 字，blocking 为 2,383 字，41 个分片边界空格/换行丢失。临时 Key 撤销后返回 401，bindings 精确恢复，随后只重建 backend/frontend 回滚到健康 `2.7.5`，四个状态服务 ID 未变化。
- `openapi-tdd-hotfix`: 旧实现面对“尾随空格 + 纯空白 + 前导换行”片段稳定红灯；`deltaText` 改为 null-to-empty 且转发条件改为非空后转绿。两类聚焦测试 44 项、独立干净数据库 8 类 CRM 测试 135 项、前端 16 文件 89 项、1,936 模块生产构建、Compose、身份/assignment 与 diff 门禁全部通过；独立审查 Critical / Important / Minor 均为 0。
- `merge/release`: PR #7 合并为 `e47979167af8`，签名实现提交为 `eb5e1f7e4dc05f53943094e09289c54cd08d0056`；`scripts/release-acr.sh --dry-run --version 2.7.7` 与真实 release 均成功，Git tag `2.7.7` 已推送。
- `images`: backend index `sha256:315623e0ea90f087cf332acfc5b981efca91d493c814a0b8a2023a7b6433a475`、amd64 `sha256:9c6b10448df2a7f1bda6b37dfdaf09ec2eacc28bd050055afbf6150279af4ddc`；frontend index `sha256:515c760bc654c8e491a8914cf48a37397fe4c3200529b0df972d397e6b3f9f24`、amd64 `sha256:96d176f71a276962ba87be12f788ecf73c3d68009d7a9804077af12fa4a082ab`。
- `backup/deploy`: `/opt/cici/backups/20260715-091243-before-2.7.7-task211-openapi-whitespace` 中 env 1,646、PostgreSQL 2,925,720、KB 511,065、Qdrant 1,584,517 bytes，全部非空。只 pull/force-recreate backend/frontend；database、Redis、RabbitMQ、Qdrant 容器身份逐项与部署前一致。
- `runtime`: 六服务健康，health `UP`，`/system/version` 返回 `2.7.7 / e47979167af8`，Flyway 最新成功版本为 V80，Nginx 配置有效；`x` HTTP 301、HTTPS 200，生产 IP 显式解析的 `onechat` HTTPS 200。
- `salesa-stream`: 5 个 fresh SalesA 会话均为 `run → model → generating`、133 个非空 delta、最大 18 UTF-16 单元、唯一尾部 done；首末 delta 到达跨度依次为 2,398.082 / 2,395.707 / 2,398.149 / 2,395.634 / 2,394.114 ms，证明不是最终突发。每次 SSE 拼接与自身两条持久化消息逐字一致，五次正文仅归一化秒级“数据截止”后哈希唯一。
- `blocking/salesb/isolation`: SalesA blocking 与自身历史逐字一致；fresh SalesB SSE 同为 133 个 delta、最大 18 UTF-16 单元并与自身历史一致，和 SalesA 截止时间归一化后相同。SalesB 读取 SalesA 会话没有返回数据；因既有 `ResponseStatusException` 映射缺口响应为通用 500，已登记独立 issue，不作为数据泄漏或 TASK-211 流式回归。
- `openapi`: fresh 临时 Key 下 blocking 与 streaming 正文均为 2,383 字；streaming 为 3 个脱敏 `agent_thought`、133 个 `message`、最大 18 UTF-16 单元、唯一尾部 `message_end`。仅归一化动态截止时间后，streaming、blocking、各自 OpenAPI history 和内部协议正文完全一致，空格与换行无丢失。
- `cleanup/security`: 临时 Key 已撤销，复用该 Key 返回 401 `agent_api_key_invalid`；初始 ACTIVE Key 数与结束时一致为 0，临时 Key 无 ACTIVE 残留，channels/toolIds/knowledgeBaseIds 与 fresh 初始绑定逐字规范化一致。9 份用户答案及脱敏 thought 通过工具名、原始 JSON、内部字段、疑似 CloudCC ID 和敏感信息扫描。
- `business-depth`: 9 份答案都包含 Top 5 `X1 130 / G5 110 / S2 95 / MP 75 / PA 65`，金额冠军 MP 2,850,000，以及数量/金额贡献、环比、订单/客户覆盖、商机、合同、退货、建议动作、数据覆盖和“订单销售额不等同于财务确认收入”声明。
- `clean-logs`: 在另一个 fresh 133-delta、约 2.43 秒且持久化精确一致的成功会话窗口内，backend ERROR 0、CRM failure 0、异常断连 0、Nginx 精确 5xx 0；窗口含 179 条 Nginx 请求日志。
- `browser-evidence`: 应用内 Browser 恢复后，以 fresh SalesA 登录、fresh 工作台会话和 `CRM 经营分析` Skill 询问“嗯，销量最好的产品有哪些？”。正确的 partial 判据为“`直接结论` 已出现且 composer 仍 disabled”：此时同一 assistant 气泡可见文本 50 字；完成后同一气泡为 2,100 字、增长 2,050 字且 composer enabled。partial/final 截图已固化为权限 `0600` 的安全证据；console error/warning 为 0，html/body/workbench/layout/main/chat-panel/chat-thread 的 `scrollWidth` 均不大于 `clientWidth`。
- `browser-content/security`: 最终可见正文包含直接结论、产品 Top 5、经营诊断、前瞻信号、建议动作、口径与覆盖，Top 5 为 X1 130 / G5 110 / S2 95 / MP 75 / PA 65，金额冠军 MP 2,850,000，并包含收入声明；工具名、`tool_call/tool_result`、原始 JSON、内部字段、凭据和“等待确认”均未出现。最初尝试以气泡 `role=status` 与正文标题同时存在捕获 partial 属于无效探针，因为该 status 只在正文为空时渲染；源码只读复核和 `chatMessageState` 7/7 证明无需前端生产代码修改。
- `governance-gates`: TASK-211 SSH 持钥登录与 9 个变更文件的 assignment 检查均为 allowed，`git diff --check` 通过；将 TASK-211 从 Active Tasks 移入 Completed Tasks 后，全仓 `validate-state.py` 仍因 129 行既有历史状态/规格基线问题退出 1，但不再包含 TASK-211、FEAT-114 或本计划的 finding，未在本任务中越界修复。
- `final-recheck`: 完成前使用新建且验收后删除的独立 PostgreSQL 测试库重新执行 8 类 CRM/流式/OpenAPI 回归，合计 135/135 通过；`chatMessageState` 7/7 通过，TypeScript/Vite 生产构建成功并转换 1,936 个模块。生产六服务继续 healthy，health `UP`、版本 `2.7.7 / e47979167af8`；最终 10 分钟窗口为 backend ERROR 0、CRM failure 0、异常断连 0、frontend 5xx 0。较早宽口径窗口中的 500 均命中已独立登记的不可见会话状态映射问题，不属于 CRM 回答链路。

## TASK-211 CRM 确定性回答真实流式输出本地验收（2026-07-15）

- `tdd-red`: 新增多分片断言后，原实现的单方法回归按预期失败：1 项运行、1 failure，实际 `delta` 分片数为 1。
- `minimal-fix`: 生产代码只把 CRM 确定性分支的 `safeSendDelta(emitter, finalText)` 替换为现有 `safeSendDeltaInChunks(emitter, finalText)`；18 字/18ms 参数、blocking、持久化、格式化器、最终 LLM 和通用模型流式路径均未改变。
- `focused-green`: `ChatOrchestratorServiceModelIdentityTest,AgentOpenApiConversationServiceTest` 共 44 项通过；覆盖多 `delta`、单片上限、精确拼接、唯一尾部 `done`、多 `message`、唯一尾部 `message_end`、精确持久化、脱敏状态和最终 LLM 零调用。
- `crm-clean-db`: 独立干净 PostgreSQL 数据库上的 8 类 CRM 回归共 135 项通过，0 failure / 0 error / 0 skipped；默认共享测试库的既有 Skill v3 checksum 污染未被修改，也未作为绿色证据。
- `frontend`: Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建成功，转换 1,936 个模块，仅保留既有大 chunk 提示；无前端生产代码变更。
- `backend-full-diagnostic`: 新建数据库的完整后端诊断到达 Surefire 汇总 326 项，重现 5 failure / 2 error，来自既有平台身份夹具、审计字段、客户洞察、模型厂商/模型清单和旧非空字段夹具；随后在 Hikari 重试窗口人工终止，未作为通过门禁。TASK-211 两个测试类没有失败，定向 135 项保持全绿。
- `static/gates`: Compose config、`git diff --check`、TASK-211 身份登录和 assignment 检查均通过；签名实现提交为 `1e7fcc7a6228c19bad193bb46787fb8fb3bd5b2d`。
- `reviews`: 任务级审查同时批准规格符合性和代码质量；整分支最终审查为 `Ready to merge: Yes`，Critical / Important / Minor 均为 0。生产 `2.7.6` 的空白保真失败、回滚、TDD 热修、`2.7.7` 发布、协议验收与应用内 Browser 视觉证据均已完成，详见上节。

## TASK-208 生产发布与真实验收（2026-07-15）

- `ancestry`: 整合提交同时包含 TASK-209 `2.7.2 / ddcda0ef6111`、TASK-208 `2.7.3 / 85b92c2d1f63` 与当前生产 TASK-210 `2.7.4 / 3206fdbc196f` 三条不可变发布线；三次 `git merge-base --is-ancestor` 均通过。
- `content-preservation`: CRM 后端、内置 `crm-business-analysis` Skill、CRM 测试和受控迁移脚本与 TASK-208 `2.7.3` 树一致；完整 `frontend/` 与当前生产 `2.7.4` 树一致，TASK-209 原图登录资产保留。
- `backend-focused`: 8 个 Surefire 报告共 143 项通过，0 failure / 0 error；覆盖路由、五层经营分析、格式化、高阶工具、阻塞/SSE/OpenAPI 防泄漏和 CRM 数据契约。
- `frontend-full`: 合并后的锁文件执行 `npm ci` 后，Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建成功，共转换 1,936 个模块，仅保留既有大 chunk 提示。
- `identity/assignment`: MANAGER-001 SSH 持钥、GitHub 身份、TASK-208 当前集成分支与状态文件代表路径均为 `allowed`；TASK-209 前端与设计事实源由其已完成 assignment 覆盖。
- `release-guard`: `2.7.3` 从未部署；并发 TASK-210 以 `2.7.4` 上线并有意排除 TASK-208。最终分支将 `2.7.4` 作为生产父线并正向撤销其 TASK-208 revert，下一次只能发布新的不可变版本 `2.7.5`。
- `release`: PR #4 合入 `origin/main`，发布 `2.7.5 / be80eea665c0`。backend index/amd64 为 `sha256:0a79c77e5c9db8f4db00a7dc310264815de461c4caf9172d29cca062b29c1b1e` / `sha256:c99ec42f67abd451de6d2e6d371166b28850bfded128f687ccfd2d7c95ecd132`；frontend index/amd64 为 `sha256:056e4fd4a064134f3bacce6827a3dbd3206ef6a442d93b50c104e05dbc6c86f4` / `sha256:cd7477395e25d58cca96b2d08f86a7a30c579cb927ab98e94c918d9f34ec69c7`。
- `backup/deploy`: 发布前备份 `/opt/cici/backups/20260715-005545-before-2.7.5-task208-crm-analysis` 的 env/PostgreSQL/KB/Qdrant 分别为 1,646 / 2,862,193 / 510,994 / 1,584,517 bytes。只重建 backend/frontend；四个状态服务容器 ID 不变并保持 `2.6.12` healthy，应用健康 `UP`、版本提交一致、Nginx 通过。
- `crm-plan`: 写前 live dry-run 精确发现 12 产品、16 客户、24 商机、72 商机产品、16 合同、48 订单和 144 明细；计划 316 条更新、316 处 owner 变化、88 处 Account 重连、404 个字段变化，创建与重复均为 0，四项数据质量检查均通过。
- `crm-execute/readback`: 先生成 316 条受保护回滚清单，再对六类对象执行 update-only；执行后结构回读 12/24/72/16/48/144，第二次 live dry-run 为待更新 0、owner 变化 0、Account 变化 0、字段变化 0、创建 0、重复 0。
- `ranking`: SalesA 最近 30 天数量 Top 5 连续为 `智能巡检终端 X1 130`、`边缘采集网关 G5 110`、`安全监测传感器 S2 95`、`制造运营分析平台 MP 75`、`预测性维护应用 PA 65`；对应销售额 884000、1408000、304000、2850000、1690000，金额冠军为 MP。
- `salesa-sse/persistence`: Owen/SalesA 登录、组织和 CloudCC 连接均成功，`crm-business-analysis` 启用。5 个全新 SSE 会话均只有 `phase/delta/done`，Top 5 与关键数值一致；5 组持久化消息均为 user + assistant，落库正文与 SSE 正文逐字一致。差异仅为每次真实查询的数据截止时间。
- `blocking/openapi`: 内部 blocking 正文通过；OpenAPI 临时启用 api channel 并创建 SalesA run-as 标准 Key 后，blocking 与 streaming 正文均一致。`agent_thought` 仅保留 `AgentCiCi runtime completed/completed` 或“运行阶段已更新”的脱敏状态，不含工具、参数、记录 ID 或原始 observation。验收后 Key 已撤销，原 `wechat/dingtalk/feishu/web` 渠道精确恢复。
- `salesb`: CCAdmin/SalesB 管理员对照查询返回同一 Top 5；当前演示组织不存在第三个普通销售 persona，因此未制造或冒用额外身份，也未扩大 role/profile/sharingRule。
- `browser-production`: 生产桌面真实新会话输入同一问题，页面显示直接结论、产品 Top 5、经营诊断、前瞻信号、建议动作和口径覆盖；数量/金额冠军、贡献率、环比、订单/客户、商机与合同信号均可见。DOM 中工具名、`tool_call/tool_result`、原始 JSON 和“等待确认”均为 false；状态为“已完成本轮处理”，console error 为 0。
- `runtime`: 最终独立 blocking smoke 后干净窗口 backend error=0、精确 Nginx 5xx=0、CRM 分析 error=0。较早的全局日志包含客户端关闭 SSE 产生的 broken pipe、客户工作台既有 `customer_signal` 并发死锁一次，以及空白新会话 404 被全局处理器记为 500；均未影响 CRM 分析结果，且不属于 TASK-208 变更路径。
- `known-baseline`: 全仓状态校验器仍报告 `origin/main` 已存在的历史治理债务，例如旧完成任务仍位于 Active Tasks 与旧规格 front matter 漂移；TASK-208 代表文件、assignment、JSON、`git diff --check` 和最终交付状态单独通过，未宣称全仓历史基线转绿。

## TASK-209 运营平台登录页原图像素锁定（2026-07-15）

- `release`: `2.7.2 / ddcda0ef6111` 已上线；backend index `sha256:f4ec61fc0532be5593a4cc6c3646906d026770ee56e55b5aebdea936c1d29979`、amd64 `sha256:3403aad868f7f06d08c6b6ac685fafd8b4f39ef3a0f5ab36dcfe35deac8e562f`；frontend index `sha256:2ae803bf615cbb84bf7ddf451716b0f94df452c2d94e6936e01eacf59a18e918`、amd64 `sha256:21ef8d647026f1ffb361c82cfb3230770da8b8cf1098fa314e4cef5cd9538eda`。
- `production`: 备份 `/opt/cici/backups/20260715-001809-before-2.7.2-task209-reference-login`；backend/frontend 与四个状态服务均 healthy，运行版本和 Git 提交一致。
- `browser`: 生产 `1672 × 941` 默认态使用无损原图整页背景，透明交互层坐标对齐；无横向溢出，控制台 error/warning 为 0，输入后真实按钮可用且未提交假凭据。

## TASK-210 客户互动工作台标准渠道图标本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-210 SSH challenge 均为 `allowed`；客户工作台源码、样式、依赖、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `tdd`: 新增来源语义测试先以缺少 `timelineSourceKind`/`lifecycleSourceLabel` 失败，再实现转绿；重复 CRM 事件键测试先以缺少 `timelineItemKey` 失败，再实现转绿。
- `frontend`: Vitest 16 个文件、89 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示；`git diff --check` 通过。
- `production-baseline`: ECS 回读确认真实线上为 `2.7.2 / ddcda0ef6111`，不是仅存在 Git 标签的 `2.7.3`；发布分支已撤销 `2.7.3` 合并并合入 `2.7.2`，避免把未部署 CRM 分析改动捎带上线。
- `backend-baseline`: 在误合并 `2.7.3` 时完整后端套件暴露共享数据库重复账号夹具、内置 `crm-business-analysis` 版本漂移、历史非空字段夹具和连接池耗尽，共运行 325 项并出现 58 failure / 5 error；该合并已撤销。本次最终基线的后端 `-DskipTests package` 与 Compose config 通过，TASK-210 不改后端代码。
- `icon-source`: 微信渠道使用 Simple Icons 公开维护的规范路径和 `#07C160` 品牌色；电话、会议、邮件、CRM 任务、CRM 日程、客户反馈使用项目既有 Lucide 标准图标，不含自绘 SVG 路径。
- `browser-local`: 真实演示组织 CRM 数据在 `1600 × 1000` 桌面端加载；CRM 任务显示清单图标、CRM 日程显示日历图标；以只读请求拦截将一条现有记录标记为微信后，规范双气泡图标与实际轴线、日期和内容列共同通过视觉检查。证据：`output/playwright/task210-local-standard-icons.png`、`output/playwright/task210-local-wechat-standard-icon-detail.png`。
- `browser-console`: 完整时间线曾暴露重复 CRM event id 的 React key 错误；加入事件 ID、发生时间和行号组合键后，重新加载并展开完整时间线只有 React DevTools info，新增 error/warning 为 0。
- `release`: 统一版本 `2.7.4 / 3206fdbc196f` 已在生产运行，backend/frontend 与四个状态服务均 healthy；TASK-210 最终生产视觉证据仍由其任务持有人补录。

## TASK-207 前台主题一致性与视觉对齐（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-207 SSH challenge 均为 `allowed`；前端、设计事实源、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `tdd-focused`: 先验证组织首字符函数、四套主题序列色、数据图 class 和智能体身份色旧内联样式的失败状态，再实现转绿；最终聚焦测试 10 项通过。
- `frontend-full`: Vitest 15 个文件、85 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示。
- `static`: `DESIGN.json` JSON parse、`git diff --check` 均通过。
- `browser-eight-themes`: 真实本地账号逐一应用 `gilded`、`crm-blue`、`ocean`、`sakura`、`lavender`、`avocado`、`wine`、`galaxy`；根主题、画布、设置面板和轨道表面均正确切换，验收后恢复 `gilded`。
- `browser-pages`: 在 `1600 × 1000` 下验证助手工作台、智能体/会话层级、AI 应用菜单、客户互动工作台、互动整理弹窗、数据洞察、客户洞察、知微画像、个人设置、专属记忆和 CRM 外层壳；`gilded`、`sakura`、`galaxy` 重点表面均读取对应主题 token。
- `browser-layout`: 数据看板四列闭合，同一行卡片同顶同高；AI 应用菜单五行均为 44px 且左锚点一致；互动弹窗左右栏同顶同高；`document/body scrollWidth == clientWidth == 1600`。
- `browser-org`: 当前组织 `CloudCC 智能体应用DEMO` 的左下角入口显示 `C`，不再显示固定 `CB`。
- `browser-console`: error/warning 为 0。截图证据见 `output/playwright/task207-*.png`，设计结论见 `design-qa.md`。

## TASK-206 CloudCC 嵌入身份同步自动恢复（2026-07-14）

- `identity/assignment`: MANAGER-001 通过 TASK-206 assignment 检查；pagecomponent 源码、预构建 bundle、配置、测试与任务状态均在授权范围内。
- `backend-focused`: `CloudccAccessTokenServiceTest,CloudccOpenApiServiceTest` 共 6 项通过；覆盖 `/api/user/getUserInfo` GET、`accessToken` 会话头、actor/org 提取和失效会话拒绝。
- `backend-full-baseline`: 全量测试完成但存在既有非 TASK-206 失败，集中在停用模型厂商、`onekeytoken` 历史配置、非空 `source_type` 旧夹具和平台技能/租户生命周期预期漂移；TASK-206 聚焦测试全部通过。
- `frontend-focused`: `CloudccEmbedSso.test.ts` 通过 7 项；明确锁定源码和发布 bundle 只能调用 `getToken()`，不得调用需要 clientId/secretKey 的 `getOpenApiToken`。
- `frontend-full`: `npm test` 通过，14 个文件、80 项；`npm run build` 成功，仅保留既有 Vite 大 chunk 提示；UMD `node --check` 与 `git diff --check` 通过。
- `cloudcc-package`: `cloudcc package pagecomponent customer-workbench . --dry-run` 通过，确认使用 `frontend/build/customer-workbench.umd.min.js` 且不打包本地凭据与状态文件。
- `cloudcc-publish`: 通过 `cc-customization-expert-msapi 2.1.279-msapi` 发布 pagecomponent V15，ID `6a5628cee4b0a577cbba2088`；customPage dry-run 后更新为 V9，精确引用该组件、`embedded=true` 和生产工作台 URL。
- `cloudcc-readback`: `verify injectionPage` 回读的组件 ID、名称、customPage V9、嵌入标记和 URL 均精确匹配；因接口未返回 `actualVersions` 保留已知 `stale_component_reference` warning，真实 CRM 三轮 HTTP 200 SSO 验收作为运行时版本证据。
- `release`: `2.6.11 / c540988655cb`；backend index `sha256:9be1120bc9a26e507068d75fbd5c9eb6db0e61ef24dc3785be9e9f8330bb5f4b`、amd64 `sha256:3694fa2545aeb136c234e9cc2ab7df64f684720f21b2ea25c424ed120eb82e69`；frontend index `sha256:ba57516fe20e08574f6b029e75f191cfb812caae29f8029454d1d981439822c5`、amd64 `sha256:4752c464acca6c864afda592e6769345173b4497ce9f0634a7f0e62168ba1079`。
- `production`: 备份 `/opt/cici/backups/20260714-202718-before-2.6.11-task206-cloudcc-session-sso` 四类文件非空；六服务 healthy，健康 `UP`，运行版本与 Git 提交一致，Nginx 配置有效，`x` 工作台和 `onechat` 生产 IP smoke 均为 200。
- `browser-production`: 真实 CloudCC CRM 注入页首次加载和连续两次刷新均显示“CloudCC CRM 已连接”、`CCAdmin / 组织管理员`、客户队列和详情数据；三次 `/ticket` 与三次 `/consume` 全部 HTTP 200，账号映射失败提示为 false，后端同期无会话验证拒绝或 ERROR。

## TASK-205 CRM 经营分析与高仿真销售数据生产验收（2026-07-14）

- `identity/assignment`: MANAGER-001 的 TASK-205 代表文件授权检查为 `allowed`；`git diff --check`、离线种子契约和后端打包通过，未包含用户 `diagrams/` 或任何凭据。
- `backend-focused`: `CrmProductSalesAnalysisServiceTest,CrmProductSalesAnalysisToolServiceTest,CrmProductSalesIntentRouterTest,ToolOrchestratorServiceTest,CrmAnalyticsDemoDatasetContractTest,FileBackedBuiltinSkillIntegrationTest` 共 17 项通过，0 failure / 0 error；路由测试按 TDD 先观察到缺类编译失败再转绿。
- `backend-full-baseline`: 全量 274 项运行完成，15 failure / 3 error；均来自既有认证夹具、平台模型/技能配置漂移、停用模型厂商、历史非空字段夹具和 PostgreSQL 连接数耗尽，与 TASK-205 定向测试无关。
- `crm-write/readback`: 默认 dry-run 后显式 execute 写入批次 `TASK-205-CRM-ANALYTICS-DEMO-V1`；第二次 execute 创建数均为 0。最终回读为产品 12、客户 16、商机 24、商机产品 72、合同 16、订单 48、订单产品 144，4 张当前草稿高销量订单被排除，关联完整性和销量/销售额排行差异检查均通过。
- `crm-ranking`: 最近 30 天有效订单销量 Top 5 为 `DEMO-X1 130`、`DEMO-G5 110`、`DEMO-S2 95`、`DEMO-MP 75`、`DEMO-PA 65`；对应销售额为 884000、1408000、304000、2850000、1690000。
- `runtime-correction`: `2.6.7` 首轮真实会话暴露发布版 `cici-system` 未锁定新 Skill，模型仍探测原子对象；新增确定性意图门并将 `crm-business-analysis` 纳入 `cici-system` 发布版本 3 后，以不可变版本 `2.6.8` 修正。`2.6.7` 不作为回滚目标。
- `production-chat`: 5 个全新会话同问“嗯，销量最好的产品有哪些？”均包含同一 Top 5、最近 30 天、销售数量口径、截止时间和 `product/cloudccorder/cloudccorderitem` 来源；服务器日志恰好 5 次 `crm_product_sales_rank` 且均为 `skill_scoped`，无原子 CRM 工具调用。
- `release`: `2.6.8 / 095094300a25`；backend index `sha256:27c985366695339a298ad3f6a333cd03827fc08fc334f9f1161242f584b7f2aa`、amd64 `sha256:ea08a7a86b8c64aa565ceef1ce768b0af367550e081a3ad6781d078b23811265`；frontend index `sha256:784504e1a57a5463d722a74941b0a15085ebf04bf2be08cef276cdb8eadfca0c`、amd64 `sha256:277a476b3cf0c1b495ab8202f3380674af0119794e7898172ce4dcda2964ed4f`。
- `production`: 最终备份 `/opt/cici/backups/20260714-184006-before-2.6.8-task205-deterministic-routing` 四类数据非空；六服务 healthy、健康 `UP`、版本一致、V80 无迁移、Nginx 有效，稳定窗口 backend error 与精确 Nginx 5xx 均为 0；`x` HTTPS 200/HTTP 301，`onechat` 生产 IP smoke 200。

## TASK-202 用户级产品主题偏好本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-202 SSH challenge 均为 allowed；后端认证、V80、前端主题模块、设计事实源、规格和状态代表文件通过 assignment 检查。
- `backend`: 在独立 PostgreSQL schema 上从空库执行 76 个 Flyway 迁移并到达 V80；`AuthFlowIntegrationTest,PlatformAuthIntegrationTest` 共 22 项，0 failure / 0 error，覆盖普通账号与平台账号的默认主题、保存、刷新/切换组织持久化和非法代码拒绝。共享测试库存在历史固定手机号夹具污染，未作为最终证据源。
- `frontend`: 主题视觉修复后 Vitest 13 个文件、73 项通过；TypeScript/Vite production build 通过，仅保留既有大 chunk 提示；`git diff --check` 与 `jq empty DESIGN.json` 通过。
- `browser-settings`: 真实桌面浏览器的“界面主题”展示八个标准选项，逐项即时预览并显示同步状态；浅色主题保持可读性，星河是唯一深色主题，布局、控件尺寸和交互结构不随主题变化。
- `browser-cross-shell`: 主应用、管理端、运营平台和客户互动工作台均读取共享语义令牌；星河运营平台最初发现硬编码浅色卡片导致低对比，改为公共 `platform-*` 令牌后复验通过。平台账号刷新后仍恢复星河，普通账号跨管理端读取红酒主题成功；验收结束后本地演示账号恢复鎏金默认。
- `browser-visual-hotfix`: 2048×1152 真实浏览器逐项检查八主题。智能体栏、会话面板、右侧栏、指标组和当前状态泳道均透明、无阴影、无变换；智能体头像固定 42×42、无阴影/缩放；八主题外层横向溢出均为 0，控制台 error/warning 为 0，验收后恢复鎏金主题。
- `design-qa`: 原版、问题版与修复版完成同图全景和聚焦对比，`design-qa.md` 最终结果为 `passed`。
- `release`: `2.6.6 / 4caaa4800b3d` 已上线；backend index `sha256:040c77eb89d4ee06b4e7ac615fa1e9bb44a4aecaf3f34a9453aa323c6351b20c`、amd64 `sha256:a57d540cab963a8c108b40471ef0a7cb025dc95aa8cdcc2f06db327ed0caa399`；frontend index `sha256:b8bed46b93bbcba24e9ad3e5face8ede291cb013a28f28de323579c1c6857982`、amd64 `sha256:efb42859509f6ebfe2bf58daa93d2af9bf8aa7ad25568e7915816b347892638d`。
- `production`: 备份 `/opt/cici/backups/20260714-142848-before-2.6.6-task202-theme-visual-hotfix` 四类数据非空；六服务 healthy、健康 UP、Flyway V80 成功、Nginx 有效、稳定窗口错误扫描为 0。生产浏览器显示版本 2.6.6，头像悬停前后均为 42×42、无阴影/变换，结构层透明、外层溢出 0、控制台错误 0。

## TASK-201 智能体构建页布局与模型治理本地验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用与 TASK-201 SSH challenge 均为 allowed；Builder 源码、样式、测试、规格、任务状态和测试报告代表文件通过 assignment 检查。
- `frontend-focused`: `npm test -- src/assistant/AgentBuilderShell.test.ts` -> 1 个文件、14 项通过，覆盖生命周期页签顺序、评测/渠道语义隔离、平台模型治理提示和既有模型默认解析。
- `frontend-full`: `npm test` -> 12 个文件、68 项通过；`npm run build` -> success，仅保留既有 Vite 大 chunk 提示；`git diff --check` -> success。
- `browser-definition`: 本地真实 1280x720 管理后台打开“客户成功” Agent；定义区左右栏均为 452.5px × 687px，起止边界一致；头像 58px、上传/清除与四个 56px 策略按钮处于同一视觉行；Builder 与主区域 `scrollWidth == clientWidth`，无横向溢出。
- `browser-model`: Agent 定义区 `基础模型` 文本不存在、`selectCount=0`；只读说明明确运行模型由平台统一策略自动选择，内部 `draft.model` 和新建 Agent 默认模型解析未删除。
- `browser-lifecycle`: 下方“版本控制与交付”依次包含流程图预览、触发与调度、试运行、评测、版本历史、发布渠道、执行记录、编译摘要、流程代码、Manifest；评测内容和企微/钉钉/飞书/Web/Open API 渠道内容分别打开，active/focus 使用文本与金色下划线，无按钮框、阴影或横向溢出。
- `browser-console`: error/warning 为 0；本地后端以 local profile 启动，Flyway V79 up to date，登录和 Agent 数据均来自真实本地 API。
- `release`: 未执行生产发布；当前证据仅覆盖本地实现与桌面端验收。

## TASK-200 多租户智能体评测控制面生产验收（2026-07-14）

- `identity/assignment`: MANAGER-001 通用和 TASK-200 SSH challenge 均为 allowed；状态、规格、后端/前端、V79 与两个 Nginx 配置均通过 assignment 检查。
- `backend`: `AgentProductionReadinessIntegrationTest,AgentEvaluationControlPlaneIntegrationTest,AgentEvaluationAssertionEngineTest,RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,PlatformGovernanceIntegrationTest,AgentRunTraceIntegrationTest` 共 20 项，0 failure / 0 error。
- `frontend`: 12 个 Vitest 文件、67 项通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk 提示；Compose config 与 `git diff --check` 通过。
- `migration/runtime`: 生产从 V78 正向迁移到 V79 `agent evaluation control plane`，`success=true`；六服务 healthy，后端 `/actuator/health=UP`，`/system/version=2.6.4 / d88f4293759f`，Nginx 配置有效。
- `production-api`: demo-org 的 `/evaluation/overview|suites|runs|issues` 均返回 JSON success；平台 `/platform/evaluation/overview|suites|runs` 均成功且平台标准套件为 1；租户访问平台接口与平台账号访问租户接口均返回 403。
- `production-browser`: 1280x720 下租户“AI 质量”、Builder 独立“评测”Tab、仅含飞书/钉钉/企微等入口的“发布渠道”、平台“智能体质量”均通过；页面显示 `2.6.4`，无横向溢出，console error/warning 为 0。
- `release`: 最终 `2.6.4 / d88f4293759f`；backend index `sha256:58983c43796896d05dc4a07059dedf1d10d26cdb6413567e7056e771a77b0388`、amd64 `sha256:fe378b7652eb52a3c2b58e3d43dfc68c00bbe16d3fa44d4011eea3aec0e5c846`；frontend index `sha256:0ffa36646860570eabe0f21cfe28514d2450608a11e981f04184971689fd2f90`、amd64 `sha256:187b2b7c3a13b518cea186187cc8e7e2a09dd7fc24a8b6b9b71cef4d54f33582`。
- `release-correction`: `2.6.3` 首次 smoke 发现评测 API 被生产 Nginx 当作 SPA HTML，补齐只代理带尾部子路径的 `/evaluation/*` 与 `/platform/evaluation/*` 后以不可变新版本 `2.6.4` 替代；`/platform/evaluation` 页面继续返回 HTML。`2.6.3` 不作为回滚目标。
- `backup/ops`: 最终备份 `/opt/cici/backups/20260714-075215-before-2.6.4-task200-nginx-hotfix` 六项非空；初始迁移前备份 `/opt/cici/backups/20260714-074613-before-2.6.3-task200-agent-evaluation` 四项非空。稳定窗口 backend error=0、frontend 5xx=0；x 域名 HTTP 301/HTTPS 200，onechat 生产 IP 解析 smoke 200，本机 DNS 空结果风险保留。

## TASK-197 客户互动档案、动态记忆与按需检索生产验收（2026-07-12）

- `backend-focused`: `CustomerMemoryServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest,CustomerCrmProjectionServiceTest` 通过，覆盖 90 天窗口、历史扩展、指定档案优先、结构化记忆生成、确认幂等和 CRM 投影兼容。
- `frontend`: 12 个测试文件、64 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。
- `full-backend-baseline`: V76 类型契约修正后 Spring/Flyway/JPA 启动通过；共享测试库全量套件仍有 16 个失败、2 个错误，来自既有账号/夹具污染、平台模型 `onekeytoken` 配置漂移、停用模型厂商和历史技能名称差异，与 TASK-197 定向套件无关。
- `production-api`: 演示组织真实客户“北京智造科技有限公司”返回 10 条时间线、1 条可追溯档案和 `crmConnection.ready=true`；档案包含 59 字确认稿、1 个原件、完整结构化分析和 10 条 ACTIVE 客户记忆。
- `assistant-context`: 普通风险问题返回 7 条证据、10 条近期互动、8 条 ACTIVE 记忆，`historyRequested=false`，其中 1 条证据可直接打开互动档案；回复正文使用 `[E1]` 等编号引用事实。
- `production-browser`: 版本 `2.5.11`，全量客户搜索命中北京智造；时间线“查看档案”打开确认记录、AI 分析、原始材料三个页签，原件在独立 Blob 标签页显示，最终控制台 `0 errors / 0 warnings`。截图：`output/playwright/task197-prod-archive-final-2.5.11.png`。
- `release`: `2.5.11 / d0ed7e4129cf`；backend index `sha256:d4ba55523711a534ce7ef37c676d8eb8505c27a6497b1a4363f675f59d0aeec9`、amd64 `sha256:a8793ab297a0a74cbde806ad29f739673a802ec069027d34355b8702d0b6fecb`；frontend index `sha256:9e154f5c605ccfbb999297f9e9f3a1935af86893781bfe15947069a4c78e2a89`、amd64 `sha256:a03f195ed0397a0484567b8fe7f403632c1d0e5a1e6b38224f87bb6ab373ca32`。
- `operations`: 最终备份 `/opt/cici/backups/20260712-143215-before-release` 四类文件非空；backend/frontend `2.5.11` healthy，健康检查 `UP`，V76 已在前一发布成功执行且重启无迁移错误。

## TASK-196 客户互动整理上下文稳定性生产验收（2026-07-12）

- `root-cause`: 互动确认调用 `queue?refresh=true` 触发 10,000 Account 全量投影；队列回读把不在当前页的已选客户替换为首条；弹窗使用动态 Account；助手又把普通“老客户经营”分析误判为模式切换。
- `automated`: 前端 12 个测试文件、64 项测试和生产构建通过；`CustomerWorkbenchServiceTest,CustomerCrmProjectionServiceTest` 共 12 项通过，覆盖选择保持、同模式幂等和明确导航命令。
- `release`: 生产 `2.5.9 / 6c7e27181fbb`；backend index `sha256:e72350e9b5a92c811649f260791c63bd2120a11a25455b672c60648303716b7f`、amd64 `sha256:d83a6892a1d46cc8aafa130ccb8831f9eead29ad8d5abd7251cad171a051addd`；frontend index `sha256:5bb6554e4202e88fadec1eb7f0870bcf1766933da076eb1851706c0632bac45a`、amd64 `sha256:ee3e34ba4eec966e3080e6dfe225d313b0bd59f42e5f0d9b8bac6491a147521d`。
- `production-browser`: 大数据组织搜索“奔驰”返回 4 条，目标客户完成 `TASK-196 稳定性验收` 受控互动归集；确认后及 35 秒轮询后，搜索词、结果数、当前客户和 CRM 连接均保持。截图：`output/playwright/task196-prod-customer-context-stable-2.5.9.png`。
- `network-logs`: 归集轨迹只有普通 `queue?mode=existing&query=奔驰`，无 `refresh=true`；确认后没有新 CRM 全量同步，浏览器控制台 0 错误，Nginx 5xx 和后端目标错误为空。
- `operations`: 备份 `/opt/cici/backups/20260712-124820-before-2.5.9-task196-context-stability` 四类文件非空；backend/frontend `2.5.9` healthy，状态服务保持 `2.3.4`；健康 `UP`、Nginx 有效、公网页面 200。

## TASK-195 客户互动时间线完整年份生产验收（2026-07-12）

- `root-cause`: 时间线复用通用 `shortDate()`，非今天/昨天只输出 `MM-DD HH:mm`，跨年记录无法区分；首次年份实现使用 `pre-line` 时浏览器仍会在连字符处折行。
- `frontend`: 12 个测试文件、62 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。单元测试覆盖 2024/2026 同月同日仍保留四位年份及非法来源值回退。
- `release`: 最终生产 `2.5.8 / a016c165fd95`；backend index `sha256:fa59e23ec070d06708c07324895333fd33be60b2b94035152c25a728cacdd21b`、amd64 `sha256:93a6bd67479c9d51f96f7b7f2c53732bd11c89fce7ba1627b454c2f66c8ab6d5`；frontend index `sha256:580f5167a4c3cfe71488eb51f81478a5efa10dae7a1d370d1861849755440bc6`、amd64 `sha256:07246081a4c74f7daed5d4f2e0867474523de39dc11148f15e0de2fddab2ebe5`。
- `production-browser`: 真实客户“梅赛德斯-奔驰汽车金融有限公司”的完整时间线共 22 条，概览显示前 5 条；2026 与 2023 记录均为 `YYYY-MM-DD` + `HH:mm` 两行，日期内部 `white-space=pre` 不折行。图标中心与垂直轴偏差 `0px`，页面无外层溢出、无业务错误。截图：`output/playwright/task195-prod-timeline-full-year-2.5.8.png`。
- `operations`: 最终备份 `/opt/cici/backups/20260712-120506-before-2.5.8-task195-no-wrap` 四类文件非空；backend/frontend `2.5.8` healthy，状态服务保持 `2.3.4`；健康 `UP`、版本/commit 一致，发布后后端目标错误和 Nginx 5xx 扫描为空。

## TASK-194 全量客户名称搜索与输入焦点治理生产验收（2026-07-12）

- `root-cause`: 原搜索只过滤当前内存投影，并在名称匹配前应用新/老客户模式和队列筛选；投影又有 10,000 Account 上限，因此 CRM 中可见的客户可能无法命中。全局 `input:focus` 阴影叠加组件边框，形成双层焦点框。
- `backend`: `CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 11 项通过；覆盖权限范围 Account 名称查询、单引号转义、模式/筛选旁路、缓存外客户详情加载和既有队列行为。
- `frontend`: 12 个测试文件、60 项通过；生产构建通过，仅保留既有 Vite chunk-size 提示。覆盖搜索态文案、客户真实模式自动对齐和现有工作台交互。
- `release`: 生产 `2.5.6 / 12c766bed77d`；backend index `sha256:bfa4ad2932c037000716213cc6df224483d863cce4a0332252fea5de77cfd59b`、amd64 `sha256:8c6918de52589c95bdac2cc7c83d9138484276454278a98a3693407bc2cd645d`；frontend index `sha256:661037ba5a6d1a7543122871f713b360e4e3ad9f3fa1311878d2598b498e56b6`、amd64 `sha256:e063dbc5fdd2adbe8d37ea271c2c2a0f855bdc3c353449ac0ee630ef7339f7ad`。
- `production-api`: 真实组织 `org5nszpgj99jaysxv6y` 在 `mode=new/filter=focus` 下查询“青岛海信商用显示”，HTTP 200，`source=CLOUDCC_SEARCH`、`searchScope=ALL_VISIBLE_ACCOUNTS`、`totalElements=1`，命中“青岛海信商用显示股份有限公司”；复测搜索 0.76 秒、详情 0.22 秒，客户分类 `EXISTING`。
- `production-browser`: 版本 `2.5.6`、CloudCC 已连接；搜索结果显示“全部客户搜索结果 1 条”，页面自动切换“老客户经营队列”并展示服务与关系预警。无过期令牌或通用服务器错误；input 为零边框/零阴影/透明背景，wrapper 为单一 1px 金色边框且无阴影。截图：`output/playwright/task194-prod-global-search-existing-mode-2.5.6.png`。
- `operations`: 备份 `/opt/cici/backups/20260712-112702-before-2.5.6-task194-global-search` 四类文件非空；backend/frontend `2.5.6` healthy，状态服务保持 `2.3.4` healthy；健康 `UP`、版本/commit 一致、Nginx 有效，公开根路由与工作台均 HTTP 200，发布后后端目标错误和 Nginx 5xx 扫描为空。

## TASK-193 客户队列最近互动倒序生产验收（2026-07-12）

- `backend`: `CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 10 项通过；覆盖新客户和老客户按最近互动倒序、暂无互动置后、10,000 客户规模和既有工作台行为。
- `frontend`: 12 个测试文件、59 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示；页面默认排序选项为“最近互动”。
- `release`: `2.5.3 / c7af96a48092` 已发布；后端 index `sha256:2be33ef3be924aed10865cd273d44db4dbb3d2e71a0948fb46ec908a6971eb11`，前端 index `sha256:c128d28bcd58917714d8bf8e8911bd2d566bb91cc48258b140a1c321eb9e8758`。
- `production-data`: 真实组织 `org5nszpgj99jaysxv6y` 使用现有 Owen 身份调用未传 `sort` 的默认接口；新客户、老客户首屏各 12 条均 `descending=true`、`emptyLast=true`。
- `operations`: 发布前备份四类产物非空；六服务健康，版本和 Git commit 一致，工作台 HTTP 200，后端目标错误和 Nginx 5xx 扫描为空。

## TASK-192 大数据量 CRM 异步初始化生产验收（2026-07-12）

- `root-cause`: 组织 `org5nszpgj99jaysxv6y` 首屏四个接口同步等待 Account、Contact、Opportunity、Task、Event、Case、Contract 全量分页读取；受控后端直读耗时 94.99 秒，超过 Nginx 60 秒读取超时。真实账号会话有效，问题不是凭据或身份映射失败。
- `before`: 2026-07-12 08:58:38，`integration-status`、`notifications`、`supervisor-summary`、`queue` 四路请求同时返回 504，页面直接展示 Nginx HTML；可见 Account 达到现有 10,000 条读取上限。
- `backend-focused`: `CustomerCrmProjectionServiceTest`、`CustomerWorkbenchServiceTest`、`CustomerSignalRepositoryIntegrationTest` 共 10 项通过；覆盖异步立即返回、并发单飞、10,000 客户五秒规模门、批量建议读取和零逐客户计数。
- `frontend`: 12 个测试文件、58 项测试通过；生产构建通过，仅保留既有 Vite chunk-size 提示。非 JSON 504 被规范化为中文业务消息，同步状态自动轮询。
- `release`: `2.5.2 / 1c2084b5746c` 已发布；后端 index `sha256:287f46e2e748bee6b49db68d1001a6136770ec8adc8ae6508a002c73a9426aea`，前端 index `sha256:ae284daf247695759e7e1961dd74db2aa3ecd8d1274cedcb28175aa8aae46b25`。
- `cold-cache`: 生产 HTTPS 四路并发请求分别在 0.996-1.013 秒返回 HTTP 200；queue 为 `CLOUDCC_SYNCING` 且 `syncStatus=SYNCING`，integration 显示“正在同步 CRM 数据”。
- `ready`: 后台同步 46.21 秒完成，Account=10,000、`recordLimitReached=true`；integration 转为 `READY`，队列 0.68 秒返回 12 条首屏记录，重点推进筛选总数 37。
- `logs`: 发布后无 504、`upstream timed out`、异常堆栈或通用服务器错误。临时浏览器诊断会话在验收收尾时过期，未重复注入凭据；API、Nginx 与后台日志证据完整。
- `state-validation`: TASK-192 自身状态、spec、assignment 和完成区归档均有效；全库校验仍被 TASK-191 及更早任务留在 Active Tasks、旧 spec 状态/时间格式和旧 task 热文件预算等既有治理债务阻塞，本任务未扩散修改这些无关历史文件。
- `boundary`: 本任务解决超时、并发阻塞和错误展示；每对象 10,000 条 OpenAPI 上限仍存在并已显式提示，完整增量投影需另立架构任务。

## TASK-191 CloudCC 重复刷新稳定性生产验收（2026-07-12）

- `root-cause`: 白屏发生在 AgentCiCi iframe 请求之前，CloudCC 重用并清空已标记 mounted 的 pagecomponent 宿主节点；`Unexpected server error` 先后暴露确定性信号 ID 并发插入冲突，以及原子 UPSERT 缺少实际事务边界。
- `component`: UMD 延迟节点 fixture 在 900ms 插入组件、1800ms 清空同一节点，1300ms/3000ms 均为一个 iframe；`node --check` 和技能 package dry-run 通过。通过 `cc-customization-expert-msapi` 发布 pagecomponent V11、绑定 customPage V5。
- `backend`: `CustomerSignalRepositoryIntegrationTest,CustomerCrmProjectionServiceTest,CustomerWorkbenchServiceTest` 共 8 项通过；真实 PostgreSQL 验证同一 ID 两次 UPSERT 仅保留一行并更新最新内容。
- `release`: dry-run、前后端构建、ACR 推送和 Git tag `2.4.12` 通过；运行版本 `2.4.12 / 4d00d417dcf3`。backend index `sha256:b60f4bead39d06831a846c3efbcf3368aba21e0b23d80fb3f6a7020cceede51c`，frontend index `sha256:ba02632b8b61f812ca9b2244b89f319f0b6b4e9e3986af7a32016be8f089649e`。
- `backup/deploy`: `/opt/cici/backups/20260712-001641-before-2.4.12-task191-transaction` 四类文件非空；backend/frontend `2.4.12` healthy，状态服务保持 `2.3.4` healthy；健康 `UP`，Nginx 配置有效。
- `production-browser`: 真实 CloudCC Web 登录并进入客户互动工作台，连续三次刷新均重新加载 iframe、真实客户数据、`CloudCC CRM 已连接` 和助理历史；未出现白屏或 `Unexpected server error`。截图：`output/playwright/task191-prod-cloudcc-refresh-stable.png`。
- `production-logs`: 发布后目标请求未再出现 duplicate key、`TransactionRequiredException`、连接池超时或通用服务器错误；部署切换期间旧 SSE 会话产生的两条 `Session not found` 404 与本修复无关，16:23Z 后错误扫描为空。

## TASK-190 CloudCC 嵌入端会话恢复生产验收（2026-07-11）

- `root-cause`: 真实 CRM 嵌入页的 `CCAdmin` 调用 CloudCC Account 查询时，CloudCC 以 HTTP 200 返回 `result=false` 和“登录失败，请再次尝试重新登录”；旧实现只刷新 HTTP 401，且同一用户缓存未命中时可能并发申请 Token。
- `backend-focused-tests`: `CloudccAccessTokenServiceTest,CloudccOpenApiServiceTest,CustomerWorkbenchServiceTest` -> **success**。8 路并发只触发 1 次 Token 请求；HTTP-200 登录失效刷新后重试成功；普通业务错误不误判。
- `release`: dry-run、前后端构建、ACR 推送、镜像 inspect 和 Git tag `2.4.9` -> **success**；运行版本 `2.4.9 / 052bf118fc1e`。
- `backup/deploy`: `/opt/cici/backups/20260711-224930-before-2.4.9-task190-cloudcc-session` 四类文件非空；backend/frontend 健康，四个状态服务保持 `2.3.4` 健康；Nginx 与公开三入口通过。
- `production-concurrency`: 真实映射成员 `CCAdmin` 的 integration、queue、notifications、supervisor 六路并发请求全部 HTTP 200。
- `production-readback`: integration `CONNECTED / ready=true / visibleAccounts=110`；老客户队列 `totalElements=48 / firstPage=12`；发布后未发现 CloudCC 登录失败、Token 获取失败或通用服务器错误。
- `cloudcc-skill`: pagecomponent V10 与 customPage V4 的组件 ID 均为 `6a503defe4b0a577cbba1f8a`；`actualVersions=[]` 仍触发既有 stale warning，属于已记录技能误报警。

## TASK-189 客户互动多模态采集生产验收（2026-07-11）

- `release`: dry-run、ACR 推送与 tag `2.4.8` 成功；运行版本为 `2.4.8 / 530ba01263b9`，V75 成功，六服务健康，Nginx 与公开三入口通过，稳定期错误扫描为空。
- `backup`: `/opt/cici/backups/20260711-161034-before-2.4.8-task189-multimodal-interaction` 的环境、PostgreSQL、KB 与 Qdrant 备份均非空。
- `production-api`: 真实截图批次 `cib_0a109c387d2e48f1afab2f864fdbc6e6` 达到 `READY`，图片资产达到 `READY`，OCR 提取 87 字符，鉴权原件请求返回 200。
- `production-confirm`: 首次确认创建事件 `cwi_e3e4cfaa8671f3cd14799c097512977c283137b9`，时间线回读命中 1 条；再次确认返回同一事件且 `deduplicated=true`。该事件作为一条有意保留的生产验收互动记录。
- `agent-browser`: 生产 AgentCiCi 组织“智能体平台演示环境”显示版本 `2.4.8`、CloudCC 已连接，多模态工作区打开正常；document/body X/Y overflow 均为 false，console error/warn 均为 0。截图：`output/playwright/task189-prod-platform-multimodal-2.4.8.png`。
- `cloudcc-browser`: 使用 `cc-customization-expert-msapi` 核验 pagecomponent V10/customPage V4 后，在真实 CloudCC Web 菜单打开客户互动工作台及多模态工作区；iframe docu…130621 tokens truncated…essionId}` and session cleanup logic for messages + session state.

- Conversation grouping and new-dialog entry verification (2026-04-27):
  - Commands:
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added top-level "新对话" action in conversation list panel.
    - New draft sessions now keep a stable session id and are reused across multi-turn messages.
    - Session list refresh preserves unsent local drafts until first persisted turn is created.
    - Loading messages for newly created unsent sessions now gracefully handles `404` as empty history.

- Workbench session history alignment verification (2026-04-27):
  - Commands:
    - `frontend`: `npm run test -- src/assistant/workbenchSessions.test.ts` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added `workbenchSessions.test.ts` to verify per-agent workbench session id generation and chronological history extraction.
    - `AssistantApp` workbench now fetches persisted session messages via `/ai/sessions/{sessionId}/messages` and refreshes after send completion.
    - Workbench right-side history uses the same message source as the main chat stream to avoid mismatch.

- FEAT-006 backend stream protocol verification (2026-04-25):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - `ChatOrchestratorService.chatStream` now emits FEAT-006 scene events:
      - `avatar_state` (`thinking` / `speaking` / `idle`)
      - `task_created`
      - `task_status`
      - `task_delta`
      - `task_done`
    - Tool phase now updates task status and emits `waiting_user` when approval tool path is hit.
    - Existing stream events remain compatible (`delta` / `tool_call` / `tool_result` / `phase` / `done` / `error`).

- FEAT-006 virtual human scene MVP frontend verification (2026-04-25):
  - Commands:
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Extended `streamAiChat` with unknown-event passthrough callback to support task/avatar SSE event handling.
    - `AssistantApp` scene page now consumes stream events and drives:
      - avatar states (`idle` / `listening` / `thinking` / `speaking`)
      - task cards (`task_created` / `task_status` / `task_delta` / `task_done`)
      - text and voice input linked to the same stream runtime path.
    - Updated immersive scene CSS for avatar motion states, task card stack, mic active state, and send/notice controls.

- MCP admin scope + cache snapshot smoke closure (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=McpServerIntegrationTest,OrchestratorIntegrationTest,ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `McpServerIntegrationTest.shouldRejectOrgUserAndAllowOrgAdminForMcpServerApis`:
      - verifies `/mcp-servers` rejects `ORG_USER`
      - verifies `/mcp-servers` allows `ORG_ADMIN`
    - Added `McpServerIntegrationTest.shouldKeepCachedSnapshotWhenDiscoverRefreshFails`:
      - verifies cache-miss path can discover tools
      - verifies discover failure response is returned
      - verifies previously discovered snapshot remains readable after failure

- FEAT-002 session reducer precision + no-repeat regression (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `ChatSessionStateService` now enriches session state from user turns with stable fields:
      - `current_object_type`
      - `current_object_name`
      - `target_segment_summary`
      - `missing_fields`
      - `next_action`
      - `no_repeat_questions`
    - Reducer now sets deterministic no-repeat constraint marker when user says “不要再重复问”.
    - Added integration coverage `shouldCaptureSessionFieldsAndNoRepeatConstraintAcrossTurns` to verify:
      - second-turn continuity under same `sessionId`
      - no-repeat constraint state persistence
      - `missing_fields` does not regress to `target_segment` when segment already present.

- Structured ioPayload + fallback/invalid replay assertions (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `nodeMetrics` now includes structured I/O payload:
      - `ioPayload.input`
      - `ioPayload.output`
    - Added fallback replay coverage:
      - `shouldExposeFallbackReplayMetadataInDebugRuntime`
    - Added invalid runtime replay coverage:
      - `shouldExposeInvalidReplayMetadataInDebugRuntime`
    - Existing published chat/debug assertions now verify both `ioSummary` and `ioPayload`, plus replay hints.

- Replayable node io-summary protocol (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `contextSnapshot.nodeMetrics` now includes per-node `ioSummary`:
      - `ioSummary.input`
      - `ioSummary.output`
    - `contextSnapshot` now includes `replayHint` for ordered replay guidance.
    - Both runtime paths expose the same replay-oriented fields:
      - `/agents/{agentId}/debug` -> `contextSnapshot`
      - `/ai/chat` -> `runtimeExecution.contextSnapshot`
    - Integration assertions updated to verify `replayHint` and `ioSummary` presence in:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Runtime execution metrics snapshot (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `contextSnapshot` now includes execution-time metrics and error protocol fields:
      - `branchHit`
      - `nodeMetrics` (`nodeId`, `costMs`, `status`)
      - `errorNode`
      - `errorType`
    - Metrics are exposed in both runtime paths:
      - `/agents/{agentId}/debug` -> `contextSnapshot`
      - `/ai/chat` -> `runtimeExecution.contextSnapshot`
    - Integration assertions updated to verify metrics snapshot presence in:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Runtime context snapshot projection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Execution results now include `contextSnapshot` for both debug and chat runtime paths.
    - `/agents/{agentId}/debug` response now contains `contextSnapshot`.
    - `/ai/chat` response now contains `runtimeExecution.contextSnapshot`.
    - Snapshot fields include minimal runtime state projection:
      - `runtimeSource`
      - `inputRoute`
      - `toolScopeSize`
      - `intent`
      - `parsedNodes`
      - `knowledgeUsed`
      - `toolInvoked`
      - `responsePlanned`
    - Integration assertions updated:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Debug runtime minimal executor output (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `AgentWorkflowRuntimeService` now executes a minimal controlled runtime path in debug mode (published/fallback/invalid branches) instead of fixed `simulated-runtime`.
    - `/agents/{agentId}/debug` now returns structured execution fields:
      - `executionStatus`
      - `executionOutput`
    - `OrchestratorIntegrationTest.shouldUsePublishedWorkflowInDebugRuntime` now also verifies `executionStatus=published-executed` and non-empty published execution output.

- Chat runtime execution visibility (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `/ai/chat` now includes `runtimeExecution` payload with:
      - `status`
      - `output`
      - `publishedVersionId`
    - `OrchestratorIntegrationTest.shouldExposePublishedRuntimePolicyInChatResponse` now additionally verifies chat response reports `runtimeExecution.status=published-executed` with published execution output text.

- Node-level runtime trace projection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `AgentWorkflowRuntimeService` now emits standardized node-level trace steps in minimal executor path:
      - `workflow-node:start`
      - `workflow-node:route-input:*`
      - `workflow-node:tool-scope:size=*`
      - `workflow-node:end:*`
    - `/agents/{agentId}/debug` now returns `executionTrace`.
    - `/ai/chat` now returns `runtimeExecution.trace`.
    - Integration assertions updated:
      - `shouldUsePublishedWorkflowInDebugRuntime`
      - `shouldExposePublishedRuntimePolicyInChatResponse`

- Workflow-code-driven node extraction (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Runtime now parses published `workflow_code` (`runAgent` body) to project code-level nodes into execution trace.
    - Extracted node markers include:
      - `intent-classify`
      - `knowledge-search`
      - `handoff-request`
      - `tool-invoke-best`
      - `response-generate`
    - Integration assertions updated to verify trace now includes parsed code node marker `workflow-node:code:intent-classify`.

- CiCi session continuity + state layer phase-1 implementation (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Flyway migrated to `v22` (`V22__chat_session_state.sql`) in integration test context.
    - Verified `GET /ai/sessions/{sessionId}/state` returns persisted session state after a user intent turn (`先添加名单，先不要发邮件`) via `OrchestratorIntegrationTest.shouldPersistSessionStateAfterUserIntentHint`.
    - Verified chat realtime stream integration still passes after `ChatOrchestratorService` message assembly changes.

- Runtime binding to published workflow dependencies (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `SkillResolverService` now reads `agent_definition.published_version_id` and prefers `workflow_manifest.dependencies` as runtime capability boundaries when publish status is `PUBLISHED`.
    - Added integration coverage `shouldPreferPublishedWorkflowDependenciesAtRuntime` to confirm runtime uses published dependency boundaries.
    - Regression confirms realtime chat stream path is still green after runtime resolver change.

- Session continuity 2-turn + published-version three-state regression (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `shouldKeepSessionStateAcrossSecondTurn`: verifies same `sessionId` two-turn conversation keeps session state (`hold_action` + `continue_current_plan`) and persists full turn history.
    - Added `shouldSwitchRuntimeDependenciesAcrossPublishStates`: verifies runtime dependency boundary transitions across three states:
      - publish V1 (`skillRefs=sales-copilot`) -> runtime tools contain CloudCC path,
      - publish V2 (`skillRefs=web-search`) -> runtime tools switch to Tavily path,
      - rollback to V1 -> runtime tools revert to CloudCC path.

- Invalid published-manifest runtime resilience (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added integration coverage `shouldGracefullyHandleInvalidPublishedManifest`.
    - Test forces a published version with invalid `workflow_manifest` JSON and verifies chat runtime still responds successfully without crashing.

- Published runtime policy injection (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `SkillResolverService` now parses `workflow_manifest.policies.maxToolCalls` and `publishedVersionId` into runtime context.
    - `ChatOrchestratorService` now applies published `maxToolCalls` (bounded) to tool-loop rounds and returns `runtimePolicy` in `/ai/chat` response.
    - Added integration coverage `shouldExposePublishedRuntimePolicyInChatResponse` to verify published policy values are visible and effective in runtime payload.

- Debug runtime uses published workflow version (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Added `AgentWorkflowRuntimeService` as debug runtime entry for agent workflow execution context.
    - `/agents/{agentId}/debug` now returns runtime metadata:
      - `runtimeSource` (`published_version` / `capability_fallback`)
      - `publishedVersionId`
      - `workflowCodePreview`
    - Added integration coverage `shouldUsePublishedWorkflowInDebugRuntime` to verify debug runtime prioritizes published workflow versions.

- Skill authoring fallback alignment for campaign workflow (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Verified the no-model fallback path no longer relies on approval/CRM/contract built-in templates as the primary generation strategy.
    - New integration coverage confirms:
      - 通用审批需求会保留原始风险事实与输出要求，而不是强绑定某个内置模板编码。
      - 营销活动需求会保留自定义工具名、编号步骤和 `email_send`，且不会被误导成 `CRM 线索分诊`。
    - Logs confirm local default path still has no configured `skill-authoring` model (`Aliyun API key is not configured.`), so this verification specifically proves the generic fallback path is working.

- Admin console SMS login bootstrap admins (2026-04-24):
  - Commands:
    - `backend`: `mvn -q -Dmaven.repo.local=.m2 -Dtest=AuthFlowIntegrationTest,ManagementConsoleIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Default `application.yml` now sets `app.auth.bootstrap-admin-mobiles` to include `13900009999`, matching README / `application-local.yml` demo behavior when the process runs without `spring.profiles.active=local`.

- MCP chat tool exposure fix + H2 migration compatibility (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
    - `backend`: `mvn -q -Dtest=OrchestratorIntegrationTest test` -> **BUILD SUCCESS**
    - `backend`: `mvn -q -Dtest=ChatRealtimeIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - Verified legacy tool ids no longer cause the approval-agent tool whitelist to collapse to empty; `/agents/approval-agent/debug` now resolves `get_pending_approvals` instead of the stale alias `approval-fetch`.
    - `V21__mcp_server_tool_cache_fields.sql` now runs successfully in H2 test context, restoring SpringBoot integration test startup.
    - `McpServerService.getTools(...)` now attempts a one-shot cache refresh when both memory cache and database snapshot are absent, reducing first-chat MCP empty-catalog failures.

- System MCP server cache implementation (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Added persistent MCP tool snapshot fields on `mcp_server` via `V21__mcp_server_tool_cache_fields.sql`.
    - `McpServerService` now separates cache read (`getTools`/`getToolCacheSnapshot`) from forced refresh (`refreshToolCache` / `/discover`) and keeps old snapshots on refresh failures.
    - Admin tools page now shows MCP cache summary (`工具数 + 更新于 + 缓存状态`) in both list and detail tabs, and detail tab reads `GET /mcp-servers/{id}/tools` by default.

- System MCP cache runtime smoke attempt (2026-04-23, superseded by 2026-04-30 runtime closure):
  - Commands:
    - `POST /auth/sms/send` with `mobile=13900009999` -> `SMS request too frequent, please retry later`
    - `POST /auth/sms/send` + `POST /auth/sms/login` with `mobile=13800138111` -> login success (`roles=["ORG_ADMIN"]`)
    - `GET /mcp-servers` with `13800138111` token -> `{"success":false,"message":"需要组织管理员权限"}`
    - `POST /mcp-servers` with `13800138111` token -> `{"success":false,"message":"需要组织管理员权限"}`
  - Result:
    - Real runtime smoke for MCP cache flow is **blocked** in current local auth/session state.
  - Notes:
    - This blocker is tracked in `.claw/issue-list.md` as `ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope`.
    - Build-level verification remains green (`backend compile`, `frontend build`).

- Skill Creator model-driven authoring compiler (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -DskipTests compile` -> **success**
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
  - Notes:
    - `BuiltinSkillCreatorService` now attempts model-driven structured draft generation first and falls back to heuristic generation when model output is unavailable/invalid.
    - Structured output is still normalized by `SkillSpecSchemaValidator` and org candidate whitelist checks to keep compatibility and safety boundaries.

- Skill Authoring Phase 2 (authoring session + clarification loop) (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
    - `frontend`: `npm run build` -> **success**
  - Notes:
    - Flyway migrated to `v20` (`V20__skill_authoring_session.sql`) in test context.
    - New integration coverage: `shouldMergeClarificationAnswersWithinAuthoringSession` (UTF-8 response parsing for MockMvc).
    - Regression coverage still includes generate/refine/create + hidden creator visibility checks.

- Skill Authoring design gap implementation verification (2026-04-23):
  - Commands:
    - `backend`: `mvn -q -Dtest=SkillAuthoringIntegrationTest test` -> **BUILD SUCCESS**
    - `frontend`: `npm run build` -> **success** (`tsc -b && vite build`)
  - Notes:
    - Flyway successfully migrated to `v19` (`V19__skill_authoring_source_fields.sql`) in integration test context.
    - Verified new persistence fields are schema-valid with existing test suite and do not break authoring generate/refine/create flow.

- V18 migration + full backend test suite restoration (2026-04-22):
  - Commands:
    - `backend`: `mvn test` → **Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
  - Breakdown:
    - `AuthFlowIntegrationTest` 6/6
    - `ManagementConsoleIntegrationTest` 1/1
    - `ChatRealtimeIntegrationTest` 1/1 (after fixing `event:connected` assertion typo)
    - `OrchestratorIntegrationTest` 2/2 (after using distinct admin mobiles + `callCount >= 1` to tolerate shared Spring test context)
    - `TavilyToolServiceTest` 10/10
    - `TavilyCatalogIntegrationTest` 1/1 — verifies `/tools` catalog exposes `tavily_search` + `tavily_extract` as builtins, `/skills` exposes `web-search` with Tavily `toolWhitelist`, and `/skills/agents/cici-system/bindings` auto-binds `web-search` with `activationMode=intent-route`
  - Notes:
    - Fix lands as a V18 rewrite (cross-DB `TIMESTAMP` + entity-aligned `user_id VARCHAR(64)` + regular `UNIQUE INDEX` instead of partial) plus two cascading preexisting test-design fixes. All recorded in `.claw/issue-list.md` → ISSUE-2026-04-22-v18-migration-blocks-h2-integration-tests (resolved).
    - Previously the entire `@SpringBootTest` layer was unreachable because Flyway aborted on V18; today every integration test that existed before Tavily work is green again, and the new Tavily integration test is online.

- Tavily Search + Extract built-in skill integration (Phase 1):
  - Commands:
    - `backend`: `mvn test -Dtest=TavilyToolServiceTest` → **Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
    - `backend`: `mvn test -Dtest=TavilyCatalogIntegrationTest` → **Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**
    - `backend`: `mvn -q test-compile` → success (whole project compiles clean)
    - `frontend`: `npm run build` → success (321 modules, no TS errors)
  - Covered by `TavilyToolServiceTest`:
    1. `toolDefinitions()` returns OpenAI-style `tavily_search` + `tavily_extract` with required params (`query` / `urls`) and enum constraints.
    2. Dispatching `tavily_search` without an `integration_app('tavily')` row returns `TAVILY_NOT_CONFIGURED`.
    3. Dispatching `tavily_extract` without config returns `TAVILY_NOT_CONFIGURED`.
    4. `max_results` is clamped to `[1, 20]` and missing values fall back to `properties.defaultMaxResults()`.
    5. `search_depth` / `topic` / `include_answer` / `include_raw_content` enums fall back to defaults on invalid input.
    6. Upstream non-2xx from Tavily is surfaced as `TAVILY_UPSTREAM_ERROR` with truncated body.
    7. `urls` with >20 entries is trimmed to exactly 20 before hitting Tavily.
    8. Queries >400 chars are truncated (no upstream rejection).
    9. `tavily_extract` successful shaping truncates `raw_content` to `properties.maxExtractChars()` and records original length.
    10. `IntegrationAppService.update("tavily", apiKey=...)` encrypts the key via `SecretCipherService` and masks it as `tvly-****` in the view, while preserving the stored cipher when the client resubmits the masked sentinel.
  - Notes:
    - Unit tests use hand-rolled fakes for `TavilyClient` + `IntegrationAppRepository` instead of Mockito to avoid JDK 25 inline-mock instrumentation issues.
    - Integration test `TavilyCatalogIntegrationTest` verifies the full wiring through HTTP: `/tools`, `/skills`, `/skills/agents/cici-system/bindings`.


- Frontend build (login preview conversation demo):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - 登录页左上欢迎区已从静态 `boot-lines` 文案升级为动态演示卡片，包含访客提问、思考摘要、流式回答三个阶段。
    - 新增 `BootLoginConversationDemo` 组件，文案动画由前端本地状态驱动，不依赖登录接口或后端实时数据。
    - 已补充独立播放轮次状态，单条场景在播完后也会自动重启，持续循环展示对话。
    - 已删除对话文案中的“通过 Slack”，并修正登录框定位逻辑：从全屏 flex 对齐改为右侧固定浮层，避免与左侧演示区视觉重叠。
    - `frontend/src/styles.css` 已补充该区域的终端式赛博视觉与移动端单列布局。
    - 登录页副标题文案已改为更贴近当前系统能力的产品表达，突出专属数字员工、企业知识库、工作流与工具、审批推进等能力。
    - 登录页副标题已进一步压缩，并改为“7x24 小时在线协作”表述，避免产生“登录后才唤醒”的语义偏差。
    - 登录页副标题已补充“记忆系统”和“自定义 Skill”能力点，使描述更贴近当前产品能力边界。
    - Vite chunk-size warning remains informational.

- Agent/Skill entry refactor Phase 1 compile/build verification:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 后端新增 `/agents/{agentId}/skills` 读写接口，并在 agent 详情返回中携带 `skillBindings`。
    - 前端 Agent Builder 已显式传递 `skillRefs`，并把 skills 与其他 draft 一起保存。
    - AdminSkillsPage 已移除 agent 绑定管理区块，收口为 skill 资产中心。

- Agent/Skill capability unification + debug trace verification:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 新增 `AgentCapabilityResolverService`，统一计算 effective skills/tools/kbs/handoff/outputContract。
    - `AgentCompileService` 已改为复用统一 resolver，并在 warning 中输出 skill-agent 边界冲突提示。
    - 新增 `POST /agents/{agentId}/debug`，返回 active skills、effective scope、trace steps 与 warnings。
    - Agent Builder 调试面板已接入 debug 接口并展示 active skills。

- User Workflow page/load + compile/publish regression fix:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - restart backend on `8080` with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
    - `GET /me/agents/cici-system/workflow`
    - `POST /me/agents/cici-system/workflow/compile`
    - `POST /me/agents/cici-system/workflow/publish`
  - Result: success
  - Notes:
    - Verified the page-load 500 root cause was a null-bearing `Map.of(...)` in `UserWorkflowController.get(...)`; after switching to `LinkedHashMap`, `GET /me/agents/cici-system/workflow` succeeds again.
    - Verified the publish failure root cause was false-positive time parsing: text containing `8080` was previously compiled into invalid hour `80`; after tightening `inferTrigger(...)` and guarding `computeNextFire(...)`, the same text compiles as `MANUAL` and publishes successfully.
    - Final state check:
      - API `GET /me/agents/cici-system/workflow/versions` shows `v4 -> PUBLISHED`
      - DB `user_workflow_version` shows `version_no=4, publish_status=PUBLISHED`

- User Workflow Phase 1.5 Feishu DM end-to-end smoke:
  - Command:
    - `PUT /me/agents/cici-system/workflow/profile` with `notificationTarget={"type":"feishu_dm","value":""}`
    - `POST /me/agents/cici-system/workflow/compile` with `sourceText="测试飞书私信送达 smoke"`
    - `POST /me/agents/cici-system/workflow/publish` with `versionNo=1`
    - `POST /me/agents/cici-system/workflow/run-now` with `routineKey="routine-1"`
  - Result: success
  - Notes:
    - Test user `18611892001` had an active Feishu binding (`open_id=ou_efc396f23aec3375205d2fc72a5bcf54`), and `demo-org` already had an enabled `feishu_bot` integration config.
    - `run-now` returned execution `status=SUCCESS`; trace notification node returned `status=SENT`, `targetType=feishu_dm`, and message `已通过飞书私信主动发送执行结果。`
    - This validates the local runtime path: execution complete -> resolve bound `open_id` -> proactive Feishu DM send.

- Feishu DM progress review recheck:
  - Command:
    - `backend`: `mvn -q -DskipTests compile`
    - `frontend`: `npm run build`
  - Result: success
  - Notes:
    - 代码复核确认：`FeishuBotMessenger.sendTextToOpenId(...)` 与 `UserWorkflowService.deliverNotification(...)` 已接入个人工作流执行完成后的主动私信发送链路。
    - 前后端在当前仓库状态下仍可通过编译/构建，说明这条链路至少通过了静态集成层面的验证。
    - 当时发现的状态口径滞后项已在后续会话中修正并完成真实 `run-now` smoke。

- Feishu pairing entry frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added a user-facing Feishu pairing section to `MyWorkflowStudio`.
    - The settings page now supports:
      - reading current pairing status
      - generating a one-time pairing code
      - copying the pairing command
      - unbinding the current Feishu account
    - Build completed successfully; Vite chunk-size warning remains informational.

- User Workflow Phase 1.5 backend compile:
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - `FeishuBotMessenger` now supports proactive text send by `open_id`, in addition to reply-by-message-id.
    - `UserWorkflowService` now attempts real Feishu DM delivery when `notificationTarget.type = feishu_dm`.
    - When the profile has no explicit target value, runtime falls back to the current user's active Feishu binding if present.
    - Delivery failures are captured into execution trace/output instead of being swallowed.

- User Workflow Phase 1.5 frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `MyWorkflowStudio` now explains that the Feishu target may be left blank and the system will try to reuse the current user's bound Feishu `open_id`.
    - Build completed successfully; Vite chunk-size warning remains informational.

- User Workflow Phase 1.5 runtime smoke:
  - Command: pending
  - Result: not yet executed
  - Notes:
    - Real end-to-end validation still needs an org with a working Feishu bot config plus a user who already has an active Feishu binding.
    - Recommended next smoke: publish a personal workflow with `notificationTarget.type=feishu_dm`, leave target empty, then call `/me/agents/cici-system/workflow/run-now` and confirm message delivery.

- User Workflow Phase 1 backend compile:
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V17__user_workflow_tables.sql` and five user-scoped workflow tables:
      - `user_agent_profile`
      - `user_workflow_spec`
      - `user_workflow_version`
      - `user_workflow_trigger`
      - `user_workflow_execution`
    - Added user-side workflow APIs under `/me/agents/{agentId}/workflow/**` for:
      - profile/spec update
      - compile / versions / publish / rollback
      - trigger list/update
      - run-now / debug / executions
    - Added `@EnableScheduling` and `UserWorkflowScheduler` for due-trigger scanning.
    - Build verifies source compatibility of the new user workflow domain/service/controller layer.

- User Workflow Phase 1 frontend build:
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Avatar entry now opens a broader personal-settings modal instead of a mailbox-only view.
    - Added `MyWorkflowStudio` with:
      - personal workflow settings
      - natural-language Spec editor
      - compile / publish / rollback actions
      - trigger list and manual run
      - recent execution records
    - Existing mailbox management remains available as a sibling tab.
    - Vite chunk-size warning remains informational.

- User Workflow Phase 1 runtime scope note:
  - Command: N/A
  - Result: partial by design
  - Notes:
    - This phase now records notification targets and execution summaries, but proactive Feishu direct-message delivery is not yet wired to a real active-send API.
    - Routines backed by unavailable tools (for example news aggregation or meeting invitation) are preserved and executed as tracked skeletons with notes in execution output rather than silently failing.

- Frontend build (workbench viewport lock / no page-scroll structure pass):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added viewport-level width/height/max-width/overflow constraints from `.cici-app` down through the workbench canvas/layout chain.
    - Replaced some fixed-width and `calc(100vh - padding)` style constraints with parent-height-based layout sizing to reduce whole-page overflow risk.
    - Tightened workbench top-bar and sidebar column limits to reduce horizontal spillover.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench compact styling + no page scrollbar pass):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench palette was pulled back toward the main system's light gray/white surface tokens instead of the earlier beige-tinted prototype look.
    - Typography, avatar sizes, paddings, and card spacing were reduced to produce a denser workbench layout.
    - Workbench container heights and overflow handling were adjusted to avoid page-level scrollbars, and the top bar columns were constrained so the state machine card no longer covers the agent list.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench layout aligned 1:1 to prototype):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench layout was reworked to mirror `frontend/public/agent-workbench-prototype.html` structure: top dock strip, top-right state card, left chat panel, right overview/history sidebar.
    - Workbench dock now uses local UI keys with `runtimeAgentId` mapping so visual dock expansion does not break existing chat runtime.
    - Existing workbench stream chat path remains active; only the page structure and workbench-local state model changed.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Frontend build (workbench state-machine Phase 1 refactor):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AssistantApp.tsx` workbench view was refactored from hero layout into `Dock + 状态机条 + 主对话区 + 右侧概览/历史`.
    - Workbench messages are now stored per agent, so switching the active dock keeps each agent's workbench dialogue state intact.
    - Existing stream chat path remains active in workbench mode, and `agentId` still follows the selected agent during request submission.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Email tool module (2026-04-19):
  - `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` -> success
  - `mvn -q -Dmaven.repo.local=.m2 -DskipTests test-compile` -> success
  - `npm run build` (frontend) -> success
  - Scope verified by compilation only:
    - `V16__email_account_table.sql` migration is present.
    - `EmailAccountEntity/Repository`, `SecretCipherService`, `EmailProviderRegistry`, `EmailAccountService`, `EmailToolService`, `EmailAccountController`, `ToolOrchestratorService` wiring, `ToolController.list()` merge all compile.
    - Frontend `MyEmailAccountsModal`, `AgentBuilderShell.TOOL_CATALOG` additions, Vite proxy for `/me`.
  - Runtime smoke (POP3 login / SMTP send / `GET /tools` response / Agent Builder selecting `email_*`) still pending: needs a user mailbox with valid credentials and a running backend.

- Backend compile (Agent Builder publish/rollback + compile-version persistence):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - `AgentCompileService` now persists draft versions into `agent_workflow_version` on `/agents/{agentId}/compile`.
    - Added agent version governance APIs (`/agents/{agentId}/versions`, `/publish`, `/rollback`).
    - Added publish config API (`PUT /agents/{agentId}/publish-configs`) and persistence wiring.
- Frontend build (Agent Builder real save/publish/rollback wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder now attempts backend-first loading for `/agents` and details.
    - Save action now calls real backend persistence endpoints for definition/spec/bindings/publish configs.
    - Added publish/rollback actions in the builder header and compile success notice with `draftVersionNo`.
- Runtime smoke (Agent Builder persistence APIs):
  - Command: start backend and call health + `/agents` related CRUD/compile/publish endpoints with real JWT
  - Result: blocked in current command sandbox
  - Notes:
    - Backend startup attempts in this environment repeatedly failed before serving HTTP due PostgreSQL connection errors (`SQL State 08001`, message: `尝试连线已失败。`).
    - Because app context could not fully initialize, this run did not produce valid endpoint smoke evidence.
    - Compile/build evidence above is valid; runtime smoke needs rerun in a stable DB-connectable environment.
- Backend compile (Agent Builder Phase1 persistence skeleton):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V15__agent_builder_persistence_phase1.sql` with agent persistence tables:
      - `agent_definition`, `agent_spec`, `agent_workflow_version`
      - `agent_kb_binding`, `agent_tool_binding`, `agent_channel_binding`, `agent_publish_config`
    - Added `AgentDefinitionController` admin APIs for persistence:
      - `POST /agents`, `GET /agents`, `GET /agents/{agentId}`, `PUT /agents/{agentId}`
      - `PUT /agents/{agentId}/spec`, `GET /agents/{agentId}/bindings`, `PUT /agents/{agentId}/bindings`
    - Added `AgentDefinitionService` and related repositories/entities for definition/spec/bindings write/read path.
    - Existing compile APIs (`/agents/compile`, `/agents/{agentId}/compile`) remain intact.
- Frontend build (Skill Studio v2 redesign + CRM template actions):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AdminSkillsPage` upgraded to a new console layout with hero metrics, CRM template strip, searchable skill cards, split editor, and binding workspace.
    - Added CRM template quick actions: apply-to-form and one-click create.
    - Build completed successfully; Vite chunk-size warning remains informational.
- Backend compile (built-in CRM skill seeds):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added 4 built-in CRM skills in `SkillDefinitionService` defaults:
      - `crm-lead-intake`
      - `crm-opportunity-health`
      - `crm-followup-orchestrator`
      - `crm-renewal-guard`
    - Skills are created lazily by existing `ensurePhaseOneDefaults(...)` flow when missing.
- Backend compile (SpecCompiler + SkillVersion phase A):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `V14__skill_spec_compiler_phaseA.sql` migration (`draft_spec_text` + `skill_version`).
    - `SkillDefinitionService` now writes draft `skill_version` snapshots on create/update.
    - Added shared `SpecCompilerService` and connected both `AgentCompileService` and `SkillDefinitionService.previewCompile`.
    - `AgentCompileService` compile payload now includes `resolvedSkillRefs`.
- Backend test compile (phase A source compatibility):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Confirms current test sources remain compilable after compile API and skill domain model expansion.
    - This run validates source compatibility only; it does not execute runtime assertions.
- Frontend build (skill draftSpecText form wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AdminSkillsPage` now supports `draftSpecText` editing and preview/save request passthrough.
    - Build completed successfully; Vite chunk-size warning remains informational.

- Runtime smoke (Skill Phase 2 API end-to-end):
  - Command: `curl` against `/skills`, `/skills/{id}`, `/skills/preview`, `/skills/agents/{agentId}/bindings` with real JWT
  - Result: success (after one bugfix)
  - Notes:
    - Verified `ORG_USER` token is rejected on `/skills` with permission error (expected).
    - Verified `ORG_ADMIN` token can complete create/update/preview/binding/list/disable full flow.
    - Initial binding update returned `500` due unique constraint conflict on `agent_skill_binding`.
    - Root cause fixed by adding `agentSkillBindingRepository.flush()` after delete-before-insert in `replaceBindings(...)`.
    - Re-run verified binding update and readback succeeded (`count=5`, smoke skill present).
- Frontend build (Admin Skills page integration):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added `/admin/skills` route and nav entry.
    - Added admin page for skill list/create/update/disable, compile preview, and agent binding updates.
    - Vite still reports chunk-size warning; build completed successfully.
- Backend compile (Skill Phase 2 backend APIs):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Phase 2 core backend APIs compile successfully: skill CRUD, skill preview compile, and agent-skill binding management.
    - `SkillDefinitionService` / `SkillController` / `skill` repositories and entities compile with the new contracts.
- Backend test compile (Skill Phase 2):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Existing test sources remain compilable after Phase 2 API/service expansion.
    - This run only verifies compilation; no runtime/integration assertions were executed.

- Runtime smoke (Skill Phase 1 agent routing + tool allowlist):
  - Command:
    - `curl -X POST /auth/sms/send` + `curl -X POST /auth/sms/login` to obtain JWT
    - `curl -X POST /ai/chat` with `agentId=cici-system`
    - `curl -X POST /ai/chat` with `agentId=sales-agent`
    - `curl -X POST /ai/chat` with `agentId=approval-agent`
  - Result: success
  - Notes:
    - `cici-system` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,general-assistant]`, `effectiveToolNames=[]`.
    - `sales-agent` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,sales-copilot]`, `effectiveToolNames=[cloudcc_getStandardObjects,cloudcc_getCustomObjects,cloudcc_getObjectFields,cloudcc_pageQuery]`.
    - `approval-agent` returned `resolvedSkills=[conversation-core,knowledge-first,safe-handoff,approval-assistant]`, `effectiveToolNames=[get_pending_approvals]`.
    - Confirms Phase 1 runtime behavior is effective in real API responses.
- Backend local startup (current source with local PostgreSQL):
  - Command: `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`
  - Result: failed (default startup)
  - Notes:
    - Flyway validation failed: migration checksum mismatch on version `12`.
    - Error indicates local DB stored checksum differs from current `V12` file.
- Backend local startup (temporary workaround for smoke):
  - Command: `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--spring.flyway.validate-on-migrate=false`
  - Result: success
  - Notes:
    - Service started and applied `V13__skill_registry_phase1.sql` on the local DB.
    - Used only to complete runtime smoke; migration checksum issue remains open for proper fix.

- Backend compile (skill phase 1 implementation):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added `skill_definition` / `agent_skill_binding` phase 1 schema and `chat_session.agent_id`.
    - Added skill runtime services, prompt assembly, default built-in skill seeds, and agent-skill default bindings.
    - Chat orchestration now resolves skills at runtime and filters tool exposure by skill allowlist.
- Backend test compile (skill phase 1 integration test sources):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Added `OrchestratorIntegrationTest` coverage for `agentId=sales-agent`, checking resolved skill metadata and effective tool allowlist.
    - Confirms new backend test sources compile with the current codebase.
- Frontend build (agentId passthrough for skill runtime):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `streamAiChat` request body now supports optional `agentId`.
    - Assistant workbench now forwards the currently selected `activeAgent.id` into `/ai/chat/stream`.
- Backend targeted test execution (skill phase 1):
  - Command: `mvn -q -Dtest=OrchestratorIntegrationTest test`
  - Result: failed in this environment
  - Notes:
    - Fixed one real issue during this run: `V12__feishu_binding_profile_columns.sql` was not H2-compatible and blocked Spring Boot test startup; migration was rewritten as two `ALTER TABLE` statements and the application context then started successfully.
    - Remaining failure is environment-specific and occurs after context startup: Mockito inline Byte Buddy self-attach still cannot initialize on the current local JDK 25 runtime, so test execution aborts in the Spring Boot mock reset listener.

- Frontend build (fix stale message panel under session polling/cache):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AssistantApp.tsx` now force-refreshes conversation messages when active conversation changes.
    - 60-second fallback polling now refreshes both session list and current active conversation messages, preventing list/panel divergence when SSE reconnects or misses events.
- Runtime diagnosis (Feishu user profile sync):
  - Command: inspect backend runtime logs and `feishu_bot_binding` rows after receiving real Feishu message
  - Result: partial (root cause confirmed; feature gated by platform permission)
  - Notes:
    - Backend log shows Feishu contact API error: `code=41050`, `msg=no user authority error`.
    - `feishu_bot_binding.display_name` / `avatar_url` remain empty due missing Feishu-side user profile read permission, not due data pipeline failure.
- Backend compile (Feishu auto-binding without pairing code):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Updated Feishu bridge path: when no active binding exists for `(orgId, tenantKey, openId)`, backend now auto-creates binding and continues conversation flow instead of returning pairing-code prompt.
    - Added fallback user resolution for auto-binding (prefer `ORG_ADMIN`, else latest available org user).
- Runtime verification (real Feishu -> agent -> Web realtime display, manual UAT):
  - Command: real Feishu single-chat with opened web workbench (manual user acceptance run)
  - Result: success
  - Notes:
    - Acceptance owner confirmed target achieved in-session.
    - Verified behavior: external Feishu message can be bridged to agent conversation and reflected in web workbench in realtime without manual refresh.
    - This closes the product acceptance gap previously tracked for live Feishu end-to-end confirmation.
- Runtime verification (session SSE stream + dual update events):
  - Command: restart backend on latest local code, then `GET /ai/sessions/stream` with valid JWT while calling `POST /ai/chat`
  - Result: success
  - Notes:
    - Initially observed `GET /ai/sessions/stream` -> `404` on old running backend process; after restart, endpoint returned `200` with `text/event-stream`.
    - SSE stream emitted `connected` first, then emitted `session_updated` for the same `sessionId` with both `trigger=user_message` and `trigger=assistant_message`.
    - This verifies the realtime event transport and server-side emit points in local runtime.
    - Remaining gap for product acceptance: one real Feishu single-chat to open Web workbench end-to-end verification.
- Backend compile (session realtime SSE sync):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Added backend session realtime event hub and `GET /ai/sessions/stream`.
    - `ChatOrchestratorService` now commits and broadcasts user-message and assistant-message updates separately, so external-channel sessions can surface in the web workbench before and after CiCi replies.
- Backend test compile (realtime sync regression test source):
  - Command: `mvn -q -DskipTests test-compile`
  - Result: success
  - Notes:
    - Added `ChatRealtimeIntegrationTest` source covering `/ai/sessions/stream` subscription and `session_updated` event expectations.
    - Confirms the new test source compiles under the current project setup.
- Backend targeted test execution (realtime sync regression):
  - Command: `mvn -q -Dtest=ChatRealtimeIntegrationTest test`
  - Result: failed in this environment
  - Notes:
    - Failure occurs before the test body runs.
    - Root cause is environment-specific: Mockito inline Byte Buddy self-attach cannot initialize on the current local JDK 25 runtime, so Spring Boot test startup aborts.
- Frontend build (session realtime subscription):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Replaced the 10-second conversation refresh loop with a long-lived `/ai/sessions/stream` subscription plus automatic reconnect.
    - Workbench now force-refreshes the active conversation when `session_updated` arrives, while retaining a 60-second polling fallback.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Runtime verification (external-channel sessions visible to ordinary system users):
  - Command: log in as `13900009996` (`ORG_USER`), then call `GET /ai/sessions` and `GET /ai/sessions/{sessionId}/messages` against restarted backend
  - Result: success
  - Notes:
    - Verified the Feishu conversation is now visible without depending on the pairing user identity or admin role.
    - Confirms the new visibility model: external-channel sessions are org/agent-scoped, while personal workbench sessions remain user-scoped.
- Runtime verification (ORG_ADMIN visibility for Feishu sessions):
  - Command: query `chat_session` / `feishu_bot_binding` in local PostgreSQL, log in as `18611892001`, then call `GET /ai/sessions` and `GET /ai/sessions/{sessionId}/messages` against restarted backend
  - Result: success
  - Notes:
    - Verified the Feishu thread was already persisted under pairing user `13900009999`, not lost.
    - Verified the previous empty web list was caused by user-scoped session filtering while the current web login used another admin account (`18611892001 / Owen`).
    - After changing `ORG_ADMIN` visibility to org scope and restarting backend, `GET /ai/sessions` returned the Feishu session and the history endpoint returned the full “在吗” conversation.
- Backend compile (real conversation list + history APIs):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Extended `/ai/sessions` to return richer conversation summaries required by the assistant workspace.
    - Added `/ai/sessions/{sessionId}/messages` for conversation history loading.
    - Verified backend compiles after wiring session summary parsing, latest-message lookup, and session ownership checks.
- Frontend build (real conversation list wiring):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workbench conversation list no longer depends on hardcoded `CONVERSATION_THREADS`; it now loads real `/ai/sessions` data after login.
    - Conversation detail pane now loads real history from `/ai/sessions/{sessionId}/messages`.
    - Added periodic conversation refresh so newly bridged external sessions, including Feishu sessions, can appear in the list without manual page reload.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Runtime verification (project status check + local startup):
  - Command: `docker compose up -d`, `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, `npm run dev`, then call `/actuator/health` and `/`
  - Result: success
  - Notes:
    - `docker compose ps` shows `cici-postgres`, `cici-redis`, `cici-rabbitmq`, `cici-qdrant` all up; postgres/redis/rabbitmq report healthy.
    - `GET http://127.0.0.1:8080/actuator/health` returned `{"status":"UP"}`.
    - `HEAD http://127.0.0.1:5173/` returned `HTTP/1.1 200 OK`.
    - Backend started with local profile and completed Flyway validation against PostgreSQL schema version 11.
- Backend compile (Feishu bot bridge status audit):
  - Command: `mvn -q -DskipTests compile`
  - Result: success
  - Notes:
    - Backend compiles with the Feishu SDK dependency and the current Feishu bot bridge classes in place.
    - This verifies the code-level integration path is buildable, but does **not** prove real Feishu runtime connectivity.
- Frontend build (Feishu pairing UI status audit):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workbench pairing UI and admin integration configuration UI both compile into the production bundle.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Frontend build (workbench chat alignment fix):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Changed the workbench message list from CSS grid to a vertical flex column so message rows no longer stretch across leftover height.
    - User messages are now explicitly right-aligned inside the workbench, with a bounded message width better suited to the wider dashboard layout.
    - Tightened the workbench composer action group so the voice and send buttons sit closer together and read as a single control cluster.
- Frontend build (send-code proxy target correction):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `frontend/vite.config.ts` and `frontend/vite.config.js` now default the dev proxy target to `http://127.0.0.1:8080`.
    - Added `VITE_BACKEND_TARGET` override support so remote backend targets can still be used without hardcoding a LAN IP.
    - `vite.config.ts` switched to Vite `loadEnv(...)` so the TypeScript config can read `VITE_BACKEND_TARGET` without relying on Node typings.
    - Restarted the Vite dev server after the config change so the new proxy target took effect immediately.
- Runtime verification (send-code chain recovery):
  - Command: start backend with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, then call `/actuator/health`, `/auth/sms/send` via both `8080` and `5173`
  - Result: success
  - Notes:
    - `GET http://127.0.0.1:8080/actuator/health` returned `{"status":"UP"}`.
    - `POST http://127.0.0.1:8080/auth/sms/send` returned `200 OK` with `devCode`.
    - `POST http://127.0.0.1:5173/auth/sms/send` returned `200 OK` with `devCode` after restarting the Vite dev server.
    - Repeating the request for the same mobile now returns `400 SMS request too frequent, please retry later`, confirming the request reaches backend rate limiting instead of failing in the dev proxy layer.
- Frontend build (workbench voice/chat/tool reconnect):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Workbench now reuses the original `streamAiChat` submit path and `/ws/asr` speech-input path instead of behaving like a static dashboard.
    - Task cards, approval cards, and quick actions can now trigger the same conversation pipeline that the original CiCi page used.
    - Added an approval drawer with `iframe` rendering so `get_pending_approvals` tool results can surface inside the workbench.
    - Attempted runtime verification against local backend, but `127.0.0.1:8080` was not listening in this session, so real end-to-end verification could not be completed.
- Frontend build (assistant hierarchy realignment):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Assistant workspace is now organized around `Agent -> Conversation -> Message` instead of a flat mixed session list.
    - Added an agent directory, agent-scoped conversation thread list, richer chat header context, and a right-side structure summary.
    - Added `docs/agent-conversation-hierarchy-design.md` and synced long-lived project docs/state files to this new IA direction.
    - Vite still reports a chunk-size warning for the production bundle, but the build completed successfully.
- Frontend build (Dify-style workflow preview canvas + minimap/zoom + channel merge):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Replaced the old Mermaid-style workflow preview surface with a Dify-inspired read-only canvas built from preview `nodes / edges`.
    - Added zoom controls, fit-to-canvas behavior, and a clickable minimap with viewport box.
    - Moved `发布渠道` into the `Agent 定义` section so it no longer occupies its own standalone card.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Frontend build (compact layout + tabbed compiler workspace):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Removed the right-side builder info column and converted the page into a tighter single-main-workspace layout.
    - Replaced the stacked compile result layout with tabs; default active tab is the workflow preview graph.
    - Condensed the header from large KPI cards into compact meta chips and a shorter status notice.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Backend runtime verification (after restart to latest code):
  - Command: stop old 8080 Java process, restart backend with `mvn -Dmaven.repo.local=.m2 spring-boot:run -Dspring-boot.run.profiles=local`, then call health/auth/compile APIs
  - Result: success
  - Notes:
    - `/actuator/health` returned `UP` after restart.
    - SMS login for `demo-org` succeeded and issued a valid assistant token.
    - `POST /agents/compile` returned `workflowCode`, `workflowManifest`, and `workflowPreview`, confirming the newly added compile API is live in the running backend.
- Frontend build (debug path highlight on workflow preview):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Added a read-only debug panel that accepts test input, simulates a path, and highlights matched nodes on the Mermaid workflow preview.
    - This iteration keeps debug execution on the frontend side, but the display layer is ready for a future real debug API.
    - Vite still reports chunk-size warnings for production build output; the build completed successfully.
- Backend compile (agent compile API skeleton):
  - Command: `mvn -DskipTests compile`
  - Result: success
  - Notes:
    - Added `AgentCompileController` with `POST /agents/compile` and `POST /agents/{agentId}/compile`.
    - Added `AgentCompileService` to generate `workflowCode`, `workflowManifest`, `workflowPreview`, `compileSummary`, `warnings`, and `dependencies`.
- Frontend build (real compile API wiring with workflow preview fallback):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder now passes assistant token into the builder shell and prefers the real `/agents/{id}/compile` API when compiling.
    - If the compile API is unavailable, the UI falls back to the existing frontend simulated compiler so the workflow preview experience remains usable.
    - Vite still reports chunk-size warnings for production build output, and the Mermaid chunk remains large but lazily loaded.
- Frontend build (workflow preview graph in Agent Builder):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Agent Builder compile output now includes a read-only workflow preview graph rendered from simulated compile artifacts (`workflowPreview`) alongside `workflow.ts` and `workflow.manifest.json`.
    - Mermaid was added as the graph rendering dependency and loaded lazily from the preview panel instead of the main bundle.
    - Vite still reports chunk-size warnings for production build output, and the Mermaid chunk is large, but the build completed successfully.
- Frontend build (checklist layout fix):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - Fixed the Agent Builder checklist layout for knowledge/tool cards by switching item content to a more stable grid layout.
    - Prevented Chinese titles from collapsing into one-character-per-line wrapping.
    - Vite still reports a chunk-size warning for the production bundle, but build completed successfully.
- Frontend build (Spec editor + compile output refactor):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - `AgentBuilderShell` was refactored from a field-based builder skeleton into a text-first Spec editor with compile result panels.
    - Current compile action is a frontend simulated compiler that generates placeholder workflow code, manifest, dependency list, and risk warnings.
    - Vite still reports a chunk-size warning for the production bundle, but build completed successfully.
- Frontend build (no-code Agent Builder framework):
  - Command: `npm run build`
  - Result: success
  - Notes:
    - New assistant-side Agent Builder workspace compiles successfully after adding:
      - left title-area workspace switch (`会话 / Agent 构建`)
      - `AgentBuilderShell.tsx` framework UI
      - Agent definition sections for identity, prompt, knowledge, tools, workflow, and release governance
    - Vite emitted a chunk-size warning for the production bundle, but the build completed successfully.
- Backend build (model provider center implementation):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: added provider-config migration/table + service/controller for 4 providers (`aliyun-bailian`, `ollama-local`, `anthropic`, `openai`), and provider model list fetch endpoints.
- Backend runtime verification (local profile):
  - Command: restart backend jar with local profile and call APIs via auth token
  - Result: success
  - Notes:
    - `GET /models/providers` returns exactly 4 providers.
    - `POST /models/providers/aliyun-bailian/models/fetch` returned `count=222` with live model ids from DashScope compatible endpoint.
- Frontend page verification (`/admin/models`):
  - Command: browser automation snapshot on running Vite dev server
  - Result: success
  - Notes: page now shows Cherry-style provider center: left provider list + right panel (`API Key` / `API 地址` / `检测` / `获取模型列表` / model list / scene mapping).
  - Enhancements (2026-04-14):
    - Added model search bar with filter icon and clear button
    - Models grouped by series prefix (e.g., `qwen`, `gpt`, `claude`) with group headers and counts
    - Filtered count badge shows `X / total` for search results
    - Switching provider resets search and reloads grouped models
- Frontend full build status (existing baseline issue):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/npm run build`
  - Result: failed (pre-existing)
  - Notes: failure remains in `src/assistant/AssistantApp.tsx` (TS2774 / TS2322 / TS18048) and is unrelated to this model-center change-set.
- Frontend full build — FIXED (2026-04-14 12:30):
  - Command: `PATH=/opt/homebrew/bin:$PATH /opt/homebrew/bin/npm run build`
  - Result: success ✅
  - Notes: fixed 6 TS errors in `AssistantApp.tsx` (speech input feature):
    - TS2774: replaced `typeof X !== "undefined"` with `"X" in window` / optional chaining for `MediaRecorder`, `WebSocket`, `AudioContext`, `getUserMedia`
    - TS2322: changed `AudioContext | undefined` cast to `AudioContext` + added runtime null check
    - TS18048: `ctx` possibly undefined — resolved by narrowing type after constructor
  - Build output: `tsc -b && vite build` → 316 modules, 83KB CSS + 480KB JS, 2.92s

- Frontend (auth parse hardening to prevent login crash):
  - Command: `npm run build`
  - Result: success
  - Notes: assistant/admin auth flows now use safe JSON parsing; empty or non-JSON response bodies no longer throw `Unexpected end of JSON input` and crash the page.
- Frontend-to-backend proxy runtime verification:
  - Command: `curl -i -X POST http://127.0.0.1:5173/auth/sms/send -H 'Content-Type: application/json' -d '{"orgId":"demo-org","mobile":"18611892001"}'`
  - Result: success (`HTTP/1.1 200 OK`)
  - Notes: fixed Vite proxy target mismatch (`8081` -> `8080`) in both `vite.config.ts` and `vite.config.js`; send-code no longer returns proxy-side 500.
- Frontend (after white-screen hook-order fix):
  - Command: `npm run build`
  - Result: success
  - Notes: fixed `Rendered fewer hooks than expected` by ensuring hooks are declared before conditional auth return in `AssistantApp`.
- Backend:
  - Command: `mvn -Dmaven.repo.local=.m2 test`
  - Result: failed in this environment
  - Notes: Mockito inline mock maker cannot attach agent on current JDK 25 runtime in sandbox (`Could not initialize plugin: org.mockito.plugins.MockMaker`).
- Backend compile/package:
  - Command: `mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: confirms backend code compiles and packages with latest changes.
- Backend compile/package after RabbitMQ+vector integrations:
  - Command: `mvn -Dmaven.repo.local=.m2 -DskipTests package`
  - Result: success
  - Notes: verified compilation after introducing AMQP queue/worker, vector-store abstractions, Qdrant adapter, and Flyway V4 migration.
- Full quality gate (post-Qdrant migration):
  - Command: `./scripts/quality-check.sh` (with Qdrant on `6333`)
  - Result: success
  - Notes: 7 backend integration tests (default profile); `OrgModelConfigRepository.deleteByOrgIdAndSceneCode` uses `@Modifying` JPQL; `OrchestratorIntegrationTest` uses `@TestPropertySource` so Maven `local` profile does not bleed in; Qdrant smoke script passes against live container.
- Full business E2E (local profile + Docker stack):
  - Command: `./scripts/run-full-demo.sh` (or `./scripts/e2e-local-business.sh` if API already up)
  - Result: success on 2026-04-02
  - Notes: SMS login (**default mobile `13900009999`** aligned with `bootstrap-admin-mobiles` for ORG_ADMIN on new user) → KB → upload → publish → MQ worker → PUBLISHED → chat RAG hit unique marker; `ragContext` size ≥ 1 with Qdrant.
- Frontend:
  - Command: `npm run build`
  - Result: success
  - Notes: production bundle was generated under `frontend/dist`.
- Frontend (after KB polling + selected-KB retrieval update):
  - Command: `npm run build`
  - Result: success
  - Notes: confirms UI changes for async indexing visibility compile and bundle correctly.

## Scope Verified In This Iteration

- New backend APIs:
  - `PUT /kb/{id}` update knowledge base
  - `DELETE /kb/{id}` delete knowledge base
  - `DELETE /kb/documents/{id}` delete document
  - `DELETE /models?sceneCode=...` delete model config
  - `DELETE /tools?toolName=...` disable tool
- Frontend management views expanded and build-verified:
  - **Admin app** (`/admin/*`): model/tool/ops/KB CRUD flows; **user role** management calling `/admin/users`
  - **Assistant app** (`/`): chat + read-only KB multi-select for RAG (no management tabs)
  - React Router + split `localStorage` keys (`cici_assistant_token` / `cici_admin_token`)
- New backend integration scope (compile-verified):
  - MQ indexing enqueue/consume flow classes added
  - Vector recall path integrated into RAG service
  - Qdrant + RabbitMQ local runtime config in `docker-compose.yml` (Qdrant on host `6333`)
- Runtime environment startup:
  - Command: `docker compose up -d && docker compose ps`
  - Result: verify with `scripts/verify-qdrant-stack.sh` when Qdrant is up
  - Notes: `cici-qdrant` exposes HTTP API on `6333`.

- End-to-end API verification (local profile, MQ indexing enabled):
  - Command: backend run on `8081` with `--app.kb.vector-store=memory`, then scripted API flow:
    - `/auth/sms/send` -> `/auth/sms/login`
    - `POST /kb` -> `POST /kb/documents/upload` -> `POST /kb/documents/{id}/publish`
    - poll `GET /kb/{kbId}/documents` until indexed
    - `POST /ai/chat` with `knowledgeBaseIds=[kbId]`
  - Result: success
  - Evidence: document reached `PUBLISHED`; chat returned `rag_count=1` and policy summary answer.

## Planned Verification

- Re-run `mvn test` under JDK 21 runtime (or with adjusted Mockito setup) to restore test gate.
- Add backend integration tests for the newly added delete/update management APIs.
- Add frontend E2E checks for new model/tool/ops management flows.
- Add end-to-end local verification for publish-document -> MQ task -> chunk indexing -> vector recall path with live RabbitMQ/Qdrant (`app.kb.vector-store=qdrant` in local profile).

## 2026-05-21 TASK-118 Current-Branch Usage Metric Cards Restore

- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: Vite still reports the existing large chunk warning after build.
- Visual verification:
  - Command: Playwright with mocked `/auth/me` and `/admin/organization/profile`, route `/admin/organization`, viewport 1440x900
  - Result: success
  - Evidence: screenshot `output/playwright/admin-organization-cards-current-branch.png`.
  - Notes: usage summary outer panel has `0px none` border, transparent background, and `0px` radius; all six usage metrics render as standalone cards with `1px solid` border, warm ivory background, `14px` radius, 12px grid gap, and no horizontal overflow.

## 2026-05-21 TASK-118 Usage Summary Data Restore

- Backend compile:
  - Command: `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/`
  - Result: success
  - Notes: confirms the restored `usageSummary` API aggregation compiles.
- Backend integration test:
  - Command: `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminOrganizationProfileIntegrationTest test` in `backend/`
  - Result: blocked before test assertions
  - Notes: Spring context startup is blocked by existing duplicate Flyway migration version `58`: `V58__platform_account.sql` and `V58__agent_open_api_cloudcc_key_type.sql`.
- Static diff check:
  - Command: `git diff --check`
  - Result: success

## 2026-05-21 TASK-127 Integrated Branch Backend Verification Unblock

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --task TASK-127 --branch codex/TASK-124-feat-046-platform-tenant-provisioning --files backend/src/main/resources/db/migration/V58__platform_account.sql docs/specs/FEAT-041-platform-accountless-login.md --json`
  - Result: success
  - Notes: `MANAGER-001` passed SSH-key possession and task-scope authorization for the merge-follow-up migration/spec fix.
- Migration collision fix:
  - Change: renamed `backend/src/main/resources/db/migration/V58__platform_account.sql` to `backend/src/main/resources/db/migration/V59__platform_account.sql`
  - Result: success
  - Notes: also synced `docs/specs/FEAT-041-platform-accountless-login.md` so the documented migration version matches the integrated branch.
- First rerun:
  - Command: `mvn -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` in `backend/`
  - Result: blocked by stale build output
  - Notes: Flyway still saw deleted `backend/target/classes/db/migration/V58__platform_account.sql`; a clean rebuild was required.
- Test-database reset:
  - Command: `docker exec cici-postgres sh -lc "dropdb -U cici agentcici_test && createdb -U cici agentcici_test"`
  - Result: success
  - Notes: reset the local PostgreSQL integration database after Flyway reported a checksum mismatch for previously applied version `58`.
- Focused backend integration gate:
  - Command: `mvn clean -Dtest=AuthFlowIntegrationTest,PlatformTenantLifecycleIntegrationTest test` in `backend/`
  - Result: success
  - Notes: `AuthFlowIntegrationTest` 16/16 passed, `PlatformTenantLifecycleIntegrationTest` 6/6 passed, total 22/22 green on local `agentcici_test`.

## 2026-05-27 TASK-137 Custom Agent Delete

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --developer MANAGER-001 --task TASK-137 --branch codex/TASK-137-custom-agent-delete --files ... --json`
  - Result: success after assignment roots were corrected from bare directories to recursive globs.
  - Notes: `check-assignment.py` also passed for implementation files and status/test-report files.
- Frontend focused test:
  - Command: `npm test -- AgentBuilderShell.test.ts` in `frontend/`
  - Result: success
  - Notes: 9 tests passed, including Agent delete fallback helper coverage.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Backend compile:
  - Command: `mvn -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/`
  - Result: success
- Backend focused integration test:
  - Command: `mvn -Dmaven.repo.local=../.m2 -Dtest=AgentDefinitionDeleteIntegrationTest test` in `backend/`
  - Result: blocked before assertions
  - Notes: Spring context startup could not obtain a PostgreSQL connection (`SQLState 08001`), so the new integration tests compiled but did not execute assertions.
- Desktop browser smoke:
  - Command: Vite dev server + in-app browser route open for `/admin/agent-builder`
  - Result: partial
  - Notes: unauthenticated route rendered the admin login page; authenticated Agent Builder smoke was blocked because `/auth/me` requires the same unavailable backend database.
- Static diff check:
  - Command: `git diff --check`
  - Result: success

## 2026-05-27 Assistant Root Auth Guard

- Authorization:
  - Command: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py .claw --json`
  - Result: success
  - Notes: `MANAGER-001` local identity verified before editing the assistant route auth behavior.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Browser auth smoke:
  - Command: Playwright route `/`, then set `localStorage.cici_assistant_token` to an invalid token and reload.
  - Result: success
  - Evidence: route rendered the assistant login form, displayed `登录状态已过期，请重新登录。`, and `localStorage` no longer contained `cici_assistant_token`.
  - Notes: expected `401 Unauthorized` was observed for `/auth/me` during invalid-token validation.

## 2026-06-02 Local Main Integration

- Scope:
  - Merged local task branches `codex/TASK-146-ops-observability-audit` and `codex/TASK-147-wecom-kf-connection-test` into `main`; `TASK-146` fast-forward also included `TASK-143`.
- Conflict resolution:
  - Files: `.claw/current-status.md`, `.claw/task-board.md`, `.claw/test-report.md`
  - Result: success
  - Notes: reconciled TASK-146 ops observability with TASK-147/TASK-148 Enterprise WeChat customer-service/domain status and preserved both validation histories.
- Merge follow-up fix:
  - File: `backend/src/main/java/com/codehouse/ciciassistant/wecom/service/WecomKfClient.java`
  - Result: success
  - Notes: first backend rerun failed Spring context startup because `WecomKfClient` had multiple constructors and no explicitly selected autowired constructor; added explicit constructor injection annotation.
- Conflict marker check:
  - Command: `rg -n "^(<<<<<<<|=======|>>>>>>>)" .claw . || true`
  - Result: success
- Static diff check:
  - Command: `git diff --check --cached`
  - Result: success
- Frontend production build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Backend focused integration gate:
  - Command: `mvn -q -Dtest='AgentRunTraceIntegrationTest,ModelProviderServiceIntegrationTest,PlatformModelProviderIntegrationTest,com.codehouse.ciciassistant.wecom.**.*Test' test` in `backend/`
  - Result: success after the constructor injection merge fix.
  - Notes: covers ops trace visibility, platform model-provider governance, and WeCom customer-service client/config behavior on the local `agentcici_test` database.

## 2026-07-09 TASK-171 CRM Clean Embed

- CRM 纯嵌入模式本地验证:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: verifies the AgentCiCi `embed=crm` route compile; existing Vite large chunk warning remains.
- UMD/static checks:
  - Command: `node --check frontend/build/customer-workbench.umd.min.js && git diff --check`
  - Result: success
  - Notes: verifies the CloudCC runtime bundle remains syntactically valid and the patch has no whitespace errors.
- CloudCC pagecomponent dry-run through `cc-customization-expert-msapi`:
  - Command: `cloudcc package pagecomponent customer-workbench --dry-run`
  - Result: success
  - Notes: recognizes `frontend/build/customer-workbench.umd.min.js` and safe pagecomponent files; no direct CRM write was attempted in this local gate.
- Browser DOM validation:
  - Command: Vite dev server plus Playwright at `http://127.0.0.1:5173/app?aiApp=customer-workbench&embed=crm` with mocked authenticated APIs.
  - Result: success
  - Evidence: `.playwright-cli/page-2026-07-09T07-11-39-914Z.png`.
  - Notes: DOM assertion returned `hasRail=false`, `hasAiApps=false`, `hasEmbedded=true`; rendered text starts from the customer queue and workbench content, not AgentCiCi platform navigation.

## 2026-07-08 TASK-171 Customer Interaction Workbench

- Authorization:
  - Command: `dev-login.py .claw --task TASK-171 ... --json`
  - Result: success
  - Notes: `MANAGER-001` passed identity and task-scope authorization for representative backend, frontend, spec, and CloudCC page component files; `check-assignment.py --task TASK-171 ...` also passed.
- CloudCC connectivity:
  - Command: CloudCC OpenAPI token flow plus MetadataService capabilities/standard-catalog checks; OpenAPI queries for standard `Task`, `Event`, and `Opportunity`.
  - Result: success
  - Notes: CRM standard objects returned real rows; MetadataService remained reachable with the OpenAPI token. Secrets and tokens are intentionally omitted from this report.
- Backend compile:
  - Command: `mvn -q -DskipTests compile` in `backend/`
  - Result: success
  - Notes: covers new customer workbench JPA entities, repositories, service/controller, migration reference, and skill definition changes.
- Frontend build:
  - Command: `npm run build` in `frontend/`
  - Result: success
  - Notes: existing Vite large chunk warning remains.
- Desktop browser validation:
  - Command: Vite dev server plus Playwright at 1440x900 with mocked authenticated APIs.
  - Result: success
  - Evidence: `output/playwright/task171-customer-workbench-desktop.png`.
  - Notes: AI 应用入口, 客户互动工作台, 老客户经营 tab, AI 快捷指令, CRM 落地建议, and `置信度 92%` render correctly; no horizontal overflow; console shows 0 errors and 0 warnings.
- CloudCC page component local validation:
  - Command: `cloudcc detail pagecomponent customer-workbench "" .`
  - Result: success
  - Notes: local component config and `prebuiltBundlePath` are recognized.
- CloudCC page component publish safety check:
  - Command: `cloudcc publish pagecomponent customer-workbench .`, followed immediately by `cloudcc delete pagecomponent <published-id> .`
  - Result: publish API returned success, then deletion returned success.
  - Notes: publish was not accepted as a valid release because the CLI packed root project config into the uploaded source payload. The cloud component record was deleted immediately, and a follow-up `cloudcc get pagecomponent .` did not show the component.
- CloudCC page component safe publish:
  - Command: create a temporary minimal CloudCC project under `/tmp` containing only `package.json.devConsoleConfig`, `frontend/pagecomponents/customer-workbench/customer-workbench.vue`, `frontend/pagecomponents/customer-workbench/config.json`, and `frontend/build/customer-workbench.umd.min.js`; then run `cloudcc publish pagecomponent customer-workbench <tmpProject>`.
  - Result: success
  - Evidence: final active component id `6a4d348fe4b0a577cbba1ebf`, apiName `custc_202607Hdhm60zo`; publish/update responses used only the minimal pagecomponent payload, unsafe config/token pattern count was `0`, and temporary credential directories were deleted.
- CloudCC page component remote verification:
  - Command: `cloudcc detail pagecomponent "" 6a4d348fe4b0a577cbba1ebf .` and `cloudcc scan msapi . online-highcode`
  - Result: success
  - Notes: remote component shows `component-customer-workbench`, `客户互动`, `isDeleted=0`, `loadModel=lazy`, and default URL `https://x.agentcici.com/app?aiApp=customer-workbench`.
- CloudCC CRM menu placement exploration:
  - Command: `cloudcc plan msapi . menus ... create`
  - Result: planned but not applied
  - Notes: generated script-menu plans did not include app/profile binding steps (`appCount=0`, `profileCount=0`), so applying was intentionally skipped to avoid an invisible or incomplete CRM menu.
- CloudCC HTML component publish:
  - Command: direct devconsole API `POST /devconsole/htmlComponent/saveHtmlComponent` with `accessToken` header and local `html/customer_interaction_workbench/{config.json,index.html}`.
  - Result: success
  - Evidence: HTML component id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`, accessPath `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`.
  - Notes: `cloudcc publish html customer_interaction_workbench .` currently fails because the CLI sends `pluginToken` as the `accessToken` header for this endpoint; direct call with the OpenAPI `accessToken` succeeds.
- CloudCC online high-code scan:
  - Command: `cloudcc scan msapi . online-highcode`
  - Result: partial success
  - Evidence: `pagecomponent` count `1` with id `6a4d348fe4b0a577cbba1ebf`, apiName `custc_202607Hdhm60zo`, component `component-customer-workbench`; `html` count `1` with id `6a4d37ece4b0a577cbba1ec0`, apiName `customer_interaction_workbench`; `customPage` count `1` with id `6a4d3b831b8c6d0ec6dd22ef`.
  - Notes: script endpoint returned an unrelated CloudCC server-side 500 during scan, so the scan is recorded as partial success even though the workbench assets are present.
- CloudCC MetadataService menu apply:
  - Command: `cloudcc apply msapi . pla2026E964195FlLpjf`
  - Result: blocked
  - Notes: MetadataService returned HTTP 403 `insufficient_scope` because the token is missing `metadata:apply`; no menu write was applied.
- CloudCC token scope probe:
  - Command: request `/api/cauth/token` with the standard body and with `scope=metadata:apply`, `metadata:read metadata:write metadata:apply`, `scopes:["metadata:apply"]`, and grant-type variants.
  - Result: blocked for apply
  - Notes: every successful response returned a JWT with payload keys `ClientId/aud/binding/exp/loginName/orgId` and no scope claim, so this developer key cannot self-request `metadata:apply`.
- CloudCC customPage write probe:
  - Command: direct devconsole API `/devconsole/custom/pc/1.0/post/insertCustomPage` using the legacy CloudCC CLI customPage payload contract.
  - Result: success
  - Evidence: customPage id `6a4d3b831b8c6d0ec6dd22ef`, pageLabel `客户互动工作台`, pageApi `customer_interaction_workbench`; `pageCustomPage` readback returned total `1`.
- CloudCC page menu and Sales Cloud binding:
  - Command: setup service `/api/customTab/tabSetDone`, then `/api/customTab/queryTabList` and `/api/appProgram/queryModifyPage`.
  - Result: success
  - Evidence: tab id `acf2026C53BE54B9R1Iu`, label `客户互动工作台`, lightning page `customer_interaction_workbench#lightning`, profile authorization count `6`; Sales Cloud app `ace20220322Salesloud` selected menu count `17`, with `客户互动工作台*` present in `selectedTabList`.
- Static diff check:
  - Command: `git diff --check`
  - Result: success
## 2026-07-12 TASK-198 AI 动态客户信号与可解释评分

- Authorization:
  - Result: success.
  - Notes: `MANAGER-001` passed task login and assignment checks for backend customer code, V77, workbench UI, specs and task state.
- Focused backend tests:
  - Command: `mvn -f backend/pom.xml -Dtest=CustomerCrmProjectionServiceTest,CustomerDynamicScoringServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest test`.
  - Result: success, 17 tests.
  - Notes: covers AI signal normalization, low-confidence pending state, idempotence, batch snapshot use, interaction confirmation and existing workbench behavior.
- Full backend baseline:
  - Command: `mvn -q -f backend/pom.xml test`.
  - Result: baseline not green, 251 tests with 16 failures and 3 errors.
  - Notes: TASK-198 focused suites are green; remaining failures/errors are unrelated existing billing/auth/skill fixture drift and connection-sensitive suites, consistent with the repository's known 19-test baseline gap.
- Frontend tests and build:
  - Command: `npm test -- --run && npm run build` in `frontend/`.
  - Result: success, 64 tests and Vite production build; existing large-chunk warning remains.
- Migration and local runtime:
  - Command: local Spring Boot startup against PostgreSQL.
  - Result: success; Flyway applied V76 and V77 and reached schema version 77, backend health became ready.
- Desktop browser validation:
  - Target: local AgentCiCi customer workbench, old-customer mode.
  - Result: success; score drawer rendered at 720px width and full viewport height, used an internal auto-scroll region, and document overflow was `x=0/y=0`.
  - Notes: verified five dimensions, 50-point insufficient-evidence baseline, 65% confidence note, filters and close action. Demo/detail score-source mismatch found during QA was fixed before release.
- Release dry run:
  - Command: `./scripts/release-acr.sh --dry-run`.
  - Result: success; generated production version `2.5.12` with canonical backend/frontend image and Git tag plan.
- Production release:
  - Result: success; Git commit/tag `4adbd3bf2d3a` / `2.5.12` and backend/frontend ACR images were pushed.
  - Images: backend index `sha256:58efb89a6c48505d8e94d797724a2207bab7f6acdeb5df21e8e9b1b74d705086`, amd64 `sha256:68ae75f21b77bd63e7e4ea6edc4b1d83ffd792f147018b568546c36175c1bafc`; frontend index `sha256:9fd8215c87319cf0b1b2259b7f0b99351cf993673fa174b603604b48ef70b53b`, amd64 `sha256:77d138450accd03c99314b5cb8459aabc003e6798167ca68b72d8db989228585`.
  - Backup: `/opt/cici/backups/20260712-192621-before-2.5.12-task198-dynamic-scoring`; env, PostgreSQL, KB files and Qdrant archives were non-empty.
  - Runtime: backend/frontend healthy on `2.5.12`; state services stayed healthy on `2.3.4`; health `UP`, version `2.5.12 / 4adbd3bf2d3a`, V77 `success=true`, Nginx valid, public root/workbench HTTP 200.
- Production dual-entry browser:
  - Result: success in AgentCiCi and real CloudCC CRM injection page using the same organization and user context.
  - Notes: CRM reached READY, existing-customer queue showed the dynamic neutral baseline, score drawer rendered all five dimensions and no-evidence state, host outer overflow was zero, and browser error/warning logs were empty.
  - Evidence: `output/playwright/task198-prod-cloudcc-score-drawer-2.5.12.png`.
- CloudCC implementation expert verification:
  - Skill: `cc-customization-expert-msapi 2.1.276-msapi`.
  - Result: component/customPage readback matched component id `6a526349e4b0a577cbba1fba`, name `component-customer-workbench`, version `11` and the production embed URL.
  - Notes: `verify injectionPage` returned the known warning `stale_component_reference` only because no runtime version snapshot was supplied; component ID/name/reference were exact, and the real CRM browser runtime rendered successfully.
- Historical signal backfill hotfix:
  - Command: `mvn -q -Dmaven.repo.local=.m2/repository -Dtest=CustomerDynamicScoringServiceTest,CustomerCrmProjectionServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest test` in `backend/`.
  - Result: success, 18 tests, zero failures/errors.
  - Notes: verifies old analysis becomes pending-only evidence at a neutral 50 baseline, repeated reads are idempotent, and current scoring/projection/ingestion behavior remains green.
- Historical signal backfill production release:
  - Result: success; Git commit/tag `ae6643c109a8` / `2.6.1`, backend index `sha256:36efd141a73d5650810e9f3d25c742385f26012b112a5845a811aa758399ec84`, frontend index `sha256:f88f747357c8126d9bd403dd208437f940145d39205112358d57a42ab3492ab1`.
  - Backup: `/opt/cici/backups/20260712-195131-before-2.6.1-task198-history-backfill`; all four artifacts are non-empty.
  - Runtime: backend/frontend healthy on `2.6.1`, state services healthy on `2.3.4`, health `UP`, version `2.6.1 / ae6643c109a8`, Nginx valid and public workbench HTTP 200.
  - Real data: organization `org2sva14i4udjmi2t4s` produced 2 pending signals and organization `org5nszpgj99jaysxv6y` produced 8; both snapshots remained `healthScore=50`, `activeSignalCount=0`.
  - Idempotence: two consecutive production explanation reads returned `50/0/2/2`; persisted signal count remained 2.
  - Stable-window logs: backend scoring errors `0`, Nginx 5xx `0`. Restart-window stream 502 responses ended when the backend became healthy and did not recur.

## 2026-07-12 TASK-199 互动驱动的客户经营动作

- Authorization:
  - Result: success.
  - Notes: `MANAGER-001` passed TASK-199 assignment checks for backend customer code, V78, workbench UI, specs and task state.
- Focused backend tests:
  - Command: `mvn -q -Dmaven.repo.local=.m2/repository -Dtest=CustomerInteractionActionServiceTest,CustomerInteractionIngestionServiceTest,CustomerWorkbenchServiceTest,CustomerDynamicScoringServiceTest,CustomerCrmProjectionServiceTest test` in `backend/`.
  - Result: success, 24 tests with zero failures/errors.
  - Notes: covers evidence-backed action creation, pending refresh without stacking, low-confidence rejection, missing target rejection, seven-day cooldown, historical-expiry rejection, hallucinated-evidence rejection, confirmation idempotence and existing workbench/scoring/projection behavior.
- Frontend tests and build:
  - Command: `npm test -- --run && npm run build` in `frontend/`.
  - Result: success, 66 tests and Vite production build; existing large-chunk warning remains.
- Desktop browser validation:
  - Target: local AgentCiCi customer workbench.
  - Result: success; historical recommendations are labeled `历史建议`, customer/timeline/action structure renders normally, and browser console has zero errors and zero warnings.
  - Evidence: `output/playwright/task199-local-dynamic-actions.png`.
- Static checks:
  - Result: success; `git diff --check` passed and fixed first-open recommendation/seed symbols are absent from production customer service code.
- Release dry run and images:
  - Result: success; release version `2.6.2`, Git `b87bbe43dd0d`, backend index `sha256:e0f275c02d910b392c708cf8940da9ca30fe1eabc2b19e2469fb42259638ae60`, frontend index `sha256:73f5b0b427d1707ee8d4de5a6819169b0df755408a0747d7387ed8917731dc12`.
- Production backup and deployment:
  - Result: success; backup `/opt/cici/backups/20260712-232657-before-2.6.2-task199-interaction-actions` contains non-empty env, PostgreSQL, KB and Qdrant artifacts. Backend/frontend are healthy on `2.6.2`; state services remain healthy on `2.3.4`.
  - Notes: health `UP`, version `2.6.2 / b87bbe43dd0d`, V78 `success=true`, Nginx valid, public root/workbench HTTP 200.
- Real interaction/action acceptance:
  - Result: success in organization `org2sva14i4udjmi2t4s`, existing customer `0012022D9CDF1CBPQGwJ`.
  - Evidence: confirmed batch `cib_554a1a6cc47e44d0afde91e1bbbd638e` produced event `cwi_f39777961d5df638a255caf7edd9308ffed0ed5c` and recommendation `cwr_0d4d4e3ddf5064c191e84b562a5f3dffc6aec10e` with key `expansion:mobile-inspection`, 100% confidence, exact source sentence, source event/batch and `2027-01-08` validity.
  - Idempotence: repeating confirmation returned `deduplicated=true`; matching action count remained `1`. The pending action was not accepted or written to CRM.
- Production browser and stable window:
  - Result: success; old-customer operations showed the new timeline event and `互动识别` action beside retained `历史建议`, with one evidence item and validity. Browser console had zero errors/warnings; task-related backend errors, migration errors and workbench Nginx 5xx were zero after warmup.
  - Evidence: `output/playwright/task199-prod-interaction-driven-action-2.6.2.png`.
  - Note: three login-shell `Session not found` responses for stale `workbench:cici-system` were observed before the stable window; they are unrelated to customer interaction/action endpoints.

## 2026-07-14 TASK-200 多租户智能体评测控制面生产落地

- Authorization and assignment:
  - Result: success.
  - Notes: `MANAGER-001` passed the generic and TASK-200 SSH challenge gates; assignment checks passed for V79, Agent/AI/Skill/Common backend code, platform/admin/Builder frontend code, Vite proxy and project-state files.
- Flyway and backend compilation:
  - Commands: clean PostgreSQL test startup, local Spring Boot startup, and `mvn -q -DskipTests compile` in `backend/`.
  - Result: success; 75 migrations validated and schema reached V79 in test and local runtime databases; backend compilation passed.
- Focused evaluation tests:
  - Command: `mvn -q -Dtest=AgentProductionReadinessIntegrationTest,AgentEvaluationControlPlaneIntegrationTest,AgentEvaluationAssertionEngineTest test` in `backend/`.
  - Result: success, 7 tests with zero failures/errors after the final redaction-order correction.
  - Coverage: platform suite draft/publish/immutability, sealed hidden-case redaction, tenant asset isolation and review lifecycle, platform auditor/read boundary, billing-role rejection, cross-Agent issue-reference rejection, Trace-to-DRAFT regression capture, mobile/email/ID-card/credential redaction, compound assertions, invalid assertion fail-closed, real evaluation model failure handling, stale/publish gates and publish readiness.
- Related security and observability regression:
  - Command: `mvn -q -Dtest=RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,PlatformGovernanceIntegrationTest,AgentRunTraceIntegrationTest test` in `backend/`.
  - Result: success with zero failures/errors.
- Full backend baseline:
  - Command: `mvn -q test` in `backend/` against a clean test schema during TASK-200 validation.
  - Result: baseline not green outside TASK-200.
  - Notes: unrelated existing failures include stale `skill_definition.source_type` fixtures, disabled meeting-minutes model provider assumptions, AutoService platform-auth expectation drift, billing/context/audit assumptions, OneKeyToken model-list expectation drift, customer-insight success mismatch and legacy skill-governance authorization assumptions. TASK-200 focused and adjacent RBAC/platform/Trace suites are green.
- Frontend tests and production build:
  - Commands: `npm test` and `npm run build` in `frontend/`.
  - Result: success; 12 files / 67 tests passed and Vite production build completed. Existing large-chunk warning remains.
- Local browser desktop validation:
  - Targets: `/admin/evaluation`, `/admin/agent-builder`, and `/platform/evaluation` on local runtime.
  - Result: success; tenant AI quality overview and evaluation-set maintenance rendered real API data, platform governance and standard-asset maintenance rendered correctly, and all checked pages had zero horizontal overflow and zero browser console errors/warnings.
  - Product-boundary evidence: Builder “评测” showed version quality, production gate and evaluation actions with no channel content; Builder “发布渠道” showed only 企微、钉钉、飞书、Web 浮窗、开放 API channel controls and no evaluation/quality headings.
  - Defect found and fixed: stale generated `vite.config.js` lacked `/evaluation` proxy although `vite.config.ts` contained it; both configs are now aligned and the page was reloaded successfully with no JSON parse alert.
- Compose and static checks:
  - Commands: `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config`, assignment check and `git diff --check`.
  - Result: success; rendered Compose output contained 232 lines and no validation error.
- Release dry run:
  - Command: `./scripts/release-acr.sh --dry-run`.
  - Result: success; generated canonical production candidate `2.6.3` for backend/frontend images, `CICI_APP_VERSION`, `VITE_CICI_APP_VERSION`, `CICI_IMAGE_TAG` and Git tag.
  - Notes: dry-run only; no image, Git tag, production data or deployment state was changed.

## 2026-07-14 TASK-203 客户互动工作台全场景演示数据

- Authorization and static validation:
  - Result: success. `MANAGER-001` passed TASK-203 SSH identity and assignment scope checks; `python3 -m py_compile scripts/seed-demo-environment.py`, `git diff --check` and the no-write `--dry-run` passed.
- CloudCC CRM V2:
  - Result: success. Batch `TASK-203-DEMO-V2` created/reused 16 Accounts, 30 Contacts, 8 Leads, 21 Opportunities, 30 Tasks, 45 Events, 8 Contracts and 8 Cases; script owner readback confirmed SalesA for every V2 record.
  - Idempotence: a second CRM upsert returned the same object counts with no duplicate-name growth.
- Minimum CRM permission:
  - Result: success. SalesA's sales profile lacked Contract/Case read access; permission set `cac203DemoVis01` adds read only. MetadataService plan `pla202604C39466BxSzs` and operation `ope202682B741D7w0fRu` reached `VERIFIED`.
  - Rollback: `rollback-plan` returned executable plan `rbp2026D899C178B8m63`; it was not applied. An earlier overlength permission-set ID plan failed before mutation and was superseded.
- AgentCiCi V2 transaction:
  - Result: success and idempotent. Latest backup `/opt/cici/backups/20260714-065319-before-task203-demo-v2`; transaction produced 16 workbench snapshots, 30 confirmed batches/events, 30 memories, 30 dynamic signals, 16 core score snapshots and 12 evidence-backed pending actions.
  - Coverage: seven source types, seven memory types, five score dimensions and ACTIVE/PENDING/EXPIRED/SUPERSEDED states. Action types are evenly split: 4 CREATE_TASK, 4 CREATE_OPPORTUNITY and 4 UPDATE_OPPORTUNITY; all 12 carry event, batch, action key, trigger and validity, and none was written to CRM.
  - Legacy cleanup: TASK-172 pending static recommendations are zero; accepted/applied historical acceptance records remain intact.
- Owen/SalesA production API acceptance:
  - Result: success. Integration returned `ready=true`, `visibleAccounts=16`, `syncStatus=READY`; queues returned new/all 8 and existing/all 8.
  - Filter totals: new focus/follow/risk/recommendations = `4/8/1/7`; existing renewal/health/service/expansion = `4/5/5/8`.
  - Scenario details: OPPORTUNITY_GAP, RELATION_GAP, NEXT_STEP_GAP, OVERDUE_TASK, SERVICE_RISK, RENEWAL_WINDOW, VALUE_STABLE and INTERACTION_GAP all appeared on their designated customers; 25-day and 80-day renewal examples, service issues, expansion opportunities and the zero-timeline silent customer were verified.
  - Archive/score: archive detail returned confirmation text, analysis and memory; seven sources and all five score dimensions were visible through authenticated APIs.
- Browser limitation:
  - The in-app browser could not resolve `onechat.agentcici.com` (`ERR_NAME_NOT_RESOLVED`). No safety interstitial was bypassed; the same Owen/SalesA identity was verified over IP-resolved HTTPS API instead. This is the existing workstation DNS risk, not an application or data failure.
## TASK-258 - 研发交付产品经理 Semattice 实时检索

- 定向后端测试：`SematticeProjectDeliveryToolServiceTest` 与 `ToolOrchestratorServiceTest` 通过；后端编译和 `git diff --check` 通过。
- 线上能力验收：生产 OACT 调用 `runtime.record.query` 返回 `DAS-DEMO:星轨移动销售助手:执行中`。
- 线上对话验收：生产 `dev-autopilot-pm` 对“现在有哪些项目在执行”返回项目 35% 进度、2 项进行中任务、5.5 小时工时和 2 项变更；通用无法访问提示为 false。
- 发布：`2.8.31 / 5c8953a3284d`；发布前备份四项均非空，backend/frontend healthy，后端 health `UP`、Nginx 校验及 `https://x.agentcici.com/` 200 通过。
## 2026-08-09 TASK-275 UAT activation 与 Semattice 入口根因回归

- UAT 真实访问证据：`POST /auth/devautopilot/handoff=200`、`POST /devautopilot/api/session/consume=200`，同秒 `GET /devautopilot/api/workspace=503`；AgentCiCi activation、Semattice binding、PM Agent/SERVICE/执行绑定和四项 activation resource 均存在且 ACTIVE。
- 根因：DevAutopilot 使用短期 RS256 OACT 查询 `/openapi/v1/official/devautopilot/activation`，但 AgentCiCi 通用 `TenantContextFilter` 按 HS application JWT 解析所有 Bearer，合法 OACT 被返回 401。新增仅作用于 activation resolve 的 OACT 验签过滤器，校验 signature、kid、issuer、audience、authorized party 及 company/tenant/principal 上下文，并在进入通用过滤器前移除 Authorization header。
- Semattice 根因：`/auth/semattice/console=200`，但 controller 复用了内部 API base URL，前端又只接受生产 hostname。新增 `APP_SEMATTICE_CONSOLE_BASE_URL`，UAT 覆盖为 `https://uat.agentcici.com`，前端允许当前受信同源或生产 Semattice hostname。
- 测试：`mvn -q -Dmaven.repo.local=.m2 -Dtest=OfficialAccessTokenServiceTest,OfficialDevAutopilotActivationFilterTest,TenantContextFilterTest,DevAutopilotHandoffServiceTest test` 通过；`npm test -- --run src/admin/adminAuthScope.test.ts` 3/3 通过；`npm run build` 通过；`bash scripts/test-release-versioning.sh` 通过并断言生产 `2.8.58` 的首个 UAT 候选为 `2.8.59-beta.1`；`git diff --check` 通过。

## 2026-08-10 TASK-275 产品经理 Agent 首页可见性与 UAT beta.5

- 根因证据：员工首页只展示内置 Agent 或 `publishedVersionId != null` 的 Agent；目标租户标准 PM 只创建 definition，未绑定 `web`，也未编译发布，因此被正确过滤。
- 实现：`DevAutopilotProductManagerAgentPublisher` 幂等补齐标准 Spec、保留既有绑定并添加 `web`、编译和发布；新 activation 与既有 activation 的正式 `initializations` 均复用该路径并要求 `published_version_id` 成功回读。
- 定向测试：`mvn -q -Dtest=DevAutopilotProductManagerAgentPublisherTest,OfficialAccessTokenServiceTest,ServicePrincipalServiceTest test` 通过；`mvn -q -DskipTests package` 通过；`git diff --check` 通过。未声称完整 Maven 套件通过。
- UAT 发布：`2.8.59-beta.5 / 0edfc3567f85`，backend/frontend ACR index digest 分别为 `sha256:6690ad74814ee308c4a42ccb300031474c5eb9cf00c7952babd84b9e7d082216` 与 `sha256:5068c400597ea2ca83faeb3ca9938884f8bf6c75b6ade6267480b1361b9668e6`。发布前备份 `/data/apps/agentcici/backups/20260810T092900Z-before-2.8.59-beta.5` 六项均非空；仅重建 backend/frontend，状态服务容器 ID 未变；health `UP`、版本和 Nginx 校验通过。
- 既有租户回读：`org00000000000000001` 的 `天工产品经理 / devautopilot-pm-09653ab9` 仍为 `enabled=true, published_version_id=NULL`，且 `agent_channel_binding` 为 0 行。结论是部署成功但受治理补偿尚未执行；当前可控浏览器无平台登录态，未绕过授权或直接写库。最终首页可见性验收待平台管理员执行一次正式 `initializations`。
- 项目状态校验：`validate-state.py .claw` 已执行但未通过，失败项为仓库既有的历史 task-board 归档、旧规格状态枚举/front matter 和旧时间格式债务；本次 TASK-275 状态卡与 FEAT-164 未新增校验错误，未在本任务中批量改写历史治理事实。

## 2026-08-10 TASK-275 初始化完成态权威判定与 UAT beta.7

- 后端定向测试：`DevAutopilotTenantApplicationReadinessTest`、`DevAutopilotProductManagerAgentPublisherTest`、`DevAutopilotHandoffServiceTest` 通过。
- 前端定向测试：`PlatformTenantApplicationsPage.test.ts` 2/2 通过；TypeScript/Vite 生产构建通过，仅保留既有大 chunk warning。
- UAT 实库只读 SQL 验证新完成条件对目标租户返回 `false`；发布后页面版本为 `2.8.59-beta.7`，卡片显示“待补齐”和“补齐初始化”。
- 真实补齐请求到达发布流程并被正确失败关闭，错误为聊天场景无平台可用模型。只读配置回读：平台厂商 7 个、已选模型 0、所有厂商 API Key 均未设置；目标 PM Agent 仍未发布、未新增 `web` binding。

## 2026-08-10 TASK-275 OneKeyToken 已验证模型与 UAT beta.8 初始化闭环

- 前端定向测试：`PlatformModelsPage.test.tsx` 3/3、`PlatformTenantApplicationsPage.test.ts` 2/2 通过；TypeScript/Vite 生产构建通过，仅有既有大 chunk warning。
- 浏览器真实检测：已配置的 OneKeyToken Chat Completions 检测成功；beta.8 曾把响应中的 `resolvedModel=qwen3.5-flash` 错当成 `validatedModel` 加入目录，该解释和配置已由 beta.9 纠正，不能作为模型列表证据。
- 平台管理员通过正式“补齐初始化”操作得到成功提示，目标租户卡片由“待补齐”变为“已完成”；没有数据库直写或未授权资源创建。
- UAT 数据库只读回读：`天工产品经理 / devautopilot-pm-09653ab9` 为 enabled，`published_version_id=2`；工作流 v1 为 `PUBLISHED`；`web` binding enabled；主 SERVICE execution binding enabled、OFFICIAL_APP、PRIMARY owner ACTIVE。
- 当前浏览器平台会话访问 `/app` 到达独立登录边界，不能作为 Demo Company 员工会话。服务端首页筛选所需的 published/web/enabled 条件已满足，但员工首页实际可见和创建会话仍待正常租户用户刷新验收。
- `validate-state.py .claw` 已执行；TASK-275、FEAT-123、FEAT-164 与 current-status 未产生校验错误。全局校验仍因仓库既有的历史任务归档、旧规格状态/front matter 和旧时间格式债务返回 1，本任务未批量改写这些历史事实。

## 2026-08-10 TASK-275 OneKeyToken 自动路由语义修正与 UAT beta.9

- 服务端：检测请求固定使用 `model=onekeytoken/auto`；成功响应返回 `validatedModel=onekeytoken/auto`，下游 `routing.model_used/model` 单独返回为 `resolvedModel`，目录能力继续为 `unavailable/0`。
- 前端：只允许把经过直接调用的 `onekeytoken/auto` 加入路由目录；下游实际模型仅展示诊断，不再保存为目录项或场景路由。定向测试 6/6、生产构建和后端 `-DskipTests package` 通过。
- 后端 `PlatformModelProviderIntegrationTest` 已更新预期，但本机 PostgreSQL `localhost:5432` 不可达，Spring Context 在 Flyway 连接阶段失败，未进入测试方法；未误报后端集成测试通过。
- UAT 浏览器真实检测回读：`validatedModel=onekeytoken/auto`、本次 `resolvedModel=qwen3.5-flash`、远程可用模型 0。错误的 `qwen3.5-flash` 已移除；数据库只读回读 OneKeyToken `selectedModels=[onekeytoken/auto]`，五个场景路由全部为 `onekeytoken/auto`。
- DevAutopilot 租户卡片继续为“运行中 / 初始化已完成”，版本显示 `2.8.59-beta.9`。

## 2026-08-10 TASK-275 产品经理领域 Skill 初始化与 UAT beta.11

- 缺陷证据：目标 Agent 已发布工作流的 Tool 为 query/create/review，但 manifest `skills=[]`；`agent_skill_binding` 与 `agent_workflow_skill_ref` 均为 0 行。截图中的 CRM 限制说明与该领域上下文缺失一致，不是 `onekeytoken/auto` 路由错误。
- 实现：平台标准 Skill 首次被模板引用前生成不可变 `PUBLISHED` 版本；模板 PM 建立 always-on binding，以显式 Skill ref 重新编译发布。标准 Spec 明确“项目”默认是 DevAutopilot/Semattice 研发交付项目，并要求未确认创建先生成草案。readiness 新增 Skill binding、当前工作流引用和 published Skill version 三项门禁。
- 测试：`DevAutopilotProductManagerAgentPublisherTest`、`DevAutopilotTenantApplicationReadinessTest`、`DevAutopilotTenantApplicationServiceTest`、`AgentWorkflowSkillRefServiceTest` 通过；后端 `-DskipTests package`、前端生产构建及 `git diff --check` 通过。未声称完整 Maven 集成套件通过。
- UAT：最终版本 `2.8.59-beta.11 / 4b0be4c4328e`，backend/frontend healthy、health `UP`、版本回读一致、Nginx 校验通过。真实平台页面先显示“待补齐”，点击正式补齐按钮后成功提示并回到“已完成”。
- 数据库只读回读：`devautopilot-pm-09653ab9` 当前工作流 v2 / `PUBLISHED`；有效 Tool 为 query/create/review/update/delete；`semattice-project-delivery-management` 为 always-on，工作流固定 Skill v1 / `PUBLISHED`；Spec 与 Skill prompt 领域断言 `domain_prompt_ready=true`。
- 当前可控会话是平台管理员，不是 Demo Company 员工；未伪造租户会话或创建真实项目。截图原句的最终对话回归由正常租户用户在新会话中完成。

## 2026-08-10 TASK-275 员工领域对话与身份目录最小权限

- 用户提供的 Demo Company 测试账号经正常 OIDC 登录；员工首页可见“天工产品经理”。新会话发送“能帮我创建一个项目吗”，`onekeytoken/auto` 返回研发项目名称追问和创建草案语义，不再出现 CRM 限制说明，领域对话回归通过；未实际创建项目。
- 正常进入 DevAutopilot 后 handoff/consume/workspace 成功，Semattice 实时项目数为 0；墨子正确显示休息不可派单，鲁班错误显示身份未同步。Semattice 数据只读回读已证明墨子 suspended、鲁班 active，故定位为目录读取授权缺失而非投影缺失。
- `OfficialAccessTokenServiceTest,DevAutopilotHandoffServiceTest` 通过。专用 handoff token 精确包含 `identity.principal.read`，并断言不含 `authorization.manage`；通用 Semattice token 保持不变。
- UAT `2.8.59-beta.12 / b070676f411a`：backend/frontend healthy，容器网络 health=UP，Nginx 有效，近期 backend 错误匹配 0；匿名 `/auth/me=401`。发布前备份六项非空，仅重建应用容器，状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`。
- 真实新 handoff：AgentCiCi 页面版本 beta.12，DevAutopilot 页面版本 `1.0.4-beta.1`；Semattice 0 项目、墨子不可派单、鲁班可派单，未出现身份目录不可用。

## 2026-08-10 TASK-276 / TASK-277 Owner 恢复与身份协调 UAT 发布验收

- 发布身份：Git tag、远端 tag、运行容器和 `/system/version` 均为 `2.8.60-beta.1 / 93a487f4e393`；backend/frontend ACR index digest 与 UAT 镜像 ID 一致。
- 发布安全：完整备份 `/data/apps/agentcici/backups/20260810T113637Z-before-2.8.60-beta.1` 的 Compose、环境、PostgreSQL、KB、Qdrant 与两项镜像均非空且 `0600`。仅 backend/frontend 重建，四个状态服务 ID 哈希未变。
- 运行验收：backend/frontend healthy，health=`UP`，Flyway 104 项校验成功且无需迁移，Nginx 配置有效，启动 ERROR/FATAL/Exception/Flyway failed 计数 0；公网首页 200，匿名 `/auth/me`、Owner 状态和协调接口均为 401。
- 受权页面验收：页脚为 `2.8.60-beta.1`；目标租户 Owner 显示 `OWNER/ACTIVE`、身份正常及统一身份可登录，Semattice 与 DevAutopilot 均运行中；浏览器 0 error / 0 warning。
- 非阻塞风险：启动日志存在 `SecretCipherService` 开发回退密钥 WARN，已登记独立 issue；不影响本次 Owner 功能，但不作为生产安全配置通过证据。

## 2026-08-14 TASK-302 Keycloak HUMAN 跨应用直调本地验收

- 后端定向：`KeycloakOidcLoginServiceTest`、`EcosystemHumanApiServiceTest`、`EcosystemApplicationTrustServiceTest`、`SystemApiCatalogServiceTest` 与 `GlobalExceptionHandlerTest` 共 14 项通过；`mvn -q -DskipTests package` 通过。
- 前端：`npm test -- --run` 共 49 个测试文件、272 项通过；`npm run build` 通过，仅保留既有大 chunk warning。
- 本地主线：功能提交 `e90a2d2b`、错误方法 405 修复 `9f58d972` 均已进入本地 `main`；最终开发环境版本为 `2.8.61-dev.9f58d97`。
- 运行态：backend、frontend 及依赖服务健康，V115 `ecosystem trusted application` 迁移成功；`/platform/system-apis` 和 `/platform/system-apis/applications` 为 200，匿名公司目录/上下文为 JSON 401，误用 GET 调用公司上下文为 JSON 405。
- 全栈门禁：环境域名源码扫描与 `./stack verify` 均通过，覆盖共享数据库隔离、TLS 边缘、OIDC、应用健康/版本和匿名鉴权边界。
- 未验证边界：当前没有可用于验收的独立 Keycloak Client 与 HUMAN 用户凭据，未执行真实成功登录、公司列表/上下文和后续 `X-Company-Id` 调用；授权态运营 UI 视觉验收亦待平台运营账号完成。本次未修改 UAT/生产。

## 2026-08-17 TASK-312 登录中转页自动跳转与结构精简

- 代码与结构：正常 `login_mode2` 中转态不再渲染说明卡片、手动登录按钮、退出提示或联系管理员文案；保留既有主视觉和同源 `/auth/oidc/login` 一次性自动跳转，统一登录完成失败时仅显示无按钮的最小错误提示。
- 自动化：`oidcAutoRedirect.test.ts` 与 `AssistantLoginTransition.test.tsx` 共 7/7 通过；前端全量 51 个测试文件、282 项通过；`npm run build` 通过，仅保留既有大 chunk warning。
- 本地主线与制品：提交 `745ee145f53a15d76aecebf5ff3cf056d54d6b7f` 已进入本地 `main`；仅重建 `cici-frontend`，运行版本 `2.8.61-dev.745ee14`、revision `745ee145f53a`、镜像 ID `sha256:461dd5628d41c16b68e93aed83a2c3469c1d313cba15aa997ec3a60d1961c05c`，healthy、restart=0。
- 路由与真实浏览器：`https://cici.localhost/app` 返回 200；全新未登录桌面会话没有任何点击即跳转到 `sso.localhost` OIDC 登录页。带本地无效完成票据的受控中转态用于视觉检查，主视觉存在、旧表单容器 0、按钮 0、正文为空，控制台 0 error / 0 warning。
- 影响边界：backend、Nginx 和其他产品容器未重建且继续 healthy/restart=0；UAT、生产、ACR、Git tag 和远端 `main` 均未修改。
