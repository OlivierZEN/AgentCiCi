---
kind: task-status
task_id: TASK-351
feature_id: FEAT-188
assignee: codex
owner_role: backend-agent
status: in_progress
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
updated_at: 2026-09-01T14:31:00Z
updated_by: codex
---

# TASK-351 - OneKeyToken 自动路由视觉能力同步

## Goal

让平台校验 `onekeytoken/auto` 时以 OneKeyToken `/models` 目录为能力事实源，动态保存自动路由模型的 `vision` 能力，使图片消息能够通过 AgentCiCi 的可信能力门禁并继续由 OneKeyToken 自动选取视觉模型。

## Scope

- 保留既有 `onekeytoken/auto` Chat Completions 活性校验及草稿凭据语义。
- 校验成功后用同一组有效配置读取 `/models`，解析并保存 `capabilities` / `input_modalities`。
- 不按模型名猜测视觉能力，不放宽 `VISION_MODEL_REQUIRED` 失败关闭，不修改附件与租户隔离契约。
- 增加草稿凭据、远端目录和 `text + vision` 可信能力回归测试。

## Done When

- [x] OneKeyToken 校验不再把 `onekeytoken/auto` 硬编码为仅 `text`。
- [x] 远端目录声明 `vision` / `image` 时，平台能力目录保存 `text + vision` 且来源为 `provider_catalog`。
- [x] 聚焦集成测试、相邻附件/模型身份测试、backend package 和 diff check 通过。
- [ ] 实现提交进入本地 `main`，从该提交构建并最小更新本地 backend。
- [ ] `cici.localhost` 回读版本、健康、restart 和运行数据库能力证据。
- [ ] 使用当前 `onekeytoken/auto` 完成真实图片会话回归，或明确记录仍待 HUMAN 的验收边界。

## Handoff

- 根因已确认：OneKeyToken 网关的 `auto` 端点本身支持图片并实际路由到 `qwen3.7-plus`，AgentCiCi 平台校验却把该虚拟模型能力固定保存为 `["text"]`。
- 最小实现改为校验成功后读取远端 `/models` 并持久化可信能力；不改变自动路由请求模型名和运行时路由契约。
- 目标方法级集成回归 2/2、相邻单元测试 3 类及 backend package 已通过；完整集成类仍被既有 OACT 测试配置漂移中的无关组织登录用例阻断，不宣称整类通过。
