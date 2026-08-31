---
kind: task-status
task_id: TASK-345
integration_id: INT-029
feature_id: FEAT-205
status: in_progress
priority: critical
primary_project: agentcici
owner_role: integration-agent
claimed_by: codex
spec_path: docs/specs/FEAT-205-application-version-mcp-binding.md
updated_at: 2026-08-31T03:26:04Z
updated_by: codex
---

# TASK-345 - DevAutopilot MCP-only 解耦回归修复

## 已验证故障

- 工具目录仍把六个研发交付工具从 `BuiltinToolCatalog` 和既有平台工具治理数据中标记为“内置”。
- `ToolOrchestratorService` 在租户没有有效应用绑定时仍回退到 AgentCiCi 内部 Semattice Service，不满足 MCP-only 和失败关闭。
- Agent Builder 的 `/tools` 目录不包含应用绑定 MCP 工具，无法把外部应用工具作为一等白名单能力展示和编译。
- 目标租户的六工具变更已生成 `v2 DRAFT`，但前端把内部 `web` 渠道等同于外部 Web 浮窗，因缺少 `widgetKey / Origin / runAsUser` 禁用发布按钮。
- 真实产品经理查询已进入绑定的 MCP Server；本地 DevAutopilot 因非 root 运行用户无法读取 `0600 root:root` 的签名密钥挂载而持续重启，调用失败。

## 范围

- 从内置目录、平台内置治理和运行时本地分发中移除六个研发交付工具。
- 所有应用版本声明的 MCP 工具只允许通过当前租户的 ACTIVE 应用绑定执行；缺绑定必须明确失败，不能进入通用 MCP 或本地 Semattice 回退。
- `/tools` 合并当前租户应用绑定 MCP 工具，并携带应用、Provider、Server 和风险来源；Agent 编译读取同一权威目录。
- 发布工作流不再被未配置的外部 Web 浮窗参数错误阻塞；浮窗公开服务继续独立失败关闭。
- 恢复本地 DevAutopilot 非 root 进程对受管密钥的最小只读权限，完成真实 MCP 查询回归。

## 完成条件

- [ ] 六工具不再以“内置”出现在工具目录或平台内置工具治理中。
- [ ] 有绑定时工具定义和执行只命中绑定 Server；无绑定时返回应用绑定缺失错误，内部 Semattice Service 调用次数为零。
- [ ] Agent Builder 能选择并显示应用 MCP 工具，白名单变更编译后可发布新工作流版本。
- [ ] 内部 Web 渠道智能体不需要配置外部浮窗即可发布；未完整配置的浮窗仍不能签发公开 Token。
- [ ] 后端、前端聚焦测试与构建通过；本地 `cici.localhost` 完成目录、编译/发布和真实 DevAutopilot MCP 查询验证。

## 回滚

- 回滚 AgentCiCi 制品会恢复旧目录和本地回退，仅允许在本地开发环境诊断；不得作为 INT-029 的 UAT/生产目标。
- 本地 DevAutopilot 密钥权限修正只影响 Git 忽略的开发 Secret 和本地编排；UAT/生产必须按各自受管 Secret 机制独立验证。
