---
kind: issue-list
version: 3
updated_at: 2026-08-13T02:00:00Z
updated_by: codex
status: active
---

# Issue List

## ISSUE-2026-08-13-devautopilot-intake-history-mismatch

- Symptom: DevAutopilot 详情抽屉中的 `REQ-6F34ECF3` 只有通用产品经理摘要、1 条通用验收和 1 条通用开发者验证项，与确认前可见草稿的 4/5/4 条具体内容不一致。
- Verified root cause: 记录创建于 2026-08-12 22:37 CST，字段保真修复版 backend 于 23:28 CST 才部署；Semattice 忠实保存了旧解析器生成的占位字段。新页面读取的是同一条旧记录，现有功能没有受治理的历史草稿纠正路径。
- Evidence: AgentCiCi `chat_message` 22 保存完整草稿、23 保存确认、24 保存该 record ID 回执；Semattice 记录 revision=1 的 `summary/acceptance/intake.assumptions` 均为旧占位内容。
- Resolution: TASK-296 增加同租户会话约束、产品经理 SERVICE `runtime.record.update`、乐观锁、规范语义逐字段回读、内容摘要和幂等纠正。首次 revision 2 回读进一步发现 Markdown `---` 被解析为 `--` 验收项且分类理由混入产品经理分析；最终规则拒绝纯标点语义项并独立保存 `classification_reason`。本地记录已纠正为 revision 3，4/5/4 条内容与草稿一致，重复调用不增加 revision。
- Status: resolved locally; UAT/production unchanged.

## Open Issues

- ISSUE-2026-08-12-admin-member-public-id-stale:
  - Symptom: 组织管理端添加全新成员失败，页面提示 `Global account public ID is not available`。
  - Verified root cause: `AdminUserService` 在插入全局账号后用同事务 Repository `findById` 回读，JPA 一级缓存返回触发器执行前的旧实体，导致 Keycloak provisioning 读取到空 `public_id`；租户 Owner 创建链路已有 `EntityManager.refresh`，成员邀请链路遗漏。
  - Safety evidence: UAT 故障目标的 account/identifier/member/identity 均为 0，事务完整回滚，无半成品数据。
  - Resolution progress: TASK-290 / FEAT-174 已改为 flush 后 refresh，并增加 provisioning 前公共编号存在的回归测试；提交 `ab1b02c` 已进入本地 main，定向测试、package 和本地全栈 backend 验证通过。
  - Status: code verified locally (critical; UAT 尚未发布修复，真实成员新增待业务验收)。

- ISSUE-2026-08-10-new-tenant-owner-missing-oidc:
  - Symptom: 平台开通的新租户 Owner 无法通过生产 OIDC 登录；目标邮箱和手机号在 AgentCiCi 与 Keycloak 均无有效身份记录，登录事件为 `user_not_found`。
  - Verified root cause: 生产 `2.8.58 / 63371f92d9ae` 的 `PlatformTenantLifecycleService.createTenant` 只创建本地账号、密码凭据和 Owner 成员，不调用 `KeycloakIdentityProvisioningService`；管理访问日志也未出现该目标的成功成员邀请请求。
  - Resolution progress: TASK-276 / FEAT-165 已发布生产 `2.8.60 / 451f797e61df`；新建链路、同一 Owner 身份协调、无有效 Owner 时复用已激活 HUMAN 的受控恢复均已上线。UAT 正式页面已验证 `OWNER/ACTIVE` 与统一身份可登录；生产未直接写库或设置密码。
  - Status: code released to production; target account reconciliation remains open because the available browser has no PLATFORM_ADMIN session.

