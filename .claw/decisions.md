---
kind: decisions
version: 3
updated_at: 2026-05-09T09:56:30Z
updated_by: ai
status: active
---

# Decisions

## DEC-001 Repository Bootstrap Shape

- Status: accepted
- Date: 2026-04-01T13:14:19Z
- Decision: bootstrap the repository as a two-application workspace with `backend/` and `frontend/` directories.
- Why this won:
  - Matches the design document's front-back separation.
  - Keeps early delivery simple while preserving room for later worker/orchestrator splits.
  - Lets AI-driven implementation progress independently on server and UI slices.
- Alternatives considered:
  - Single backend-only start: rejected because the design includes a React management/admin surface from day one.
  - Full microservice split now: rejected because it adds coordination cost before the core domain is stable.

## DEC-002 Backend First-Cut Architecture

- Status: accepted
- Date: 2026-04-01T13:14:19Z
- Decision: start with a modular Spring Boot monolith that exposes auth, chat, and knowledge-base API boundaries.
- Why this won:
  - Fastest path to a working MVP.
  - Easier enforcement of `org_id` propagation, shared audit hooks, and common error handling.
  - Future extraction into separate services remains possible once traffic and ownership justify it.

## DEC-003 Auth MVP Persistence Strategy

- Status: accepted
- Date: 2026-04-01T13:24:34Z
- Decision: implement auth MVP with Spring Data JPA and an H2 in-memory datastore for rapid local verification.
- Why this won:
  - Enables immediate end-to-end auth testing without waiting for external PostgreSQL setup.
  - Keeps domain entities and repository boundaries compatible with later PostgreSQL migration.
  - Reduces setup friction for iterative AI-driven development loops.
- Revisit trigger:
  - Switch to PostgreSQL-backed profiles before integration and staging environments.

## DEC-004 Local Infra Validation Path

- Status: accepted
- Date: 2026-04-01T13:33:38Z
- Decision: run local integration using Docker Compose (`postgres + redis`) and a dedicated Spring `local` profile.
- Why this won:
  - Provides reproducible developer setup for auth integration tests.
  - Verifies Redis-backed SMS code storage and PostgreSQL-backed JPA persistence together.
  - Keeps default profile fast for unit/integration runs that do not require external services.

## DEC-005 Schema Management Baseline

- Status: accepted
- Date: 2026-04-01T13:44:22Z
- Decision: manage auth schema with Flyway migrations and switch JPA DDL mode to `validate`.
- Why this won:
  - Prevents accidental schema drift between environments.
  - Makes PostgreSQL and H2 behavior more predictable in CI/local runs.
  - Creates a stable path for future additive migrations (RAG, tools, audit tables).
- Notes:
  - Local profile enables `baseline-on-migrate` to absorb previously initialized local schemas.

## DEC-006 Step 2 Orchestrator MVP Shape

- Status: accepted
- Date: 2026-04-01T13:52:54Z
- Decision: implement orchestration as a service pipeline (model router -> RAG retrieval -> tool execution -> response synthesis) with persistent session/audit records.
- Why this won:
  - Aligns with the target architecture while remaining fully testable in local mode.
  - Decouples model/tool providers behind clear interfaces for future real provider adapters.
  - Enables cost/audit observability during MVP stage.

## DEC-007 Step 3 Quality Gate

- Status: accepted
- Date: 2026-04-01T13:52:54Z
- Decision: define Step 3 completion as passing `scripts/quality-check.sh` plus delivering deployment and security checklists.
- Why this won:
  - Converts abstract quality goals into a repeatable local gate.
  - Bundles regression evidence with operational guidance for release readiness.

## DEC-008 Split Admin UI and Org RBAC

- Status: accepted
- Date: 2026-04-02T00:00:00Z
- Decision: separate **assistant** and **admin** UIs (distinct routes and `localStorage` keys); enforce **ORG_ADMIN** on management APIs via AOP; **ORG_USER** may only use KB GET endpoints plus chat; expose `/admin/users` for role management; first-time role from `app.auth.bootstrap-admin-mobiles` when creating a new user row.
- Why this won:
  - Matches enterprise expectation that operators and end-users do not share one console.
  - Keeps JWT and backend as single source of truth (`GET /auth/me` re-check on admin routes).
