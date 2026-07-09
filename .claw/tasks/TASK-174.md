---
kind: task-status
task_id: TASK-174
title: 数据洞察 AI 应用生产发布
status: done
owner_role: project-manager
assignee: MANAGER-001
spec_path: docs/specs/FEAT-084-data-insight-ai-app.md
assignment_path: .claw/assignments/TASK-174.yaml
updated_at: 2026-07-10T07:28:00+08:00
updated_by: MANAGER-001
---

# TASK-174 - 数据洞察 AI 应用生产发布

## 当前目标

在智能体平台 AI 应用列表中交付“数据洞察”AI 应用，基于 CRM 潜客、商机、客户、合同订单、销售业绩形成精细仪表板，并发布到生产环境。

## 当前进展

- 已读取 `PRODUCT.md`、`DESIGN.md` 和 Product Design/Impeccable/Frontend Design 规则。
- 已读取 CloudCC MSAPI 技能，确认内置 `cloudcc` CLI 可用。
- 已引用 CloudCC `platform/overview`、`platform/capabilityMap`、`platform/standardCapabilities`、`methodology/blueprint`。
- 已运行 `cloudcc scan msapi . standard-catalog`，确认 CRM 组织存在 `Account`、`Contact`、`cloudcclead`、`Opportunity`、`contract`、`cloudccorder`、`product`、`Task`、`Event` 等标准对象。
- 已发现现有 `customer-insight` 应用、后端表和模型路由，可升级复用。
- 已完成后端 `/ai/customer-insights/dashboard` API，实现演示组织真实 CRM 聚合数据优先、其他无数据组织 Mock fallback。
- 已将智能体平台 AI 应用入口展示为“数据洞察”，并新增精细化 CRM 仪表板：核心指标、销售漏斗、客户分层、业绩趋势、重点客户、风险与建议。
- 已按 Product Design / Impeccable / Frontend Design 工作流完成桌面端本地和生产浏览器检查，确认仪表板渲染、横向溢出、主内容宽度和细节布局正常。
- 已完成生产发布 `2.3.2`，生产 `/system/version` 返回 `version=2.3.2`、`imageTag=2.3.2`、`gitCommit=d144149168ea`。

## 计划

1. 建立 FEAT-084、TASK-174 和授权边界。`done`
2. 新增数据洞察 dashboard API 与测试。`done`
3. 升级 AI 应用入口和前端仪表板 UI。`done`
4. 本地构建、后端测试、桌面端截图检查和交互复测。`done`
5. 更新测试报告、状态文件，按生产 runbook 发布。`done`

## 验证记录

- `identity-gate`: passed, `MANAGER-001` task-scoped access allowed.
- `assignment-check`: passed, representative implementation files allowed.
- `cloudcc-standard-catalog`: passed, output saved to `/tmp/agentcici-standard-catalog.json`.
- `backend-customer-insight-integration`: `mvn -q -Dtest=com.codehouse.ciciassistant.customerinsight.CustomerInsightIntegrationTest test` in `backend/` -> passed.
- `frontend-build`: `npm run build` in `frontend/` -> passed; existing Vite large chunk warning remains.
- `static-check`: `git diff --check` -> passed.
- `desktop-visual-check`: local Vite `/app?aiApp=customer-insight` with mocked authenticated APIs and data insight dashboard payload -> passed; screenshot `output/playwright/data-insight-desktop-refined.png`; main panel `scrollWidth=clientWidth=966`, no overflow offenders.
- `release-dry-run-2.3.2`: `./scripts/release-acr.sh --dry-run` -> passed, resolved version `2.3.2`.
- `release-2.3.2`: `./scripts/release-acr.sh --version 2.3.2` -> passed; backend/frontend ACR images and Git tag pushed.
- `production-backup-2.3.2`: ECS backup `/opt/cici/backups/20260710-072126-before-2.3.2-task174-data-insight` -> passed.
- `production-health-2.3.2`: six containers healthy; `/actuator/health` -> `UP`; `/system/version` -> `2.3.2`; frontend `nginx -t` -> passed; recent backend error scan had no matches.
- `production-public-smoke-2.3.2`: `https://x.agentcici.com/` and `/app?aiApp=customer-insight` -> HTTP 200; `http://x.agentcici.com/` -> HTTPS 301; production-IP resolved `https://onechat.agentcici.com/` -> HTTP 200; local DNS for `onechat.agentcici.com` still returned NXDOMAIN.
- `production-authenticated-data-insight-smoke`: demo org login returned `智能体平台演示环境`; `/ai/customer-insights/dashboard` -> `REAL_CRM_DEMO`, customers `10`, leads `6`, opportunities `8`, accounts `8`, funnel `6`, risks `5`.
- `production-browser-data-insight`: Playwright on `https://x.agentcici.com/app?aiApp=customer-insight` at 1620x920 -> passed; `scrollWidth=clientWidth=1306`, `offenderCount=0`, screenshot `output/playwright/task174-prod-data-insight-2.3.2.png`.

## 变更文件

- `docs/specs/FEAT-084-data-insight-ai-app.md`
- `.claw/tasks/TASK-174.md`
- `.claw/assignments/TASK-174.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/assistant/cici-ui.css`
- `frontend/src/assistant/customer-insight/*`
- `backend/src/main/java/com/codehouse/ciciassistant/customerinsight/*`
- `backend/src/test/java/com/codehouse/ciciassistant/customerinsight/*`

## 交接说明

- 不输出、提交或记录密码、token、secret、cookie 或可复用凭据。
- 若后续要真实写入 CloudCC 合同/订单标准对象，必须先按 MSAPI/OpenAPI 字段扫描和可回滚计划单独开任务。
