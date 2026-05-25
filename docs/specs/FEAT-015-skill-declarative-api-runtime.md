---
kind: feature-spec
feature_id: FEAT-015
title: Skill Declarative API Runtime
status: in_implementation
owner_role: backend-agent
task_ids: TASK-036
related_decisions: FEAT-009
related_issues: none
updated_at: 2026-05-05T00:50:29Z
updated_by: ai
---

# FEAT-015 - Skill 内嵌声明式 API 运行时

## 背景与目标

当前系统已经支持 Skill 白名单工具、MCP 工具发现、内置工具分发和运行时 function calling。这个模式适合可复用工具池，但对某些业务 Skill 来说过重：

- 管理员已经知道某个 Skill 应调用哪个业务 API，不希望模型再从 MCP 工具池里检索和判断。
- 远程 API 的地址、方法、参数映射、鉴权和返回路径应由 Skill 配置确定，而不是让模型在提示词中理解和执行。
- 模型应该只负责从用户上下文中抽取业务参数，不应看到或编造 URL、Header、Method、Token。
- 运行时只注入当前激活 Skill 的专属 API 工具，减少上下文长度、降低工具选择噪音、提升执行效率。

本功能目标是新增一套 **Skill 内嵌声明式 API 能力**：

- Skill 编辑/发布时允许声明远程 API 契约。
- 发布时把 API 契约编译为 Skill 专属 function schema 和后端执行计划。
- 运行时只在对应 Skill 激活时注入这些专属工具。
- 工具执行由后端确定性完成，模型只提交参数。
- 该能力对最终用户不可见，不进入普通工具白名单，但必须接受平台治理、权限控制和审计。

## 范围

### In Scope

- Skill 规格中新增 `runtimeApis` 声明结构，用于定义 API 动作。
- 发布时校验并编译 `runtimeApis`：
  - 生成模型可见的 function schema。
  - 生成模型不可见的 execution plan。
  - 绑定到 Skill 发布版本快照。
- 运行时解析当前激活 Skill 的 API 工具，并注入到模型工具列表。
- 新增 `SkillApiToolService`，负责 API 契约编译、运行时工具定义生成和远程 API 执行。
- 支持常见 HTTP 调用：
  - `GET` / `POST` / `PUT` / `PATCH` / `DELETE`。
  - JSON body、query params、path params、固定 header 模板。
  - 结果路径提取、响应裁剪和脱敏。
- 支持服务端鉴权引用 `authRef`，不允许在 Skill 明文保存密钥。
- 支持平台级安全控制：
  - URL host 白名单。
  - 禁止内网、localhost、metadata IP 和非 HTTP(S) 协议。
  - 参数 JSON Schema 校验。
  - 超时、响应大小、调用次数限制。
  - 高风险动作二次确认策略。
- 支持管理端编辑、预览、发布校验和错误提示。
- 支持调用审计，记录 Skill、API 动作、用户、状态码、耗时、参数摘要和错误。

### Out Of Scope

- 不替代现有 MCP 工具体系。
- 不把 `runtimeApis` 混入普通 `toolWhitelist` UI。
- 不允许模型传入任意 URL、Method、Header 或 Token。
- 不支持任意脚本执行、JavaScript transform、动态代码模板。
- 第一版不做复杂 OAuth 授权流程配置，只引用系统已有或后续统一接入的 `authRef`。
- 第一版不支持 multipart 文件上传和流式 API。
- 第一版不做跨 Skill 共享 API 动作；每个 API 动作默认归属单个 Skill。

## 用户场景

- 管理员创建“潜客查询 Skill”，直接在 Skill 中声明潜客查询 API。用户问“查一下张三这个客户”，模型只看到 `skillapi__lead_lookup__query_leads`，填入 `keyword=张三`，后端直接调用固定 API。
- 管理员创建“审批处理 Skill”，声明待审批查询 API 和审批提交 API。查询 API 可自动执行；提交审批属于高风险动作，需要用户确认后再调用。
- 管理员在 Skill 编辑页修改 API URL 或参数映射，发布时系统检查 host 是否允许、schema 是否有效、鉴权引用是否存在；失败时阻止发布。
- 平台治理员查看某个 Skill 内嵌了哪些远程接口、调用频次和失败率，但最终用户不会看到“工具白名单”或 API 地址。

## 现状与约束

### Verified Facts

