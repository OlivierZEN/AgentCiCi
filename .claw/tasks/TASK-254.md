---
kind: task-status
task_id: TASK-254
status: in_progress
updated_at: 2026-07-29T12:10:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-254.yaml
spec_path: docs/specs/FEAT-147-company-id-completeness-audit.md
---

# TASK-254 - company_id 迁移完整性审计与遗留修复

## Current State

- Status: `in_progress`
- Next action: 统一账单 JPQL、E2E、Qdrant smoke 与演示 SQL 的企业标识字段，并运行静态/编译验证。
- Blocked: none

## Scope

- 只处理运行时源码和可执行运维脚本中表示顶层企业的旧 `orgId`/`org_id`。
- 保留 Flyway 历史、迁移验证和前端旧响应兼容，不修改生产数据库、前端页面、Semattice、主线或生产。

## Evidence

- `UserEntity` 当前关系属性为 `company`；账单仍出现 `member.org.id`。
- E2E 脚本、Qdrant smoke、生产演示 SQL 与当前运行时的 `companyId`/`company_id` 契约不一致。

## Supersedes

- TASK-253 的单一账单修复由本任务完整性审计纳入，避免仅修一个页面而遗漏脚本路径。
