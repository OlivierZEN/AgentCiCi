---
kind: task-status
task_id: TASK-173
status: review
updated_at: 2026-07-10T00:15:08+08:00
updated_by: MANAGER-001
assignee: MANAGER-001
owner_role: fullstack-agent
assignment_path: .claw/assignments/TASK-173.yaml
spec_path: docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md
---

# TASK-173 - 客户互动工作台真实智能体助理

## Scope

- 清理客户互动工作台右侧 AI 助理中用户截图划掉的大语音条和快捷按钮。
- 将 `/customer-workbench/assistant` 从规则回复升级为真实智能体编排调用。
- 复用现有 `/ws/asr` 阿里云实时语音录入能力。
- 完成本地编译、测试、桌面端验证和状态回写。

## Initial Findings

- 当前右侧助理的大语音条为 `.customer-workbench__voice`。
- 当前四个快捷按钮区域为 `.customer-workbench__quick`。
- 当前工作台语音使用浏览器 `SpeechRecognition`，未复用生产 ASR hook。
- `useAsrVoiceInput` 已封装 `/ws/asr` 实时语音链路，并在主助手、会议听记等入口使用。
- `/ai/chat` 的 `ChatOrchestratorService` 已承载智能体解析、权限校验、模型路由、工具、工作流、Trace 和计量。

## Implementation Plan

- 建立 FEAT-083、TASK-173 和授权边界。
- 后端 `CustomerWorkbenchService.assistant(...)` 接入 `ChatOrchestratorService`。
- 前端 `CustomerWorkbenchApp` 删除被划掉控件，接入 `useAsrVoiceInput`。
- 清理废弃样式并补充录音态样式。
- 增加或更新聚焦测试，运行真实构建验证。

## Verification

- `identity-gate`: skill-packaged `dev-login.py .claw --developer MANAGER-001 --task TASK-173 --branch main --files ... --json` -> allowed.
- `assignment-check`: skill-packaged `check-assignment.py .claw --developer MANAGER-001 --task TASK-173 --branch main --files ... --json` -> allowed.
- `backend-compile`: `mvn -q -f backend/pom.xml -DskipTests compile` -> success.
- `backend-agent-orchestrator-smoke`: `mvn -q -f backend/pom.xml -Dtest=OrchestratorIntegrationTest#shouldExposeCloudccDiscoveryToolsForDefaultCiciAgent test` -> success.
- `backend-customer-assistant-unit`: `mvn -q -f backend/pom.xml -Dtest=CustomerWorkbenchServiceTest test` -> success.
- `frontend-build`: `npm --prefix frontend run build` -> success; existing Vite large chunk warning remains.
- `frontend-asr-hook-test`: `npm --prefix frontend run test -- useAsrVoiceInput` -> success, 3 tests passed.
- `static-check`: `git diff --check -- ...` for TASK-173 touched files -> success.
- `desktop-visual-check`: local preview `http://127.0.0.1:4173/app?aiApp=customer-workbench` with mocked authenticated APIs at 1440x900 -> success; `.customer-workbench__voice=0`, `.customer-workbench__quick=0`, composer microphone enabled.

## Changed Files

- `docs/specs/FEAT-083-customer-workbench-real-agent-assistant.md`
- `.claw/tasks/TASK-173.md`
- `.claw/assignments/TASK-173.yaml`
- `.claw/task-board.md`
- `.claw/current-status.md`
- `.claw/test-report.md`
- `backend/src/main/java/com/codehouse/ciciassistant/customer/service/CustomerWorkbenchService.java`
- `backend/src/test/java/com/codehouse/ciciassistant/customer/service/CustomerWorkbenchServiceTest.java`
- `frontend/src/assistant/customer-workbench/CustomerWorkbenchApp.tsx`
- `frontend/src/assistant/customer-workbench/customerWorkbenchApi.ts`
- `frontend/src/assistant/cici-ui.css`

## Handoff

- Implementation is ready for review and release planning.
- Production deployment was not performed in this task turn.
- The right assistant now relies on `cici-system` plus `customer-interaction-workbench`; if a tenant lacks `cici-system` grants, the runtime access-control path will reject the run as designed.
