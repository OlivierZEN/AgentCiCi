---
kind: task-status
task_id: TASK-116
assignee: DEV-wolong
status: ready
branch: codex/TASK-116-skill-module-completion
pr_url: n/a
spec_path: docs/specs/FEAT-038-admin-skill-module-completion.md
assignment_path: .claw/assignments/TASK-116.yaml
updated_at: 2026-05-19T02:18:19Z
updated_by: MANAGER-001
---

# TASK-116 Skill Module Completion And Optimization

## Scope

Complete and optimize the admin skill module from the current working baseline. Current verified baseline:

- skill layering fields and standard/custom governance are implemented
- tenant custom skills support create, save draft, publish, restore, export, import, delete impact, and soft delete
- platform standard skills are tenant-visible but tenant-managed body editing is blocked
- platform core policy and platform skill template governance exist in `/platform/skills`
- runtime API draft/snapshot data and `skill_api_tool` compile/runtime execution exist
- Skill private APIs are injected only from resolved active skill context
- file-backed builtin skill metadata and docs summary exist

## Development Package

### P0 Security And Regression

- Add authentication, org ownership, actor ownership, expiry, and audit visibility to skill export package downloads.
- Make `SkillGovernanceIntegrationTest` and related skill tests repeatable against an existing local database.
- Replace fixed skill-code test collisions with generated or cleanup-safe fixtures.
- Replace brittle standard-skill name assertions with behavior assertions.
- Add import preview confirmation UI before creating imported skills.
- Replace skill delete and version restore `window.confirm` / `window.prompt` flows with accessible modal dialogs.

### P1 Completion

- Implement a real confirmation flow for high-risk Skill runtime APIs.
- Improve runtime API editor UX with structured fields for method, URL, authRef, timeout, risk, parameters, request mapping, response mapping, and validation.
- Add version diff and restore impact preview across prompt, spec, tools, KBs, output contract, risk, and runtime APIs.
- Add or verify management audit events for create, save draft, publish, restore, delete, export, import preview, import create, runtime API compile, and confirmed runtime API execution.

### P2 Optimization

- Clarify export standardization fallback policy and surface it in user-visible export job metadata.
- Improve standard skill detail read-only explanations and platform template version visibility.
- Keep tenant derivation hidden unless a future confirmed spec reopens it.
- Tighten mobile/desktop visual polish for `/admin/skills` and skill editor routes.

## Out Of Scope

- Open API parity files owned by TASK-112 unless MANAGER-001 expands both assignments.
- Billing usage ledger files and migration `V53__billing_usage_ledger.sql` owned by TASK-114 unless MANAGER-001 expands both assignments.
- Knowledge-base maintenance files and migration `V54__kb_module_maintenance.sql` owned by TASK-115 unless MANAGER-001 expands both assignments.
- New billing/pricing behavior for skills.

## Preflight

Before editing, run task-scoped `dev-login.py` for `DEV-wolong` on branch `codex/TASK-116-skill-module-completion`.

## Verification Target

- `mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest,FileBackedBuiltinSkillIntegrationTest test`
- Additional focused backend tests for export download authorization and high-risk runtime API confirmation
- `npm run build` when frontend changes are made
- Desktop and 390px mobile screenshots for changed `/admin/skills` routes
- `.claw` state validation passes after handoff updates

## Known Initial Test Finding

The 2026-05-19 assessment run of:

`mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest,FileBackedBuiltinSkillIntegrationTest test`

failed with 3 failures in `SkillGovernanceIntegrationTest` because local seeded skill data already contained fixed test skill codes and platform governance had changed the display name of `general-assistant`. Treat this as part of P0 test-idempotency work.

## Manager Verification

- `dev-login.py` for `MANAGER-001` returned `allowed`.
- `check-assignment.py` for `DEV-wolong` / `TASK-116` returned `allowed` for representative skill backend, migration, admin frontend, spec, task-status, and `backend/pom.xml` paths.
- `.claw` `validate-state.py` passed.
- `.claw/team-status.md` was refreshed from source records.

## Assignment History

- 2026-05-19T10:18:19+08:00: User asked to assign overall skill module completion and optimization to `DEV-wolong`.
