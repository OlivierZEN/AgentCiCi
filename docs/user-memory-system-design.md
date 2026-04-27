# 专属记忆系统设计文档

**版本**: v1.0  
**日期**: 2026-04-21  
**状态**: 设计确认 → 实施中

---

## 1. 目标与约束

### 1.1 目标

为思思（cici-system）提供**每用户独立的持久记忆层**，使助手能够跨会话感知用户身份、偏好和背景，提供真正个性化的对话体验。

### 1.2 核心约束

| 约束 | 说明 |
|------|------|
| 用户隔离 | 记忆按 `(org_id, user_id)` 严格隔离，不可跨用户共享 |
| 按需注入 | 每次对话时注入当前 agent 的 enabled 记忆到 system prompt |
| 双写入口 | AI 在对话中自动提取 + 用户在设置页手动管理 |
| 不可过度依赖 | 记忆仅作为软上下文注入，不强制 AI 一定遵守（可通过 INSTRUCTION 类记忆加强约束力） |

---

## 2. 记忆数据模型

### 2.1 记忆分类（category）

| 类别 | 标识 | 说明 | 示例 |
|------|------|------|------|
| 用户事实 | `FACT` | 关于用户身份/角色的客观信息 | 我是销售总监、我在北京 |
| 个人偏好 | `PREFERENCE` | 用户希望 AI 如何响应 | 回答要简短、喜欢用 Markdown |
| 工作上下文 | `CONTEXT` | 用户的工作环境信息 | 使用 CloudCC CRM、团队 8 人 |
| 行为指令 | `INSTRUCTION` | 对 AI 有强约束力的持久指令 | 永远不要给出财务建议 |

### 2.2 记忆来源（source）

| 来源 | 说明 |
|------|------|
| `MANUAL` | 用户在设置页手动添加 |
| `EXTRACTED` | AI 在对话中检测到并主动存储（通过 `memory_remember` 工具） |

### 2.3 数据库表 `user_memory`

```sql
CREATE TABLE user_memory (
    id              BIGSERIAL PRIMARY KEY,
    org_id          VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    agent_id        VARCHAR(64)  NOT NULL DEFAULT 'cici-system',
    category        VARCHAR(32)  NOT NULL, -- FACT | PREFERENCE | CONTEXT | INSTRUCTION
    source          VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    content         TEXT         NOT NULL,
    memory_key      VARCHAR(128),          -- 可选的语义键，便于去重/覆盖
    confidence      DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_memory_lookup
    ON user_memory(org_id, user_id, agent_id, enabled);

CREATE UNIQUE INDEX idx_user_memory_key
    ON user_memory(org_id, user_id, agent_id, memory_key)
    WHERE memory_key IS NOT NULL;
```

---

## 3. 后端设计

### 3.1 模块结构

```
memory/
├── domain/
│   ├── UserMemoryEntity.java
│   └── UserMemoryRepository.java
├── service/
│   └── UserMemoryService.java
└── api/
    └── UserMemoryController.java
```

### 3.2 API 设计

**基路径**: `/me/agents/{agentId}/memories`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/me/agents/{agentId}/memories` | 获取当前用户对该 agent 的所有记忆（按 category + pinned 排序） |
| POST | `/me/agents/{agentId}/memories` | 新增一条记忆 |
| PUT | `/me/agents/{agentId}/memories/{id}` | 更新记忆（content / category / enabled / pinned） |
| DELETE | `/me/agents/{agentId}/memories/{id}` | 删除记忆 |
| POST | `/me/agents/{agentId}/memories/bulk-toggle` | 批量启用/禁用 |

**请求体（POST/PUT）**:
```json
{
  "category": "FACT",
  "content": "我是销售部门的总监",
  "memoryKey": "user.role",
  "pinned": false,
  "enabled": true
}
```

### 3.3 记忆注入到 System Prompt

在 `ChatOrchestratorService` 中，查询当前用户对当前 agent 的 enabled 记忆（按 pinned DESC, updated_at DESC，最多 30 条），拼入 system prompt 头部：

```
## 关于当前用户的专属记忆

以下是用户告知你或你在过往对话中主动记录的信息，请在回答时充分参考：

【用户事实】
- 我是销售部门的总监
- 我在上海工作

【个人偏好】
- 回答请保持简洁，不超过 3 段
- 喜欢使用表格展示对比类数据

【工作上下文】
- 使用 CloudCC CRM 系统，团队共 8 人

