---
kind: task-status
task_id: TASK-353
feature_id: FEAT-188
assignee: codex
owner_role: backend-agent
status: review
branch: main
pr_url: n/a
spec_path: docs/specs/FEAT-188-conversation-image-paste-attachments.md
updated_at: 2026-09-01T15:37:39Z
updated_by: codex
---

# TASK-353 - 图片追问跨轮视觉上下文修复

## Goal

修复图片首轮已识别、后续明确追问图片内容时模型却声称未收到图片的问题，同时避免在普通文本轮次中无条件重复发送历史图片。

## Scope

- 以同一会话最近 20 条消息为边界，仅识别明确的历史图片指代表达。
- 本轮没有新图片时，回取最近一个带图片的历史用户消息并恢复为多模态内容。
- 只接受同租户、同会话、同消息且状态为 `ATTACHED` 的图片附件。
- 历史图片与本轮图片共用可信 `vision` 能力门禁；不放宽 `VISION_MODEL_REQUIRED`。
- 覆盖普通 blocking 与 streaming 编排路径、附件读取和误触发负例。

## Done When

- [x] 真实故障会话证明首轮消息有关联图片、后续文本轮无新附件，且旧历史装配只含纯文本。
- [x] 明确“图片中 / 截图里 / 上一张图片”等追问会重新注入最近一张历史图片。
- [x] 普通图片能力咨询和无图片指代的文本轮不会重复注入历史图片。
- [x] 历史图片仍通过受信视觉能力门禁，跨租户、跨会话或错误消息关联均不采用。
- [x] 聚焦测试与 `git diff --check` 通过。
- [x] backend package 通过。
- [x] 修复提交、本地 `main` 制品与运行门禁通过。
- [ ] 使用既有真实图片会话完成后续追问回归；HUMAN 确认回答内容与图片匹配。

## Handoff

- 故障会话 `8f4dbc70-88d9-4aeb-b45b-23de41987ae4` 中，用户消息 99 关联 1 张图片，助手消息 100 已识别；用户消息 101 是零附件文本追问，助手消息 102 错误声称没有收到图片。
- 根因是 `buildRecentHistoryMessages` 只回灌 `role + content` 字符串，未恢复历史消息的附件内容；本轮附件门禁也只检查当前轮附件。
- 修复采用显式指代触发和最近一张历史图片上限，不恢复所有历史图片，不改变图片存储与额度契约。
- 修复提交 `123619a7223e` 已进入本地 `main`；并行语音提交随后推进主线至 `80f720730cd3`，后者包含本修复。共享开发环境已从最新主线构建并运行 `2.8.68-dev.80f7207`，backend/frontend healthy/restart=0。
