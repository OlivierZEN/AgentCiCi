# 邮件收发内置工具设计文档

更新时间：2026-04-19（v2，依据确认反馈更新）
适用项目：`cc-cici-assistant`
状态：**已确认，可进入实现**

## 确认纪要（v2 基线）

本轮产品确认：

1. 收件协议先做 **POP3**（而不是 IMAP）；SMTP 负责发件。
2. Provider 预设暂定 **阿里云企业邮箱 / Hotmail / Gmail / Custom** 四种。
3. 认证只用 **密码 / App Password** 模式（Phase 1 **不接 OAuth2**）。
4. 发件 / 回复的二次确认做成 **可配置**（不默认强制 safe-handoff）。
5. 邮箱配置入口放在 **助手端个人信息 / 我的账户**，**不进管理端**。
6. 工具命名统一 `email_*`。
7. 附件 Phase 1 仅支持 URL 附件。
8. **不** 新建预设 `email-agent`；该工具仅作为 **Agent 工具白名单可选项** 对齐。

> 需求确认的“出现在系统的 tools 列表中”明确口径为：**出现在 Agent Builder 工具白名单的可选项列表中**（即 `AgentBuilderShell.TOOL_CATALOG`），并同时出现在管理侧用于展示已启用工具的 `GET /tools` 清单中。非“新增一个顶层导航页”。

---

## 1. 目标与范围

### 1.1 本次要做的

新增一个**系统内置标准工具** —— **邮件收发工具（`email`）**。

- 用户在系统中配置自己的**个人邮箱账号**；
- 工具读取当前登录用户的邮箱配置，**代表该用户**进行邮件收发；
- 智能体在 tool-calling 阶段可调用该工具处理邮件任务；
- 该工具必须出现在 **Agent Builder 工具白名单可选项**（`AgentBuilderShell.TOOL_CATALOG`）里，且在管理侧 `GET /tools` 的启用工具清单中一并可见；
- 支持以下邮箱类型（Phase 1，协议均为 **POP3（收件）+ SMTP（发件）**，认证统一为密码 / App Password）：
  - **阿里云企业邮箱**（`pop.qiye.aliyun.com` / `smtp.qiye.aliyun.com`）
  - **Hotmail / Outlook 个人邮箱**（`outlook.office365.com`）
  - **Gmail**（`pop.gmail.com` / `smtp.gmail.com`，需用户在 Google 账户中开启两步验证并生成 App Password）
  - **通用 POP3/SMTP 兼容邮箱**（`custom`，用户手填 host/port/ssl/密码）

### 1.2 本次不做的

- 不做群发营销、邮件模板管理、邮件服务器自建。
- 不做邮件附件 AI 解析（只做透传下载链接）。
- 不做 Exchange Web Services / MAPI / IMAP IDLE 实时推送（Phase 1 为“拉取式”）。
- 不做组织共享邮箱 / 代收代发他人邮箱。本工具始终**以当前登录用户自己的邮箱身份**执行。

### 1.3 与现有体系的对齐

- 与现有 `CloudccOpenApiService` 一致：作为**内置原生工具**由 `ToolOrchestratorService` 直调，不走 MCP。
- 工具命名遵循现有习惯（`cloudcc_pageQuery`、`get_pending_approvals`），定义为 `email_*` 系列。
- 凭据配置路径参考现有 `IntegrationAppService` 的“组织级配置”风格，但**拆为用户级**（每个用户自己的邮箱），入口在助手端「个人信息 / 我的账户」内，不进管理端。
- 权限仍按 `ORG_ADMIN` / `ORG_USER` 划分；工具本身对所有 `ORG_USER` 可见（因为发件人就是本人），只有当**用户未配置**时返回友好引导。

---

## 2. 术语

| 术语 | 含义 |
|------|------|
| **邮件账号（EmailAccount）** | 某个用户绑定的一组收发配置：协议、host、port、登录名、加密密码、发件显示名等。 |
| **邮件工具（Email Tool）** | 运行时给模型调用的一组 function-calling 工具，名称以 `email_` 开头。 |
| **邮件提供商（Provider）** | 阿里云企业、Hotmail、Gmail、Custom 四类预设；Custom 支持手填。 |
| **App Password（应用专用密码）** | Hotmail / Gmail 开启二步验证后，专门给第三方客户端使用的登录密码。 |

