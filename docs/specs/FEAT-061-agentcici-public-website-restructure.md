---
kind: feature-spec
feature_id: FEAT-061
title: AgentCiCi Public Website Restructure
status: draft
owner_role: project-manager
task_ids: TASK-144
related_decisions: FEAT-025, FEAT-037, FEAT-039
related_issues: none
updated_at: 2026-06-24T00:00:00Z
updated_by: MANAGER-001
---

# FEAT-061 AgentCiCi Public Website Restructure

## Goal

重构 `agentcici.com` 官网，删除旧的 `SalesMost AI Suite`、AutoReachAI / FollowUpAI 主叙事和旧官网内容，把公开站点统一成 `AgentCiCi 企业级智能体平台`。

新官网必须提供中英双语版本，并以以下一级板块组织：

- `Solutions`
- `SkillsHub`
- `Pricing`
- `Docs`
- `Community`

`Solutions` 是主页，重点介绍系统内置功能和企业智能体落地场景：AutoService、AI 听记、客户洞察等。保留全站预约演示入口和共用预约表单。

## Reference Boundary

用户指定参考 `https://www.getswan.com/` 的官网设计理念和方法。可借鉴的方法：

- 以用例和角色任务组织信息，而不是只堆功能列表。
- 把智能体表达成可理解的工作单元。
- 用 Before / After 或流程差异解释价值。
- 展示集成生态、执行链路和轻量 CTA。2026-06-24 用户反馈要求移除首页 hero 文案下方的 `预约演示 / SkillsHub / 登录` 三按钮组，避免首屏 CTA 堆叠；站点头部、价格页和底部预约表单仍保留预约入口。

不得照抄 Swan 的代码、文案、角色命名、视觉资产、图形素材、客户内容或页面结构细节。

用户后续指定参考 `SqlCoop/VoltAgent-awesome-disign-md` 的 DESIGN.md 方法论。可借鉴的是设计系统写法和设计纪律：先定义视觉气质、颜色角色、字体层级、组件状态、布局节奏、深度规则和反模式；实现中不复制外部项目名称、代码、文案、配色身份或品牌资产。

用户后续指定参考 `https://www.codebuddy.cn/pricing/` 的价格页表达方式。可借鉴的是清晰的套餐卡、价格、权益和补充说明组织方法；不得复制 CodeBuddy 的代码、文案、产品权益、价格体系、视觉资产或实现命名。

## Information Architecture

### Chinese Routes

- `/` -> `Solutions`
- `/solutions` -> `Solutions`
- `/skill-hub` -> `SkillsHub`
- `/pricing` -> `Pricing`
- `/docs` -> `Docs`
- `/community` -> `Community`

### English Routes

- `/global` -> `Solutions`
- `/global/solutions` -> `Solutions`
- `/global/skill-hub` -> `SkillsHub`
- `/global/pricing` -> `Pricing`
- `/global/docs` -> `Docs`
- `/global/community` -> `Community`

旧的 `/suite/*`、`/pricing/global`、`/autoservice/*` 官网入口不再作为主要内容入口；可重定向到新结构，避免公开站点残留旧叙事。

## Content Requirements

### Solutions

页面目标：讲清 AgentCiCi 是企业智能体平台，能把业务系统、知识库、渠道和智能体运行治理连接起来。

必须包含：

- Hero：AgentCiCi 企业级智能体平台，强调运行、治理、业务系统集成和可追溯执行。
- 内置 Solutions：
  - AutoService：售后智能体，连接知识库、企业微信微信客服、订单/物流/工单/保修等业务系统，支持人工接管摘要。
  - AI 听记：会议和音频转写，支持实时/上传音频、摘要、待办、章节和企业知识沉淀。
  - 客户洞察：整合 CRM、沟通、工单、订单和运行轨迹，生成客户画像、风险、机会和下一步建议。
  - 可扩展补充：OpenAPI 智能体、知识库问答、工单/CRM 协同。
- Enterprise runtime：权限、审计、评测、发布、Open API、成本/工作量计量。
- Before / After：从人工检索、手工整理、分散系统到智能体可执行闭环。
- 保留全站共用预约演示表单。表单必须真实提交到 `/api/autoservice/demo-requests`，生成运营后台 `/platform/website-leads` 可查询的预约记录。

### SkillsHub

页面目标：呈现技能交换市场和企业内技能复用能力，对应产品内技能导入、导出、版本、审核和共享。

必须包含：

- 技能包导入/导出。
- 企业内部技能库。
- 审核、版本、权限、回滚。
- Skill 与 Agent、Workflow、Tools、MCP 的关系。
- 未来公共市场与企业私有市场的双形态。

### Pricing

页面目标：作为可正式上线的销售报价页，先只展示在线订阅版本，不强调部署术语，不展示私有化版本线。

必须包含：

