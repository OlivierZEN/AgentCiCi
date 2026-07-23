---
kind: task-status
task_id: TASK-230
status: ready
updated_at: 2026-07-23T01:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-230.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-230 - 受认证凭据记忆上下文绑定

## Scope

- 建立 API 凭据到通用 `applicationCode`、主体类型、身份等级和允许命名空间的服务端绑定；
- OpenAPI 只从已认证凭据和绑定构建可信记忆上下文，客户端仅能提交外部主体标识；
- 覆盖阻塞与流式调用，绑定缺失时安全降级为不注入记忆。

## Non-goals

- 不增加具体应用渠道、客户/订单等领域模型，或客户端可配置的组织/Agent/scope；
- 不把原始外部用户资料复制到 Agent CC。

## Acceptance

- 同一外部主体标识在不同凭据绑定下无法互相读取；
- 伪造应用、主体类型、身份等级或 scope 的客户端 metadata 不影响可信上下文；
- 绑定缺失或禁用时不注入记忆且不影响现有 OpenAPI 调用。

## Progress

- 已新增 V88 凭据绑定表、绑定服务和 OpenAPI 阻塞/流式调用接入。可信上下文完全由已认证凭据及其绑定导出；绑定缺失、主体缺失或主体未注册均安全降级为无记忆。
- 定向绑定、可信作用域、OpenAPI 会话回归与后端编译通过；待补充绑定管理 API、生命周期与两个适配契约验收。
