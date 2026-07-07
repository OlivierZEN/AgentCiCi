---
kind: task-status
task_id: TASK-169
status: review
updated_at: 2026-07-07T14:12:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-169.yaml
spec_path: docs/specs/FEAT-079-kb-data-quality-annotation.md
---

# TASK-169 - 独立数据清洗与智能标注平台能力

## Scope

- 补齐客户要求中的独立数据清洗与智能标注平台能力，并达到生产就绪标准。
- 新增 `/admin/data-quality` 作为组织控制台一级入口，面向所有数据源；首版以知识库和 KB 连接器作为第一批数据源适配器。
- 覆盖数据源聚合、扫描、复核、规则预览/应用、智能标注建议、审核入库、前端工作区和验证证据。
- 将智能标注画像能力作为「知微画像」落地到前台 AI 应用区，并与原「客户洞察」保持完全独立的应用入口、组件目录和渲染路径。

## Initial Findings

- FEAT-067 已完成企业知识平台的 parser、ACL、eval、connector、drift audit、embedding metadata 和 Qdrant smoke。
- 当前缺口集中在独立平台入口、数据源聚合、质量扫描、重复/无效数据识别、正则清洗、人审队列和标注工作流。
- `kb_chunk.content_hash`、`KbChunkEntity.updateContent(...)`、文档 metadata 和 vector upsert 可以作为清洗/标注实现基础。

## Implementation Plan

- 创建 FEAT-079、TASK-169 和授权边界。
- 新增 V70 迁移及质量治理/标注数据模型。
- 新增 `/data-quality` 后端聚合 API 和 `/admin/data-quality` 前端页面。
- 实现质量扫描、规则 preview/apply、人审队列和审计。
- 实现智能标注建议、接受/拒绝、文档 metadata 与 chunk annotation 入库。
- 完成 `/admin/data-quality` 独立平台桌面端验证。
- 在前台 AI 应用区新增「知微画像」入口，副标题为 AI 智能打标与画像引擎，按 CDP demo 高保真还原对象列表、画像详情、标签库、AI 配置和运营看板，不复用客户洞察分段报告 UI。

## Verification

- `dev-login.py` for `MANAGER-001` covering FEAT-079/TASK-169 assignment and state files -> allowed.
- `check-assignment.py` for TASK-169 representative spec, state, V70 migration, KB backend, tenant lifecycle, integration test, and admin KB frontend files -> allowed.
- `dev-login.py` for `MANAGER-001` / `TASK-169` covering the same representative files on `codex/TASK-169-kb-data-quality-annotation` -> allowed.
- `git diff --check` -> success for TASK-169 setup files.
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests test` in `backend/` -> success after backend data-quality API and model changes.
- `npm run build` in `frontend/` -> success after `/admin/data-quality` page and navigation changes; existing large chunk warning remains.
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` in `backend/` -> success; Flyway schema v70 and scan/clean/annotate integration path passed.
- `check-assignment.py` for TASK-169 including `frontend/vite.config.js` / `frontend/vite.config.ts` and standalone data-quality page -> allowed.
- `curl -i http://127.0.0.1:5173/data-quality/sources` -> proxied to backend and returned expected unauthenticated `401` JSON, confirming local dev proxy uses the real `/data-quality` API.
- Playwright desktop validation at `http://127.0.0.1:5173/admin/data-quality` with local backend/frontend -> success:
  - Login `13900009999 / szyd1234` as org admin succeeded.
  - First-level nav entry `数据清洗标注` opened `/admin/data-quality`.
  - `/data-quality/sources`, `/runs`, `/issues`, `/kb/{id}/quality/rules`, and annotation suggestion list requests returned `200`.
  - Manual scan `POST /data-quality/knowledge-bases/{kbId}/runs` returned `200`; page showed `COMPLETED`, `114` scanned chunks, and `20` open issues.
  - Browser console had 0 errors and 0 warnings after data-source load and scan.
  - Desktop layout check returned `innerWidth=1440`, `scrollWidth=1440`, `bodyScrollWidth=1440`; no horizontal overflow.
  - Screenshot saved to `output/playwright/task169-data-quality-desktop.png`.
