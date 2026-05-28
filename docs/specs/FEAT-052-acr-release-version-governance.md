---
kind: feature-spec
feature_id: FEAT-052
title: ACR release version governance
status: implemented
owner_role: project-manager
task_ids: TASK-130
related_decisions: none
related_issues: none
updated_at: 2026-05-28T11:28:59Z
updated_by: MANAGER-001
---

# FEAT-052 - ACR Release Version Governance

## Background And Goal

AgentCiCi already ships backend and frontend images to ACR, but the release version is still partly manual: image tags, Git tags, build metadata, and the user-visible program version can drift. Each ACR image push should now create one release version and reuse that exact value everywhere.

## Version Rule

- Canonical release version: one Docker-compatible string following the production or test-release rules below.
- Production versions use three numeric segments with no letters, for example `2.0.1`.
- Production auto-generation increments the latest production Git tag by one patch version. Each segment has a maximum value of `12`; when the third segment reaches `12`, the next version carries into the second segment and resets the third segment to `1`. For example `2.0.12` becomes `2.1.1`.
- Test versions are based on the latest production version and append `-beta.<n>`, for example `2.0.1-beta.1`. Multiple test releases on the same production baseline increment only the beta counter.
- Source at release time: `scripts/release-acr.sh` generates or accepts the canonical version before building anything.
- Same value must be used for:
  - backend image tag: `cici-backend:<version>`
  - frontend image tag: `cici-frontend:<version>`
  - Git annotated tag: `<version>`
  - frontend program version: `VITE_CICI_APP_VERSION=<version>`
  - backend runtime version: `CICI_APP_VERSION=<version>`
  - deployment env: `CICI_IMAGE_TAG=<version>` and `CICI_APP_VERSION=<version>`
- `latest` may be pushed as a convenience alias, but it is never the release identifier and must not be used for rollback decisions.

## Release Flow

1. Confirm the worktree and intended release scope.
2. Generate the next version:
   - If `RELEASE_VERSION` or `CICI_RELEASE_VERSION` is supplied, use it.
   - Otherwise use production channel by default, read existing numeric production Git tags, and increment the latest version.
   - Use `--channel test` or `--beta` to generate the next beta version from the latest production Git tag.
   - If no production tag exists, the default initial production baseline is `2.0.1`.
3. Build backend with `CICI_APP_VERSION` and `GIT_COMMIT` embedded.
4. Build frontend with `VITE_CICI_APP_VERSION` embedded.
5. Build and push ACR backend/frontend images using the canonical version and optional `latest` alias.
6. Inspect pushed images to verify the tags exist.
7. Create the Git annotated tag with the same canonical version.
8. Push the Git tag when `PUSH_GIT_TAG=true`.
9. Deploy by setting `CICI_IMAGE_TAG=<version>` and `CICI_APP_VERSION=<version>` in `deploy/acr.env`.

## User Visible Behavior

- After login, authenticated product surfaces show a small version label in the left navigation bottom area.
- Surfaces covered in this feature:
  - `/` assistant workbench
  - `/admin/*` organization console
  - `/platform/*` operations console
- Login pages do not show the version label.
- The visible label uses the exact frontend build version and remains compact enough not to compete with navigation.

## Interface And Data Impact

- No database migration.
- Frontend reads build-time `import.meta.env.VITE_CICI_APP_VERSION`.
- Backend exposes the same runtime version through `/system/version` for internal smoke and diagnostics.
- Docker images receive OCI version/revision labels.
- Compose passes `CICI_APP_VERSION` into the backend service and keeps it aligned with the deploy tag through `scripts/deploy-acr.sh`.

## Acceptance Criteria

- Running `scripts/release-acr.sh --dry-run` prints the next version and all intended tags without pushing.
- Real release runs fail before push if the worktree is dirty, unless `ALLOW_DIRTY_RELEASE=true`.
- The same version string appears in backend image tag, frontend image tag, Git tag, frontend UI label, and backend `/system/version`.
- `frontend npm run build` succeeds with the version badge.
- Backend compile succeeds after the version endpoint change.
- `git diff --check` and `.claw` validation pass.

## Risks And Rollback

- Risk: an operator still manually pushes `latest`; mitigation is to document `latest` as non-authoritative and keep release script as the only blessed ACR push path.
- Risk: static frontend displays an old version after a partial backend-only hotfix; mitigation is to treat each ACR push as a full release unless explicitly documented as a backend-only emergency with a matching Git tag.
- Rollback: set `CICI_IMAGE_TAG` and `CICI_APP_VERSION` back to the previous canonical version, then restart compose.

## Implementation Progress

- TASK-130 opened to implement the release script, runtime version metadata, and left-bottom product version label.
- TASK-130 completed the canonical release script, backend runtime version endpoint, authenticated product-surface version badge, Docker/Compose version metadata, and production release runbook updates.

## Handoff Notes

- Canonical production release flow is now `docs/production-release-runbook.md` plus `scripts/release-acr.sh`.
- Use `./scripts/release-acr.sh --dry-run` before any real release; real release runs must keep the generated version aligned across ACR tags, Git tag, frontend badge, backend `/system/version`, and `deploy/acr.env`.
- Do not create another version source in `package.json` or `pom.xml`; those remain package/build metadata, not release identity.
