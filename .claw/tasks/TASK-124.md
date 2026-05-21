---
kind: task-status
version: 1
task_id: TASK-124
title: Platform tenant manual provisioning and lifecycle entry split
status: ready
assignee: MANAGER-001
branch: codex/TASK-124-platform-tenant-manual-provisioning
spec_path: docs/specs/FEAT-046-platform-tenant-manual-provisioning-and-lifecycle-entry.md
assignment_path: .claw/assignments/TASK-124.yaml
updated_at: 2026-05-21T04:35:00Z
updated_by: ai
---

# TASK-124 - Platform tenant manual provisioning and lifecycle entry split

## Scope

Define and then implement the platform-side adjustment where `/platform/tenants` becomes the tenant list entry page, each tenant gets a dedicated lifecycle detail route, the list page gains a manual "open new tenant" action, and all future new tenants adopt the shared 20-character `org` ID rule.

## Plan

1. Register task/spec/state files and confirm the current platform tenant flow, account model, and old org ID generation rule.
2. Land a feature spec that defines route split, modal-based manual provisioning, owner-account reuse logic, audit expectations, and the new ID format.
3. In a follow-up implementation pass, update backend creation logic and platform tenant routes/pages.
4. Run build, targeted integration tests, and desktop/mobile visual QA once code changes begin.

## Coordination

- Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on `TASK-124`.
- Keep unrelated dirty worktree changes intact.
- Do not expand this task into billing, subscription, or non-platform account features.

## Progress

- 2026-05-21T04:35:00Z: Completed context loading for `cc-aidev-guidelines-common` and `impeccable`, reviewed FEAT-024 / FEAT-010 / current platform tenant implementation / existing `AuthService.createOrg(...)`, and created FEAT-046 plus TASK-124 assignment/status files.

## Verification

- `identity-bootstrap`: `python3 /Users/owenmacbook/.agents/skills/cloudcc-aidev-guidelines-common/scripts/dev-login.py /Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/.claw --ssh-key /Users/owenmacbook/.ssh/id_ed25519_agentcici_pm --developer MANAGER-001 --git-username OwenZheng-Cloud --files .claw/current-status.md .claw/task-board.md .claw/assignments .claw/tasks docs/specs --no-cache --json` -> allowed.

## Notes

- This task is currently in the spec-first stage only; no frontend or backend implementation has been started yet.
- The new tenant ID rule must be shared across platform manual provisioning, `/auth/register`, and authenticated organization creation, while leaving historical org IDs unchanged.
