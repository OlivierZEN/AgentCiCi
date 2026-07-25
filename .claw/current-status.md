---
kind: current-status
version: 4
updated_at: 2026-07-25T01:50:00Z
updated_by: MANAGER-001
phase: multi-track-production-and-review
active_task: TASK-245
next_action: "等待真实组织管理员会话验收 TASK-245 的同组织、跨组织、Semattice 跳转与浏览器返回 AgentCiCi 流程；未获授权不发布生产。"
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- TASK-245 / FEAT-138：已完成本地实现并进入 review。组织管理员在前台“切换组织”菜单可从对应组织行点击轻量“管理后台”；跨组织时先调用既有 `/auth/switch-company`，再复用返回 token 进入 `/admin`。组织控制台标题右侧新增产品下拉：当前 AgentCiCi 管理端具状态标识，Semattice 管理端通过受保护的 `/auth/semattice/console` 按当前 TenantContext 再核验管理员、活跃成员、统一身份和 provisioned binding 后签发短时 OACT，前端仅以内存处理并立即跳转固定 HTTPS fragment URI。定向前端测试 6/6、后端 OACT 定向测试和生产构建通过；本会话没有真实管理员凭据，产品菜单签发/跳转与浏览器返回待用户验收。该任务只新增受保护跳转签发 API，不新增持久 token、角色、移动端或生产发布。

- TASK-244 / FEAT-137：用户反馈完成 SSO 后回调 `x.agentcici.com/auth/oidc/callback` 返回 `Invalid OIDC login state`。根因是主站和应用站都创建 host-only state Cookie、而 callback 固定到 `x`。现已以 `2.8.13 / 877337078ea8` 发布：主站先跳 `x`，仅 `x` 创建 state。发布前备份 `/opt/cici/backups/20260724-201945-before-2.8.13-oidc-canonical-entrypoint` 四项均非空；backend/frontend 健康，版本、Nginx 和公网 canonical-start smoke 均通过。真实用户 Keycloak 复验待完成。

- TASK-243 / FEAT-136：已完成生产发布。Keycloak 26.7.0 在 `sso.agentcici.com` 作为唯一 IdP；AgentCiCi 以 OIDC BFF 映射全局账户，签发 10 分钟 RS256 OACT，Semattice 仅通过 AgentCiCi JWKS 本地验签。真实公司 `org2sva14i4udjmi2t4s` 已绑定 Semattice tenant `93ff0c87-a626-529e-b8cf-195825df2488`，真实成员 OACT 访问通过。AgentCiCi `2.8.12 / 6574f168234e` 进一步修复租户应用页状态：刷新后读取持久化 binding，不再误显示“未开通”。

- TASK-242 / FEAT-135：顶层企业身份已在生产统一为 `company_id`。AgentCiCi `2.8.9 / 0194706` 已健康发布，Flyway V94/V95 成功：V94 将顶层 `org_id` / 根表 / 生命周期表物理改为 `company_id` / `company*`，V95 补齐既有 profile 的 `organization_size → company_size`；既有 `org...` 值不重写，旧 JSON、Header 与 JWT claim 均 fail closed。V60 遗留 `ORG` 授权记录已先替换约束再转为 `COMPANY`。备份位于 `/opt/cici/backups/20260724-134723-before-2.8.7-company-id`；六服务健康，外部 HTTPS 与匿名鉴权边界 smoke 通过。AgentCiCi PR #17/#19 和 Semattice 契约 PR #3 均已合并 main。

- TASK-241 / FEAT-134：AgentCiCi 已发布内测 `2.8.5-beta.3 / bef088d5769c`，V93 正向迁移、双向密钥注入、未签名 HMAC 403 与健康检查均通过。Semattice 的实现与 Go 全量/race/vet/build 已通过，但其 ECS 仍是 migration 1–12；试验性新制品在真实 reservation 后因缺少 migration 13 返回 500，已立刻原子回滚到上一健康 release。继续发布必须使用专用 migrator 显式执行 migration 13，不能复用运行时 control/runtime 凭据或改写历史。

- TASK-236 已完成 FEAT-133 P2 并集成至 `main`（`cbf9728`）：默认关闭的精确 Agent 白名单可将 Web、流式和 OpenAPI 统一接入固定 `RETRIEVE → SYNTHESIZE` 计划。灰度运行禁用所有工具、确认续执行和 CRM 快捷路径；初始化失败安全回退到既有聊天链路，运行事实携带最小回退原因。定向回归、后端编译与新建后删除的 PostgreSQL 16 V1→V91 集成验证通过；共享测试库的既有 V81 checksum 漂移仍未修复、未 repair。

