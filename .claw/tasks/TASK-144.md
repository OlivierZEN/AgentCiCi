---
kind: task-status
task_id: TASK-144
assignee: MANAGER-001
owner_role: frontend-agent
status: done
branch: codex/TASK-144-agentcici-public-website-restructure
pr_url: n/a
spec_path: docs/specs/FEAT-061-agentcici-public-website-restructure.md
assignment_path: .claw/assignments/TASK-144.yaml
updated_at: 2026-06-24T03:24:00Z
updated_by: MANAGER-001
---

# TASK-144 AgentCiCi Public Website Restructure

## Goal

重构 AgentCiCi 官网为中英双语企业级智能体平台官网，一级板块为 Solutions、SkillsHub、Pricing、Docs、Community，并以 Solutions 为主页。

## Scope

- 新增或重写公开官网 React 组件与样式。
- 重接公开官网路由。
- 删除旧公开官网叙事入口：SalesMost AI Suite、AutoReachAI / FollowUpAI 主站矩阵、旧 AutoService 独立官网入口。
- 移除用户截图标注的首页 hero 下方 `预约演示 / SkillsHub / 登录` 三按钮组。
- 保留全站预约演示入口，并让预约表单真实提交到运营后台预约记录。
- 更新规格、任务状态和验证记录。

## Out Of Scope

- 不修改认证后产品页、后台、平台控制台或后端业务接口；本次仅复用既有预约演示后端接口和运营后台列表。
- 不实现真实 Community 发帖、Docs 文档搜索或 SkillsHub 在线交易。
- 不新增移动端专项适配。

## Verification

- 2026-05-29T23:19:35Z - 2026-05-30T03:18:46Z: completed public website restructure and iterative visual passes. Earlier screenshots retained under `output/playwright/task144-*`; latest design facts are in the spec.
- 2026-05-29T23:19:35Z: task-scoped `dev-login.py` for `MANAGER-001` / `TASK-144` -> allowed.
- 2026-05-29T23:19:35Z: route slug adjusted from `/skillshub` / `/skills-hub` to `/skill-hub` because existing Vite dev proxy reserves `/skills*` for backend API calls.
- 2026-05-30T03:36:56Z: addressed in-browser comments on the homepage:
  - Removed the unclear `run /solution/autoservice` command from the hero visual.
  - Removed AI-flavored eyebrow labels such as `AGENTCICI PLATFORM`, `SOLUTIONS`, and `RUNTIME` from the Chinese homepage sections.
  - Replaced visible technical terms in the Chinese homepage such as `trace`, `Agent 执行`, and `Work Credits` with user-facing language like processing records, smart handling, and usage clarity.
  - Reworded the visible Solutions and Runtime headings into more natural Chinese business copy.
- 2026-05-30T03:36:56Z: `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- 2026-05-30T03:36:56Z: Playwright CLI desktop checks:
  - `http://127.0.0.1:5178/` -> success, screenshot `output/playwright/task144-browser-comments-final.png`, console errors `0`.
  - `/`, `/pricing`, `/global`, `/skill-hub`, `/docs`, and `/community` route smoke -> console errors `0`.
- 2026-05-30T09:07:25Z: simplified the Pricing page to only show three public subscription plans and removed private-deployment pricing from the page:
  - `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
  - In-app browser desktop check `http://127.0.0.1:5178/pricing` -> 3 plans, no `私有化` / `Private deployment` / `SaaS` pricing text, credit note visible, console errors `0`; screenshot `output/playwright/task144-simple-pricing-zh.png`.
  - In-app browser desktop check `http://127.0.0.1:5178/global/pricing` -> 3 plans, no private-deployment pricing text, credit note visible, console errors `0`; screenshot `output/playwright/task144-simple-pricing-en.png`.
- 2026-05-30T09:23:33Z: adjusted Pricing model after product discussion so Credits cover actual workload, builder seats control configuration/governance permissions, and concurrency controls peak runtime capacity. Public plan cards now show builder seats, team-member capacity, and concurrent agent runs instead of paid operator seats.
  - `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
  - In-app browser desktop check `http://127.0.0.1:5178/pricing` -> old operator-seat copy absent, concurrency limits visible, console errors `0`; screenshot `output/playwright/task144-pricing-concurrency-zh.png`.
  - In-app browser desktop check `http://127.0.0.1:5178/global/pricing` -> old operator-seat copy absent, concurrency limits visible, console errors `0`; screenshot `output/playwright/task144-pricing-concurrency-en.png`.
- 2026-05-30T09:41:53Z: added knowledge-base cost dimensions to Pricing after product discussion. Public plan cards now show knowledge storage capacity and monthly document processing pages, and add-ons include knowledge capacity packs plus document processing packs.
  - `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
  - In-app browser desktop check `http://127.0.0.1:5178/pricing` -> knowledge capacity, document processing, and related add-ons visible, console errors `0`; screenshot `output/playwright/task144-pricing-knowledge-zh.png`.
  - In-app browser desktop check `http://127.0.0.1:5178/global/pricing` -> English knowledge capacity and document processing visible, console errors `0`; screenshot `output/playwright/task144-pricing-knowledge-en.png`.
