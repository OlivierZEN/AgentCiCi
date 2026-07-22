---
kind: task-status
task_id: TASK-170
status: implemented
updated_at: 2026-07-06T16:55:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-170.yaml
spec_path: docs/specs/FEAT-080-security-rules-platform.md
---

# TASK-170 - 安全规则平台与输入输出安全网关

## Scope

- 补齐客户要求中的安全规则平台能力，并达到生产就绪标准。
- 实现敏感信息识别脱敏、敏感词库维护、内容审核分类、prompt injection 检测、输入输出安全网关。
- 新增 `/admin/security-rules` 管理入口和 `/security-rules/*` 管理 API。
- 接入审计、聊天输入输出、工具调用和知识/RAG 上下文关键链路。

## Initial Findings

- 现有 RBAC、审计、工具白名单和部分安全 prompt/评测门禁已存在。
- `docs/security-and-compliance-checklist.md` 明确列出 PII masking、role-based tool allowlist、prompt injection detector 和 context sanitization 为待补项。
- 安全能力应作为统一服务和网关落地，避免散落在 prompt 或单个控制器中。

## Implementation Plan

- 创建 FEAT-080、TASK-170 和授权边界。
- 新增 V84 安全规则、检测事件、复核项数据模型（合并时按最新主线迁移时间线重编号）。
- 实现 `SensitiveDataDetector`、`SecurityRuleEngine`、`PromptInjectionDetector`、`SafetyGatewayService`。
- 接入 `AuditService` / `PlatformAuditService` 脱敏。
- 接入聊天输入输出、工具调用、RAG/tool 上下文安全检查。
- 新增管理 API 和 `/admin/security-rules` 页面。
- 增加后端集成测试、前端构建、桌面端 Playwright 验证。

## Verification

- `dev-login.py` for `MANAGER-001` setup files -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-170` representative files -> allowed.
- `check-assignment.py` for TASK-170 representative spec, state, security migration, backend security service, and admin frontend files -> allowed.
- `git diff --check` -> success for TASK-170 setup files.
- TDD RED: `mvn -q -Dtest=SecurityRedactionServiceTest,SafetyGatewayServiceTest,SecurityRulesServiceTest,AuditServiceSecurityTest test` -> failed as expected before security module implementation because target classes did not exist.
- Focused GREEN: `mvn -q -Dtest=SecurityRedactionServiceTest,SafetyGatewayServiceTest,SecurityRulesServiceTest,AuditServiceSecurityTest,PlatformAuditServiceTest,ChatOrchestratorServiceModelIdentityTest test` in `backend/` -> success.
- Backend compile/package: `mvn -q -DskipTests package` in `backend/` -> success.
- Frontend dependencies: `npm ci` in `frontend/` -> success.
- Frontend build: `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- Desktop UI verification: Playwright with mocked `/security-rules/*` and `/auth/me` APIs opened `http://127.0.0.1:5174/admin/security-rules`, ran the rule test, switched to events, and captured `output/playwright/task170-security-rules-desktop.png` -> success.
- Final static check: `git diff --check` -> success.
- Full backend `mvn -q test` was attempted but blocked by local shared test database Flyway validation: applied migration `V70` exists in `agentcici_test` but is not present in this isolated security worktree based on `origin/main`; this is an environment/branch-state mismatch rather than a TASK-170 compile or focused regression failure.

## Changed Files

- `docs/specs/FEAT-080-security-rules-platform.md`
- `.claw/tasks/TASK-170.md`
- `.claw/assignments/TASK-170.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/src/main/resources/db/migration/V84__security_rules_platform.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/security/**`
- `backend/src/main/java/com/codehouse/ciciassistant/ops/service/AuditService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformAuditService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ToolOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/security/**`
- `backend/src/test/java/com/codehouse/ciciassistant/ops/AuditServiceSecurityTest.java`
- `frontend/src/admin/pages/AdminSecurityRulesPage.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/App.tsx`
- `frontend/vite.config.js`
- `frontend/vite.config.ts`

## Handoff

- Branch/worktree for implementation: `codex/TASK-170-security-rules-platform`.
- Isolated worktree: `/Users/owenmacbook/.config/superpowers/worktrees/cc-codeup-agentcici_PM/codex-TASK-170-security-rules-platform`.
- Production-readiness scope implemented: sensitive data detection/redaction, custom sensitive lexicon CRUD/test, content moderation classification, prompt injection detection, input/output safety gateway, audit persistence redaction, chat/RAG/tool runtime integration, detection event review UI/API.
