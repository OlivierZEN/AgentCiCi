---
kind: task-status
task_id: TASK-352
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
updated_at: 2026-09-01T14:51:11Z
updated_by: codex
---

# TASK-352 - 讯飞实时听写与结束状态收敛

## Goal

修复 AI 听记和对话框共用的讯飞实时听写链路，确保只有上游真正就绪后才进入录音态，实时结果可被解析，停止、错误和关闭均能确定结束。

## Scope

- 兼容平台历史保存的讯飞官方主机根 URL，运行时自动补齐协议路径。
- 修复讯飞最后帧解析、异常关闭和结束事件转发。
- 修复共享前端 hook 的 ready 门禁与一次性完成回调。
- 覆盖 AI 听记、嵌入听记和普通对话框话筒的共同链路。
- 完成自动化、构建、本地 `main` 制品和正式入口验证。

## Done When

- [x] 官方讯飞根 URL 在保存、读取、校验和运行时规范化。
- [x] 收到讯飞上游 `started` 后才申请麦克风和发送音频。
- [x] 官方 `data.ls=true` 空最后帧仍触发一次 `finished`。
- [x] 错误、上游关闭和停止超时均不永久停留在 recording/stopping。
- [x] 后端聚焦测试/package、前端聚焦/全量/build 通过。
- [ ] 修复提交进入本地 `main`，backend/frontend 从该提交运行且健康。
- [ ] 使用合成音频完成真实讯飞上游转写与结束技术探测。
- [ ] HUMAN 使用真实麦克风确认 AI 听记和对话框实时听写。

## Handoff

- 当前数据库的 `iflytek_asr.realtimeUrl` 是官方主机根路径；不直接改数据库，运行时和治理读写层负责向后兼容。
- 真实麦克风涉及浏览器权限和用户语音，仅在用户明确确认后执行；技术探测优先使用不含敏感内容的合成音频。
