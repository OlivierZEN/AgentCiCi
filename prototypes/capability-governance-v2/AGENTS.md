# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.

## Confirmed Product Direction

- This prototype must use progressive disclosure. Never keep the list, full editor, version history, dependency impact, preview, and risk inspector permanently visible in one canvas.
- The primary flow is list page → right-side read-only detail drawer → dedicated edit/version/dependency/preview route.
- A list page is for scanning and filtering. A drawer is for low-risk summary inspection. Editing and high-risk governance actions use focused pages with breadcrumbs and clear return paths.
- Match the authenticated operations console and CRM Standard Blue visual language from the supplied screenshots. Do not redesign the brand shell.
