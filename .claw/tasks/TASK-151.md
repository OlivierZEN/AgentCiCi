---
kind: task-status
task_id: TASK-151
assignee: MANAGER-001
owner_role: fullstack-agent
status: done
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-064-rbac-production-readiness.md
assignment_path: .claw/assignments/TASK-151.yaml
updated_at: 2026-06-23T10:12:00+08:00
updated_by: MANAGER-001
---

# TASK-151 RBAC Production Readiness

## Scope

把当前 RBAC 与审计追踪从受控内测可用收口到生产默认安全：禁用外部 header 上下文伪造、建立默认鉴权兜底、细分平台写接口角色，并补齐平台模型治理审计、平台审计查询脱敏和审计查询索引。

## Plan

- 后端：硬化 `TenantContextFilter`，保留明确公开白名单与 OpenAPI/embed 例外。
- 后端：为平台治理、计费、模型写接口补方法级平台角色约束。
- 后端：补平台模型治理写动作审计、平台审计 DTO 查询/脱敏、组织审计数据库侧过滤和审计查询索引。
- 前端：平台审计页补关键词、事件类型、资源类型筛选，并兼容新旧审计响应。
- 测试：新增 RBAC 生产就绪集成测试，覆盖无 token、伪造 header、token 混用和平台角色越权。
- 状态：更新 FEAT-064、任务板、当前状态和测试报告。

## Verification Target

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,AuthFlowIntegrationTest,AgentOpenApiIntegrationTest test`
- `mvn -q -Dtest=AgentRunTraceIntegrationTest,RbacProductionReadinessIntegrationTest,PlatformGovernanceIntegrationTest,PlatformModelProviderIntegrationTest test`
- `npm run build`
- `git diff --check`
- assignment scope check

## Progress

- 2026-06-08T00:00:00+08:00: Task opened and assigned to `MANAGER-001` on `codex/TASK-151-rbac-production-readiness`.
- 2026-06-08T11:28:00+08:00: Implemented production RBAC hardening: protected APIs now require authenticated context by default, `X-Org-Id` / `X-User-Id` context is disabled unless explicitly configured, platform write APIs have method-level role restrictions, and focused RBAC regression coverage was added.
- 2026-06-08T12:06:00+08:00: Closed audit tracking readiness gaps from review: platform model provider/selected-model/route writes now log platform audit events without secrets, platform audit logs support 7-day DTO query/filter/redaction, organization audit search is database-filtered, and V62 adds audit query indexes.
- 2026-06-23T08:48:00+08:00: Fixed production-observed `/platform/audit/logs` initial-load failure by routing empty-keyword audit queries through a repository method without `LIKE :q`, avoiding PostgreSQL nullable parameter inference as `bytea`.
- 2026-06-23T10:12:00+08:00: Merged TASK-151 fix to `main`, released `2.1.2`, and verified production `/api/platform/audit/logs?limit=100` plus browser `/platform/audit` load successfully.

## Verification

- Initial `mvn -q -Dmaven.repo.local=.m2 -Dtest=RbacProductionReadinessIntegrationTest test` -> **blocked** because local PostgreSQL was not listening on `localhost:5432`.
- Started Docker Desktop, ran `docker compose up -d postgres`, and confirmed `cici-postgres` healthy.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=RbacProductionReadinessIntegrationTest test` in `backend/` -> **success**.
- First expanded regression rerun exposed persistent `agentcici_test` data pollution; rebuilt only the test database and reran the same suite.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=RbacProductionReadinessIntegrationTest,PlatformAuthIntegrationTest,AuthFlowIntegrationTest,PlatformGovernanceIntegrationTest,PlatformBillingConfigurationIntegrationTest,PlatformModelProviderIntegrationTest,AgentOpenApiIntegrationTest test` in `backend/` after rebuilding `agentcici_test` -> **success**.
- `git diff --check` -> **success**.
- `check-assignment.py` from the loaded `cc-aidev-guidelines-common` skill package -> **allowed** for representative TASK-151 files.
- `mvn -q -Dtest=AgentRunTraceIntegrationTest,RbacProductionReadinessIntegrationTest,PlatformGovernanceIntegrationTest,PlatformModelProviderIntegrationTest test` in `backend/` -> **success**.
- `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
- Browser desktop QA with Vite and mocked platform audit APIs -> **success** for `/platform/audit` structure, filters, redacted rows, and table fit (`tableWidth=926`, `wrapWidth=928`, no body horizontal overflow); screenshot capture timed out twice in the in-app browser after DOM/layout checks.
- `git diff --check` after audit readiness changes -> **success**.
- `check-assignment.py` after authorization expansion for audit/frontend/migration files -> **allowed**.
- `dev-login.py` from the loaded `cc-aidev-guidelines-common` skill package for TASK-151 audit-query fix files -> **allowed**.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=PlatformAuditServiceTest,PlatformGovernanceIntegrationTest test` in `backend/` -> **success**.
- `git diff --check` after the audit query fix -> **success**.
- `check-assignment.py` for the audit query fix files -> **allowed**.
- `git push origin main` after merge -> **success**.
- `./scripts/release-acr.sh --dry-run` -> **success**, next version `2.1.2`.
- Initial `./scripts/release-acr.sh` -> **failed before tag/image completion** because GitHub tag lookup reset and Docker Hub `eclipse-temurin:21-jre` metadata returned EOF; no release tag was created.
- `./scripts/release-acr.sh --version 2.1.2` -> **success** after locally tagging the previous backend image as the JRE base to bypass Docker Hub metadata EOF.
- Production deploy to ECS `47.97.119.160` -> **success**; backup directory `/opt/cici/backups/20260623-100637-before-2.1.2`.
- Production smoke -> **success** for six healthy containers, backend `/system/version` `2.1.2` / `06288ee6403b`, `https://x.agentcici.com/`, platform login, `/api/platform/skills`, `/api/platform/tools`, `/api/platform/audit/logs?limit=100`, org login/core APIs, and browser `/platform/audit`.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/tenant/TenantContextFilter.java`
- `backend/src/main/java/com/codehouse/ciciassistant/auth/security/PlatformRoleAuthorizationAspect.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/api/PlatformController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/api/PlatformTenantLifecycleController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/billing/api/PlatformBillingController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/model/api/PlatformModelProviderController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ops/domain/AuditLogRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ops/service/AuditService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/domain/PlatformAuditLogRepository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformAuditService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/platform/PlatformAuditServiceTest.java`
- `backend/src/main/resources/db/migration/V62__audit_log_query_indexes.sql`
- `backend/src/test/java/com/codehouse/ciciassistant/auth/RbacProductionReadinessIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/model/PlatformModelProviderIntegrationTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/platform/PlatformGovernanceIntegrationTest.java`
- `frontend/src/platform/pages/PlatformAuditPage.tsx`
- `frontend/src/styles.css`
- `docs/specs/FEAT-064-rbac-production-readiness.md`
- `.claw/assignments/TASK-151.yaml`
- `.claw/issue-list.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