---

## 3. 顶层架构

```
┌─────────────────┐       ┌──────────────────────┐      ┌─────────────────────┐
│ Assistant UI    │       │ Admin / Profile UI   │      │ Agent Builder UI     │
│ （对话触发工具）│       │ （用户邮箱配置入口） │      │ （工具白名单勾选）   │
└────────┬────────┘       └──────────┬───────────┘      └─────────┬───────────┘
         │                           │                            │
         ▼                           ▼                            ▼
┌────────────────────────────────────────────────────────────────────────────┐
│ Backend HTTP API                                                           │
│ POST /ai/chat/stream   →   ChatOrchestratorService                          │
│ GET/PUT/DELETE /me/email-accounts        ←──── EmailAccountController       │
│ GET  /tools                              ←──── ToolController (已有)        │
└────────────────────┬───────────────────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│ ToolOrchestratorService                                                   │
│   - getToolDefinitions(orgId, allowedToolNames)                           │
│       + 注入 email_list_inbox / email_get_message / email_send / ...      │
│   - executeTool(orgId, userId, toolName, argsJson, allowedToolNames)      │
│       + 内置分支 → EmailToolService                                       │
└────────────────────┬───────────────────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│ EmailToolService     （新增领域服务）                                      │
│   ├── EmailAccountService       读写/加密 email_account 表                 │
│   ├── EmailProviderRegistry     内置 4 类 provider 预设                    │
│   ├── ImapClient (Jakarta Mail) 收件：folders / list / fetch / search      │
│   └── SmtpClient (Jakarta Mail) 发件：send / reply / forward               │
└────────────────────────────────────────────────────────────────────────────┘
```

Phase 1 运行在后端单进程里：所有 IMAP/SMTP 出站调用直接从应用容器发起，**不经 MCP**，不新增 sidecar。

---

## 4. 数据模型

### 4.1 新表 `email_account`

```
email_account
├── id                   BIGSERIAL PK
├── org_id               VARCHAR(64)  NOT NULL
├── user_id              VARCHAR(64)  NOT NULL           -- 绑定到具体用户
├── provider_code        VARCHAR(32)  NOT NULL           -- aliyun_mail / hotmail / gmail / custom
├── display_name         VARCHAR(128)                    -- 发件显示名（可空，默认用 user.name）
├── email_address        VARCHAR(256) NOT NULL
├── login_username       VARCHAR(256) NOT NULL           -- 多数情况 = email_address
├── auth_type            VARCHAR(16)  NOT NULL           -- password / app_password（Phase 1 仅这两种）
├── secret_cipher        TEXT         NOT NULL           -- AES-GCM 密文（见 §7）
├── secret_iv            VARCHAR(64)  NOT NULL
├── pop3_host            VARCHAR(128) NOT NULL
├── pop3_port            INT          NOT NULL
├── pop3_ssl             BOOLEAN      NOT NULL DEFAULT TRUE
├── smtp_host            VARCHAR(128) NOT NULL
├── smtp_port            INT          NOT NULL
├── smtp_ssl_mode        VARCHAR(16)  NOT NULL           -- ssl / starttls / plain
├── require_send_confirm BOOLEAN      NOT NULL DEFAULT TRUE  -- §7.3 可配置二次确认开关
├── enabled              BOOLEAN      NOT NULL DEFAULT TRUE
├── last_verified_at     TIMESTAMP                        -- 最近一次连通性测试成功时间
├── last_verify_error    VARCHAR(512)                     -- 最近一次失败原因（脱敏）
├── created_at           TIMESTAMP    NOT NULL
└── updated_at           TIMESTAMP    NOT NULL

UNIQUE (org_id, user_id, email_address)
INDEX  (org_id, user_id)
```

约束与策略：

