---
kind: task-status
task_id: TASK-147
assignee: MANAGER-001
owner_role: project-manager
status: review
branch: codex/TASK-147-wecom-kf-connection-test
pr_url: n/a
spec_path: docs/specs/FEAT-023-ai-native-after-sales-agent.md
assignment_path: .claw/assignments/TASK-147.yaml
updated_at: 2026-06-01T07:10:14Z
updated_by: MANAGER-001
---

# TASK-147 WeCom Customer-Service Connection Test

## Scope

- Add an organization-admin connection test for configured Enterprise WeChat customer-service accounts.
- The test must verify stored CorpID and customer-service Secret by calling the WeCom token endpoint through existing backend credentials handling.
- The API response must only return diagnostic status, timestamp, and expiry metadata; it must never reveal Secret, EncodingAESKey, or access tokens.
- Add a compact action and result state to `/admin/channels/wechat-kf`, preserving the existing admin product visual language.
- Update FEAT-023 implementation progress and record focused validation.

## Out Of Scope

- Real customer-message callback smoke with production WeCom credentials.
- CRM, order, logistics, work-order, warranty, refund, or ticket object mappings.
- Any high-risk write action, live customer reply automation change, or new support-ticket schema.
- Mobile-specific layout, screenshots, or automated mobile checks.

## Design Notes

- Product surface: organization admin configuration page.
- User: implementation/admin operator validating whether WeCom credentials are usable before copying callback URL and running live callback smoke.
- Tone: compact, factual, operational. Use inline text action and metadata row rather than a new modal.
- Constraint: keep the current 鎏金账房 warm ivory, ink text, champagne structural-line style.

## Verification Target

- Focused backend tests for connection-test response behavior and secret/token redaction.
- Frontend build or focused TypeScript/build gate.
- `git diff --check`.
- Desktop browser smoke for `/admin/channels/wechat-kf` if the local app can run in this session.

## Progress

- 2026-06-01T06:55:50Z: Task opened from the user's request to continue the enterprise WeCom customer-service feature.
- 2026-06-01T07:10:14Z: Implemented backend connection test and admin UI action. Ready for review after focused backend/frontend/browser validation.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/wecom/api/AdminWecomKfAccountController.java`
- `backend/src/main/java/com/codehouse/ciciassistant/wecom/service/WecomKfClient.java`
- `backend/src/main/java/com/codehouse/ciciassistant/wecom/service/WecomKfConfigService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/wecom/service/WecomKfClientTest.java`
- `backend/src/test/java/com/codehouse/ciciassistant/wecom/service/WecomKfConfigServiceTest.java`
- `frontend/src/admin/pages/AdminWecomKfAccountsPage.tsx`
- `frontend/src/styles.css`
- `docs/specs/FEAT-023-ai-native-after-sales-agent.md`

## Verification 2026-06-01

- `mvn -q -Dtest='com.codehouse.ciciassistant.wecom.**.*Test' test` in `backend/` -> success.
- `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.
- `git diff --check` -> success.
- Playwright CLI desktop smoke for `/admin/channels/wechat-kf` with mocked admin/WeCom APIs -> success; the page shows `测试连接`, `企业微信连接测试通过`, and result `已通过 · 2026-06-01 15:12`; console errors `0`, screenshot `output/playwright/task-147-wecom-kf-connection-test.png`.

## Handoff

- Live WeCom callback smoke still requires real CorpID, customer-service Secret, Token, EncodingAESKey, `open_kfid`, and a reachable filed callback domain.
- This task only verifies the stored CorpID/Secret token path; it does not validate `sync_msg` callback delivery or `send_msg` to a real customer session.
