---
kind: task-status
task_id: TASK-294
status: in_progress
updated_at: 2026-08-12T15:00:00Z
updated_by: codex
assignee: codex
owner_role: integration-agent
spec_path: docs/specs/FEAT-178-devautopilot-intake-field-fidelity.md
depends_on: none
---

# TASK-294 - 修正 DevAutopilot 受理草稿字段映射

## 范围

- 约束可见草稿与隐藏结构化载荷一致。
- 增强隐藏标记缺失时的 Markdown 草稿解析。
- 完整保留分析、验收标准、影响分析和开发者验证项。

## 完成条件

- 满足 FEAT-178 验收标准，定向测试和后端构建通过。
- 变更独立提交并合并到 AgentCiCi 本地 `main`。
- 本地 backend 健康、版本与业务回读一致。
