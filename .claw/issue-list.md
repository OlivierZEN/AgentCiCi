---
kind: issue-list
version: 3
updated_at: 2026-05-11T15:58:32Z
updated_by: ai
status: active
---

# Issue List

## Open Issues

- ISSUE-2026-04-21-spec-compiler-is-template-based:
  - Symptom: 当前系统会生成 workflow code / preview / manifest，但更接近“规则归纳 + 固定模板代码生成”，尚不能可靠承接复杂自然语言业务意图。
  - Verified root cause: `SpecCompilerService` 仅做文本分行、关键词推断、简单规则抽取；`AgentCompileService.buildWorkflowCode(...)` 输出固定 TypeScript 模板，并未调用 LLM 做真实编译。
  - Evidence: code inspection on 2026-04-21 of `SpecCompilerService` and `AgentCompileService`.
  - Status: open (P1，属于产品能力差距而非单点 bug)。
- ISSUE-2026-04-08-cloudcc-token-invalid-credential:
  - Symptom: CloudCC MCP tool calls still use placeholder args (`{open_api_token}`, `{base_url}`) because backend cannot obtain session token.
  - Verified root cause: 2026-04-30 重新验证后，组织级 `cloudcc_crm` 配置与 CloudCC 网关解析均正常，但使用系统内已绑定账号的真实用户 `13800000001/哪吒`（`ccUsername=nezha@cloudcc.com`）请求已解析网关 `https://szyd.apis.cloudcc.cn/lightningapi/api/cauth/token` 仍返回 `result=false`, `returnInfo=Please check your username and password.`，说明阻塞点仍是用户绑定凭证无效或已失效。
  - Evidence:
    - `GET /integrations` 显示 `cloudcc_crm` 已启用，且存在 `orgId/clientId/secretKey/orgapi_switch_address`。
    - `GET /admin/users` 显示 `13800000001/哪吒` 已绑定 `cc_username/cc_safetymark`。
    - `curl https://developer.apis.cloudcc.cn/oauth/apidomain?...` 成功返回 `orgapi_address=https://szyd.apis.cloudcc.cn/lightningapi`。
    - `curl -X POST https://szyd.apis.cloudcc.cn/lightningapi/api/cauth/token ...` 返回 `Please check your username and password.`
  - Status: open (requires CloudCC-side credential verification/rotation).
- ISSUE-2026-04-30-chat-smoke-blocked-by-aliyun-api-key:
  - Symptom: 以真实绑定 CloudCC 账号的用户走 `/ai/chat` 发起 CloudCC 查询类问题时，聊天链路未触发工具，直接返回 `Aliyun API key is not configured.`
  - Verified root cause (inferred): `sales-agent` 当前运行模型路径依赖阿里云模型配置，但本地运行态缺少可用 Aliyun API key，导致聊天链路在模型调用阶段提前失败；同次响应里 `effectiveToolNames` 已包含 CloudCC 相关工具，说明问题不在工具暴露面。
  - Evidence:
    - `POST /ai/chat` with `agentId=sales-agent` and CloudCC query question -> `answer="Aliyun API key is not configured."`
    - Same response shows `effectiveToolNames=["rag-search","cloudcc_pageQuery","quote-generator","cloudcc_getStandardObjects","cloudcc_getCustomObjects","cloudcc_getObjectFields","get_pending_approvals"]`
    - Same response shows `runtimeExecution.contextSnapshot.toolInvoked=false`
  - Status: open (blocks assistant-entry CloudCC smoke, but does not change the separate CloudCC credential failure above).

## Resolved / Superseded

