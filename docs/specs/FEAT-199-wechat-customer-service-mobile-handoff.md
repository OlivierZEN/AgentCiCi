---
kind: feature-spec
feature_id: FEAT-199
title: 微信客服企业微信手机端人工监控与强制接管
status: implemented
primary_project: agentcici
task_ids: TASK-327
related_integrations: none
updated_at: 2026-08-21T06:44:54Z
updated_by: codex
---

# FEAT-199 - 微信客服企业微信手机端人工监控与强制接管

## 需求来源与结论

客户继续在微信侧使用企业微信「微信客服」发起咨询。AgentCiCi 负责 AI 接待、知识检索、风险判断、运行观测和审计；人工坐席不以 AgentCiCi Web 聊天页作为工作台，而是在企业微信手机端监控、接管并通过原生微信客服会话回复客户。

用户已确认以下产品边界：

- 企业微信是人工坐席主界面，AgentCiCi 不复制完整客服聊天工作台。
- AI 接待中允许值班人员从企业微信内查看会话摘要并强制接管。
- 接管成功后必须立即阻断 AI 模型调用和 API 发送，防止人工与 AI 双重回复。
- AgentCiCi Web 管理端继续负责账号配置、Agent/知识库绑定、运行 Trace、审计与运营统计。

## 现状

- FEAT-023 已实现回调校验、`sync_msg`、`send_msg`、外部客户会话、消息日志和 48 小时/5 条窗口计数。
- 当前 `sync_msg` 解析未保留 `origin`、`servicer_userid` 或会话状态事件，无法区分客户、系统与人工坐席消息。
- 当前发送前只检查本地时间窗口，不读取企业微信权威 `service_state`。
- 当前没有 `service_state/get`、`service_state/trans`、接待人员列表、企业微信成员 OAuth、移动会话或 JS-SDK 原生聊天跳转。
- FEAT-023 的真实回调、消息拉取和发送正例仍未用正式微信客服账号完成业务验收。本功能不得把 mock 测试替代为真实渠道验收。

## UAT 发布结果

- 功能提交 `a6427a94548d` 已包含在 `2.8.66-beta.2 / 525f0f610926`；运行 backend/frontend 的版本、commit、镜像 label/digest 一致，V123 与六容器 healthy/restart=0 通过。
- UAT 移动页返回 200；带合法同源 `pageUrl` 的无会话 context 返回 JSON 401，不存在入口 UUID 返回 JSON 400。
- UAT 未配置或调用真实微信客服账号、Secret、OAuth、客户消息或状态转换；状态 3 接管、人工回复无 AI 双发与原生会话跳转仍待获授权 HUMAN 验收。生产未修改。

## 产品设计

### 使用者与场景

- 使用者：被配置为目标微信客服账号接待人员的企业微信成员。
- 场景：值班人员在手机上短时扫视 AI 会话、识别等待人工或风险会话、强制接管并进入企业微信原生客服聊天。
- 物理场景：移动办公中单手快速处理，环境光不稳定，页面必须紧凑、可读、动作少且回执明确。
- 语气：克制、可靠、偏运营工作台，不使用营销页、社交聊天页或装饰性视觉。

### 移动入口形态

- 企业微信内打开 `/mobile/wechat-kf`。
- 未建立移动坐席会话时，后端依据账号的企业 ID、应用 AgentId 和部署配置生成企业微信 OAuth 地址；不由前端拼接环境域名。
- OAuth 回调只接受短时单次 `state`，使用企业微信返回的成员 `userid` 建立 HttpOnly、Secure、SameSite=Lax 的服务端会话。
- 后端必须实时或在短 TTL 内通过接待人员列表确认该成员属于目标客服账号；不能只相信前端传入的 userid。
- 页面只显示会话状态、最近客户摘要、最近时间、当前接待人、接管原因和动作回执，不复制聊天正文编辑器。
- 接管成功后通过企业微信 JS-SDK 打开指定 `open_kfid + external_userid` 的原生微信客服聊天。

### 视觉与交互

- register：`product`。
- 沿用 `鎏金账房` 语义 token、墨色文字、暖象牙表面、香槟金主操作和紧凑密度；其他账号主题只映射颜色，不改变结构。
- 会话按 `待人工 → AI 接待 → 人工接待` 分组，列表行为主，不做卡片宫格。
- 主操作“立即接管”只在远端状态允许且本地 revision 一致时启用。
- loading 使用行骨架；empty 说明“当前没有需要关注的会话”；error 显示可重试原因；接管中锁定同一会话动作。
- 页面可见时按短周期增量刷新，进入后台后停止轮询；返回前台立即刷新。
- 所有状态和错误都有文字，不只依赖颜色。

## 企业微信权威状态机

