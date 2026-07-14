---
kind: task-status
task_id: TASK-206
status: in_progress
updated_at: 2026-07-14T10:34:25Z
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

## Next Action

- 完成任务授权检查后实现重试状态机，构建并通过本地故障注入验证。

## Constraints

- 不降低 CloudCC 当前用户与 AgentCiCi 映射成员一致性校验。
- 不把 CloudCC CRM token 当作 AgentCiCi token。
- pagecomponent 发布、customPage 读取/绑定和注入页验证必须使用 `cc-customization-expert-msapi`。
- 不触碰 TASK-203/204/205 或未跟踪 `diagrams/`。

