---
kind: current-status
version: 4
updated_at: 2026-08-11T05:49:57Z
updated_by: codex
phase: implementation
active_task: TASK-281
next_action: "发布 2.8.61-beta.7，验证待补充缺陷草案能生成唯一可执行的完整确认文本，再完成真实写入与隔离验收。"
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

## Latest Snapshot

- TASK-280 UAT 验收缺陷已修复并随 `2.8.61-beta.7 / 4f7ae57f0aec` 发布：`18611892001` 的 Keycloak 用户已激活且本地 binding subject 一致，但原重发入口只报“已激活”而不协调 `PENDING_ACTIVATION`。新入口在远端仍待激活时重发邮件，远端已激活时同步成员为 `ACTIVE` 并写脱敏审计。后端身份定向测试、package、前端 42 文件/229 项、构建及 UAT 技术门禁通过；当前无可控 ORG_ADMIN 浏览器会话，尚未点击正式入口，成员仍保持 pending，等待用户刷新页面后执行一次“检查激活状态”并用独立浏览器登录回归。

- TASK-281 UAT 阶段修正：AgentCiCi `2.8.61-beta.5 / 6473494fff8f` 已通过正式“同步标准模板”将第二租户升级为 Semattice published `7 对象 / 83 字段`，`dev_defect` 23 字段且 HUMAN/SERVICE 投影完整。真实对话随即发现未确认草案中的“确认后成功提交”被回执守卫误判为完成态成功声明；无写入、无脏数据。已局部修正为只门禁完成态声明，下一候选为 beta.6。
- beta.6 已验证草案可正常展示；用户补充父项目后却未继续注入草案协议，模型输出服务端不接受的“确认提交此缺陷”短指令。仍未执行写入。beta.7 将依据近期待补充草案识别字段续答，强制重新输出完整草案及唯一可执行确认文本。

- TASK-281 / FEAT-169 / INT-009 已完成本地实现：产品经理对缺陷采用完整草稿确认，确认后由租户 PM SERVICE 写入 `dev_defect`，再以 `runtime.record.get` 回读并核验字段、revision 与 correlation；只有 `SEMATTICE_LIVE + readback_verified` 才允许成功声明。SSE `tool_result` 和前端独立回执卡片显示对象、业务编号、记录 ID、revision 与 correlation，失败结果不得由模型改写为成功。后端定向测试、前端 42 文件/228 项测试和构建通过；完整 Maven 套件因本机 PostgreSQL/Hikari 连接重试被停止，UAT 仍等待 Semattice 提供方先发布。

- TASK-280 / FEAT-168 已发布 UAT `2.8.61-beta.3 / 47affe4086e5`：组织用户页返回脱敏统一身份状态，并在“CloudCC账号绑定信息”中为 `ACTIVE + MISSING` 成员提供独立“修复统一身份”入口。操作要求手机号二次确认，复用 Keycloak HUMAN provisioning，保持角色和资料不变；需要首次激活时转为 `PENDING_ACTIVATION` 并发信，相同幂等键只执行一次且写入脱敏平台审计。后端定向测试、package、前端完整 225 项测试和生产构建通过；UAT 六容器 healthy、Flyway V109、Nginx、公网与匿名 401 边界、30 秒稳定窗口均通过。尚未调用真实修复接口或修改 `18611892001`。

- TASK-279 / FEAT-167 已完成并通过 UAT 业务验收：`2.8.61-beta.2 / c66d9448c95b` 将机器主体治理负责人和业务调用者分离，租户 OWNER/ORG_ADMIN 与负责人自动 APP_ADMIN，普通成员按 VIEWER/CONTRIBUTOR/REVIEWER/APP_ADMIN 显式授权；OACT/审计分别记录负责人和实际发起人，前端在发送前预检权限，SSE 错误结构化结束。Demo Company 的非负责人 ORG_ADMIN 已成功调用产品经理查询 Semattice，审计中的 actor 与 owner 不同；管理弹窗角色回读正确，浏览器无 error/warning。当前 UAT `2.8.61-beta.3` 沿主线包含该提交与 V109。

- TASK-278 / FEAT-158 已完成：UAT AI表格“业务数据服务暂时不可用”根因为 `2.8.60-beta.1` HUMAN OACT scopes 缺少 `metadata.read`。`2.8.61-beta.1 / d4b273af39c2` 已恢复最低元数据/记录读取范围并上线测试发布门禁；受权租户页面回读 6 个 DevAutopilot 业务对象、真实 0 记录空状态，console error/warning 为 0。只重建 backend/frontend，状态服务未变；生产保持 `2.8.60`。

