---
kind: task-status
task_id: TASK-254
status: review
updated_at: 2026-07-29T12:25:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-254.yaml
spec_path: docs/specs/FEAT-147-company-id-completeness-audit.md
---

# TASK-254 - company_id 迁移完整性审计与遗留修复

## Current State

- Status: `review`
- Next action: 等待 PostgreSQL 测试库可用时补跑 `AdminBillingIntegrationTest`；未经用户授权不合并主线或发布生产。
- Blocked: `127.0.0.1:5432` 当前不可达，Spring 集成测试无法建立 PostgreSQL/Flyway 上下文。

## Scope

- 只处理运行时源码和可执行运维脚本中表示顶层企业的旧 `orgId`/`org_id`。
- 保留 Flyway 历史、迁移验证和前端旧响应兼容，不修改生产数据库、前端页面、Semattice、主线或生产。

## Evidence

- `UserEntity` 当前关系属性为 `company`；账单仍出现 `member.org.id`。
- E2E 脚本、Qdrant smoke、生产演示 SQL 与当前运行时的 `companyId`/`company_id` 契约不一致。
- 已修复以上四条可执行路径；shell 语法、Python AST 解析、后端 `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 与定向静态扫描通过。

## Supersedes

- TASK-253 的单一账单修复由本任务完整性审计纳入，避免仅修一个页面而遗漏脚本路径。
