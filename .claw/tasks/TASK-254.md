---
kind: task-status
task_id: TASK-254
status: complete
updated_at: 2026-07-29T12:35:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: backend-agent
assignment_path: .claw/assignments/TASK-254.yaml
spec_path: docs/specs/FEAT-147-company-id-completeness-audit.md
---

# TASK-254 - company_id 迁移完整性审计与遗留修复

## Current State

- Status: `complete`
- Next action: 已发布生产；本机 PostgreSQL 恢复后可补跑 `AdminBillingIntegrationTest`，不影响本次已完成的发布记录。
- Blocked: none

## Scope

- 只处理运行时源码和可执行运维脚本中表示顶层企业的旧 `orgId`/`org_id`。
- 保留 Flyway 历史、迁移验证和前端旧响应兼容，不修改生产数据库、前端页面、Semattice、主线或生产。

## Evidence

- `UserEntity` 当前关系属性为 `company`；账单仍出现 `member.org.id`。
- E2E 脚本、Qdrant smoke、生产演示 SQL 与当前运行时的 `companyId`/`company_id` 契约不一致。
- 已修复以上四条可执行路径；shell 语法、Python AST 解析、后端 `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` 与定向静态扫描通过。
- 主线已合并为 `105cc666a958`，生产已发布 `2.8.25`；四项备份均非空，六容器健康，health `UP`、版本接口、Nginx、`x.agentcici.com` 和匿名 401 边界均通过。

## Supersedes

- TASK-253 的单一账单修复由本任务完整性审计纳入，避免仅修一个页面而遗漏脚本路径。
