---
kind: feature-spec
feature_id: FEAT-204
title: Web 浮窗发布渠道与官网售前智能体
status: in_progress
owner_role: fullstack-agent
task_ids: TASK-334,TASK-335,TASK-337,TASK-339,TASK-343,TASK-344,TASK-347
related_decisions: FEAT-202
related_issues: ISSUE-2026-08-28-web-widget-empty-stream,ISSUE-2026-08-31-web-widget-buffered-stream
updated_at: 2026-09-01T06:16:48Z
updated_by: codex
---

# FEAT-204 - Web 浮窗发布渠道与官网售前智能体

## 背景与目标

Agent Builder 已有独立“发布渠道”页签，但 Web 浮窗仍只有占位说明，不能配置来源、访客身份、外观、安装代码或启停。`FEAT-202` 已交付受信外部系统使用的统一 SDK 和短时 Embed Token，会话、附件、语音、依据、工具过程与确认回读不应重复实现。

本功能把 Web 浮窗产品化，并在 AgentCiCi 官网以本地 demo 租户的“售前跟进 Agent”完成真实接入。公开网站不能持有 API Key 或长期 Token，必须由同源后端依据已发布渠道配置签发受限短时访客 Token。

## 用户与场景

- 组织管理员在 Agent Builder 为某个已发布 Agent 开启 Web 浮窗，配置允许来源、运行成员、启动文案和限额，复制安装代码并预览。
- 网站访客点击右下角浮窗，以匿名访客会话向售前智能体咨询产品、方案和演示安排。
- 安全或配置异常时，浮窗不暴露内部身份或密钥，公开端点返回明确失败，既有官网仍可正常浏览。

## 范围

### In Scope

- `publishConfigs.web`：稳定 `widgetKey`、允许来源、`runAsUserId`、显示名称、启动文案、Token TTL、每分钟限额和默认折叠状态。
- Agent Builder Web 浮窗配置表单、状态摘要、受控预览、安装代码和复制动作。
- `GET /public/web-widgets/{widgetKey}` 与 `POST /public/web-widgets/{widgetKey}/tokens`。
- 公开 Web 配置与 website 会话从已发布 Agent Definition 回读系统智能体头像；官网启动器、浮窗标题、欢迎态与智能体消息使用同一头像。
- 产品 Nginx 将 `/public/**` 明确代理到 backend，避免 GET 被 SPA fallback 吞掉、POST 返回静态站点 405。
- OpenAPI 与 Web Widget CORS Filter 必须分别限定 URL pattern，不能用全局 Filter 抢先拒绝另一契约的预检请求。
- 公开端点只解析启用的 `web` 渠道配置，要求 Agent 启用且存在已发布版本。
- 校验请求来源、运行成员 ACTIVE、该成员对目标 Agent 具备 RUN 权限；使用 Redis 进行按 widget/IP 的分钟限流，Redis 不可用时失败关闭。
- 短时 Token 仅含 `chat:read/chat:write`，来源为 `website`，访客 ID 使用浏览器生成并受长度/格式约束；不授予附件、语音或外部写权限。
- 官网通过后端运行参数 `app.website-widget.default-key` 解析默认浮窗；未配置或公开配置不可用时静默不挂载。入口键是公开定位符，不包含租户、成员或长期凭证。
- 本地 demo 租户配置、编译、评测/发布门禁、发布和真实一轮对话回读。
- 经用户明确授权的 UAT `2.8.68-beta.1` 发布，以及租户 `orgickjr6icm6l2zitpn` 的售前智能体、Web 渠道和站点首页默认入口配置。

### Out Of Scope

- 不把 API Key、JWT、租户 ID、成员 ID或环境域名写入官网源码或前端制品模板。
- 不允许访客自选公司、Agent、运行成员、权限或 Token TTL。
- 不为公开访客开放附件、语音、高风险写工具或 AgentCiCi 登录态能力。
- 不修改 Semattice、DevAutopilot 产品仓；不发布生产。
- 不新增移动端专属布局或移动端自动化验收。

## 设计与交互

### Agent Builder

- 保留现有“发布渠道”左右结构，Web 浮窗不再显示占位。
- 顶部显示启用状态、接入方式、公开入口键状态和线上版本状态。
- 表单只使用现有产品控件与 1px 结构线，不新增嵌套卡片或装饰性视觉。
- 运行成员使用现有组织成员 API 下拉选择，只列出 ACTIVE 成员，并提示使用最小权限专用成员。
- 允许来源一行一个 Origin；保存时由后端规范化并在公开签发时精确校验。
- 安装代码不包含租户/成员/密钥，只包含 SDK 相对入口和 `widgetKey`；复制为显式用户动作。
- 预览使用当前配置的显示名称、启动文案和默认折叠状态，不签发真实访客 Token。
- 预览头像直接来自当前 Agent Definition 草稿；实际安装代码不固化图片，而是在运行时读取公开 Web 配置中的服务端权威头像。

### 官网

