---
kind: task-status
task_id: TASK-247
status: in_progress
updated_at: 2026-07-24T13:37:58Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: platform-governance-agent
assignment_path: .claw/assignments/TASK-247.yaml
spec_path: docs/specs/FEAT-140-platform-user-directory.md
---

# TASK-247 - 平台全量个人用户目录

## Current State

- Status: `in_progress`
- Next action: 以全局账户为唯一数据源实现全量查询和定向回归。
- Blocked: none

## Scope

- 平台注册用户目录展示所有个人账户，不因组织成员关系排除账户。
- 同一账户加入多个组织时仅显示一条，不暴露组织明细。
- 保持接口鉴权、搜索、分页和现有表格结构；不发布生产。

## Evidence

- 已验证现有 `searchPersonalAccounts` 的 `not exists company_member` 条件是排除已加入组织用户的直接原因。

## Handoff

- 规格：`docs/specs/FEAT-140-platform-user-directory.md`。