- Documentation: `AgentCiCi智能体平台实现设计方案.md`, `README.md`, `.claw/current-status.md`, and `scripts/e2e-local-business.sh` / `run-full-demo.sh` outputs stay aligned with this decision.

## DEC-009 Assistant-Side Agent Builder Entry

- Status: accepted
- Date: 2026-04-15T10:07:54Z
- Decision: implement the first-phase no-code Agent Builder as a dedicated assistant-side workspace, toggled from the left title area inside the existing IM shell, rather than as a separate route or admin-only page.
- Why this won:
  - Matches the Cherry-style product interaction the user requested: building agents from the main workspace instead of context switching into admin pages.
  - Keeps the current chat experience intact while introducing a parallel “framework first” builder surface that can be expanded incrementally.
  - Allows phase-by-phase delivery: front-end builder skeleton first, then draft persistence, tool registry, publishing, and governance without reworking the main layout.
- Alternatives considered:
  - Separate `/agent-builder` route: rejected because it breaks the requested inline workspace experience and adds navigation overhead.
  - Admin-only Agent Builder: rejected because this feature is meant to live beside the employee assistant experience, not only in configuration consoles.

## DEC-010 Text-First Workflow Compilation

- Status: accepted
- Date: 2026-04-15T10:07:54Z
- Decision: use **natural-language workflow specs -> AI-generated workflow code -> server-managed execution** as the target Agent workflow architecture, instead of making graphical node editing or hand-maintained workflow JSON the primary source of truth.
- Why this won:
  - Better matches AI Coding era interaction: business users describe intent and flow in natural language, while the system compiles it into executable artifacts.
  - Reduces manual maintenance cost of drag-and-drop flow editors and avoids making human-operated diagrams the main configuration surface.
  - Preserves enterprise requirements by keeping execution, permission control, auditing, versioning, and publish governance on the server side.
- Alternatives considered:
  - Graphical workflow editor as the primary authoring mode: rejected because it adds manual operation cost and is not AI-native.
  - Hand-authored node JSON as the primary configuration model: rejected because it is still an implementation-shaped intermediate form that business users should not maintain directly.

## DEC-011 Generated Workflow Preview Graph

- Status: accepted
- Date: 2026-04-16T03:20:00Z
- Decision: when compiling natural-language Agent specs, generate a **read-only workflow preview graph** together with workflow code and manifest, and treat that graph strictly as a visualization artifact rather than an editable source of truth.
- Why this won:
  - Matches the desired product shape: users describe the flow in natural language, the system compiles executable code, and the UI simultaneously offers a graphical preview for understanding and debugging.
  - Preserves the text-first / code-first architecture while avoiding a regression back to manually maintained flow editors.
  - Creates a clean path for the frontend to show compile-time flow previews first, then overlay runtime traces later, without introducing graphical authoring complexity.
- Alternatives considered:
  - No graph preview at compile time: rejected because it makes complex generated workflows harder to understand and review before publishing.
  - Editable canvas generated from compiled code: rejected because it would reintroduce dual sources of truth and higher maintenance cost.

## DEC-012 Assistant IA Uses Agent-Scoped Conversations

- Status: accepted
- Date: 2026-04-16T11:50:00Z
- Decision: reorganize the assistant workspace around **Agent -> Conversation -> Message**, treating each published agent as a top-level workspace object, each user/channel thread as an agent-scoped conversation, and each channel only as a conversation attribute/filter rather than a top-level entity.
- Why this won:
  - Matches the product's next-stage mental model once multiple published agents and multi-channel access exist at the same time.
  - Keeps the built-in `CiCi` agent as a stable system-level entry while allowing other published agents to coexist as first-class objects.
  - Creates a cleaner foundation for future backend persistence, routing, channel ingestion, handoff, and conversation ownership logic.