- TASK-275 / FEAT-164 / INT-008 已完成双租户 UAT。第二租户 Owner 通过受治理恢复绑定到现有激活 HUMAN；同一 DEMO 身份可切换 A/B 组织。第二租户正式开通自动创建已发布 `研发产品经理` Agent/PM SERVICE，租户 ORG_ADMIN 独立新增、改名、暂停和恢复 1 名自定义开发者后三系统状态一致。A 保持 2 名开发者，B 只显示本租户 0 项目、1 名开发者与产品经理；B 的 Semattice 会话不能用 A 的 `company_id` 参数覆盖可信租户。产品经理首页可见并以 `onekeytoken/auto` 按 DevAutopilot 语义响应。AgentCiCi UAT 已由 TASK-278 推进至 `2.8.61-beta.1 / d4b273af39c2`。

- TASK-276 / TASK-277 已发布生产 `2.8.60 / 451f797e61df`。backend/frontend ACR index digest 为 `sha256:1b4e96962c08900ae0372601b9a7fc99134615bcc0cd00aff36b5f102d8dba4a` / `sha256:859d23f4a65944161b22cc5a6cbeac2bc2db762a8f21a799eb490776491047c9`；发布前完整备份 `/opt/cici/backups/20260810T122603Z-before-2.8.60-owner-identity` 四项非空且为 `0600`。仅重建 backend/frontend，四个状态服务 ID 哈希保持 `88b03a2170ddc7acc3047e9ae42926298479174e218f956e226cfd4f2b9fbea7`；六容器 healthy、health=`UP`、Flyway V108 无迁移、Nginx 有效、x HTTPS=200/HTTP=301、匿名鉴权 401、启动 ERROR 计数 0。目标 Owner 的生产身份协调尚未执行：当前可控浏览器无 PLATFORM_ADMIN 登录态，未绕过认证或直接写库。

- TASK-276 / TASK-277 已发布 UAT `2.8.60-beta.1 / 93a487f4e393`。ACR backend/frontend index digest 为 `sha256:a68027a8949a2ec315bc756caa7661e76fd347988158ece806d06ee3128ca06c` / `sha256:2da4ffdb68a4c1e83df589644282fb768644d3f865c61f8fb3cc646b616ce966`；完整备份 `/data/apps/agentcici/backups/20260810T113637Z-before-2.8.60-beta.1` 非空且为 `0600`。只重建 backend/frontend，四个状态服务 ID 哈希保持 `b5dca5759af2a9cfb0ed4285fdb3b01c9af02db33eb2bfbabfa347fe728de2bc`；health=`UP`、Nginx、首页 200、匿名鉴权 401 与启动 ERROR 计数 0。受权 UAT 页面回读目标租户 Owner 为 `OWNER/ACTIVE`、统一身份可登录，Semattice 与 DevAutopilot 均运行中，浏览器 0 error / 0 warning。生产未修改。

- TASK-276 / TASK-277：Owner 常规身份协调与无有效 Owner 启动死锁恢复已由 `299f1f0` 提交并推送。后端 4 类 26 项定向测试、package、前端 4 项测试与生产构建通过；完整后端套件因本机 PostgreSQL 未启动在连接重试阶段停止。恢复只接受远端已激活 HUMAN，通过悲观锁串行所有权变更，保留原待激活 Owner；页面常规协调使用脱敏状态和独立确认 modal，不设置或展示密码。正式 tag 已推进至 `2.8.59`，发布预检确认下一 UAT 为 `2.8.60-beta.1`。

- TASK-277 / FEAT-166：用户已确认在“租户应用”页增加当前 Owner 身份协调入口。范围为脱敏状态、公共编号二次确认、受管 HUMAN provisioning、成员激活状态回写、幂等与平台审计；不替换 Owner，不直接写生产数据库，不自动发布。

- TASK-275 / INT-008 已发布 UAT `2.8.59-beta.12 / b070676f411a`。发布前完整备份 `/data/apps/agentcici/backups/20260810T103604Z-before-2.8.59-beta.12`，仅重建 backend/frontend，四个状态服务 ID 未变；健康、版本、Nginx、匿名 401 与错误日志通过。真实员工重新 handoff 到 DevAutopilot `1.0.4-beta.1` 后，0 项目空工作台正常，墨子 suspended 不可派单、鲁班 active 可派单，证明 `identity.principal.read → identity.principal.directory` 闭环通过且未授予 `authorization.manage`。第二测试租户 Owner 仍为 `PENDING_ACTIVATION`，仅双租户验收待完成。

- TASK-275 / INT-008 双租户 UAT：平台管理员已对第二测试租户 `orgvdd8xckmvc8r5yi6q` 通过正式按钮开通 Semattice，页面和数据库回读均为 `PROVISIONED`，且两个测试租户的 Semattice tenant 标识均存在。随后正式 DevAutopilot 开通按预期失败关闭为 `DevAutopilot activation requires an active tenant ORG_ADMIN`；只读回读确认该租户唯一 `OWNER` 仍为 `PENDING_ACTIVATION`，没有 activation 或机器主体遗留。该门禁不能由平台账号、直接写库或虚构负责人绕过；需 Owner 完成邮件激活和首次 OIDC 登录后继续。Demo Company 的正常员工产品经理对话回归仍需业务登录。

