---
kind: feature-spec
feature_id: FEAT-081
title: 客户互动工作台
status: implemented
owner_role: fullstack-agent
task_ids: TASK-171
related_decisions: FEAT-067, FEAT-079, FEAT-080
updated_at: 2026-07-09T19:21:00+08:00
updated_by: MANAGER-001
---

# FEAT-081 - 客户互动工作台

## 背景与目标

客户互动工作台是一个面向销售、售前、客户经理和销售主管的 AgentCiCi AI 应用。它不把“拜访记录”作为核心概念，而是把微信沟通、电话、会议、客户反馈、售前交流和 CRM 已有数据统一理解为“客户互动事实”，再通过 AI 帮助用户识别推进机会、客户风险、下一步行动和可落地 CRM 更新。

本特性同时走两条交付线路：

- AgentCiCi 侧：构建客户互动工作台 AI 应用、支撑智能体、技能、工具调用和演示数据服务。
- CloudCC CRM 侧：复用 CRM 标准对象和标准字段，补充必要模块入口、页面承载和演示数据，让工作台可以反向嵌入或联动 CRM。

最终目标是达到生产就绪：用户可在 AgentCiCi AI 应用列表进入工作台，从客户列表进入客户详情，查看新客户推进和老客户经营视图，使用右侧 AI 客户助理通过文字或语音指令查询、整理、生成跟进任务、查看风险、沉淀互动摘要，并能把建议落地到 CRM。

## 引用依据

- CloudCC 平台文档：`platform/overview introduction`、`platform/capabilityMap introduction`、`platform/standardCapabilities introduction`。
- CloudCC 方法论：`methodology/moduleDesign devguide`。
- 目标租户已通过 MetadataService `standard-catalog` 验证：8 个应用、141 个菜单、192 个对象、4854 个字段。
- AgentCiCi 产品事实源：`PRODUCT.md`、`DESIGN.md`、`DESIGN.json`。

## 能力路径

CloudCC 侧命中：

```text
standard-catalog -> Account/Contact/Lead/Opportunity/Task/Event 复用判断
-> fields/recordType/pagelayout -> menu/application -> OpenAPI/pagecomponent
-> profile/sharingRule
```

AgentCiCi 侧命中：

```text
AI app -> agent/skill/tool bindings -> CloudCC integration session
-> customer interaction APIs -> workbench UI -> seeded demo data
-> runtime audit and production validation
```

## 用户与场景

### 角色

- 销售：快速判断新客户是否值得推进，生成下一步跟进任务，补齐联系人和商机信息。
- 售前：从散落沟通中提炼需求、痛点、方案关注点、技术风险和待确认事项。
- 客户经理：经营老客户，识别续约、增购、满意度、服务风险和关键人变化。
- 销售主管：查看团队客户推进和老客户经营风险，追踪 CRM 落地情况。

### 场景

- 新客户推进：从线索、客户、联系人、互动摘要和商机信号中判断跟进优先级。
- 老客户经营：围绕已成交或存量客户，识别续约、复购、服务风险、关系健康和价值提升机会。
- 互动整理：把电话、微信、会议、客户反馈等碎片内容整理为摘要、客户事实、需求、风险、行动项和 CRM 建议。
- AI 操作工作台：用户通过右侧 AI 客户助理切换客户、查询互动、生成任务、查看风险或采纳建议。

## CloudCC 标准对象复用判断

目标租户已扫描：是。

命中的标准对象：

- `Account`：客户主档，作为工作台客户列表和客户详情主对象。
- `Contact`：联系人与关键人信息。
- `Lead` / `cloudcclead`：潜在客户和市场线索。
- `Opportunity`：商机、新客户推进和成交机会。
- `Task` / `Event`：跟进任务、会议、电话、拜访等可执行活动。
- `Case` / 服务对象：老客户服务风险和客户反馈的可选来源。
- `Contract` / `Order` / `Quote`：老客户经营中的合同、订单、报价、续约或增购线索。

