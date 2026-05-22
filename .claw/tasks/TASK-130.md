---
kind: task-status
version: 1
task_id: TASK-130
title: ACR release version governance and app version badge
status: done
assignee: MANAGER-001
owner_role: project-manager
branch: codex/local-uncommitted-feature-mr
spec_path: docs/specs/FEAT-052-acr-release-version-governance.md
assignment_path: .claw/assignments/TASK-130.yaml
updated_at: 2026-05-22T04:42:10Z
updated_by: MANAGER-001
---

# TASK-130 - ACR Release Version Governance And App Version Badge

## Scope

- Design and implement a single canonical version for ACR image pushes, Git tags, backend runtime metadata, and frontend UI display.
- Add a small version marker to authenticated left navigation areas.
- Update release/deploy documentation so future ACR pushes use the same process.

## Preflight

- Manager bootstrap `dev-login.py` with intended state/spec/script/frontend/backend files returned `allowed`.
- Run task-scoped `dev-login.py` for `TASK-130` before implementation edits.

## Changed Files

- `scripts/release-acr.sh`
- `scripts/deploy-acr.sh`
- `deploy/Dockerfile.backend`
- `deploy/Dockerfile.frontend`
- `deploy/docker-compose.acr.yml`
- `deploy/acr.env.example`
- `backend/src/main/java/com/codehouse/ciciassistant/system/HealthController.java`
- `backend/src/main/resources/application.yml`
- `frontend/src/shared/appVersion.ts`
- `frontend/src/shared/AppVersionBadge.tsx`
- `frontend/src/vite-env.d.ts`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/platform/PlatformShell.tsx`
- `frontend/src/styles.css`
- `frontend/src/assistant/cici-ui.css`
- `docs/production-release-runbook.md`
- `docs/specs/FEAT-052-acr-release-version-governance.md`
- `.claw/devops.md`

## Progress

- 2026-05-22T03:07:01Z: Opened TASK-130 and FEAT-052 for the user's release version governance request.
- 2026-05-22T04:42:10Z: Implemented the canonical ACR release script, backend `/system/version`, frontend build-time version badge, Docker/Compose metadata propagation, and production release runbook governance.
- 2026-05-22T10:45:33Z: Moved the previously local uncommitted feature work onto `codex/local-uncommitted-feature-mr` for Codeup review.

## Verification

- `identity`: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-130` on `main` with intended frontend/backend/deploy/script/docs/state files -> **allowed**.
- `impeccable-context`: loaded `PRODUCT.md` and `DESIGN.md`; FEAT-052 badge remains within the authenticated product register.
- `release-dry-run`: `./scripts/release-acr.sh --dry-run` -> **success**, generated `2.0.B3`, printed backend/frontend image tags, optional `latest`, image inspect commands, and Git tag commands without building, pushing, or tagging.
- `frontend`: `VITE_CICI_APP_VERSION=2.0.B3 npm run dev` for browser QA and `npm run build` -> **success**, with existing Vite chunk-size warning.
- `backend`: `mvn -q -Dmaven.repo.local=../.m2 -DskipTests compile` in `backend/` -> **success**.
- `scripts`: `bash -n scripts/release-acr.sh scripts/deploy-acr.sh` -> **success**.
- `browser`: Playwright desktop screenshots at 1365x900 with mocked auth/API data -> **success**; `/`, `/admin/kb`, and `/platform` all render `.app-version-badge` with `2.0.B3` at the left navigation bottom. Screenshots:
  - `output/playwright/feat052-assistant-version.png`
  - `output/playwright/feat052-admin-version.png`
  - `output/playwright/feat052-platform-version.png`
- `search`: targeted `rg` found no legacy production runbook filename, old production host, legacy cert names, or stale ACR variable-name references in current release guidance and FEAT-052 files.
- `diff`: `git diff --check` -> **success**.
- `state`: `validate-state.py .claw --json` -> **success**.