- 四个公开套餐：标准版、专业版、企业版、Custom 定制版。Custom 定制版参考 Swan Pricing 的 Scale/Custom 组织方式，只借鉴“标准套餐 + 定制档”的信息结构，不复制其价格、权益或文案。
- 每个套餐必须展示月费或定制报价、适用阶段、初始化 Credits 或合同额度、知识库容量、构建席位、团队成员上限、并发运行数和明确功能列表。
- 标准版建议价格：`¥1,999 / 月`，包含 `8,000 Credits / 月`、`5 GB 知识库容量`、`1 个构建席位`、`最多 20 个团队成员`、`2 路并发智能体运行`，覆盖首个 AutoService 或 AI 听记试点，并展示 `3 个生产智能体`。
- 专业版建议价格：`¥6,999 / 月`，包含 `35,000 Credits / 月`、`30 GB 知识库容量`、`2 个构建席位`、`最多 100 个团队成员`、`10 路并发智能体运行`，覆盖 AutoService、AI 听记、客户洞察、Open API、SkillsHub 审核、运行记录和成本归因，并展示 `10 个生产智能体`。
- 企业版建议价格：`¥18,800 / 月起`，包含 `100,000 Credits / 月起`、`100 GB 起知识库容量`、`5 个构建席位`、`最多 500 个团队成员起`、`50 路并发智能体运行起`，覆盖大型公司、多智能体、多组织治理、SSO、高级审计、发布评测、预算预警、专属客户成功和 SLA，并展示 `50 个生产智能体`。
- Custom 定制版：价格展示 `Custom`，CTA 为 `联系销售`，适合超大规模、本地化部署、专属网络、数据驻留、专属模型/向量库/连接器、集团级权限审计、年度成功复盘和企业级 SLA 等场景。
- Credits 说明：Credits 按自然月发放，当月有效；超额 Credits 包从 `¥999 / 10,000 Credits` 起，年度预购底价不低于 `¥799 / 10,000 Credits`；对话、检索、普通文档处理、OCR/扫描件、转写、摘要、工具调用和洞察任务都统一消耗 Credits。
- 知识库容量说明：知识库容量包含原文存储、向量索引、元数据、日志和备份保留；超出套餐后容量包从 `¥299 / 100 GB / 月` 起。容量是长期资源，不在用户无操作时扣 Credits。
- 文档处理说明：官网不展示独立文档处理额度或文档处理包；普通文本、OCR/扫描件、高级检索和 rerank 统一折算为 Credits，并在账单明细中解释。
- 计费逻辑：Credits 负责智能体运行时真实消耗和文档处理工作量，知识库容量负责长期存储和索引资源，并发运行数负责资源峰值，构建席位负责配置、发布和治理权限。普通团队成员不作为核心收费项，只作为权限、审计和组织管理容量。
- 补充包：Credits 包、知识库容量包、并发与构建扩展、上线服务。

### Docs

页面目标：作为用户指南入口，不需要完整文档系统。

必须包含：

- 快速开始。
- 管理员配置。
- Solutions 使用指南。
- SkillsHub 导入导出。
- Open API 和集成。
- 私有化部署与治理。

### Community

页面目标：未来智能体交流社区预告。

必须包含：

- 智能体模板与实践案例。
- 技能贡献与交换。
- 行业场景讨论。
- 开发者和管理员交流。
- 当前状态可标注为 Coming soon / 即将开放。

## Design Direction

注册类型：公开品牌官网，属于 brand register。它可以比认证后产品界面更有品牌表达，但仍要延续 AgentCiCi 的可信企业调性。

设计原则：

- 主视觉必须第一屏明确出现 `AgentCiCi` 和 `企业级智能体平台`。
- 不做旧式营销套件或多品牌矩阵，不把 AutoReachAI / FollowUpAI 当主线。
- 不使用紫色渐变、玻璃拟态、大面积深色命令中心或泛 AI 模板卡片。
- 官网公开页不强制遵循认证后产品的鎏金色。视觉可参考成熟国际科技品牌官网的配色方法，但不得照抄品牌资产、文案或实现。
- 最新官网色系采用冷白、蓝灰、钴蓝和极淡雾紫：单主色、同类色层次、低饱和背景和高识别 CTA。避免金、绿、橙、青等多色并列造成不协调。
- 信息表达强调“智能体从业务输入到可审计输出”的运行链路。
- 页面视觉应从泛营销海报收敛为精致的企业产品官网：克制标题字号、丰富但有控制的品牌配色、真实运行/审计信息界面、8px 以内卡片半径、稳定留白和成熟成品质感。
- 背景不得使用网格纹理；减少可见框线，优先使用柔和色带、填充色块、阴影、深浅层次和产品化视觉建立页面层级。
- 深色区域不得使用突兀纯黑、近似纯黑或高对比大色块；需要突出产品信息时，优先使用冷白、蓝灰、浅钴蓝和雾紫等同类色中低对比彩色面和柔和阴影。
- 首页中文文案避免 AI 生成感的英文 eyebrow 和难懂技术字段。公开页首屏和中文主要内容不展示 `AGENTCICI PLATFORM`、`SOLUTIONS`、`RUNTIME`、命令路径、Policy、Trace、Output 等内部标签；优先使用客户请求、业务知识、智能处理、人工确认、服务回写等用户可理解的表达。
- 页面默认只做桌面端质量验收，不新增移动端专属适配范围。

## Acceptance Criteria

- 中英双语路由全部可访问。
- 旧官网主叙事被新 AgentCiCi 平台叙事替换。
- `Solutions` 作为主页并包含 AutoService、AI 听记、客户洞察。
- `SkillsHub`、`Pricing`、`Docs`、`Community` 均有独立页面与导航入口。
- 首页 hero 文案下方不再展示 `预约演示 / SkillsHub / 登录` 三按钮组。
- 预约演示入口保留在站点头部、价格页和全站共用 demo 区域，中文和英文都可见。
- 预约演示表单提交后必须真实写入 `autoservice_demo_request`，并可在运营后台 `网站注册与预约演示` 列表中查询。
- 前端构建通过。
- 使用真实浏览器完成桌面端截图检查，无明显文字重叠、导航错位或控制台错误。

## Handoff Notes

- 本规格由用户在 2026-05-30 明确确认 shape 后创建。
- 本次只重构公开官网，不修改认证后产品页 `/admin/*`、`/platform/*`、助手工作台或后端业务逻辑。
- 2026-06-24 用户补充反馈：移除首页 hero 下方截图标注的三按钮组，并检查所有公开网站页面的预约演示能力，确保数据真实进入运营后台预约记录。
