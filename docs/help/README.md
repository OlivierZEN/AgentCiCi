# AgentCiCi Help Center Content

This directory is the durable handoff point for customer-facing help content.

The current MVP is implemented as structured frontend data in `frontend/src/help/helpContent.ts` so the product can ship quickly without a CMS. Future work can move each document into Markdown or a CMS import pipeline, but the public URL slugs should stay stable.

## MVP Document Set

- `getting-started/what-is-agentcici`
- `getting-started/accounts-roles`
- `user-workbench/overview`
- `user-workbench/knowledge-selection`
- `admin/models/providers`
- `admin/agent-builder/overview`
- `admin/skills/create`
- `openapi/quickstart`
- `openapi/api-keys`
- `admin/ops/run-logs`
- `admin/wechat-kf/setup`
- `user-workbench/meeting-minutes`
- `troubleshooting/kb-not-hit`
- `troubleshooting/openapi-errors`
- `security/api-key`
- `changelog`

## Writing Rules

- Write for product users, administrators, operators, and developers, not for internal implementation review.
- Keep every document anchored to an actual product entry such as `/`, `/admin/kb`, `/admin/agent-builder`, `/platform/audit`, or `/openapi/*`.
- Include role, entry, prerequisites, steps, result validation, safety notes, common errors, and related documents.
- Use placeholder API keys, tokens, trace IDs, customer IDs, and URLs in examples.
- Keep URL slugs in English and titles in Chinese.
- Mark planned or UAT-only behavior explicitly before publishing it as generally available.

## Implementation Notes

- Public app route: `/help/*`.
- Dedicated host route: `help.agentcici.com/*`.
- The Nginx config keeps `help.agentcici.com` as a pure SPA host so documentation paths such as `/openapi/quickstart` are not proxied to backend Open API endpoints.