- TASK-237 已获授权实施 FEAT-133 P3：新增规则优先模式路由。默认关闭的精确 Agent 白名单、P2/P3 双开关与稳定原因码共同限定 Plan-Exec；关闭、未命中或特征解析失败均保持既有 Direct/ReAct 链路，不新增工具、写入、确认、Reflect、重规划、UI 或生产放量。

- TASK-237 已完成实现并进入 review：路由器在 RAG/工具 Schema 前以服务端确定性规则选择 Direct、ReAct 或 Plan-Exec，确认续执行保留既有路径；P2 未启动时自动回退既有 ReAct。Chat、流式与 OpenAPI 共享脱敏 `modeDecision` 投影。规则/P2/聊天定向回归 48 项、编译和 diff 检查通过；全新后删除的 PostgreSQL 16 从 V1 迁移至 V91，并通过 4/4 集成用例。

- TASK-237 已完成并集成至 `main`（`5c08c33`）。TASK-238 已获授权实施 P4：默认关闭的受控 Reflect 仅对 P2/P3 已启动、且 P3 标记为需要审查的运行创建组织隔离事实；确定性 Gate 优先，审查不新增工具、写入、确认或自由重规划。

- TASK-238 已完成实现并进入 review：V92 的审查事实与 `REFLECT_GATE` 事件均按组织隔离；成功 Plan-Exec 才可进入默认关闭的精确 Agent Gate。Gate 验证 Agent、步骤、预算、确认和输出，阻断即 `HANDOFF`，不调用新模型或工具。评测增加运行模式、审查状态和确认前零写入断言；定向回归通过，隔离 PostgreSQL 从 V1→V92 的运行时集成 5/5 通过。

- TASK-238 已完成并集成至 `main`（`6817ba5`）。用户已确认 P5 shape：组织管理员与平台运营在现有 Trace 详情中查看“运行执行”，默认运行总览，保留回归集入口；多主题使用同一布局和语义 token。TASK-239 已获授权，开始补齐 Trace 与运行事实的精确关联、脱敏投影和桌面端 `gilded`/`galaxy` 验收，不新增路由或移动端实现。

- TASK-239 已完成 P5：阻塞/流式 Chat 将精确 `runtimeRunId` 作为 Trace 脱敏详情的一部分保存；Trace 详情仅以同组织运行 ID 回读运行、计划、步骤、事件和审查事实，未关联历史 Trace 显示明确空态。现有详情新增运行总览、步骤/事件时间线、折叠证据与条件性例外说明，样式仅用语义主题 token。后端定向回归、V1→V92 全新库集成、前端 3/3 与生产构建通过；受权组织管理员在隔离最小事实库完成 `gilded`/`galaxy` 的关联 Trace、展开/复制和 1280px 无横向溢出验收。审计日志的独立 `/ops/audit/logs` 在该最小库返回 500，不归因于 P5 Trace 投影。

- TASK-240 / P6 与 TASK-241 / FEAT-134 的历史功能分支已于 2026-07-24 合并回主线；冲突按当前 `company_id` 契约消解。P6 三项能力均保持默认关闭，须同时精确命中 `allowed-company-ids` 与 Agent 白名单，指标不含公司或用户高基数字段；没有用户明确指定生产试点公司、只读 Agent 与观察窗口时，不改线上开关或发布 P6。

- TASK-234 已按用户要求调整生产版本规则：修订段最大值为 365，`2.8.365` 的下一版为 `2.9.1`；主、次版本上限仍为 12。脚本级边界回归与 dry-run 校验均通过，未发布生产。

- FEAT-131 通用外部应用智能体记忆平台已发布至生产 `2.8.5 / 02d380d10508`。可信凭据绑定、受控上下文注入、关系库二次授权的脱敏语义检索、候选/人工治理、Trace/评测状态、保留/删除/legal hold、组织导出与 purge、两个独立通用适配契约均有代码与验证证据；生产数据库已正向迁移 V85–V90，六服务健康，公网 HTTPS smoke 通过。默认共享测试库的既有 V81 checksum 漂移未修复或掩盖。

## Historical Notes

- TASK-233 已获授权：人工治理必须覆盖已生效记忆与主体删除，调用既有生命周期服务并同时验证组织、Agent、角色和 legal hold；完成后执行 FEAT-131 的逐项生产就绪审计，不发布生产。