- ISSUE-2026-08-10-uat-secret-cipher-dev-fallback:
  - Symptom: UAT `2.8.60-beta.1` 后端启动日志提示 `SecretCipherService` 使用开发回退密钥；容器环境变量名称回读确认未注入 `APP_SECRET_KEY` 或 `APP_SECURITY_SECRET_KEY`。
  - Impact: 不影响本次 Owner 身份与登录链路，但 UAT 中需要可逆加密的集成配置缺少受管、可轮换的环境密钥，不能作为生产安全基线。
  - Resolution plan: 先只读盘点 UAT 已有密文及加解密兼容性，再由配置事实源生成并注入 root-only 受管密钥，执行迁移/回读和回滚演练；不得直接更换密钥造成历史密文不可解。
  - Status: open (high; non-blocking for TASK-276/277 UAT functional acceptance, blocking for production parity claim).

- ISSUE-2026-08-10-keycloak-ownership-attributes-not-managed:
  - Symptom: UAT Keycloak 新 HUMAN User 已正确创建和绑定，但 Admin API 回读中 `agentcici_public_id`、`agentcici_account_id` 自定义属性为空。
  - Verified root cause: Realm User Profile 仅声明 `username/email/firstName/lastName/phoneNumber/role/tenant`，`unmanagedAttributePolicy` 未启用；Keycloak 对 provisioning 请求中的两个未声明属性静默丢弃。
  - Impact: 不影响当前 subject 绑定、邮件激活和邮箱登录，但本地 binding 丢失后的严格 ownership recovery 无法满足双属性证明，会按设计失败关闭。
  - Resolution plan: 由 Keycloak 配置事实源所有者声明两个 server-managed、用户不可编辑属性，先在 UAT readback，再评估生产变更；不得以一次性数据库或未受管 Realm patch 代替。
  - Status: open (high; does not block TASK-276 UAT login test).

- ISSUE-2026-08-05-ai-table-auth-header:
  - Symptom: 已登录用户在 `https://x.agentcici.com/app` 打开 AI表格时，目录请求显示 `Authentication required`，无法读取业务对象。
  - Verified root cause: `AiTableBusinessObjectList` 对 `/ai-table/catalog` 和记录查询使用裸 `fetch`，仅设置 `credentials: same-origin`；用户端的实际登录态由 `LS_ASSISTANT_TOKEN` 中的 Bearer Token 提供。该请求没有 `Authorization`，被 AgentCiCi 鉴权层拒绝，未进入 OACT 签发或数据平台调用。
  - Resolution: TASK-266 已统一改用 `authFetch`，保持既有 Token 更新重试语义和同源请求边界；已发布生产 `2.8.49 / 760776a354f5`。
  - Verification: 前端定向 2 项、全量 34 文件/208 项测试与生产构建通过；线上 backend/frontend healthy，版本、Nginx、x HTTPS/HTTP 与受保护接口的匿名/无效 Bearer 401 边界均通过。
  - Status: resolved by TASK-266 on 2026-08-05; awaiting authorized member data readback.

- ISSUE-2026-07-24-oidc-state-cross-origin:
  - Symptom: 用户完成 Keycloak SSO 后，`https://x.agentcici.com/auth/oidc/callback` 返回 `Invalid OIDC login state`，无法进入系统。
  - Verified root cause: `agentcici.com/auth/oidc/login` 与 `x.agentcici.com/auth/oidc/login` 均创建 host-only `CICI_OIDC_STATE` Cookie，但 Keycloak client 的 redirect URI 固定为 `https://x.agentcici.com/auth/oidc/callback`。从主站域发起时 Cookie 不会发送到 `x`，state 比较在读取 Redis 事务前失败。
  - Resolution: TASK-244 将 OIDC start 规范到 callback 所属源站；保留 host-only Cookie 和 fail-closed 比较，不扩展 Cookie Domain。
  - Verification: `KeycloakOidcLoginServiceTest` 3/3、后端编译和静态检查通过；生产 `2.8.13` 的 backend/frontend 健康、Nginx 与入口 smoke 均通过，主站已先跳规范 `x` host，规范入口才写入 state Cookie。
  - Status: resolved by TASK-244 on 2026-07-24; awaiting user login confirmation.

