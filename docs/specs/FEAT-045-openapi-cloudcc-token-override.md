---
kind: feature-spec
feature_id: FEAT-045
title: OpenAPI CloudCC token override and key typing
status: assigned
owner_role: fullstack-agent
task_ids: TASK-123
related_decisions: FEAT-021, FEAT-036, FEAT-042
related_issues: none
updated_at: 2026-05-21T03:18:00Z
updated_by: ai
---

# FEAT-045 - OpenAPI CloudCC token override and key typing

## Renumbering Note

This requirement was first drafted outside the repo with a temporary `FEAT-037` label.

Inside this project, `FEAT-037` is already reserved by SaaS billing and usage ledger work in [FEAT-037-saas-billing-usage-ledger.md](/Volumes/AISpace/codehouse/cc-codeup-agentcici_PM/docs/specs/FEAT-037-saas-billing-usage-ledger.md). To avoid source-of-truth conflicts, this requirement is formally renumbered to `FEAT-045`.

## Background

Current Agent OpenAPI requests authenticate with an AgentCiCi API key, resolve one `agent_api_credential`, and execute the chat as that key's fixed `runAsUserId`.

For CloudCC-related runtime access, the current backend path is:

1. OpenAPI resolves the credential and uses `runAsUserId`.
2. `ChatOrchestratorService` and downstream tool execution keep using `orgId + runAsUserId`.
3. `CloudccAccessTokenService` reads the current organization's `cloudcc_crm` integration plus that run-as user's `cc_username` and `cc_safetymark`.
4. The backend exchanges those fields for a CloudCC access token and injects it into native CloudCC tools, CloudCC MCP tools, and `integration:cloudcc.accessToken` skill API calls.

That model works for server-to-server hosted calls, but it is inaccurate for CloudCC embedded pages where the browser session already holds the current CloudCC user's short-lived token. In that embedding scenario, continuing to derive CloudCC identity from the fixed run-as user causes:

- CRM data identity drift: CloudCC data access happens as the API key's run-as user, not as the current CloudCC page user.
- Operational overhead: every possible embedded-page caller must also maintain CloudCC username and safetymark inside AgentCiCi user profiles.

At the same time, the project must not allow arbitrary OpenAPI callers to switch CloudCC identity just by sending a token inside any request. The identity-switching ability must be explicitly declared by the API key type.

## Goal

- Add key typing to Agent OpenAPI credentials with first-phase values `standard` and `cloudcc`.
- Keep `standard` as the default and preserve all existing OpenAPI behavior.
- Allow `cloudcc` keys to require a caller-supplied CloudCC access token per request.
- Make CloudCC native tools, CloudCC MCP tools, and CloudCC skill API auth refs use the caller-supplied token for that request instead of deriving a token from run-as user profile fields.
- Preserve AgentCiCi API key authentication as the primary gateway. A CloudCC token proves current CloudCC user login state for CRM access only; it does not replace AgentCiCi API key auth.
- Prevent CloudCC tokens from being stored in DB rows, request/response summaries, trace details, prompts, model messages, or error payloads.
- Leave an extensible seam for future external caller types such as OA or Salesforce.

## Non-Goals

- Do not persist caller CloudCC tokens into `organization_member`, `integration_app`, `agent_api_call_log`, `agent_api_message`, or any runtime log table.
- Do not allow CloudCC token alone to invoke Agent OpenAPI.
- Do not promote CloudCC external users into internal AgentCiCi users.
- Do not allow unrestricted caller-defined gateway URLs or internal address overrides.
- Do not change the meaning of `runAsUserId` for non-CloudCC capabilities such as internal audit ownership, organization boundary, memory, email, or other user-scoped runtime features.

## Current-Code Facts

- The current public OpenAPI entrypoints are `POST /openapi/v1/agents/{agentId}/chat` and `POST /openapi/v1/agents/{agentId}/chat/stream`.
- The current request DTO is `AgentOpenApiRunService.ChatCommand`, not a separate conversation service DTO.
- `agent_api_credential` does not yet contain `key_type`.
- `CloudccAccessTokenService` currently derives CloudCC token exclusively from organization integration config plus run-as user `cc_username` and `cc_safetymark`.
- Native CloudCC tools, MCP CloudCC tools, and skill API `integration:cloudcc.accessToken` already converge on `CloudccAccessTokenService.getSessionContext(orgId, userId)`.

