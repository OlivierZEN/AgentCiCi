---
kind: task-status
task_id: TASK-247
status: done
updated_at: 2026-07-24T13:42:30Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: platform-governance-agent
assignment_path: .claw/assignments/TASK-247.yaml
spec_path: docs/specs/FEAT-140-platform-user-directory.md
---

# TASK-247 - 平台全量个人用户目录

## Current State

- Status: `done`
- Next action: 等待合并；生产发布仅在用户明确授权后执行。
- Blocked: none

## Scope

- 平台注册用户目录展示所有个人账户，不因组织成员关系排除账户。
- 同一账户加入多个组织时仅显示一条，不暴露组织明细。
- 保持接口鉴权、搜索、分页和现有表格结构；不发布生产。

## Evidence

- 已验证现有 `searchPersonalAccounts` 的 `not exists company_member` 条件是排除已加入组织用户的直接原因。
- `searchRegisteredAccounts` 只查询 `user_account`，因此每个全局账户最多产生一行，不受零、一或多条成员关系影响。
- `mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过（2 tests）；前端定向测试 4/4、生产构建和 `git diff --check` 通过。

## Handoff

- 规格：`docs/specs/FEAT-140-platform-user-directory.md`。本地浏览器仅能到达平台登录边界，未用或伪造平台凭据，真实受保护页面验收待受权账号完成。
