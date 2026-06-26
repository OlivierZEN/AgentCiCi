---
kind: task-status
task_id: TASK-163
status: review
updated_at: 2026-06-26T09:18:00Z
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-163.yaml
spec_path: docs/specs/FEAT-073-email-id-refresh-and-voice-followup.md
---

# TASK-163 - 邮件 ID 刷新重试与语音后续可用性修复

## Scope

- 修复 POP3 `messageId` 失效后未自动刷新并读取正文的问题。
- 保留待展开邮件上下文，仅在正文读取成功后清除。
- 复核语音入口“等待当前回答结束”卡住问题，并补必要兜底。

## Plan

- 建立 FEAT-073 规格和 TASK-163 授权。
- 扩展会话状态里的待展开邮件上下文。
- 增加 `email_get_message` 失败后的自动搜索/重读。
- 跑 focused 后端测试、编译、静态检查；如改前端则跑前端测试/构建。
- 合并并发布生产热修。

## Verification

- `mvn test -Dtest=ChatOrchestratorServiceModelIdentityTest` in `backend/` -> success, 25 tests passed.
- `npm run build` in `frontend/` -> success; existing Vite large chunk warning remains.

## Changed Files

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatSessionStateService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/service/ChatOrchestratorServiceModelIdentityTest.java`
- `frontend/src/assistant/AssistantApp.tsx`
- `docs/specs/FEAT-073-email-id-refresh-and-voice-followup.md`
- `.claw/tasks/TASK-163.md`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`

## Handoff

- Branch: `codex/TASK-163-email-id-refresh-voice-followup`.
- 已实现 stale POP3 `messageId` 自动刷新重试：确认读正文时先尝试旧 ID，失败后用已保存主题/发件人重新搜索并立刻用新 ID 再读正文。
- 前端回答流结束后会先释放 `chatLoading`，历史刷新不再阻塞麦克风；同时增加 180 秒兜底释放，避免 SSE/网络异常导致语音入口一直显示等待当前回答结束。
- 未用自动化读取生产真实邮件正文，避免触碰真实邮件内容；发布后建议由用户在目标会话中复测同一邮件。
