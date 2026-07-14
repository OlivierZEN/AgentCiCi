---
kind: task-status
task_id: TASK-206
status: done
updated_at: 2026-07-14T10:51:08Z
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

## Delivery Result

- Vue pagecomponent 与 UMD fallback 均已实现 `0/800/2000/4000ms` 有限退避、每次重新读取 token/用户、终态释放锁和一次 5 秒恢复轮次。
- HTTP 400/401/403 保持失败关闭，不降低用户映射和数据权限校验；状态提示不包含 token、原始响应或内部堆栈。
- CloudCC pagecomponent V13（`6a561531e4b0a577cbba2080`）和 customPage V7 已通过 `cc-customization-expert-msapi` 发布、绑定和回读。
- 真实 CloudCC CRM 页面首次加载及连续两次刷新均显示“CloudCC CRM 已连接”，客户列表与详情正常，无白屏或身份失败提示。

## Constraints

- 不降低 CloudCC 当前用户与 AgentCiCi 映射成员一致性校验。
- 不把 CloudCC CRM token 当作 AgentCiCi token。
- pagecomponent 发布、customPage 读取/绑定和注入页验证必须使用 `cc-customization-expert-msapi`。
- 不触碰 TASK-203/204/205 或未跟踪 `diagrams/`。
