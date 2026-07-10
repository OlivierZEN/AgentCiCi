---
kind: goals
version: 3
updated_at: 2026-07-10T15:26:34Z
updated_by: MANAGER-001
status: active
---

# Goals

## 当前交付目标

- 完成 FEAT-081 客户互动工作台的 32 项生产差距，覆盖真实 CloudCC 数据权限、CRM 确认写回与回读审计、真实指标信号、新客户推进、老客户经营、结构化 AI 动作和完整异常状态。
- 完成 AgentCiCi、CloudCC CRM 嵌入、真实 CRM 写入和桌面浏览器端到端验证，提交推送并按生产 runbook 发布线上版本。
- 当前执行任务：`TASK-182`。

## Product Goal

- Build AgentCiCi as an independent enterprise multi-organization agent runtime and governance platform with pluggable models, Agent Builder, tool orchestration, RAG knowledge enhancement, observability, Open API, and external system/channel integrations.
- Position AgentCiCi around CRM, after-sales, and enterprise business-system agent operations rather than a generic agent/workflow builder; the first commercial wedge is an AI-native after-sales agent connected to WeCom customer service, CRM, order/work-order systems, knowledge bases, and governed runtime observability.

## MVP Scope

- Backend foundation on Java 21 and Spring Boot 3 (Web MVC monolith).
- Frontend on React + Vite with **two entry points**: assistant (`/`) vs admin (`/admin/login`, `/admin/*`), separate `localStorage` tokens.
- Assistant workspace information architecture centered on **Agent -> Conversation -> Message**, with channels treated as conversation attributes rather than top-level objects.
- Multi-organization aware request context; JWT with `org_id` and `roles`.
- Fixed-password login for the current development stage; org-scoped membership now lives in `organization_member.role_code` (`ORG_ADMIN` / `ORG_USER`) with global identity in `user_account`.
- Chat + RAG with optional `knowledgeBaseIds`; KB list/read for all logged-in users, KB writes and models/tools/ops for `ORG_ADMIN` only.
- Admin user APIs: list org users, change roles (guard last admin downgrade).
- Knowledge-base document pipeline (upload, MQ indexing, Qdrant/memory vector store per profile).

## Success Criteria

- Backend and frontend projects can build locally.
- Repository structure supports incremental delivery by capability slice.
- Core interfaces and folders align with the design document; **README、设计方案、`.claw/current-status.md`、演示脚本**对双入口与权限的描述一致。
- Durable project state is maintained in `.claw/`.
- Product roadmap decisions are checked against `docs/specs/FEAT-025-agentcici-market-positioning-and-roadmap.md`: prioritize after-sales/CRM integration, governed runtime operations, evaluation, release governance, and workload billing before broad generic-builder expansion.

## Constraints

- All tenant-sensitive data paths must carry `org_id`.
- Test and operational records must contain only verified results.
- Keep the first delivery focused on project skeleton and safe extension points.
