---
updated_at: 2026-05-18T23:20:36Z
status: active
feature_id: FEAT-037
related_specs:
  - docs/specs/FEAT-003-saas-billing-and-packaging.md
  - docs/specs/FEAT-022-agent-workload-billing-model.md
---

# FEAT-037 SaaS Billing Usage Ledger

## Goal

Implement the first production-shaped SaaS billing foundation for AgentCiCi:

- package plans and organization subscriptions
- immutable usage meter events
- work-credit rating
- credit ledger entries
- quota pre-check hooks
- admin and platform billing surfaces

This feature turns FEAT-003 and FEAT-022 from product direction into an executable implementation plan. The first release is not a payment processor or tax invoice system. It is the trustworthy billing facts layer that future payment, renewal, contract, top-up, and finance workflows can rely on.

## Relationship To Earlier Billing Specs

FEAT-037 does not replace FEAT-003 or FEAT-022.

- `FEAT-003 SaaS Billing And Packaging` remains the upstream commercial model and packaging source of truth. It defines organization-level billing, platform subscription, seats, resource usage, add-ons, overage posture, and SaaS packaging.
- `FEAT-022 Agent Workload Billing Model` refines the usage layer from FEAT-003 into the customer-facing `work credits` / `智能体工作量` model.
- `FEAT-037 SaaS Billing Usage Ledger` is the first engineering delivery spec for those decisions. It defines schema, services, APIs, UI surfaces, quota hooks, and task execution boundaries.

If FEAT-037 appears to conflict with FEAT-003 on commercial packaging, FEAT-003 wins and FEAT-037 should be corrected. If FEAT-037 appears to conflict with FEAT-022 on work-credit naming or customer-facing usage language, FEAT-022 wins and FEAT-037 should be corrected.

## Product Position

Use this external commercial model:

```text
platform subscription + paid operation/build seats + work credits + enterprise add-ons
```

Do not make token usage the first-level customer-facing billing concept. Tokens, embeddings, vector storage, and third-party API cost stay as internal cost and expanded detail. Customer-facing product copy should use `智能体工作量`, `工作量额度`, and `credits`.

## Billing Subject

The billing subject is always the organization.

- A personal user is never the direct billing subject.
- API keys, external users, channels, and credentials are attribution dimensions under an organization.
- Cost attribution must support organization, user, agent, API key, channel, external user, and billable domain.

## Architecture

### 1. Usage Events

`usage_meter_event` is the immutable fact table. It answers: what happened in the system?

Events are append-only. Corrections must be written as new rating or ledger adjustments, not by rewriting the source event.

Required fields:

- `id`
- `org_id`
- `user_id`
- `agent_id`
- `session_id`
- `credential_id`
- `external_user_id`
- `billable_domain`
- `billable_item_code`
- `quantity`
- `unit`
- `work_credit_quantity`
- `model_provider`
- `model_name`
- `model_tier`
- `is_platform_paid`
- `source_type`
- `source_id`
- `idempotency_key`
- `occurred_at`
- `rated_at`
- `plan_code`
- `rate_card_version`
- `metadata_json`

Use `idempotency_key` to deduplicate retries and replays. Recommended keys:

- chat turn: `chat:{sessionId}:{messageId}`
- model call: `model:{traceId}:{phase}:{attempt}`
- RAG retrieval: `rag:{traceId}:{retrievalId}`
- tool call: `tool:{traceId}:{toolCallId}`
- Open API request: `openapi:{credentialId}:{idempotencyKey|requestId}`
- workflow execution: `workflow:{executionId}`

### 2. Rating

Rating converts source events into work credits. It answers: how much billable work did this event represent?

Rating is deterministic for a given `rate_card_version`. If pricing changes, keep the old version for historical events.

First-phase domains:

| Domain | Source | First release behavior |
| --- | --- | --- |
| `assistant_chat` | `ChatOrchestratorService` / chat trace | accepted user turn base credits |
| `model_usage` | model client usage fields | token envelope and model tier credits |
| `rag_retrieval` | `RagService.retrieveContext()` | retrieval count credits |
| `tool_call` | `ToolOrchestratorService` | read/write/platform-paid tool credits |
| `workflow_run` | `AgentWorkflowExecutionLogEntity` | execution-level credits only |
| `open_api_chat` | `agent_api_call_log` | accepted Open API request credits |
| `kb_indexing` | KB indexing lifecycle | document/chunk/MB credits |

Do not enable workflow node-level billing until the workflow runtime has real node execution facts. Current execution logs are enough for workflow-run level billing only.

### 3. Ledger

`billing_credit_ledger` is the financial work-credit book. It answers: what happened to the customer's credit balance?

Ledger entries are append-only. Examples:

- `included_grant`: monthly or annual included credits
- `top_up_grant`: purchased top-up package
- `usage_debit`: rated usage consumes credits
- `adjustment_credit`: support or finance credit
- `adjustment_debit`: manual correction
- `reversal_credit`: refund for a prior usage debit
- `expiration_debit`: unused credits expire

Every usage debit should reference the rated usage event or rating batch. Refunds and corrections should reference the original debit where possible.

### 4. Plans And Subscriptions

Plans define capability and allowance. Subscriptions bind a plan to an organization.

First-phase plans:

- `trial`: low credits, restricted Open API, one real pilot.
- `team`: department pilot, basic knowledge and common connectors.
- `business`: production usage, Open API, billing views, trace and attribution.
- `enterprise`: contract terms, SSO/SLA/private deployment options, custom credit pool.

