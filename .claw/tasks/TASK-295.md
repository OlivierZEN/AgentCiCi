---
kind: task-status
task_id: TASK-295
status: done
updated_at: 2026-08-12T15:25:52Z
updated_by: codex
assignee: codex
owner_role: release-agent
depends_on: none
---

# TASK-295 - AgentCiCi 单发布线版本规范

## 范围

- 在项目规则与生产发布 Runbook 中固化 `Q-dev.<SHA> → Q-beta.N → Q`。
- 明确生产完成前不推进 DEV 基础版本，生产完成后再与下一轮 UAT 同步推进。

## 验收

- 发布文档明确拒绝基础版本降号晋级。
- 不修改发布脚本、业务源码、UAT、生产、ACR 或当前运行环境。