- TASK-232 已获授权：提供通用记忆候选的查询、审核通过和拒绝 API；API 必须沿用当前组织与 Agent 权限边界，Trace/评测仅记录最小、脱敏的记忆治理证据。

- TASK-231 已获授权：通用记忆生命周期必须与既有组织保留、legal hold、导出和 purge 一致；实现仅能使用通用主体、应用、记录、证据和向量模型，不得引入外部应用领域对象或生产发布。

- TASK-231 已进入 review：主体删除会立即撤销并脱敏关系型记忆，过期清理与组织 purge 同步处理派生向量；legal hold 统一阻断。定向测试、编译及新建后删除的 V1→V88 PostgreSQL 迁移通过；既有平台生命周期集成测试受共享库 V81 checksum 不一致阻断，未修改历史迁移或执行 repair。

- TASK-230 已获授权：将 API 凭据与通用应用、主体类型、身份等级和命名空间绑定，OpenAPI 只能使用该绑定建立记忆作用域；绑定缺失安全降级为无记忆。

- TASK-229 已获授权：只建立由可信服务端认证层提供的通用外部主体运行时上下文，并注入授权后的记忆；不添加任何外部应用领域功能或客户端信任边界。

- TASK-228 已获授权：为通用记忆建立受控语义检索，向量命中只能作为候选，必须回读关系库完成组织、主体、scope、状态和有效期校验。

- TASK-227 已完成 FEAT-131 Phase 2 的治理前置：V86 新增通用候选与证据表，候选以 `PENDING` 保存，只有显式审核通过才创建可读取的 `ACTIVE` 记忆；重复审核被拒绝。定向 JUnit、后端编译、diff 检查和新建后删除的 PostgreSQL 16 临时库 V1→V86 迁移均通过。

- TASK-226 已由 MANAGER-001 分配并启动：按 FEAT-131 Phase 1 建设通用主体、会话记忆与可信外部应用上下文核心。授权边界覆盖 memory/ai/agent 后端、迁移、定向测试和项目状态；禁止任何外部应用领域耦合、移动端、生产发布和外部业务写入。

- TASK-226 已完成通用主体记忆 Phase 1 增量：V85 新增主体、记忆记录、会话快照三张通用表；`ExternalMemoryContextService` 以组织、应用、外部主体与会话构造可信上下文，读取时按 scope/时效过滤且不隐式创建主体；`MemoryContextPromptAssembler` 只在字符预算内组装已授权的摘要与记忆。定向 JUnit、后端编译和 diff 检查通过；新建并删除的 PostgreSQL 16 临时库已从 V1 全量迁移至 V85，三张记忆表断言通过。尚未接入外部应用入口、Chat 编排器、向量索引或自动候选写入。

- 已新增 `FEAT-131-agent-memory-platform.md` 通用平台设计基线：Agent CC 面向任何外部应用提供主体记忆、会话上下文、混合检索、路由移交、候选写入、Trace/评测和生命周期治理；外部应用仅通过可信契约接入并继续拥有其渠道、原始交互和领域事实。FollowUp 仅是首个参考接入方，不会在平台模型、接口、工具、Skills 或页面中形成耦合功能。

- TASK-225 已发布生产 `2.8.4 / 2f2f1a013ec2`：工作台所选技能成为本轮强制业务上下文，只注入该技能的流程、输出契约和文件型参考文档；平台安全、Agent 直接工具和技能工具授权边界保持不变。Trace 现在记录用户选择、有效上下文、选择状态/原因、实际激活和候选绑定技能，工作台及管理端监控同步展示。后端定向测试、后端编译、前端 28 文件/187 断言、前端构建、ACR inspect、四类备份、六服务健康、版本与公网 HTTPS smoke 均通过；当前无已授权组织用户会话，受保护页面的桌面交互复核留待后续完成，未伪造结果。

- `2.8.3 / 651bc2294bee` 已健康发布：TASK-224 为两个审计服务的运行时构造器显式标注注入入口，消除 `2.8.2` 的启动重启问题。backend/frontend 与四个状态服务均健康，V84 成功，公网 x HTTPS 与显式生产 IP 的 onechat HTTPS 为 200；匿名 `/auth/me` 正确返回 401。`2.8.2` 仍保留为失败版本证据，线上已从其回滚并以新不可变版本恢复。

- TASK-223 已完成本地修复：用户补充“每天 09:00”后失败的根因已验证为时钟正则与 `inferTrigger` 捕获组读取不一致。显式捕获时段/小时/分钟后，定向测试确认每日 `09:00` 生成 `0 0 9 * * *` 并可计算下一次执行时间，下午 `3点30分` 正确换算为 `0 30 15 * * *`；后端定向测试和编译、diff 检查均通过，尚未发布生产。

