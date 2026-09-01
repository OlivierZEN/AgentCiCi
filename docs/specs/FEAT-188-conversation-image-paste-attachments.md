---
kind: feature-spec
feature_id: FEAT-188
title: 对话框连续粘贴图片附件
status: verified
primary_project: agentcici
owner_role: fullstack-agent
task_ids: TASK-309,TASK-336,TASK-338,TASK-342,TASK-351
related_issues: ISSUE-2026-08-28-vision-capability-scope,ISSUE-2026-09-01-onekeytoken-auto-vision-capability
updated_at: 2026-09-01T14:31:00Z
updated_by: codex
---

# FEAT-188 - 对话框连续粘贴图片附件

## 背景与目标

DevAutopilot 需求 `REQ-6F34ECF3` 的端到端任务 `019ffeb0-88a0-739f-afcb-6e667e9d2572` 已完成设计评审并由用户批准。员工在 AgentCiCi 助手工作台输入框聚焦时，需要通过 Ctrl+V / Command+V 连续粘贴本地截图，看到缩略图与上传状态，并把图片随本轮消息交给已确认 vision 能力的模型。

## 范围

### In Scope

- `/` 助手工作台的统一图片附件队列：文件选择、一次多张粘贴、连续多次粘贴、缩略图、大小、状态、删除与失败替换。
- 单张图片服务端实际字节数不超过 20 MiB；同一会话有效图片累计不超过 10 张。
- HUMAN 登录态下的会话附件上传、读取、删除和消息关联 API；tenant、user、session 三层隔离。
- 图片附件元数据、会话额度槽位、消息关联和本地受管文件存储。
- 当前聊天模型具备平台已确认 `vision` 能力时，以 OpenAI-compatible multimodal content 传入本轮图片；不具备时失败关闭并保留已上传附件。
- 纯文本旧请求、知识库、Agent、Skill、工具调用和流式响应兼容。

### Out Of Scope

- 图片编辑、OCR 产品能力、跨会话素材库、任意文件附件、移动端专项适配。
- UAT 或生产发布；需本地开发测试完成后另行取得发布授权。
- 把附件复制到 DevAutopilot、Semattice 或其他仓库形成第二事实源。

## 用户流程

1. 用户粘贴或选择 PNG/JPEG/WebP 图片，前端先校验类型、20 MiB 和剩余数量。
2. 合格图片进入队列并逐项上传；服务端按实际内容签名复验类型和字节数，并以 1..10 的唯一槽位保证并发下不超额。
3. 用户可删除尚未关联消息的附件并释放槽位；失败项保留原因以及删除、替换入口。
4. 发送时携带 `attachmentIds`；服务端确认附件 READY、属于当前主体和会话，在保存用户消息的同一事务中关联附件。
5. 模型具备已确认 vision 能力时，当前用户消息由文本和 `image_url` data URL 组成；否则返回明确的能力错误，附件保持可恢复。

## 接口与数据

- `POST /ai/sessions/{sessionId}/attachments`：multipart `file` + `clientAttachmentId`，同步上传并返回附件视图。
- `GET /ai/sessions/{sessionId}/attachments`：返回当前用户在该会话中的有效附件与 10 张额度。
- `GET /ai/sessions/{sessionId}/attachments/{attachmentId}/content`：鉴权读取图片内容。
- `DELETE /ai/sessions/{sessionId}/attachments/{attachmentId}`：只允许删除未关联消息的附件。
- `/ai/chat` 与 `/ai/chat/stream` 的 `ChatRequest` 新增 `attachmentIds`；允许图片存在时正文为空。
- 新表 `chat_attachment`：public id、company/user/session、slot、client id、message id、文件名、MIME、size、SHA-256、storage path、状态与审计时间。
- 唯一键 `(company_id, session_id, slot_no)` 从数据库层限制每会话最多 10 个有效附件；未关联删除采用物理删除释放槽位，已关联附件保留。

## 错误语义

- `ATTACHMENT_TOO_LARGE`：HTTP 413。
- `CONVERSATION_IMAGE_LIMIT_EXCEEDED`：HTTP 409。
- `UNSUPPORTED_IMAGE_TYPE`：HTTP 415。
- `ATTACHMENT_NOT_READY` 或重复消费：HTTP 409。
- `VISION_MODEL_REQUIRED`：HTTP 409，表示当前聊天路由模型未确认 vision 能力。
- 无权读取的 tenant/user/session 附件按非泄露原则返回 404。

## 验收标准

- 20 MiB 图片通过，20 MiB + 1 字节失败；PNG/JPEG/WebP 文件签名与 MIME 不匹配失败。
- 同会话第 10 张成功、第 11 张失败；并发竞争不能突破 10 张。
- 删除未发送附件释放额度；跨用户、跨会话、跨租户读取与引用失败。
- 连续粘贴、多选、缩略图、上传中/失败/已就绪、删除与替换状态可用；键盘粘贴不破坏文本粘贴。
- 流式聊天只在所有本轮附件 READY 后发送，发送成功后附件与消息关联；失败时队列可恢复。
- 聚焦后端测试、前端测试、前端 production build、完整 backend package、`git diff --check`、桌面真实浏览器交互和本地 `cici.localhost` 版本/健康门禁通过。