复用原则：

- 不新建“拜访记录”对象。互动日志不等于拜访，首版以 AgentCiCi 侧的互动聚合数据和 CRM 标准活动对象承载可落地动作。
- 不新建客户主档、联系人、商机、任务的同义对象。
- 只有当客户互动事实需要独立审计、来源追踪和 AI 分析版本时，AgentCiCi 平台侧新增本地表；CRM 侧仍优先用标准对象承载最终行动。

## 全局对象地图

| 领域 | 对象/模块 | 归属 | 说明 |
|---|---|---|---|
| 客户主档 | Account | CloudCC 标准对象 | 客户列表、客户详情、客户分层、负责人 |
| 联系人 | Contact | CloudCC 标准对象 | 关键人、角色、联系方式 |
| 线索 | Lead/cloudcclead | CloudCC 标准对象 | 新客户早期来源 |
| 商机 | Opportunity | CloudCC 标准对象 | 新客户推进和增购机会 |
| 活动/任务 | Task/Event | CloudCC 标准对象 | 跟进任务、会议、电话、拜访等落地动作 |
| 服务风险 | Case/服务对象 | CloudCC 标准对象优先 | 老客户服务反馈和风险 |
| 互动事实 | customer_interaction_event | AgentCiCi 平台表 | 微信、电话、会议、反馈等统一事实流 |
| AI 建议 | customer_workbench_recommendation | AgentCiCi 平台表 | CRM 落地建议、置信度、采纳状态 |
| 工作台客户画像 | customer_workbench_snapshot | AgentCiCi 平台表 | 客户摘要、健康度、风险、机会、下一步行动 |

## AgentCiCi 数据模型

新增 Flyway 迁移，建议文件：`V72__customer_interaction_workbench.sql`。

### customer_interaction_event

用于记录从 CRM、演示数据或外部渠道同步来的互动事实。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid/string | 主键 |
| org_id | varchar | AgentCiCi 组织 |
| crm_account_id | varchar | CloudCC Account id |
| crm_contact_id | varchar | 可选联系人 id |
| source_type | varchar | `WECHAT`、`PHONE`、`MEETING`、`EMAIL`、`CRM_TASK`、`CUSTOMER_FEEDBACK`、`DEMO` |
| occurred_at | timestamp | 互动发生时间 |
| subject | varchar | 主题 |
| raw_summary | text | 原始摘要或演示摘要 |
| ai_summary | text | AI 整理摘要 |
| sentiment | varchar | `POSITIVE`、`NEUTRAL`、`NEGATIVE` |
| intent_tags | json/text | 需求、风险、预算、竞品、服务、续约等标签 |
| lifecycle_area | varchar | `NEW_CUSTOMER`、`EXISTING_CUSTOMER`、`MIXED` |

### customer_workbench_recommendation

用于保存 AI 可落地建议。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid/string | 主键 |
| org_id | varchar | 组织 |
| crm_account_id | varchar | 客户 |
| recommendation_type | varchar | `CREATE_TASK`、`UPDATE_OPPORTUNITY`、`CREATE_OPPORTUNITY`、`UPDATE_RISK`、`ADD_CONTACT`、`CREATE_CASE` |
| title | varchar | 建议标题 |
| rationale | text | 依据 |
| confidence | numeric | 置信度 |
| status | varchar | `PENDING`、`ACCEPTED`、`DISMISSED`、`APPLIED` |
| crm_payload | json/text | 拟写入 CRM 的 payload |
| applied_crm_id | varchar | 落地后 CRM 记录 id |

### customer_workbench_snapshot

用于快速驱动页面。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid/string | 主键 |
| org_id | varchar | 组织 |
| crm_account_id | varchar | 客户 |
| segment | varchar | `NEW`、`EXISTING`、`STRATEGIC`、`RISK` |
| health_score | integer | 老客户健康分 |
| progress_score | integer | 新客户推进分 |
| risk_count | integer | 风险数 |
| next_action_count | integer | 下一步行动数 |
| snapshot_json | json/text | 页面聚合快照 |

