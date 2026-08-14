---
kind: task-status
task_id: TASK-307
feature_id: FEAT-187
integration_id: INT-022
status: review
updated_at: 2026-08-14T09:45:00+08:00
updated_by: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-187-devautopilot-name-based-task-transfer.md
---

# TASK-307 - 产品经理自动识别开发者并转派任务

## 当前证据

- AgentCiCi 定向测试和 backend package 通过。
- Semattice record、authorization、DevAutopilot template 与 catalog 定向测试通过。
- 待本地 main 制品部署后，以真实登录会话验证“鲁班下班了，把鲁班的任务都转交给哪吒”只生成确认草案；不在验收中执行真实转派。
