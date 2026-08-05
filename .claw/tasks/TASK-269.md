---
kind: task-status
task_id: TASK-269
status: in_progress
updated_at: 2026-08-05T07:03:22Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: project-manager
assignment_path: n/a
spec_path: n/a
---

# TASK-269 - 指定全局用户公共编号更正

## Scope

- 经用户明确授权，仅将生产 `user_account.primary_mobile = 18611892001` 的 `public_id` 更正为 `U2026OLVX1230`。
- 变更前必须确认手机号精确命中一条账号、目标编号未被占用，且当前值符合既有 `UYYYYXXXXXXXX` 格式。
- `trg_user_account_public_id_immutable` 会拒绝常规修改；仅在单个数据库事务内临时停用该触发器，执行受当前值保护的单行更新、恢复触发器并提交。唯一约束和格式 CHECK 必须继续有效。

## Out of Scope

- 不修改其他账户、组织成员关系、Principal、Keycloak 用户、密码、MFA、会话、角色或业务数据。
- 不发布应用镜像、不修改 Flyway 历史迁移，也不改变公共编号“默认不可变”的产品规则。

## Verification

- 待执行：生产行级预检、变更前 PostgreSQL 备份、事务内更新与回读、触发器状态确认。

## Next Action

- 将任务登记推送主线后，执行只读预检；预检全部通过才进行一次性生产数据更正。