## 状态机

### AI 建议状态

```text
PENDING -> ACCEPTED -> APPLIED
PENDING -> DISMISSED
ACCEPTED -> PENDING (用户撤销采纳)
APPLIED 为终态，后续修改需要产生新建议。
```

### 客户经营状态

```text
新客户：待识别 -> 待跟进 -> 方案沟通 -> 评估决策 -> 商机推进 -> 成交/暂缓/流失
老客户：健康 -> 需关注 -> 风险中 -> 挽回中 -> 稳定/流失
```

注意：状态不是强制按拜访阶段切分互动内容。一次互动可以同时命中新客户推进、方案需求、服务风险和老客户经营多个标签。

## API 设计

### 双向登录与身份一致性

客户互动工作台必须遵循以下 token 边界：

- CloudCC CRM 页面运行态 token 只作为 CloudCC 当前登录用户的身份入口和服务端校验凭据，不得作为 AgentCiCi 登录 token 使用。
- AgentCiCi 登录 token 只用于访问 AgentCiCi 工作台和平台 API，不得作为 CloudCC OpenAPI 或 CloudCC MCP 调用凭据。
- CloudCC OpenAPI / CloudCC MCP 调用必须统一使用 AgentCiCi 后端通过当前 AgentCiCi 用户绑定信息生成的 CloudCC accessToken，即 `CloudccAccessTokenService` 产出的 `CloudccSessionContext.accessToken`。
- 同一个 CloudCC 用户无论从 CloudCC CRM 嵌入页进入，还是从 AgentCiCi AI 应用列表进入，都必须映射到同一个 AgentCiCi `organization_member`；否则客户数据权限会漂移。
- 首版不做自动创建 AgentCiCi 用户。CRM 免登录进入时，必须能在当前 AgentCiCi 组织中找到 `cc_username` 匹配的已启用成员，并且该成员已有可生成 CloudCC accessToken 的绑定信息；找不到时返回“请先绑定 CloudCC 账号”。

推荐登录链路：

```text
CloudCC pagecomponent
  -> $CCDK.CCToken.getOpenApiToken()/getToken + $CCDK.CCUser.getUserInfo()
  -> POST /auth/cloudcc-sso/ticket
  -> AgentCiCi 服务端校验 CloudCC token 可被 CloudCC setup read endpoint 接受
  -> 按 orgId + ccUsername 映射 AgentCiCi 成员
  -> 确认可通过 CloudccAccessTokenService 生成 CloudCC accessToken
  -> 返回 60 秒一次性 ssoTicket
  -> iframe 打开 /app?aiApp=customer-workbench&ssoTicket=...
  -> POST /auth/cloudcc-sso/consume
  -> 写入 AgentCiCi 登录态
```

接口：

- `POST /auth/cloudcc-sso/ticket`
  - public endpoint。
  - 请求体包含 `agentOrgId`、`cloudccAccessToken`、`cloudccUser`、`parentOrigin`、`targetPath`。
  - 后端不得信任前端传入的用户名；必须先校验 CloudCC token，再与用户映射结果交叉校验。
  - 返回一次性 `ticket`、`expiresAt`、`targetUrl`。
- `POST /auth/cloudcc-sso/consume`
  - public endpoint。
  - 请求体包含一次性 `ticket`。
  - 成功后返回与 `/auth/password/login` 一致的登录 payload。

### 工作台 API

- `GET /customer-workbench/accounts`
  - 返回客户列表、客户分层、负责人、最近互动、风险数、建议数。
- `GET /customer-workbench/accounts/{accountId}`
  - 返回客户详情聚合快照。
- `GET /customer-workbench/accounts/{accountId}/timeline`
  - 返回互动时间线。
