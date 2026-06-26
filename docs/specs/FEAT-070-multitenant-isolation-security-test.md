# FEAT-070 多租户隔离安全测试

## 背景

AgentCiCi 是多组织 SaaS 平台，线上 `2.1.4` 已修复一次由 `chat_session_state` 单列主键导致的跨组织会话状态冲突。需要补充更全面的多租户隔离测试，验证组织 A 的 token 无法读取、修改、删除或推断组织 B 的敏感数据。

## 目标

- 建立一组可重复运行的后端集成测试，覆盖核心租户数据面的横向越权风险。
- 使用真实 Spring Security、MockMvc、Flyway、本地 PostgreSQL 测试库执行。
- 覆盖认证组织切换、组织后台、Agent、会话状态/消息、运行日志、知识库、平台接口边界。
- 发现缺陷时优先修复并补充回归测试；若缺陷影响面较大，先记录阻塞和风险。

## 非目标

- 不进行生产数据破坏性测试。
- 不新增移动端专项测试。
- 不替代代码审计、渗透测试或数据库 RLS 方案。
- 不修改已发布 migration，除非测试发现必须补正的安全缺陷。

## 测试矩阵

### 认证与组织边界

- 同账号多组织只能切换到自己所属组织。
- A 账号不能切换到 B 账号组织。
- 普通组织 token 不能访问平台接口。
- 平台 token 不应携带组织上下文访问组织业务接口。

### 组织后台边界

- `/admin/organization/profile` 只能返回当前 token 组织。
- 请求体中的 `orgId` 不能覆盖 token 中的组织。
- A 组织无法读取 B 组织成员列表、导出任务、账单或用量数据。

### Agent 与运行日志边界

- `/agents` 只列出当前组织 Agent。
- A 组织拿 B 组织 `agentId` 调用详情、删除、发布、运行配置时必须返回 404/403，且不得修改 B 组织数据。
- `/me/agents/run-logs`、详情接口只返回当前组织 trace。

### 对话与会话状态边界

- 同一 `sessionId` 可在不同组织中独立存在。
- `/ai/sessions/{sessionId}/messages` 与 `/ai/sessions/{sessionId}/state` 只能读取当前组织可见会话。
- 删除会话只删除当前组织的数据。

### 知识库与 RAG 边界

- `/admin/kb` 只列出当前组织知识库。
- A 组织不能通过 B 组织 `kbId/documentId/chunkId` 读取、发布、重建、删除或检索 B 组织知识。
- RAG 检索必须以当前组织过滤，即使传入其他组织知识库 ID 也不得返回内容。

### OpenAPI 与平台边界

- Agent OpenAPI credential、session map、usage、ledger 必须按组织隔离。
- 平台治理接口必须拒绝组织 token。
- 平台导出/清理只允许平台角色，且测试只使用 dry-run 或本地测试库。

## 验收标准

- 新增至少一个 focused integration test，覆盖两个新建组织之间的横向越权矩阵。
- 测试断言既检查 API 响应，也检查数据库中受保护组织数据未被改变。
- 本地运行 focused test 通过。
- 相关现有隔离/RBAC tests 通过或记录明确阻塞。
- `git diff --check` 与 assignment check 通过。

## 实施结果

- 新增 `MultitenantIsolationIntegrationTest`，通过真实 `/auth/register` 生成两个组织和 JWT token，使用 MockMvc 验证跨组织访问。
- 覆盖认证切换、组织资料、Agent 列表/详情/删除、会话列表/消息/删除、同名 workbench 会话状态、个人运行日志、知识库/文档/chunk 读写删除，以及组织 token 与平台 token 边界。
- 被拒绝的跨组织删除/修改后，测试继续断言 B 组织 Agent、会话、文档、chunk、KB 仍存在且状态未改变。
- 发现并修复 `ResponseStatusException` 被全局 fallback 映射成 HTTP 500 的问题；现在会保留原始 4xx/5xx 状态与 reason。
- focused test 与相关回归 `ChatSessionStateServiceIntegrationTest`、`AgentDefinitionDeleteIntegrationTest`、`PlatformAuthIntegrationTest` 均已通过。
