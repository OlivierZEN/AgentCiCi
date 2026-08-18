---
kind: feature-spec
feature_id: FEAT-194
title: 服务端持有的会话身份
status: implemented
owner_role: fullstack-agent
task_ids: TASK-317
related_decisions: none
related_issues: none
updated_at: 2026-08-18T03:18:23Z
updated_by: codex
---

# FEAT-194 服务端持有的会话身份

## 背景

工作台在没有历史会话时使用 `workbench:<agent>` 作为持久化 `chat_session.id`。该值既不包含租户或用户，也不是由服务端签发；后端又按全局主键查询后更新会话，因此同一智能体在不同租户首次对话时会复用其他租户的会话行。消息和 Trace 仍按当前租户写入，但会话列表按会话行的租户与用户过滤，造成“观测可见、前端历史不可见”的完整性故障。

## 目标

- `chat_session.id` 只保存服务端生成的全局 UUID，不再接受业务键或前端稳定字符串作为新会话主键。
- Web 工作台必须先调用会话创建 API，获得 UUID 后才能上传附件、发送消息或读取历史。
- 每次写入、读取、删除和更新均同时校验会话所属公司以及用户/组织可见范围。
- 外部渠道的业务会话键与内部 UUID 分离：业务键只作为 `source_key`，由服务端在公司、渠道范围内解析。
- 不迁移三个测试环境的历史会话；通过数据库迁移清空会话、消息、状态、附件、Trace 和外部会话映射测试数据，建立新约束。

## 数据设计

`chat_session` 保持单一全局主键，新增：

- `id`：UUID 文本，服务端生成；数据库约束 UUID 格式。
- `channel_code`：`web`、`feishu`、`wecom_kf`、`dingtalk`、`webchat`、`openapi`、`customer_workbench` 等。
- `visibility_scope`：`USER` 或 `COMPANY`。
- `source_key`：可空的外部渠道业务键；Web 会话必须为空。
- 唯一约束：`(company_id, channel_code, source_key)`，仅对非空 `source_key` 生效。

`chat_message`、`chat_session_state`、`chat_attachment` 继续引用内部 UUID 字符串；迁移增加指向 `chat_session.id` 的外键并采用级联删除。`agent_run_trace` 保留弱关联，以允许会话创建前失败也能记录诊断 Trace，但运行时写入统一使用内部 UUID。

## API 与运行时

### 创建 Web 会话

`POST /ai/sessions`

请求：

```json
{"agentId":"devautopilot-pm"}
```

响应返回会话摘要，其中 `id` 是服务端 UUID。接口从认证上下文取得 `company_id` 与 `user_id`；客户端不能指定所有者、可见范围或主键。

### 发送消息

`POST /ai/chat` 与 `POST /ai/chat/stream` 的 `sessionId` 只接受已存在且属于当前公司、当前用户的 Web 会话 UUID。不存在、跨租户或跨用户均失败关闭，不再隐式创建。

内部渠道调用通过服务端会话身份服务解析：若传入外部业务键，则按 `(company, channel, source_key)` 查找或创建内部 UUID；运行日志、消息和状态只使用解析后的 UUID。公司级渠道会话以 `COMPANY` 可见，客户工作台等个人上下文使用 `USER`。

## 前端行为

- 工作台历史按后端返回的 `agentId` 与 `channel=web` 归属，不再根据 ID 前缀判断。
- 没有可用会话时自动调用创建 API；“新对话”也调用同一 API。
- 会话创建期间禁用发送与附件操作；创建失败显示错误，不生成本地伪会话 ID。
- 删除当前最后一个会话后创建新的服务端会话。

## 安全与失败语义

- UUID 全局唯一只解决标识碰撞，授权仍必须执行 `company_id + visibility_scope + owner_user_id` 校验。
- Web 请求携带外部业务键、非法 UUID、其他公司的 UUID或同公司其他用户的 USER 会话，统一拒绝。
- 外部渠道 `source_key` 不在普通 Web API 返回，也不能被 Web 客户端用于寻址。
- 唯一键竞争由数据库约束兜底，服务在并发创建时回读既有映射。

## 验收标准

1. 两个租户、两个用户为同一智能体创建会话，得到四个不同 UUID，均只出现在各自会话列表中。
2. 未先创建的 Web UUID、跨租户 UUID、同租户其他用户 UUID均不能写消息。
3. 首次进入工作台并发送一条消息后，会话立即出现在会话历史；刷新后消息可回读。
4. 连续新建三条会话均有独立 UUID 和独立消息历史。
5. 相同外部 `source_key` 在不同租户解析为不同内部 UUID；同租户同渠道重试解析为同一 UUID。
6. 数据库迁移后不存在旧会话测试数据，所有新 `chat_session.id` 满足 UUID 格式，消息/状态/附件不存在孤儿引用。
7. 后端定向测试、package、前端定向测试、全量测试与 production build 通过；本地 `cici.localhost` 完成授权态首次发送/刷新回读验证，容器 healthy 且 restart=0。

## 回滚

- 代码回滚到迁移前版本时，V122 不可逆恢复测试历史；如需回滚，仅回滚应用版本并继续使用已生成的 UUID 会话 ID。
- 三个环境均为测试账号历史，本次不提供数据恢复或兼容旧 `workbench:*` 主键的路径。
