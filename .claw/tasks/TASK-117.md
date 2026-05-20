---
kind: task-status
task_id: TASK-117
assignee: MANAGER-001
status: done
branch: codex/TASK-117-agentcici-help-center-site
pr_url: n/a
spec_path: docs/specs/FEAT-039-agentcici-help-center-site.md
assignment_path: .claw/assignments/TASK-117.yaml
updated_at: 2026-05-19T04:34:12Z
updated_by: MANAGER-001
---

# TASK-117 AgentCiCi Help Center Site

## Scope

Own FEAT-039 end to end:

- help center information architecture for `help.agentcici.com`
- documentation navigation and URL slug structure
- MVP documentation content plan
- product-register visual direction aligned with `鎏金账房`
- optional frontend implementation for the help center shell when requested
- search, navigation, mobile reading, Open API docs, and troubleshooting documentation patterns
- deployment notes for `help.agentcici.com` if the site is implemented

## Out Of Scope

- Rewriting existing AgentCiCi runtime features.
- Replacing developer-facing `docs/specs/` with customer help docs.
- Editing active TASK-112, TASK-114, TASK-115, or TASK-116 implementation files unless the manager explicitly expands both assignments.

## Preflight

Before implementation edits, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-117-agentcici-help-center-site`.

## Verification Target

- Help center spec remains aligned with `PRODUCT.md`, `DESIGN.md`, and current routes.
- If UI is implemented, frontend build passes.
- If UI is implemented, desktop and 390px mobile screenshots are reviewed for readability, navigation, search, and horizontal overflow.
- `.claw` state validation passes after assignment or handoff updates.

## Assignment History

- 2026-05-19T12:20:18+08:00: User assigned FEAT-039 to Owen; task owner recorded as `MANAGER-001`.
- 2026-05-19T12:34:12+08:00: Implemented the help center MVP on `codex/TASK-117-agentcici-help-center-site`.

## Completed Work

- Added `frontend/src/help/HelpCenterApp.tsx` and `frontend/src/help/help-center.css` for the product-register help center shell.
- Added `frontend/src/help/helpContent.ts` with structured navigation, role entrypoints, search aliases, related docs, and 16 MVP documents.
- Updated `frontend/src/App.tsx` so `/help/*` serves the help center and `help.agentcici.com/*` uses the same SPA shell from the domain root.
- Added `docs/help/README.md` as the durable content maintenance handoff point.
- Updated `deploy/nginx.cici.ssl.conf` with a dedicated `help.agentcici.com` HTTPS server and SPA fallback.
- Updated `docs/specs/FEAT-039-agentcici-help-center-site.md` from planning-only draft to implemented MVP record.

## Verification Evidence

- `identity`: `dev-login.py` for `MANAGER-001` / `TASK-117` on branch `codex/TASK-117-agentcici-help-center-site` with intended help center files -> `allowed`.
- `frontend`: `npm run build` in `frontend/` -> success, with existing Vite chunk-size warning.
- `desktop`: Playwright CLI screenshot `output/playwright/help-center-desktop.png`; `/help` at 1440px has no horizontal overflow, left navigation and home sections render correctly.
- `mobile`: Playwright CLI screenshot `output/playwright/help-center-mobile-openapi.png`; `/help/openapi/quickstart` at 390px has `documentElement.scrollWidth=390`.
- `search`: Playwright CLI typed `401`; search returned 3 results and first result was `Open API 401 / 403 / 429`.
- `mobile-nav`: Playwright CLI verified mobile `目录` button opens the navigation overlay.
