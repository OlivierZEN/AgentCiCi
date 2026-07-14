---
kind: task-status
task_id: TASK-211
status: ready
updated_at: 2026-07-14T23:22:06Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-211.yaml
spec_path: docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md
---

# TASK-211 - CRM 确定性回答真实流式输出纠偏

## Scope

- 把 CRM 确定性完整正文从单个 SSE `delta` 改为服务端有节奏的多分片输出。
- 保持 blocking、持久化、Agent OpenAPI、Top 5、五层经营分析和防泄漏事实完全一致。
- 以 TDD 覆盖 CRM SSE 与 OpenAPI streaming 的分片数量、顺序、拼接正文和结束事件。
- 发布新的不可变版本并完成 SalesA 五次真实流式页面验收。

## Current State

- 根因已验证：生产 5 次 CRM SSE 都只有一个 2,383 字符正文 `delta`；OpenAPI streaming 也只有一个正文 `message`。
- 前端逐片渲染和 Nginx buffering 配置正常；缺陷由后端 CRM 确定性分支的一次性 `safeSendDelta` 引入。
- 用户已批准方案 A：复用现有 `safeSendDeltaInChunks`，不恢复最终 LLM，不新增前端模拟打字。
- 详细事件契约、TDD 门禁、生产验收和回滚条件已写入 FEAT-114，等待用户确认书面规格后实施。

## Next Action

- 用户确认书面 FEAT-114 后，编写实施计划；先让多分片回归测试在当前实现上正确失败，再做最小后端修复。

## Constraints

- 不修改 CRM 数据、CloudCC 元数据、角色、简档、共享规则或演示批次。
- 不修改前端生产代码，不引入第二套打字动画或消息缓冲状态机。
- 不恢复最终 LLM，不泄漏工具事件、原始 JSON、内部 ID 或敏感凭据。
- 不覆盖 `2.7.5`，只允许按发布 runbook 创建后续不可变版本。

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/openapi/service/AgentOpenApiConversationServiceTest.java`
- `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md`
- `docs/superpowers/plans/2026-07-15-crm-streaming-output.md`
- `.claw/tasks/TASK-211.md`
- `.claw/assignments/TASK-211.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/issue-list.md`
- `.claw/test-report.md`
- `.claw/devops.md`

## Handoff

- 分支：`codex/TASK-211-crm-streaming-output`。
- 先读 FEAT-114 的“TASK-211 真实流式输出纠偏设计”，再读当前单包路径与已有分块 helper。
- 当前阶段只有治理文档变更，尚未编辑生产代码或测试。