- `ToolOrchestratorService` 当前负责把内置工具、邮件工具、Tavily 工具和 MCP 工具转换为模型 function schema，并在 `executeTool(...)` 中分发执行。
- `SkillResolverService` 当前负责解析当前 Agent/Session 的 Skill 绑定、激活模式、`allowedToolNames`、`skillDeclaredToolNames` 和 Skill prompt。
- `SkillDefinitionEntity` 当前已包含 `promptFragment`、`draftSpecText`、`toolWhitelist`、`kbWhitelist`、`handoffRule`、`outputContract`、`riskLevel` 等字段。
- FEAT-014 已引入 Skill 草稿/发布版本状态机，后续运行时应优先绑定发布版本快照，避免草稿变更影响线上 Agent。
- 当前 `toolWhitelist` 语义是“Skill 允许使用哪些已有工具”，不适合承载 Skill 私有 API 契约。

### Key Constraint

`runtimeApis` 必须和普通工具白名单分层：

| 能力类型 | 来源 | 用户可见 | 模型可见 | 管理员可见 | 平台治理 |
|---|---|---|---|---|---|
| 普通工具白名单 | 工具池 / MCP / 内置工具 | 不直接可见 | 工具名、描述、参数 | 可选择 | 可治理 |
| Skill 内嵌 API | Skill 自带 API 契约 | 不可见 | 抽象 function schema | 可配置 | 必须治理 |

模型只能看到抽象工具名、描述和参数 schema，不能看到真实 URL、Header、Token、Method。

## 方案设计

### 1. Skill API 声明结构

在 Skill 规格中新增 `runtimeApis`，建议作为结构化 JSON 存储，而不是只放在 `draftSpecText` 自然语言中。

示例：

```yaml
runtimeApis:
  - apiCode: query_leads
    displayName: 查询潜在客户
    description: 当用户询问潜在客户、线索、客户列表时调用。
    riskLevel: LOW
    triggerMode: model_decide
    method: POST
    url: https://crm.example.com/api/leads/search
    authRef: crm_user_token
    timeoutSeconds: 10
    inputSchema:
      type: object
      properties:
        keyword:
          type: string
          description: 客户名称、手机号、公司名或搜索关键词
        pageSize:
          type: integer
          description: 返回数量
          default: 10
      required:
        - keyword
    request:
      query: {}
      headers:
        Content-Type: application/json
      body:
        q: "{{keyword}}"
        limit: "{{pageSize}}"
    response:
      resultPath: "$.data.records"
      maxItems: 20
      maxBytes: 12000
      redactPaths:
        - "$..password"
        - "$..token"
```

字段说明：

| 字段 | 说明 |
|---|---|
| `apiCode` | Skill 内唯一 API 编码，只允许小写字母、数字、下划线 |
| `displayName` | 管理端显示名称 |
| `description` | 给模型看的调用时机描述 |
| `riskLevel` | `LOW` / `MEDIUM` / `HIGH` |
| `triggerMode` | 第一版支持 `model_decide`；后续可扩展 `auto_before_answer` |
| `method` | HTTP 方法 |
| `url` | 固定 URL，发布时校验 host 和协议 |
| `authRef` | 服务端鉴权引用，不是密钥值 |
| `inputSchema` | 模型可填写参数的 JSON Schema |
| `request` | 参数到 query/header/body 的模板映射 |
| `response` | 响应提取、裁剪、脱敏配置 |

### 2. 发布期编译

发布 Skill 时，后端读取 `runtimeApis`，对每个 API 动作生成两份产物。

#### 模型可见 function schema

```json
{
  "type": "function",
  "function": {
    "name": "skillapi__lead_lookup__query_leads",
    "description": "当用户询问潜在客户、线索、客户列表时调用。",
    "parameters": {
      "type": "object",
      "properties": {
        "keyword": {
          "type": "string",
          "description": "客户名称、手机号、公司名或搜索关键词"
        },
        "pageSize": {
          "type": "integer",
          "description": "返回数量",
          "default": 10
        }
      },
      "required": ["keyword"]
    }
  }
}
```

#### 后端执行计划

```json
{
  "toolName": "skillapi__lead_lookup__query_leads",
  "skillCode": "lead_lookup",
  "apiCode": "query_leads",
  "method": "POST",
  "url": "https://crm.example.com/api/leads/search",
  "authRef": "crm_user_token",
  "timeoutSeconds": 10,
  "requestTemplate": {
    "query": {},
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "q": "{{keyword}}",
      "limit": "{{pageSize}}"
    }
  },
  "responseMapping": {
    "resultPath": "$.data.records",
    "maxItems": 20,
    "maxBytes": 12000,
    "redactPaths": ["$..password", "$..token"]
  }
}
```

