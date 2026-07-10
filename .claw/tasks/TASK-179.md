---
kind: task-status
task_id: TASK-179
title: AI 听记实时发言人分离热修
status: ready
owner_role: fullstack-agent
assignee: MANAGER-001
spec_path: docs/specs/FEAT-089-ai-minutes-speaker-diarization-hotfix.md
assignment_path: .claw/assignments/TASK-179.yaml
updated_at: 2026-07-10T11:24:00+08:00
updated_by: MANAGER-001
---

# TASK-179 - AI 听记实时发言人分离热修

## 当前目标

恢复 AI 听记实时录音的说话人分离，同时保留未配置讯飞环境的阿里云实时转写可用性和明确降级提示。

## 已验证事实

- 两个 AI 听记实时入口均固定为 `provider: "aliyun"`、`speakerDiarization: false`。
- 阿里云实时 handler 的 `result-generated` 事件只转发文本，不包含 speaker 字段。
- 讯飞 handler 已支持 `role_type=2`、角色解析和 speaker 事件。

## 计划

1. 完成任务分配、身份门禁与授权检查。
2. 实现会议场景自动 provider 选择和降级状态事件。
3. 切换两个 AI 听记入口并补充回归测试。
4. 运行后端定向测试/编译、前端定向测试/构建和桌面端验证。
5. 更新测试证据与热状态；如执行生产发布，按 runbook 完成 dry-run、备份、部署和 smoke。

## 交接约束

- 不修改普通语音输入 provider。
- 不新增移动端适配或移动端自动化测试。
- 不输出、提交或记录任何凭证或可复用会话信息。
