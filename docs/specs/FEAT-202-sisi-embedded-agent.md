---
kind: feature-spec
feature_id: FEAT-202
title: 思思嵌入式智能应用
status: implemented
owner_role: fullstack-agent
task_ids: TASK-332
related_decisions: FEAT-032,FEAT-179,FEAT-197
related_issues: none
updated_at: 2026-08-27T11:54:55Z
updated_by: codex
---

# FEAT-202 - 思思嵌入式智能应用

## 背景与目标

“思思”是 AgentCiCi 内部受治理智能体在外部业务系统中的固定产品映射。首个接入方为 CloudCC CRM：终端用户已经登录 CloudCC，但无需再登录 AgentCiCi；CloudCC 服务端使用受治理 API Key，以 CloudCC 组织 ID、当前绑定用户名、父页面 Origin 和业务上下文换取短时 Embed Token，浏览器只持有该短时 Token。

本次交付需要同时提供页面级 iframe 与悬浮浮窗，由同一 JS SDK 切换。对话能力覆盖流式回复、历史恢复、图片和文档附件、实时语音输入、知识依据、工具执行状态、服务端确认协议和可信回执。

## 范围

### In Scope

- 注册固定应用 `sisi`，外部名称和“思”字印章固定，运行 Agent 默认映射 `cici-system`。
- CloudCC 组织 ID 与 AgentCiCi 组织配置显式绑定；CloudCC 绑定用户名必须解析为当前组织 ACTIVE 成员。
- Embed Token 绑定应用、组织、CloudCC 组织 ID、成员、外部用户名、Origin、Agent、业务对象、Scope、唯一标识和过期时间。
- 同一外部用户、Agent 和业务对象在 Token 续期后恢复同一服务端会话；不同用户和业务对象相互隔离。
- `/embed/sisi` 页面模式与浮窗模式，共用真实 Agent 运行时、附件、ASR、知识检索和 Tool SSE。
- 稳定 SDK `/sdk/sisi.js` 与版本 SDK `/sdk/sisi@1.0.0.js`，支持 `page` / `float`、Token 刷新、上下文换票、开关、聚焦、销毁和事件订阅。
- 组织管理员在“嵌入式智能应用”配置允许 Origin、CloudCC 组织 ID、内部 Agent、运行成员、Scope 和 TTL，并生成短时调试 Token。
- 桌面浏览器、键盘、错误状态、Token 失效、上传与语音状态验证。

### Out Of Scope

- 本任务不发布 UAT 或生产，也不写入真实 CloudCC 业务数据。
- 不允许浏览器持有长期 API Key、CloudCC 安全标记或 AgentCiCi 登录 Token。
- 不接受任意前端 CSS、任意智能体品牌名或客户端覆盖 Agent ID。
- 不新增移动端专项布局；浮窗窄容器属于 SDK 桌面嵌入形态。
- 不绕过现有服务端高风险动作确认、权限、PDP、Tool allowlist、审计和幂等门禁。

## 用户与成功标准

- CloudCC 员工在客户、订单、工单等记录页打开思思，直接围绕当前记录对话。
- 用户能看到生成、检索和工具执行进度，展开知识依据，并区分“建议”“等待确认”“已执行回执”。
- Token 过期或业务上下文变化时 SDK 重新向 CloudCC 后端取票，不能把旧 Token 或旧上下文继续用于新对象。
- 页面模式和浮窗模式使用同一会话，切换容器不会丢失历史。

## 设计事实

- 已确认视觉方向为 `鎏金账房`：暖象牙画布、墨色文字、香槟金结构线、克制状态色。
- 固定形象为朱砂红“思”字印章，不使用真人头像、机器人、玻璃拟态、渐变或营销 Hero。
- 页面模式为“业务上下文 / 主对话 / 执行与依据”三栏；浮窗模式为 408px 不透明面板，证据与过程以内联折叠呈现。
- 主要动作是提问；证据、过程和确认次于阅读流。高风险确认不得以装饰性按钮伪造，只有服务端返回明确确认口令时才允许一键回填并发送。

## 身份与安全设计

1. CloudCC 后端持有 AgentCiCi 受治理 API Key，通过 `POST /embed/v1/apps/sisi/tokens` 换取 Token。
2. Token 请求必须携带 `source=cloudcc`、`externalTenantId=<CloudCC组织ID>`、`user.externalUserId=<CloudCC绑定用户名>`、精确 `parentOrigin` 和业务上下文。
3. 服务端以 API Key 确定 AgentCiCi 组织，不接受请求覆盖；配置中的 CloudCC 组织 ID 必须与请求一致。
4. 外部用户名必须精确解析为当前组织 ACTIVE `company_member.cc_username`，Token 内部执行身份使用该成员 ID，而不是共享 run-as 身份。
5. SDK 从自身 script URL 推导 AgentCiCi Origin；Token 通过 `postMessage` 发送到 iframe，不写入查询参数或本地存储。调试页面仅允许把短时 Token 放在 URL fragment。
6. 页面所有 API 均为同源相对路径；Embed Token 只允许 `/embed/v1/apps/*` 与受控 ASR WebSocket，不能调用普通 `/ai/*`。

