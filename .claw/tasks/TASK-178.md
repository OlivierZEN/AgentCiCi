---
kind: task-status
task_id: TASK-178
title: CRM 嵌入客户互动工作台语音输入热修
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md
assignment_path: .claw/assignments/TASK-178.yaml
updated_at: 2026-07-10T08:55:00+08:00
updated_by: MANAGER-001
---

# TASK-178 - CRM 嵌入客户互动工作台语音输入热修

## 当前目标

修复 CloudCC CRM 端嵌入客户互动工作台里 AI 助手语音输入点击后提示“未识别到有效的语音内容”的问题，确保嵌入 iframe 具备麦克风权限，并避免 ASR 启动失败被误报为空语音。

## 当前进展

- 已读取项目热状态、任务板、TASK-173/TASK-175 相关上下文。
- 已定位风险点：pagecomponent iframe 缺少 `allow="microphone"`；`useAsrVoiceInput` 启动失败后 `abort()` 会触发空完成回调覆盖真实错误。
- 已实现：pagecomponent iframe 增加 `allow="microphone; clipboard-write"`；ASR hook 启动失败或主动 abort 不再触发空完成回调。
- 本地验证、生产发布、CloudCC pagecomponent V10 发布、自定义页 V4.0 绑定和生产浏览器回归已完成。

## 计划

1. 建立 FEAT-088、TASK-178 和授权边界。`done`
2. 修复 ASR hook 启动失败/主动 abort 回调语义。`done`
3. 为 CloudCC pagecomponent iframe 和 UMD bundle 增加 microphone allow。`done`
4. 运行前端测试、构建、CloudCC package dry-run 和浏览器/DOM 验证。`done`
5. 发布到生产和 CloudCC CRM，并记录验收证据。`done`

## 验证记录

- `identity-gate`: generic MANAGER-001 dev-login for intended files -> allowed.
- `identity-gate-task`: `dev-login.py .claw --developer MANAGER-001 --task TASK-178 --branch main --files ... --json` -> allowed.
- `assignment-check`: `check-assignment.py .claw --developer MANAGER-001 --task TASK-178 --branch main --files ... --json` -> allowed.
- `frontend-asr-test`: `npm --prefix frontend test -- useAsrVoiceInput.test.ts` -> success, 3 tests passed.
- `umd-syntax`: `node --check frontend/build/customer-workbench.umd.min.js` -> success.
- `iframe-allow-static`: `rg` confirmed `allow="microphone; clipboard-write"` in Vue and UMD render/fallback paths.
- `cloudcc-package-dry-run`: `cloudcc package pagecomponent customer-workbench . --dry-run` -> success; safe file count `2`, no unsafe config pattern.
- `frontend-build`: `npm --prefix frontend run build` -> success; existing Vite large chunk warning remains.
- `local-umd-host`: Playwright simulated CRM host with UMD fallback -> success; iframe exists, `allow=microphone; clipboard-write`, src `https://x.agentcici.com/app?aiApp=customer-workbench&embed=crm`, no outer right scrollbar. Screenshot: `output/playwright/task178-local-umd-microphone-allow.png`.
- `release-dry-run-2.3.5`: `./scripts/release-acr.sh --dry-run` -> success; version `2.3.5`, commit `aac3080c103c`.
- `release-2.3.5`: `./scripts/release-acr.sh --version 2.3.5` -> success; backend/frontend linux/amd64 images and Git tag `2.3.5` pushed.
- `production-backup-2.3.5`: ECS backup `/opt/cici/backups/20260710-083254-before-2.3.5-task178-crm-workbench-voice` -> success.
- `production-deploy-2.3.5`: backend/frontend recreated on `2.3.5`; backend and frontend healthy; `/actuator/health=UP`; `/system/version` returned `version=2.3.5`, `imageTag=2.3.5`, `gitCommit=aac3080c103c`; frontend `nginx -t` passed; recent backend error scan empty.
- `cloudcc-pagecomponent-publish`: `cloudcc publish pagecomponent customer-workbench .` -> success; published component id `6a503defe4b0a577cbba1f8a`, apiName `custc_202607y6ji407v`, version `10`, payload contains iframe `allow="microphone; clipboard-write"`.
- `cloudcc-custompage-update`: `cloudcc update customPage . customer_interaction_workbench @/tmp/task178-custompage-string-payload.json` -> success; current custom page id `6a503e1ee4b0a577cbba1f8b`, `renderVersion=V4.0`, component ref id `6a503defe4b0a577cbba1f8a`.
- `cloudcc-verify`: `cloudcc verify injectionPage . customer_interaction_workbench --expected-component-id 6a503defe4b0a577cbba1f8a --stale-policy warning` -> passed, issues `[]`.
- `public-smoke`: `https://x.agentcici.com/` and `/app?aiApp=customer-workbench&embed=crm` -> HTTP `200`.
- `prod-browser-mic-denied-regression`: Playwright production embed page with mocked `getUserMedia` rejection -> success; notice stayed `实时语音启动失败：CRM iframe microphone denied for test`, `hasEmptySpeechNotice=false`, `rootScrollable=false`. Screenshot: `output/playwright/task178-prod-embed-mic-denied-debug.png`.

## 变更文件

- `docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md`
- `.claw/tasks/TASK-178.md`
- `.claw/assignments/TASK-178.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `.claw/devops.md`
- `frontend/src/shared/useAsrVoiceInput.ts`
- `frontend/src/shared/useAsrVoiceInput.test.ts`
- `frontend/pagecomponents/customer-workbench/customer-workbench.vue`
- `frontend/build/customer-workbench.umd.min.js`
- `frontend/pagecomponents/customer-workbench/config.json`

## 交接说明

- 不输出、提交或记录密码、token、secret、cookie 或可复用凭据。
- 本任务实现已发布为生产 `2.3.5`；CloudCC CRM 自定义页已绑定 V10 组件。