These facts mean the feature should be implemented as a shared runtime seam in the current OpenAPI stack, not as a one-off patch to only a future Dify-style compatibility endpoint.

## Key Type Model

### Supported First-Phase Types

| `keyType` | Purpose | CloudCC token requirement | CloudCC data identity |
| --- | --- | --- | --- |
| `standard` | Default OpenAPI key for existing server-to-server use cases | No caller token required; `cloudccContext` must not be used as an identity switch | Derived from run-as user `cc_username` + `cc_safetymark` |
| `cloudcc` | CloudCC embedded page or CloudCC UI runtime key | Caller must provide `cloudccContext.accessToken` on each request | Uses caller-supplied CloudCC access token |

### Future Extensibility

The internal design should allow future key types such as:

- `oa`
- `salesforce`
- controlled `custom:<code>`

without rewriting the OpenAPI main path.

## API Contract

### 1. API Key Create and Read

Agent API key create payload adds `keyType`:

```json
{
  "name": "CloudCC embedded key",
  "runAsUserId": "user-123",
  "keyType": "cloudcc"
}
```

Rules:

- `keyType` is optional and defaults to `standard`.
- Allowed values are whitelisted by service logic.
- Unknown values must be rejected at create/update time.
- `runAsUserId` remains required and must still belong to the current org.
- First phase should treat `keyType` as immutable after creation. If the caller needs a different type, create a new key.

### 2. Runtime Request Body

The current `/chat` and `/chat/stream` payloads should accept a new top-level object:

```json
{
  "sessionId": "crm-page-001",
  "message": "查询当前客户最近的跟进记录",
  "cloudccContext": {
    "accessToken": "cloudcc-page-runtime-token",
    "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi",
    "setupSvc": "https://szyd.apis.cloudcc.cn/setup"
  }
}
```

When FEAT-036 `chat-messages` compatibility endpoints are delivered, the same semantic object must also be supported there, using the same validation rules.

Rules:

- `cloudccContext.accessToken` is required for `keyType=cloudcc`.
- Maximum token length should be bounded.
- `baseUrl` is optional. If missing, resolve from the current org CloudCC integration gateway.
- `setupSvc` is optional. If missing, derive from `baseUrl`.
- `standard` keys must not silently accept `cloudccContext` as a CRM identity switch. The request should fail with a stable error.

## Security Requirements

### No Token Persistence

Caller CloudCC tokens must not appear in:

- `agent_api_call_log.request_summary`
- `agent_api_call_log.response_summary`
- OpenAPI message metadata rows if FEAT-036 adds them
- `agent_run_trace.detail_json`
- prompt assembly inputs
- model messages
- normal logs
- returned error messages
- test snapshots or spec examples with live credentials

Allowed audit fields are non-secret summaries such as:

```json
{
  "openApiKeyType": "cloudcc",
  "cloudccCredentialSource": "caller_supplied",
  "cloudccBaseUrlHost": "szyd.apis.cloudcc.cn"
}
```

### Base URL Restrictions

If the caller provides `baseUrl` or `setupSvc`, it must satisfy project-controlled allow rules:

- same host as the current org CloudCC CRM integration gateway, or
- a trusted `*.apis.cloudcc.cn` host with CloudCC path constraints

Reject:

- `localhost`
- private IPs
- bare IPs
- non-HTTP(S) schemes
- embedded credentials in URLs
- internal AgentCiCi service address overrides

### No Silent Fallback

For `cloudcc` keys:

- missing token must fail before runtime execution
- invalid token must fail
- expired token must fail
- denied base URL must fail
- no path may silently fall back to run-as-derived CloudCC token

## Runtime Design

### 1. Data Model

Add `key_type` to `agent_api_credential` with default `standard`.

Entity and view models must expose:

- persisted `keyType`
- read API value for management UI and docs

### 2. External Caller Runtime Context

Introduce a non-persistent runtime value object, for example:

```java
public record ExternalCallerRuntimeContext(
        String keyType,
        CloudccCredentialOverride cloudccOverride
) {}
```

and:

```java
public record CloudccCredentialOverride(
        String accessToken,
        String baseUrl,
        String setupSvc,
        String source
) {}
```

This object exists only for a single request lifecycle.

### 3. Provider / Resolver Layer

Introduce a small resolver seam, for example:

