---
kind: task-status
task_id: TASK-114
assignee: MANAGER-001
owner_role: project-manager
status: review
branch: codex/TASK-114-feat-037-billing-ledger
pr_url: n/a
spec_path: docs/specs/FEAT-037-saas-billing-usage-ledger.md
assignment_path: .claw/assignments/TASK-114.yaml
updated_at: 2026-05-30T11:32:30Z
updated_by: MANAGER-001
---

# TASK-114 FEAT-037 SaaS Billing Usage Ledger

## Scope

Implement FEAT-037 end to end:

- billing schema migration using the next valid mainline migration version, currently expected as `V60__billing_usage_ledger.sql` after assignment refresh
- billing package domain entities, repositories, DTOs, and services
- plan, subscription, usage meter event, rate card, quota, and credit ledger services
- organization admin and platform billing APIs
- `/admin/billing` and `/platform/billing` product UI
- runtime metering hooks in slices: chat/model first, then RAG, tools, workflow, KB indexing, and Open API where they do not collide with active Open API work
- private deployment billing posture: annual license, active seats, module/capacity packs, and services first; Work Credits for governance/platform-paid usage, not local-model token double charging
- deployment billing mode switch: backend `BillingModeProperties` + `/billing/mode`, frontend `billingMode.ts` normalization helper
- edition lines: SaaS `团队版/商业版/企业版`; private deployment `部门版/企业版/集团版`; capacity and service packs are separate add-ons
- deterministic default plan/subscription seed behavior
- focused backend tests, frontend build, and desktop visual QA

## Out Of Scope

- Payment provider, invoice tax, or external finance system integration.
- Editing TASK-112-owned Open API files while TASK-112 is active, unless MANAGER-001 explicitly updates both assignments.
- Full contract pricing, sales discounting, automatic renewal, external finance reconciliation, and final quota enforcement before metering and ledger correctness are proven.
- Charging customer-owned local model token usage as a default private-deployment billing item.

## Preflight

Before editing, run task-scoped `dev-login.py` for `MANAGER-001` on branch `codex/TASK-114-feat-037-billing-ledger`.

Before implementation creates the migration, update `.claw/assignments/TASK-114.yaml` because it still authorizes only `backend/src/main/resources/db/migration/V53__billing_usage_ledger.sql`. Current local and `origin/main` migration head is `V59`, so `V53` would be out of order for Flyway-managed environments.

## Design Reassessment 2026-05-28

- FEAT-037 remains the engineering delivery spec for FEAT-003 and FEAT-022, but first delivery should optimize for billing facts correctness, not complete commercial operations.
- First slice should prove `usage_meter_event`, real model token usage, deterministic rating, and append-only `billing_credit_ledger`.
- `/admin/billing` and `/platform/billing` should be read-oriented audit and explanation surfaces first. Mutation-heavy plan editing, payment, invoice, contract, and renewal workflows stay out of scope.
- Runtime metering should be staged to reduce conflict risk: chat/model first, then RAG/tool/Open API/workflow/KB indexing.
- Tool billing requires a `billing_type` classification before charging tool calls.
- Private deployment pricing should use annual license, active operation/build seats, module/capacity packs, and implementation/support services. Credits remain for usage governance, budget controls, contract quotas, and platform-paid resources.
- Code switching mechanism is deployment-level, not tenant self-service: `private_deployment` remains the default; `saas` can be selected by Spring configuration.
- Plan seed should use stable codes: `saas_team`, `saas_business`, `saas_enterprise`, `private_department`, `private_enterprise`, `private_group`; `trial` remains a trial plan/status.

## Verification Target

- Backend focused billing tests pass.
- Backend compile passes.
- Frontend build passes.
- Desktop screenshots for `/admin/billing` and `/platform/billing` are reviewed; do not add mobile compatibility implementation or mobile tests unless separately requested.
- `.claw` state validation passes after handoff updates.

## Implementation Notes 2026-05-30

