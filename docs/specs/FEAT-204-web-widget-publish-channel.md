---
kind: feature-spec
feature_id: FEAT-204
title: Web 浮窗发布渠道与官网售前智能体
status: implementation
owner_role: fullstack-agent
task_ids: TASK-334
related_decisions: FEAT-202
related_issues: none
updated_at: 2026-08-28T06:51:53Z
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
- 产品 Nginx 将 `/public/**` 明确代理到 backend，避免 GET 被 SPA fallback 吞掉、POST 返回静态站点 405。
- 公开端点只解析启用的 `web` 渠道配置，要求 Agent 启用且存在已发布版本。
- 校验请求来源、运行成员 ACTIVE、该成员对目标 Agent 具备 RUN 权限；使用 Redis 进行按 widget/IP 的分钟限流，Redis 不可用时失败关闭。
- 短时 Token 仅含 `chat:read/chat:write`，来源为 `website`，访客 ID 使用浏览器生成并受长度/格式约束；不授予附件、语音或外部写权限。
- 官网通过后端运行参数 `app.website-widget.default-key` 解析默认浮窗；未配置或公开配置不可用时静默不挂载。入口键是公开定位符，不包含租户、成员或长期凭证。
- 本地 demo 租户配置、编译、评测/发布门禁、发布和真实一轮对话回读。

### Out Of Scope

- 不把 API Key、JWT、租户 ID、成员 ID或环境域名写入官网源码或前端制品模板。
- 不允许访客自选公司、Agent、运行成员、权限或 Token TTL。
- 不为公开访客开放附件、语音、高风险写工具或 AgentCiCi 登录态能力。
- 不修改 Semattice、DevAutopilot 产品仓；不发布 UAT 或生产。
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

### 官网

- 官网是品牌表面，但浮窗保持克制企业语气：默认折叠，启动器显示“咨询售前”，展开后复用 `FEAT-202` 对话界面。
- 后端默认入口键未配置或公开配置不可用时不渲染启动器，不阻断官网首屏、表单或导航。
- Token 过期时 SDK 重新调用同源 Token Provider；访客 ID 只保存在浏览器本地并使用随机 UUID。

## 服务端契约与安全

### 公开配置

`GET /public/web-widgets/{widgetKey}` 返回公开展示字段、SDK/Embed 相对路径和 Token TTL。它不返回公司、运行成员、内部配置 JSON、权限明细或发布记录 ID。

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

## 实现进展

- 2026-08-28：完成现状扫描和安全方案；进入实现。

## 交接说明

- 先读 `FEAT-202` 的 Token/会话边界，再读本规格。
- 本地 demo 数据发布属于验证步骤，不得固化为 Flyway tenant seed 或业务源码常量。