## 风险与回滚

- 大图片会放大模型请求体：只注入本轮附件，不在后续轮次重复注入历史图片；服务端严格限制单图和会话总数。
- 文件写入与数据库事务不完全原子：失败路径删除新建文件；未引用过期清理作为后续受管运维任务，不在本轮直接删除业务数据。
- 回滚时先关闭前端入口并回滚消费代码；保留 `chat_attachment` 表与已关联文件，避免历史消息引用损坏。

## 实现进展

- 设计批准事件：`019fffb8-c211-7e87-abee-7f9b583628f2`。
- 2026-08-14：后端附件 API、V116、消息关联和 vision 能力门禁已实现；前端连续粘贴、选择、缩略图、上传状态、删除/替换、图片-only 发送和历史鉴权预览已接通。后端 47 项定向测试、skip-tests package、前端 50 文件/278 项全量测试、production build 和 diff check 通过；共享测试库仍被既有 V81 checksum 漂移阻断。待本地 main 提交、`cici.localhost` 迁移与桌面真实浏览器验收。

## 2026-08-28 普通租户视觉能力误判修复

- 用户截图复现附件已上传，但 `/ai/chat/stream` 返回 `409 VISION_MODEL_REQUIRED`。
- 运行模型路由、凭据和能力目录均由平台治理组织统一管理；旧视觉门禁却用业务组织 ID 查 `model_provider_config`，普通租户没有该行，因此即使平台 `chat` 模型已确认 `vision` 也会被误判。
- 修复只统一能力事实源，不按模型名推断、不把未确认模型放行，也不改变附件、租户隔离或失败不落消息的既有契约。
- 验收新增：任意普通租户使用平台已确认 `vision` 的当前聊天模型时必须通过门禁；未确认能力仍返回 `VISION_MODEL_REQUIRED`。
- 本地验收：实现 `036c12a0d006` 进入本地 main，backend/frontend 同为 `2.8.67-dev.036c12a`；已登录普通租户真实粘贴用户原截图，`qwen3.7-plus` 分别识别出 `409` 和 `VISION_MODEL_REQUIRED`，能力误判已消除。远程、UAT、生产未修改。

## 2026-08-28 UAT 技术发布

- 修复已随冻结 `2970bea75208` 发布为 UAT `2.8.67-beta.1`；远程 `main`、tag、前后端不可变镜像和运行 commit 一致，未更新 `latest`。
- 完整备份、最小 backend/frontend 切换、四状态服务 ID 保持、六容器健康、V125、Nginx、公开/匿名门禁和稳定窗口通过；生产保持 `2.8.66`。
- 技术发布不替代登录态业务接受；需由已登录 UAT 用户粘贴图片，确认上传成功、模型实际识别内容且不再返回错误 `VISION_MODEL_REQUIRED`。

## 2026-08-28 生产发布

- 用户明确确认 UAT HUMAN 验收通过；冻结 `2.8.67-beta.1 / 2970bea75208` 原样晋级为生产 `2.8.67`，正式与 UAT tag、运行 commit 一致，本地后续功能未混入候选。
- backend/frontend 正式不可变 digest、完整备份与 `2.8.66` 回滚点、最小切换、四状态服务 ID 保持、六容器健康、V125、Nginx、公开/匿名门禁、数据计数守恒和累计 100 秒稳定窗口通过。
- 技术发布未代替生产登录用户执行真实图片上传或模型识别；生产 HUMAN 业务接受仍需由已登录用户完成。

## 2026-09-01 OneKeyToken 自动路由能力同步

- 最短路径复现证明 OneKeyToken `onekeytoken/auto` 本身接受同一 PNG，并以 `request_type=vision` 自动路由到 `qwen3.7-plus`；AgentCiCi 的 409 来自本地可信能力目录把 `onekeytoken/auto` 固定为仅 `text`，不是聚合服务不支持多模态。
- OneKeyToken 平台校验继续先用 `model=onekeytoken/auto` 执行 Chat Completions 活性探测；成功后必须用同一组有效配置读取 `/models`，以远端 `capabilities` 和 `input_modalities` 保存受信能力，来源为 `provider_catalog`。
- 不根据自动路由实际选中的下游模型反推或固定能力；远端目录不可用、未声明视觉能力或校验失败时继续失败关闭，图片聊天仍返回 `VISION_MODEL_REQUIRED`。
- 本轮仅修改 AgentCiCi 后端校验与能力同步，不改变 OneKeyToken 自动路由协议、附件数据、路由模型名或其他产品。
