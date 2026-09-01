---
kind: feature-spec
feature_id: FEAT-208
title: 门户统一身份完整注销
status: implemented
owner_role: fullstack-agent
task_ids: TASK-350
related_decisions: INT-031
related_issues: none
updated_at: 2026-09-01T12:22:39Z
updated_by: codex
---

# FEAT-208 - 门户统一身份完整注销

## 背景与目标

用户在门户首页点击左下角“退出登录”后，页面短暂回到登录态，随后又自动进入系统。当前前端只清理 AgentCiCi 浏览器 Token，没有结束 Keycloak SSO 会话；无本地 Token 的页面又会自动发起 OIDC 登录，因此 Keycloak 静默签发新会话。

本功能要求一次退出同时结束 AgentCiCi 当前浏览器状态和 Keycloak 统一登录会话，退出后落到统一登录页，不再自动回到工作台。

## 范围

### In Scope

- 门户左下角退出入口先清理本地业务 Token、跨标签同步状态和页面内敏感运行态。
- 新增同源 `/auth/oidc/logout` 入口，由后端生成 Keycloak RP-Initiated Logout 跳转。
- OIDC 回调后仅在服务端保存加密的 ID Token/Refresh Token，并通过 HttpOnly、Secure、SameSite=Lax Cookie 关联当前登录会话；Token 不写入前端存储。
- 退出时一次性消费服务端会话，以 `id_token_hint`、`client_id` 和受控 `post_logout_redirect_uri` 结束 Keycloak 浏览器 SSO。
- OIDC 未启用时保持原本地退出能力，直接返回 `/app`。

### Out Of Scope

- 注销同一账号在其他设备上的全部会话。
- 修改 UAT、生产 Keycloak Client 或发布线上版本。
- 为历史登录会话补造 ID Token；缺少新会话关联时使用 Keycloak 当前浏览器会话与 `client_id` 发起标准注销。

## 现状与约束

- `/app` 的访客态会自动访问 `/auth/oidc/login`，该行为用于免点击登录，不能整体删除。
- 业务 Token 是无服务端 Session 的短期 OACT；当前标签页退出仍以清理本地 Token 为第一步。
- ID Token 和 Refresh Token 都不得进入 JavaScript、LocalStorage、URL 参数日志或任务文档；只有 RP-Initiated Logout 标准要求的 `id_token_hint` 由后端 302 直接交给 Keycloak。
- `post_logout_redirect_uri` 固定从受校验的 OIDC callback Origin 推导为 `/app`；不得在业务源码中固化环境域名。
- 各环境 Keycloak `agentcici-bff` 必须把对应 `/app` 精确登记为 Valid Post Logout Redirect URI；该部署事实由环境配置所有者维护。

## 方案设计

1. OIDC callback 完成 code exchange 后，后端加密保存 ID Token 和 Refresh Token，生成随机登录会话 ID。
2. callback 响应继续使用一次性 `oidc_ticket` 完成前端业务 Token 兑换，同时写入只覆盖 `/auth/oidc` 的 HttpOnly 会话 Cookie。
3. 用户点击退出时，前端先阻止本轮自动登录、清理业务 Token和页面状态，再硬跳转同源 logout 入口。
4. 后端校验规范 Host，一次性消费登录会话，构造 Keycloak `end_session_endpoint` 兼容路径并清除会话 Cookie。
5. Keycloak 结束 SSO 后跳回 `/app`；现有自动 OIDC 登录再次运行时，因为 SSO 已失效，停留在统一登录界面等待用户主动认证。

## 接口与数据影响

- 新增公开浏览器跳转接口：`GET /auth/oidc/logout`。
- OIDC callback 新增 `CICI_OIDC_SESSION` Cookie；只存随机 ID，不存任何身份 Token。
- Redis/内存 OIDC Session 结构由单一加密 Refresh Token 扩展为加密 ID Token + Refresh Token；记录仍按 Refresh Token TTL 自动过期并在注销时一次性删除。
- 不新增数据库表或迁移；既有登录、CloudCC SSO 和公司切换接口保持兼容。

## 任务拆分

- `TASK-350`：实现前后端注销链、聚焦与全量测试、本地 main 归并和 `cici.localhost` 真实浏览器验证。

## 验收标准

- 聚焦测试证明 logout URI 包含 `id_token_hint`、`client_id` 和固定同源 `/app` 回跳，且服务端登录会话只能消费一次。
- callback Cookie 必须为 HttpOnly、Secure、SameSite=Lax，路径限制为 `/auth/oidc`；退出响应将其 Max-Age 置零。
- 前端退出只访问同源相对路径，不维护 Keycloak 或环境地址。
- 本地真实浏览器：登录后点击左下角退出，Keycloak SSO 会话结束，最终显示统一登录页；刷新或等待均不会静默进入工作台。
- 后端聚焦测试与 package、前端聚焦/全量测试与 production build、域名门禁和 `git diff --check` 通过。
- 变更提交进入本地 `main`，backend/frontend 从同一明确提交构建；`https://cici.localhost/`、容器健康、重启次数、版本和制品 commit 回读一致。

## 风险与回滚

- 若环境未登记 Valid Post Logout Redirect URI，Keycloak 会拒绝回跳；发布前必须只读回读客户端配置，缺失时由环境所有者按受管流程补齐。
- 回滚应用代码即可恢复旧行为；新增 Cookie 和 Redis Session 都是短时数据，可自然过期，不需要数据回滚。

## 实现进展

- 当前状态：代码、本地主线归并、Keycloak 本地配置和运行部署已完成；真实 HUMAN 浏览器退出待确认。
- 已完成：根因定位、前后端标准注销链、后端聚焦 12 项、前端全量 62 文件/339 项、backend package 和 frontend production build。
- 未完成：真实登录态点击退出并确认停留统一登录页。默认后端全量测试因既有测试配置连接不到 `localhost:5432` 未通过，不将其记为成功。

## 交接说明

- 先读本规格、`docs/specs/FEAT-148-app-auto-oidc-redirect.md` 和 `KeycloakOidcLoginService`。
- 不得通过关闭自动 OIDC 登录掩盖统一身份会话未注销的问题。
- UAT/生产配置与发布不在本任务授权范围。
