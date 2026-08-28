---
kind: task-status
task_id: TASK-334
feature_id: FEAT-204
status: review
priority: critical
owner_role: fullstack-agent
claimed_by: codex
updated_at: 2026-08-28T07:51:00Z
updated_by: codex
---

# TASK-334 - Web 浮窗发布渠道与官网售前智能体

## 范围

- 将 Agent Builder 的 Web 浮窗从占位页升级为可保存、预览、安装和启停的发布配置。
- 新增只面向已发布 Web 渠道的短时访客 Token，校验来源、运行成员、RUN 权限和速率限制。
- 复用现有统一嵌入 SDK 与会话链路，在 AgentCiCi 官网按运行参数挂载浮窗。
- 在本地 demo 租户 `org3gxskla32gln3bvop` 配置并发布 `sales-agent / 售前跟进 Agent`。
- 从 AgentCiCi 本地 `main` 构建 backend/frontend，更新并回读 `https://cici.localhost/`。

## 完成条件

- Web 浮窗页可以配置稳定入口键、允许来源、最小运行成员、显示名称、启动文案、Token TTL 和每分钟限额。
- 未发布、未启用 Web、来源不匹配、运行成员无效或无 RUN 权限时，公开配置/Token 接口失败关闭。
- 官网没有长期 API Key、租户 ID或成员 ID，只使用构建期注入的非秘密 `widgetKey`。
- 目标 demo Agent 具备已发布版本和 Web 渠道配置，官网浮窗可建立真实短时会话并完成至少一轮模型对话。
- 聚焦后端测试、前端测试、production build、域名门禁、diff check 和本地全栈回读通过。
- 变更提交并进入 AgentCiCi 本地 `main`；远程推送、UAT 与生产保持不变。

## 当前证据

- `org3gxskla32gln3bvop` 已有 `sales-agent / 售前跟进 Agent`，当前无已发布版本，渠道为 `dingtalk,wechat`。
- `FEAT-202` 已交付 `sisi@1.0.0.js`、`/embed/sisi` 和短时 Embed Token 会话链路；外部受信宿主仍必须用服务端 API Key 换票。
- Agent Builder 后端已按 Agent/渠道保存 `publishConfigs`，Web UI 当前仍是“配置页即将开放”占位。
- 本地首次部署验收发现前端 Nginx 未代理 `/public/**`：GET 回落为 SPA HTML、POST 为 405；已纳入本任务修复并要求真实端点复验。
- 二次门禁发现既有 OpenAPI CORS Filter 全局拦截其他 OPTIONS，且 `/system/version` 回落为 SPA；已收窄 Filter URL pattern 并补版本代理。

## 下一步

- 用户目视确认 Agent Builder Web 配置页和官网浮窗；进入 UAT/生产前由管理员创建仅具 `sales-agent` RUN 权限的专用 ACTIVE 成员并替换 demo OWNER。

## 完成证据

- 本地 `main@ee4a59a62c51` 包含 Web 配置 UI、公开配置/Token、`sisi@1.1.0.js`、官网挂载、V125、Nginx `/public/**` 与版本路由、隔离 CORS Filter。
- 前端全量 58 文件/316 项、production build；后端 Web Widget/CORS/租户聚焦测试与 package；域名扫描、SDK 语法和 `git diff --check` 通过。
- `org3gxskla32gln3bvop / sales-agent` 已生成编译版本 v1、readiness `blocked=false`、发布为 `PUBLISHED` 并启用 Web 渠道；警告为无知识库和未配置评测集。
- 公开配置 200 且不返回 company/runAs；错 Origin 403；正确 Origin 200，TTL=600、权限仅 `chat:read/chat:write`；预检 200；Embed Token 访问普通 `/agents` 为 401。
- website 会话回读 `source=website / agent=sales-agent / product=售前跟进智能体`，真实模型返回售前团队能力说明，执行日志 `CHANNEL:SUCCESS`。
- backend/frontend 镜像 label 均为 `2.8.67-dev.ee4a59a / ee4a59a62c51`，两容器 healthy/restart=0；V125 success，版本 API 和前端带版本资源一致。
- 浏览器官网启动器默认 `aria-expanded=false`，点击后为 true，标题与欢迎语正确，console error/warn=0。
- PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Nginx、Semattice、DevAutopilot 未替换且 restart=0；标准 `./stack verify` 被本任务外的 Semattice 版本漂移失败关闭，未越权修正。
