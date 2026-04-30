---
kind: goals
version: 3
updated_at: 2026-04-16T11:58:45Z
updated_by: ai
status: active
---

# Goals

## Product Goal

- Build an enterprise multi-organization AI assistant platform with pluggable models, tool orchestration, and RAG knowledge enhancement.

## MVP Scope

- Backend foundation on Java 21 and Spring Boot 3 (Web MVC monolith).
- Frontend on React + Vite with **two entry points**: assistant (`/`) vs admin (`/admin/login`, `/admin/*`), separate `localStorage` tokens.
- Assistant workspace information architecture centered on **Agent -> Conversation -> Message**, with channels treated as conversation attributes rather than top-level objects.
- Multi-organization aware request context; JWT with `org_id` and `roles`.
- SMS login; org-scoped users in `app_user.role_code` (`ORG_ADMIN` / `ORG_USER`).
- Chat + RAG with optional `knowledgeBaseIds`; KB list/read for all logged-in users, KB writes and models/tools/ops for `ORG_ADMIN` only.
- Admin user APIs: list org users, change roles (guard last admin downgrade).
- Knowledge-base document pipeline (upload, MQ indexing, Qdrant/memory vector store per profile).

## Success Criteria

- Backend and frontend projects can build locally.
- Repository structure supports incremental delivery by capability slice.
- Core interfaces and folders align with the design document; **README、设计方案、`.claw/current-status.md`、演示脚本**对双入口与权限的描述一致。
- Durable project state is maintained in `.claw/`.

## Constraints

- All tenant-sensitive data paths must carry `org_id`.
- Test and operational records must contain only verified results.
- Keep the first delivery focused on project skeleton and safe extension points.
