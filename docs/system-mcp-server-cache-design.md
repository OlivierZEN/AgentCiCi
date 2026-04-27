# 系统 MCP 服务器缓存机制实现设计

更新时间：2026-04-23  
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文用于为“系统 MCP 服务器设置”补齐一套可实施的缓存方案，使后台工具页具备类似 Dify 的体验：

1. MCP 卡片可直接展示“包含 N 个工具”“更新于 X 前”。
2. 详情页优先展示最近一次成功发现的工具快照，而不是每次都实时请求远端 MCP。
3. 管理员可手动点击“刷新”触发重新发现，并覆盖缓存。
4. 即使远端 MCP 暂时不可用，系统仍可保留并展示上一次成功快照，避免页面完全空白。

本次设计聚焦“工具发现缓存（tools/list snapshot）”，不改变现有 `tools/call` 的实时执行语义。

## 2. 设计结论

本次改造采用以下结论作为基线：

1. **缓存对象**是“某个组织下某个 MCP 服务器最近一次成功发现到的工具清单及其元数据”。
2. **缓存层次**分两层：
   - 进程内短期缓存：保留现有 `ConcurrentHashMap`，用于减少同一实例内重复解析。
   - 数据库存储缓存：新增持久化快照，支撑页面刷新后仍可展示“工具数 / 更新时间 / 最近快照”。
3. **前端默认读缓存**，只有用户显式点击“刷新”时才强制访问远端 MCP 服务器。
4. **缓存失败不清空旧快照**：刷新失败时保留上一次成功缓存，并把状态标记为 `error`。
5. **缓存只允许手动刷新**：Phase 1 不引入自动定时刷新、TTL 过期刷新或后台轮询机制。

一句话概括：

**列表页看持久化快照，详情页先读缓存，只有管理员手动点击刷新时才访问远端。**

## 3. 现状分析

结合当前仓库，MCP 管理已经具备“发现工具”和“内存缓存”的基础，但距离产品需要的缓存体验还有明显差距。

### 3.1 已有能力

- 后端 `McpServerService` 已有：
  - `discoverTools(orgId, serverId)`：调用远端 `initialize + tools/list`
  - `toolCache`：进程内内存缓存
  - `invalidateCache(serverId)`：编辑/删除时失效
- 前端 `AdminToolsPage` 已有：
  - MCP 列表页
  - MCP 详情页
  - “刷新工具”按钮
  - 工具页签首次打开时自动调用 `/mcp-servers/{id}/discover`

### 3.2 当前问题

#### 问题一：缓存仅存在于单进程内存

当前 `toolCache` 在服务重启后丢失，也无法让前端拿到“最近一次成功发现时间”“最近工具数”等元数据。

#### 问题二：列表页无法展示 Dify 风格的更新时间

`GET /mcp-servers` 只返回 `mcp_server.updatedAt`。  
这个时间是“配置被编辑”的时间，不是“工具缓存被更新”的时间，因此无法正确展示“更新于 1 天前”。

#### 问题三：详情页默认行为过于依赖远端可用性

当前工具页签在 `tools.length === 0` 时会直接调用 `/discover`。如果远端 MCP 短暂不可用，管理员会看到“暂无工具/发现失败”，而不是上一次可用快照。

#### 问题四：缓存状态不可见

页面无法区分：

- 从未发现过
- 已有缓存可用
- 最近一次刷新失败

这会让“是否需要点刷新、刷新失败会不会影响已有能力”都不透明。

## 4. 设计范围与非目标

### 4.1 设计范围

本次设计覆盖：

- MCP 工具发现结果的持久化缓存
- 缓存状态与手动刷新策略
- MCP 管理后台列表/详情页展示
- 手动刷新交互
- 后端接口与服务层改造

### 4.2 非目标

本次设计不覆盖：

- `tools/call` 结果缓存
- 聊天运行时的工具调用结果复用
- MCP Server 健康检查的定时探活
- 分布式缓存中间件（Redis）引入
- 自动后台轮询刷新
- TTL 自动过期与自动补刷新

## 5. 目标体验

## 5.1 列表页

每个 MCP 卡片展示：

- 名称
- 状态（已启用 / 已停用）
- 工具数
- 缓存更新时间（如“更新于 1 天前”）
- 可选的缓存状态提示（如“未同步”“刷新失败”）

### 目标规则