- Alternatives considered:
  - Keep a flat mixed session list: rejected because it hides which agent is actually serving each conversation and becomes harder to scale as more agents are published.
  - Use channel as the top-level navigation object: rejected because the real business owner is the agent, while channel is only the origin of a conversation.

## DEC-013 External Session Sync Uses Org-Scoped SSE Events

- Status: accepted
- Date: 2026-04-17T07:22:17Z
- Decision: deliver external-channel conversation realtime sync through a lightweight backend SSE stream (`/ai/sessions/stream`) that publishes session update events from the unified chat persistence layer, while keeping a low-frequency frontend polling fallback.
- Why this won:
  - Reuses the existing `chat_session` / `chat_message` persistence layer, so Feishu, WebChat, and future external channels can share one realtime sync mechanism instead of each channel pushing bespoke frontend logic.
  - Matches the current product rule that external-channel conversations are org/agent-scoped, allowing one org's logged-in operators to refresh immediately when an external user or CiCi updates a session.
  - Keeps the change set small and incremental: no need to redesign the Feishu long connection itself or introduce a heavier message broker/WebSocket stack just to refresh the workbench.
- Alternatives considered:
  - Keep 10-second polling only: rejected because it cannot satisfy the requirement that external conversations appear in the web workbench nearly immediately.
  - Build a dedicated per-channel push path from Feishu to the frontend: rejected because it duplicates conversation-sync logic outside the existing unified chat persistence model.

## DEC-014 Agent Spec and Skill Use a Layered Fusion Model

- Status: accepted
- Date: 2026-04-18T03:30:15Z
- Decision: keep **natural-language Agent Spec -> compiled workflow -> managed execution** as the top-level Agent architecture, while evolving `skill` into a reusable capability module that shares the same natural-language authoring and compiler backbone but does not replace Agent Spec.
- Why this won:
  - Preserves the already accepted text-first Agent Builder direction instead of resetting the product around skill composition only.
  - Allows `skill` to grow from prompt/tool policy into a reusable, versioned, multi-tenant capability asset without forcing it to become the top-level published product object.
  - Creates one shared compile spine for `Agent` and `skill`, reducing long-term duplication in natural-language parsing, risk analysis, preview generation, and governance.
  - Keeps multi-tenant safety stronger by making `skill` primarily responsible for reusable policy/subflow capability, while `Agent` remains responsible for top-level orchestration, publishing, and channel-facing identity.
- Alternatives considered:
  - Replace Agent Spec entirely with skill composition: rejected because it weakens the current Agent Builder mainline and blurs top-level workflow ownership.
  - Keep Agent Spec and skill as two unrelated systems: rejected because it would duplicate compiler, versioning, and governance investment over time.

## DEC-015 Agent Builder Owns Skill Selection, Admin Owns Skill Governance

- Status: accepted
- Date: 2026-04-21T12:20:00Z
- Decision: keep `skill` as an org-level governed capability asset in admin, but move the primary Agent-Skill selection workflow into `Agent Builder` / Agent settings, with `agent_skill_binding` remaining the single source of truth.
- Why this won:
  - Aligns the product entry with the already accepted `Agent Builder` mainline, so users configure a full Agent in one place instead of splitting top-level setup across assistant and admin surfaces.
  - Preserves `skill` as a reusable governed asset rather than degrading it into ad-hoc per-Agent local config.
  - Matches the existing runtime model, which already resolves skills by `agentId`, and therefore minimizes schema churn while improving UX consistency.
  - Creates a clean separation of concerns: admin governs what skills exist; Agent Builder governs which skills a given Agent may use.
- Alternatives considered:
  - Keep Agent-Skill binding primarily in admin skill settings: rejected because it fragments Agent configuration and scales poorly as published agents increase.
  - Move both skill governance and skill selection entirely into Agent Builder: rejected because it weakens organization-level reuse, lifecycle control, and risk governance for shared skills.

## DEC-016 Tavily API Key is Tenant-Scoped Integration_App Only

