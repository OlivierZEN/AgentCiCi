---
kind: feature-spec
feature_id: FEAT-183
title: 内部生态系统 API 目录
status: in_implementation
primary_project: agentcici
task_ids: TASK-302
related_integrations: INT-019
updated_at: 2026-08-14T04:59:00Z
updated_by: codex
---

# FEAT-183 - 内部生态系统 API 目录

## 产品目标

运营平台向内部生态应用开发者提供“可被依赖的系统契约”目录，而不是把所有 Controller 自动生成文档。首版只收录账号可访问公司查询与公司上下文切换、身份与 OACT、租户应用激活与交接、Semattice 能力发现、主体同步、元数据读取和记录运行时等跨应用高频核心 API。

## 信息架构与交互

- 能力治理新增一级菜单“系统 API”，下设 `AgentCiCi` 与 `Semattice` 两个提供方入口。
- 首页说明治理边界并展示提供方摘要；提供方页面使用可搜索、可筛选的紧凑列表。
- 首页提供独立“接入应用”入口；受信应用使用单独列表扫描 Client、Scope 和状态，新增/编辑使用显式弹窗，停用是独立治理动作。
- 点击记录打开宽抽屉，展示用途、调用约束、鉴权、输入输出和常见错误速览。
- 完整调用说明使用独立路由，承载 Schema、请求/响应示例、兼容与回滚说明。
- 不提供在线执行按钮，避免把目录读取权限误解为业务调用授权。

## 事实源与数据契约

- AgentCiCi 条目由 AgentCiCi 后端维护并投影。
- Semattice 条目由 Semattice Capability Registry 与提供方元数据生成，通过内部 HMAC 目录端点交付；AgentCiCi 不复制其 Schema。
- 聚合响应为 `v1`，提供方包含状态和错误说明；单个提供方不可用时其余目录仍可读取。
- 文档示例只使用环境变量和占位凭据，禁止固化环境域名、Secret 或真实租户标识。

## HUMAN API 鉴权说明

- 内部独立应用以 Keycloak `access_token` 为统一 HUMAN 调用凭证，不再为每个应用建设专用 handoff，也不要求先交换 AgentCiCi 长期令牌；`id_token` 只服务于客户端登录展示，不能调用 API。
- 平台管理员先登记受信应用的 `app_code`、Keycloak `client_id` 和允许 Scope。调用时应用只发送 `Authorization: Bearer <KEYCLOAK_ACCESS_TOKEN>`；AgentCiCi 根据令牌 `azp` 自动识别应用，不接受客户端自报 `app_code`。
- AgentCiCi 必须验证 Keycloak 签名、Issuer、有效期、`azp` 和 `aud=agentcici-api`，按 `(issuer, sub)` 映射既有 HUMAN 账号，并在每次调用时重新校验受信应用状态及 ACTIVE 公司成员关系。
- 无公司上下文的 `GET /openapi/v1/ecosystem/companies` 返回当前 Keycloak 用户可访问的 ACTIVE 公司；`POST /openapi/v1/ecosystem/company-context` 校验指定公司并返回成员、角色和后续请求所需的 `X-Company-Id`，不创建服务端“当前公司”全局状态。
- 后续生态 HUMAN API 继续携带同一 Keycloak `access_token`，并在需要公司上下文时发送 `X-Company-Id`。服务端必须逐请求校验公司成员关系，禁止只相信 Header。
- AgentCiCi 自身前端继续使用现有 OIDC BFF 与 Ecosystem HUMAN Token，不受本契约影响；已有 DevAutopilot 单次 handoff 继续用于从 AgentCiCi 主动启动目标应用，但不作为独立应用自主登录的通用前置。
- 机器或服务应用继续使用 SERVICE Principal、SERVICE Token 交换和 OACT，不能借用 HUMAN 端点。
- Keycloak Client 的注册是一次性平台治理操作；应用运行时只处理标准 Keycloak 登录、Bearer Token 和可选 `X-Company-Id`，不接触 AgentCiCi 密钥、OACT 或 HMAC。

## 首批范围