编译校验包括：

- `apiCode` 唯一且合法。
- `toolName` 由系统生成，不接受管理员手写覆盖。
- `inputSchema` 必须是 object schema，字段类型受限。
- `url` 必须是 HTTPS 或允许的 HTTP 测试环境地址。
- host 必须命中平台允许列表。
- 禁止内网地址、回环地址、链路本地地址、metadata 地址。
- `authRef` 必须存在且当前组织可用。
- `request` 模板只能引用 `inputSchema` 中声明的字段。
- `riskLevel=HIGH` 的动作必须配置确认策略。

### 3. 运行期注入

运行时不从 MCP 工具池检索这些 API。流程为：

```text
用户提问
  ↓
SkillResolverService 解析当前 Agent 和当前激活 Skill
  ↓
读取该 Skill 发布版本绑定的 skill_api_tool
  ↓
ToolOrchestratorService.getToolDefinitions(...) 注入专属 function schema
  ↓
模型只看到当前 Skill 的 API 工具
  ↓
模型提交参数
  ↓
ToolOrchestratorService.executeTool(...) 识别 skillapi__ 前缀
  ↓
SkillApiToolService.dispatch(...) 按 execution plan 调用远程 API
```

当前 Skill 未激活时，不注入其 API 工具。这样模型无法跨 Skill 调用隐藏 API。

### 4. 执行期调用

`SkillApiToolService.dispatch(...)` 的执行步骤：

1. 解析 `toolName`，确认命名空间为 `skillapi__`。
2. 根据当前 `ResolvedSkillContext` 校验该工具属于当前激活 Skill 的发布版本。
3. 使用 `inputSchema` 校验模型传入的 `argumentsJson`。
4. 根据 execution plan 渲染 path/query/header/body。
5. 通过 `authRef` 从服务端凭证管理中注入鉴权 header 或 token。
6. 检查 URL 和最终请求仍满足安全策略。
7. 发送 HTTP 请求，执行超时、响应大小和状态码处理。
8. 按 `response.resultPath` 提取结果。
9. 脱敏并裁剪结果，返回给模型。
10. 写入审计日志。

模型永远不能传入或覆盖：

- `url`
- `method`
- `headers`
- `authRef`
- `timeoutSeconds`
- `responseMapping`

### 5. Trigger Mode

第一版只要求支持：

| 模式 | 说明 |
|---|---|
| `model_decide` | 工具以 function schema 注入，模型决定是否调用并填参数 |

后续可扩展：

| 模式 | 说明 |
|---|---|
| `auto_before_answer` | Skill 激活后，系统先用轻量参数抽取或确定性映射调用 API，再把结果作为上下文给模型 |
| `manual_confirm` | 高风险动作，模型准备调用后必须等待用户确认 |

本次 P0 优先实现 `model_decide`，因为它改动小、复用当前 tool-calling loop，同时已经能避免 URL 幻觉和工具池筛选。

## 接口与数据影响

### 数据模型建议

新增迁移 `V36__skill_declarative_api_runtime.sql`。

建议新增表：

```sql
CREATE TABLE skill_api_tool (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_version_id BIGINT,
    skill_code VARCHAR(64) NOT NULL,
    api_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL DEFAULT 'model_decide',
    input_schema_json TEXT NOT NULL,
    execution_plan_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_skill_api_tool_version_api
    ON skill_api_tool(org_id, skill_version_id, api_code);

CREATE UNIQUE INDEX ux_skill_api_tool_tool_name
    ON skill_api_tool(org_id, tool_name);
```

可选扩展：

- `skill_definition.runtime_api_draft_json`：保存草稿态 API 声明。
- `skill_version.runtime_api_snapshot_json`：保存发布版本 API 声明快照。
- `platform_policy_bundle.allowed_api_hosts_json`：平台允许 host 策略。

### 后端服务

新增：

- `SkillApiToolService`
  - `compileDraftApis(...)`
  - `publishApisForVersion(...)`
  - `getRuntimeToolDefinitions(...)`
  - `dispatch(...)`
- `SkillApiContractValidator`
- `SkillApiHttpExecutor`
- `SkillApiAuditService`

改造：

