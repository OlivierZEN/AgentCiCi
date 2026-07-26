---
kind: task-status
task_id: TASK-251
status: review
updated_at: 2026-07-26T13:36:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-251.yaml
spec_path: docs/specs/FEAT-144-global-user-public-id.md
---

# TASK-251 - 全局用户公共编号

## Current State

- Status: `review`
- Next action: 已合入唯一的 `main` 工作区；如另行授权发布，执行 Flyway V97 自动回填真实存量账户，再以受权平台会话复核目录。
- Blocked: none

## Scope

- 公共编号固定为 `UYYYYXXXXXXXX`，数据库唯一且不可变。
- 保留 UUID、Keycloak 映射、登录标识和组织成员关系原有语义。
- 平台目录只读展示；不做生产发布、移动端或认证边界改造。

## Evidence

- 当前 `user_account.id` 由 `UUID.randomUUID().toString()` 生成，`account_external_identity` 已以 `issuer + subject` 唯一映射该全局账户。
- 现有平台注册用户目录已经以 `user_account` 为唯一行源，适合承载公共编号展示。
- 全新 PostgreSQL 16 已从 V1 迁移到 V96，插入 2024 历史账户后迁移 V97，断言回填格式为 `U2024XXXXXXXX`；随后插入 2026 账户，断言自动生成 `U2026XXXXXXXX` 且更新公共编号被数据库拒绝。
- 后端定向测试、前端 2 项定向测试、前端构建、模拟平台角色桌面截图和静态检查均通过；临时数据库容器已删除。
- 2026-07-26 已合入唯一的 AgentCiCi `main` 工作区，未发布生产。

## Handoff

- 规格：`docs/specs/FEAT-144-global-user-public-id.md`。
- 分支：`codex/TASK-251-global-user-public-id`。