- TASK-275 / INT-008：已发布 UAT `2.8.59-beta.11 / 4b0be4c4328e`，修复租户标准产品经理只绑定 Tool、未绑定 `semattice-project-delivery-management` Skill 而把“项目”误解为 CRM 项目的缺陷。完成态现在还要求 always-on Skill、当前发布工作流的不可变 Skill 引用及已发布 Skill 版本；真实页面先从“已完成”纠正为“待补齐”，平台管理员通过正式按钮补偿后恢复“已完成”。只读回读确认 `天工产品经理 / devautopilot-pm-09653ab9` 工作流 v2 已发布，5 个研发交付 Tool、标准 Skill binding 和 workflow Skill v1 引用完整，领域提示断言通过。平台会话不能替代租户员工会话，截图原句的最终对话回归仍需正常租户用户执行。

- TASK-275 / FEAT-123：已发布 UAT `2.8.59-beta.9 / 534a3baff64e`，纠正 beta.8 将 OneKeyToken 下游实际路由误当成目录模型的语义错误。检测现在返回并保存直接验证的稳定路由别名 `onekeytoken/auto`，`qwen3.5-flash` 仅作为该次 `resolvedModel` 诊断信息。UAT 已移除错误的 `qwen3.5-flash` 目录项，五个场景路由全部回读为 `onekeytoken/auto`；实时 Chat Completions 检测成功，OneKeyToken 远程目录仍为 0。DevAutopilot 初始化状态保持“已完成”。

- TASK-275 / INT-008：UAT `2.8.59-beta.8 / 8213646b4fa3` 完成了目标租户初始化，但曾错误地把 OneKeyToken 响应中的下游实际路由 `qwen3.5-flash` 当作可保存目录模型；该模型配置错误已由 beta.9 移除。beta.8 的初始化事实仍有效：页面回读“初始化状态=已完成”，只读数据库确认 `天工产品经理 / devautopilot-pm-09653ab9` 已启用、工作流 `PUBLISHED`、`web` 渠道启用，受控 SERVICE 为 `ACTIVE`。当前平台会话不能替代租户员工会话，员工首页最终可见和创建会话仍需正常租户用户刷新验证；第二租户隔离仍待完成。

- TASK-275 / INT-008：已发布 UAT `2.8.59-beta.7 / 7e309a39394d`。应用卡片完成态改为服务端权威检查，必须同时回读已发布 PM Agent、启用的 `web` 渠道和有效 SERVICE 主体；目标租户现在正确显示“待补齐”并提供按钮。平台管理员真实点击后被 Agent 生产就绪门禁拒绝：UAT 平台 7 个厂商均无凭据、已选模型数为 0，聊天场景没有可用模型。该依赖不能通过直接写库或发布无运行模型的假 Agent 绕过；模型页面已交给平台管理员配置。

- TASK-275 / INT-008：已发布 UAT `2.8.59-beta.5 / 0edfc3567f85`。标准产品经理 Agent 的新建与既有租户补齐初始化现在都必须绑定 `web`、使用标准 Spec 编译并通过生产就绪门禁发布；员工首页继续只展示内置或已发布 Agent。UAT 只读回读确认目标租户 `天工产品经理 / devautopilot-pm-09653ab9` 仍为 `published_version_id=NULL` 且无 channel binding，说明代码已上线但既有租户尚未执行一次正式 `initializations` 补偿。当前可控浏览器没有平台登录态，未绕过授权、未直接写数据库；待平台管理员执行补偿后完成首页可见性验收。

- TASK-276 / FEAT-165：已发布 UAT `2.8.59-beta.6 / 9563aa2e37cf`。真实开通创建租户 `orgvdd8xckmvc8r5yi6q`，Owner EMAIL/MOBILE 标识、OWNER/PENDING_ACTIVATION 成员、OIDC issuer/subject 绑定与 Keycloak enabled 用户均一致，本地密码凭据为 0；Required Actions 为 `VERIFY_EMAIL`、`UPDATE_PASSWORD`。6 容器 healthy，health=`UP`、版本/Nginx/首页/匿名 401/错误日志均通过。测试租户保留给 Owner 完成邮件激活和首次登录；生产未修改。

- TASK-275 / INT-008：UAT 已发布 `2.8.59-beta.3 / 5be204680e16`，配置修订 `uat-config-20260809T135729Z-666d570`。受管初始化为目标租户建立/协调 HUMAN owner、产品经理和开发者 Semattice Principal；activation 权威回读 `墨子开发者=SUSPENDED`、`鲁班/天工产品经理=ACTIVE`，不再使用历史资源快照覆盖主体状态。普通业务 OACT 继续要求统一身份，只有服务器内部且仅含 `identity.principal.sync` 的初始化令牌允许先建立历史 HUMAN owner 投影。第二租户隔离仍待验收。