- Status: accepted
- Date: 2026-04-22T15:25:00Z
- Decision: the Tavily `tavily_search` + `tavily_extract` built-in tools resolve their API key exclusively from `integration_app(code="tavily")` (encrypted with `SecretCipherService`). There is **no** environment variable or `application.yml` fallback; if a tenant has not configured a key the tool returns a structured `TAVILY_NOT_CONFIGURED` error to the LLM.
- Why this won:
  - Matches the existing multi-tenant model for third-party secrets (CloudCC, Feishu, etc.) — admins already have a single governance surface under `/admin/integrations`, and per-tenant metering / rotation is native.
  - Removes the attack surface of a shared platform-level key where one tenant's abuse (rate-limit hits, ToS violations) would harm all tenants.
  - Keeps audit / rotation / disable controls owned by tenant admins instead of ops.
- Alternatives considered:
  - Env var / yaml fallback for platform-wide default key: rejected because it silently enables the tool for all tenants, blurs billing responsibility, and creates a secrets-rotation blind spot.
  - User-level API key (stored on the user record): rejected because it would require every user to acquire their own Tavily account, which does not fit the current org-admin-configures-integrations pattern.

## DEC-017 web-search Skill Defaults to cici-system with intent-route

- Status: accepted
- Date: 2026-04-22T15:25:00Z
- Decision: the new built-in `web-search` skill (bundling `tavily_search` + `tavily_extract`) is auto-bound to `cici-system` for every tenant via `SkillDefinitionService.DEFAULT_AGENT_SKILLS`, with `activation_mode = intent-route`. New tenants are auto-provisioned through the existing `ensureDefaultBindings()` path.
- Why this won:
  - `cici-system` is the tenant's fallback catch-all agent; making web search available there means users never need to configure it to get "ask the web" behavior.
  - `intent-route` (instead of `always-on`) keeps the general assistant from issuing unnecessary external web calls on every internal question — only routes out when the intent actually indicates a public-web need.
  - Domain-specific agents (Sales, HR, etc.) can still opt in explicitly via Agent Builder when their use case warrants it, preserving per-agent minimization.
- Alternatives considered:
  - Auto-bind to every Agent: rejected — over-broad, would increase cost and leak public-web behavior into domain-scoped agents.
  - Leave web-search off by default for every agent: rejected — violates the confirmed requirement that `cici-system` should get it out of the box and new tenants should be auto-provisioned.

## DEC-018 Agent workflow execution log + runtime triggers API (FEAT-004)

- Status: accepted
- Date: 2026-04-25T10:50:00Z
- Decision: add table `agent_workflow_execution_log` and append-only rows from `debug` (`TRY_RUN`), `publishVersion` (`MANUAL_PUBLISH`), and `chat` / `chatStream` after `evaluateForChat` (`CHANNEL`). Expose `GET /agents/{agentId}/runtime/executions` and `GET /agents/{agentId}/runtime/triggers` (org admin) for Agent Builder. Triggers response is derived from bindings + lifecycle + published schedule stub (no separate trigger table yet).
- Why this won:
  - Delivers durable observability aligned with FEAT-004 without blocking compile/publish/chat on logging failures.
  - Reuses existing org-scoped agent APIs and RBAC; avoids inventing a second execution store.
- Alternatives considered:
  - Full event bus / external observability only: rejected for MVP — Builder still needs in-product list.
  - Skip stream path logging: rejected — `chatStream` now mirrors non-stream `chat` by calling `evaluateForChat` + `appendFromChat` after the model stream completes.

## DEC-019 Agent runtime schedule sync API (FEAT-005)

- Status: accepted
- Date: 2026-04-25T14:25:00Z
- Decision: introduce persistent runtime schedule table `agent_runtime_schedule_trigger` and explicit sync endpoint `POST /agents/{agentId}/runtime/schedules/sync`. `GET /runtime/triggers` now reports `scheduleSource` (`persisted|inferred|placeholder`) so UI can distinguish preview vs synced state.
- Add-on decision: `publishConfigs.feishu.autoSyncSchedulesOnPublish` controls whether publish automatically runs one schedule sync; default is `true`.
- Why this won:
  - Preserves FEAT-004's immediate Spec-based visibility while adding a deterministic “apply” action for runtime governance.
  - Keeps synchronization idempotent at Agent scope by deactivating previous active rows before storing newly inferred rows.