- ISSUE-2026-05-11-meeting-minutes-spoken-trigger-missed:
  - Symptom: 用户在与智能体对话中说“开始进行会议纪要”后，没有滑出实时会议纪要面板。
  - Verified root cause: FEAT-029 前端触发判断只匹配精确短语“开始会议纪要/开始会议记录/开始会议听记/开启会议纪要/开启会议记录/开启会议听记”，未覆盖口语中常见的“开始进行会议纪要”。
  - Resolution (2026-05-11):
    - 新增 `frontend/src/assistant/meetingMinutesCommand.ts`，用独立 helper 识别自然口语触发。
    - `AssistantApp.tsx` 改为复用该 helper。
    - 新增 `meetingMinutesCommand.test.ts` 覆盖“开始进行会议纪要”等触发和解释型问题不误触发。
  - Verification (2026-05-11):
    - `frontend npm run test -- meetingMinutesCommand.test.ts` -> success。
    - `frontend npm run build` -> success（Vite chunk-size warning 保留）。
    - target `git diff --check` -> success。
  - Status: resolved.

- ISSUE-2026-05-11-iflytek-asr-config-rejected:
  - Symptom: 用户填写“讯飞实时转写”配置后，FEAT-029 真实转写 smoke 未跑通；后端 `/ws/asr?provider=iflytek&speakerDiarization=true` 在 start 后返回泛化 `invalid ws text message`。
  - Verified root cause: `iflytek_asr` 组织配置已启用且 Secret 加密存储；`realtimeUrl` 已从 Spark Chat 地址修正为 FEAT-029 要求的 AST 实时语音转写地址，但讯飞 AST WebSocket Upgrade 仍返回 `35010 AccessKeyId Not Exists`。这说明当前 Access Key ID/APIKey 未被实时语音转写大模型 AST 服务接受，或该 App 尚未开通对应服务/额度。
  - Evidence:
    - `GET /integrations` 返回 `iflytek_asr.enabled=true`，App ID / Access Key ID present，Secret masked as `iflytek-****`。
    - Sanitized DB check after URL correction: `accessKeySecret` is JSON object, App ID length `8`, Access Key ID length `32`, configured URL `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1`。
    - Backend `/ws/asr` smoke returned `{"type":"error","message":"invalid ws text message"}` after `connected`。
    - Raw WebSocket Upgrade to `wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1` returned `35010 AccessKeyId Not Exists` both with official minimal parameters and with `role_type=2/pd=com`。
    - 2026-05-11T15:18:28Z retest after user confirmed quota enabled still returned the same `35010 AccessKeyId Not Exists` response from Iflytek.
  - Resolution (2026-05-11):
    - 用户提供正确 AppID/APIKey/APISecret 后，已重新写入 `iflytek_asr` 并加密存储 Secret。
    - 修复后端讯飞 provider：等待上游 `data.action=started` 后再通知客户端发音频；适配 `data.cn.st.rt[].ws[].cw[]` 文本结构；为 Java WebSocket 补 `request(1)`；串行化 binary audio sends。
    - 修复前端 ASR hook：等待服务端 `status started` 后再发送 PCM。
  - Verification (2026-05-11):
    - Raw Iflytek handshake -> `101 Switching Protocols`。
    - Standalone Java probe -> received raw ASR result events。
    - Backend `/ws/asr` with generated 16k PCM -> received `partial/final` transcript and speaker metadata。
  - Status: resolved.

- ISSUE-2026-05-10-v1-8-github-release-not-pushed:
  - Symptom: 用户反馈 `V1.8` 没有发布成功。
  - Verified root cause: ECS/ACR 发布已完成，但 GitHub 远端 `origin/main` 仍停在 `V1.7` commit `bd4b8c9`，远端 tag 列表也没有 `V1.8`；本地仓库处于 detached HEAD，`V1.8` commit/tag 只存在本地。
  - Resolution (2026-05-10):
    - 执行 `git push origin HEAD:main`，将远端 `main` 推进到 `1b2ea27c55660d094174a1544199157f8ba8321d`。
    - 执行 `git push origin V1.8`，创建远端 `V1.8` tag。
  - Verification (2026-05-10):
    - `git ls-remote --heads origin main` 返回 `1b2ea27c55660d094174a1544199157f8ba8321d`。
    - `git ls-remote --tags origin 'V1.8*'` 中 `V1.8^{}` 返回 `1b2ea27c55660d094174a1544199157f8ba8321d`。
    - ECS 仍为 `CICI_IMAGE_TAG=V1.8`，六容器 healthy，后端 health `UP`，frontend `nginx -t` 成功。
    - Browser 渲染验证主域为 SalesMost AI Suite 综合站，autoservice 子域为 AgentCiCi 登录页。
  - Status: resolved.