- TASK-274 / FEAT-163：已发布生产 `2.8.57 / 750fb71ab47d`。机器主体 scope 现在通过 ORG_ADMIN 受治理完整替换接口、独立 SERVICE allowlist、明确确认页和脱敏平台审计维护。企业 `org5nszpgj99jaysxv6y` 中仅大乔 `dev-autopilot-product-manager` 新增 `runtime.record.delete`，悟空、后羿、哪吒和 HUMAN 默认 scope 未扩大；Client ID、Client Secret、负责人和生命周期均未变化。新签发 SERVICE OACT 已通过 Semattice 非写入安全探测，未删除业务记录。发布前备份为 `/opt/cici/backups/20260805-235439-before-2.8.57-task274-scope-governance`。

- 项目治理协议已按用户指令从 `cc-aidev-guidelines-common` 切换为 `agentic-project-guidelines` `3.10.0`；README、AGENTS 与 brownfield baseline 已同步。旧任务、旧测试记录和兼容目录中的历史引用不改写，也不再构成当前 assignment/SSH 门禁。

- TASK-273：已完成 Keycloak 生产人工运维交接。SSO 主机 `115.29.222.70` 的 Keycloak `26.7.0` 和 PostgreSQL `16.13` 均处于 active；PostgreSQL 仅监听回环地址。真实数据库连接值已仅写入 `/root/agentcici-ops-handover/keycloak-postgres.env`（`0600 root:root`），并通过 root 专用脚本完成非交互连接验证；仓库只提交不含秘密的交接手册。人工接管仍要求人员使用自己的阿里云控制台与 SSH 身份，未复制或公开自动化私钥。

- TASK-272 / FEAT-162：已发布生产 `2.8.56 / 564fb9fbfd8d`，修复所有组织管理端功能页深链刷新被后端 `/admin/*` 控制器截获的问题。浏览器管理 API 已统一至 `/api/admin/*`，而 `/admin/*` 仅保留 SPA 页面路由。前端定向测试 52/52、完整后端测试、生产构建、Compose/Nginx 配置校验通过；发布前四类备份位于 `/opt/cici/backups/20260805-184240-before-2.8.56`。六容器 healthy、health=UP、版本接口与 Nginx 均正确；匿名 `https://x.agentcici.com/admin/service-principals` 为 `200 text/html` 且含 SPA root，匿名 `/api/admin/service-principals` 仍为预期 JSON 401。未改后端控制器、鉴权或业务数据；`onechat.agentcici.com` 仍无 DNS 解析，未作为成功依据。

- TASK-270：用户明确授权将悟空开发者 SERVICE 的 Client ID 由 `dev-autopilot-developer` 改为 `dev-autopilot-developer-wukong`。受治理 API 已发布于 `2.8.53`，且显式确认入口已发布于 `2.8.55 / 9796b475d7d5`；AgentCiCi 权威记录、Keycloak client 和 identity mirror 均在事务/补偿边界内处理，Secret 不读取、不轮换。当前线上仍未改名：本次执行环境没有可用的 Oliver ORG_ADMIN 会话，未绕过人类委托直接改库。待 Oliver 在“机器主体 → 悟空”完成一次确认后，立即同步 DevAutopilot allowlist 与 root-only CLI 凭据并回归。

- TASK-269：已按用户明确授权完成生产全局账户 `18611892001` 的公共编号更正：`U20267MV3E4N7 → U2026OLVX1230`。预检确认目标手机号唯一、目标编号未占用且唯一/格式/不可变保护正常；PostgreSQL 备份位于 `/opt/cici/backups/20260805-150512-before-task269-user-public-id-correction`。受当前旧值保护的单行事务更新受影响数为 `1`，提交后独立回读确认目标编号全局唯一、旧编号为 `0`、触发器已恢复启用；数据库 healthy、backend health=UP。未修改 Keycloak、密码、MFA、组织成员、Principal 或其他业务数据，也未发布应用。

- TASK-268 / FEAT-160：第一、第二阶段已完成 AgentCiCi 侧本地实现并提交，包含 Semattice 只读适配、稳定绑定、反向导入提案、漂移阻断、确定性编译、影响模拟、独立审批、回填/覆盖校验、激活、候选取消和非破坏性安全回滚；已连接工作区不能绕过运行治理直发本地版本。V105 与真实跨系统闭环尚未在隔离/生产环境执行；破坏性字段退役与 purge 继续失败关闭，第三、第四阶段未开始。

