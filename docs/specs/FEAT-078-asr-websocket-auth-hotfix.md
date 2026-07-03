# FEAT-078 ASR WebSocket 鉴权与线上语音入口修复

## 背景

2026-07-03，线上版本 `2.1.11` 出现两个相关问题：

- AI 听记点击“开始听记”后麦克风无法启动。
- 对话窗口麦克风无法识别到语音，并提示未识别到有效语音内容。

两处入口共用前端 ASR 语音输入 hook，都会先建立 `/ws/asr` WebSocket，再打开浏览器麦克风和发送音频分片。

## 线上现象

- 生产健康检查正常，`/system/version` 返回 `2.1.11`。
- Nginx 访问日志显示浏览器多次请求 `/ws/asr?token=...`，返回 `401`。
- 手工 WebSocket 握手携带 query token 仍返回 `401 {"message":"Authentication required"}`。
- 后端 ASR handler 未产生日志，说明请求在进入 WebSocket handler 之前被租户过滤器拦截。

## 根因

`TenantContextFilter` 对除公共路径以外的请求强制要求 `Authorization: Bearer ...`。浏览器原生 WebSocket 不能设置自定义 `Authorization` header，因此前端按后端 handler 设计将 JWT 放在 query token 中。

当前 `/ws/asr` 未被标记为公共握手路径，导致过滤器在 WebSocket handler 读取并校验 query token 之前直接返回 401。前端等待 WebSocket `open` 成功后才调用 `getUserMedia`，所以用户看到的是麦克风无法启动或语音无法识别。

## 设计

- 将 `/ws/asr` 作为 WebSocket 握手入口从 `TenantContextFilter` 的强制 header 鉴权中排除。
- 安全校验仍由 `AliyunRealtimeAsrWebSocketHandler.afterConnectionEstablished(...)` 完成：
  - 无 query token：关闭 WebSocket。
  - query token 无效或过期：关闭 WebSocket。
  - token 有效：建立 ASR 会话上下文。
- 只放行 `/ws/asr` 与其子路径，不放宽其他 `/ws/*` 或普通业务 API。

## 验收标准

- 无 `Authorization` header 的 `/ws/asr` 请求不会被 `TenantContextFilter` 返回 `Authentication required`。
- `/auth/me`、`/me/email-accounts` 等普通受保护接口继续无 token 返回 401。
- 线上携带有效 query token 的 WebSocket 握手可以成功进入 ASR handler。
- AI 听记和对话窗口语音入口可以拉起麦克风并进入语音识别链路。

## 非目标

- 不更换 ASR 供应商。
- 不改前端语音 UI 视觉。
- 不调整普通 REST API 的租户鉴权策略。
- 不在日志或文档中记录生产 JWT token。
