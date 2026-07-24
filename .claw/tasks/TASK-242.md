---
kind: task-status
task_id: TASK-242
status: done
updated_at: 2026-07-24T06:12:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: integration-agent
assignment_path: .claw/assignments/TASK-242.yaml
spec_path: docs/specs/FEAT-135-company-id-unification.md
---

# TASK-242 - 顶层租户 company_id 统一

## Scope

- 按 FEAT-135 将 AgentCiCi 顶层租户的 `org_id` 统一为 `company_id`，并与 Semattice 的既有受控开户契约对齐。
- 新增 V94/V95 前向迁移；同步后端、前端、JWT、测试、规格和运营端显示。

## Constraints

- 不修改 V1–V93；不保留旧字段兼容路径；不重写已有 ID 字符串。
- `organization_id` 仅为未来内部组织架构保留，当前不得复用为顶层租户身份。

## Completion

用户已明确授权立即发布。生产 `2.8.9 / 0194706` 已于 2026-07-24 发布：V94 成功统一 company 身份与遗留授权 principal，V95 成功将 profile 的 `organization_size` 改为 `company_size`；backend/frontend 与四个状态服务健康，`/actuator/health` 为 `UP`，公网 HTTPS 与匿名鉴权边界 smoke 通过。V94 的初版约束顺序问题和 V94 后 profile 字段遗漏均已通过新不可变版本修复；失败制品 `2.8.7` / `2.8.8` 未作为健康版本交付。