- 有成功缓存时：显示 `工具数 + 更新于`
- 从未刷新过时：显示 `未同步`
- 最近刷新失败但有旧快照时：仍显示旧工具数，同时提示 `上次刷新失败`

## 5.2 详情页

工具页签顶部展示：

- 最近缓存时间
- 缓存状态
- “刷新”按钮

工具列表展示逻辑：

1. 优先展示数据库中的最近一次成功快照
2. 若无快照，则展示“未同步”，等待管理员手动点击刷新
3. 手动点击“刷新”时，强制访问远端并覆盖缓存
4. 刷新失败时不清空旧列表

## 6. 总体方案

## 6.1 缓存分层

### A. 数据库存储缓存（事实源）

新增 `mcp_server` 相关缓存字段，保存最近一次工具发现快照及元数据。

推荐 Phase 1 直接扩展现有 `mcp_server` 表，而不是新建独立快照表，原因：

1. 需求只关心“最近一次成功快照”，不需要历史版本。
2. 当前后台管理接口已经以 `McpServerEntity` 为核心，扩字段改动最小。
3. 列表页天然要展示缓存摘要，放在同一实体最容易取数。

### B. 进程内缓存（性能层）

保留现有：

- `toolCache`
- `serverCache`
- `toolServerIndex`

但其职责调整为：

- 仅作为当前 JVM 实例内的热缓存
- 启动后可由数据库快照预热
- 一旦数据库快照更新，内存缓存同步覆盖

换句话说：

**数据库缓存负责“可见性与持久性”，内存缓存负责“单实例性能”。**

## 6.2 数据模型设计

### 6.2.1 `mcp_server` 新增字段

建议新增以下字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `tool_cache_json` | TEXT / JSON | 最近一次成功发现的工具列表快照 |
| `tool_cache_count` | INT | 快照中的工具数量 |
| `tool_cache_status` | VARCHAR(32) | 缓存状态：`empty` / `ready` / `refreshing` / `error` |
| `tool_cache_updated_at` | TIMESTAMP | 最近一次成功刷新时间 |
| `tool_cache_error_message` | TEXT | 最近一次刷新失败原因（仅摘要） |
| `tool_cache_last_attempt_at` | TIMESTAMP | 最近一次尝试刷新时间 |
| `tool_cache_version` | VARCHAR(64) | 快照版本指纹，可为工具名+schema 哈希 |

### 6.2.2 字段语义

- `tool_cache_updated_at`
  - 只在“成功拿到 tools/list 并写入快照”时更新
  - 是前端“更新于 xx 前”的唯一事实源

- `tool_cache_status`
  - `empty`：从未成功刷新
  - `ready`：缓存可用
  - `refreshing`：正在刷新
  - `error`：最近一次刷新失败，但可能仍存在旧快照

- `tool_cache_error_message`
  - 用于后台提示“刷新失败：xxx”
  - 不替代完整日志，日志仍写服务端

## 7. 后端改造设计

## 7.1 实体层

在 `McpServerEntity` 增加缓存字段与对应 getter/setter。

同时建议新增一个派生方法：

- `hasToolCache()`

用于减少控制器和 service 中的状态判断散落。

## 7.2 服务层职责调整

### 现有方法调整

#### `discoverTools(orgId, serverId)`

调整为“强制刷新并写回缓存”的语义：

1. 读取 `McpServerEntity`
2. 将 `tool_cache_status` 标记为 `refreshing`
3. 访问远端 `initialize + tools/list`
4. 成功时：
   - 序列化工具列表到 `tool_cache_json`
   - 更新 `tool_cache_count`
   - 更新 `tool_cache_updated_at`
   - 清空 `tool_cache_error_message`
   - 设置 `tool_cache_status = ready`
   - 更新内存 `toolCache`
5. 失败时：
   - 更新 `tool_cache_last_attempt_at`
   - 写入 `tool_cache_error_message`
   - 若已有旧快照：`tool_cache_status = error`
   - 若无旧快照：`tool_cache_status = error`
   - 不清空旧的 `tool_cache_json`

#### `getTools(orgId, serverId)`

调整为“只读缓存”的语义：

1. 先查内存缓存
2. 没有内存缓存时查数据库快照
3. 若数据库有快照：
   - 反序列化并回填内存缓存
   - 返回快照
4. 若数据库无快照：
   - 返回空结果和 `empty` 状态，不自动访问远端

注意：

- `getTools` 只负责读缓存，不承担自动刷新职责
- 真正访问远端只在“管理员点击刷新”时发生

