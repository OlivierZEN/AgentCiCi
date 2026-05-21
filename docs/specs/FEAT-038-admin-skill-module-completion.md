---
kind: feature-spec
feature_id: FEAT-038
title: Admin Skill Module Completion And Optimization
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-116
related_decisions: FEAT-009, FEAT-014, FEAT-015, FEAT-028
related_issues: none
updated_at: 2026-05-19T02:18:19Z
updated_by: MANAGER-001
---

# FEAT-038 - 管理端技能模块补齐与优化

## Goal

把管理端技能模块从“核心闭环已可用”推进到“运营级可治理、可确认、可回归”的完成态。

本任务基于 2026-05-19 对 `/admin/skills`、Skill 后端服务、平台技能治理、版本导入导出和 Skill 内嵌声明式 API 运行时的评估结果。当前工程骨架已经较完整：技能分层、租户自定义技能 CRUD、标准技能只读配置、草稿/发布版本、导入导出、软删除、内嵌 API 编译与运行时注入、平台治理页均已存在。后续重点不是重写，而是补齐安全、确认流、产品体验、测试稳定性和治理审计。

## Current Verified Baseline

- `skill_definition` 已具备 `source_type`、`visibility`、`edit_policy`、`binding_policy`、`update_policy`、`current_published_version_id`、`latest_draft_version_id` 等分层治理字段。
- `skill_version` 已具备 `change_log`、`diff_summary`、`version_source`、`created_by`、`restore_visible`、`retention_state`、`restored_from_version_id` 和 package manifest 字段。
- 管理端 Skill API 已覆盖列表、新建、更新、版本列表、恢复、发布、删除影响分析、删除、导出、导入和导入创建。
- `SkillDefinitionService` 已限制平台标准技能只可配置启停，自定义技能可编辑、发布、恢复、删除。
- `SkillPackageService` 已能导出 universal-skill-package@1.0 zip，并支持导入 zip 后生成租户自定义技能草稿。
- `SkillApiToolService` 已能编译 runtime APIs、生成 Skill 私有 function schema、执行远程 HTTP API、校验 authRef 和记录调用审计。
- `SkillResolverService` 与 `ToolOrchestratorService` 已接入 Skill 私有 API 的激活态注入和执行前 allowed tool 校验。
- `/admin/skills` 与 `/admin/skills/:id/edit` 已具备列表、筛选、发布、版本、导入导出和内嵌 API 编辑入口。
- 平台侧 `/platform/skills` 已覆盖标准 Skill 模板版本、核心策略包、影响分析、发布与回滚。

## Gaps To Close

### P0 Security, Correctness, And Regression

1. **Export download authorization**
   - `GET /skills/exports/{exportId}/download` must require org admin authentication.
   - Export artifacts must be bound to org and actor, have an expiration policy, and reject cross-org or stale download attempts.
   - Download success/failure should be audit-visible enough for operations.

2. **Repeatable test baseline**
   - Fix `SkillGovernanceIntegrationTest` so it is idempotent against existing local seed data.
   - Avoid fixed skill codes that fail when tests are rerun against a non-empty local database.
   - Relax brittle assertions that assume platform standard skill names can never be updated by platform governance.
   - Keep or add focused coverage for standard skill edit blocking, custom skill publish/export/import/delete, retained pinned versions, runtime API injection, authRef, and high-risk confirmation blocking.

3. **Import confirmation and resource mapping**
   - Frontend must not immediately create a skill after import preview.
   - Add a blocking modal with `role="dialog"` and `aria-modal="true"` that shows parsed fields, warnings, unmatched tools/KBs, skill code conflicts, and editable draft fields before creation.
   - Backend should expose enough preview metadata for the modal to make safe decisions.