## API 与事件

### Token 请求

```json
{
  "source": "cloudcc",
  "externalTenantId": "cloudcc-org-id",
  "parentOrigin": "https://reserved.invalid",
  "user": {
    "externalUserId": "cloudcc-bound-username",
    "displayName": "当前用户"
  },
  "context": {
    "objectType": "Account",
    "objectId": "record-id",
    "recordName": "客户名称",
    "customerName": "客户名称"
  },
  "permissions": ["chat:read", "chat:write", "attachment:write", "voice:input"],
  "ttlSeconds": 600
}
```

### Runtime API

- `POST /embed/v1/apps/sisi/sessions`
- `GET /embed/v1/apps/sisi/sessions/{sessionId}/messages`
- `POST /embed/v1/apps/sisi/sessions/{sessionId}/chat/stream`
- `GET|POST /embed/v1/apps/sisi/sessions/{sessionId}/attachments`
- `GET|DELETE /embed/v1/apps/sisi/sessions/{sessionId}/attachments/{attachmentId}`

### SDK / postMessage

- 宿主到 iframe：`host:init`、`host:update-token`、`host:focus`、`host:request-close`。
- iframe 到宿主：`embed:ready`、`embed:resize`、`embed:conversation-started`、`embed:token-required`、`embed:action-confirmed`、`embed:error`、`embed:close`。
- 所有消息包含固定 `source=agentcici-sisi-embed`、request ID 和时间戳；SDK 只接受目标 iframe window 且要求精确 AgentCiCi Origin。

## 附件与语音

- 每会话最多 10 个附件，单文件 20 MiB。
- 图片：PNG、JPEG、WebP；文档：TXT、Markdown、CSV、JSON、PDF、DOCX。
- 图片以多模态内容进入模型；文档在服务端提取受限长度文本后进入当前用户消息，不执行文档中的代码或宏。
- ASR 复用 `/ws/asr`，Embed Token 必须包含 `voice:input`（会议纪要保留 `meeting:start` 兼容）。

## 验收标准

- 未配置 CloudCC 组织绑定、用户名不存在/停用、Origin 不允许、Scope 越权、Token 过期或跨应用使用均失败关闭。
- 两名 CloudCC 用户或两个业务对象不能读取、上传或发送到彼此会话。
- Token 续期后同一身份和对象恢复同一会话；切换对象必须取得新 Token。
- 页面与浮窗完成默认、加载、流式、工具、依据、附件、语音、确认、成功、空、错误和 Token 过期状态。
- 后端定向测试、前端定向测试、全量前端测试、backend package、frontend build、域名扫描、SDK 语法检查、Flyway 迁移和真实浏览器桌面截图通过。
- 本地开发环境更新必须来自本地 `main` 明确提交，并联合回读 backend/frontend 镜像、版本、commit、页面制品、健康和 restart。

## 风险与回滚

- 身份绑定错误可能导致越权，所有身份解析和会话访问均使用服务端校验并返回同一 404 语义，避免枚举。
- 文档解析可能耗费资源，严格限制大小、数量、提取字符数并拒绝未知类型。
- SDK 与页面按 `1.0.0` 冻结；稳定 URL 可回退到上一版本文件，数据库 V124 只新增定义和会话审计表，不删除旧结构。
- 功能可通过组织级 `enabled=false` 或撤销 `chat:write` Scope 立即停用；会议纪要应用不受影响。

## 实现进展

- 2026-08-27：用户确认定位、CloudCC 身份模型、统一 SDK、完整多模态范围、固定名称形象、视觉探针 B/C 组合及最终 north-star mock。
- 2026-08-27：进入实现，目标为本地生产可用代码和技术验证；UAT/生产发布仍需独立授权。
- 2026-08-27：实现完成并通过 PostgreSQL V124/Embed 身份链集成测试、后端聚焦测试、前端全量测试与构建、SDK 语法和 Chromium 页面/408px 浮窗交互验收；等待归并本地 `main` 后更新统一开发环境。
- 2026-08-27：实现提交 `93572887` 已进入本地 `main` 并构建为 backend/frontend `2.8.67-dev.9357288`；V124、应用目录、SDK、正式页面、运行指纹、完整本地技术验证和稳定日志通过。真实 CloudCC 宿主换票与业务验收、远程推送及 UAT/生产发布继续作为独立授权门禁。