### 新增建议方法

建议新增：

- `getToolCacheSummary(orgId, serverId)`
- `getToolCacheSnapshot(orgId, serverId)`
- `refreshToolCache(orgId, serverId)`

目的：

1. 让“读取缓存”和“强制刷新”语义分离
2. 避免控制器仍用 `/discover` 同时承担“读缓存”和“刷缓存”两种角色

## 7.3 控制器接口设计

### 7.3.1 列表接口

`GET /mcp-servers`

返回字段补充：

```json
{
  "id": 1,
  "name": "CloudCC-MCP-CRM",
  "enabled": true,
  "toolCacheCount": 43,
  "toolCacheStatus": "ready",
  "toolCacheUpdatedAt": "2026-04-22T09:00:00Z",
  "toolCacheLastAttemptAt": "2026-04-22T09:00:00Z",
  "toolCacheErrorMessage": ""
}
```

注意：

- `updatedAt` 继续表示“服务器配置更新时间”
- 新增 `toolCacheUpdatedAt` 表示“工具缓存更新时间”
- 前端列表页必须改用 `toolCacheUpdatedAt`

### 7.3.2 详情缓存读取接口

建议新增：

`GET /mcp-servers/{id}/tools`

默认返回缓存快照：

```json
{
  "serverId": 1,
  "cacheStatus": "ready",
  "cacheUpdatedAt": "2026-04-22T09:00:00Z",
  "cacheErrorMessage": "",
  "tools": [...]
}
```

行为规则：

- 有缓存：直接返回
- 无缓存：返回 `empty` 状态与空列表

### 7.3.3 手动刷新接口

保留或重命名现有：

- 现有：`POST /mcp-servers/{id}/discover`
- 建议语义：`POST /mcp-servers/{id}/refresh-tools`

Phase 1 为兼容前端，可先保留 `/discover`，但其响应体应补充缓存元数据。

建议响应：

```json
{
  "cacheStatus": "ready",
  "cacheUpdatedAt": "2026-04-23T08:00:00Z",
  "toolCount": 43,
  "tools": [...]
}
```

## 7.4 运行时工具目录影响

当前 `getAllToolsForOrg()` / `executeTool()` 依赖 `getTools()`。

本次改造后，运行时会默认读缓存快照，因此有两个好处：

1. 聊天编排和 Agent Builder 展示拿到的是同一份工具定义来源。
2. 即使远端 MCP 短暂不可用，只要已有缓存，工具目录仍可构建。

同时需要接受一个约束：

- 新增/删除工具不会立刻反映到平台，直到管理员手动刷新。

这是刻意选择的产品语义，符合“缓存快照”模型。

## 7.5 并发与一致性

为避免同一服务器被重复刷新，建议增加“单服务器刷新互斥”：

- `ConcurrentHashMap<Long, ReentrantLock>` 或等价机制

规则：

1. 同一 `serverId` 同时只允许一个刷新任务执行
2. 后来的刷新请求可：
   - 直接复用正在进行中的结果，或
   - 返回“正在刷新中”

Phase 1 可采用最简单策略：

- 若发现已有刷新进行中，直接返回当前缓存和 `refreshing` 状态

## 8. 前端改造设计

## 8.1 类型扩展

`AdminToolsPage.tsx` 中的 `McpServer` 增加：

- `toolCacheCount`
- `toolCacheStatus`
- `toolCacheUpdatedAt`
- `toolCacheLastAttemptAt`
- `toolCacheErrorMessage`

详情页工具返回值新增缓存元数据结构，而不是只返回 `McpTool[]`。

## 8.2 列表页展示改造

当前列表页只显示 transport 和 URL，建议增加一行摘要：

- `43 个工具`
- `更新于 1 天前`
- `未同步 / 刷新失败`

建议优先级：

1. 有 `toolCacheUpdatedAt`：显示相对时间
2. 无 `toolCacheUpdatedAt` 且 `toolCacheStatus=empty`：显示 `未同步`
3. `toolCacheStatus=error` 且 `toolCacheCount>0`：显示 `上次刷新失败`

## 8.3 详情页展示改造

工具页签头部建议改为：

- 左侧：`包含 43 个工具`
- 辅助信息：`更新于 1 天前`
- 状态文案：`缓存可用 / 未同步 / 上次刷新失败`
- 右侧：刷新按钮

### 交互规则

