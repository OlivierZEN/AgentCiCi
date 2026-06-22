---
kind: feature-spec
feature_id: FEAT-068
title: Agent runtime concurrency hardening
status: implemented
owner_role: fullstack-agent
task_ids: TASK-158
related_decisions: FEAT-004, FEAT-019, FEAT-066
updated_at: 2026-06-21T23:47:34Z
updated_by: MANAGER-001
---

# FEAT-068 - Agent 运行时并发隔离加固

## 背景与目标

Web 多用户和 OpenAPI 多客户端会并发调用同一个 Agent。当前系统把 Agent 定义作为共享配置，每次调用创建请求级运行上下文，正常不同用户和不同 session 不共享运行态。但同一个 session 并发消息仍可能出现状态覆盖、回复顺序交错、工具副作用重复或公共线程池不可控扩张。

本特性目标：

- 同一 `orgId + sessionId` 内的 chat / stream 调用串行执行，避免同一会话上下文竞态。
- `chat_session_state` 支持乐观锁，避免状态静默覆盖。
- 每次 Agent 调用生成 `runId`，返回 payload、SSE、trace、workflow log、billing metadata 能定位单次运行。
- 流式调用使用有界 executor，不再使用默认 `CompletableFuture` 公共线程池。
- 基础并发与速率保护覆盖组织、Agent、用户和 session 维度。
- 工具调用具备本轮幂等键，便于外部写工具做去重扩展。
- Agent 运行读取的发布版本和治理版本在 trace 中固定记录，便于回放和排障。

## 设计方案

### 1. Session 串行执行

新增 `AgentRuntimeConcurrencyService`，维护 JVM 内 `sessionLocks`，以 `orgId + sessionId` 为 key 对 chat 同步路径和 stream blocking 路径加锁。锁对象使用引用计数清理，避免无限增长。

首版为单 JVM 本地锁；多实例部署后需要升级为 Redis/PostgreSQL advisory lock。

### 2. 有界流式线程池

新增 `AgentRuntimeAsyncConfig`，提供 `agentRuntimeExecutor`，配置：

- core: 8
- max: 32
- queue: 256
- thread name: `agent-runtime-`

`chatStream` 和 OpenAPI 异步执行改用该 executor。

### 3. runId 与 trace

每次调用生成 `runId`，贯穿：

- chat / stream response。
- SSE phase / done event。
- stage trace metadata。
- workflow execution context snapshot。
- tool idempotency key。

### 4. 乐观锁

`chat_session_state` 增加 `version` 字段，JPA 实体使用 `@Version`。发生乐观锁冲突时，当前阶段保留异常上抛并由调用方返回重试错误；后续可补自动重试合并。

### 5. 基础并发限制

新增轻量内存 limiter，按 key 计数：

- `org:{orgId}`
- `agent:{orgId}:{agentId}`
- `user:{orgId}:{userId}`
- `session:{orgId}:{sessionId}`

超限返回明确错误。首版配置常量保守落地，后续可接入租户套餐和平台配置。

## 验收标准

- 同一 session 的两个并发 chat 请求串行进入核心执行区。
- 不同 session 可以并发执行。
- stream / OpenAPI async 不再使用默认 common pool。
- response 和 trace 中包含 `runId`。
- `chat_session_state` 有乐观锁版本字段和迁移。
- 聚焦后端测试覆盖 session 串行、不同 session 并行和 runId 透出。
- 验证至少包含 focused backend test、`git diff --check`、assignment check。

## 非目标

- 不在本阶段实现跨 JVM 分布式锁。
- 不新增移动端验收。
- 不改变现有 Agent Builder UI。
- 不重做工具系统的外部事务模型；本阶段只提供本轮幂等键。

## 实现进展

- 2026-06-21T23:47:34Z：
  - 新增 `AgentRuntimeConcurrencyService`，按 `orgId + sessionId` 对 chat 和 stream blocking 路径串行化，同时提供组织、Agent、用户维度并发计数限制。
  - 新增 `AgentRuntimeAsyncConfig`，将 chat stream 和 OpenAPI async/sync-timeout 包装路径切换到有界 `agentRuntimeExecutor`，避免默认 common pool 不受控扩张。
  - 每次 chat / stream 生成 `runId`，同步响应、SSE run/done/error、stage trace metadata、workflow context snapshot 均透出该值。
  - 工具 trace 参数增加 `_idempotencyKey`，不改变真实工具入参，避免破坏工具 schema。
  - `chat_session_state` 新增 `version` 乐观锁字段和 V68 migration。
  - workflow context snapshot 记录 `publishedVersionId` / `publishedVersionNo`，用于发布切换期间回放排障。
  - `AgentRuntimeConcurrencyServiceTest` 覆盖同 session 串行、不同 session 并发和锁清理。
