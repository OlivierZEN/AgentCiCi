# Security And Compliance Checklist (Step 3)

## Auth And Access

- [x] Password login uses the database-initialized fixed credential for the current local/internal phase.
- [x] JWT carries `org_id` and `user_id`.
- [x] Invalid token returns `401`.
- [x] SMS verification login is disabled in the API and removed from the three login entries.
- [ ] Replace the fixed local/internal password with per-user password reset or SSO before production.

## Multi-Organization Isolation

- [x] Tenant context is extracted and enforced for key APIs.
- [x] Chat, tools, and audit records include `org_id`.
- [ ] Add explicit repository-level tenant guards for all future query paths.

## Data Protection

- [ ] Store secrets in KMS/config center (not plain yaml).
- [ ] Add PII masking for audit payloads. Tracked by FEAT-080 / TASK-170.
- [ ] Add data retention policy for chat and audit records.

## Prompt/Tool Safety

- [x] Tool execution is controlled by registered tools.
- [ ] Add role-based tool allowlist.
- [ ] Add prompt injection detector and context sanitization rules. Tracked by FEAT-080 / TASK-170.

## Release Gates

- [x] Backend regression tests pass.
- [x] Local profile tests pass with PostgreSQL + Redis.
- [ ] Add SAST/dependency scan in CI.
