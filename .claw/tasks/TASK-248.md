---
kind: task-status
task_id: TASK-248
status: review
updated_at: 2026-07-24T14:34:49Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: platform-governance-agent
assignment_path: .claw/assignments/TASK-248.yaml
spec_path: docs/specs/FEAT-141-platform-user-directory-organizations.md
---

# TASK-248 - 平台注册用户目录展示已加入组织

## Current State

- Status: `review`
- Next action: 等待用户决定是否将已验证的功能分支合并到 `main`；本任务未获生产发布授权。
- Blocked: none

## Scope

- 账户是唯一列表行源，组织归属只作为附加只读字段。
- 只展示当前有效组织，不展示成员角色或历史关系。
- 不改注册、组织成员关系、权限、迁移、主题、移动端、合并或生产发布。

## Evidence

- TASK-247 已在 `2.8.15` 将目录改为 `user_account` 全量账户查询，保证一账户一行。
- `company_member` 是一对多成员关系，本任务必须在账户分页之后批量读取并去重，不能用联结改变分页语义。
- `mvn -q -Dtest=PlatformRegisteredUserServiceTest test`、后端编译、前端 2 项定向测试、生产构建和 `git diff --check` 均通过。
- 本地桌面端访问受保护路由按预期转至平台登录，控制台没有 error；无受权平台账号，未使用或伪造凭据来验收真实目录内容。

## Handoff

- 规格：`docs/specs/FEAT-141-platform-user-directory-organizations.md`。
- 实施分支：`codex/TASK-248-platform-user-organizations`。