- **一个 user 可绑多个邮箱**，但同一邮箱地址只允许绑定一次；模型侧默认作用在“主邮箱”。
- `provider_code`：Phase 1 固定 4 种枚举，服务端校验。
- 明文密码/App Password 永不落库：只存 AES-GCM 密文（见 §7）。
- 不保存邮件正文到本地：邮件工具每次在线拉取，最多在响应层返回摘要。

### 4.2 Flyway 迁移

新增 `V16__email_account_table.sql`（紧接当前最新的 `V15`）。
包含上表及索引；使用 `CREATE TABLE IF NOT EXISTS` 与 `CREATE INDEX IF NOT EXISTS`，与现有风格一致。

### 4.3 与 `tool_definition` 的关系

- `tool_definition` 当前只是一个“对外可见的工具清单”。`email` 作为**内置工具**，无需也不应该在 `tool_definition` 里硬编码插入——参考 `cloudcc_*` 没有入 `tool_definition`。
- 但为了满足需求中的“出现在系统的 tools 列表中”，我们在 `ToolController.list()` 里对**内置工具集合做统一补齐**（详见 §8.3）。

---

## 5. 邮件工具的 function-calling 设计

统一前缀 `email_`，风格与 `cloudcc_*` 对齐。Phase 1 覆盖读取、搜索、发送、回复四大核心能力。

### 5.1 工具列表

| 工具名 | 用途 | 风险级 |
|--------|------|--------|
| `email_list_inbox`      | 列出收件箱最近邮件（按时间倒序分页） | 低 |
| `email_search`          | 关键字 / 发件人 / 日期区间过滤（POP3 侧在本地做，见 §11.1） | 低 |
| `email_get_message`     | 根据 messageId 读正文（纯文本 + 安全 HTML 摘要） | 低 |
| `email_send`            | 新邮件发送（可带抄送、附件 URL） | **高**，二次确认是否开启由账号配置决定（见 §7.3） |
| `email_reply`           | 对指定 messageId 回复；POP3 下用 `In-Reply-To` 头拼接线程（非服务端线程） | **高**，同上 |

> Phase 1 不提供 `email_delete` / `email_move` / `email_archive`，避免首版就出现误操作风险。
> POP3 协议下不存在服务端“文件夹 / 多 mailbox”能力，`email_list_inbox` 始终作用于服务器当前可见邮件集合。

### 5.2 Schema 示例（对外约定）

`email_list_inbox`：

```json
{
  "name": "email_list_inbox",
  "description": "列出当前用户收件箱中最近的邮件摘要。不会读取正文。",
  "parameters": {
    "type": "object",
    "properties": {
      "limit": { "type": "integer", "minimum": 1, "maximum": 50, "default": 20 }
    }
  }
}
```

> 说明：POP3 协议没有 `\Seen` 标志，无法可靠支持 `unreadOnly`，故 Phase 1 **不提供** `unreadOnly` 参数。

`email_send`：

```json
{
  "name": "email_send",
  "description": "用当前用户已配置的邮箱发送一封新邮件。若账号开启了二次确认，模型须先请用户确认后再调用。",
  "parameters": {
    "type": "object",
    "required": ["to", "subject", "body"],
    "properties": {
      "to":        { "type": "array", "items": { "type": "string" } },
      "cc":        { "type": "array", "items": { "type": "string" } },
      "subject":   { "type": "string", "maxLength": 256 },
      "body":      { "type": "string", "maxLength": 20000 },
      "bodyFormat":{ "type": "string", "enum": ["text", "html"], "default": "text" },
      "attachmentUrls": { "type": "array", "items": { "type": "string" }, "maxItems": 5 }
    }
  }
}
```

### 5.3 模型端返回

所有 `email_*` 工具统一返回**单个字符串**（与 `cloudcc_*` 风格一致），内容为 Markdown 结构化摘要，便于模型二次组织输出。例如：

```
已获取收件箱最近 20 封邮件：
- [2026-04-19 14:20] 客户张三 <zhangsan@acme.com> · 合同初稿确认 · 未读
- [2026-04-19 11:02] ...（省略）
可用 `email_get_message` 读取正文，参数 messageId=...
```

