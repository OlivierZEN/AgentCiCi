---
kind: feature-spec
feature_id: FEAT-057
title: OpenAPI docs copy cleanup
status: approved
owner_role: frontend-agent
task_ids: TASK-138
related_decisions: none
related_issues: R20260527-5R8TN / R20260527-3M7KP
updated_at: 2026-05-27T03:32:12Z
updated_by: MANAGER-001
---

# FEAT-057 - OpenAPI Docs Copy Cleanup

## Metadata

- source_feedback: `R20260527-5R8TN`, `R20260527-3M7KP`
- status: `ready-for-implementation`
- owner_role: `frontend-agent`
- created_at: 2026-05-27
- task: `TASK-138`

## Problem

The OpenAPI documentation surface repeats authentication guidance in multiple places, and the CloudCC sending-message example uses names that should be clearer for external implementers.

## Goals

- Remove the duplicated authentication explanation from the OpenAPI documentation where it repeats the lower example or dedicated auth section.
- In the sending-message module, rename `CLOUDCC_PAGE_TOKEN` to `CLOUDCC_OPENAPI_TOKEN`.
- Add help links for obtaining CloudCC `accessToken` and `baseUrl`.
- Keep the documentation concise and developer-oriented.

## Non Goals

- No backend API behavior change.
- No OpenAPI endpoint route migration, which is tracked separately by `FEAT-058`.
- No broad redesign of the docs page.

## Design

### Copy Changes

Update the OpenAPI docs dialog and standalone docs page content source so:

- Authentication guidance appears in one canonical place.
- Sending-message examples use `CLOUDCC_OPENAPI_TOKEN`.
- CloudCC `accessToken` guidance links to `https://help.cloudcc.cn/product03/sdkcan-kao/#getopenapitoken`.
- CloudCC `baseUrl` guidance links to `https://help.cloudcc.cn/product03/apigai-lan/#1接口联调说明`.

### Affected Surfaces

Likely files:

- `frontend/src/assistant/AgentOpenApiDocsDialog.tsx`
- `frontend/src/admin/pages/AdminAgentOpenApiDocsPage.tsx`
- `frontend/src/help/helpContent.ts`, if the help center repeats the same examples.
- Existing specs may be updated only if implementation changes the durable API docs truth.

## Acceptance Criteria

- The OpenAPI docs no longer show repeated authentication explanation blocks.
- No visible example uses `CLOUDCC_PAGE_TOKEN`.
- The sending-message example uses `CLOUDCC_OPENAPI_TOKEN`.
- The CloudCC token and base URL help links are visible near the relevant CloudCC context explanation.
- `npm run build` passes.

## Verification Plan

- Targeted `rg` for `CLOUDCC_PAGE_TOKEN` returns no product docs/UI matches.
- Targeted `rg` confirms `CLOUDCC_OPENAPI_TOKEN` appears in the updated sending-message docs.
- Desktop browser smoke opens the OpenAPI docs page or dialog and checks copy presence.
- `git diff --check`.

## Handoff Notes

- This task is intentionally smaller than the endpoint-route change. If `TASK-140` lands first, this docs cleanup should adopt the new route examples from `FEAT-058`.