- ISSUE-033:
  - Symptom: 工作台选择指定技能后，回复行为没有稳定体现所选技能；只有用户在消息正文中再次声明技能时，模型才更稳定遵循该技能。
  - Verified facts: 前端会提交 `activeSkillCode`，但当前后端仅将它用于技能专属工具授权；提示词和文件型参考文档仍并列注入所有绑定技能。
  - Scope: 强制业务上下文、Trace 选择状态和两个运行监控视图，不改技能绑定或权限模型。
  - Resolution: TASK-225 让有效选择只注入所选技能的业务流程、输出契约和文件型参考文档，同时保留平台安全与 Agent 直接工具边界；运行记录和两个监控视图已明确显示选择、有效上下文、实际激活及未采纳原因。
  - Verification: 后端定向测试、编译、前端完整单测、构建和静态检查均通过；已发布生产 `2.8.4 / 2f2f1a013ec2` 并通过健康、版本、HTTPS 与匿名鉴权边界 smoke。受保护工作台/Trace 的真实桌面交互仍需具备授权组织会话后补验。
  - Status: resolved by TASK-225 on 2026-07-22.

- ISSUE-2026-07-15-session-not-found-status-mapped-500:
  - Symptom: a signed-in user requesting another user's non-org-scoped `/ai/sessions/{id}/messages` receives HTTP 500 `Unexpected server error` rather than a non-disclosing 404/403.
  - Verified facts: the response contains no `data` and no other user's content, so tenant/user isolation holds. `queryVisibleSession` correctly returns empty and throws `ResponseStatusException(HttpStatus.NOT_FOUND)`, but `GlobalExceptionHandler` has no dedicated handler and its generic exception path maps the status exception to 500 while producing ERROR logs.
  - Scope: pre-existing HTTP/error-observability semantics discovered during TASK-211's negative permission check; it does not affect CRM ranking, SSE/OpenAPI text integrity, or data isolation and is not included in TASK-211's backend-only stream patch.
  - Recommended resolution: add a focused red/green controller exception-mapping test, preserve the non-disclosing reason, return the intended status without ERROR-level generic logging, and run cross-user/session regression separately.
  - Status: open; requires a separately assigned task.

- ISSUE-2026-07-09-cloudcc-custompage-bind-skill-gap:
  - Symptom: TASK-171 SSO closure requires updating a CloudCC customPage to the latest pagecomponent id through `cc-customization-expert-msapi`, but the skill write path fails even though publish and runtime loading work.
  - Verified facts: `cloudcc package pagecomponent customer-workbench . --dry-run` passed; `cloudcc publish pagecomponent customer-workbench .` published V7 id `6a4f2c24e4b0a577cbba1f4c`; `cloudcc bind pagecomponent . customer_interaction_workbench ...` failed with `Bind PageComponent Failed: 系统发生异常`; `cloudcc update customPage . customer_interaction_workbench @output/task171-custompage-bind-2.2.4.json` failed with `Save CustomPage Failed: 系统发生异常`.
  - Verified facts: `cloudcc verify injectionPage . customer_interaction_workbench --expected-component component-customer-workbench` returned `status=passed`, but its own output still reported `actualComponentIds=["6a4db950e4b0a577cbba1eca"]`, which is stale relative to the latest V7 id.
  - Verified facts: real CRM browser verification still loaded `component-customer-workbench-V7.0.js` by `compUniName` and completed SSO successfully, so the production runtime path is healthy; the defect is in repeatable customPage bind/update automation and stale-id validation.
  - Verified facts (2026-07-11): `cc-customization-expert-msapi 2.1.276-msapi` read back pagecomponent V10 and customPage V4 with the exact same component id `6a503defe4b0a577cbba1f8a`; `verify injectionPage --expected-component-id 6a503... --stale-policy warning` nevertheless emitted `stale_component_reference` because `actualVersions=[]`. Real CloudCC iframe loaded the workbench and passed the production multimodal flow, proving this instance is a missing-version-evidence false positive rather than a stale binding.
  - Recommended skill fixes: surface the underlying devconsole response body for bind/update failures; validate customPage `compList` / `pageContent` envelope against the documented lightning-devconsole contract before submit; compare id first; only report a version mismatch when runtime/version evidence is available; when `actualVersions` is empty, emit a distinct `runtime_version_unavailable` diagnostic instead of `stale_component_reference`.
  - Status: open (skill improvement follow-up; production embedded SSO is verified).

