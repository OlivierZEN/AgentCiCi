# Security And Compliance Checklist (Step 3)

## Auth And Access

- [x] SMS code has TTL and request throttling.
- [x] JWT carries `org_id` and `user_id`.
- [x] Invalid token returns `401`.
- [ ] Integrate real SMS provider and remove `devCode` from production responses.

## Multi-Organization Isolation

- [x] Tenant context is extracted and enforced for key APIs.
- [x] Chat, tools, and audit records include `org_id`.
- [ ] Add explicit repository-level tenant guards for all future query paths.

## Data Protection

- [ ] Store secrets in KMS/config center (not plain yaml).
- [ ] Add PII masking for audit payloads.
- [ ] Add data retention policy for chat and audit records.

## Prompt/Tool Safety

- [x] Tool execution is controlled by registered tools.
- [ ] Add role-based tool allowlist.
- [ ] Add prompt injection detector and context sanitization rules.

## Release Gates

- [x] Backend regression tests pass.
- [x] Local profile tests pass with PostgreSQL + Redis.
- [ ] Add SAST/dependency scan in CI.
