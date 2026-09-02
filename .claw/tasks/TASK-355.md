---
kind: task-status
task_id: TASK-355
status: in_progress
priority: critical
owner_role: backend-agent
claimed_by: codex
spec_path: docs/specs/FEAT-054-ai-minutes-local-audio-upload.md
updated_at: 2026-09-02T01:31:55Z
updated_by: codex
---

# TASK-355 - AI 听记文件转写协议路由修复

## 范围

- 修复 `file-asr` 已选择同步 `qwen-audio-3.0-asr-flash`，后端却固定提交 Fun-ASR/Filetrans 异步任务导致 403 的协议错配。
- 根据治理路由中的实际模型选择同步或异步文件转写协议，不改变实时听记的讯飞路由。
- 同步模型通过专属 DashScope 多模态生成接口接收 Base64 Data URL；异步 Filetrans/Fun-ASR 保留临时 OSS、轮询和说话人分离。
- 把上游 HTTP 错误转换为用户可理解的文件转写错误，不再落入 `Unexpected server error`。

## 完成条件

- [x] 真实失败日志和运行路由共同证明协议错配。
- [x] 模型名可以确定性选择同步或异步协议。
- [x] 同步响应可转换为现有 transcript 数据结构。
- [x] 聚焦测试、后端 package 与 `git diff --check` 通过。
- [x] 修复提交进入本地 `main` 并从该提交重建 backend。
- [ ] 使用无敏感合成音频通过正式上传入口得到转写结果。
- [ ] 用户使用原录音完成 HUMAN 验收。

## 当前证据

- 失败请求已成功上传文件，但提交异步任务返回 `403 AccessDenied: current user api does not support asynchronous calls`。
- 运行 `file-asr` 为 `aliyun-bailian/qwen-audio-3.0-asr-flash`，专属地址为 `*.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`；密钥只确认已配置，不读取或记录明文。
- 阿里云官方协议把 `qwen-audio-3.0-asr-flash` 定义为同步文件转写，把 `qwen-audio-3.0-asr-flash-filetrans` / Fun-ASR 定义为异步文件转写。
- 修复 `f668a2f06c38` 已进入本地 main；backend 运行 `2.8.68-dev.f668a2f`、healthy/restart=0，health=`UP`，文件/实时两条路由保持预期。Chrome 控制接口不支持向文件选择器注入合成文件，未读取浏览器存储或绕过身份；正式入口真实上传待 HUMAN 复测。