- 2026-06-24T00:00:00Z: applying user feedback to remove the homepage hero CTA button group and wire the shared public demo form to the existing `/api/autoservice/demo-requests` endpoint so submitted data appears in the operations console appointment list.
- 2026-06-24T03:08:00Z: validation for user feedback:
  - `dev-login.py` for `MANAGER-001` / `TASK-144` covering public website frontend, spec, and task files -> **allowed**.
  - `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
  - `mvn -q -Dtest=AutoServiceDemoRequestIntegrationTest test` in `backend/` -> **failed** on existing stale test credential: public submit returned `200` and created a request, but the test queried `/platform/autoservice/demo-requests` with an organization token and got expected platform-surface `403 需要平台账号权限`.
  - Local real-backend browser validation with backend `mvn spring-boot:run -Dspring-boot.run.profiles=local` on `8080` and Vite on `5178` -> **success**.
  - Playwright desktop checked `/`, `/solutions`, `/skill-hub`, `/pricing`, `/docs`, `/community`, `/global`, `/global/solutions`, `/global/skill-hub`, `/global/pricing`, `/global/docs`, and `/global/community`: each route has the shared demo form with company/contact/mobile/focus/submit fields, header demo link points to `#demo`, and Solutions hero CTA button count is `0`.
  - Playwright submitted a real demo request from `/global/docs`; platform API and `/platform/website-leads` both found record `id=8`, status `NEW`, sourcePath `/global/docs`.
  - Screenshots: `output/playwright/task144-demo-hero-buttons-removed.png`, `output/playwright/task144-demo-form-submit-success.png`, `output/playwright/task144-demo-record-platform.png`; final browser console errors `0`.
- 2026-06-24T03:40:00Z: pre-merge/pre-release gates:
  - `npm run build` in `frontend/` -> **success**; existing Vite large chunk warning remains.
  - `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` in `backend/` -> **success**.
  - `docker compose --env-file deploy/acr.env.example -f deploy/docker-compose.acr.yml config >/tmp/cici-compose-check.yml` -> **success**.
  - `git diff --check` -> **success**.
- 2026-06-24T03:24:00Z: merged and released to production as `2.1.3`:
  - Branch `codex/TASK-144-agentcici-public-website-restructure` committed as `2a9c5ea`, merged into `main`, and pushed to `origin/main`; production release commit is `916ee5f48d7a`.
  - Release used `./scripts/release-acr.sh --dry-run`, then `./scripts/release-acr.sh --version 2.1.3`; Git tag `2.1.3` and backend/frontend `2.1.3` plus `latest` images were pushed.
  - Production backup created at `/opt/cici/backups/20260624-111422-before-2.1.3` with env, PostgreSQL dump, KB files, and Qdrant archive.
  - ECS `/opt/cici/deploy/acr.env` now has `CICI_IMAGE_TAG=2.1.3` and `CICI_APP_VERSION=2.1.3`; `/system/version` returns `version=2.1.3`, `imageTag=2.1.3`, `gitCommit=916ee5f48d7a`.
  - Six production compose services are healthy; backend `/actuator/health` returns `UP`; latest Flyway rows remain applied through version `68`; frontend `nginx -t` passed; recent backend error scan was empty.
  - Public smoke: `https://x.agentcici.com/` returns `200`; `http://x.agentcici.com/` redirects to HTTPS; direct `onechat.agentcici.com` DNS remains NXDOMAIN from the current resolver, while explicit production-IP resolve returns `200`.
  - API smoke: org login/core org APIs passed; platform login, platform me, skills, tools, and audit logs passed; org token against platform skills still returns expected `403`.
  - Production Playwright desktop verification on `https://x.agentcici.com/` confirmed Solutions hero CTA button count `0`, header demo link to `#demo`, and no browser console errors.
  - Production Playwright submitted a real demo request from `/global/docs`; platform appointment list found record `id=8`, company `线上发布验证 REL213-1782271046262`, status `NEW`, site `global`, locale `en`, sourcePath `/global/docs`.
  - Screenshots: `output/playwright/release-2.1.3-public-home.png`, `output/playwright/release-2.1.3-demo-submit.png`, `output/playwright/release-2.1.3-platform-website-leads.png`.

## Changed Files

- `docs/specs/FEAT-061-agentcici-public-website-restructure.md` defines the confirmed bilingual public website IA, Swan reference boundary, content requirements, design direction, and acceptance criteria.
- `.claw/assignments/TASK-144.yaml` authorizes the public website rewrite for `MANAGER-001`.
- `frontend/src/suite/AgentCiciWebsite.tsx` adds the new bilingual public website covering Solutions, SkillsHub, Pricing, Docs, Community, and demo booking; the shared demo form submits real records to the operations backend.
- `frontend/src/suite/agentcici-website.css` adds the new public website visual system and desktop layout.
- Pricing now uses production-facing standard, professional, and enterprise plan cards with monthly prices, initialized Credits, knowledge storage, document processing pages, builder seats, team-member capacity, concurrent agent runs, feature lists, Credits packs, knowledge capacity packs, document processing packs, concurrency/builder expansion, and launch services.
- `frontend/src/App.tsx` wires the new public routes and redirects old `/suite/*`, `/pricing/global`, and `/autoservice/*` public routes to the new structure.
- Removed old public website files: `frontend/src/suite/SuiteLanding.tsx`, `frontend/src/suite/SuitePricingPage.tsx`, `frontend/src/suite/suite-site.css`, `frontend/src/autoservice/AutoServiceLanding.tsx`, `frontend/src/autoservice/autoservice-copy.ts`, and `frontend/src/autoservice/autoservice-site.css`.

## Handoff

- Assigned branch: `codex/TASK-144-agentcici-public-website-restructure`; released to production from `main` in version `2.1.3`.
- Shape confirmed by user on 2026-05-30.
- Public website is restructured, locally verified on desktop, merged to `main`, and deployed. The latest pass removes the screenshot-marked homepage hero button group while preserving header demo access, and all checked public pages submit real appointment records to the operations console. `SkillsHub` uses `/skill-hub` route to avoid the existing `/skills` API proxy prefix.
- Follow-up: update the stale backend focused integration test so platform appointment-list assertions log in through `/auth/platform/password/login` instead of using an organization token.