- 官网是品牌表面，但浮窗保持克制企业语气：默认折叠，启动器显示“咨询售前”，展开后复用 `FEAT-202` 对话界面。
- 公开官网浮窗与启动器默认采用产品既有 `crm-blue` 标准主题；不覆盖受信 `page` 嵌入显式选择的其他主题。
- 浮窗输入工具栏只保留语音与靠右发送动作，不展示公开访客无权限使用的附件入口；`page` 模式继续保留附件能力。
- 标题栏图标按钮 hover 不显示背景框，只用图标色反馈；键盘 `focus-visible` 仍显示可访问性轮廓。
- 输入区话筒在默认、hover、listening 和 focus 状态都保持透明，不生成主题色背景块；该约束由公共 `cici-product-icon-button` 承担，并同步适用于公开页、前台、后台和平台页的裸图标按钮。
- 后端默认入口键未配置或公开配置不可用时不渲染启动器，不阻断官网首屏、表单或导航。
- 官网启动器、浮窗标题、欢迎态和智能体消息统一渲染系统内 Agent Definition 头像；为空或图片加载失败时回退为智能体名称首字，不回退为另一智能体图片。
- Website 浮窗等待首段正文时只显示无文字的三点等待动效；不得把工具选择、检索、生成、安全校验或工具执行等内部 phase/busy label 渲染为访客可见消息。受信 `page` 嵌入保留既有阶段展示。
- Token 过期时 SDK 重新调用同源 Token Provider；访客 ID 只保存在浏览器本地并使用随机 UUID。

## 服务端契约与安全

### 公开配置

`GET /public/web-widgets/{widgetKey}` 返回公开展示字段、系统智能体头像、SDK/Embed 相对路径和 Token TTL。头像是已发布 Web Agent 面向访客的公开身份素材；接口仍不返回公司、运行成员、内部配置 JSON、权限明细或发布记录 ID。

Base64 头像不得写入短时 JWT。website 会话以已验证 Token 中的 `companyId + agentId` 在服务端重新读取启用的 Agent Definition 并投影头像；客户端配置不能覆盖该字段。`source=cloudcc` 的受信 page 嵌入继续保持 FEAT-202 的固定思思身份。

### Token 签发

`POST /public/web-widgets/{widgetKey}/tokens` 请求只接受 `visitorId`、`parentOrigin`、当前页面 path 和 locale。服务端以渠道配置为唯一事实源：

1. 按稳定 `widgetKey` 定位唯一 Web 配置，重复键失败关闭。
2. Agent 必须启用、Web 渠道启用且 `publishedVersionId` 非空。
3. `parentOrigin` 必须与请求 Origin 一致并命中允许来源。
4. `runAsUserId` 必须仍是 ACTIVE 组织成员，并具备目标 Agent RUN 权限。
5. Redis 按 `widgetKey + client IP + minute` 计数；超过限额返回 429，Redis 不可用返回 503。
6. JWT 最长 15 分钟，只授予 `chat:read/chat:write`，绑定公司、Agent、运行成员、来源、访客和页面上下文。

### 会话隔离

会话键包含公司、Agent、widget、访客、来源和网站页面对象；同一访客同一页面可恢复历史，不同 widget、Agent、访客或公司互不可见。公开 Token 不能访问普通 `/agents`、`/admin`、`/platform` 或工作台 API。

## 兼容与回滚

- 现有 `sisi@1.0.0.js`、CloudCC API Key 换票和 `source=cloudcc` 保持兼容。
- SDK 增量支持可配置启动器文案和默认折叠；保留 `AgentCiCiSisi` 全局入口。
- 关闭 Web 渠道或清空后端默认入口键即可立即降级；既有官网和其他发布渠道不受影响。
- 回滚代码后 `publishConfigs.web` JSON 可保留，旧版本只会忽略该配置。

## 验收标准

1. Agent Builder Web 浮窗配置可回读、编辑、保存，切换 Agent 后不串配置。
2. 公开配置/Token 的正例和未发布、未启用、错 Origin、失效成员、无 RUN、重复 key、限流/Redis 故障负例均有后端测试。
3. Embed Token 只可访问 `/embed/v1/apps/sisi/**`，不能访问普通受保护 API。
4. 官网未配置 key 时无浮窗；配置有效 key 时默认折叠，点击展开，Token 过期可刷新。
5. 前端聚焦测试、全量测试、production build、后端聚焦测试/package、域名扫描和 diff check 通过。
6. `sales-agent` 在 `org3gxskla32gln3bvop` 启用 Web 渠道并有已发布版本；官网真实浮窗完成一轮问答并留下会话/执行记录。
7. 本地 backend/frontend 均从 AgentCiCi 本地 `main` 同一提交构建，`https://cici.localhost/` 路由、健康、restart、版本 API、镜像标签和页面制品一致。
8. 远程 main、UAT 与生产保持不变。
9. demo `sales-agent` 的系统头像必须在启动器、浮窗标题、欢迎态和智能体消息中一致显示；公开配置和会话响应均可追溯到 Agent Definition，JWT 不承载头像正文。

## 流式响应兼容要求

