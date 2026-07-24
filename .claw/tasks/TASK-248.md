---
kind: task-status
task_id: TASK-248
status: in_progress
updated_at: 2026-07-24T14:28:53Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: platform-governance-agent
assignment_path: .claw/assignments/TASK-248.yaml
spec_path: docs/specs/FEAT-141-platform-user-directory-organizations.md
---

# TASK-248 - 平台注册用户目录展示已加入组织

## Current State

- Status: `in_progress`
- Next action: 为全量账户目录补充一次批量有效成员关系聚合，并在既有表格显示组织名称。
- Blocked: none

## Scope

- 账户是唯一列表行源，组织归属只作为附加只读字段。
- 只展示当前有效组织，不展示成员角色或历史关系。
- 不改注册、组织成员关系、权限、迁移、主题、移动端、合并或生产发布。

## Evidence

- TASK-247 已在 `2.8.15` 将目录改为 `user_account` 全量账户查询，保证一账户一行。
- `company_member` 是一对多成员关系，本任务必须在账户分页之后批量读取并去重，不能用联结改变分页语义。

## Handoff

- 规格：`docs/specs/FEAT-141-platform-user-directory-organizations.md`。
- 实施分支：`codex/TASK-248-platform-user-organizations`。