1. 进入工具页签：
   - 调用 `GET /mcp-servers/{id}/tools`
   - 若返回缓存快照则直接渲染
   - 若返回 `empty` 则展示空态与“刷新”按钮
2. 点击刷新：
   - 按钮进入 loading
   - 调用 `POST /mcp-servers/{id}/discover`
   - 成功后更新列表、更新时间、列表页摘要
   - 失败后保留旧列表，toast 提示错误

## 8.4 相对时间格式化

建议新增统一方法，如：

- `刚刚`
- `5 分钟前`
- `2 小时前`
- `1 天前`

避免直接显示 ISO 时间，保持和 Dify 类似的阅读感。

## 9. 状态机设计

## 9.1 服务端缓存状态

```text
empty
  └─(首次手动刷新成功)──> ready
  └─(首次手动刷新失败)──> error

ready
  └─(手动刷新成功)───────> ready
  └─(手动刷新失败)───────> error

error
  └─(手动刷新成功)───────> ready
```

说明：

- `error` 不代表无快照，只代表“最近一次手动刷新失败”
- 前端应根据 `toolCacheCount > 0` 判断是否仍可展示旧列表

## 10. 失败处理与降级策略

### 场景一：远端 MCP 暂时不可用

- 保留旧快照
- 状态标记为 `error`
- 前端仍展示旧工具列表
- 提示“上次刷新失败，当前展示缓存快照”

### 场景二：缓存 JSON 反序列化失败

- 记录日志
- 清理内存缓存
- 数据库存储标记为 `error`
- 等待管理员再次手动刷新

### 场景三：编辑服务器配置

当 URL / headers / transportType / timeout 等配置变化时：

1. 清空内存缓存
2. 数据库存储状态置为 `empty`
3. 清空 `tool_cache_json/tool_cache_count/tool_cache_updated_at`

原因：

- 旧配置下的工具快照不应继续作为新配置的事实源

## 11. 实施步骤建议

## Phase 1：最小闭环

1. Flyway 为 `mcp_server` 增加缓存字段
2. 扩展 `McpServerEntity`
3. `McpServerService` 支持数据库缓存读写
4. `GET /mcp-servers` 返回缓存摘要
5. 新增 `GET /mcp-servers/{id}/tools`
6. `POST /discover` 改为“强制刷新并更新缓存”
7. 前端列表页展示 `工具数 + 更新时间`
8. 前端详情页优先读缓存，保留“刷新”按钮

### Phase 1 验收标准

1. 服务重启后，MCP 列表仍能显示上次成功工具数与更新时间
2. 远端 MCP 不可用时，详情页仍能展示旧工具列表
3. 手动点击刷新后，列表与详情页的更新时间同步更新
4. 新建但未刷新过的服务器显示 `未同步`

## Phase 2：增强项

可选增强：

1. 刷新互斥与结果复用
2. 工具快照版本比对（新增/删除/Schema 变化提示）
3. 在 Agent Builder 中同样显示缓存更新时间

## 12. 对现有代码的落点建议

### 后端

- `backend/src/main/resources/db/migration/`
  - 新增 `mcp_server` 缓存字段迁移
- `backend/src/main/java/com/codehouse/ciciassistant/mcp/domain/McpServerEntity.java`
  - 增加缓存字段
- `backend/src/main/java/com/codehouse/ciciassistant/mcp/service/McpServerService.java`
  - 增加数据库快照读写与缓存状态流转
- `backend/src/main/java/com/codehouse/ciciassistant/mcp/api/McpServerController.java`
  - 新增读取缓存接口，补缓存摘要返回

### 前端

- `frontend/src/admin/pages/AdminToolsPage.tsx`
  - MCP 列表卡片显示工具数/更新时间/状态
  - 工具页签改为“先读缓存、后手动刷新”

## 13. 推荐实施取舍

如果这轮你希望尽快落地，并且尽量少改表/少改前端逻辑，我建议按下面这个取舍做：

1. **先把缓存快照直接存在 `mcp_server` 表上**
2. **先做 `GET /mcp-servers/{id}/tools` + `POST /mcp-servers/{id}/discover` 双接口分工**
3. **列表页只先补三样：工具数、更新时间、未同步/失败状态**
4. **缓存仅支持手动刷新**

这是改动最小、收益最高的一版，基本就能把你图里那种“像 Dify 一样有更新时间、有刷新、有旧快照”的核心体验补齐。