- TASK-252 / FEAT-145：已发布生产 `2.8.52 / 8c9ce75884c5`，修复统一身份接入后的两处密码生命周期缺陷。个人档案不再将密码提交给 AgentCiCi，而是启动 Keycloak 的 `UPDATE_PASSWORD` OIDC 动作，并强制重新认证；OIDC 启用时旧本地密码写接口会拒绝，杜绝“页面成功、实际 Keycloak 密码未变”。人工邀请 Required Actions 邮件落地已改为 `/app`，Keycloak `agentcici-bff` 精确白名单仅保留 `/app` 与 `/auth/oidc/callback`。发布前两次完整备份均非空（最终备份 `/opt/cici/backups/20260805-143617-before-2.8.52-oidc-password-route`）；六容器 healthy、health=UP、Nginx 有效、x HTTPS=200/HTTP=301，线上密码入口实际 302 至 Keycloak 且断言 `UPDATE_PASSWORD`、`prompt=login`、PKCE/state/nonce 与正常 callback。未使用任何用户密码、令牌或邮件链接；真实新成员首次激活由正常管理员邀请完成。

- TASK-267 / FEAT-159：已发布生产 `2.8.50 / 82e1c249e622`。AgentCiCi 组织控制台“机器主体”入口位于“组织架构 → 机器主体”，路由为 `/admin/service-principals`；它调用受 ORG_ADMIN 保护的 SERVICE Principal API，展示主体、Client ID、最小 scopes、受众、人类负责人及生命周期，并提供明确确认后的暂停、恢复与 Client Secret 轮换。新密钥仅存在 React 内存并仅本次显示，切换主体/关闭面板即清除；没有历史密钥读取、浏览器持久化或日志输出。发布前四类备份非空，六容器 healthy、backend health=UP、Nginx 有效、`x` HTTPS=200、HTTP=301，匿名机器主体 API 为预期 401；受权管理员真实会话验收待完成。

- TASK-266 / FEAT-158：已发布生产 `2.8.49 / 760776a354f5`，修复 AI表格目录和记录请求绕过统一 Bearer 会话的问题。`requestAiTable` 现复用 `authFetch(LS_ASSISTANT_TOKEN, ...)`，保留同源 Cookie、Token 更新重试和原有受保护 API 错误语义；浏览器仍不接触 OACT、租户或公司标识，鎏金账房和受控主题结构不变。backend/frontend ACR index digest 为 `sha256:eb931697527bcdfbc8486a7a23910c0f37ddd8b8be0bfd3a356b9499e8ce576c` / `sha256:69f7573a3bbfb9b2f7b41638905e539e866bd95721cd81fbc7a26de2f796f209`，发布前四类备份位于 `/opt/cici/backups/20260805-075621-before-2.8.49-ai-table-auth` 且均非空。六容器 healthy、backend health=UP、版本/Nginx/x HTTPS/HTTP 均通过；匿名与无效 Bearer 的 AI表格目录请求均正确返回 401。当前会话无受权成员 Cookie/测试账号，未伪造真实记录回读。

## Snapshot

- 生产已发布 `2.8.47 / aeeb24f9ea66`：包含 TASK-255 的 `/app` 未登录自动 OIDC 跳转及 TASK-266 的 AI表格业务对象列表预览。backend/frontend 不可变 ACR digest 分别为 `sha256:28980489578b0bdc50d148941056154833d96c8fc16e5afb0aa8d6dcedeba686` / `sha256:fc36895b5063c30665edbf2a419564d56ff54fa81172318918eec463322133a5`；发布前备份 `/opt/cici/backups/20260804-233730-before-2.8.47` 的环境、PostgreSQL、知识库与 Qdrant 均非空。仅重建 backend/frontend，六容器 healthy，`/actuator/health=UP`、版本接口返回 `2.8.47` 与该提交、Nginx 校验通过、`x.agentcici.com` HTTPS=200、HTTP=301、匿名 `/auth/me`=401。`onechat.agentcici.com` 仍无法 DNS 解析，未作为发布成功依据。

- TASK-265 / FEAT-157：DEV Autopilot 产品经理评审能力已完成生产闭环。`dev-autopilot-pm` 显式绑定 query/create/review 三个 Tool、always-on Skill 与产品经理 SERVICE Principal；第三方开发者 CLI 已完成设计驳回/批准、进度与工时、阻塞上报/解除、制品提交、完成申请和最终批准。正式任务 `019fcc18-756f-7782-a9e7-bf34e9c94670` 最终为 `已完成 / 100% / revision 13`；哪吒休息态与产品经理冒用开发者 CLI 的负向边界均通过。

- TASK-253 与 TASK-255 已按用户授权合并至 `main`。TASK-253 的计费修复此前已由 TASK-254 发布，历史合并未重复引入功能差异；TASK-255 的 `/app` 访客一次性 OIDC 自动跳转、回调票据保护和 5 项定向测试已随 `2.8.47` 发布生产。