【行为指令】
- 不要主动推荐外部工具或服务
```

### 3.4 memory_remember 工具

向 `ToolOrchestratorService` 注册内置工具，AI 在检测到需要记忆的信息时主动调用：

**工具定义**:
```json
{
  "name": "memory_remember",
  "description": "当用户提到关于自己的重要信息（身份、偏好、工作背景、持久指令）时，将其保存为专属记忆，以便在未来对话中持续参考。仅在对话中出现了明确值得长期记住的新信息时才调用。",
  "parameters": {
    "category": "FACT | PREFERENCE | CONTEXT | INSTRUCTION",
    "content": "要记忆的内容（中文，简洁清晰）",
    "memoryKey": "可选的语义键，如 user.role、user.location"
  }
}
```

当 `memoryKey` 有值时，执行 upsert（若已有相同 key 的记忆则覆盖，而非新增）。

---

## 4. 前端设计

### 4.1 入口

在「个人设置」弹窗（`MyEmailAccountsModal.tsx`）的 Tab 行新增第三个 Tab：**专属记忆**。

```
[我的工作流]  [我的邮箱]  [专属记忆]  ← 新增
```

### 4.2 UserMemoryPanel 界面结构

```
┌──────────────────────────────────────────────────────────┐
│  专属记忆                                    [+ 新增记忆] │
├──────────────────────────────────────────────────────────┤
│  💡 这些记忆将在每次与思思对话时自动注入上下文，           │
│     帮助思思更好地理解你的身份和需求。                    │
├──────────────────────────────────────────────────────────┤
│  [全部] [用户事实] [个人偏好] [工作上下文] [行为指令]     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  📌 行为指令                                             │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ✓  不要主动推荐外部工具       🤖 AI提取  [编辑][删]  │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  👤 用户事实                                             │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ✓  我是销售部门的总监         ✍️ 手动添加 [编辑][删] │ │
│  │ ✓  我在上海工作               🤖 AI提取  [编辑][删]  │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ⚙️  个人偏好                                            │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ✓  回答请保持简洁             ✍️ 手动添加 [编辑][删] │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  🏢 工作上下文                                           │
│  ┌────────────────────────────────────────────────────┐ │
│  │ ✓  使用 CloudCC CRM，8人团队  🤖 AI提取  [编辑][删]  │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  空状态提示: 还没有任何专属记忆。你可以手动添加，         │
│  或在对话中告诉思思你的信息，她会自动记住。               │
└──────────────────────────────────────────────────────────┘
```

### 4.3 新增/编辑记忆弹窗

- 类别选择（下拉或 tag 按钮）
- 内容文本域（2-4 行，支持最长 500 字）
- 置顶开关
- 启用开关

---

## 5. 记忆提取时机（AI 侧）

AI 在以下场景应主动调用 `memory_remember`：

| 触发场景 | 示例用户消息 | 应存内容 |
|------|------|------|
| 用户自我介绍 | "我是销售总监" | FACT: 用户是销售总监 |
| 用户表达偏好 | "以后回答简短一些" | PREFERENCE: 回答应简短 |
| 用户提供工作背景 | "我们团队用 CloudCC" | CONTEXT: 团队使用 CloudCC CRM |
| 用户下达持久指令 | "永远用中文回复我" | INSTRUCTION: 始终用中文回复 |

**不应**触发记忆的场景：临时问题、一次性查询、闲聊。

---

## 6. 隐私与安全

- 记忆内容不出现在任何公开 API 中（除了 `/me/` 前缀的个人接口）
- 删除账号时级联删除所有记忆
- AI 提取的记忆 `confidence < 0.8` 时建议标记为待确认
- 用户可一键清空全部记忆（`DELETE /me/agents/{agentId}/memories/all`）

---

## 7. 实施分阶段

| 阶段 | 内容 | 预计工作量 |
|------|------|------|
| Phase 1 (当前) | DB + CRUD API + 手动维护 UI + 系统提示注入 | 1-2天 |
| Phase 2 | memory_remember 工具 + AI 自动提取 | 0.5天 |
| Phase 3 | 记忆摘要压缩 + 向量相似度召回（大量记忆时）| 待评估 |

---

## 8. 关联设计决策

- 记忆属于 `(org_id, user_id, agent_id)` 三元组，当前 Phase 1 agent_id 默认为 `cici-system`，后续可扩展到其他内置/自定义 agent
- 记忆注入上限默认 30 条；如记忆过多，Phase 3 改为向量召回最相关 N 条
- `memory_key` 机制确保 AI 反复提取相同维度的信息时不重复累积（如每次说"我在上海"只保留一条 `user.location`）