- `GET /customer-workbench/accounts/{accountId}/recommendations`
  - 返回 CRM 落地建议。
- `POST /customer-workbench/recommendations/{id}/accept`
  - 采纳建议。
- `POST /customer-workbench/recommendations/{id}/apply`
  - 写入 CloudCC CRM。
- `POST /customer-workbench/interaction-drafts`
  - 从文本/语音转写内容生成互动草稿和建议。
- `POST /customer-workbench/assistant`
  - 工作台右侧客户助理对话入口，支持页面切换指令。

### CloudCC 工具能力

工作台智能体可绑定以下技能/工具：

- 查询客户列表和客户详情。
- 查询联系人、线索、商机、任务、活动、服务反馈。
- 生成互动摘要、风险信号和下一步行动。
- 创建或更新 CRM 跟进任务。
- 创建商机或更新商机阶段。
- 补充联系人建议。
- 生成主管视角客户风险汇总。

高风险写入动作必须进入确认或采纳流程，不允许 AI 静默写 CRM。

## 前端设计

### 入口

客户互动工作台出现在 AgentCiCi AI 应用列表中。进入后仍在认证后主界面内运行，不做营销页。

### 主页面布局

延续用户确认的三栏工作台：

- 顶部：`新客户推进 / 老客户经营` 为主模式切换。两者是不同客户队列之间的互斥切换，不是单个客户详情里的页签。
- 左侧：根据当前主模式展示对应客户队列，含搜索、分组、筛选、客户列表和风险/建议标记；切换主模式时同步切换客户池和首个客户。
- 中间：客户详情，顶部为客户名、CRM 打开入口、摘要指标；主内容根据当前主模式展示不同页签。
  新客户推进模式含 `推进概览 / 互动时间线 / 推进信号 / CRM 落地建议 / 下一步行动`。
  老客户经营模式含 `经营概览 / 互动时间线 / 服务问题 / 价值兑现 / 续约增购 / 关系地图`。
- 右侧：AI 客户助理，对话、语音输入、快捷操作和页面切换指令。

### 新客户推进

展示：

- 商机成熟度。
- 关键联系人覆盖。
- 需求明确度。
- 预算/时间/决策链信号。
- 下一步建议。

### 老客户经营

展示：

- 健康度与风险趋势。
- 续约/增购机会。
- 服务问题和客户反馈。
- 关键人关系维护。
- 最近价值触点。

### 视觉方向

注册表面：`product`。

物理场景：销售或客户经理在 27 英寸办公显示器上，一边看 CRM 客户数据，一边把刚结束的电话、微信和会议内容整理成可执行行动，环境是白天办公场景，需要高密度、低疲劳、可信的业务工作台。

方向：克制的企业客户经营舱。使用暖象牙底、墨色文本、香槟金结构线；客户状态用少量蓝、绿、红、琥珀语义色辅助，不使用大面积渐变、玻璃拟态或营销式 hero。

### 高保真实现约束

用户最新确认的 CRM 标准蓝/鎏金工作台稿是当前实现基准。工作台首屏必须保留：

- 顶部 AgentCiCi 品牌与 `AI 应用 / 客户互动工作台` 面包屑、CloudCC CRM 已连接状态、通知/帮助/用户区域。
- 顶部右侧保留 `新客户推进 / 老客户经营` 主模式切换；严禁使用旧的销售导向命名。
- 左侧队列随主模式切换：新客户模式显示 `新客户推进队列`，含重点推进、待跟进、风险客户、待确认建议；老客户模式显示 `老客户经营队列`，含续约90天、健康下降、服务异常、增购信号。
- 中间客户详情，包含客户名、`Account`、`Opportunity`、负责人、关注状态、最近互动、打开 CRM 客户主页按钮和 4 个摘要指标；页签必须随主模式切换，不能把 `新客户推进` 和 `老客户经营` 作为同一客户详情内的并列页签。
- 概览首屏必须同时露出互动时间线和 CRM 落地建议，两列卡片不能被底部截断。
- 客户区域下方必须保留业务汇总框：新客户模式为 `推进关键项`，老客户模式为 `服务与关系预警`，用于承载从工单、会议、微信和 CRM 更新中提取的关键风险/机会。
- `新客户推进` 必须展示 5 段推进阶段、推进信号、建议动作和 CRM 补齐项。
- `老客户经营` 必须展示续约稳定、服务响应、增购机会、关系覆盖，以及经营信号、风险与阻塞、客户价值动作。
- 右侧 AI 客户助理必须保留语音/输入指令入口、对话区、快捷操作、输入框和 AI 内容提示。

