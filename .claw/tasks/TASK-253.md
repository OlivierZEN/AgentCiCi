---
kind: task-status
task_id: TASK-253
status: review
updated_at: 2026-07-29T12:03:22Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-253.yaml
spec_path: docs/specs/FEAT-146-billing-company-member-query-repair.md
---

# TASK-253 - 计费用量公司成员查询修复

## Current State

- Status: `review`
- Next action: 等待用户授权合并主线或生产发布；有可用 PostgreSQL 测试库后复跑账单总览集成测试。
- Blocked: none

## Evidence

- 线上组织管理端截图显示：`Could not resolve attribute 'org' of 'UserEntity'`。
- 当前 `UserEntity` 只有 `company` 关联；失败查询位于 `BillingUsageMeteringService.activeBuilderSeatUsers`。

## Scope

- 仅修改计费服务的实体路径与定向回归测试。
- 不修改计费策略、迁移、前端、主线或生产环境。

## Verification

- `member.org` 已替换为当前实体关联 `member.company.id`；OWNER/ORG_ADMIN、ACTIVE 与 `companyId` 条件未变。
- `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 与 `git diff --check` 通过。
- `mvn -q -Dmaven.repo.local=../.m2 -Dtest=AdminBillingIntegrationTest test` 未进入用例：Flyway 连接 `localhost:5432` 被拒绝；未修改测试库、迁移或进行 repair。