- ISSUE-2026-06-22-onechat-dns-nxdomain:
  - Symptom: `onechat.agentcici.com` direct DNS lookup returned NXDOMAIN from the current resolver during the `2.1.1` production smoke.
  - Verified facts: server-local HTTPS vhost smoke with explicit resolve returned HTTP 200, and public `https://onechat.agentcici.com/` with `--resolve onechat.agentcici.com:443:47.97.119.160` returned HTTP 200; `https://x.agentcici.com/` returned HTTP 200 through normal DNS.
  - Inferred root cause: DNS record for `onechat.agentcici.com` is missing, not propagated, or not visible to the tested public resolver.
  - Status: open (DNS/provider follow-up; release `2.1.1` is otherwise healthy on ECS).
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

- ISSUE-005:
  - Symptom: 产品经理已展示“缺陷受理草稿”，用户回复“确认提交缺陷”后没有创建 Semattice 缺陷，回执门禁显示本轮没有真实写入成功回执。
  - Verified root cause: 该模型回复遗漏不可见 `DEV_AUTOPILOT_INTAKE_V1` 标记；可见草稿兜底只识别“缺陷创建草案”和冒号字段，既不识别实际“缺陷受理草稿”标题，也不能解析 Markdown 表格，因此确认轮 `toolCount=0`，写入工具从未调用。
  - Resolution: TASK-288 统一识别需求、缺陷、变更的受理草稿/摘要，支持 Markdown 表格与冒号字段，并优先从父项目信息提取稳定 `DAS-*` 编号；可信写后回读门禁保持不变。
  - Verification: 故障同构定向测试、编排待处理草稿测试、回执门禁回归、后端 package 与本地 stack verify 通过；真实浏览器回读待有效本地 HUMAN 登录后补验。
  - Status: code resolved; local business acceptance pending.

- ISSUE-2026-08-11-ai-table-uat-metadata-scope:
  - Symptom: 已登录 UAT 租户进入 AI表格后无法读取业务对象，页面显示“业务数据服务暂时不可用”。
  - Verified root cause: UAT `cici-backend:2.8.60-beta.1` 实际 HUMAN OACT scopes 缺少 `metadata.read`；AI表格目录调用 `metadata.version.get-current`，Semattice 契约明确要求 `metadata.read`。缺失来自 AgentCiCi `docker-compose.uat-acr.override.yml`，不是租户数据为空。
  - Resolution: TASK-278 恢复 UAT HUMAN `metadata.read`，保留 SERVICE 独立 allowlist，并在测试发布入口和最终 Compose 回读中增加 `metadata.read + runtime.record.read` 门禁。
  - Verification: UAT `2.8.61-beta.1 / d4b273af39c2` 真实租户页面回读 6 个业务对象与真实空记录状态，原错误消失，console error/warning 为 0。
  - Status: resolved on 2026-08-11.

- ISSUE-032:
  - Symptom: 用户补充“每天 09:00”后系统返回 `IndexOutOfBoundsException`，未创建任务。
  - Verified root cause: `UserWorkflowService.CLOCK_PATTERN` 没有定义捕获组，但 `inferTrigger` 读取时段、小时和分钟三组；合法时钟文本解析时必然越界。
  - Resolution: TASK-223 将正则调整为对应的显式捕获组，并增加每日 09:00、下午 3:30 cron/nextFireAt 回归测试。
  - Verification: `mvn -q -Dtest=UserWorkflowServiceTest test` 与 `mvn -q -DskipTests compile` 均通过。
  - Status: resolved by TASK-223 on 2026-07-22; 未发布生产。