## 演示数据

首版需补充足够演示数据，覆盖：

- 12 个客户，包含新客户、存量客户、战略客户、风险客户。
- 每个客户 3 到 8 条互动事实，来源覆盖微信、电话、会议、客户反馈、CRM 任务。
- 至少 5 个新客户推进场景，含方案沟通、预算确认、竞品比较、决策链不清、待约演示。
- 至少 5 个老客户经营场景，含续约、增购、服务风险、满意度下降、关键人变化。
- 至少 20 条 AI 建议，覆盖创建任务、更新商机、补充联系人、创建服务风险、生成复盘摘要。

演示数据必须可重复初始化，不依赖生产真实客户数据。

## 权限与安全

- 所有 API 必须按 org 隔离。
- CRM 写入动作必须校验 CloudCC 连接绑定和当前用户权限。
- AI 建议采纳和写入 CRM 必须可审计。
- 敏感信息进入模型或审计前应复用 FEAT-080 的安全网关。

## 生产就绪验收

- AI 应用列表能看到并进入“客户互动工作台”。
- 客户列表能加载 CRM/演示客户，并能进入客户详情。
- 新客户推进和老客户经营两个主视图可用。
- 右侧 AI 客户助理能根据用户指令切换客户、查询风险、总结互动、生成跟进任务建议。
- 采纳建议后能进入确认流程，确认后能写入 CloudCC CRM 或在演示模式下写入本地模拟结果。
- CloudCC MetadataService/OpenAPI 连通验证通过。
- 演示数据初始化后页面有完整可讲述故事线。
- 后端测试、前端构建和桌面端 Playwright 验证通过。

## 当前实现状态

更新时间：2026-07-09T19:21:00+08:00。

