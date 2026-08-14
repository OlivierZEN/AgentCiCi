---
kind: feature-spec
feature_id: FEAT-176
title: 平台联网搜索与网页抓取集成
status: verified
owner_role: fullstack-agent
task_ids: TASK-292, TASK-305
related_decisions: none
related_issues: none
updated_at: 2026-08-14T02:20:00Z
updated_by: codex
---

# FEAT-176 - 平台联网搜索与网页抓取集成

## 背景与目标

- 百炼 Responses API 提供 `web_search` 联网搜索，并通过同时声明 `web_search` 与 `web_extractor` 完成指定网页内容抓取。
- 平台运营人员需要分别配置、检测和启停这两类能力；Agent/Skill 作者需要从既有工具目录发现、授权和治理对应内置工具。
- 原有 Tavily 搜索/正文抽取继续保留，既有配置、工具名和运行逻辑不改变。

## 范围

### In Scope

- 在运营端“平台集成配置”新增“联网搜索（百炼）”和“网页抓取（百炼）”两张独立卡片。
- 两项配置分别保存 API Key、业务空间 API Host、模型、超时和输入上限，API Key 沿用 `SecretCipherService` 加密与固定掩码。
- 代码解释器、联网搜索和网页抓取的同步请求超时保持默认 120 秒、最小 10 秒，可配置上限统一提升到 60 分钟；保存校验与运行时 HTTP 请求使用同一上限。
- 新增 `managed_web_search` 与 `managed_web_extract` 两个内置工具，接入工具目录、平台治理、Agent/Skill 授权和运行时安全网关。
- 联网搜索请求只声明 `web_search`；网页抓取请求固定同时声明 `web_search` 与 `web_extractor`。
- 返回最终答案、搜索/抓取调用次数、Token 用量、模型和延迟，不返回模型思考过程。

### Out Of Scope

- 不替换或合并 Tavily 集成及 `tavily_search` / `tavily_extract`。
- 不承诺通过 OpenAI 兼容 Responses API 返回可验证的搜索来源列表；协议未返回来源时不得生成或伪造引用。
- 不支持浏览器 Cookie、登录态网页、文件上传、长会话复用或 AgentCiCi 宿主机本地抓取。
- 不修改模型配置、场景模型路由或平台集成之外的页面结构。

## 官方协议约束

- 联网搜索文档：<https://help.aliyun.com/zh/model-studio/web-search>。Responses API 通过 `tools: [{"type":"web_search"}]` 启用；OpenAI 兼容 Responses API 当前不提供搜索来源返回与引用标注。
- 网页抓取文档：<https://help.aliyun.com/zh/model-studio/web-extractor>。Responses API 必须同时声明 `web_search` 和 `web_extractor`，用量位于 `usage.x_tools`。
- 服务地址因地域和业务空间不同，只接受运营配置的 HTTPS `*.maas.aliyuncs.com` API Host；不在业务源码中固定真实环境地址。

## 用户场景

- 平台运营人员分别配置两个能力，使用当前草稿或已保存密钥进行最小连接检测，通过后再启用。
- 智能体通过 `managed_web_search` 查询时效信息；当协议未返回来源时，结果显式标记不可提供来源列表。
- 智能体通过 `managed_web_extract` 抓取公开网页，并给出抓取任务的最终结果；私网、回环、`.local` 和带用户信息的目标 URL 被拒绝。
- 配置缺失、功能停用、模型不兼容、上游超时、响应过大或无最终答案时失败关闭。

## 方案设计

1. `IntegrationAppService` 注册两个平台托管集成，默认关闭并独立加密密钥。
2. 受管 Web Responses 客户端根据模式构建精确工具声明，统一限制超时、响应体和错误投影。
3. 受管 Web 工具服务负责工具 Schema、配置解析、API Host 门禁、抓取目标 URL 门禁、检测和稳定错误码。
4. `ToolOrchestratorService` 把两个能力作为普通内置工具注册和分派，继续服从平台工具治理。
5. 运营 UI 复用已有卡片和配置弹窗，不改变平台集成页的交互结构。

## 接口与数据影响

- `GET /api/platform/integrations` 增加 `managed_web_search`、`managed_web_extractor`。
- `PUT /api/platform/integrations/{appCode}` 保存对应配置。
- `POST /api/platform/integrations/managed-web-search/test` 与 `/managed-web-extractor/test` 检测连接。
- 内置工具名：`managed_web_search`、`managed_web_extract`。
- 不新增数据库表或迁移，继续使用 `integration_app.config_json`。

## 验收标准

- 平台集成列表稳定显示 Tavily、讯飞、代码解释器、联网搜索和网页抓取，新增两项默认关闭。
- API Key 不出现在响应、日志、测试输出或 Git；掩码回写不覆盖既有密文。
- 搜索请求只含 `web_search`；抓取请求同时含 `web_search` 和 `web_extractor`。
- 两个内置工具出现在目录中，停用平台集成或平台工具后不可执行。
- 搜索结果不伪造协议未提供的来源；成功结果不包含 reasoning。
- 后端定向测试、前端完整测试与生产构建通过。
- 运营页面明确显示最长 60 分钟，并以数字输入约束阻止超过 `3,600,000 ms` 的配置；后端仍作为最终可信门禁。
- 提交合并本地 `main` 后，仅从该提交重建本地 backend/frontend，并回读 `https://cici.localhost/` 路由、健康、重启次数和版本指纹。

## 风险与回滚

- 风险：联网能力产生额外调用费用；结果返回搜索/抓取调用次数和 Token 用量，平台可独立停用。
- 风险：可配置 API Host 形成 SSRF；仅接受百炼业务空间 HTTPS Host。
- 风险：抓取目标可能指向敏感网络；拒绝明显的本地、私网、链路本地、组播及带用户信息 URL。
- 风险：上游内容不可信；工具结果作为外部数据返回，不能提升为系统指令。
- 回滚：关闭相应集成或工具治理即可停止注入；代码回滚不涉及迁移，Tavily 不受影响。

## 实现与交接

- 两张配置卡、密钥加密、连接检测、Responses 客户端、两个内置工具、目录治理和运行时分派已实现；原有 Tavily 代码路径未改变。
- 后端定向 16 项、package、前端完整 46 文件/249 项和生产构建通过；共享测试库既有 V81 checksum 漂移继续阻断 Spring 集成用例，未 repair。
- 功能提交 `9a8cb9a` 已合并本地 `main@1f362c7`，本地 backend/frontend 从该提交构建和运行，完整 stack verify 与版本指纹回读通过。
- 集成默认关闭；平台管理员需分别录入百炼业务空间 API Key/API Host，执行“测试搜索”“测试抓取”后按需启用，再用 Agent 会话完成真实调用验收。
- TASK-305 将代码解释器、联网搜索和网页抓取的可配置超时上限统一提升为 60 分钟；默认 120 秒与最小 10 秒不变。前后端边界测试、构建、本地 main 制品和完整本地栈验证通过。
- TASK-305 已随远程 `main@626f7e22c774` 发布 UAT `2.8.61-beta.21`；部署制品回读 60 分钟提示与边界值，健康、迁移、匿名鉴权、公网 smoke 和稳定窗口通过。真实 60 分钟厂商任务仍需受权配置后的业务验收。
