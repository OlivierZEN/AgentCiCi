---
kind: task-status
task_id: TASK-250
status: in_progress
updated_at: 2026-07-24T23:56:24Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-250.yaml
spec_path: docs/specs/FEAT-143-mcp-http-session-propagation.md
---

# TASK-250 - MCP HTTP 会话复用修复

## Current State

- Status: `in_progress`
- Next action: 实现初始化响应头捕获、初始化完成通知和后续 MCP 请求头透传，并运行定向回归。
- Blocked: none

## Scope

- 修复 AgentCiCi Java `McpClient` 的短生命周期会话状态和 Streamable HTTP 初始化顺序。
- 覆盖 SSE 初始化响应、会话 ID、协议版本、Bearer JWT 与工具调用顺序。
- 不修改 Semattice、MCP Server 数据、前端、数据库、主线或生产环境。

## Evidence

- Semattice 已在公网复现：`initialize` 返回 `Mcp-Session-Id`，无该头的 `tools/list` 返回会话初始化错误；携带该头后返回正常工具列表。
- 当前客户端的 SSE 解析在记录响应头前提前返回，且请求头处理顺序不能保证协议/会话头不被配置覆盖。

## Handoff

- 规格：`docs/specs/FEAT-143-mcp-http-session-propagation.md`。
- 实施分支：`codex/TASK-250-mcp-session-propagation`。
- 任务结束前不得合并 `main` 或发布生产；需要用户明确授权。
