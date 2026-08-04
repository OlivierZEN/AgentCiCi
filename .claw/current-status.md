---
kind: current-status
version: 4
updated_at: 2026-08-04T15:39:00Z
updated_by: MANAGER-001
phase: ai-table-live-production
active_task: TASK-266
next_action: "TASK-266 已获用户授权进入真实数据接入与生产发布：实现当前会话 OACT 的对象目录/记录查询、游标分页和列偏好；onechat.agentcici.com 的 DNS 解析风险仍需单独修复。"
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

- TASK-266 / FEAT-158：用户已确认 AI表格高保真形态，并授权升级为生产可用的真实数据列表及发布。实现将由 AgentCiCi 服务端基于当前会话成员签发短期 OACT，读取已发布对象目录和严格授权的记录查询结果；浏览器不持有 OACT、租户或公司标识。桌面鎏金账房和现有 8 个主题结构保持不变，范围包括游标分页、受索引约束的查询、列偏好、刷新、详情与明确的 loading/empty/error/permission 状态，不包含写入、批量、导出或移动端。已发现现有 Vite/Nginx 未代理 `/ai-table`，该精确代理补齐已纳入受控发布范围，避免线上 SPA fallback 吞掉 API。

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