---

## 6. 配置入口与接口

### 6.1 用户邮箱管理 API（新增）

`RequireOrgUser`，仅允许本人读写自己的邮箱账号。

| Method | Path | 说明 |
|--------|------|------|
| `GET`    | `/me/email-accounts` | 列出当前用户邮箱（密文字段脱敏，仅返回 masked 配置） |
| `POST`   | `/me/email-accounts` | 新建邮箱账号（入参详见 §6.2） |
| `PUT`    | `/me/email-accounts/{id}` | 更新；可仅更新部分字段（密码为空则保留原密文） |
| `DELETE` | `/me/email-accounts/{id}` | 删除 |
| `POST`   | `/me/email-accounts/{id}/verify` | 连通性测试（登陆 POP3 + SMTP；不发邮件） |

### 6.2 `POST /me/email-accounts` 请求体

```json
{
  "providerCode": "gmail",
  "emailAddress": "foo@gmail.com",
  "loginUsername": "foo@gmail.com",
  "displayName":  "Owen",
  "authType":     "app_password",
  "secret":       "xxxxxxxxxxxx",
  "pop3": { "host": "pop.gmail.com", "port": 995, "ssl": true },
  "smtp": { "host": "smtp.gmail.com", "port": 465, "sslMode": "ssl" },
  "requireSendConfirm": true
}
```

当 `providerCode` 为 `aliyun_mail / hotmail / gmail` 时，`pop3` / `smtp` 字段允许省略，**服务端使用 provider 预设补齐**（§6.3）。仅 `custom` 必须手填。`requireSendConfirm` 可省略，省略时默认 `true`。

### 6.3 `EmailProviderRegistry` 预设

| providerCode | pop3.host | pop3.port | smtp.host | smtp.port | smtp.sslMode | 说明 |
|--------------|-----------|-----------|-----------|-----------|--------------|------|
| `aliyun_mail` | `pop.qiye.aliyun.com` | 995 (SSL) | `smtp.qiye.aliyun.com` | 465 | `ssl` | 阿里云企业邮箱 |
| `hotmail`     | `outlook.office365.com` | 995 (SSL) | `smtp-mail.outlook.com` | 587 | `starttls` | 需在个人账号中生成 App Password |
| `gmail`       | `pop.gmail.com` | 995 (SSL) | `smtp.gmail.com` | 465 | `ssl` | 需开启两步验证后使用 App Password |
| `custom`      | 用户填 | 用户填 | 用户填 | 用户填 | 用户填 | 通用 POP3/SMTP |

### 6.4 前端入口（Phase 1）

- **助手工作台 → 顶部用户头像 → 个人信息**中新增「我的邮箱」分区：列表 + 新建 / 编辑 / 测试 / 删除 / 二次确认开关。
- Agent Builder 的工具面板（`AgentBuilderShell.TOOL_CATALOG`）中，`email_*` 系列工具与现有 `rag-search`、`crm-customer` 等并列出现，可勾选进 Agent 的 `toolIds`。
- **不进管理端**：该能力属于用户级，不是组织级集成。

---

## 7. 安全与合规

### 7.1 凭据加密

- 引入 `SecretCipherService`（新建）：使用 `AES-GCM-256`，主密钥从环境变量 `APP_SECRET_KEY`（base64 32B）读取；若变量未设置，启动失败（避免“默默落明文”）。
- `email_account.secret_cipher` = base64(AES-GCM 密文)；`secret_iv` 为 12B 随机数 base64。
- 业务层读到 `SecretCipherService.decrypt(cipher, iv)` 得到明文密码，仅在发起 IMAP/SMTP 的瞬间使用，不进日志。

### 7.2 最小可见性

- 所有 `/me/email-accounts/*` 返回 **不带** `secret_cipher / secret_iv`；回显密码位用 `"secret": "***"`。
- 日志使用 SLF4J；邮箱密码、邮件正文**不写入** DEBUG 日志；发件对象地址可在 `INFO` 级别记录便于排错。

### 7.3 速率与二次确认（可配置）

