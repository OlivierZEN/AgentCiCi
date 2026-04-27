# Tavily Search 内置 Skill 设计文档

更新时间：2026-04-22（v2，已确认 Phase 1 范围）  
适用项目：`cc-cici-assistant`  
状态：**已确认，可进入实现**

## 0. 背景

Tavily 官方在 [tavily-ai/skills](https://github.com/tavily-ai/skills/blob/main/skills/tavily-search/SKILL.md)
提供了一套「Anthropic Skill」格式的 `tavily-search`，其本质是：**面向 LLM 的网页搜索能力**，返回带相关性评分、摘要、元数据的结构化搜索结果，用于给 Agent 补「当前互联网上的最新信息 / 公开资料」。

同目录下还提供 [`tavily-extract`](https://github.com/tavily-ai/skills/blob/main/skills/tavily-extract/SKILL.md)：**对已知 URL 做正文抽取**，返回清洗过的 markdown / text。本次 Phase 1 一并收入。

本次目标：在 `cc-cici-assistant` 里把 Tavily 的 web-search / web-extract 能力做成**系统内置的标准能力**，让平台内置 Agent（`cici-system`）以及任何用户自定义 Agent，都能在工具白名单 / 技能范围里直接启用它。

> Tavily 官方 Skill 是基于 `tvly` CLI 的形态；在 Java/Spring 服务端里我们**不走 CLI 也不走 Claude Skill loader**，而是**直接调 Tavily REST API**（`POST https://api.tavily.com/search` / `POST https://api.tavily.com/extract`），这是与当前 `EmailToolService`、`CloudccOpenApiService` 一致的做法。官方 SKILL.md 在本方案中只作为「能力规格 / Prompt 提示语」参考。

## 0.1 确认纪要（v2 基线）

Phase 1 四个关键确认：

1. **范围**：Phase 1 同时交付 `tavily_search` 与 `tavily_extract` 两个原生工具；`tavily_research` 留给 Phase 2。
2. **API Key 管理**：**仅支持租户级** `integration_app('tavily')`；**不**提供 `TAVILY_API_KEY` 环境变量兜底，也不在 `application.yml` 放明文 key。未配置的租户调用时返回可读错误 `TAVILY_NOT_CONFIGURED`。
3. **默认绑定**：`web-search` 内置 skill 默认挂到 `cici-system`，`activation_mode = intent-route`、`enabled = true`。
4. **Tool seed 机制**：新增 `ToolDefinitionService.ensureBuiltinTools(orgId)` **懒加载器**，在新建租户时自动补齐 `tavily_search` / `tavily_extract` 等内置工具行；**不**使用 Flyway 静态 seed。迁移只负责表结构（若需要）。风格与现有 `SkillDefinitionService.ensureBuiltinSkills` 完全对齐。

---

## 1. 目标与范围

### 1.1 本次要做的

1. 新增**两个系统内置原生工具**：
   - **`tavily_search`**：Web 搜索，通过 `ToolOrchestratorService` function-calling 分发，由 `TavilySearchToolService` 调 `POST https://api.tavily.com/search`。
   - **`tavily_extract`**：对给定 URL 做正文抽取，调 `POST https://api.tavily.com/extract`，返回清洗后的 markdown / text。
   - 两者都出现在 `GET /tools` 目录与 Agent Builder `TOOL_CATALOG` 中。

2. 新增**系统内置 Skill** `web-search`（`builtin = true`）：
   - `tool_whitelist = ["tavily_search", "tavily_extract"]`；
   - `prompt_fragment` 描述：先 `tavily_search` 取候选来源 → 必要时 `tavily_extract` 抓取目标 URL 正文 → 再综合回答；
   - `risk_level = LOW`、`output_contract` 要求带 source URL。

3. **默认绑定**：`web-search` 写进 `DEFAULT_AGENT_SKILLS`，绑定到 `cici-system`，`activation_mode = intent-route`、`enabled = true`、`priority = 20`。

4. **API Key 管理（租户级唯一方式）**：
   - 仅走 `integration_app` 表，`code = "tavily"`，`config_json` 里 `apiKey` 字段加密存储；
   - 若当前 `orgId` 未配置 → 调用时工具结果返回 `{success:false, code:"TAVILY_NOT_CONFIGURED"}`，**不**从 env / yaml 兜底；
   - `application.yml` 仅保留 **非凭据** 的默认参数（`api-base`、`default-max-results`、`default-search-depth` 等）。

5. **Admin 入口**：在 Admin 侧 `integration_app` / 三方集成页新增 Tavily 配置 Card（复用现有 `IntegrationAppService`），含「测试连接」按钮。

6. **Tool 懒加载**：新增 `ToolDefinitionService.ensureBuiltinTools(orgId)`，在首次访问 `GET /tools`、`GET /skills`、Agent 相关接口、或新建租户 hook 时为该 org 幂等写入 `tavily_search` / `tavily_extract` 两行 `tool_definition`。**不**依赖 Flyway 静态 seed。

### 1.2 本次不做的

- **不**做 `tavily-research`（多轮综合检索）等其它同系列能力（Phase 2）。
- **不**做自建缓存层 / 搜索结果持久化 / 消费统计看板（Phase 2/3）。
- **不**做前端「Web 搜索」独立面板；能力完全走聊天 tool-calling。
- **不**做 MCP Server 形态暴露（备选方案记录，但 Phase 1 走原生）。
- **不**在 Flyway 迁移中 INSERT 种子数据；迁移只做表结构（若需要）。

### 1.3 与现有体系的对齐

| 现有模式 | 本次对齐方式 |
|---------|-------------|
| `EmailToolService` 原生实现 + `ToolOrchestratorService` 分支 | 新增 `TavilyToolService`，`ToolOrchestratorService.executeTool` 增加 `tavily_` 前缀分支（覆盖 search/extract） |
| `CloudccOpenApiService` 通过 `IntegrationAppService` 读租户凭据 | `TavilyToolService` 通过 `integration_app('tavily')` 读 `apiKey` |
| `BUILTIN_SKILLS` Java 种子 + `ensureBuiltinSkills` 懒加载 | `web-search` 注入 `BUILTIN_SKILLS` |
| `DEFAULT_AGENT_SKILLS` 默认挂 `cici-system` | 新增 `("cici-system", "web-search")` |
| `ToolController.BUILTIN_TOOLS` HTTP 目录 | 新增 `tavily_search` / `tavily_extract` 两条 |
| `SkillDefinitionService.ensureBuiltinSkills` 幂等种子 | 新增 `ToolDefinitionService.ensureBuiltinTools(orgId)` 同构幂等种子 |
| 前端 `AgentBuilderShell.TOOL_CATALOG` UI 预设 | 新增「Tavily 网页搜索」「Tavily 正文抽取」两条 |

---

## 2. 术语

| 术语 | 含义 |
|------|------|
| **Tavily Search** | Tavily 提供的 LLM-optimized web search API |
| **Tavily Extract** | Tavily 提供的 URL 正文抽取 API |
| **`tavily_search`** | Function calling 名称，对应 `tool_definition.tool_name` |
| **`tavily_extract`** | Function calling 名称，对应 `tool_definition.tool_name` |
| **`web-search`** | 新增的 builtin skill code，承载 Prompt 策略 + 工具白名单（含 search/extract） |
| **Tavily API Key** | Tavily 侧签发的租户 API Key，仅通过 `integration_app('tavily').config_json.apiKey` 配置 |

---

## 3. 能力与用户场景

### 3.1 典型触发意图

- 「最新」「今天」「上周」「最近一年」等时间词 → `time_range`
- 「在 xx 网站 / 域名查」 → `include_domains`
- 「新闻 / 财经」 → `topic`
- 「帮我找资料 / 有没有关于 xxx 的文章」 → 普通 search
- 当 Agent **RAG 命中为空 / 置信度低** 且问题具备**外部事实性**时，可由 skill 的 `activationMode = intent-route` 把它路由进来

### 3.2 Agent 侧调用样例

```text
user: 帮我查下 2026 年 Q1 国内大模型开源进展的新闻
→ LLM 触发 tool_call: tavily_search({
    "query": "2026 Q1 China open source large language models progress",
    "topic": "news",
    "time_range": "quarter",   // 会被映射为最接近的 quarter/3month 语义
    "max_results": 8,
    "include_answer": "basic"
  })
→ TavilySearchToolService 调 Tavily API，返回命中 URL + snippets + answer
→ LLM 基于结果生成带引用的回答
```

### 3.3 输出契约（`output_contract`）

- 引用必须带 **源 URL**（来自 `result.url`）与标题；
- 若 Tavily 返回了 `answer` 字段，允许先抛给用户一个简短综述，但结尾仍需要附 3~5 条可点击来源；
- 当结果全部为空或 API 报错，明确告知「未在公开互联网查到，不进行编造」。

---

## 4. 方案总览

```
┌──────────────── Agent Runtime ────────────────┐
│                                               │
│   ChatOrchestratorService.chat(Stream)        │
│           │                                   │
│           ▼                                   │
│   SkillResolverService.resolve ─► Capability  │
│           │   effectiveToolNames 含           │
│           │   "tavily_search" / "tavily_extract"│
│           ▼                                   │
│   AliyunBailianClient (tool_call)             │
│           │                                   │
│           ▼                                   │
│   ToolOrchestratorService.executeTool         │
│     ├─ cloudcc_* ─► CloudccOpenApiService     │
│     ├─ email_*  ─► EmailToolService           │
│     ├─ memory_* ─► UserMemoryService          │
│   ✚ ├─ tavily_* ─► TavilyToolService          │
│     │             ├ search  → POST /search    │
│     │             └ extract → POST /extract   │
│     └─ else     ─► McpServerService           │
└───────────────────────────────────────────────┘

        ↕ apiKey 读取（唯一路径）
┌────────────────────────────────────────────┐
│  IntegrationAppService  (code = "tavily")  │
│  config_json = { apiKey: "tvly-...",       │
│                  defaultMaxResults: 5,     │
│                  defaultSearchDepth: ...,  │
│                  defaultExtractFormat:"md"}│
└────────────────────────────────────────────┘

         ↕ 新建租户 / 首次访问 /tools /skills
┌────────────────────────────────────────────┐
│  ToolDefinitionService.ensureBuiltinTools  │
│    (orgId)  幂等写入 tool_definition 行：   │
│      - tavily_search                       │
│      - tavily_extract                      │
└────────────────────────────────────────────┘
```

Skill 侧：

```
SkillDefinitionService.BUILTIN_SKILLS
   └── web-search (builtin=true)
        ├ prompt_fragment  : "当用户问题涉及互联网最新公开信息……
        │                     先 tavily_search 得候选来源，
        │                     必要时 tavily_extract 抓取 URL 正文"
        ├ tool_whitelist   : "tavily_search,tavily_extract"
        ├ kb_whitelist     : ""           // 不收敛 KB
        ├ handoff_rule     : ""
        ├ output_contract  : "结论 + 3~5 条带 URL 的来源"
        └ risk_level       : LOW

DEFAULT_AGENT_SKILLS
   ├── cici-system  ─ web-search      (intent-route, priority 20, enabled)
   ├── cici-system  ─ knowledge-first (现有)
   └── ... 其它已存在绑定
```

---

## 5. 数据模型与迁移

### 5.1 `tool_definition` 不走 Flyway 静态 seed

结合 v2 确认：**不**新增 `V19__tavily_*.sql` 做 INSERT。迁移文件只在**必要时**扩字段（当前 `tool_definition` 结构已足够，Phase 1 无迁移）。

取而代之的是新增 `ToolDefinitionService.ensureBuiltinTools(orgId)`：

- 内部维护一张与 `SkillDefinitionService.BUILTIN_SKILLS` 对应的 **`BUILTIN_TOOLS` 数组**（`tavily_search` / `tavily_extract` 等），每项包含 `toolName` / `displayName` / `description` / `provider` / `riskLevel` / `enabled`；
- 幂等写入：`findByOrgIdAndToolName(orgId, toolName)` 不存在则 INSERT；已存在但 `builtin=true` 时允许按种子覆盖 `displayName` / `description` / `provider` / `riskLevel`，`enabled` 保留用户修改；
- 调用时机（与 skill seed 完全同构）：
  - `GET /tools`：进入前触发 `ensureBuiltinTools(currentOrgId)`；
  - `GET /skills` / `SkillDefinitionService.ensurePhaseOneDefaults`：改为先 `ensureBuiltinTools` 再 `ensureBuiltinSkills`，确保 skill 的 `tool_whitelist` 指向的 tool 记录已存在；
  - Agent 相关接口（`GET /agents`、`POST /agents/{id}/compile` 等）：进入前触发；
  - 新建租户的初始化钩子：在 `OrgProvisioningService` / 等价入口显式调用（若当前项目尚无统一 org provisioning 流程，则以「首个使用该 org 的人发起任意 skill/agent 请求时被动触发」为兜底）。

> 与 skill 的 seed 思路一致：**迁移只造结构，运行时造数据**。

### 5.2 `integration_app` 新增 `code = "tavily"` 预设

- `code = "tavily"`
- `label = "Tavily（Web 搜索 / 正文抽取）"`
- `config_schema`（给管理端渲染表单用，仅在 `IntegrationAppService` 中以代码声明，不新增表）：
  - `apiKey` string **required secret**（必须加密存储）
  - `defaultSearchDepth` enum [`basic`,`advanced`,`fast`,`ultra-fast`]，默认 `basic`
  - `defaultMaxResults` int 1–20，默认 5
  - `defaultTopic` enum [`general`,`news`,`finance`]，默认 `general`
  - `defaultIncludeAnswer` enum [`none`,`basic`,`advanced`]，默认 `none`
  - `defaultExtractFormat` enum [`markdown`,`text`]，默认 `markdown`（给 `tavily_extract` 用）
  - `timeoutMs` int 2000–30000，默认 10000

> 现仓库 `integration_app` 结构已支持 `code + config_json`，**Phase 1 无新表、无新迁移**。

### 5.3 **不新增**独立表

不为 Tavily 单独建 `tavily_search_log` / `web_search_cache`。后续如果出现「热查询缓存 / 计费 / 治理」需求再新增 `web_search_call_log`（Phase 2）。

---

## 6. 后端设计

### 6.1 模块划分

```
backend/src/main/java/com/codehouse/ciciassistant/
 └── tool/
     └── tavily/
         ├── TavilyToolService.java             // 承载 search + extract 两个 function
         ├── TavilyClient.java                  // RestClient / WebClient 封装
         ├── TavilyProperties.java              // @ConfigurationProperties（仅非凭据默认参数）
         └── dto/
             ├── TavilySearchRequest.java
             ├── TavilySearchResponse.java
             ├── TavilyResult.java
             ├── TavilyExtractRequest.java
             └── TavilyExtractResponse.java
```

### 6.2 `TavilyProperties`

**只保存非凭据默认值**，不再持有 API Key：

```java
@ConfigurationProperties("cici.tool.tavily")
public record TavilyProperties(
    String   apiBase,              // https://api.tavily.com
    int      defaultMaxResults,    // 5
    String   defaultSearchDepth,   // basic
    String   defaultTopic,         // general
    String   defaultExtractFormat, // markdown
    Duration timeout               // 10s
) {}
```

`application.yml`：

```yaml
cici:
  tool:
    tavily:
      api-base: https://api.tavily.com
      default-max-results: 5
      default-search-depth: basic
      default-topic: general
      default-extract-format: markdown
      timeout: 10s
```

> **刻意不提供 `api-key` / `TAVILY_API_KEY`**：v2 确认 API Key 只走 `integration_app`，避免生产误将明文密钥写入配置。

### 6.3 `TavilyToolService` 职责

| 职责 | 说明 |
|------|------|
| 读取 API Key | **唯一路径**：`IntegrationAppService.findByOrgIdAndCode(orgId, "tavily")` → `config_json.apiKey`（经 `SecretCipherService.decrypt`）。无记录或 `apiKey` 为空 → tool result `{success:false, code:"TAVILY_NOT_CONFIGURED"}` |
| function 分发 | 单服务内部根据 `toolName` 分发 `tavily_search` / `tavily_extract`，两者共享同一 `TavilyClient` |
| 参数归一（search） | 将 function calling 入参映射到 `TavilySearchRequest`；钳制 `max_results ≤ 20`、枚举校验 `search_depth` / `topic` / `time_range` |
| 参数归一（extract） | `urls`：非空 array，长度 1–20；`format` 默认 integration 配置；`extract_depth` 枚举 `basic`/`advanced` |
| 调 HTTP | `POST {apiBase}/search` 或 `/extract`，`Authorization: Bearer <apiKey>`，`timeout` 可配；失败统一包成 tool result |
| 输出整形（search） | 回吐 `answer` + `results[].{title,url,snippet,score,publishedDate}` + `responseTime`，不把 raw HTML / 图片直塞上下文 |
| 输出整形（extract） | 回吐 `results[].{url,content,rawContentLength}`，超长 content 截断到配置阈值（默认 20000 字符，`cici.tool.tavily.max-extract-chars`） |
| 审计 | 复用 `ToolInvocationLogService`：记录 `orgId/userId/agentId/toolName/queryOrUrls 摘要/latency/resultCount/httpStatus` |

### 6.4 Function Calling Schema

在 `ToolOrchestratorService.getToolDefinitions()` 中注册**两个** function：

```json
{
  "type": "function",
  "function": {
    "name": "tavily_search",
    "description": "Search the public web via Tavily and return LLM-optimized results with snippets, relevance scores and source URLs. Use when the user asks for fresh, external or verifiable online information.",
    "parameters": {
      "type": "object",
      "required": ["query"],
      "properties": {
        "query":              { "type": "string",  "maxLength": 400 },
        "search_depth":       { "type": "string",  "enum": ["ultra-fast","fast","basic","advanced"] },
        "max_results":        { "type": "integer", "minimum": 1, "maximum": 20 },
        "topic":              { "type": "string",  "enum": ["general","news","finance"] },
        "time_range":         { "type": "string",  "enum": ["day","week","month","year"] },
        "start_date":         { "type": "string",  "format": "date" },
        "end_date":           { "type": "string",  "format": "date" },
        "include_domains":    { "type": "array", "items": { "type": "string" } },
        "exclude_domains":    { "type": "array", "items": { "type": "string" } },
        "country":            { "type": "string" },
        "include_answer":     { "type": "string",  "enum": ["none","basic","advanced"] },
        "include_raw_content":{ "type": "string",  "enum": ["none","markdown","text"] }
      }
    }
  }
}
```

```json
{
  "type": "function",
  "function": {
    "name": "tavily_extract",
    "description": "Fetch one or more URLs via Tavily and return cleaned readable content (markdown or text). Use after tavily_search when you need the full body of a specific page, or when the user pastes a URL.",
    "parameters": {
      "type": "object",
      "required": ["urls"],
      "properties": {
        "urls":          { "type": "array", "minItems": 1, "maxItems": 20, "items": { "type": "string", "format": "uri" } },
        "format":        { "type": "string", "enum": ["markdown","text"] },
        "extract_depth": { "type": "string", "enum": ["basic","advanced"] },
        "include_images":{ "type": "boolean" }
      }
    }
  }
}
```

### 6.5 `ToolOrchestratorService.executeTool` 分发

在现有 `email_*` / `cloudcc_*` / `memory_*` 之后增加统一 `tavily_` 分支：

```java
if (toolName.startsWith("tavily_")) {
    return tavilyToolService.invoke(orgId, userId, agentId, toolName, args);
}
```

`TavilyToolService.invoke` 内部再拆到 `search` / `extract` 两个方法。

错误语义：未配置 / 超额 / 失败 → 工具结果 `{"success": false, "code": "TAVILY_NOT_CONFIGURED" | "TAVILY_UPSTREAM_ERROR" | "TAVILY_TIMEOUT", "message": "..."}`，不抛异常到 chat 正文。

### 6.6 `SkillDefinitionService` 新增 builtin

在 `BUILTIN_SKILLS` 数组追加：

```java
new BuiltinSkillSeed(
  "web-search",
  "Web 搜索",
  "面向公开互联网的搜索能力，返回带 URL 与相关度的结果，必要时可对命中 URL 做正文抽取。",
  /* builtin */ true,
  /* enabled */ true,
  /* promptFragment */ """
      当用户的问题涉及「最新资讯 / 外部事实 / 行业动态 / 未命中知识库的内容」时：
      1) 先用 tavily_search 取候选来源；query 控制在 400 字符内，使用搜索式短语
         - 含「最近 / 今天 / 本周 / 今年」等时间词 → 设置 time_range
         - 新闻类 → topic=news；财经数据 → topic=finance
      2) 若需要更完整的原文（例如用户问具体条款、数据），对关键 URL 调用 tavily_extract
         抓取正文（format=markdown 优先）。
      3) 回答必须附 3~5 条可点击的来源链接（标题 + URL），来源均来自 tavily_search / tavily_extract 返回。
      """,
  /* toolWhitelist */ "tavily_search,tavily_extract",
  /* kbWhitelist */ "",
  /* handoffRule */ "",
  /* outputContract */ "答案末尾必须附 3~5 条可点击来源（标题 + URL），来源均来自 tavily_search / tavily_extract。",
  /* riskLevel */ "LOW"
)
```

在 `DEFAULT_AGENT_SKILLS` 追加：

```java
Map.entry("cici-system", new DefaultBinding("web-search", "intent-route", 20, true))
```

### 6.7 `ToolDefinitionService.ensureBuiltinTools(orgId)` 懒加载

新增（或扩展）`ToolDefinitionService`：

```java
private static final List<BuiltinToolSeed> BUILTIN_TOOLS = List.of(
  new BuiltinToolSeed("tavily_search",  "Tavily 网页搜索",
      "调用 Tavily API 做面向 LLM 的网页搜索，返回命中 URL、摘要、相关度。",
      "tavily", "LOW", /*enabled*/ true),
  new BuiltinToolSeed("tavily_extract", "Tavily 正文抽取",
      "调用 Tavily API 抓取指定 URL 的正文，返回清洗后的 markdown/text。",
      "tavily", "LOW", /*enabled*/ true)
);

@Transactional
public void ensureBuiltinTools(String orgId) {
    for (var seed : BUILTIN_TOOLS) {
        toolDefinitionRepo.findByOrgIdAndToolName(orgId, seed.toolName())
          .ifPresentOrElse(
              existing -> refreshBuiltinFields(existing, seed),   // 仅覆盖非用户字段
              ()       -> toolDefinitionRepo.save(seed.toEntity(orgId))
          );
    }
}
```

**幂等规则**：
- 新建：所有字段按 seed 写入；
- 已存在：覆盖 `displayName` / `description` / `provider` / `riskLevel` / `builtin=true`；`enabled` 尊重用户修改；`updatedAt = now()`。

**调用时机**：
- `ToolController.list(...)` / `SkillController.list(...)` / `AgentController.list(...)` / `AgentController.debug(...)` 进入前先 `ensureBuiltinTools(orgId)`；
- `SkillDefinitionService.ensurePhaseOneDefaults(orgId)`：调整为 `ensureBuiltinTools(orgId)` → `ensureBuiltinSkills(orgId)` → `ensureDefaultAgentSkills(orgId)` 三步顺序，确保 `web-search.toolWhitelist` 引用的 tool 先落库；
- 新建租户钩子：在现有 org provisioning（若无则复用登录/上下文首次写入路径）中显式调用。

### 6.8 `AgentCapabilityResolverService` 行为自然适配

无需改动。只要 skill 的 `toolWhitelist` 含 `tavily_search` / `tavily_extract` 且 agent 侧未明确白名单或白名单包含它们，交集结果就会自动带上。

### 6.9 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tools` | 经 `ensureBuiltinTools` 后自动包含 `tavily_search` 与 `tavily_extract` |
| GET | `/skills` | 经 `ensureBuiltinSkills` 后自动包含 `web-search`（`builtin=true`） |
| PUT | `/skills/{skillCode}` | 内置 skill 只允许改 `enabled`（已有保护） |
| GET/PUT | `/admin/integrations/tavily` | 管理端读写 Tavily `config_json`（复用 `IntegrationAppController`） |
| POST | `/admin/integrations/tavily/test` | 用当前配置做 `tavily_search{query:"ping",max_results:1}` 体检 |

---

## 7. 前端改动

### 7.1 `AgentBuilderShell.tsx`

- `TOOL_CATALOG` 追加两条：
  ```ts
  { id: "tavily_search",  name: "Tavily 网页搜索", description: "调用 Tavily API 检索公开互联网最新信息。", level: "低风险" },
  { id: "tavily_extract", name: "Tavily 正文抽取", description: "抓取给定 URL 的清洗正文（markdown/text）。", level: "低风险" },
  ```
- 其余 UI（Skill 范围两列卡、工具白名单两列卡、添加弹窗）**无需改动**，因为这层已经统一。
- Skill 多选里会自动出现 **Web 搜索**（`GET /skills` 自带 builtin）。

### 7.2 `AdminSkillsPage.tsx`

- 无需改代码：`builtin = true` 的只读保护已存在，只会渲染「启停 + 保存」。

### 7.3 新增「集成 → Tavily」配置卡

- 复用现有 Admin Integrations 页面（如已有 `IntegrationAppEditor`），新增 `code = tavily` 的表单；
- 表单字段：
  - `apiKey`（密码输入、保存时服务端加密、读回脱敏 `tvly-****`）
  - 默认参数：`defaultSearchDepth` / `defaultMaxResults` / `defaultTopic` / `defaultIncludeAnswer` / `defaultExtractFormat` / `timeoutMs`
- 「测试连接」按钮 → 调 `POST /admin/integrations/tavily/test`，后端用当前填入的 key 做一次 `tavily_search{query:"ping",max_results:1}` 请求，命中即 `ok:true`。

### 7.4 登录/登陆/Agent Builder 顶部无感知

前端不需要硬编码 API Key 或 Tavily endpoint，全部由后端托管。

---

## 8. 安全与合规

| 项 | 方案 |
|----|------|
| API Key 存储 | 仅在 `integration_app.config_json.apiKey`，**必经 `SecretCipherService` 加密**；Admin GET 回脱敏 `tvly-****`；**禁止**在 `application.yml`、环境变量、仓库中出现明文 |
| API Key 唯一来源 | `TavilyToolService` 只从 `integration_app(orgId, "tavily")` 读取；无记录 → 结构化错误 `TAVILY_NOT_CONFIGURED`，**不**回落 env |
| 租户隔离 | `invoke(orgId, ...)` 签名强制带 `orgId`；跨租户请求无法拿到对方 key |
| 审计日志 | 记录 `orgId / userId / agentId / toolName / query 或 urls 摘要（≤ 200 字符）/ latency / resultCount / httpStatus`，**不**记录完整结果正文 |
| 速率与成本 | Phase 1 仅做 **每次调用超时 + 单会话调用次数上限**（配置 `cici.tool.tavily.per-session-limit`，默认 10）；计费看板留 Phase 2 |
| Extract 输出限制 | `tavily_extract` 单 URL 正文截断到 `cici.tool.tavily.max-extract-chars`（默认 20000），防止超大上下文注入 |
| 内容风险 | Tavily 结果可能含不良信息 URL，输出到用户时依赖现有 LLM 安全层与 `handoff_rule`；**不**新增独立黑名单 |
| GDPR / 数据出境 | 在 `docs/security-and-compliance-checklist.md` 追加一条：Tavily 为境外 API，仅对公开信息调用；`prompt_fragment` 明确禁止向其发送用户个人数据 / CRM 客户资料 |

---

## 9. 测试计划

### 9.1 后端单测

| 测试项 | 用例 |
|--------|------|
| `TavilyToolService.search` 正常返回 | Mock `TavilyClient`，断言参数归一与字段裁剪 |
| `TavilyToolService.extract` 正常返回 | Mock `TavilyClient`，断言 URL 数组、format、`maxExtractChars` 截断 |
| API Key 缺失 | 无 `integration_app('tavily')` 记录 → 返回 `TAVILY_NOT_CONFIGURED`，**不**读 env |
| 参数越界 | `max_results=999` 被钳制到 20；未知 `search_depth` 回落 `basic`；`urls.length=0` 报可读错误 |
| HTTP 超时 / 5xx | 返回 `TAVILY_UPSTREAM_ERROR` / `TAVILY_TIMEOUT`，不抛异常到上游 |
| `ToolDefinitionService.ensureBuiltinTools` | 空库 org → 写入两行；已存在（用户改名）→ 覆盖 displayName/description，保留 `enabled` |
| `AgentCapabilityResolverService` | agent 无工具白名单 + `web-search` skill → effective 包含 `tavily_search` 与 `tavily_extract` |
| `AgentCapabilityResolverService` | agent 显式排除 `tavily_search` → resolver 交集只剩 `tavily_extract`，`warnings` 提示 |

### 9.2 后端集成

- `/tools` 返回包含 `tavily_search` 与 `tavily_extract`
- 新建一个从未访问过的 org → 首次 `/tools` → 触发 `ensureBuiltinTools`，两行落库；再次调用幂等
- `/skills` 对任意 org 返回 `web-search`
- `GET /agents/cici-system` → `skillBindings` 含 `web-search`
- `POST /agents/cici-system/debug` → `effectiveToolNames` 含 `tavily_search` 与 `tavily_extract`，warnings 为空
- `POST /chat/stream`（配置好 Tavily key 的 dev env）→ 「查一下今日科技新闻」触发 `tavily_search`；「帮我读一下 https://...」触发 `tavily_extract`

### 9.3 E2E（前端）

- Agent Builder 工具白名单弹窗能看到「Tavily 网页搜索」「Tavily 正文抽取」两条，可加入、移除、tooltip 显示描述
- Agent Builder 技能范围出现 **Web 搜索**，可启停、切换 activation mode
- Admin Skill Studio 上 `web-search` 只读、可停用
- Admin 集成页：保存 Tavily apiKey → 测试连接成功 → 聊天能查到新闻；清空 apiKey → 工具调用返回可读错误、不崩溃

### 9.4 回归

- 关闭/不配置 Tavily 情况下，不影响 `cici-system` 的其它既有技能（`knowledge-first` / `general-assistant`）回答行为；`tavily_search` 调用失败时 LLM 会降级回常规回答。

---

## 10. 实施计划

### Phase 1 — 能力落地（目标 2~3 天工作量）

1. `TavilyProperties` / `TavilyClient` / `TavilyToolService` + DTO（含 search 与 extract）
2. `ToolOrchestratorService.executeTool` / `getToolDefinitions` 增加 `tavily_search` + `tavily_extract`
3. `ToolDefinitionService.ensureBuiltinTools(orgId)` + `BUILTIN_TOOLS` 数组；接入 `/tools`、`/skills`、Agent 相关 controller 进入前的兜底调用
4. 新建租户 hook 调用 `ensureBuiltinTools` + `ensureBuiltinSkills` + `ensureDefaultAgentSkills`（如无统一 provisioning，则文档注明被动触发路径）
5. `SkillDefinitionService.BUILTIN_SKILLS` 新增 `web-search`；`DEFAULT_AGENT_SKILLS` 追加 `("cici-system","web-search","intent-route",20,true)`
6. `IntegrationAppService` 注册 `code="tavily"` schema（含加密 apiKey + 默认参数）+ `POST /admin/integrations/tavily/test`
7. 前端 `AgentBuilderShell.TOOL_CATALOG` 追加两条（search / extract）；Admin 集成页加 Tavily 卡
8. 单测 + 集成测试 + `.claw/test-report.md` 记录；`docs/security-and-compliance-checklist.md` 追加 Tavily 条目

### Phase 2 — 治理增强（按需）

- `tavily-research`（多轮综合检索）作为独立 skill（带多步 tool-calling 子图）
- 调用日志表 `web_search_call_log` + Admin 用量看板
- 每用户 / 每会话更细粒度限流（目前只是配置常量）
- `tavily_search` 热查询缓存（短 TTL）以降低成本

### Phase 3 — 与 RAG 融合（可选）

- 在 `RagService` 失败或命中分 < 阈值时，由 `SkillResolverService` 自动把 `web-search` 加入 effectiveSkills
- 支持把 Tavily 返回的 top-N 文档作为临时上下文拼进 system message（带来源标注）

---

## 11. 风险与权衡

| 风险 | 对策 |
|------|------|
| 不同租户共用一条 API Key 造成计费串账 | 只支持 `integration_app` 租户级配置，**无**系统级兜底；未配置租户工具调用直接报 `TAVILY_NOT_CONFIGURED` |
| 结果质量受 query 影响很大 | `prompt_fragment` 给 LLM 明确「query 写作规范」，并对 `query.length > 400` 做前置截断 |
| 国内网络出口访问 `api.tavily.com` 不稳定 | `TavilyClient` 支持可选 `cici.tool.tavily.proxy` + 重试（指数退避 2 次） |
| LLM 选错工具（该 RAG 却去 tavily） | `activationMode = intent-route`；`prompt_fragment` 写明「站内知识优先，公开互联网补充」 |
| Extract 返回超大正文撑爆上下文 | `max-extract-chars` 截断 + `results[].rawContentLength` 保留提示 |
| 管理端泄露 apiKey | Admin GET 脱敏、update 强制密文、操作写审计 |
| 新租户缺种子导致 `web-search.tool_whitelist` 指向的 tool 行不存在 | `ensureBuiltinTools` 先于 `ensureBuiltinSkills` 调用；resolver 若发现工具缺失也输出可读 warning |

---

## 12. 验收清单

- [ ] `GET /tools` 对任意 org 返回 `tavily_search` 与 `tavily_extract`（新 org 首次访问后幂等落库）
- [ ] `GET /skills` 返回 `web-search`（`builtin=true`，`tool_whitelist="tavily_search,tavily_extract"`）
- [ ] `GET /agents/cici-system` 的 `skillBindings` 默认包含 `web-search`（`intent-route`，enabled）
- [ ] `POST /agents/cici-system/debug` 返回 `effectiveToolNames` 含 `tavily_search` 与 `tavily_extract`
- [ ] **未配置** apiKey 时调用返回 `TAVILY_NOT_CONFIGURED`，不污染会话；env `TAVILY_API_KEY` 即使存在也**不**生效
- [ ] 配置 apiKey 后：聊天中 `tavily_search` 正常返回来源；`tavily_extract` 能读取单篇 URL 正文
- [ ] Agent Builder Skill 范围 / 工具白名单均能加/减 `web-search`、`tavily_search`、`tavily_extract`
- [ ] Admin Skill Studio 对 `web-search` 进入只读保护
- [ ] 新建租户场景下 `ensureBuiltinTools` 在 `ensureBuiltinSkills` 之前执行，无 FK / 数据顺序异常
- [ ] `.claw/test-report.md` 附带 smoke 结果
- [ ] `docs/security-and-compliance-checklist.md` 新增 Tavily 条目

---

## 附：与 Tavily 官方 SKILL.md 的对应关系

| 官方 SKILL.md 要点 | 本方案对应实现 |
|---------------------|----------------|
| `name: tavily-search` / `tavily-extract` | Java 工具名 `tavily_search` / `tavily_extract`（下划线以贴合 OpenAI function naming 约定） |
| `description`（面向 Claude Skill 触发语义） | 复制到 function `description` 与 `web-search` `prompt_fragment` |
| `allowed-tools: Bash(tvly *)` | **不**执行 CLI，改为 REST API 调用 |
| `tvly login` / `TAVILY_API_KEY` | **只**走 `integration_app('tavily').apiKey`；env 不再兜底 |
| Search Options 表（`--depth` / `--max-results` / …） | 全量映射为 `tavily_search` function parameter schema |
| Extract Options 表（`--format` / `--extract-depth` / …） | 映射为 `tavily_extract` function parameter schema |
| Tips（query <= 400 字符 / 拆分子查询 / 用 time-range） | 写进 `web-search` 的 `prompt_fragment` |
| `tavily-research` | 列入 Phase 2 roadmap |
