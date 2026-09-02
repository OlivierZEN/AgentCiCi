---
kind: task-status
task_id: TASK-355
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-054-ai-minutes-local-audio-upload.md
updated_at: 2026-09-02T02:08:00Z
updated_by: codex
---

# TASK-355 - AI 听记文件转写协议路由修复

## 范围

- 修复 `file-asr` 已选择同步 `qwen-audio-3.0-asr-flash`，后端却固定提交 Fun-ASR/Filetrans 异步任务导致 403 的协议错配。
- 用户明确要求上传录音也必须区分发言人；治理候选和运行时只允许支持说话人分离的 Filetrans/Fun-ASR 模型，不改变实时听记的讯飞路由。
- 复用现有阿里云厂商凭据，把 `file-asr` 路由迁移到 `qwen-audio-3.0-asr-flash-filetrans`；保留临时 OSS、异步轮询和 `diarization_enabled=true`。
- 同步 Flash 不再作为上传录音兜底，避免把多人录音伪装为单一发言人。
- 把上游 HTTP 错误转换为用户可理解的文件转写错误，不再落入 `Unexpected server error`。

## 完成条件

- [x] 真实失败日志和运行路由共同证明协议错配。
- [x] 治理候选只显示支持发言人分离的上传录音模型。
- [x] 运行时拒绝不支持发言人分离的同步模型。
- [x] 数据迁移把现有 `file-asr` 路由切换到 Filetrans。
- [x] 聚焦测试、后端 package 与 `git diff --check` 通过。
- [x] 修复提交进入本地 `main` 并从该提交重建 backend。
- [ ] 使用无敏感合成音频通过正式上传入口得到转写结果。
- [ ] 用户使用原录音完成 HUMAN 验收。

## 当前证据

- 失败请求已成功上传文件，但提交异步任务返回 `403 AccessDenied: current user api does not support asynchronous calls`。
- 运行 `file-asr` 为 `aliyun-bailian/qwen-audio-3.0-asr-flash`，专属地址为 `*.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`；密钥只确认已配置，不读取或记录明文。
- 阿里云官方协议把 `qwen-audio-3.0-asr-flash` 定义为同步文件转写，把 `qwen-audio-3.0-asr-flash-filetrans` / Fun-ASR 定义为异步文件转写。
- `qwen-audio-3.0-asr-flash-filetrans` 与现有异步请求协议匹配并支持说话人分离，继续使用同一百炼工作空间和已治理 API Key；不复制或改写密钥。
- `eed14231521c` 已进入本地 main；单元 `4/4`、package/diff 通过，治理集成测试因既有默认测试库不可达而中止。V133 事务预演和部署迁移均成功。
- backend 运行 `2.8.68-dev.eed1423`，image `sha256:6610f0d5...`，healthy/restart=0，health=`UP`；运行 `file-asr` 已回读为 `aliyun-bailian/qwen-audio-3.0-asr-flash-filetrans`，实时听记仍为讯飞，对话听写仍为 Paraformer。
- 正式入口多发言人录音上传与页面 speaker 分段待 HUMAN 复测；不以模型路由和健康检查替代真实业务验收。