```java
public interface ExternalCallerAuthProvider {
    String keyType();
    ExternalCallerRuntimeContext validateAndBuildContext(
            AgentApiCredentialEntity credential,
            AgentOpenApiRunService.ChatCommand command);
}
```

First-phase providers:

- `standard`: rejects CloudCC override semantics and returns empty external runtime context.
- `cloudcc`: requires and validates `cloudccContext`, builds CloudCC override context.

### 4. OpenAPI Entry Integration

Validation should happen inside the shared OpenAPI run layer so that:

- current `/chat`
- current `/chat/stream`
- future FEAT-036 `chat-messages`

all follow the same rule set.

The preferred integration point is `AgentOpenApiRunService`, before invoking `ChatOrchestratorService`.

### 5. Request-Scoped CloudCC Override

`CloudccAccessTokenService.getSessionContext(orgId, userId)` should first check for a request-scoped CloudCC override bound to the current OpenAPI request.

Priority should be:

1. current request override
2. normal run-as cache
3. normal run-as fresh fetch

For `cloudcc` keys, any 401 refresh path must stay inside the caller-supplied override model and must not degrade into run-as token derivation.

### 6. Downstream Reuse

The main benefit of using `CloudccAccessTokenService` as the seam is that existing downstream code can stay largely unchanged:

- native CloudCC tool calls
- CloudCC MCP header injection
- CloudCC MCP argument injection
- skill API `integration:cloudcc.accessToken`

All of them should continue to call `getSessionContext(orgId, userId)` and receive the effective runtime credential source.

## Error Codes

Add stable business errors such as:

- `unsupported_key_type`
- `cloudcc_token_required`
- `cloudcc_context_not_allowed`
- `cloudcc_context_invalid`
- `cloudcc_base_url_denied`
- `cloudcc_token_rejected`

The response text must explain the problem without ever echoing the token.

## FEAT-036 Coordination

This feature is adjacent to FEAT-036, not a replacement for it.

Coordination rules:

- FEAT-045 owns CloudCC key typing and caller-supplied CloudCC token override semantics.
- FEAT-036 owns broader OpenAPI Dify parity and compatibility endpoints.
- Shared implementation should land once in common OpenAPI runtime seams, not as duplicated logic across multiple controllers.
- If FEAT-036 `chat-messages` is implemented in the same stacked branch, FEAT-045 rules must apply there too.

## TASK-123

### Scope

Assign `TASK-123` to `DEV-fengchu` to implement:

- `agent_api_credential.key_type`
- API key create/read support for `keyType`
- request DTO support for `cloudccContext`
- OpenAPI shared runtime validation for `standard` vs `cloudcc`
- request-scoped CloudCC credential override
- native/MCP/skill-api CloudCC runtime reuse
- no-token-leak hardening in OpenAPI logs and traces
- OpenAPI docs and management UI wording updates where needed

### Out Of Scope

- OA or Salesforce token validation implementation
- full FEAT-036 delivery outside the CloudCC token override seam
- browser-side token storage strategy beyond documentation constraints

## Acceptance Criteria

- Existing keys and omitted `keyType` values behave as `standard`.
- `standard` key behavior remains backward compatible for current OpenAPI callers.
- `cloudcc` keys require `cloudccContext.accessToken` on runtime requests.
- `cloudcc` key requests use caller-supplied CloudCC token for CloudCC native tools, MCP tools, and skill API auth refs.
- No `cloudcc` key failure path silently falls back to run-as-derived CloudCC token.
- Caller CloudCC token does not appear in call logs, trace details, prompts, or error payloads.
- Base URL override is restricted and cannot become an SSRF channel.
- Current `/chat` and `/chat/stream` are covered, and any FEAT-036 parity route touched in the same implementation follows the same rules.

## Verification

- OpenAPI integration tests for `standard` and `cloudcc` key paths
- Tests proving caller token reaches native CloudCC and MCP CloudCC paths
- Tests proving no token leakage into call log or trace persistence
- `backend mvn -q -Dmaven.repo.local=.m2 -Dtest=AgentOpenApiIntegrationTest test`
- `backend mvn -q -Dmaven.repo.local=.m2 -DskipTests compile`
- `frontend npm run build`
- `git diff --check`

## Assignment History

- 2026-05-21T03:18:00Z: External draft requirement renumbered from temporary `FEAT-037` label to `FEAT-045` because in-repo `FEAT-037` is already owned by billing. Formal spec created and assigned to `DEV-fengchu` as `TASK-123`.