- 每账号每分钟 `email_send` ≤ 10 次；`email_list_inbox` ≤ 20 次（Redis 计数，租户级双 Key：`org:user:tool`）。
- `email_send` / `email_reply` 是否走人工二次确认由 **账号级** 字段 `require_send_confirm` 决定：
  - 默认值：`true`（保守）。
  - 实现方式：`EmailToolService` 在分发 `send / reply` 时读取当前账号的该字段。
    - 若为 `true` 且入参没有携带 `confirmed=true`，工具返回 `NEEDS_CONFIRMATION` 风格字符串，提示模型先向用户回显收件人 / 主题 / 正文要点并等待显式确认，再带 `confirmed=true` 重新调用。
    - 若为 `false`，直接放行。
  - 该开关用户可在「我的邮箱」页面切换，无需改 skill。
- `riskLevel` 仍然标记为 `HIGH`，便于审计与 Agent Builder UI 提示；但**不依赖** `safe-handoff` 做强制阻断。

### 7.4 审计

- 每次 `email_send / email_reply` 调用写一条 `audit_log`：`event_type=email.send`，`detail` 里记录收件人列表、主题、messageId；正文不入库。
- 连通性测试成功 / 失败写入 `email_account.last_verified_at` / `last_verify_error`。

### 7.5 多租户与 PII

- `email_account` 按 `org_id + user_id` 隔离；跨租户查询不可达。
- 返回给模型的摘要**默认截断**（发件人名 + 主题 + 前 200 字摘要），超长正文需显式调用 `email_get_message`。
- 符合 `docs/security-and-compliance-checklist.md` 基线（凭据加密、最小返回、审计可追溯）。

---

## 8. 与运行时对接

### 8.1 `EmailToolService`（新增）

位置：`backend/src/main/java/com/codehouse/ciciassistant/email/service/EmailToolService.java`

骨架：

```java
@Service
public class EmailToolService {
    public String listInbox(String orgId, String userId, ListInboxArgs args);
    public String search(String orgId, String userId, SearchArgs args);
    public String getMessage(String orgId, String userId, GetMessageArgs args);
    public String send(String orgId, String userId, SendArgs args);
    public String reply(String orgId, String userId, ReplyArgs args);

    public String verifyConnection(String orgId, String userId, Long accountId);

    public static String toolName(String op); // e.g. "email_send"
    public static JsonNode toolSchema(String op, ObjectMapper mapper);
}
```

底层用 **Jakarta Mail** (`jakarta.mail` 2.x)：

- POP3：`Store store = session.getStore(pop3Ssl ? "pop3s" : "pop3"); store.connect(host, port, user, pass);`
  - 只有一个 `INBOX` folder；`folder.open(READ_ONLY)`；`folder.getMessages()` 后取最后 N 封。
  - 不对服务器做任何写操作（不删信、不移动）。
- SMTP：`Transport.send(...)` 或显式 `Transport.connect(...)` + `sendMessage(...)`
  - `ssl` 模式：使用 `smtps`；`starttls` 模式：设置 `mail.smtp.starttls.enable=true` + `mail.smtp.starttls.required=true`；`plain` 模式仅允许在 `custom` 且用户显式选择时启用。
- messageId 策略：POP3 下使用 **信头 `Message-ID`** 作为模型面上的 `messageId`。若信头缺失，退化为 `UIDL` 或序号做临时 id，**同时在返回里注明不可跨会话稳定**。

### 8.2 `ToolOrchestratorService` 修改

在现有 `getToolDefinitions(...)` 中追加内置邮件工具注册（受 `allowedToolNames` 过滤）：

```java
addBuiltInTool(result, allowedToolNames, EmailToolService.toolName("list_inbox"),
               EmailToolService.toolDescription("list_inbox"),
               EmailToolService.toolSchema("list_inbox", objectMapper));
// ... send / reply / search / get_message
```

`executeTool(...)` 增加分支：

```java
if (toolName.startsWith("email_")) {
    return emailToolService.dispatch(orgId, userId, toolName, argumentsJson);
}
```

### 8.3 `ToolController.list()` 对外清单