- ISSUE-2026-07-15-crm-deterministic-stream-single-delta:
  - Symptom: CRM 产品销量答案能正确返回，但页面等待后一次性出现完整正文，失去普通对话的流式输出体验；Agent OpenAPI streaming 同样只有一个正文事件。
  - Verified root cause: 生产 5 份 CRM SSE 均为 `phase ×3 → delta ×1 → done ×1`，唯一 `delta` 为 2,383 字符；OpenAPI streaming 也只有一个 2,383 字符 `message`。`ChatOrchestratorService` 的 CRM 确定性分支对完整格式化正文只调用一次 `safeSendDelta`，而前端逐 delta 更新和 Nginx buffering 配置均正常。
  - Resolution: TASK-211 复用服务端现有 `safeSendDeltaInChunks`，保持确定性正文、防泄漏、blocking、持久化和业务结果不变；OpenAPI bridge 同步修复分片边界空白保真。PR #6/#7 已合并并发布不可变 `2.7.7 / e47979167af8`。
  - Verification: TDD、135 项干净库 CRM 回归、SalesA 5 次 133-delta 流、blocking、SalesB、OpenAPI 133-message/history、权限清理、防泄漏和干净日志均通过。应用内 Browser fresh 会话在 composer 禁用时捕获同一气泡 50 字 partial，完成后为 2,100 字，console error/warning 0 且无横向溢出。
  - Status: resolved by TASK-211 on 2026-07-15.

- ISSUE-2026-07-08-customer-workbench-injection-whitepage:
  - Symptom: 用户反馈 CloudCC CRM 菜单“客户互动工作台”已经可见，但打开 `#/injectionComponent?page=customer_interaction_workbench&button=Home` 后页面主体为空白。
  - Verified root cause: 真实 CRM Web 登录自测复现白页；`detailCustomPage` 显示 customPage 仍指向旧 pagecomponent `6a4d348fe4b0a577cbba1ebf`，且 pageContent 中 `embedded=false`；页面加载的 `component-customer-workbench-V4.0.js` 仅暴露 `window["component-customer-workbench"]`，没有在 CRM 注入容器的 `<component-customer-workbench>` 标签上自动挂载 Vue 组件。
  - Resolution (2026-07-08): 发布 pagecomponent V5，线上组件 id `6a4db950e4b0a577cbba1eca`、apiName `custc_2026079sRcX7wv`，UMD bundle 增加自动挂载与 DOM iframe fallback，组件默认 `embedded=true`；再通过 devconsole developer-token API 更新 customPage `customer_interaction_workbench` 到 id `6a4dbc0ce4b0a577cbba1ecb`、renderVersion `V2.0`，页面内容指向新组件 id 并设为 `embedded=true`。
  - Verification (2026-07-08): Playwright/Chrome 使用用户提供的 CloudCC Web 账号登录后重开同一路由，页面加载 `component-customer-workbench-V5.0.js`，自定义元素和 iframe `https://x.agentcici.com/app?aiApp=customer-workbench` 正常渲染；截图 `output/playwright/task171-cloudcc-injection-fixed.png`。无 AgentCiCi 会话时 iframe 显示 AgentCiCi 登录页，这是后续 SSO/login handoff 问题，不属于 CloudCC 白页。
  - Status: resolved.

- ISSUE-2026-07-08-customer-workbench-menu-not-visible-outside-sales-cloud:
  - Symptom: 用户反馈在 CloudCC CRM 系统中没有看到“客户互动工作台”菜单和功能。
  - Verified root cause: CRM tab id `acf2026C53BE54B9R1Iu` 已存在，customPage/pagecomponent 链路也存在，但该 tab 只被选入 Sales Cloud `ace20220322Salesloud` 的 `selectedTabList`；在默认 `CloudCC` 应用以及市场云、服务云、商务云、客服服务云、项目管理系统、利润云中仍位于未选菜单，因此用户切换到这些应用时不可见。
  - Resolution (2026-07-08): 使用 setup service `/api/newApp/save` 在保留各应用原菜单顺序、应用名称、默认启动页和简档可见性的前提下，把现有 tab id `acf2026C53BE54B9R1Iu` 追加到全部 8 个应用。
  - Verification (2026-07-08): `/api/newApp/getAppTabs` 回读全部 8 个应用，返回 `appCount=8`、`selectedCount=8`、`selectedInAllApps=true`；`/api/customTab/queryTabList` 回读 tab label `客户互动工作台`、type `page`、lightning page `customer_interaction_workbench#lightning`；AgentCiCi 页面 URL 返回 HTTP `200`，未登录 API 返回预期 HTTP `401`。
  - Residual note: 若某个已登录 CRM 用户仍看不到菜单，优先刷新 CRM、切换应用或重新登录以更新前端/登录态菜单缓存。
  - Status: resolved.