- `SkillDefinitionEntity` / `SkillVersionEntity` 增加 API 契约草稿和发布快照字段，或引入独立表。
- `SkillResolverService.ResolvedSkillContext` 增加 `List<ResolvedSkillApiTool> skillApiTools`。
- `ToolOrchestratorService.getToolDefinitions(...)` 追加当前上下文的 Skill API function schema。
- `ToolOrchestratorService.executeTool(...)` 对 `skillapi__` 命名空间走 `SkillApiToolService.dispatch(...)`。
- `SkillDefinitionService.publish(...)` 在发布时编译 API 契约并绑定版本。

### 管理端接口

Skill 保存/发布接口需要支持 `runtimeApis`：

```json
{
  "skillCode": "lead_lookup",
  "name": "潜客查询",
  "promptFragment": "...",
  "runtimeApis": [
    {
      "apiCode": "query_leads",
      "displayName": "查询潜在客户",
      "description": "当用户询问潜在客户、线索、客户列表时调用。",
      "method": "POST",
      "url": "https://crm.example.com/api/leads/search",
      "authRef": "crm_user_token",
      "inputSchema": {}
    }
  ]
}
```

预览编译接口应返回：

- API 契约校验错误。
- 将生成的工具名。
- 模型可见参数 schema。
- 安全策略告警。
- 高风险动作确认要求。

### 前端影响

管理端 Skill 新建/编辑页新增“内嵌 API”页签或在“边界规则”下新增 API 动作区。

UI 原则：

- 不放入“工具白名单”选择器。
- API 动作以 Skill 私有能力展示。
- URL、Method、AuthRef、参数、请求映射、返回映射分区编辑。
- 发布前显示编译校验结果。
- 高风险 API 显示明确风险标记。
- 遵守 `鎏金账房` product register，使用紧凑表单和统一模式窗口。

## 任务拆分

- `TASK-036`: Skill declarative API runtime（P0，backend-agent-runtime）

建议实施子任务：

- `TASK-036A`: 数据模型和 Skill API 契约 schema。
- `TASK-036B`: 发布期编译和校验。
- `TASK-036C`: 运行时工具注入和 `skillapi__` 分发。
- `TASK-036D`: HTTP 执行器、鉴权引用、安全策略和审计。
- `TASK-036E`: 管理端 Skill 编辑页“内嵌 API”配置 UI。
- `TASK-036F`: 集成测试、SSRF 测试、发布版本 pin 测试和真实 API smoke。

## 验收标准

- 管理员可以在 Skill 草稿中配置至少一个 `runtimeApi`，保存草稿不影响运行时。
- 发布 Skill 时，系统校验 API 契约并生成发布版本专属 API 工具。
- 当前 Skill 激活时，模型工具列表包含该 Skill 的 `skillapi__{skillCode}__{apiCode}`。
- 当前 Skill 未激活时，模型工具列表不包含该 Skill 的 API 工具。
- 模型只能传入 `inputSchema` 中声明的参数，不能传 URL、Header、Method 或 Token。
- 后端根据 execution plan 调用固定远程 API 并返回裁剪后的结果。
- 普通 `toolWhitelist` UI 不显示这些内嵌 API 动作。
- 平台审计能查到每次内嵌 API 调用的 Skill、API、用户、结果状态、耗时和错误摘要。
- SSRF 防护测试覆盖 localhost、127.0.0.1、::1、169.254.169.254、内网网段、非 HTTP(S) 协议。
- 高风险 API 在未确认时不得执行。

建议验证命令：

```bash
cd backend
mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillApiToolServiceTest,SkillDeclarativeApiRuntimeIntegrationTest test
```

```bash
cd frontend
npm run build
```

## 风险与回滚

### 风险

- SSRF 和内网探测风险。
- 管理员误配置 API 导致敏感数据暴露。
- 模型参数抽取错误导致错误查询或错误写入。
- 高风险动作未确认直接执行。
- API 响应过大拖慢模型上下文或泄露敏感字段。

### 缓解

- 默认只允许平台配置的 host。
- 默认禁止内网、localhost、metadata IP。
- 默认响应裁剪和脱敏。
- `riskLevel=HIGH` 强制二次确认。
- 所有 API 调用写审计。
- 发布版本绑定执行计划，草稿变更不影响线上。

### 回滚

- 平台开关关闭 `skill_api_runtime` 后，不再注入 `skillapi__` 工具。
- 已发布 Skill 的 API 工具可标记 `enabled=false`，保留配置和审计。
- 如果发布编译失败，不更新当前发布版本。

