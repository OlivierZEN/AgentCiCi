---
kind: current-status
version: 4
updated_at: 2026-07-17T06:44:52Z
updated_by: MANAGER-001
phase: general-ontology-platform-v1-implementation
active_task: "TASK-213"
next_action: "Implement the authorized V83 reference-package provenance and fingerprint by TDD, then complete final independent review, merge and production acceptance."
read_next:
  goals: false
  decisions: false
  issue_list: true
  task_board: true
  active_task_status: true
  test_report: true
  devops: true
---

# Project Current Status

`current-status.md` is the hot index. Rewrite it as the latest snapshot; do not append session history.

## Snapshot

- TASK-213 is in progress on `codex/TASK-213-general-ontology-v1`: the domain-neutral backend, V82 persistence, project-delivery/CloudCC reference paths, read-only semantic query and business-first visual workbench are implemented locally. Workspace-create and reference-package result reconciliation already require an authoritative current-organization list plus exact business identity and creator. Final security review identified that an identical workspace manually created by the same administrator still needs explicit provenance separation; V82 remains unchanged and the authorized forward-only V83 will record reference-package source, package ID and content fingerprint. Frontend 177/177 and the prior isolated PostgreSQL/backend package run pass, but V83 still requires TDD, fresh regression, final independent review and real browser acceptance. Production remains `2.7.9 / c04e992b3840`, so TASK-213 must not be described as merged or online yet.

- TASK-201 右栏说明移除与双栏对齐增量修复已在生产 `2.7.9 / c04e992b3840` 完成。PR #11、不可变 Git/镜像 tag、发布前四项备份、仅重建 backend/frontend、六服务健康及 1600×1000 真实 Agent Builder 页面均通过；右栏模型治理说明与分隔线不存在，左右编辑列同为 612.5px × 604px，顶底边一致，系统提示词与发布备注输入底边一致，页面横向溢出、生产 console warning/error、backend ERROR/Exception 与 Nginx 精确 5xx 均为 0。即时应用回滚点为 `2.7.8 / 4814d2b9534d`。
- TASK-212 已在生产 `2.7.8 / 4814d2b9534d` 完成。PR #10、不可变 Git/镜像 tag、发布前四项备份、只重建 backend/frontend、V81 非事务并发索引、六服务健康、双向 401/403/200 权限矩阵及 `1600 x 1000` Agent Builder/平台页面均通过；两个索引 valid/ready，页面无外层横向溢出，console warning/error、稳定窗口 backend ERROR 与 Nginx 精确 5xx 均为 0。完整 Maven 诊断仍只有既有 341 项中的 3 failure / 7 error，未误报全量套件通过。即时应用回滚点为 `2.7.7 / e47979167af8`，V81 索引可安全保留。
- TASK-211 is complete in production `2.7.7 / e47979167af8`. PR #6 changed deterministic CRM SSE to the existing 18-character/18ms chunk sender; production `2.7.6` then exposed OpenAPI per-fragment whitespace loss, was rejected and rolled back. PR #7 removed per-delta trim/blank filtering, passed clean-DB CRM 135/135, frontend 89/89 and independent review, and was released as a new immutable version.
- Fresh production protocol and desktop evidence pass: SalesA 5/5 streams each emitted 133 deltas over about 2.4 seconds with exact persistence; blocking and SalesB match after cutoff-only normalization. OpenAPI blocking/streaming are both 2,383 characters, streaming has 133 messages plus one terminal event, and history/internal bodies are equal. Temporary API access is revoked, bindings are exactly restored, nine answer files have no tool/raw-ID leakage, and the final clean window has zero backend error, CRM failure, abnormal disconnect or Nginx 5xx. A fresh application-internal Browser run captured the same assistant bubble at 50 visible characters while the composer was disabled and at 2,100 characters after completion, with console error/warning 0, no horizontal overflow, complete Top 5/five-layer analysis, and no internal result leakage.
- TASK-210's `2.7.5 / be80eea665c0` implementation is preserved through production `2.7.9`: FEAT-116 renders the public standard WeChat mark and distinct Lucide business-source icons, preserves the compact timeline across all eight themes, and removes duplicate-key console errors from CRM event id collisions. Frontend 16 files / 89 tests and production build passed; independent final production visual evidence remains with TASK-210.
- TASK-208's `2.7.5 / be80eea665c0` implementation is preserved through production `2.7.9`. SalesA receives a deterministic five-layer CRM answer with direct conclusion, product Top 5, business diagnosis, forward signals, actions and data coverage; SSE, persisted messages, blocking, OpenAPI and desktop UI do not expose the internal tool result or trigger the false “等待确认” state.
- TASK-209 remains preserved through production `2.7.9`; the platform login is still locked to the approved reference image.
- TASK-207 is complete on `codex/TASK-207-frontend-theme-alignment-audit`: all eight themes now own authenticated frontend surfaces and data/identity colors; the organization entry uses the current organization name's first character; dashboard rows, menus, forms, lists and the interaction-ingestion dialog passed a real `1600 × 1000` desktop audit. Frontend 15 files / 85 tests, production build, JSON validation and diff checks passed; browser console error/warning and outer horizontal overflow are zero.
- TASK-206 is complete in production `2.6.11 / c540988655cb`. The pagecomponent now reads the current CRM session with `$CCDK.CCToken.getToken()`, the backend validates it through `/api/user/getUserInfo`, and strict session-user/page-user/AgentCiCi-member consistency remains in force. Real CRM initial load plus two refreshes produced three HTTP 200 ticket/consume pairs with no mapping error.
- TASK-205 remains the production CRM analysis baseline; TASK-208 hardens its routing, formatting, permission diagnostics and protocol behavior without introducing a separate general-purpose Agent.
- CloudCC batch `TASK-205-CRM-ANALYTICS-DEMO-V1` is now governed for SalesA and linked to the 16 TASK-203 V2 Accounts. Readback is 12 products, 16 accounts, 24 opportunities, 72 opportunity products, 16 contracts, 48 orders and 144 order items; a repeat plan reports zero updates, creates and duplicates. Quantity Top 5 remains `X1 130 / G5 110 / S2 95 / MP 75 / PA 65`, while amount champion is MP as designed.
- TASK-204 is ready: the approved design removes the nested frame and excess inset around the Agent Builder guide, then replaces the two persistent avatar buttons with an accessible avatar-triggered upload/change/remove menu. FEAT-110 awaits written user review before implementation.
- TASK-203 remains unintegrated on its dedicated branch, while production CloudCC already contains 16 TASK-203 V2 Accounts owned by and visible to SalesA. TASK-208 may read and reference those accounts but must not modify TASK-203's exclusive seeder.
- TASK-202 is complete in production `2.6.6 / 4caaa4800b3d`. The hotfix keeps agent bar, chat panel, sidebar metrics and machine lanes transparent and removes avatar scaling/shadows across all eight themes.
- TASK-200 is complete in production `2.6.4 / d88f4293759f`: V79, four-layer evaluation assets, deterministic assertions, real candidate execution, snapshots/comparison/staleness, publish gates, Trace regression capture, quality issues and platform/tenant/Builder/Ops product surfaces are live.
- TASK-199 is complete in production `2.6.2`: first-open fixed recommendations and demo action seeds are removed. Confirmed interactions produce AI action candidates governed by verbatim-evidence validation, confidence, business-key deduplication/refresh, seven-day cooldown, historical validity and the existing human-confirmed CRM write path.
- TASK-198 is complete in production `2.6.1`: V77 stores evidence-backed AI signals and versioned score snapshots; new interactions incrementally update the current customer with confidence gating, 90-day decay and lifecycle replacement. Queue filtering/sorting, detail metrics and the explanation drawer share one snapshot source.
- TASK-197 is done in production `2.5.11`: confirmed interactions now retain archive linkage, AI analysis, original materials and typed customer memory; timeline and assistant evidence open the same auditable archive.
- Production currently runs `2.7.9 / c04e992b3840`; backend/frontend and four state services are healthy, state-service container IDs were preserved and remain on `2.6.12`, Flyway is at V81, and CloudCC pagecomponent V15/customPage V9 are active. Immediate application rollback is healthy `2.7.8 / 4814d2b9534d`; release `2.7.6 / 2055947aae07` remains immutable failed-acceptance evidence and is not a rollback target.
- TASK-182 now uses current-user CloudCC tokens and record permissions for Account/Contact/Opportunity/Task/Event/Case/Contract projection, server-side new/existing queues, real metrics/signals, follow/notifications, all business tabs, customer-level AI history/actions, manually confirmed interaction ingestion, and supervisor summaries.
- TASK-170 security rules platform remains in progress and may resume after TASK-200 merge/release planning.
- 已知风险：本机仍无法解析 `onechat.agentcici.com`，但显式使用生产 IP 的 smoke 返回 200；另有跨用户不可见会话因 `ResponseStatusException` 被通用异常处理捕获而返回无数据的 500 而非 404/403，隔离成立但状态与日志语义需独立任务修复。