- TASK-252 / CloudCC CRM orgId 契约：已发布生产 `2.8.44 / 4690e58cc154`。AgentCiCi 租户外键 `integration_app.company_id` 保持不变；CloudCC 配置/Token 请求统一为 `orgId`，兼容读取旧 `config.companyId`。V104 已成功从旧键或网关 URL 回填，6 个 CloudCC 集成中 5 个已有 `orgId`，香港大学仍未配置。定向测试、backend compile、前端 build、不可变镜像、发布前四类备份、六容器健康、V104、Nginx、版本接口、x=200 与匿名 auth=401 均通过；等待受权成员实际 CRM 数据回读。

- TASK-252 / 公司切换会话隔离：已发布生产 `2.8.45 / 435ee0af6e2d`。截图页脚仍为 2.8.42，而服务器实际已是 2.8.44，确认旧 SPA 入口被浏览器复用。现已将工作台初始状态也按 `companyId` 建键，SSE/轮询回调捕获公司作用域并在旧作用域静默终止；`/app` 入口为 `no-store`，哈希 `/assets/` 为 immutable。定向 Vitest 5/5、TypeScript/Vite build、发布前四类备份、六容器健康、Nginx、版本接口、入口/资源缓存响应头、旧资源 404、`x.agentcici.com` 200 和匿名 `auth/me` 401 均通过；等待受权用户刷新后复核公司 A→B→A 页面内容。

- TASK-252 / FEAT-145：人类邀请修复已发布 `2.8.41 / 3320ed77515d`。Flyway V102 成功，Keycloak Realm SMTP（SSL 465）和人工 provisioning 均为受控启用；既有绑定按远端 `sub` 复核，未激活才重发 Required Actions，已激活不重置密码。远端用户缺失时仅以不可变 public ID、受管 account ID 属性和邮箱同时证明归属后重绑，否则 fail closed；重复邀请不能恢复已停用成员。2026-08-04 已完成历史回填：5 个活跃但未绑定的全局账户均已创建/复用 Keycloak User、写入 issuer+subject 映射并触发邮箱验证与设置密码邮件；当前活跃未绑定数为 0，5/5 初始动作均就绪。另已在用户确认后修复一例重复 Keycloak User 和一例 Keycloak 空邮箱：前者仅将本地 issuer+subject 绑定切换至原可用手机号登录身份，后者补写已验证邮箱并重发初始化邮件；Principal 镜像已同步，未重置密码、未删除用户。backend/frontend healthy，根路径 200，匿名鉴权和服务交换入口均为预期 401。首个新成员的真实邮箱点击/首次 OIDC 激活由正常业务邀请完成，不伪造用户凭据。

- TASK-264 / FEAT-156：研发身份花名已收敛为 Oliver（HUMAN 产品总监）、大乔（SERVICE 产品经理）、悟空和后羿（SERVICE 开发者）。三台 SERVICE 的 PRIMARY owner 均为 Oliver；后羿已复用开发者角色和研发交付部 primary membership。Semattice 已认证控制台回读 4 members / 3 roles / 1 organization / 5 objects / 42 fields，悟空/后羿 CLI 正向与大乔越权负向均通过。后羿凭据只保存在生产 root-only 文件。

- TASK-263 / FEAT-155：已发布生产 `2.8.40 / f4011a8a3b79`。研发交付产品经理显式绑定 `semattice_project_delivery_query`、`semattice_project_delivery_create` 和标准 Skill `semattice-project-delivery-management`；Agent→SERVICE Principal `742daca1-ce58-49cc-9e53-530444ba1c47` 使用 `PRIMARY_OWNER` 委托和最小 scope OACT，产品总监 HUMAN 只提供委托与确认上下文。线上查询返回 Semattice 实时项目，未确认创建 Trace 工具数 0，明确确认后 SERVICE 创建 `DAS-941C43CF`；Semattice 审计的 query/create actor 均为 SERVICE，记录 owner 为“DEV Autopilot 产品经理”。三端公网健康均为 200。

- TASK-262 / FEAT-154：已发布生产 `2.8.38` 并完成 DEV Autopilot 研发身份体系。全局用户 `18611892001` 绑定产品总监 HUMAN；产品经理和开发者 SERVICE 均以其为 PRIMARY 人类负责人。管理端已具备查询、密钥轮换、暂停、恢复、永久撤销、负责人移交和脱敏审计；开发者主体在 AgentCiCi 暂停时 CLI 被拒绝，恢复后可用，轮换后旧 secret 失效、新 secret 可完成短时 OACT 与任务读取。HTTPS 入口曾因仅使用基础 Compose 重建而丢失 443，已恢复 SSL override，并将 `/devautopilot/` 动态代理固化到两份版本化 Nginx 配置；公网健康为 200。