- 已在隔离 worktree `/private/tmp/task114-billing-live` 完成运行时真实计费闭环，分支 `codex/TASK-114-feat-037-billing-ledger`。
- 新增 `BillingUsageMeteringService`，同步和流式聊天完成后记录 usage meter events，并在 SaaS `platform_paid/included` 且回答有效时写入 `usage_debit` ledger；私有化、客户自付资源或平台配置错误继续记录事实但不二次扣减 credits。
- 首批真实扣费项为 `conversation_credit`、`model_token_credit`、`retrieval_credit`、`tool_call_credit`、`workflow_credit`，均映射到官网 Pricing 的 `Credits 包`；管理端消耗明细已经显示 `官网报价条目`。
- SaaS 版本内含 credits 和权益对齐官网报价：团队版 50,000、商业版 250,000、企业版 1,000,000，并补齐 `SaaS Credits 加购包`、`知识库容量包`、`文档处理包`、`并发与构建扩展`、`上线服务包`。
- 修复幂等 source id 只按 session/domain 导致跨组织同 session 互相抵消的问题，改为 `orgId:sessionId:domain`。
- 修复同秒多 ledger 事件余额读取不稳定的问题，余额读取改按 ledger id 顺序。
- 修复新 SaaS 组织默认席位使用量沿用演示数据的问题，默认操作席位为 1，构建席位为 0。
- 修复 Vite dev proxy 漏转 `/admin/billing/*` API 以及过宽代理 `/admin/billing` 抢走前端页面路由的问题，现只代理账单 API 子路径。

## Verification 2026-05-30

- `mvn -q -DskipTests compile` in `/private/tmp/task114-billing-live/backend` -> success。
- `SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/agentcici_test' SPRING_DATASOURCE_USERNAME=cici SPRING_DATASOURCE_PASSWORD=cici123 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3 mvn -q -Dtest='com.codehouse.ciciassistant.billing.**.*Test' test` -> success。
- `npm test -- AdminBillingPage.test.ts PlatformBillingPage.test.ts billingMode.test.ts` in `/private/tmp/task114-billing-live/frontend` -> success，7 tests passed。
- `npm run build` in `/private/tmp/task114-billing-live/frontend` -> success；仍有既有 Vite chunk size warning。
- SaaS 扣费集成验证：billable chat metering trace 生成 `conversation_credit=1`、`model_token_credit=0.5` 与 `workflow_credit=0.2`，余额从 `50000` 变为 `49998.3`，重复记录同一 session/domain 不重复扣费。
- SaaS 真实 API 验证：本地无 Aliyun API key 时真实 `/ai/chat` 返回配置错误，生成 `conversation_credit` 与 `workflow_credit` 的 `non_billable` 用量事实，credits 保持 `50000`，ledger 不产生 `usage_debit`，符合平台错误不计费规则。
- 桌面 Playwright 验证 `/admin/billing`：页面显示账本、消耗明细、`官网报价条目 · Credits 包`，控制台 error 为 0，截图 `.playwright-cli/page-2026-05-30T11-32-09-874Z.png`。

## Assignment History

- 2026-05-18T23:20:36Z: Initial draft split FEAT-037 into separate backend/UI tasks.
- 2026-05-19T07:20:36+08:00: User assigned FEAT-037 full development to `DEV-nezha`; TASK-114 now owns the whole feature.
- 2026-05-20T09:54:39+08:00: User reassigned TASK-114 from `DEV-nezha` to Owen (`MANAGER-001`) and requested no further task assignment to `DEV-nezha`.
- 2026-05-21T23:47:23+08:00: Verification scope aligned with the project rule that new feature work does not add mobile compatibility implementation, screenshots, or tests by default.
- 2026-05-28T16:40:00+08:00: Reassessed billing design; narrowed first delivery to metering, rating, and ledger correctness before broad UI and quota enforcement.
- 2026-05-28T17:05:00+08:00: Added private-deployment commercial posture: no default local-model token double charging; use license, seats, capacity/modules, and services as primary revenue model.
- 2026-05-28T17:20:00+08:00: Added deployment billing mode switch scope and initial code target: backend billing mode properties/API plus frontend normalization helper.
- 2026-05-28T17:35:00+08:00: Added edition lines for SaaS and private deployment, with capacity/service packs kept as add-ons rather than version names.
