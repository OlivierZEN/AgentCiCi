---
kind: task-status
task_id: TASK-349
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
updated_at: 2026-09-01T13:34:35Z
updated_by: codex
---

# TASK-349 - 讯飞实时语音转写纳入模型厂商治理

## Goal

把既有科大讯飞实时语音转写能力放入平台“模型厂商治理”列表，通过同一入口维护原有平台托管配置，不复制凭据或改变运行时事实源。

## Scope

- 模型厂商 API 投影既有讯飞平台托管记录，并提供保存与配置校验。
- 通用平台集成列表移除重复的讯飞入口。
- 模型厂商页面增加科大讯飞专用凭据表单和实时 ASR 能力说明。
- 修复 `voice-asr` 场景候选只读取通用模型目录且只允许阿里云的问题，提供固定讯飞适配器候选并恢复实际 WebSocket 路由。
- 补充聚焦后端、前端测试及桌面端本地验证。

## Done When

- [x] 模型厂商列表可见并可保存讯飞配置，Secret 只返回掩码。
- [x] 运行时继续读取既有 `integration_app(iflytek_asr)`。
- [x] 通用平台集成列表不重复展示讯飞。
- [x] 配置校验失败关闭并声明真实实时语音探测边界。
- [x] 启用且凭据完整的讯飞配置可作为 `voice-asr` 候选保存，运行时解析并进入讯飞 WebSocket 适配器。
- [x] 聚焦测试、构建、差异检查、本地正式路由和制品指纹回读完成。
- [ ] 平台管理员重新登录后完成授权态桌面视觉确认，并使用真实讯飞凭据完成一次实时语音识别。

## Handoff

- 2026-09-01 用户授权态截图确认：讯飞厂商配置已存在，但 `voice-asr` 仍显示候选 0。根因是场景白名单只允许 `aliyun-bailian`、候选只读取 `model_provider_config`，且 `/ws/asr` 硬性拒绝非阿里云路由；本轮已重新打开任务修复。

- 实现提交 `944898f8` 已进入本地 `main`，其父提交 `1ffc9092` 为已独立提交的 TASK-348；本任务没有清理、回退或重复提交 TASK-348 文件。
- 自动化已通过：前端全量 `61 files / 338 tests`、production build、后端编译、讯飞选择单测 `3/3`、平台集成 `1/1`、模型厂商集成 `1/1`、`git diff --check`。
- backend/frontend 从 `main@944898f8` 运行 `2.8.68-dev.944898f / 944898f8b956`，healthy/restart=0；backend 镜像 JAR SHA-256 与干净构建产物一致，正式路由和带版本资源为 200，30 秒稳定窗口 severe/结构化 5xx 均为 0。
- 应用内浏览器与 Chrome 的平台会话都已过期并回到安全登录；未读取存储或代填密码，因此授权态桌面视觉仍待 HUMAN。当前“校验配置”不替代真实实时语音识别。
