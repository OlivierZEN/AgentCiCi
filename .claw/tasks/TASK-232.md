---
kind: task-status
task_id: TASK-232
status: done
updated_at: 2026-07-23T10:18:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-232.yaml
spec_path: docs/specs/FEAT-131-agent-memory-platform.md
---

# TASK-232 - 通用记忆审核 API 与质量门禁

## Scope

- 提供按组织与 Agent 权限隔离的通用候选查询、审核通过和拒绝 API；
- 候选必须持久化其产生时的 Agent 归属；审核 API 只能读取路径 Agent 的候选，拒绝以 scopeKey 等可伪造字段推断归属；
- 审核操作必须带操作者和原因，写入脱敏审计，并复用候选审核服务的幂等与索引规则；
- 将记忆上下文注入的最小 Trace 证据纳入既有评测/发布质量检查，禁止把记忆正文、外部主体标识或向量内容写入 Trace/评测资产。

## Non-goals

- 不增加外部应用专属页面、渠道流程或自动批准长期记忆；
- 不改变已有 Agent 评测资产权限、发布流程或生产环境。

## Acceptance

- 无 Agent 权限、跨组织或重复审核均被安全拒绝；审核通过才创建可读取记录，拒绝不写入可读取记录；
- Trace 和评测门禁能区分“未注入”“已注入”“被截断/降级”，且不泄露记忆正文；
- 定向 API/权限/审核/Trace/评测测试、后端编译和静态检查通过。

## Progress

- V89 为候选补充产生 Agent 归属；新候选必须带 `agentId`，审核与查询使用组织、归属 Agent 和 `MANAGE` 权限共同过滤。
- 已提供候选最小视图、批准和拒绝 API；审核事件只记录 Agent 与候选 ID，不记录主体标识或正文。Trace 现明确输出 `NOT_INJECTED`、`INJECTED` 或 `TRUNCATED` 状态。
- 现有评测断言引擎已支持 `MEMORY_CONTEXT_STATE`，评测 dry-run 固定标记 `NOT_INJECTED` 并只输出状态型 Trace，不携带记忆正文或主体标识。
- 两个独立通用适配契约以 `adapter-alpha` 与 `adapter-beta` 配置验证：相同外部主体标识在不同凭据绑定、应用代码、主体类型和内部会话下产生不同可信上下文；禁用其中一个绑定时只对该适配安全降级。