| service_state | 本地 owner_mode | 行为 |
|---|---|---|
| 0 未处理 | AI | 可进入 AgentCiCi；发送前必须再次回读 0/1 |
| 1 智能助手接待 | AI | AgentCiCi 可回复；可转 2 或指定人员转 3 |
| 2 待接入池 | PENDING | 禁止 AgentCiCi 模型调用和发送；等待坐席接入或指定人员转 3 |
| 3 人工接待 | HUMAN | 禁止 AgentCiCi 模型调用和发送；人工在企业微信回复 |
| 4 已结束/未开始 | ENDED | 禁止 AgentCiCi 主动恢复为 AI；客户下一条消息使远端重新进入 0 |

状态转换规则：

- 客户明确要求“人工客服/转人工/真人客服”时，服务端确定性转入状态 2，不交给模型自行决定。
- 移动端强制接管从允许的 0/1/2 转入状态 3，并指定当前已认证、正在接待的 `servicer_userid`。
- 3 只允许转给其他接待人员或结束为 4；不提供“切回 AI”按钮。
- 企业微信返回成功后必须调用 `service_state/get` 写后回读；回读不匹配即失败关闭，不展示成功回执。

## 并发与双重回复防护

每个会话维护 `state_revision` 和 `owner_mode`。任何 AI 回复至少经过两道门禁：

1. 调用模型前读取企业微信权威状态，仅 0/1 允许继续。
2. `send_msg` 前重新读取权威状态，并确认本地 `state_revision` 未变化、`owner_mode=AI`。

人工接管会在本地事务中增加 revision 并建立发送 fence。若接管发生在模型运行期间，模型结果允许完成但不得发送；消息日志记录 `suppressed_after_handoff`。并发重复接管必须通过 `Idempotency-Key + 会话 + actor` 返回同一结果，不能重复改变状态。

## 消息来源处理

`sync_msg` 必须完整保留：

- `origin=3`：客户消息。仅在权威状态 0/1 时进入 Agent；状态 2/3 时只记录并通知人工。
- `origin=4`：系统事件。解析接待人员状态和会话状态变更，更新本地 owner、servicer 和 revision。
- `origin=5`：企业微信客户端人工消息。记录为 HUMAN_OUTBOUND，绝不输入 Agent、绝不触发自动回复。

通过 API `send_msg` 的 AgentCiCi 消息不会由 `sync_msg` 再次返回，本地仍以企业微信返回的 `msgid` 和发送状态记录。

## 数据设计

### `wecom_kf_account` 增量

- `mobile_entry_id UUID UNIQUE NOT NULL`：不暴露数据库主键的移动入口标识。
- `wecom_app_agent_id VARCHAR(64)`：企业微信自建应用 AgentId；启用移动入口时必填。
- `wecom_app_secret_cipher/wecom_app_secret_iv`：自建应用 Secret 的独立密文；不能复用微信客服 Secret。
- `wecom_app_access_token_cipher/wecom_app_access_token_iv/wecom_app_access_token_expires_at`：自建应用 OAuth/JS-SDK access token 的独立加密缓存。
- `mobile_handoff_enabled BOOLEAN NOT NULL DEFAULT FALSE`：显式开关，历史账号默认关闭。

### `wecom_kf_conversation` 增量

- `public_id UUID UNIQUE NOT NULL`。
- `remote_service_state INTEGER NOT NULL DEFAULT 0`。
- `owner_mode VARCHAR(16) NOT NULL DEFAULT 'AI'`。
- `servicer_userid VARCHAR(128)`。
- `state_revision BIGINT NOT NULL DEFAULT 0`。
- `state_checked_at TIMESTAMPTZ`。
- `handoff_reason VARCHAR(64)`。

### `wecom_kf_message` 增量

- `origin INTEGER`。
- `servicer_userid VARCHAR(128)`。
- `event_type VARCHAR(64)`。
- `remote_msg_id VARCHAR(128)`。

### `wecom_kf_handoff_operation`

持久化 `operation_id/public_id/company_id/conversation_id/actor_userid/idempotency_key/correlation_id/expected_revision/old_state/target_state/readback_state/status/reason/error_code/created_at/completed_at`。不保存 access token、Secret、OAuth code、JS-SDK ticket 或可复用 Cookie。

移动 OAuth state 和移动会话存在 Redis，使用短 TTL；单节点开发降级到进程内到期缓存。JS-SDK ticket 只在后端进程内按企业和用途缓存到企业微信返回的到期时间。Cookie 只保存随机 session id，不保存 userid 或 access token。

## API 设计

### 管理端

- 现有账号保存请求增加 `wecomAppAgentId`、`wecomAppSecret`、`mobileHandoffEnabled`；两个 Secret 独立加密且响应永不回传。
- 启用移动接管时必须同时具备自建应用 AgentId 和 Secret；管理端连接测试同时验证客服 access token、自建应用 access token、接待人员列表和两类 JS-SDK ticket。
- 响应提供同源相对 `mobileEntryPath`，不返回部署域名。

### 企业微信移动端