当前实现只读 `tool_definition` 表。修改为：

- 组织级工具清单 = `tool_definition` 启用项 **∪** 内置工具静态清单（CloudCC、approval、email）。
- 每项补齐 `category`（`crm / approval / email / knowledge / custom`）供前端分组与 Agent Builder 选择。

### 8.4 `SkillDefinitionService` 不改动

本轮**不新增** `email-assistant` 内置 skill，也**不新建** `email-agent` 预设 Agent。邮件工具仅以「可选工具」形式在 Agent Builder 中展示；是否挂到具体 Agent 由管理员自行决定。

### 8.5 Agent Builder 展示

前端 `AgentBuilderShell.TOOL_CATALOG` 追加以下条目（顺序不敏感，保持与现有一致的字段结构）：

```ts
{ id: "email_list_inbox",  name: "邮件收件箱",   description: "读取当前用户邮箱最近邮件摘要。",   level: "低风险" },
{ id: "email_search",      name: "邮件搜索",     description: "按关键字 / 发件人 / 时间过滤邮件。", level: "低风险" },
{ id: "email_get_message", name: "邮件读正文",   description: "按 messageId 读取一封邮件正文。",   level: "低风险" },
{ id: "email_send",        name: "邮件发送",     description: "以当前用户身份发送新邮件。",       level: "高风险" },
{ id: "email_reply",       name: "邮件回复",     description: "对指定 messageId 回复一封邮件。",  level: "高风险" },
```

保存时照常写入 `agent_tool_binding`，与现有一致。

后端 `GET /tools` 也需要返回这 5 个工具（静态合并内置清单），确保和 Agent Builder 前端勾选列表是同一份事实源。

---

## 9. 错误处理与用户体验

所有 `email_*` 工具遵循统一失败消息格式：

```
❌ 邮件工具执行失败：{简短原因}
- accountId: {id 或 "未配置"}
- 建议：{引导文本，例如 "请先在 我的邮箱 中添加或重新测试账号"}
```

典型场景：

| 场景 | 返回 |
|------|------|
| 用户未配置任何邮箱 | `❌ 当前用户尚未配置邮箱，请先在「个人信息 → 我的邮箱」中绑定。` |
| POP3/SMTP 认证失败 | `❌ 登录失败：Authentication failed。请确认密码或 App Password 是否正确并未过期。` |
| 网络/超时 | `❌ 邮箱服务连接超时（10s）。请稍后重试。` |
| Send 需要二次确认 | `NEEDS_CONFIRMATION：请先向用户回显 收件人/主题/正文要点，获得确认后再带 confirmed=true 重新调用。` |

---

## 10. 落地计划（Phase 1）

| 步骤 | 内容 | 预期产出 |
|------|------|----------|
| 1 | Flyway `V16__email_account_table.sql`；`EmailAccountEntity/Repository` | 建表成功；JPA 扫描通过 |
| 2 | `SecretCipherService`（AES-GCM 封装） + 启动时密钥校验 | 单元测试：加解密、IV 随机、缺失密钥拒启动 |
| 3 | `EmailProviderRegistry` + `EmailAccountService`（CRUD + 连通测试） | `POST /me/email-accounts/{id}/verify` 成功 |
| 4 | `EmailToolService`（list/get/send/reply/search 基于 Jakarta Mail POP3 + SMTP） | 对 Gmail / 阿里企业邮 / custom 实测通过 |
| 5 | `ToolOrchestratorService` 注册 + 执行分发；`ToolController.list()` 补齐内置项 | `GET /tools` 看到 email 系列；Agent Builder 勾选可用 |
| 6 | 前端：个人信息「我的邮箱」分区 + `AgentBuilderShell.TOOL_CATALOG` 扩展 | UI 闭环：配置 → 勾选 → 对话触发 |
| 7 | 审计 + 速率限制 + E2E smoke，写入 `.claw/test-report.md` | 证据入库 |

Phase 2（不在本次范围）：OAuth2（Gmail / Microsoft Graph）、IMAP 与 IDLE 推送、附件 multipart 上传、邮件模板与签名、组织共享邮箱、跨协议搜索能力增强。

