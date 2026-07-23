---
kind: task-status
task_id: TASK-229
status: done
updated_at: 2026-07-23T00:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-229.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-229 - 通用可信运行时记忆上下文

## Scope

- 定义仅可由可信服务端调用的通用外部主体运行时上下文；
- 将已授权的摘要、结构化记忆和语义命中按预算注入 Chat 编排器；
- 不存在或不可信上下文时维持现有内部用户聊天行为，不隐式创建外部主体。

## Non-goals

- 不实现具体外部应用的认证、渠道、页面或业务工具；
- 不信任客户端传入的组织、主体、Agent 或 scope；不保存原始会话。

## Acceptance

- 有效的可信上下文只注入其允许读取的通用记忆；
- 外部上下文缺失、非法或未注册时不降级为跨主体读取；
- 内部用户聊天路径保持现有行为；定向编排测试、编译和 diff 检查通过。

## Progress

- 已实现服务端显式作用域 `TrustedMemoryRuntimeContextService`，并接入 Chat 系统提示词装配。作用域要求组织和最终解析 Agent 同时匹配，退出后自动清除；无上下文时不注入。
- Trace 记录是否注入、结构化记忆数量、语义命中数量和截断状态，不记录主体标识或记忆正文。
- 定向作用域、Chat 编排回归与后端编译通过；尚待补充从受认证适配层建立该上下文的接口契约。
