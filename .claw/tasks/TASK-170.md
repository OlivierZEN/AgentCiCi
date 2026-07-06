---
kind: task-status
task_id: TASK-170
status: in_progress
updated_at: 2026-07-06T16:20:00+08:00
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
- 新增 V71 安全规则、检测事件、复核项数据模型。
- 实现 `SensitiveDataDetector`、`SecurityRuleEngine`、`PromptInjectionDetector`、`SafetyGatewayService`。
- 接入 `AuditService` / `PlatformAuditService` 脱敏。
- 接入聊天输入输出、工具调用、RAG/tool 上下文安全检查。
- 新增管理 API 和 `/admin/security-rules` 页面。
- 增加后端集成测试、前端构建、桌面端 Playwright 验证。

## Verification

- `dev-login.py` for `MANAGER-001` setup files -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-170` representative files -> allowed.
- `check-assignment.py` for TASK-170 representative spec, state, V71 migration, backend security service, and admin frontend files -> allowed.
- `git diff --check` -> success for TASK-170 setup files.

## Changed Files

- `docs/specs/FEAT-080-security-rules-platform.md`
- `.claw/tasks/TASK-170.md`
- `.claw/assignments/TASK-170.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- Branch/worktree for implementation: `codex/TASK-170-security-rules-platform`.
- Isolated worktree: `/Users/owenmacbook/.config/superpowers/worktrees/cc-codeup-agentcici_PM/codex-TASK-170-security-rules-platform`.