- ISSUE-2026-07-08-cloudcc-custom-page-cli-unsupported:
  - Symptom: TASK-171 needed a complete CloudCC CRM-side navigation path for “客户互动工作台” (`pagecomponent/html -> customPage -> page menu -> application/profile visibility`), but the Go CLI dispatcher rejected `cloudcc get customPage/custompage`, and MSAPI apply lacked `metadata:apply`.
  - Verified facts: `platform/customPage devguide` documents `cloudcc create/get/update/delete customPage`; `platform/menu` requires a page menu to point at a `pageApi` or script tab; `platform/application` can bind menu IDs to applications. The installed `cloudcc-cli-go version: 2.1.271-msapi` supports pagecomponent publish and MSAPI application/menu domains, but customPage high-code writes were not callable through that dispatcher.
  - Verified facts: MSAPI menu plan `pla2026E964195FlLpjf` was generated for script tab id `tab20265938D889zoxqP` opening `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`, but `cloudcc apply msapi . pla2026E964195FlLpjf` returned HTTP 403 `insufficient_scope`, missing `metadata:apply`; re-requested OpenAPI tokens did not include a scope claim.
  - Resolution (2026-07-08): derived the direct devconsole/setup payload contracts from the legacy CloudCC CLI implementation, created customPage id `6a4d3b831b8c6d0ec6dd22ef` with pageApi `customer_interaction_workbench`, then created page menu tab id `acf2026C53BE54B9R1Iu` through `/api/customTab/tabSetDone`.
  - Verification (2026-07-08): `pageCustomPage` readback returned customPage count `1`; `/api/customTab/queryTabList` returned the page tab with lightning page `customer_interaction_workbench#lightning`; `/api/appProgram/queryModifyPage` for Sales Cloud app `ace20220322Salesloud` returned the tab in `selectedTabList`; online high-code scan shows pagecomponent `1`, HTML component `1`, and customPage `1`.
  - Residual note: MSAPI apply still requires a token with `metadata:apply`, and the Go CLI customPage dispatcher should still be fixed for future repeatability, but TASK-171 CRM visible navigation is no longer blocked.
  - Status: resolved.

- ISSUE-2026-06-22-platform-audit-log-query-500:
  - Symptom: production smoke for `/api/platform/audit/logs` returned backend HTTP 500 after platform login.
  - Verified root cause: backend logs show PostgreSQL `ERROR: operator does not exist: text ~~ bytea` for a `platform_audit_log` query using `LIKE` filters with a bytea-bound parameter.
  - Resolution (2026-06-23):
    - TASK-151 routes empty-keyword platform audit queries through a repository method without `LIKE :q`.
    - Release `2.1.2` deployed the fix to ECS.
  - Verification (2026-06-23):
    - `GET https://x.agentcici.com/api/platform/audit/logs?limit=100` with platform token -> HTTP 200, `success=true`.
    - Browser `/platform/audit` on production shows the audit table empty state instead of the prior loading failure.
  - Status: resolved.

