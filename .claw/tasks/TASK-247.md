---
kind: task-status
task_id: TASK-247
status: done
updated_at: 2026-07-24T14:23:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: platform-governance-agent
assignment_path: .claw/assignments/TASK-247.yaml
spec_path: docs/specs/FEAT-140-platform-user-directory.md
---

# TASK-247 - 平台全量个人用户目录

## Current State

- Status: `done`
- Next action: 已合并 main 并发布 `2.8.15`；等待受权平台账号复核目录内容。
- Blocked: none

## Scope

- 平台注册用户目录展示所有个人账户，不因组织成员关系排除账户。
- 同一账户加入多个组织时仅显示一条，不暴露组织明细。
- 保持接口鉴权、搜索、分页和现有表格结构；用户已明确授权后发布生产。

## Evidence

- 已验证现有 `searchPersonalAccounts` 的 `not exists company_member` 条件是排除已加入组织用户的直接原因。
- `searchRegisteredAccounts` 只查询 `user_account`，因此每个全局账户最多产生一行，不受零、一或多条成员关系影响。
- `mvn -q -Dtest=PlatformRegisteredUserServiceTest test` 通过（2 tests）；前端定向测试 4/4、生产构建和 `git diff --check` 通过。
- 合并后完整后端测试、前端定向测试与构建、Compose 配置检查均通过；生产 `2.8.15 / 38cb22e3a587` health `UP`，六服务健康。

## Release

- 主线合并提交 `38cb22e3a587`，annotated tag `2.8.15` 已推送。
- 备份：`/opt/cici/backups/20260724-222041-before-2.8.15-task247` 的 env、PostgreSQL、KB、Qdrant 均非空。
- backend/frontend ACR index digest：`sha256:8e4fc950102a0c1173c8e97c545358b28533d5fea0c98a0aca533ee7c1ffd81d`、`sha256:7e0bf4f0ed12ecd644630ead048953a5428395e32da9abdd1ddd73a55c2ff080`。
- 仅重建 backend/frontend；`agentcici.com`、`/platform/registered-users`、`x.agentcici.com` 均 HTTP 200，匿名平台接口按预期为 401。

## Handoff

- 规格：`docs/specs/FEAT-140-platform-user-directory.md`。未用或伪造平台凭据，真实受保护页面验收待受权账号完成。
