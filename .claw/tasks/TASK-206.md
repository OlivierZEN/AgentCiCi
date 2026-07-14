---
kind: task-status
task_id: TASK-206
status: in_progress
updated_at: 2026-07-14T12:15:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-206.yaml
spec_path: docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md
---

# TASK-206 - CloudCC 嵌入身份同步自动恢复

## Scope

- 修复 pagecomponent 首次身份交换失败后永久锁死的问题。
- 让 Vue 挂载与 UMD fallback 使用相同的有限退避重试和失败释放策略。
- 增加聚焦测试、技能打包/发布验证和真实 CloudCC CRM 嵌入验收。

## Verified Root Cause

- 线上 pagecomponent/customPage 绑定正确，相同测试用户重新登录后的 SSO 全链路为 HTTP 200。
- 当前 `bootstrapSso` / `bootstrapFallbackSso` 在任何失败后都保留 started 标志，且没有重试。
- 截图发生时的一次失败因此被放大为当前页面永久失败，需要重开 CRM 页面才能恢复。
- 后续普通销售用户复测仍被拒绝；Nginx 响应长度与后端安全文案精确对应为 `CloudCC runtime token 校验失败`，并非页面所显示的“账号未映射”。
- CloudCC CRM 前端源码确认 `$CCDK.CCToken.getToken()` 才是同步读取当前用户会话的方法；`getOpenApiToken(clientId, secretKey, ...)` 是需要应用凭据的异步换票方法。pagecomponent 将后者误当成回调接口调用，导致发送给 AgentCiCi 的并非可验证的当前 CRM 会话，后端因此返回 `ccapi-10003` 并被前端误显示为账号未映射。

## Delivery Result

- Vue pagecomponent 与 UMD fallback 均已实现 `0/800/2000/4000ms` 有限退避、每次重新读取 token/用户、终态释放锁和一次 5 秒恢复轮次。
- HTTP 400/401/403 保持失败关闭，不降低用户映射和数据权限校验；状态提示不包含 token、原始响应或内部堆栈。
- CloudCC pagecomponent V13（`6a561531e4b0a577cbba2080`）和 customPage V7 已通过 `cc-customization-expert-msapi` 发布、绑定和回读。
- 真实 CloudCC CRM 页面首次加载及连续两次刷新均显示“CloudCC CRM 已连接”，客户列表与详情正常，无白屏或身份失败提示。

## Reopened Work

- pagecomponent 只使用 `$CCDK.CCToken.getToken()` 读取当前 CRM 会话，不再调用需要 clientId/secretKey 的 `getOpenApiToken`。
- 后端通过组织绑定网关的 `/api/user/getUserInfo` 验证该会话，并从 CloudCC 返回的登录标识和组织标识提取 actor/org，保持会话用户、页面用户、AgentCiCi 成员三方一致。
- 前端读取服务端安全消息，按真实原因展示可行动提示；不得显示 token、原始响应或内部异常。
- 增加普通用户、失效会话和身份不一致的聚焦测试，完成应用发布、pagecomponent 发布和真实 CRM 复验。

## Constraints

- 不降低 CloudCC 当前用户与 AgentCiCi 映射成员一致性校验。
- 不把 CloudCC CRM token 当作 AgentCiCi token。
- pagecomponent 发布、customPage 读取/绑定和注入页验证必须使用 `cc-customization-expert-msapi`。
- 不触碰 TASK-203/204/205 或未跟踪 `diagrams/`。
