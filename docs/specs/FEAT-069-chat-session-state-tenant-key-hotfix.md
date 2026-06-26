# FEAT-069 对话会话状态租户主键热修

## 背景

线上 `2.1.3` 版本在工作台对话中出现后端错误：

- 时间：2026-06-26 12:12:12 CST
- 容器：`cici-backend:2.1.3`
- 错误：`duplicate key value violates unique constraint "chat_session_state_pkey"`
- 冲突键：`session_id=workbench:cici-system`
- 触发链路：`ChatOrchestratorService.chatStreamBlockingLocked -> ChatSessionStateService.mergeUserTurn`

线上只读排查显示，`chat_session_state` 表已有 `session_id=workbench:cici-system, org_id=demo-org` 的记录。新请求在另一个组织上下文中复用了同一个工作台 session id，应用按 `session_id + org_id` 查询为空后尝试插入，但数据库主键仍是单列 `session_id`，因此失败。

## 目标

- 修正 `chat_session_state` 的数据模型，使会话状态以 `session_id + org_id` 为租户隔离主键。
- 保持现有服务层查询语义 `findBySessionIdAndOrgId` 不变。
- 避免跨组织复用 `workbench:cici-system`、OpenAPI session id 或其他通用 session id 时发生主键冲突。
- 补充后端测试覆盖同一 `session_id` 在不同组织下可分别保存状态。

## 非目标

- 不变更前端会话 ID 生成规则。
- 不迁移或删除既有会话消息。
- 不新增分布式锁、缓存层或移动端适配。
- 不改动已发布 Flyway migration；只追加新 migration。

## 设计

### 数据库

追加 `V69__chat_session_state_tenant_primary_key.sql`：

- 删除旧单列主键 `chat_session_state_pkey`。
- 新增复合主键 `(session_id, org_id)`。

现有线上数据没有同一 `session_id + org_id` 的重复行，因此可以正向迁移。复合主键与既有 `findBySessionIdAndOrgId`、`deleteBySessionIdAndOrgId` 语义一致。

### JPA

`ChatSessionStateEntity` 改为复合主键实体：

- `sessionId` 与 `orgId` 都标记为 `@Id`。
- 新增 `ChatSessionStateId` 作为 `IdClass`。
- `ChatSessionStateRepository` 仍保留 `findBySessionIdAndOrgId` 与 `deleteBySessionIdAndOrgId`。

### 验收标准

- 同一 `session_id` 在不同 `org_id` 下可以各自保存状态，不抛 `DataIntegrityViolationException`。
- 同一组织同一 session 的状态更新仍复用原行并更新 version。
- 后端相关 focused test 通过。
- `git diff --check` 通过。

