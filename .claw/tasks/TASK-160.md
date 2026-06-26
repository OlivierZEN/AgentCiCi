---
kind: task-status
task_id: TASK-160
status: done
updated_at: 2026-06-26T05:20:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-160.yaml
spec_path: docs/specs/FEAT-070-multitenant-isolation-security-test.md
---

# TASK-160 - Multitenant isolation security test

## Scope

- 对平台核心多租户数据面做全面隔离测试。
- 新增 focused backend integration test，覆盖两个真实注册组织之间的横向越权矩阵。
- 若发现安全缺陷，在同任务内修复并补充回归。

## Initial Plan

- 建立 FEAT-070 测试规格与 TASK-160 授权。
- 盘点已有 RBAC、tenant lifecycle、Agent、KB、OpenAPI 测试。
- 新增 `MultitenantIsolationIntegrationTest`。
- 覆盖认证切换、组织后台、Agent、会话状态、运行日志、知识库和平台接口边界。
- 运行 focused test、相关回归组合、backend compile、`git diff --check`、assignment check。

## Verification

- `mvn -q -Dtest=MultitenantIsolationIntegrationTest test` in `backend/` -> **success**。
- `mvn -q -Dtest=MultitenantIsolationIntegrationTest,ChatSessionStateServiceIntegrationTest,AgentDefinitionDeleteIntegrationTest,PlatformAuthIntegrationTest test` in `backend/` -> **success**。
- 测试覆盖两个真实注册组织之间的横向越权矩阵：组织切换、组织资料读取、Agent 列表/详情/删除、会话列表/消息/删除、同名 workbench session state、个人运行日志列表/详情、知识库/文档/chunk 读写删除、组织 token 与平台 token 边界。
- 测试发现并修复 `ResponseStatusException` 被全局异常处理器兜底成 HTTP 500 的问题；现在会按原始状态码返回，例如跨组织会话/运行日志访问返回 404。
- 未发现跨组织数据泄漏、误删或误改；被拒绝的跨组织删除后，目标组织 Agent、会话、文档、chunk 和 KB 状态均保持不变。

## Changed Files

- `docs/specs/FEAT-070-multitenant-isolation-security-test.md`
- `.claw/tasks/TASK-160.md`
- `.claw/assignments/TASK-160.yaml`
- `backend/src/test/java/com/codehouse/ciciassistant/security/MultitenantIsolationIntegrationTest.java`
- `backend/src/main/java/com/codehouse/ciciassistant/common/web/GlobalExceptionHandler.java`
