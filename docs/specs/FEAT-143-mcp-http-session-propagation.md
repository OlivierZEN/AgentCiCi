---
kind: feature-spec
feature_id: FEAT-143
title: MCP Streamable HTTP 会话复用修复
status: done
owner_role: backend-agent
task_ids: TASK-250
related_decisions: none
related_issues: MCP 工具刷新失败：tools/list 在会话初始化阶段无效
updated_at: 2026-07-25T00:10:00Z
updated_by: MANAGER-001
---

# FEAT-143 - MCP Streamable HTTP 会话复用修复

## 背景与目标

- `cc-semattic-mcp` 的工具刷新在 `tools/list` 阶段失败，服务端返回 `method "tools/list" is invalid during session initialization`。
- 已确认 Semattice 的 `initialize` 响应包含 `Mcp-Session-Id`；AgentCiCi 之后的请求没有稳定复用该会话，且未统一发送 `MCP-Protocol-Version`。
- 本功能让 AgentCiCi 的 Streamable HTTP 客户端完整执行初始化、初始化完成通知和会话头透传，而不改变 Semattice 的严格协议校验。

## 范围

### In Scope

- `initialize` 成功响应到达后立即读取并在内存中保存 `Mcp-Session-Id`，包括 SSE 格式的 JSON-RPC 响应。
- 在携带同一会话 ID 的前提下发送 `notifications/initialized`；只有该通知成功后才标记客户端初始化完成。
- `tools/list`、`tools/call` 统一携带已保存的会话 ID 与 `MCP-Protocol-Version`；动态 `Authorization: Bearer ...` 头继续透传到工具调用。
- 在没有已完成初始化的本地状态时，工具列表和工具调用先完成初始化；删除或变更 MCP Server 时继续清理内存会话。
- 用本地 HTTP 伪服务覆盖 `initialize → notifications/initialized → tools/list → tools/call` 的顺序、请求头和 Bearer JWT 透传。

### Out Of Scope

- 不修改 Semattice 服务、协议校验、租户绑定、MCP Server 配置页面或数据库结构。
- 不把短生命周期的 MCP 会话 ID 持久化到数据库、日志或前端。
- 不执行主线合并、镜像构建或生产发布。

## 用户场景

- 组织管理员刷新 `cc-semattic-mcp` 工具列表时，AgentCiCi 先获得服务端会话，再同步工具；不再出现“会话初始化期间 tools/list 无效”。
- 已发现工具在执行时，客户端复用同一会话并同时携带为当前组织/用户解析的 Bearer JWT。
- 进程重启、服务配置变更或会话缺失后，客户端安全地重新初始化，而不是直接发送未初始化的 MCP 方法。

## 现状与约束

- `McpClient` 目前在解析普通 JSON-RPC 成功响应后才写入会话；SSE 响应分支会提前返回，导致 `initialize` 的响应头未保存。
- 发送请求时的静态或动态配置头可覆盖先前附加的会话头，且没有保证 `MCP-Protocol-Version`。
- MCP 会话仅适合在当前后端进程中按服务器隔离保存；不能作为长期配置或审计内容持久化。

## 方案设计

1. 初始化请求使用声明的协议版本；收到 HTTP 响应后先读取 `Mcp-Session-Id`，再解析 JSON 或 SSE 的 JSON-RPC 结果。
2. 将会话 ID 写入按 MCP Server 隔离的内存状态，发送带同一会话 ID 的 `notifications/initialized`，成功后标记初始化完成。
3. `tools/list` 与 `tools/call` 在调用前确保初始化完成；所有后续 HTTP POST 在静态/动态头处理后强制设置当前 `MCP-Protocol-Version` 和内存会话 ID，防止配置覆盖协议头。
4. 删除或更新服务器配置时沿用现有缓存失效路径清理该服务器的初始化状态和会话 ID。

## 接口与数据影响

- 不新增 API、数据库字段或前端接口。
- 远端 MCP 请求新增/保证以下头：`MCP-Protocol-Version`，以及服务端曾提供时的 `Mcp-Session-Id`。
- `tools/call` 保持既有动态请求头合并规则，因此 Bearer JWT 仍由 `McpServerService` 解析并透传。

## 任务拆分

- TASK-250：实现 MCP HTTP 客户端会话状态、严格初始化顺序和定向回归测试。

## 验收标准

- 伪 MCP 服务能观察到严格顺序：`initialize`、`notifications/initialized`、`tools/list`、`tools/call`。
- 初始化响应的 `Mcp-Session-Id` 被用于后三个请求；`tools/list` 与 `tools/call` 均带 `MCP-Protocol-Version`；`tools/call` 带 Bearer JWT。
- SSE 形式的初始化 JSON-RPC 响应同样会保存会话 ID。
- 定向 Maven 测试、后端编译和 `git diff --check` 通过。

## 风险与回滚

- 风险：第三方无会话 MCP Server 可能不返回 `Mcp-Session-Id`。客户端仍完成初始化并保留无会话兼容行为；只在服务端明确给出 ID 时透传。
- 回滚：回退本功能分支提交即可恢复原有客户端；不涉及数据迁移或服务器端状态变更。

## 实现进展

- 当前状态：已合并至 `main`（`4958bc1`）。
- `McpClient` 将初始化响应和解析结果分离，SSE 分支也会在返回前读取 `Mcp-Session-Id`；`notifications/initialized` 失败会阻止后续调用。
- 会话和初始化完成状态按 MCP Server 隔离保存在当前进程，`tools/list`/`tools/call` 会先确保初始化完成。
- 配置或动态头中的 `Mcp-Session-Id`、`MCP-Protocol-Version` 被忽略，最终请求只使用客户端协商的会话和声明的协议版本。

## 交接说明

- 优先检查 `backend/src/main/java/com/codehouse/ciciassistant/mcp/service/McpClient.java` 的 SSE 提前返回路径和请求头覆盖顺序。
- 验证不得访问或改动 Semattice 服务；生产发布需要用户另行授权。
