---
kind: task-status
task_id: TASK-179
title: AI 听记实时发言人分离热修
status: done
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md
assignment_path: .claw/assignments/TASK-179.yaml
updated_at: 2026-07-10T11:40:48+08:00
updated_by: MANAGER-001
---

# TASK-179 - AI 听记实时发言人分离热修

## 当前目标

恢复 AI 听记实时录音的说话人分离，同时保留未配置讯飞环境的阿里云实时转写可用性和明确降级提示。

## 已验证事实

- 两个 AI 听记实时入口均固定为 `provider: "aliyun"`、`speakerDiarization: false`。
- 阿里云实时 handler 的 `result-generated` 事件只转发文本，不包含 speaker 字段。
- 讯飞 handler 已支持 `role_type=2`、角色解析和 speaker 事件。

## 当前进展

- 已实现 `provider=auto`：可用讯飞配置时选择讯飞角色分离，否则使用阿里云并返回明确降级状态。
- 两个 AI 听记实时入口均已请求说话人分离；普通输入框语音保持阿里云行为不变。
- 本地测试、构建、真实工作台桌面验证与生产发布闭环均已完成。

## 计划

1. 完成任务分配、身份门禁与授权检查。
2. 实现会议场景自动 provider 选择和降级状态事件。
3. 切换两个 AI 听记入口并补充回归测试。
4. 运行后端定向测试/编译、前端定向测试/构建和桌面端验证。
5. 更新测试证据与热状态；如执行生产发布，按 runbook 完成 dry-run、备份、部署和 smoke。

## 本地验证

- TASK-179 `check-assignment.py` 与 task-scoped `dev-login.py`：`allowed`。
- 后端 `RealtimeAsrProviderSelectionTest,IflytekAsrResultParserTest`：7 tests passed。
- 前端 `useAsrVoiceInput.test.ts meetingTranscript.test.ts`：7 tests passed。
- 前端 `npm run build`：通过，仅保留既有 large chunk warning。
- 本地真实工作台：未配置讯飞组织启动 AI 听记后进入“录音中”，展示“本次无法自动区分发言人”；停止后录音状态释放，浏览器 console error 为 0。
- `git diff --check`：通过。

## 生产验证

- `release-acr.sh --dry-run`：版本 `2.3.7`，Git commit `01a5df8cb919`，镜像与应用版本一致。
- `release-acr.sh --version 2.3.7`：backend/frontend linux/amd64 镜像推送、inspect 与 Git tag 推送成功。
- 生产备份：`/opt/cici/backups/20260710-113712-before-2.3.7-task179-ai-minutes-speaker`，包含 PostgreSQL、环境文件、知识库和 Qdrant 备份。
- 生产部署：backend/frontend `2.3.7` healthy；基础设施容器保持 `2.3.4` healthy；`/actuator/health=UP`；`/system/version` 返回 `version=imageTag=2.3.7`、`gitCommit=01a5df8cb919`；Nginx 配置通过。
- 公网 smoke：`https://x.agentcici.com/` 与 `/app` 返回 200，HTTP 根路径返回 HTTPS 301。
- 生产浏览器：演示组织显示版本 `2.3.7`；AI 听记进入录音中且无讯飞配置错误、无阿里云 diarization 降级提示，说明自动选择已配置讯飞分支；停止后录音状态释放，console error 为 0。
- 后端日志：未发现 ASR/讯飞失败；存在两条非 ASR 的旧会话 `Session not found` 404，已记录为独立日志噪声。

## 变更文件

- `backend/src/main/java/com/codehouse/ciciassistant/ai/ws/AliyunRealtimeAsrWebSocketHandler.java`
- `backend/src/test/java/com/codehouse/ciciassistant/ai/ws/RealtimeAsrProviderSelectionTest.java`
- `frontend/src/shared/useAsrVoiceInput.ts`
- `frontend/src/shared/useAsrVoiceInput.test.ts`
- `frontend/src/assistant/AssistantApp.tsx`
- `frontend/src/embed/EmbedMeetingMinutesPage.tsx`
- `docs/specs/FEAT-059-ai-minutes-local-asr.md`
- `docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md`

## 交接约束

- 不修改普通语音输入 provider。
- 不新增移动端适配或移动端自动化测试。
- 不输出、提交或记录任何凭证或可复用会话信息。
- 本任务已发布到生产 `2.3.7`；继续观察真实多人会议的 speaker 质量，不把角色分离解释为实名声纹识别。
