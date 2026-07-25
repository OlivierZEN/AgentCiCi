---
kind: task-status
task_id: TASK-250
status: review
updated_at: 2026-07-25T00:00:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-250.yaml
spec_path: docs/specs/FEAT-143-mcp-http-session-propagation.md
---

# TASK-250 - MCP HTTP 会话复用修复

## Current State

- Status: `review`
- Next action: 等待用户授权合并主线或生产发布；发布后在 `cc-semattic-mcp` 刷新工具并执行受权工具调用复核。
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

## Verification

- 任务范围的 SSH 身份、Git 身份、任务分支与 MCP 源码/测试/状态文件均经 `dev-login.py` 和 `check-assignment.py` 返回 `allowed`。
- `mvn -q -Dmaven.repo.local=../.m2 -Dtest=McpClientTest test` 通过（1 test）：本地 HTTP 伪 MCP 服务以 SSE 返回 initialize 结果，断言四步顺序、会话 ID、协议版本与 Bearer JWT。
- `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 和 `git diff --check` 通过。
