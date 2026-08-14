---
kind: task-status
task_id: TASK-307
feature_id: FEAT-187
integration_id: INT-022
status: review
updated_at: 2026-08-14T06:25:00Z
updated_by: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-187-devautopilot-name-based-task-transfer.md
---

# TASK-307 - 产品经理自动识别开发者并转派任务

## 当前证据

- AgentCiCi 定向测试、backend package，以及 Semattice record、authorization、DevAutopilot template、catalog 全量 Go 测试通过。
- 本地开发测试环境已从各自本地 `main` 构建：AgentCiCi backend 为 `2.8.61-dev.9cce47c`，Semattice 为 `1.0.3-dev.81685db`；均 healthy、restart=0，完整 `cc-local-stack ./stack verify` 通过。
- 真实登录会话中，“把鲁班的任务都转交给哪吒”已自动识别两个 Developer Profile，并只生成 1 项“待开始”任务的确认草案；未输入最终确认口令，任务 owner 保持鲁班。
- 真实转派仍需组织管理员显式把 `runtime.record.transfer` 同步给产品经理 SERVICE；开发者 SERVICE 不授予该 scope。授权后由用户回复精确确认口令才会执行写入。