## 实现进展

- 当前状态：后端最小闭环和管理端配置 UI 已完成第一轮，`authRef=integration:tavily.apiKey` 与 `authRef=integration:cloudcc.accessToken` 已接入服务端凭证解析，并通过本地 HTTP / CloudCC mock smoke。
- 已完成项：
  - 明确 `runtimeApis` 与普通 `toolWhitelist` 分层。
  - 明确发布期 function schema / execution plan 双产物。
  - 明确运行时只注入当前激活 Skill 的专属 API 工具。
  - 明确安全、鉴权、审计和 P0 验收边界。
  - 新增 Flyway `V37__skill_declarative_api_runtime.sql`：`skill_definition.runtime_api_draft_json`、`skill_version.runtime_api_snapshot_json` 与 `skill_api_tool` 发布计划表。
  - 后端 Skill 创建、更新、预览和发布接口已支持 `runtimeApis` 字段；预览会返回 `runtimeApiPreview`，发布阻断校验错误。
  - `SkillApiToolService` 已实现 API 契约编译、function schema 生成、execution plan 保存、参数 schema 校验、URL/host 安全校验、模板渲染、HTTP 执行、响应路径提取、裁剪、脱敏和调用审计。
  - `SkillResolverService` / `ToolOrchestratorService` / `ChatOrchestratorService` 已接入运行时注入：只在对应 Skill ambient 或当前 activeSkill 生效时暴露 `skillapi__{skillCode}__{apiCode}` 工具；非激活上下文显式拒绝执行。
  - 回归覆盖 `runtimeApis` 预览、localhost/127.0.0.1 阻断、发布后 `skill_api_tool` 生成、手动激活 Skill 才注入工具、非激活上下文拒绝执行。
  - 管理端 Skill 新建/编辑页已新增“内嵌 API”页签；API 动作仍与普通工具白名单分层，支持编辑 URL、Method、AuthRef、参数 schema、请求映射、返回映射和确认要求，并在保存/预览时提交结构化 `runtimeApis`。
  - `SkillApiToolService` 已支持第一种服务端凭证引用：`authRef=integration:tavily.apiKey` 从现有 Tavily 集成配置读取并解密 API key，运行时注入 `Authorization: Bearer ...`，不把密钥暴露给模型或前端。
  - 回归覆盖带 authRef 的 Skill API 发布、激活上下文执行、本地 HTTP endpoint 调用、Authorization header 注入和 `$..token` 响应脱敏。
  - `SkillApiToolService` 已支持 CloudCC 用户态凭证引用：`integration:cloudcc.accessToken`、`cloudcc.accessToken`、`integration:cloudcc.userToken`、`cloudcc.userToken` 发布期只校验 CloudCC CRM 集成启用，运行期按当前用户通过 `CloudccAccessTokenService` 换取 session token，并注入 `accessToken` header。
  - 回归新增本地 CloudCC mock smoke：mock domain 解析、token 换取与业务 API endpoint，验证 Skill API runtime 使用当前用户绑定凭证、业务 API 收到 `accessToken` header，响应中的 `$..accessToken` 被脱敏。
  - 管理端 authRef 提示已同步 `integration:tavily.apiKey` 和 `integration:cloudcc.accessToken` 两类示例。
- 未完成项：
  - 真实外部 API smoke 尚未执行；当前 smoke 使用本地 HTTP endpoint。
  - 其他集成应用和 header 形态需要继续扩展；真实 CloudCC smoke 仍依赖 TASK-023 先修复用户绑定凭证与本地模型 key。

## 交接说明

下一位接手者先读：

- `backend/src/main/java/com/codehouse/ciciassistant/ai/service/ToolOrchestratorService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/service/SkillResolverService.java`
- `backend/src/main/java/com/codehouse/ciciassistant/skill/domain/SkillDefinitionEntity.java`
- `docs/specs/FEAT-014-skill-versioning-import-export.md`

实现时先走后端最小闭环：

1. 数据模型保存发布版本 API 执行计划。
2. `SkillApiToolService` 能用固定契约调用一个测试 HTTP endpoint。
3. 当前激活 Skill 注入 `skillapi__` function schema。
4. 模型调用后能返回 API 结果。

前端 UI 可以第二阶段跟进，但后端必须先保证不需要模型理解 URL 就能确定性执行。