- ISSUE-2026-05-14-local-assistant-login-default-account:
  - Symptom: 本地前台助手登录页使用默认手机号和固定密码时显示 `登录失败：Invalid mobile or password`。
  - Verified root cause: FEAT-024 之后前台登录不再提交 `orgId`；未知手机号在无组织登录流程下不能自动创建组织成员。当前本地库只有 `13800138111` 与 `13900009999` 两个账号，旧助手端默认 `18611892001` 不存在，因此即使用固定密码 `szyd1234` 也会返回 401。
  - Resolution (2026-05-14):
    - `frontend/src/assistant/AssistantApp.tsx` 默认手机号改为本地种子账号 `13900009999`。
    - `README.md` 手动测试账号说明同步更新。
  - Verification (2026-05-14):
    - `POST /auth/password/login` with `13900009999/szyd1234` -> HTTP 200, `success=true`。
    - `POST /auth/password/login` with old default `18611892001/szyd1234` -> HTTP 401, `Invalid mobile or password`。
    - `frontend npm run build` -> success。
    - `git diff --check -- frontend/src/assistant/AssistantApp.tsx README.md` -> success。
  - Status: resolved.

- ISSUE-2026-05-14-local-embed-screenshot-tooling:
  - Symptom: FEAT-032 `TASK-093` 嵌入页实现后，自动化截图 QA 未能产出可信桌面/移动截图。
  - Verified facts: `frontend npm run build` 通过；真实 Chrome 桌面可见态能渲染 `/embed/meeting-minutes` 并显示“会议 session 已就绪，可开始听记”；debug token 调用 `/embed/v1/apps/meeting-minutes/sessions` 返回 `CREATED` session。
  - Verified blocker: 2026-05-14T06:54Z 时 headless Playwright 在当前机器访问 Vite dev/preview 页面停在模块加载前，保存出的截图为空白；macOS `screencapture` 返回 `could not create image from display`。
  - Resolution (2026-05-14):
    - 使用 Playwright CLI 会话重新打开运行中的 Vite 页面并完成可信截图。
    - 发现并修复 `.cici-meeting-drawer--embed` 在 1360px 以下被普通 drawer 宽度 media rule 覆盖的问题。
  - Verification (2026-05-14):
    - `/embed/meeting-minutes` desktop/mobile screenshots -> success。
    - `/admin/embed-apps/meeting-minutes` debug tab iframe preview desktop/mobile screenshots -> success。
    - `frontend npm run build` -> success。
    - targeted `git diff --check` -> success。
  - Status: resolved.

- ISSUE-2026-05-13-prod-backend-container-missing:
  - Symptom: 线上 ECS `47.97.119.160` 可以 SSH 登录，frontend/database/redis/rabbitmq/qdrant 五个容器 healthy，但 `cici-backend` 容器未创建/未运行；用户在 `https://autoservice.agentcici.com/` 登录时报 `HTTP 502`。
  - Verified root cause: compose 配置中存在 `backend` 服务，且本机已有 `cici-backend:V1.9` 镜像，但 `docker ps -a` 与 `docker compose ps -a backend` 均不显示 backend 容器；前端容器内 `nginx -t` 失败，报 `host not found in upstream "backend"`。Nginx 日志中用户登录请求 `POST /auth/password/login` 命中旧 upstream `172.18.0.6:8080` 并返回 502。
  - Resolution (2026-05-13):
    - 在 `/opt/cici` 执行 `docker compose --env-file deploy/acr.env -f deploy/docker-compose.acr.yml -f deploy/docker-compose.acr.ssl.yml up -d backend` 恢复 `cici-backend`。
    - 后端启动完成后执行 `docker exec cici-frontend nginx -t` 和 `docker exec cici-frontend nginx -s reload`，刷新 Nginx upstream。
  - Verification (2026-05-13):
    - `docker compose ... ps` -> 六容器均 healthy。
    - `GET http://127.0.0.1:8080/actuator/health` on ECS -> `{"status":"UP"}`。
    - `curl --http1.1 -k -H "Host: autoservice.agentcici.com" https://127.0.0.1/` on ECS -> `HTTP 200`, title `AgentCiCi`。
    - `POST /auth/password/login` through ECS local Nginx with `Host: autoservice.agentcici.com` -> `HTTP 200`, `success:true`。
  - Status: resolved.

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