- Alternatives considered:
  - Compile-time auto-write only: rejected — users lose manual control over when draft semantics should become runtime config.
  - Keep inferred-only forever: rejected — no durable audit trail and ambiguous ownership between compile and runtime.
  - Always auto-sync with no toggle: rejected — some teams need explicit manual sync gate during staged rollout.

## DEC-020 Skill Governance Uses Layered Platform/Tenant Model

- Status: accepted
- Date: 2026-04-30T00:00:00Z
- Decision: evolve Skill governance from a simple `builtin/custom` split into a layered model: platform core policy, platform standard Skill, tenant derived Skill, tenant custom Skill, and hidden platform service capability.
- Why this won:
  - Separates mandatory hidden safety/fallback policy from tenant-visible reusable business capabilities.
  - Allows platform standard Skills to be visible and bindable while remaining platform-maintained and non-editable.
  - Gives tenants a clean customization path through derived/custom Skills instead of modifying platform templates directly.
  - Creates stable versioning and impact-analysis hooks for platform updates, Agent publish pinning, audit, and billing.
- Alternatives considered:
  - Keep only `builtin=true/false`: rejected because it cannot express visibility, editability, binding policy, update policy, or hidden platform service capabilities.
  - Let tenants edit built-in standard Skills directly: rejected because platform upgrades and support would become ungovernable.

## DEC-021 Platform Operations Console Starts As Modular Monolith Control Plane

- Status: accepted
- Date: 2026-04-30T00:00:00Z
- Decision: implement the platform operations console first inside the existing codebase and backend service, with isolated `/platform/**` routes, platform RBAC, platform table prefixes, and usage-metering events; design boundaries so metering, billing, and platform config can be extracted later.
- Why this won:
  - Current Agent, Skill, KB, Tool, billing, and runtime domains are still evolving quickly, so premature microservice extraction would add coordination cost.
  - A shared database and backend service make first-phase tenant operations, quota checks, and usage event writes easier to keep consistent.
  - Separate routes, roles, audit logs, and domain packages prevent the platform console from becoming a loose extension of tenant admin.
  - Event-based metering creates the future extraction path without blocking runtime delivery now.
- Alternatives considered:
  - Build a fully independent platform microservice immediately: rejected because interfaces and ownership boundaries are not stable enough yet.
  - Add platform pages directly into the tenant admin console: rejected because it blurs platform-vs-tenant responsibility and weakens cross-tenant audit.

## DEC-022 Platform RBAC Reuses Existing SMS Login and JWT Claims in Phase 1

- Status: accepted
- Date: 2026-04-30T07:35:00Z
- Decision: for phase-1 `/platform/**`, continue using the existing SMS login flow and org-scoped JWT, then derive platform roles from configured mobile lists (`platform-admin/operator/support/billing/auditor-mobiles`) and inject them into the token `roles` claim and `/auth/me` response.
- Why this won:
  - Delivers an independent platform control plane and backend RBAC boundary without first introducing a second auth subsystem or platform-user table.
  - Keeps the first implementation compatible with the existing `TenantContext` / AOP authorization path, so `/platform/**` can land with limited blast radius.
  - Preserves room to later replace config-derived roles with dedicated platform identities once tenant operations, billing, and support workflows harden.
- Alternatives considered:
  - Create separate platform login and identity tables immediately: rejected because it adds schema and migration surface before platform workflows are validated.
  - Reuse `ORG_ADMIN` as implicit platform access: rejected because it collapses tenant and platform responsibility boundaries.

## DEC-023 Page Design Governance Uses Impeccable + Root Design Facts

