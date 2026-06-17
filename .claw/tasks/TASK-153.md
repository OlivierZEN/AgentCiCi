---
kind: task-status
task_id: TASK-153
status: review
updated_at: 2026-06-18T00:07:00+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-153.yaml
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
---

# TASK-153 - Platform-governed Tavily, Iflytek, and OneKeyToken provider

## Scope

- Move Tavily search and Iflytek realtime ASR configuration out of organization admin and into platform operations.
- Runtime Tavily and Iflytek calls resolve platform-governed configuration, not tenant-owned rows.
- Add OneKeyToken as a model provider in `/platform/models`.
- Preserve organization admin integration management for organization-owned apps such as CloudCC CRM and Feishu bot.

## Design Notes

- Reuse `integration_app` with the platform governance org id as the platform-scope storage key.
- Add platform integration APIs under `/platform/integrations`.
- Organization `/integrations` should hide and reject platform-managed app codes.
- OneKeyToken uses OpenAI-compatible model fetching and default base URL `https://my.onekeytoken.com/v1`.

## Verification Plan

- Run task-scoped identity and assignment checks before source edits.
- Add focused backend integration coverage for platform integration management and organization write lockout.
- Run focused Maven tests for model provider governance, management console lockout, platform integration governance, and Tavily config masking.
- Run frontend build.

## Progress

- Task and assignment created.
- Implemented platform-scoped Tavily and Iflytek integration management under `/platform/integrations`.
- Organization `/integrations` now filters out platform-managed apps and rejects writes/tests for Tavily and Iflytek.
- Runtime config resolution for Tavily and Iflytek now maps those app codes to the platform governance org.
- Added OneKeyToken as an OpenAI-compatible model provider with default base URL `https://my.onekeytoken.com/v1`.
- Replaced the temporary OpenAI fallback logo for OneKeyToken with the user-provided yellow `OK` OneKeyToken logo asset.
- Changed OneKeyToken model catalog fetching to a static catalog from the developer guide because the current production gateway does not expose OpenAI-compatible `/models`; `deepseek-chat` and `qwen3.5-flash` are preset catalog entries, not proof of a successful gateway `/models` response.
- Frontend platform console now has `集成配置` at `/platform/integrations`; admin integration page reuses the same component but stays organization-scoped.
- Renamed the platform sidebar entry from `模型厂商` to `模型配置`; `/platform/models` now uses peer tabs for `模型厂商治理` and `场景模型路由` so scene routing is no longer visually nested under a selected provider.

## Verification

- `dev-login.py` for `TASK-153` representative backend/frontend/spec/task files -> allowed.
- `check-assignment.py` for representative TASK-153 files -> allowed.
- `mvn -Dmaven.repo.local=.m2 -Dtest=PlatformIntegrationGovernanceIntegrationTest,PlatformModelProviderIntegrationTest,ManagementConsoleIntegrationTest,TavilyToolServiceTest test` in `backend/` -> success, 15 tests passed.
- `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- `git diff --check` -> success.
- `npm run build` in `frontend/` after replacing the OneKeyToken logo asset -> success; existing Vite large chunk warning remains.
- `dev-login.py` and `check-assignment.py` for TASK-153 frontend model-config tab files -> allowed.
- `npm run build` in `frontend/` after the model-config tab split -> success; existing Vite large chunk warning remains.
- `git diff --check` after the model-config tab split -> success.
- Playwright CLI desktop smoke on `http://localhost:5173/platform/models` -> success: sidebar shows `模型配置`, page title shows `模型配置`, `模型厂商治理` and `场景模型路由` are peer tabs, and the route tab shows only scene route controls.
- `mvn -Dmaven.repo.local=.m2 -Dtest=PlatformModelProviderIntegrationTest test` in `backend/` after the OneKeyToken static catalog fix -> success, 1 test passed.
- `npm run build` in `frontend/` after the OneKeyToken static catalog notice/title fix -> success; existing Vite large chunk warning remains.
- `git diff --check` after the OneKeyToken static catalog fix -> success.
- Local API smoke after backend restart: `POST /platform/models/providers/onekeytoken/models/fetch` returned `success=true`, `catalogSource=static`, `remoteFetchSupported=false`, and models `onekeytoken/auto`, `deepseek-chat`, `qwen3.5-flash`.
- Playwright CLI desktop smoke after the fix -> success: OneKeyToken `全部模型` opens without a 404 banner and shows `预设模型 · OneKeyToken` plus the static catalog notice.
- Local services restarted: backend `8080` returned `{"status":"UP"}`, frontend `5173` returned `HTTP 200`.
- Local API smoke:
  - platform `/platform/integrations` returned `tavily`, `iflytek_asr`;
  - organization `/integrations` returned `cloudcc_crm`, `feishu_bot`;
  - `/platform/models/providers` returned `onekeytoken` with default base URL `https://my.onekeytoken.com/v1`;
  - organization `PUT /integrations/tavily` returned HTTP 403 with the platform-managed message.
- Desktop screenshot attempt via temporary `npx playwright` spec was blocked by transient module-resolution behavior for `@playwright/test`; temporary spec/config files were removed.

## Handoff

- Branch remains `codex/TASK-152-ai-minutes-billing-timeout` because TASK-152 local review changes are still uncommitted in the same worktree.
- No database migration was added; `integration_app` stores platform-managed rows under the configured governance org.
