---
kind: feature-spec
feature_id: FEAT-119
title: OneKeyToken 实时凭据检测修复
status: approved
task_id: TASK-214
source: https://onekeytoken.com/docs.html
updated_at: 2026-07-21T00:00:00Z
---

# OneKeyToken 实时凭据检测修复

## 背景与问题

运营端“模型配置”将 OneKeyToken 的模型目录标记为静态目录。现有“检测”复用目录读取逻辑，只返回内置模型名而不访问 OneKeyToken。因此错误 Key、失效 Key 和未保存的新 Key 均会被误报为检测成功。

## 目标与范围

- OneKeyToken 的“检测”必须验证当前输入的 API 地址与 API Key，且不保存草稿配置。
- 成功必须代表一次真实、非流式 Chat Completions 调用已被网关接受。
- 失败要在原有表单的错误区展示可操作、已脱敏的信息；不得泄露 Key 到接口响应、审计或日志。
- 静态模型目录仍可用于“全部模型”展示，但不得再被用作凭据有效性的依据。

## OneKeyToken 接入契约（由源文档转写）

- 生产 Base URL 为 `https://my.onekeytoken.com/v1`，实际检测端点为 `POST /chat/completions`。
- 使用 `Authorization: Bearer <OneKeyToken Key>` 和 `Content-Type: application/json`。
- 每次业务调用都必须生成在同一应用或账号范围内唯一的 `x-request-id`；重复 ID 会返回 `409 conflict`。
- 请求使用 OpenAI Chat Completions 兼容 JSON；推荐 `model: onekeytoken/auto`、非流式 `stream: false`，并含至少一条 `messages` 用户消息。
- 应用接入 Key 可传 `x-customer-id`，如传 `x-user-id` 则必须同时传客户 ID。本次平台凭据检测不伪造终端用户归因头。
- 响应可读取 `choices[0].message.content`、`usage` 与 `routing`；检测只保存必要的非敏感结果，不返回模型正文或 Key。
- `401`、`403`、`400`、`409`、网络失败和非预期响应均为检测失败，不能被静态目录掩盖。

## 设计与交互

运营人员在明亮办公环境的高密度运营工作台中维护上游模型凭据，需要快速确认“表单草稿是否真实可用”，而不是确认本地预置是否存在。保持现有鎏金账房表单结构、按钮位置、桌面密度和主次按钮语义：点击“检测”时按钮进入禁用加载状态；成功只提示已验证的路由模型和可用模型目录数；失败保留用户输入、在现有错误区说明失败原因，并可更正后重试。无需新增弹窗、卡片、页面视觉或移动端适配。

## 服务端设计

- `POST /platform/models/providers/{providerCode}/check` 接收可选草稿 `apiBaseUrl`、`apiKey` 与 `enabled`，仅用于本次检测，不调用保存逻辑。
- 非 OneKeyToken 厂商维持现有配置检测语义；OneKeyToken 走专用实时验证。
- OneKeyToken 验证使用有效的草稿值覆盖已保存值。地址为空时回退到已保存地址；Key 为空时回退到已保存 Key。厂商未启用或最终 Key 为空时直接失败。
- 请求 `POST {baseUrl}/chat/completions`，使用 `Authorization`、`Content-Type`、唯一 `x-request-id`，正文为最小 `onekeytoken/auto` 非流式请求。
- 仅 HTTP 成功且响应是可识别的 Chat Completion 才返回 `ok: true`；结果仍携带静态目录信息，但明确 `catalogSource: static`、`remoteFetchSupported: false`。
- 不将请求 Key、Authorization 值、完整上游错误体或响应正文写入异常、审计或 API 响应。

## 验收标准

1. 错误或空的 OneKeyToken Key 点击检测返回失败，前端不会显示“检测成功”。
2. 有效 Key 的检测调用 Chat Completions，携带 Bearer 鉴权、唯一 `x-request-id` 与 `stream: false`，并显示成功。
3. 在未点击保存前修改地址或 Key，检测使用修改后的草稿，持久化配置不变。
4. “全部模型”仍展示预置目录，但不会作为授权成功证据。
5. 后端与前端自动化测试覆盖成功、401/403、无 Key、未保存草稿和无泄露约束；桌面端检查覆盖 loading、success、error 与重试。

## 非目标

- 不调整 OneKeyToken 的计费、客户钱包、模型路由或实际运行时调用。
- 不自动保存检测草稿，不加入终端用户归因头，不新增移动端适配。
