---
kind: task-status
task_id: TASK-308
feature_id: FEAT-185
integration_id: INT-023
status: done
updated_at: 2026-08-14T09:20:00Z
updated_by: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-185-devautopilot-delegated-product-manager-execution.md
---

# TASK-308 - DevAutopilot 任务评审委托授权

## 当前证据

- 真实驳回首次失败证明 `TASK_REVIEW` 在读取任务前会调用 `identity.principal.sync`，而旧委托只签发 `runtime.record.read/create/update`。
- 提交 `95656c5b` 将 `identity.principal.sync` 加入固定 `TASK_REVIEW` 白名单；调用方仍不能自选 scope 或机器主体。定向测试和 backend package 通过。
- 本地 backend 运行 `2.8.61-dev.95656c5`，healthy/restart=0；完整 `cc-local-stack ./stack verify` 通过。
- 已登录真实页面使用原始意见成功驳回：任务进入 `设计驳回 / revision 5`；事件账本产生 `design_changes_requested / changes_requested`，意见完整回读。UAT/生产未修改。