- TASK-222 已完成 TASK-170 安全规则平台与 TASK-219 模型目录导航收敛。TASK-170 的冲突按当前 `main` 最新代码处理，迁移按主线时间线由 V71 重编号为 V84，避免 Flyway 乱序；安全规则、审计、聊天/工具编排 56 项后端定向测试、后端打包、TASK-219 的 20 项前端定向测试和前端生产构建均通过。

- TASK-221 is in review: the complete `/admin/*` static audit now routes shared modals, organization/user dialogs, skills subpages and row menus, ontology workbench, operations/monitor, embedded apps and billing through current `--theme-*` values. AdminToolsPage no longer injects category gradients or fixed colors. Assignment check, 11 focused theme tests, production build and diff check pass. This session has no authenticated administrator, so logged-in blue-theme desktop evidence remains an explicit manual visual-acceptance item.

- TASK-220 is in review: the quick-command dialog, composer popovers/actions, session selection and session menu now use only current `--theme-*` values, so the selected blue theme supplies its own surface, border, accent and overlay instead of gilded fallback colors. Assignment check, 9 targeted theme tests and production build pass. Local browser has no authenticated user session and correctly stops at login, so authenticated desktop visual evidence remains pending rather than fabricated.

- TASK-219 is ready: the user selected the Product Design “运营中枢” direction and explicitly required genuine task-to-page separation, not cosmetic menu grouping. FEAT-124 defines the complete `/platform/*` route map, list/drawer/editor/version boundaries, function-preservation contract, visual acceptance and the platform-only eight-theme preference. The existing platform account already persists an independent server `themeCode`; implementation makes the setting visible and isolates the local fallback key from user/Admin surfaces. The model sub-route work is blocked only by active TASK-218’s exclusive `PlatformModelsPage.tsx` change.

- TASK-217 is complete in production `2.7.12 / b20261d8b89b`: current-user/current-Agent schedule creation reuses the personal workflow scheduler, requires a valid cadence before writes, makes authorized Tavily work observable, and renders static workflow parsing only as “工作流定义检查”. Blocking and streaming sessions deterministically request a cadence instead of returning a fake configuration JSON. Focused backend regression, ACR inspect, Git tag, four-part backup, six healthy services, `x`/production-IP onechat HTTPS smoke and clean post-release logs passed. The full local suite remains blocked by the pre-existing shared-test-db Flyway V81 checksum mismatch and was not repaired; no authorized user session was available to create a production test task.

- TASK-218 正在处理厂商模型目录能力边界：OneKeyToken 未开放远程模型枚举，运营端不得显示或回填本应用的预设模型；仅保留运营人员已经显式保存的选择。

- TASK-215 is complete in production `2.7.11 / 281f35b2cb2f`: Trace nodes retain the 220-character compact summary while administrators can expand and copy up to 12,000 characters of redacted saved detail. Focused backend tests, 27 frontend test files / 179 tests, production build, Compose configuration, ACR image inspection, release backup, six-container health, version and public smoke passed. The authenticated Trace interaction was previously verified locally; production browser reached the independent admin login boundary without console errors, but no administrator credential is available in this session.
- TASK-216 is complete as a design-only exploration: four same-structure desktop conversation-workbench skins and their specification are preserved as visual references, without changing production theme facts.

- TASK-214 已在生产 `2.8.1 / 9bc8510cbede` 完成：平台 OneKeyToken“检测”使用未保存的地址/Key 草稿发起真实的非流式 Chat Completions 调用，并携带唯一请求 ID；静态模型仅保留为目录，401/403 返回可操作的脱敏错误，检测不持久化草稿。全新 PostgreSQL 后端集成、前端定向测试、生产构建、ACR 镜像检查、备份和六容器运行健康均通过。受保护的生产端点会按设计拒绝匿名请求（401），运营人员现可用真实业务 Key 验收。