- TASK-261 / FEAT-153：已发布生产 `2.8.34 / 84c814b19fe0`。未确认创建请求现由 `onekeytoken/auto` 理解完整自然语言并生成草案，服务端不再正则抽取名称；截图原句正确得到 `AgentCiCi企业级智能体平台`。Trace 显示模型调用 1 次、工具调用 0 次、`WAITING_CONFIRMATION`；Semattice 同名项目计数为 0。精确确认后的 OACT 同租户写入门禁保持不变。

- TASK-260：`2.8.33` 的正则热修复已由 TASK-261 的模型语义方案替代；不再继续扩展自然语言名称正则。

- TASK-259 / FEAT-152：已发布生产 `2.8.32 / 2e42ed3ec926`。研发交付产品经理现具备同租户项目、需求、任务创建能力，采用“草案—用户精确确认—服务端合成写入—Semattice 回执”边界；模型不拥有自由写工具，当前成员短期 OACT 是唯一身份/租户来源。持久化智能体提示词已同步这一规则。线上已验证未确认请求只返回草案，随后创建 `DAS-00B30667 / 棕榈地`、`REQ-02F5F798 / 项目启动工作台` 和任务“搭建项目启动页”，智能体实时查询及 Semattice 父子 UUID 回读均正确。发布前四类备份非空，六容器健康，backend health `UP`、版本接口为 2.8.32。

- TASK-258 / FEAT-151：已发布生产 `2.8.31 / 5c8953a3284d`。研发交付产品经理使用当前登录成员的短期 OACT 读取同租户 Semattice 已发布研发交付对象；`dev-autopilot-pm` 不再被降级为默认 CiCi，项目事实问题会先执行只读查询再总结。生产真实对话已返回 `DAS-DEMO / 星轨移动销售助手`（执行中、35%）、2 个进行中任务、5.5 小时已登记工时及 2 项确认变更；不含“无法直接访问”通用回退。`2.8.29` 的空工具名异常和 `2.8.30` 的自定义智能体降级均已由 `2.8.31` 覆盖。

- TASK-257 / FEAT-150：已发布生产 `2.8.28 / f2814efc3a07`。助手工作台的 AI 应用启动器现有“DEV Autopilot / 研发交付”外部入口；它复用当前菜单语汇，在当前页跳转至 `https://x.agentcici.com/devautopilot/`，不复制应用业务数据或改变认证契约。定向测试、32 个前端测试文件（199 tests）、生产构建、六容器健康、版本接口与两个公网入口均通过；无用户凭据的生产浏览器按预期处于统一登录边界，未伪造已登录会话。

- TASK-256 / FEAT-149：已发布生产 `2.8.27 / fa9a843dd143`。同租户双人元数据审批事实与 OACT `approvals` claim 已上线；同一管理员自审批为 403，另一有效管理员批准后，原发起人 OACT 成功发布 DEV Autopilot 首个 Semattice 元数据版本。版本含 5 个对象、37 个字段；`DAS-DEMO` 演示项目、需求、任务、工时和变更均已写入，独立应用真实需求/任务与变更确认闭环通过。

- TASK-255 / FEAT-148：用户反馈 AgentCiCi 点击登录后停留在统一账号中间页。已确认 `AssistantApp` 只在按钮点击时调用既有 OIDC 入口；本任务会让无会话 `/app` 自动跳转至 `/auth/oidc/login`，保留 OIDC/CloudCC 回调票据处理和手动回退，不改后端或生产。

- TASK-254 / FEAT-147：全面审计并修复当前可执行的 company_id 遗留：账单席位 JPQL 改为 `member.company.id`，本地 E2E 登录改用 `E2E_COMPANY_ID`/`companyId`，Qdrant smoke 与生产演示 SQL 改用 `company_id`。主线 `105cc666a958` 已发布生产 `2.8.25`；发布前四项备份非空，六服务健康，backend health `UP`、版本接口、Nginx、`x.agentcici.com` 和匿名 `401` 边界均通过。Flyway 历史、迁移验证及前端旧响应 `orgId` 兼容保持不动；本机 PostgreSQL `127.0.0.1:5432` 不可达，账单集成测试待数据库可用时补跑。TASK-253 已被本任务替代，不单独合并。

