---
kind: task-status
task_id: TASK-269
status: done
updated_at: 2026-08-05T07:08:30Z
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

## Current State

- 已完成生产公共编号更正：手机号 `18611892001` 的 `public_id` 从 `U20267MV3E4N7` 更正为 `U2026OLVX1230`。
- 该次数据维护不涉及应用发布或任何 Keycloak、密码、MFA、会话、Principal、组织成员和业务数据变更。

## Out of Scope

- 不修改其他账户、组织成员关系、Principal、Keycloak 用户、密码、MFA、会话、角色或业务数据。
- 不发布应用镜像、不修改 Flyway 历史迁移，也不改变公共编号“默认不可变”的产品规则。

## Verification

- 预检：目标手机号命中 `1` 条，目标编号占用数为 `0`，格式 CHECK、唯一约束和不可变触发器均存在且启用。
- 备份：`/opt/cici/backups/20260805-150512-before-task269-user-public-id-correction/postgres.dump` 已创建（6,571,806 bytes，目录与文件权限已收紧）。
- 事务：受当前旧值保护的更新受影响行数为 `1`；事务内目标行数为 `1`，随后恢复触发器并成功提交。
- 独立回读：目标账号数 `1`、手机号与目标编号匹配数 `1`、旧编号剩余数 `0`、目标编号全局数 `1`、触发器状态 `O`；数据库容器 healthy，backend health 为 `UP`。

## Next Action

- 已完成；恢复 TASK-268 的后续业务本体实施规划。