4. **Replace browser-native destructive prompts**
   - Replace `window.confirm` / `window.prompt` in skill delete and version restore flows with project-standard modal dialogs.
   - The delete modal must show `delete-impact` blockers, binding/pin status, reason input, and a clear destructive confirmation action.
   - The restore modal must show version source, changelog, diff summary, risk level, and explain that restore writes a draft only.

### P1 Product Completion

1. **High-risk runtime API confirmation**
   - Current high-risk Skill API execution returns `CONFIRMATION_REQUIRED`; add a real pending-action or confirmation token flow.
   - Confirmation must bind org, user, skill version, tool name, arguments hash, expiry, and audit trail.
   - Confirmed execution must not allow changed arguments or inactive skill context.

2. **Structured runtime API builder**
   - Replace or augment JSON-only API editing with structured controls: method, URL, authRef selector, timeout, risk level, confirmation requirement, parameter rows, request mapping, response mapping, and test validation.
   - Keep advanced JSON escape hatch only where useful.
   - Show host whitelist/authRef validation errors near the field that caused them.

3. **Version diff and restore preview**
   - Version management should show field-level changes for prompt, spec, tool whitelist, KB whitelist, output contract, risk, and runtime APIs.
   - Restore should preview impact before writing the draft.
   - Preserve the rule that restore never directly changes published runtime.

4. **Management action audit**
   - Add or verify audit records for create, save draft, publish, restore, delete, export, import preview, import create, runtime API compile failures, and runtime API confirmed execution.
   - Audit payloads must avoid secrets and raw sensitive business data.

### P2 Governance And Operations

1. **Export standardization policy**
   - Decide whether model standardization fallback is allowed.
   - If fallback remains allowed, label the artifact clearly as deterministic/offline standardized, not model-standardized.
   - If strict model standardization is required, fail export when the model route is unavailable or schema validation fails.

2. **Platform and tenant skill consistency**
   - Ensure tenant admin standard skill details clearly show read-only source, current platform template version, and why edit/publish/delete/export are unavailable.
   - Keep derivation hidden unless a future spec reopens template diff/merge/upgrade flows.

3. **Visual and accessibility hardening**
   - All changed admin product UI must follow `PRODUCT.md`, `DESIGN.md`, `DESIGN.json`, and AGENTS.md `鎏金账房` rules.
   - Modal close controls must be bare `×` glyphs, footer actions unified, and product-panel tabs/text actions must avoid button chrome.
   - Verify desktop and 390px mobile screenshots for changed `/admin/skills` routes.

## Out Of Scope

- TASK-112 Open API parity files unless MANAGER-001 updates both assignments.
- TASK-114 billing usage ledger files or migration `V53__billing_usage_ledger.sql`.
- TASK-115 knowledge-base maintenance files or migration `V54__kb_module_maintenance.sql` unless explicitly coordinated.
- Reopening tenant-derived skill creation as a product feature. First complete standard/custom skill governance.
- New billing or pricing behavior for skills.

## Suggested Implementation Order

1. Fix export download authorization and test idempotency.
2. Replace delete/restore browser-native prompts with accessible modals.
3. Add import preview confirmation modal and backend metadata gaps.
4. Implement high-risk runtime API confirmation flow.
5. Improve runtime API builder and version diff/restore preview.
6. Add audit coverage and visual QA screenshots.

## Acceptance Criteria

- Export downloads require authenticated org admin context and reject unauthorized/stale artifacts.
- Skill governance integration tests can be rerun locally without fixed-code collisions.
- Import zip flow stops at preview and requires explicit confirmation before skill creation.
- Delete and restore flows use project-standard modal dialogs, not `window.confirm` or `window.prompt`.
- High-risk runtime API tools can complete only through a bound confirmation flow; unconfirmed or tampered calls are blocked.
- Runtime API editing is understandable without editing raw JSON for the common path.
- Version restore shows enough impact context for an administrator to make a safe decision.
- Relevant backend tests, frontend build, and visual desktop/mobile checks are recorded in `.claw/tasks/TASK-116.md`.