- TASK-252 / FEAT-145：AgentCiCi `main` 已发布 `2.8.24 / 58a96d618207`，Semattice Principal 投影已发布 `20260727T151437Z-console`。V98/V99 建立并回填 HUMAN/SERVICE Principal、Keycloak identity mirror、责任人、幂等操作与机器 scope；生产 Flyway V98/V99 成功。服务交换端点为 `/openapi/v1/official/service-token`，缺少 Bearer 为 401、feature flag 关闭时为 403；Semattice 只接受短期 OACT 并本地 JWKS 验签，不接受原始 Keycloak service token。人类 provisioning 与 service-token-exchange 由 Compose 显式传入且保持 `false`；机器 `machine-provisioning` 已启用，provisioner secret 已仅写入部署环境，Client Credentials 管理令牌实测成功（300 秒），但尚未创建任何机器主体。Realm 仍无 SMTP，故未开启人类邀请。六服务 healthy、backend health `UP`、`x.agentcici.com` 200、匿名 `/auth/me` 401、OACT JWKS 200。`onechat.agentcici.com` DNS 不可解析，作为既有入口风险保留。

- TASK-251 / FEAT-144 与 TASK-248 / FEAT-141 已发布生产 `2.8.19 / 99d4cc3cb206`。Flyway V97 成功回填全部历史账户的 `UYYYYXXXXXXXX` 公共编号，并为新账户维持格式、唯一与不可变约束；生产库 `public_id` 空值与格式不匹配均为 0。平台“注册用户”目录保持一账户一行，并只读展示公共编号及已加入组织。六服务健康、Nginx 校验通过，生产 IP/SNI 的 onechat/x HTTPS 均为 200，匿名 `/auth/me` 为 401。真实受权平台会话的目录列展示仍待后续复核。

- TASK-250 / FEAT-143：已合并 `main`（`4958bc1`）。`McpClient` 在解析结果前读取 `initialize` 响应的 `Mcp-Session-Id`，包括 SSE 分支；只有同一会话的 `notifications/initialized` HTTP 成功后才允许 `tools/list`/`tools/call`。所有 MCP POST 最终统一使用 `MCP-Protocol-Version: 2025-03-26` 与当前内存会话 ID，配置或动态头不能注入陈旧协议/会话值，既有动态 Bearer JWT 仍会透传。定向本地 HTTP 测试验证严格四步顺序、SSE 会话捕获、工具列表/调用请求头和 Bearer JWT；后端编译与 diff 检查通过。未修改 Semattice、数据库、前端或生产环境；生产发布和真实受权复核仍需单独授权。

- TASK-249 / FEAT-142：用户报告组织管理端“组织简档”加载失败。已定位为生产 Nginx 和本地 Vite 仍代理旧 `/admin/organization/...`，而当前 `GET /admin/company/profile` 落入 SPA `index.html`：生产匿名请求返回 `200 text/html`，不是后端鉴权 JSON。当前生产 V96 的组织简档统计相关表和 `company_id` 字段已只读核实存在；本任务仅补齐精确 API 代理，不改后端、数据、权限、UI 或生产环境。

- TASK-248 / FEAT-141：账户分页继续以 `user_account` 为唯一行源；服务层批量读取有效 `company_member` 后按企业去重，接口只读返回 `organizations`，前端展示“已加入组织”列。该能力已随 `2.8.19` 发布，真实受权平台会话验收仍待完成。

- TASK-247 / FEAT-140：已合并 main（`38cb22e`）并发布 `2.8.15`。运营端“注册用户”现从 `user_account` 查询全部个人账户，不再用 `company_member` 排除已加入组织者；账户表是唯一行源，所以一人加入多个组织仍只显示一次。完整后端测试、前端定向测试/构建、Compose 配置和 diff 检查通过；发布前四项备份均非空，六服务健康，版本为 `2.8.15 / 38cb22e3a587`，`agentcici.com`、注册用户路由和 `x` 均通过，匿名平台接口仍为 401。未使用或伪造平台凭据，真实目录内容待受权账号复核。

- TASK-246 / FEAT-139：已合并 `main`（`6cee975`）并发布 `2.8.14`。租户 API 边界会把迁移期 `orgId` 归一为 `companyId`，并在生成/请求详情路由前拒绝无效标识，避免 `/platform/tenants/undefined` 触发 `Validation failure`。合并后 21 项前端定向测试、构建、Compose 配置和 diff 检查通过；发布前四项备份均非空，backend/frontend 健康，版本接口为 `2.8.14 / 6cee975539e4`，Nginx 与公网 `agentcici.com`、`/platform/tenants`、`x` 均通过。未使用平台账号，真实受保护页面交互待受权复核。

- TASK-245 / FEAT-138：已合并 main（`c6822c4`）并发布 `2.8.17`。AgentCiCi 产品菜单改为触发器左对齐，避免弹层越过侧栏左界；Semattice 顶栏菜单明确当前项并可直接回到 `https://x.agentcici.com/admin`，不传递 OACT。生产四项备份均非空，backend/frontend 与四个状态服务均健康，health `UP`、Nginx 有效、`x.agentcici.com` 为 200，匿名 `/auth/me` 和新端点均为预期 401。未使用或伪造真实管理员凭据，双向切换可由受权会话继续复核。

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