- Status: accepted
- Date: 2026-04-30T11:54:33Z; updated 2026-05-01T09:33:50Z
- Decision: adopt `impeccable` as the mandatory project-level workflow for all page design work, with root `PRODUCT.md`, `DESIGN.md`, and `DESIGN.json` as the shared design source of truth for authenticated product surfaces.
- Why this won:
  - Turns design quality from a one-off preference into a durable project rule that future agents can follow automatically.
  - Gives assistant, admin, and platform pages one shared product-register baseline while still allowing route-level density tuning by workflow.
  - Forces visual-language changes to update the design facts in the same session, reducing drift between code and documentation.
  - Captures `/admin/skills` list hardening as a reusable admin CRUD list rule set: native table alignment is protected, search/filter/empty states cannot stretch the page, toolbar buttons stay unified, filters use gold text tabs, and row actions move behind an opaque hover/focus more menu.
- Alternatives considered:
  - Keep design rules only in ad-hoc chat prompts: rejected because the guidance would not survive handoff or later sessions.
  - Treat each surface as fully independent with no shared baseline: rejected because it would quickly fragment component vocabulary, tone, and interaction patterns.

## DEC-024 Account Identity Separates Global Person From Organization Membership

- Status: accepted
- Date: 2026-05-08T15:48:32Z
- Decision: evolve AgentCiCi auth from org-scoped `app_user(org_id, mobile, role_code)` into a global account model: `user_account` represents the natural person; login identifiers, auth credentials, and external identities are child records; `organization_member` represents that person inside one organization with role, seat type, status, and organization-local profile.
- Implementation note 2026-05-08T16:04:39Z: because the system is still in development and not live, the first implementation uses a direct migration path instead of a compatibility layer: initialization migration no longer creates `app_user`; `organization_member.id` is the org-scoped identity carried by JWT `sub`/`member_id`; `user_account.id` is carried separately as `account_id`.
- Why this won:
  - Matches enterprise collaboration products such as Feishu, WeCom, DingTalk, Slack, and Notion: one personal account can join or create multiple organizations.
  - Prevents the same mobile number or email from producing duplicate global users while still allowing the user to create additional organizations after login.
  - Keeps billing, permissions, data access, and subscription lifecycle organization-scoped rather than personal-account-scoped.
  - Makes future login methods natural: mobile, email, username, password, OTP, Passkey, Google/Gmail, Microsoft, Feishu, DingTalk, WeCom, SAML, and OIDC can all bind to the same global person without widening `user_account`.
  - Clarifies data retention: organization business data can be exported, frozen, and purged when a subscription ends, while the global account and other organizations remain intact.
- Alternatives considered:
  - Keep `mobile + org_id` as the user identity forever: rejected because it duplicates one natural person across organizations and makes multi-org switching, SSO, account merge, invitation, and offboarding brittle.
  - Store all login methods directly as columns on `user_account`: rejected because OAuth/SSO provider identities, verification state, primary markers, account merge, and unlink flows require one-to-many child records.
  - Tie global account lifetime to a single organization subscription: rejected because a user may belong to multiple organizations and should keep the global identity even if one organization expires or is purged.

## DEC-025 Tenant Purge Uses Queued Jobs Before Physical Deletion

- Status: accepted
- Date: 2026-05-09T09:56:30Z
- Decision: real tenant purge requests create a `QUEUED` `organization_purge_job` first, then a Spring scheduled worker runs the physical DB/file/vector cleanup. Operators can cancel only `QUEUED` real purge jobs; `RUNNING` jobs finish into `SUCCEEDED`, `FAILED`, or `PARTIAL_FAILED`.
- Why this won:
  - Keeps high-risk tenant deletion out of the platform API request thread.
  - Gives operators a short cancellation window before physical deletion starts.
  - Preserves the existing guarded purge controls: `PENDING_PURGE`, legal hold, fresh source dry-run, and `PURGE {orgId}` confirmation are still checked when the job is queued and again when the worker executes it.
  - Fits the current modular monolith without introducing a broker or separate worker service before production traffic justifies that operational cost.
- Alternatives considered:
  - Continue synchronous purge in the HTTP request: rejected because large DB/file/vector cleanup can exceed request time and gives no cancellable queued state.
  - Introduce RabbitMQ or an external worker now: deferred because the local monolith scheduler is enough for the first lifecycle control-plane implementation; production hardening can add distributed locks, dead-letter handling, and alerting.