- AgentCiCi：当前账号可访问公司查询、公司上下文切换、SERVICE Token 交换、DevAutopilot activation 查询、handoff 兑换、Semattice 开通预留、Semattice 控制台 handoff 兑换、OACT JWKS。
- Semattice：能力发现、租户状态、主体同步/目录、当前元数据版本、对象列表、记录查询/读取/创建/更新和授权解释。

## 验收标准

1. 平台角色可读取两个提供方目录；未授权用户维持既有拒绝语义。
2. 提供方、分类、协议、scope、风险、版本与状态可在列表快速扫描。
3. URL 驱动列表、抽屉和独立文档页，可刷新、返回和深链。
4. Semattice 暂时不可用时 AgentCiCi 目录仍显示，并明确标记远端不可用。
5. 页面明确说明“出现在目录中不代表自动获得调用权限”。
6. 本地开发环境从 AgentCiCi 与 Semattice 各自本地 `main` 构建并通过跨项目契约与全栈验证。
7. 公司查询不接受客户端指定账号；公司上下文选择必须根据 Keycloak `sub` 对应账号重新校验 ACTIVE 成员关系，不产生可绕过 Keycloak 的第二套长期令牌。
8. 只有平台登记为 ACTIVE、Scope 匹配且 Keycloak `azp` 精确命中的内部应用可使用 HUMAN 生态端点；未知、停用、Audience 不匹配或未绑定 HUMAN 账号的 Token 一律失败关闭。
9. 公司 API 文档分别给出独立应用直调、AgentCiCi 自身前端和机器应用流程，明确 `access_token` 可调用、`id_token` 不可调用、公司级请求必须携带 `X-Company-Id`。
10. 受信应用登记、停用与 Scope 变更必须由平台角色执行并记录审计；不得在业务源码中硬编码环境域名、Client Secret 或允许 Client 列表。
11. 受信应用表单必须对无效应用代码给出具体、可操作的内联原因；空格输入需明确建议改用连字符，不能只通过禁用保存按钮表达失败。

## 回滚

- 回滚 AgentCiCi 任务提交可移除聚合 API、页面和菜单，不影响目录中任何既有业务 API。
- 回滚 Semattice 任务提交只移除只读目录投影，不改变 Capability Registry 或 invoke 路径。

## 实现与验收状态

- Keycloak HUMAN 直调、受信 Client 治理、公司目录/上下文 API、系统 API 调用文档和运营端管理 UI 已完成，任务进入 review。
- 功能提交 `e90a2d2b` 与 HTTP 方法错误修复 `9f58d972` 已进入 AgentCiCi 本地 `main`；统一开发环境运行 `2.8.61-dev.9f58d97`，V115、健康、版本、匿名 401、错误方法 405、页面路由和完整 `./stack verify` 均已回读通过。
- Keycloak 直调增量已发布 UAT `2.8.61-beta.20 / 1b6bb8f1974a`；V115、运行版本、制品 digest、健康、状态服务不重启、页面/匿名鉴权边界和稳定窗口均通过，生产未修改。进入 done 前仍需使用一个真实新登记 Keycloak Client 完成登录、公司列表、公司上下文及后续 `X-Company-Id` 业务调用验收。
- 受信应用应用代码的实时错误反馈已由本地 `main@2daa18ef` 实现并部署到 `cici.localhost`；空格、长度、首字符和字符集均有具体提示，前端全量测试、production build、运行制品回读和完整本地栈验证通过。UAT/生产未修改。
- 受信应用目录与登记/编辑 modal 的桌面可读性已由 `8522fefb` 修整：表格不再继承通用目录的最小列宽，最右操作列完整显示；弹窗使用统一字段高度、稳定 Scope 选项和更清晰的 14–16px 表单字号，保存、启停、权限与审计逻辑不变。前端全量 49 文件/275 项、production build 和完整本地栈验证通过，`cici.localhost` 前端为 `2.8.61-dev.8522fef`。
- `2.8.61-beta.22` 的 UAT 候选构建因 ACR OAuth 连接重置而未完成，Git tag 与完整镜像均不存在；UAT 继续运行 beta.21，未发生部署写入。待 registry 鉴权网络恢复后重新从远程 `main@8522fef` 冻结候选。