## Read Next

- `.claw/tasks/TASK-213.md`, `.claw/assignments/TASK-213.yaml`, `docs/specs/FEAT-118-general-ontology-modeling-platform.md` and its plan - active general ontology V1 delivery and authorization source.

- `.claw/tasks/TASK-201.md`, `.claw/assignments/TASK-201.yaml` and `docs/specs/FEAT-107-agent-builder-layout-and-model-governance.md` - completed production Agent Builder right-column cleanup and alignment acceptance.
- `.claw/tasks/TASK-212.md`, `.claw/assignments/TASK-212.yaml` and `docs/specs/FEAT-117-skill-dag-governance-phase1.md` - completed production Skill DAG Phase 1 scope, authorization and acceptance source.
- `.claw/tasks/TASK-211.md`, `.claw/assignments/TASK-211.yaml`, `docs/superpowers/plans/2026-07-15-crm-streaming-output.md` and the TASK-211 section in `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md` - completed production `2.7.7` protocol, application-internal Browser and governance acceptance.
- `.claw/tasks/TASK-210.md`, `.claw/assignments/TASK-210.yaml` and `docs/specs/FEAT-116-customer-workbench-standard-channel-icons.md` - active customer workbench standard source icon repair.
- `.claw/tasks/TASK-208.md`, `.claw/assignments/TASK-208.yaml` and `docs/specs/FEAT-114-crm-product-sales-analysis-hardening.md` - completed CRM stability, deep-analysis, SalesA migration and production acceptance record.
- `.claw/tasks/TASK-209.md` and `docs/specs/FEAT-115-platform-login-cosmic-visual-refresh.md` - production login source that TASK-208 must preserve.
- `.claw/tasks/TASK-207.md`, `docs/specs/FEAT-113-frontend-theme-consistency-and-alignment.md` and `design-qa.md` - completed frontend theme and alignment delivery plus visual evidence.
- `.claw/tasks/TASK-206.md` and `docs/specs/FEAT-112-cloudcc-embed-sso-recovery.md` - completed CloudCC embed SSO recovery and verification evidence.
- `.claw/tasks/TASK-205.md` and `.claw/assignments/TASK-205.yaml` - completed CRM analysis baseline and superseded authorization history.
- `.claw/task-board.md` and `.claw/test-report.md` - compact task index and latest verified commands.
