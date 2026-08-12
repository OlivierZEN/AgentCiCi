# FEAT-173 Semattice Console Handoff Provider

## 目标

AgentCiCi 为已登录、具备组织管理权限的成员提供环境无关的 Semattice 管理端入口，同时避免把 OACT 暴露给浏览器。

## 契约 v1

- 公开入口：`POST /auth/semattice/console`，同源认证，返回 `redirectUri`；URI 只包含不透明 ticket。
- 内部兑换：`POST /internal/semattice/console-handoffs/redeem`，沿用 `X-Internal-*` HMAC 认证。
- ticket：256 bit 随机、Base64URL、60 秒有效、单次消费；数据库仅保存 SHA-256 digest、company/member 绑定及生命周期时间。
- 兑换响应：只通过服务间 TLS/HMAC 返回短时 OACT；浏览器和日志不可见。
- 错误：无效、过期和已消费统一失败；避免提供可枚举差异。

## 配置

`app.semattice.console-base-url` 必须为 HTTPS absolute origin，不允许 userinfo、query 或 fragment；path 仅允许空或 `/`。实际值只来自部署环境。

OIDC 拆分为对外稳定的 `app.auth.oidc.issuer` 与服务间可达的 `app.auth.oidc.backchannel-base-url`。授权页跳转和 Token issuer 校验使用前者；code exchange、JWKS 与管理 API 使用后者。

## 回滚

不保留旧 `#oact` 消费路径。回滚时仅恢复上一完整应用版本和对应入口配置，不允许临时重新暴露浏览器 OACT。