- Embed 页面必须以 AgentCiCi 后端规范 `delta` 负载 `{ "text": "..." }` 作为首选文本字段。
- 为兼容历史代理实现，可继续接受纯文本、`content` 和 `delta`，但不得用兼容字段取代服务端规范。
- 流结束后若真实增量存在，assistant 气泡必须展示完整正文；“本次未返回文字内容。”只用于服务端确实未产生任何文本的场景。
- 回归必须包含解析单测、本地正式制品和官网真实模型问答，不能只以 curl 收到 SSE 作为前端成功证据。

## 真实流式与时延预算

- 普通咨询没有外部事实需求或明确工具意图时必须进入 DIRECT，只允许一次最终模型调用；Agent 拥有工具不等于本轮必须先调用模型判断工具。
- 工具规划、知识检索或确认流程确有必要时可以先于最终生成执行；phase 必须准确写入 SSE/Trace 供技术观测。受信 `page` 嵌入可展示准确阶段，公开 Website 浮窗不得暴露内部编排文案，只以无文字等待动效反馈仍在处理。
- 最终模型的供应商增量必须在模型完成前进入 SSE；禁止先完整累积再用固定字数和 sleep 模拟 streaming。
- 增量输出以完整行或安全句段为最小门禁单元，先完成思维段过滤、敏感信息遮蔽和内置内容安全检查，再下发并加入权威正文。
- 租户启用任意自定义整段安全规则，或当前 Agent 具备受研发交付回执约束的写工具时，允许明确降级为 `buffered`：完成整段门禁后用单个 delta 返回，不得模拟打字。
- Trace 至少记录模型调用次数、供应商首增量耗时、首 SSE delta 耗时、输出模式和总耗时；真实浏览器回归必须证明首 delta 早于模型完成。
- 目标体验预算：普通咨询模型调用数为 1；平台编排开销不超过 2 秒；首可见正文由供应商 TTFT 主导，而不是等待完整回答。

## 实现进展

- 2026-08-28：完成现状扫描和安全方案；进入实现。
- 2026-08-28：实现、自动化、本地 main、V125、demo 发布、公开安全负例、真实模型会话和浏览器验收通过；进入用户 review。demo 现用唯一 ACTIVE OWNER，非 demo 发布前必须替换为专用 RUN-only 成员。
- 2026-08-28：用户回报官网浮窗回复为空；定位为 Embed 消费方漏读规范 `{text}`。TASK-335 已进入本地 main，同提交前后端制品与官网原问题真实回归通过，进入用户 review。
- 2026-08-28：TASK-337 已完成官网浮窗 CRM 标准蓝、发送按钮布局、公开附件入口和标题栏 hover 修正；实现进入本地 main，同提交前后端制品、自动化、视觉对照和真实非空回复通过，进入用户 review。
- 2026-08-28：TASK-339 根据后续截图定位话筒浅蓝框来自 `sisi-composer` 的主题 hover 背景，同时发现公共裸图标原语仍允许浅色背景；现改为跨页面透明背景、图标变色反馈，并增加静态契约门禁。
- 2026-08-28：TASK-343 已补齐 Web 浮窗身份素材链路；公开配置、website 会话、SDK、Embed 和 Agent Builder 预览均从服务端权威 Agent Definition 读取头像，JWT 不携带 Base64。实现 `9191e5a3eacf` 进入本地 main，自动化、同提交双制品、公开配置和官网浏览器三处头像回读通过，进入用户 review。
- 2026-08-31：TASK-344 获得用户明确 UAT 发布授权；目标租户 `orgickjr6icm6l2zitpn` 已只读确认 ACTIVE 但当前无智能体。发布按 `2.8.68-beta.1` 不可变候选、完整备份、backend/frontend 最小切换执行；租户智能体与 Web 渠道必须走官方产品链路，首页只通过受管运行配置引用公开 widget key。
- 2026-08-31：TASK-347 只读确认 UAT 普通咨询存在两次模型调用和完整缓存后的模拟分片；进入 DIRECT 单调用、流式安全门禁、首字时延和 Embed 阶段提示修复。
- 2026-08-31：TASK-347 实现 `19080005` 已进入本地 main；自动化、双制品与真实 website Trace 证明普通咨询单模型/零工具、首 delta 早于完成，浏览器显示阶段提示并在完成前连续增长。自定义整段规则与研发写回执明确使用单 delta buffered；UAT 仍为 beta.3，修复尚未发布。
- 2026-09-01：用户复核指出 Website 浮窗不应显示“正在选择所需工具”等内部阶段；TASK-347 恢复 `in_progress`，规格调整为公开访客仅见无文字等待动效，SSE/Trace 与受信 `page` 嵌入继续保留阶段信息。
- 2026-09-01：过滤实现 `629e35db` 进入本地 main，自动化/build、frontend 单服务制品、运行指纹、官网 float DOM 与桌面截图通过；进入用户 review，UAT/生产未修改。

## 交接说明

- 先读 `FEAT-202` 的 Token/会话边界，再读本规格。
- 本地 demo 数据发布属于验证步骤，不得固化为 Flyway tenant seed 或业务源码常量。