- TASK-213 is complete in production `2.7.10 / f922b86f1884` through PR #13. The domain-neutral V82/V83 ontology platform, business visual workbench, AI draft-only boundary, immutable versions, deterministic Schema/GraphQL/query compilation, INLINE_SAMPLE project-delivery proof and CloudCC adapter/reference package are live. Fresh-database backend regressions passed 127/127, frontend passed 177/177 and production build, and independent security/spec reviews approved with Critical 0 / Important 0. Production `project-delivery` is published as immutable v1 from draft revision 6 with 15/15 validated mappings; explain/execute, evidence, audit redaction, idempotent publish and cross-tenant 404 passed. The 1600×1000 production Browser passed list/canvas/mapping/technical/version views with console warning/error 0 and overflow 0. V82/V83 are successful, 13 ontology tables are live, and the 480-second/17-sample window kept six services healthy with zero restart/OOM/backend error and zero unexpected 5xx. `customer-operations` installs with verified package provenance but both available demo users currently lack a usable per-user CloudCC session, so discovery returns the designed `502 DATA_SOURCE_UNAVAILABLE` without damaging or publishing the draft.

- TASK-201 右栏说明移除与双栏对齐增量修复已在生产 `2.7.9 / c04e992b3840` 完成。PR #11、不可变 Git/镜像 tag、发布前四项备份、仅重建 backend/frontend、六服务健康及 1600×1000 真实 Agent Builder 页面均通过；右栏模型治理说明与分隔线不存在，左右编辑列同为 612.5px × 604px，顶底边一致，系统提示词与发布备注输入底边一致，页面横向溢出、生产 console warning/error、backend ERROR/Exception 与 Nginx 精确 5xx 均为 0。即时应用回滚点为 `2.7.8 / 4814d2b9534d`。
- TASK-212 已在生产 `2.7.8 / 4814d2b9534d` 完成。PR #10、不可变 Git/镜像 tag、发布前四项备份、只重建 backend/frontend、V81 非事务并发索引、六服务健康、双向 401/403/200 权限矩阵及 `1600 x 1000` Agent Builder/平台页面均通过；两个索引 valid/ready，页面无外层横向溢出，console warning/error、稳定窗口 backend ERROR 与 Nginx 精确 5xx 均为 0。完整 Maven 诊断仍只有既有 341 项中的 3 failure / 7 error，未误报全量套件通过。即时应用回滚点为 `2.7.7 / e47979167af8`，V81 索引可安全保留。
- TASK-211 is complete in production `2.7.7 / e47979167af8`. PR #6 changed deterministic CRM SSE to the existing 18-character/18ms chunk sender; production `2.7.6` then exposed OpenAPI per-fragment whitespace loss, was rejected and rolled back. PR #7 removed per-delta trim/blank filtering, passed clean-DB CRM 135/135, frontend 89/89 and independent review, and was released as a new immutable version.
- Fresh production protocol and desktop evidence pass: SalesA 5/5 streams each emitted 133 deltas over about 2.4 seconds with exact persistence; blocking and SalesB match after cutoff-only normalization. OpenAPI blocking/streaming are both 2,383 characters, streaming has 133 messages plus one terminal event, and history/internal bodies are equal. Temporary API access is revoked, bindings are exactly restored, nine answer files have no tool/raw-ID leakage, and the final clean window has zero backend error, CRM failure, abnormal disconnect or Nginx 5xx. A fresh application-internal Browser run captured the same assistant bubble at 50 visible characters while the composer was disabled and at 2,100 characters after completion, with console error/warning 0, no horizontal overflow, complete Top 5/five-layer analysis, and no internal result leakage.
- TASK-210's `2.7.5 / be80eea665c0` implementation is preserved through production `2.7.10`: FEAT-116 renders the public standard WeChat mark and distinct Lucide business-source icons, preserves the compact timeline across all eight themes, and removes duplicate-key console errors from CRM event id collisions. Frontend 16 files / 89 tests and production build passed; independent final production visual evidence remains with TASK-210.
- TASK-208's `2.7.5 / be80eea665c0` implementation is preserved through production `2.7.10`. SalesA receives a deterministic five-layer CRM answer with direct conclusion, product Top 5, business diagnosis, forward signals, actions and data coverage; SSE, persisted messages, blocking, OpenAPI and desktop UI do not expose the internal tool result or trigger the false “等待确认” state.
- TASK-209 remains preserved through production `2.7.10`; the platform login is still locked to the approved reference image.
- TASK-207 is complete on `codex/TASK-207-frontend-theme-alignment-audit`: all eight themes now own authenticated frontend surfaces and data/identity colors; the organization entry uses the current organization name's first character; dashboard rows, menus, forms, lists and the interaction-ingestion dialog passed a real `1600 × 1000` desktop audit. Frontend 15 files / 85 tests, production build, JSON validation and diff checks passed; browser console error/warning and outer horizontal overflow are zero.
- TASK-206 is complete in production `2.6.11 / c540988655cb`. The pagecomponent now reads the current CRM session with `$CCDK.CCToken.getToken()`, the backend validates it through `/api/user/getUserInfo`, and strict session-user/page-user/AgentCiCi-member consistency remains in force. Real CRM initial load plus two refreshes produced three HTTP 200 ticket/consume pairs with no mapping error.
- TASK-205 remains the production CRM analysis baseline; TASK-208 hardens its routing, formatting, permission diagnostics and protocol behavior without introducing a separate general-purpose Agent.
- CloudCC batch `TASK-205-CRM-ANALYTICS-DEMO-V1` is now governed for SalesA and linked to the 16 TASK-203 V2 Accounts. Readback is 12 products, 16 accounts, 24 opportunities, 72 opportunity products, 16 contracts, 48 orders and 144 order items; a repeat plan reports zero updates, creates and duplicates. Quantity Top 5 remains `X1 130 / G5 110 / S2 95 / MP 75 / PA 65`, while amount champion is MP as designed.
- TASK-204 is ready: the approved design removes the nested frame and excess inset around the Agent Builder guide, then replaces the two persistent avatar buttons with an accessible avatar-triggered upload/change/remove menu. FEAT-110 awaits written user review before implementation.
- TASK-203 remains unintegrated on its dedicated branch, while production CloudCC already contains 16 TASK-203 V2 Accounts owned by and visible to SalesA. TASK-208 may read and reference those accounts but must not modify TASK-203's exclusive seeder.
- TASK-202 is complete in production `2.6.6 / 4caaa4800b3d`. The hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- Production currently runs `2.8.1 / 9bc8510cbede`; backend/frontend and four state services are healthy, and the state services remain on `2.6.12`. The release backup is `/opt/cici/backups/20260721-190903-before-2.8.1-task214-onekeytoken`; immediate application rollback is `2.7.12 / b20261d8b89b`.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- TASK-170 security rules platform is merged into `main` and awaits its separate authenticated desktop/production acceptance;本地集成回归已通过。
- 已知风险：本机仍无法解析 `onechat.agentcici.com`，但显式使用生产 IP 的 smoke 返回 200；当前两个演示组织的密码登录用户均不能取得有效 CloudCC 当前用户会话，`customer-operations` 元数据发现按设计返回 `DATA_SOURCE_UNAVAILABLE`，需恢复用户绑定后再完成真实 CRM 目录/查询验收；另有跨用户不可见会话因 `ResponseStatusException` 被通用异常处理捕获而返回无数据的 500 而非 404/403，隔离成立但状态与日志语义需独立任务修复。