---

## 11. POP3 实现注意事项

Phase 1 统一用 POP3 收件，需要特别处理如下差异点。

### 11.1 能力降级

| 能力 | POP3 下的做法 |
|------|--------------|
| 多文件夹 / 多 mailbox | 不支持；只操作服务器默认邮件集合 |
| 服务端搜索 | 不支持；`email_search` 先拉取最近 `limit` 封，在服务端内存里按条件过滤 |
| `\Seen` 未读标志 | 不支持；`email_list_inbox` 不提供 `unreadOnly` 参数 |
| 线程 / Conversation | 不支持；`email_reply` 通过设置 `In-Reply-To`、`References` 信头拼接线程 |
| UID 稳定性 | Gmail POP3 有 UIDL，但部分 provider 不稳定；对外以 RFC `Message-ID` 作为主 id |

### 11.2 不做删除策略

`Folder.open(READ_ONLY)`，**绝不** 调 `setFlag(DELETED)` 或 `folder.close(true)`；以免 POP3 语义下误删服务器邮件。

### 11.3 Gmail POP3 的特殊要求

- 需在 Gmail 设置中打开 POP3 访问；
- 开启两步验证后生成 App Password；
- Gmail POP3 默认只会给“最近”邮件，这点在 `email_list_inbox` 的帮助文案里需说明。

### 11.4 Phase 2 演进方向

后续若需要真正的服务端搜索 / 多文件夹 / IDLE 推送，再在 `EmailAccount` 模型里加 `protocol` 字段（`pop3 | imap`），并扩 `EmailToolService` 分发层，不破坏 Phase 1 的 API 形态。

---

## 12. 风险与开放问题

以下为仍需注意的执行期风险（不再是设计阻塞）：

1. **Gmail 服务端可见性**：POP3 只能拉到“最近”邮件，Google 可能仅返回近 30 天或 N 条以内，超范围不可见。这是 Gmail 策略，不是本系统 bug。
2. **Hotmail App Password 入口**：部分个人账户必须启用双因素后才出现 App Password 入口；用户侧会有额外引导成本，需在「我的邮箱」页面内附链接说明。
3. **附件直传未支持**：仅 URL 附件。若后端访问该 URL 失败（超时 / 403），整封邮件 `email_send` 失败并回传原始错误，不做静默降级。
4. **审计范围**：写操作（send/reply/verify）统一写 `audit_log`；读操作（list/get/search）仅 `DEBUG` 留痕。若后续合规要求更严，再单独拉分支增强。
5. **多邮箱选择**：一用户绑多邮箱时，工具默认取 `enabled=true` 且 `created_at` 最早的一条作为主邮箱。`accountId` 作为可选参数已在各工具 schema 里预留，供对话显式选择。

---

## 13. 对照需求原文

| 需求点 | 对应设计位置 |
|--------|--------------|
| 用户可在系统中配置自己的个人邮箱 | §4.1 `email_account` + §6 `/me/email-accounts` |
| 工具读取用户已配置的邮箱进行收发 | §8.1 `EmailToolService` + §5 工具集 |
| 智能体可调用此工具处理邮件任务 | §8.2 `ToolOrchestratorService` |
| 工具出现在 Agent 工具白名单可选列表 | §8.3 `ToolController.list()` 统一内置工具清单 + §8.5 `AgentBuilderShell.TOOL_CATALOG` 扩展 |
| 阿里云企业邮箱 / Hotmail / Gmail / 通用兼容 | §6.3 `EmailProviderRegistry`（含 custom） |
| 基于 POP3/SMTP 通用兼容 | §11 POP3 实现注意事项 |
| 二次确认做成可配置 | §4.1 `require_send_confirm` + §7.3 账号级开关 |
| 仅使用密码模式 | §4.1 `auth_type ∈ {password, app_password}`，无 OAuth2 |
| 入口放在个人信息 | §6.4 助手端个人信息「我的邮箱」分区 |
| 不建预设 `email-agent` | §8.4 明确不新增 skill / 预设 Agent |