- ISSUE-2026-04-17-jdk25-mockito-inline:
  - Symptom: targeted backend test execution (`mvn -q -Dtest=ChatRealtimeIntegrationTest test`) previously failed before entering the test body with Mockito inline Byte Buddy self-attach initialization errors on JDK 25.
  - Previous root cause: local Maven runtime used JDK 25 and Spring Boot's `ResetMocksTestExecutionListener` aborted test startup with `Could not initialize plugin: org.mockito.plugins.MockMaker`.
  - Resolution (2026-05-03):
    - Re-verified the exact targeted command under Maven's active Java runtime.
    - Current `mvn -version` shows Maven using Java `25.0.2`.
    - `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` now succeeds.
  - Verification (2026-05-03): Surefire report `com.codehouse.ciciassistant.ai.ChatRealtimeIntegrationTest.txt` shows `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; no `Mockito`, `ByteBuddy`, or `Could not initialize plugin` failure appeared.
  - Status: resolved; no current JDK25 Mockito blocker reproduced.

- ISSUE-2026-05-03-skill-import-code-held-by-soft-delete:
  - Symptom: 管理端导入技能 zip 并创建时返回 `Skill code already exists: email-marketing-campaign2`，但用户已经在页面中手动删除过同 code 技能。
  - Verified root cause: 自定义 Skill 删除采用软删除，旧 `skill_definition` 记录被标记为 `lifecycle_status=DELETED` 并隐藏，但数据库唯一索引 `uk_skill_definition_org_code` 仍占用原 `skill_code`；导入创建阶段复用 `createSkill(...)`，因此同 code 包会被判定为已存在。
  - Resolution (2026-05-03):
    - `SkillDefinitionEntity.markDeleted(...)` 删除时将旧记录 `skill_code` 归档为 `原code__deleted_{id}`，释放原 code 给后续导入或新建。
    - `SkillDefinitionService.createSkill(...)` 遇到同 code 的历史 `DELETED` 记录时，先归档并 `saveAndFlush` 旧记录，再创建新 Skill。
    - 新增 Flyway `V36__archive_deleted_skill_codes.sql`，处理已经软删除但仍占用原 code 的历史数据。
  - Verification (2026-05-03): `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest test` -> success; `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` -> success.

- ISSUE-2026-04-30-assistant-claims-claude-on-bailian:
  - Symptom: 前台询问“你现在自己在调用的是什么大模型吗？”时，思思回答“基于 Claude（Anthropic 的 Claude 模型）构建”，但用户后台实际接入的是百炼。
  - Verified root cause: 数据库中 `anthropic` provider 处于 disabled 且 API key 为空，`demo-org/chat` 路由为 `aliyun-bailian / deepseek-v4-pro`，`cici-system.model=deepseek-v4-pro`，相关 agent system prompt 和 skill prompt 未包含 Claude；错误回答来自模型在缺少“当前运行 provider/model”事实时对自身身份的幻觉。
  - Resolution (2026-04-30):
    - `ChatOrchestratorService.buildInitialMessages(...)` 新增运行模型上下文 block，注入当前服务端模型供应商和模型名称。
    - 对模型身份类问题增加约束：只能依据运行上下文回答，不得在 provider/model 不匹配时自称 Claude、Anthropic、OpenAI、GPT、Gemini。
    - 新增 `ChatOrchestratorServiceModelIdentityTest` 覆盖 `aliyun-bailian / deepseek-v4-pro` 的提示块。
  - Verification (2026-04-30): `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatOrchestratorServiceModelIdentityTest test` -> success; `backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` -> success.
  - Note: 当前数据是“百炼供应商 + deepseek-v4-pro 模型”，不是 qwen；若产品预期是通义千问，需要调整 chat 模型路由或智能体 model 字段。

- ISSUE-2026-04-30-workbench-streaming-placeholder-overwritten:
  - Symptom: 会话工作台中，用户发送消息后助手回复区域有时先消失，等待一段时间后再整段出现最终回复，看起来不是流式输出。
  - Verified root cause: 前端先乐观插入用户消息与空助手占位，但后端流式链路会先持久化 user turn，再执行工具调用/模型生成，assistant turn 最后才持久化；这个窗口内 `loadWorkbenchMessages(..., true)` 可能拉到“只有用户消息”的服务端旧历史，并覆盖本地占位。占位被覆盖后，后续 `delta` 追加逻辑发现最后一条不是助手消息，无法继续追加，最终只能等结束后重拉完整历史。
  - Resolution (2026-04-30):
    - 新增 `frontend/src/assistant/chatMessageState.ts`，统一保护本地流式消息状态。
    - `loadConversationMessages` / `loadWorkbenchMessages` 遇到远端历史缺少有效助手内容时保留本地助手占位或部分流式文本。
    - delta 到达时如尾部助手消息已丢失，会自动补回助手气泡并继续追加。
    - 工作台提交时同步更新 `conversationMessages[sessionId]`，避免工作台消息与历史缓存分叉。
    - 移除工作台 effect 对 `activeWorkbenchThoughts.length` 的依赖，状态机提示变化不再导致历史重拉。
  - Verification (2026-04-30): `frontend npm run build` -> success; `frontend npm test` -> success (`3` files, `12` tests，含 `chatMessageState.test.ts`)。

- ISSUE-2026-04-23-mcp-smoke-blocked-by-admin-auth-scope:
  - Symptom: MCP cache Phase 1 implementation后，计划执行真实管理端 smoke（`/mcp-servers`、`/mcp-servers/{id}/tools`、`/mcp-servers/{id}/discover`）时，当前可登录账号链路无法稳定取得可用 ORG_ADMIN 权限上下文。
  - Verified root cause (updated): 2026-04-23 的阻塞已不再复现。本轮真实运行态重新验证显示，`13800138111` 通过 `/auth/sms/send` + `/auth/sms/login` 登录后，`/auth/me` 返回 `roles=["ORG_ADMIN","PLATFORM_ADMIN"]`，且可正常访问 `/mcp-servers`；当时的问题更接近局部登录态/上下文异常，而不是当前代码路径上的持续性权限缺陷。
  - Resolution (2026-04-30):
    - 使用真实本地短信登录链路重新完成管理员 smoke，而不是仅依赖集成测试结论。
    - 验证 `GET /mcp-servers` 可返回真实 MCP server 列表。
    - 验证 `GET /mcp-servers/1/tools` 可返回缓存工具快照（`toolCount=43`，`cacheStatus=ready`）。
    - 验证 `POST /mcp-servers/1/discover` 可成功刷新缓存时间戳。
    - 追加验证 `ORG_USER` 登录后访问 `GET /mcp-servers` 仍返回 `需要组织管理员权限`。
  - Verification (2026-04-30):
    - `POST /auth/sms/send` + `POST /auth/sms/login` with `mobile=13800138111` -> login success (`roles=["ORG_ADMIN","PLATFORM_ADMIN"]`)
    - `GET /auth/me` with `13800138111` token -> success
    - `GET /mcp-servers` -> success
    - `GET /mcp-servers/1/tools` -> success (`toolCount=43`, `cacheStatus=ready`)
    - `POST /mcp-servers/1/discover` -> success
    - `POST /auth/sms/send` + `POST /auth/sms/login` with `mobile=13800138121` -> login success (`roles=["ORG_USER"]`)
    - `GET /mcp-servers` with `13800138121` token -> `{"success":false,"message":"需要组织管理员权限"}`

- ISSUE-2026-04-30-platform-console-dev-proxy-route-collision:
  - Symptom: 平台登录成功后，`/platform/skills` 与 `/platform/tools` 页面直接显示 `Unexpected token '<', "<!doctype "... is not valid JSON`，平台概览/审计也无法稳定回显真实接口数据。
  - Verified root cause: 平台前端页面直接 `fetch("/platform/**")`，与 React Router 的 `/platform/**` 页面路由共用同一前缀；在 Vite 开发环境下，请求会落回前端 `index.html`，前端随后把 HTML 当 JSON 解析。
  - Resolution (2026-04-30):
    - 前端平台页接口前缀统一切换为 `/api/platform/**`。
    - `frontend/vite.config.js` 与 `frontend/vite.config.ts` 新增 `/api/platform` proxy rewrite，将请求转发到后端 `/platform/**`。
  - Verification (2026-04-30):
    - 浏览器人工回归：`/platform/login` -> `/platform` -> `/platform/skills` -> `/platform/tools` -> `/platform/audit` 均可正常加载。
    - 平台技能页成功创建 `core-default` 草稿版本 `v3`，平台审计页出现对应创建记录。
    - `frontend npm run build` -> success.

- ISSUE-2026-04-24-cici-session-history-not-injected:
  - Symptom: 思思在同一会话第二轮中会重复询问上一轮已经确认的信息，表现为“界面会话连续，但模型协作不连续”。
  - Verified root cause: `ChatOrchestratorService.buildInitialMessages(...)` 早期只注入 system prompt、当前用户问题、RAG 内容与长期用户记忆，没有回灌同一 `sessionId` 的历史消息；同时系统缺少会话级执行状态层来表达“已确认动作 / 暂缓动作 / 当前对象 / 缺失字段”。
  - Resolution (2026-04-24):
    - 新增 `V22__chat_session_state.sql` 与 `ChatSessionStateService`，落地会话状态持久层。
    - `ChatOrchestratorService` 已注入最近历史消息与 session state 块，并在工具调用后写回会话状态。
    - 新增 `GET /ai/sessions/{sessionId}/state` 调试接口，便于排查同 session 连续性。
  - Verification (2026-04-24): `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> success; `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=ChatRealtimeIntegrationTest test` -> success.

- ISSUE-2026-04-21-agent-runtime-not-bound-to-published-workflow:
  - Symptom: Agent Builder 可以保存、编译、发布版本，但线上聊天链路早期不会按已发布版本执行对应 workflow。
  - Verified root cause: 早期 `publishVersion(...)` 仅更新 `agent_definition.published_version_id` 与版本 `publish_status`；聊天运行时仍通过 `SkillResolverService` 解析 skills / tools / kb，并未读取 `agent_workflow_version.workflow_code`、`workflow_manifest` 或 `workflow_preview`。
  - Resolution (2026-04-30):
    - 运行时已优先读取已发布版本的 `workflow_manifest.dependencies` 与 `workflow_code`，并透出 `runtimePolicy`、`runtimeExecution`、`contextSnapshot`。
    - 发布时新增 `agent_workflow_skill_ref`，让已发布 Agent pin 住具体 skill snapshot，避免后续 skill 编辑导致运行时漂移。
  - Verification (2026-04-30): `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest test` -> success; 其中包含 `shouldPreferPublishedWorkflowDependenciesAtRuntime`、`shouldSwitchRuntimeDependenciesAcrossPublishStates`、`shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits`。

- ISSUE-2026-04-21-agent-debug-still-simulated:
  - Symptom: Agent Builder 中“试运行”曾只能高亮前端模拟路径，无法给出真实后端执行 trace、工具调用或命中分支证据。
  - Verified root cause: 早期 frontend `runDebug()` 只依赖 `simulateDebugTrace(...)`，且仓库中没有完整接线到真实 runtime 调试结果。
  - Resolution (2026-04-30):
    - 后端新增并稳定使用 `POST /agents/{agentId}/debug`，返回 `runtimeSource/publishedVersionId/executionStatus/executionTrace/contextSnapshot/policyBundle/resolvedSkillVersions/runtimeGovernanceNotes`。
    - `frontend/src/assistant/AgentBuilderShell.tsx` 已改为“后端真实运行优先”，成功时直接展示 runtime trace / governance 摘要，仅在接口异常时才回退前端模拟。
  - Verification (2026-04-30): `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=OrchestratorIntegrationTest#shouldUsePublishedWorkflowInDebugRuntime test` -> success; `frontend` `npm run build` -> success.

- ISSUE-2026-04-29-kb-delete-leaves-vector-points:
  - Symptom: 管理端删除知识文档后，源文件和 `kb_document` 会被删除，但对应 `kb_chunk` 与向量库 point 没有同步清理；删除整个知识库也只删除 `knowledge_base` 主表。
  - Verified root cause: `KnowledgeBaseService.deleteDocument(...)` 仅删除源文件和文档行，`deleteKnowledgeBase(...)` 仅调用 `kbRepository.deleteByIdAndOrgId(...)`；旧 `VectorStoreClient` 只有 `upsert/search` 契约，没有删除接口；旧 `kb_chunk` 缺少可靠 `document_id` 字段。
  - Resolution (2026-04-29):
    - 新增 `kb_chunk.document_id/status/enabled/deleted_at/chunk_index/content_hash` 等生命周期字段，文档/知识库保留可检索状态闸门字段。
    - 扩展 `VectorStoreClient` 为结构化 upsert/search，并支持按 vectorId、document、knowledgeBase 删除；memory 与 Qdrant 适配器均已实现。
    - `KnowledgeBaseService` 删除文档、删除知识库、取消发布、重建索引会同步处理 DB chunk、源文件、向量点和 Agent KB 绑定。
    - `RagService` 对向量命中做 DB 二次过滤，DB fallback 同样只返回 ACTIVE KB + PUBLISHED document + ACTIVE chunk。
  - Verification (2026-04-29): `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` -> success.

- ISSUE-2026-04-24-skill-authoring-fallback-misclassifies-campaign-flow:
  - Symptom: 在管理端“自然语言创建技能”中输入明确的“邮件市场营销活动”流程后，系统生成的草稿会偏成 `CRM 线索分诊`，与用户原始步骤和工具名明显不匹配。
  - Verified root cause: 默认 `skill-authoring` 场景没有可用模型时，`BuiltinSkillCreatorService` 会退回启发式生成；旧设计把少量内置行业模板当成主要先验，导致生成时优先猜“最像哪个内置场景”，而不是忠实保留 sourceText 中的目标、事实、工具名和步骤。
  - Resolution (2026-04-24):
    - 模型提示词改为强调“不要依赖内置行业模板猜业务”，而是优先保留 sourceText 中的明确事实。
    - 启发式 fallback 改为通用结构化提取，不再依赖审批/CRM/合同等固定模板分支。
    - 工具白名单推断继续优先匹配 sourceText 中显式出现的工具名。
    - `draftSpecText` 保留用户编号步骤；若没有编号步骤，也会保留 sourceText 中的关键事实句。
    - 更新 `SkillAuthoringIntegrationTest` 覆盖通用生成与营销活动流程两类场景。
  - Verification (2026-04-24): `backend` `mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillAuthoringIntegrationTest test` -> BUILD SUCCESS.

- ISSUE-2026-04-22-v18-migration-blocks-h2-integration-tests:
  - Symptom: Every `@SpringBootTest` integration test (`OrchestratorIntegrationTest`, `ManagementConsoleIntegrationTest`, `AuthFlowIntegrationTest`, `ChatRealtimeIntegrationTest`, and any new one) failed at context load with `Migration V18__user_memory_tables.sql failed`.
  - Verified root cause: `V18__user_memory_tables.sql` used three pieces of PostgreSQL-only SQL that the H2 test runtime could not parse — the `TIMESTAMPTZ` type alias, `DEFAULT NOW()`, and the partial unique index `CREATE UNIQUE INDEX ... WHERE memory_key IS NOT NULL`. In addition the `user_id` column was declared `BIGINT` while `UserMemoryEntity.userId` is a `String`, which would have broken `hibernate.ddl-auto=validate` even on Postgres.
  - Resolution (2026-04-22):
    - Rewrote V18 to the project's cross-DB convention: `TIMESTAMP` (not `TIMESTAMPTZ`), no `DEFAULT NOW()` (entity sets `createdAt/updatedAt` via `Instant.now()`), `user_id VARCHAR(64)` to match the entity, and a regular `UNIQUE INDEX` on `(org_id, user_id, agent_id, memory_key)` — standard SQL treats `NULL` as distinct in unique indexes (both PostgreSQL and H2 default behaviour), so the original "允许 NULL 语义键多次并存、带 key 则唯一" intent is preserved without the partial-index `WHERE` clause.
    - Fixed two cascading preexisting test-design flakes that only surfaced once V18 stopped blocking context load: `ChatRealtimeIntegrationTest` asserted the wrong SSE `event:` format (literal `"event: connected"` with a space vs. the actual `"event:connected"` without), and `OrchestratorIntegrationTest` had both methods driving SMS login with the same admin mobile and asserting an exact `callCount=1` while the Spring test context is shared across tests — changed to distinct mobiles + `>= 1` count.
  - Verification (2026-04-22):
    - `mvn test` at `backend/` → `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`, including `AuthFlowIntegrationTest` 6/6, `ManagementConsoleIntegrationTest` 1/1, `ChatRealtimeIntegrationTest` 1/1, `OrchestratorIntegrationTest` 2/2, `TavilyToolServiceTest` 10/10, and the restored `TavilyCatalogIntegrationTest` 1/1.
  - Note: because V18 was never successfully applied anywhere (the BIGINT vs String mismatch would have tripped `ddl-auto=validate` on any real Postgres boot as well), the migration content change does not require Flyway repair in existing environments — it is effectively a first-apply.
- ISSUE-2026-04-18-flyway-v12-checksum-mismatch:
  - Symptom: backend could not start with default local profile because Flyway validation failed (`Migration checksum mismatch for migration version 12`).
  - Verified root cause: local PostgreSQL `flyway_schema_history` stored checksum `-61204255` for `V12`, while the repository migration file resolves to `-241842311` (migration file was legitimately amended after apply).
  - Resolution (2026-04-19): aligned DB `flyway_schema_history.checksum` for version `12` to the value Flyway computes for the current file (equivalent intent to `flyway repair` for that row); backend starts with `local` profile; user restarted services.
  - Status: resolved (local-dev governance); other environments should use the same repair/reset policy if they hit the same mismatch.
- ISSUE-2026-04-18-skill-bindings-unique-conflict:
  - Symptom: `PUT /skills/agents/{agentId}/bindings` returned `500 Unexpected server error`.
  - Verified root cause: delete-before-insert happened in one transaction without immediate flush, causing PostgreSQL unique constraint `uk_agent_skill_binding_org_agent_skill` conflict on reinsert.
  - Resolution: fixed on 2026-04-18 by adding `agentSkillBindingRepository.flush()` after `deleteByOrgIdAndAgentId(...)` in `SkillDefinitionService.replaceBindings(...)`.
  - Verification: rerun API smoke and confirmed binding update + readback success.
- ISSUE-2026-04-21-user-workflow-feishu-copy-stale:
  - Symptom: 个人工作流的飞书私信能力代码已接入运行时，但编译结果和前端设置页仍显示“待接入/预留”，会误导对功能进展的判断。
  - Verified root cause: `UserWorkflowService.compile(...)` 在 `notificationTarget.type = feishu_dm` 时仍追加旧告警“主动飞书推送接口仍待接入”；`MyWorkflowStudio` 的通知方式下拉仍显示“飞书私信（预留）”。
  - Resolution (2026-04-21): 编译告警改为“链路已接入、仍需端到端验证”，前端通知方式文案改为“飞书私信”；随后使用真实已绑定用户完成 `run-now` smoke，避免文案与真实能力继续分叉。
  - Verification: `backend` `mvn -q -DskipTests compile` -> success; `frontend` `npm run build` -> success; `POST /me/agents/cici-system/workflow/run-now` trace -> notification `status=SENT`.
- ISSUE-2026-04-21-user-workflow-bundle-null-published-version:
  - Symptom: 打开“个人设置 > 我的工作流”立即出现 `Unexpected server error`，后续编译/发布动作也会被页面 refresh 失败拖垮。
  - Verified root cause: `UserWorkflowController.get(...)` 使用 `Map.of(...)` 组装 `agent` 返回体时直接放入可空 `publishedVersionId`；当共享助手尚无已发布版本时抛空指针并被统一包装为 500。
  - Resolution (2026-04-21): 改为使用 `LinkedHashMap` 组装 `agent` 返回体，允许 `publishedVersionId = null` 正常下发。
  - Verification: `GET /me/agents/cici-system/workflow` on local `8080` -> success.
- ISSUE-2026-04-21-user-workflow-false-time-parse:
  - Symptom: 个人工作流文案中出现普通数字时，编译结果可能被错误识别为定时任务；发布时 `materializeTriggers` 进一步抛 `DateTimeException`，导致“发布最新版本”失败。
  - Verified root cause: `inferTrigger(...)` 的时间正则过宽，会把诸如 `8080` 这样的普通数字误判成 hour；`computeNextFire(...)` 对越界 hour/minute 缺少兜底。
  - Resolution (2026-04-21): 仅在存在明确时间标记（如 `: / 点 / 时 / 上午` 等）时才按 schedule 解析，并对 hour/minute 越界值回退为 `MANUAL`/`null`。
  - Verification: text `修复后 8080 再编译一次` now compiles to `triggerType=MANUAL`; `POST /me/agents/cici-system/workflow/publish` for `v4` -> success.
- ISSUE-2026-04-17-external-session-owned-by-pairing-user:
  - Resolution: fixed on 2026-04-17 by changing external-channel session visibility to org/agent scope instead of the previously inferred pairing-user scope.
  - Product rule captured: external Feishu users are conversation participants, while system login users are CiCi operators who may view and later take over those conversations.
- ISSUE-2026-04-17-feishu-session-hidden-from-other-admins:
  - Superseded by `ISSUE-2026-04-17-external-session-owned-by-pairing-user`.
  - Note: the earlier admin-only fix was an intermediate step and did not fully match the intended product semantics.
- ISSUE-2026-04-17-feishu-conversation-list-not-wired:
  - Resolution: fixed on 2026-04-17 by wiring the assistant workspace to real `/ai/sessions` data, adding `/ai/sessions/{sessionId}/messages`, and replacing the static frontend thread list with live conversation loading plus periodic refresh.
  - Runtime acceptance: real Feishu environment end-to-end verification confirmed in-session on 2026-04-17 (external single-chat -> agent bridging -> web realtime update without manual refresh).
- ISSUE-2026-04-01-milvus-runtime: superseded (Milvus removed; stack moved to Weaviate then **Qdrant**).

## Watch Items

- Feishu bot runtime verification:
  - Verified facts: codebase already contains Feishu SDK dependency, backend pairing/event-bridge/reply chain, admin configuration entry, and assistant workbench pairing UI.
  - Latest confirmation: product acceptance run has confirmed real Feishu message round-trip and realtime web visibility in the target flow.
  - Status: closed for current milestone (keep routine regression monitoring).
- Production Qdrant: enable `api-key` and TLS as required; set `app.kb.qdrant.api-key` in config.