## Read Next

- `.claw/tasks/TASK-213.md`, `.claw/assignments/TASK-213.yaml`, `docs/specs/FEAT-118-general-ontology-modeling-platform.md` and its plan - completed general ontology V1 delivery, production evidence and authorization source.

- `.claw/tasks/TASK-201.md`, `.claw/assignments/TASK-201.yaml` and `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md` - completed production Agent Builder right-column cleanup and alignment acceptance.
- `.claw/tasks/TASK-212.md`, `.claw/assignments/TASK-212.yaml` and `docs/specs/FEAT-117-skill-dag-governance-phase1.md` - completed production Skill DAG Phase 1 scope, authorization and acceptance source.
- `.claw/tasks/TASK-211.md`, `.claw/assignments/TASK-211.yaml`, `docs/superpowers/plans/2026-07-15-crm-streaming-output.md` and the TASK-211 section in `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md` - completed production `2.7.7` protocol, application-internal Browser and governance acceptance.
- `.claw/tasks/TASK-210.md`, `.claw/assignments/TASK-210.yaml` and `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md` - active customer workbench standard source icon repair.
- `.claw/tasks/TASK-208.md`, `.claw/assignments/TASK-208.yaml` and `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md` - completed CRM stability, deep-analysis, SalesA migration and production acceptance record.
- `.claw/tasks/TASK-209.md` and `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md` - production login source that TASK-208 must preserve.
- `.claw/tasks/TASK-207.md`, `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md` and `design-qa.md` - completed frontend theme and alignment delivery plus visual evidence.
- `.claw/tasks/TASK-206.md` and `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md` - completed CloudCC embed SSO recovery and verification evidence.
- `.claw/tasks/TASK-205.md` and `.claw/assignments/TASK-205.yaml` - completed CRM analysis baseline and superseded authorization history.
- `.claw/task-board.md` and `.claw/test-report.md` - compact task index and latest verified commands.