Subscription states:

- `trialing`
- `active`
- `past_due`
- `paused`
- `canceled`

Plan configuration must include:

- included credits
- billing period
- rollover policy
- top-up policy
- overage mode per domain
- seat package limits
- feature flags
- audit and data retention limits
- Open API production access flag

### 5. Quota Enforcement

Use three enforcement layers:

1. Request pre-check: subscription active, feature allowed, hard limit not exceeded.
2. Reservation for high-cost actions: reserve estimated credits, settle actual usage after execution.
3. Async rating and ledger write: source events are rated and debited in the background.

Overage modes:

| Mode | Behavior | Default use |
| --- | --- | --- |
| `auto_charge` | continue and bill overage | enterprise Open API and contract accounts |
| `soft_limit` | warn but allow | assistant chat and normal RAG |
| `hard_limit` | block new high-cost work | premium models, third-party paid tools, bulk indexing |

Platform errors that produce no useful business result should be non-billable or reversed. User cancellation after completed model/tool work can bill completed portions.

## Backend API Shape

### Organization Admin APIs

- `GET /admin/billing/overview`
- `GET /admin/billing/usage-events`
- `GET /admin/billing/ledger`
- `GET /admin/billing/subscription`
- `GET /admin/billing/quota`

These APIs show the current organization only.

### Platform APIs

- `GET /platform/billing/plans`
- `POST /platform/billing/plans`
- `GET /platform/billing/subscriptions`
- `PUT /platform/billing/subscriptions/{orgId}`
- `GET /platform/billing/usage-events`
- `GET /platform/billing/ledger`
- `POST /platform/billing/ledger-adjustments`

Platform APIs require platform authorization and must support organization filtering.

## Frontend Surfaces

### `/admin/billing`

Organization admins need a dense account-level view:

- current plan and subscription status
- remaining credits and period dates
- usage by domain
- recent ledger entries
- recent usage events
- quota warning states

Follow `鎏金账房`: compact tables, warm ivory surfaces, gold linework for active state, no marketing metrics, no oversized cards, no nested background boxes inside panels.

### `/platform/billing`

Platform operators need:

- plan list and feature flags
- organization subscription list
- organization usage lookup
- ledger adjustment workflow
- rating version and quota policy visibility

Any adjustment must require an explicit reason. Do not silently rewrite historical entries.

## Implementation Phases

### Phase 1: Billing Foundation

- Add billing schema migration.
- Add billing domain entities, repositories, DTOs, and services.
- Seed default plans and demo subscription if absent.
- Add organization admin read APIs.
- Add platform read APIs for plans, subscriptions, usage, and ledger.
- Add deterministic sample/demo data only through controlled service methods or tests, not random UI fixtures.

### Phase 2: Product UI

- Add `/admin/billing` route and navigation entry.
- Add `/platform/billing` route and navigation entry.
- Implement responsive desktop/mobile tables without horizontal overflow.
- Use real APIs with clear loading, empty, and error states.

### Phase 3: Runtime Metering

- Capture real model usage from `AliyunBailianClient` and dynamic provider paths.
- Emit events from chat, Open API, RAG, tool, workflow, and KB indexing sources.
- Deduplicate by `idempotency_key`.
- Rate events into credits and write ledger debits.

### Phase 4: Quota Controls

- Add request-level pre-check for high-cost domains.
- Add soft-limit warnings to admin billing overview.
- Add hard-limit enforcement for premium models, paid third-party tools, bulk indexing, and production Open API where configured.

## Delivery Task

### TASK-114 FEAT-037 SaaS billing usage ledger

Owner: `DEV-nezha`

Scope:

- billing package domain model
- `V53__billing_usage_ledger.sql`
- plan, subscription, usage event, rate card, quota, and credit ledger services
- admin and platform billing APIs
- `/admin/billing` and `/platform/billing` product UI
- runtime metering hooks for chat, RAG, tool, workflow, KB indexing, and Open API where they do not collide with active TASK-112 work
- focused backend tests, frontend build, and desktop/mobile screenshot QA

Notes:

- TASK-112 remains owned by `DEV-fengchu`. Do not modify files actively owned by TASK-112 unless the manager updates both assignments.
- Open API runtime metering should be integrated after TASK-112 lands or in a small follow-up if direct integration would cause branch conflict.
- The implementation may still be delivered in internal commits or phases, but FEAT-037 accountability is assigned to `DEV-nezha`.

## Acceptance Criteria

- Billing facts are append-only.
- Usage facts and credit ledger are separate tables and services.
- Organization admins can view subscription, remaining credits, usage, and ledger for their own organization.
- Platform operators can inspect plans, subscriptions, usage events, and ledger entries across organizations.
- Demo and test data are deterministic.
- Quota checks are designed before enforcement is enabled.
- Product UI follows `DESIGN.md` product rules and has desktop/mobile screenshot verification before shipping.
- No private keys, API keys, bearer tokens, or reusable secrets are written to docs, logs, tests, or task status files.

## Verification Plan

- Backend focused tests for billing repositories/services/controllers.
- Backend compile after migrations and entity wiring.
- Frontend build.
- Browser QA for `/admin/billing` and `/platform/billing` at desktop and 390px mobile.
- `.claw` state validation after task and assignment updates.
