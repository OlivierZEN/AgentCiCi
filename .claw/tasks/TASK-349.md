---
kind: task-status
task_id: TASK-349
assignee: codex
owner_role: fullstack-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-062-platform-model-provider-governance.md
updated_at: 2026-09-01T09:49:41Z
updated_by: codex
---

# TASK-349 - 讯飞实时语音转写纳入模型厂商治理

## Goal

把既有科大讯飞实时语音转写能力放入平台“模型厂商治理”列表，通过同一入口维护原有平台托管配置，不复制凭据或改变运行时事实源。

## Scope

- 模型厂商 API 投影既有讯飞平台托管记录，并提供保存与配置校验。
- 通用平台集成列表移除重复的讯飞入口。
- 模型厂商页面增加科大讯飞专用凭据表单和实时 ASR 能力说明。
- 补充聚焦后端、前端测试及桌面端本地验证。

## Done When

- [x] 模型厂商列表可见并可保存讯飞配置，Secret 只返回掩码。
- [x] 运行时继续读取既有 `integration_app(iflytek_asr)`。
- [x] 通用平台集成列表不重复展示讯飞。
- [x] 配置校验失败关闭并声明真实实时语音探测边界。
- [ ] 聚焦测试、构建、差异检查和桌面端视觉检查完成。

## Handoff

- 当前 `main` 存在 TASK-348 的未提交改动；本任务只修改模型治理、讯飞平台集成投影、FEAT-062 与 TASK-349 状态文件，不清理或夹带 TASK-348。
- 自动化已通过：前端全量 `61 files / 338 tests`、production build、后端编译、讯飞选择单测 `3/3`、平台集成 `1/1`、模型厂商集成 `1/1`、`git diff --check`。桌面端 `cici.localhost` 回读待从本地 `main` 制品完成。
