---
kind: task-status
task_id: TASK-308
feature_id: FEAT-185
integration_id: INT-023
status: review
updated_at: 2026-08-14T08:43:00Z
updated_by: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-185-devautopilot-delegated-product-manager-execution.md
---

# TASK-308 - DevAutopilot 任务评审委托授权

## 当前证据

- AgentCiCi 为固定操作 `TASK_REVIEW` 签发 `runtime.record.read/create/update`，调用方不能自行选择 scope 或机器主体。
- 定向测试 `DevAutopilotExecutionAuthorizationServiceTest` 通过，提交 `44f4a6f9` 已进入本地 `main`。
- 本地 backend/frontend 运行 `2.8.61-dev.44f4a6f`，均 healthy、restart=0；完整 `cc-local-stack ./stack verify` 通过。
- DevAutopilot 消费端已在已登录真实任务页面回读方案和二次确认入口；未替用户提交评审决定，任务保持 `设计待确认 / revision 4`。UAT/生产未修改。