- `GET /wecom/kf/mobile/start?entry=...`：生成 OAuth state 并跳转企业微信。
- `GET /wecom/kf/mobile/callback?code=...&state=...`：消费 state、验证企业微信成员和坐席资格、建立移动会话。
- `GET /wecom/kf/mobile/api/context?pageUrl=...`：一次返回当前坐席、账号、脱敏会话列表和只为受校验同源 URL 签发的 JS-SDK 配置。
- `POST /wecom/kf/mobile/api/conversations/{publicId}/refresh`：回读企业微信权威状态；要求固定同源请求标记。
- `POST /wecom/kf/mobile/api/conversations/{publicId}/takeover`：请求体提交 `idempotencyKey`、`correlationId` 和 `expectedRevision`，服务端 session 决定 actor/账号/company。

所有移动 API 只从服务端 session 解析账号、company 和坐席身份，拒绝前端提交或覆盖这些字段。

## 安全边界

- OAuth state 单次消费、短 TTL、常量时间比较；callback URL 由受校验的部署 Origin 生成。
- 移动 Cookie 必须 HttpOnly、Secure、SameSite=Lax、Path 限定到 `/wecom/kf/mobile`；写请求另要求同源自定义 Header，跨站表单不能触发接管。
- 列表、接管、JS-SDK 配置均验证移动 session、客服账号启用状态、移动接管开关和坐席资格。
- 接管目标 userid 永远使用当前 OAuth 成员，不接受请求体传入。
- `open_kfid`、`external_userid`、company 和账号必须来自同一数据库会话关系；跨租户、跨账号、枚举 publicId 均返回不可区分的 404/403。
- 日志与审计不输出 Secret、access token、OAuth code、JS ticket、Cookie 或完整客户标识。
- 高影响接管回执包含 operation ID、revision、correlation ID、旧/新/回读状态和坐席，但客户 ID 仅在受权移动页面最小展示或脱敏。

## 验收标准

### 确定性测试

1. `origin=3/4/5` 分流正确，人工消息永不进入 Agent。
2. 客户明确转人工从 0/1 进入 2，写后回读匹配后才成功。
3. 强制接管只允许已认证且属于目标账号的接待人员，从 0/1/2 进入 3。
4. OAuth state 重放、过期 state、非企业成员、非接待人员、停用账号、关闭移动开关全部失败关闭。
5. 接管与 AI 回复并发时，接管 revision fence 获胜，AI 消息不发送。
6. 同一幂等键重试返回同一 operation；不同 payload 复用同一键被拒绝。
7. 写后回读不匹配、企业微信错误、坐席非“正在接待”均不展示成功。
8. 跨公司、跨账号和 publicId 枚举失败关闭。
9. 前端覆盖 loading、empty、error、disabled、revision 冲突、接管成功和原生跳转失败状态。
10. Flyway 从现有 V122 和全新数据库均迁移成功。

### 本地开发环境

- 代码提交进入 AgentCiCi 本地 `main`。
- 从该 main 明确 commit 构建 backend/frontend `:local` 镜像并更新 `cc-local-stack` 对应服务。
- 回读 backend/frontend 镜像 label、环境版本、版本 API、页面资源、容器健康、restart count、正式入口和匿名负例。
- 未配置真实企业微信账号的本地环境使用保留测试域名和 mock 传输验证；不得伪造真实企业微信业务成功。

### 真实渠道业务验收

以下证据在具备授权测试账号后单独执行，未完成前只能称为“代码与本地技术门禁通过”：

1. 真实企业微信成员 OAuth 登录手机入口。
2. 真实接待人员列表校验通过。
3. 微信客户发送消息，AgentCiCi AI 回复一次。
4. 手机端强制接管，企业微信回读状态 3、指定坐席一致。
5. 接管期间人工发送消息，AgentCiCi 不调用模型、不发送消息。
6. 企业微信原生聊天跳转成功。
7. 人工结束后客户重新发消息，按状态机恢复到新会话。

## 发布与回滚

- 数据迁移只增加可空字段、带默认值字段和新表；历史账号 `mobile_handoff_enabled=false`，部署不会自动启用外部行为。
- 发布后先为一个测试客服账号配置 AgentId 并显式启用，完成真实渠道验收后再扩大。
- 回滚应用时关闭移动接管开关并保留新增表和审计记录；不删除企业微信会话或客户消息。
- 若移动入口异常，人工仍可通过企业微信既有待接入池处理；AgentCiCi 可停用该客服账号的 AI 渠道。

## 非目标

- 不实现坐席排班、WFM、绩效、完整工单中心或会话存档合规平台。
- 不在 AgentCiCi Web 或移动页复制完整聊天输入框。
- 不把企业微信 HUMAN 权限继承给 AgentCiCi SERVICE run-as 身份。
- 不在本任务发布 UAT 或生产，不修改其他产品仓库；发布需单独遵循线上 DevOps Skill 与发布授权。
