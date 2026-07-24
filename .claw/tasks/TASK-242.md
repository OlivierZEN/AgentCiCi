---
kind: task-status
task_id: TASK-242
status: in_progress
updated_at: 2026-07-24T01:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-242.yaml
spec_path: docs/specs/FEAT-135-company-id-unification.md
---

# TASK-242 - 顶层租户 company_id 统一

## Scope

- 按 FEAT-135 将 AgentCiCi 顶层租户的 `org_id` 统一为 `company_id`，并与 Semattice 的既有受控开户契约对齐。
- 新增 V94 前向迁移；同步后端、前端、JWT、测试、规格和运营端显示。

## Constraints

- 不修改 V1–V93；不保留旧字段兼容路径；不重写已有 ID 字符串。
- `organization_id` 仅为未来内部组织架构保留，当前不得复用为顶层租户身份。

## Next action

完成最终回归：fresh PostgreSQL V1→V94、认证旧 JWT fail-closed、平台生命周期与 Semattice 受控开户；随后提交 AgentCiCi 变更并同步 Semattice 契约文档。
