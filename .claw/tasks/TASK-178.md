---
kind: task-status
task_id: TASK-178
title: CRM 嵌入客户互动工作台语音输入热修
status: in_progress
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-088-crm-workbench-voice-input-hotfix.md
assignment_path: .claw/assignments/TASK-178.yaml
updated_at: 2026-07-10T08:45:00+08:00
updated_by: MANAGER-001
---

# TASK-178 - CRM 嵌入客户互动工作台语音输入热修

## 当前目标

修复 CloudCC CRM 端嵌入客户互动工作台里 AI 助手语音输入点击后提示“未识别到有效的语音内容”的问题，确保嵌入 iframe 具备麦克风权限，并避免 ASR 启动失败被误报为空语音。

## 当前进展

- 已读取项目热状态、任务板、TASK-173/TASK-175 相关上下文。
- 已定位风险点：pagecomponent iframe 缺少 `allow="microphone"`；`useAsrVoiceInput` 启动失败后 `abort()` 会触发空完成回调覆盖真实错误。
- 已实现：pagecomponent iframe 增加 `allow="microphone; clipboard-write"`；ASR hook 启动失败或主动 abort 不再触发空完成回调。
- 本地验证已通过，待提交后按生产 runbook 发布。

## 计划

1. 建立 FEAT-088、TASK-178 和授权边界。`done`
2. 修复 ASR hook 启动失败/主动 abort 回调语义。`done`
3. 为 CloudCC pagecomponent iframe 和 UMD bundle 增加 microphone allow。`done`
4. 运行前端测试、构建、CloudCC package dry-run 和浏览器/DOM 验证。`done`
5. 发布到生产和 CloudCC CRM，并记录验收证据。`pending`

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
- 当前工作区存在 TASK-177 数据洞察相关未提交改动，本任务必须避开。