- Final `git diff --check` -> success.
- Final `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- `dev-login.py` and `check-assignment.py` for TASK-169 expanded frontend AI app files -> allowed.
- `npm run build` in `frontend/` after making AI 应用「知微画像」 independent from 「客户洞察」 -> success; existing Vite large chunk warning remains.
- Pre-merge 2026-07-07 validation:
  - `dev-login.py` for `MANAGER-001` / `TASK-169` covering current changed files -> allowed.
  - `check-assignment.py` for current TASK-169 changed files -> allowed.
  - `git diff --check` -> success.
  - `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check-task169-release.yml` -> success.
  - `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
  - First `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` attempt was blocked because local PostgreSQL was not listening on `localhost:5432`; started repo local infrastructure with `docker compose up -d --remove-orphans postgres redis rabbitmq qdrant`.
  - `SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/agentcici_test' SPRING_DATASOURCE_USERNAME=cici SPRING_DATASOURCE_PASSWORD=cici123 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3 mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test` in `backend/` -> success; Flyway validated schema at version 70.
- Playwright desktop validation at `http://127.0.0.1:5179/app` with mocked local auth/API -> success:
  - AI 应用列表同时显示 `AI 听记`、`客户洞察`、`知微画像`。
  - `客户洞察` 保持原入口和原客户洞察文案，`frontend/src/assistant/customer-insight/**` 中无 `variant` / `portrait` / `知微` 混用逻辑。
  - `知微画像` 使用独立 `frontend/src/assistant/zhiwei-portrait/ZhiweiPortraitDemoApp.tsx` 渲染，页面展示 `AI 智能打标与画像引擎`、对象列表、画像详情、标签库、AI 配置和运营看板。
  - Browser console had 0 errors after local route mocks; layout check returned `innerWidth=1440`, `scrollWidth=1440`, `bodyScrollWidth=1440`.
  - Additional nav validation clicked `画像详情`、`标签库`、`AI 配置`、`运营看板`; all views rendered successfully with 0 console errors.
  - Screenshot saved to `output/playwright/zhiwei-portrait-ai-app.png`.

## Changed Files

- `docs/specs/FEAT-079-kb-data-quality-annotation.md`
- `.claw/tasks/TASK-169.md`
- `.claw/assignments/TASK-169.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `backend/src/main/resources/db/migration/V70__kb_data_quality_annotation.sql`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/api/DataQualityController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/api/KnowledgeBaseController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/service/KbDataQualityService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbQualityRuleEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbQualityRunEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbQualityIssueEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbAnnotationSuggestionEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/KbChunkAnnotationEntity.java`
- `backend/src/main/java/com/codehouse/ciciassistant/kb/domain/*Repository.java`
- `backend/src/main/java/com/codehouse/ciciassistant/platform/service/PlatformTenantLifecycleService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/kb/KnowledgeBaseLifecycleIntegrationTest.java`
- `frontend/src/admin/pages/AdminDataQualityPage.tsx`
- `frontend/src/admin/pages/AdminKnowledgePage.tsx`
- `frontend/src/admin/AdminShell.tsx`
- `frontend/src/App.tsx`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/assistant/customer-insight/CustomerInsightAppPanel.tsx`
- `frontend/src/assistant/customer-insight/CustomerInsightModuleNav.tsx`
- `frontend/src/assistant/customer-insight/CustomerInsightReportPreview.tsx`
- `frontend/src/assistant/customer-insight/CustomerInsightSectionEditor.tsx`
- `frontend/src/assistant/customer-insight/customerInsightSections.ts`
- `frontend/src/assistant/zhiwei-portrait/ZhiweiPortraitDemoApp.tsx`
- `frontend/vite.config.js`
- `frontend/vite.config.ts`

## Handoff

- Branch for implementation: `codex/TASK-169-kb-data-quality-annotation`.
- User changed direction from embedded KB治理 to independent `/admin/data-quality`; implementation pivoted accordingly.
- Current implementation has backend data model/API, standalone frontend route/nav, KB adapter as first data source, desktop browser validation evidence, and front AI app entries where `知微画像` and `客户洞察` are separate applications with separate render paths.
- Ready for review/merge packaging on branch `codex/TASK-169-kb-data-quality-annotation`.
