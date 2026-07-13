---
kind: goals
version: 3
updated_at: 2026-07-14T01:22:40+08:00
updated_by: MANAGER-001
status: complete
---

# Goals

## 当前交付目标

- 完成 FEAT-106 多租户智能体评测控制面，覆盖平台核心、标准应用、行业包和租户私有评测资产。
- 完成确定性断言、运行快照、版本对比、结果失效、发布门禁、Trace 回流和质量问题闭环。
- 建设 `/platform/evaluation`、`/admin/evaluation` 与 Agent Builder 独立“评测”Tab；原“发布渠道”只保留 IM/API/调度运行入口。
- 完成自动化测试、桌面浏览器、生产构建和发布就绪验证。
- 交付状态：`TASK-200` 已达到生产发布就绪，等待评审、合并并按运行手册发布。

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