- AgentCiCi 侧已完成首版实现：后端新增客户互动事实、CRM 落地建议和客户快照模型；初始化 12 个演示客户、36 条互动事实、24 条建议；提供客户列表、客户详情、建议采纳/落地和 AI 客户助理 API。
- 智能体/技能侧已新增内置技能 `customer-interaction-workbench`，绑定到系统助手和销售助手，默认提示约束 AI 先读取 CRM/互动事实、再生成可确认的 CRM 落地建议。
- 前端已在认证后主界面 AI 应用列表新增“客户互动工作台”，实现左侧客户推进队列、中间客户详情、新客户推进、老客户经营、CRM 落地建议、右侧 AI 客户助理和语音入口。
- AgentCiCi `/app?aiApp=customer-workbench` 仍作为平台内 AI 应用直达入口；CloudCC CRM 嵌入必须使用 `/app?aiApp=customer-workbench&embed=crm`。`embed=crm` 是 CRM 专用轻量入口，只渲染客户互动工作台主体，不渲染 AgentCiCi 左侧平台栏、AI 应用列表、主页 header 或其他平台无关内容。
- CloudCC CRM 侧已通过 OpenAPI 验证 `Task`、`Event`、`Opportunity` 标准对象可查询；页面组件 `customer-workbench` 和预构建 UMD bundle 已通过安全临时项目发布到 CloudCC 云端。当前线上有效组件 id 为 `6a4d348fe4b0a577cbba1ebf`，apiName 为 `custc_202607Hdhm60zo`，本地 `frontend/pagecomponents/customer-workbench/config.json` 已同步该 id。
- CloudCC 页面组件默认工作台地址必须为 `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`，SSO `targetPath` 也必须使用同一路径。pagecomponent 自身只负责 CloudCC token 读取、换取 AgentCiCi 一次性 `ssoTicket` 和 iframe 承载，不再显示“AI 应用”“打开工作台”等 AgentCiCi 平台级头部。
- 生产版本 `2.2.7` 和 CloudCC pagecomponent V8 已验证该嵌入形态：CRM 运行态加载 `component-customer-workbench-V8.0.js`，iframe 使用 `/app?aiApp=customer-workbench&embed=crm&ssoTicket=...`，iframe 内无 AgentCiCi 平台侧栏、无 AI 应用列表，仅显示客户互动工作台主体。
- 最新本地高保真 UI 修复已恢复用户设计稿中的工作台细节和业务页签：Playwright 1496x1064 截图 `output/playwright/task171-workbench-fidelity-local-v4.png` 验证首屏无横向/纵向页面溢出，概览页两个“查看全部”入口完整露出；`output/playwright/task171-workbench-new-customer-panel.png` 和 `output/playwright/task171-workbench-existing-customer-panel.png` 验证 `新客户推进` 与 `老客户经营` 业务内容可用。
- CloudCC HTML 组件已通过 devconsole API 保存，作为 CRM 内可承载的 iframe 包装页。线上 HTML 组件 id 为 `6a4d37ece4b0a577cbba1ec0`，apiName 为 `customer_interaction_workbench`，访问路径为 `/oss/html/org0720f814430017229/customer_interaction_workbench-v1.html`。
- 不得直接从当前仓库根目录执行 `cloudcc publish pagecomponent customer-workbench .`，除非先修复 CLI 的 pagecomponent 依赖收集白名单；根目录发布曾把项目配置打入 `compContentVue`，对应云端组件已立即删除。
- CloudCC customPage 已通过 devconsole API 创建并验证：线上 customPage id 为 `6a4d3b831b8c6d0ec6dd22ef`，页面标签 `客户互动工作台`，pageApi `customer_interaction_workbench`。
- CloudCC CRM 菜单已通过 setup 服务创建并绑定销售云：页面菜单 id 为 `acf2026C53BE54B9R1Iu`，tab label `客户互动工作台`，lightning page `customer_interaction_workbench#lightning`，已对 6 个简档授权，并已在销售云应用 `ace20220322Salesloud` 的 `selectedTabList` 中验证可见。
- MSAPI 菜单计划 `pla2026E964195FlLpjf` 仍因当前 OpenAPI JWT 缺少 `metadata:apply` scope 无法 apply；但本任务已通过 CloudCC setup/devconsole API 完成同等页面和菜单配置，该限制不再阻塞 CRM 可见入口。
- 桌面端 Playwright 验收通过，截图为 `output/playwright/task171-customer-workbench-desktop.png`；直达入口验收通过，截图为 `output/playwright/task171-customer-workbench-deeplink.png`。验证工作台标题、AI 应用入口、老客户经营 tab、AI 快捷指令、CRM 落地建议、`置信度 92%`、无横向溢出和控制台 0 error/0 warning。
- 生产发布完成：`2.2.2` 发布主功能，`2.2.3` 修复 HTTPS vhost 对 `/customer-workbench/*` 的代理。当前生产 `/system/version` 返回 `version=2.2.3`、`imageTag=2.2.3`、`gitCommit=f0ec47509bde`；认证后 `/customer-workbench/accounts` 返回 12 个演示客户，客户详情和 AI 客户助理 smoke 均通过。

## 非目标

- 不在首版实现移动端专属适配。
- 不把微信、电话等真实外部渠道打通作为硬依赖；首版用演示数据和可扩展接口。
- 不让 AI 自动静默改 CRM 关键数据。
- 不重建 CRM 客户、联系人、商机、任务等标准对象。
