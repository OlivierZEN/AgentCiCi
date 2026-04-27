# 自然语言工作流描述 + AI 生成流程代码 产品框架设计

更新时间：2026-04-16  
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文给出一套适配当前项目的 Agent 构建产品框架设计，核心方向不是“图形化拖拽节点”，而是：

**自然语言描述业务流程 -> AI 编译为可执行工作流代码 + 只读流程图预览 -> 服务端托管执行与治理**

这个设计服务于当前项目正在建设的“无代码 Agent 构建”能力，并与现有架构保持一致：

- 前端：React + Vite 助手工作台
- 后端：Spring Boot 3 单体编排服务
- 现有能力：多模型、RAG、Tools / MCP、组织隔离、审计与权限

## 2. 核心判断

### 2.1 我们不把什么作为主入口

不把下面两类形态作为未来主入口：

- 人工拖拽式流程图
- 用户直接维护节点 JSON / 条件配置表

原因：

1. AI 难以替用户完成图形化操作，协同效率低。
2. 图形化配置本身是高维护成本的中间表达，不是最佳 source of truth。
3. 对复杂业务流程来说，图形节点一旦增多，理解和维护成本迅速上升。
4. 在 AI Coding 时代，文本描述更接近业务语言，也更适合作为 AI 的编译输入。

### 2.2 我们把什么作为主入口

我们选择：

**自然语言 Spec 作为用户主输入**

例如：

> 你是售前跟进 Agent。  
> 用户提问后先判断是产品问答、报价请求还是实施咨询。  
> 如果是产品问答，先检索知识库；若命中率低于 0.7，则追问或转人工。  
> 如果是报价请求，先查询客户级别，再调用报价工具生成标准报价说明。  
> 最终输出结论、依据和下一步建议。

系统再把这段描述自动编译为：

- 可执行工作流代码
- 与代码同源的只读流程图预览
- 工具依赖清单
- 权限需求清单
- 风险标签
- 版本与发布元数据

### 2.3 总体产品原则

1. **文本优先**
   用户只描述“要做什么”，不要求手工搭流程图。
2. **代码落地**
   AI 产出的是可执行流程代码，而不是仅供展示的 JSON。
3. **托管执行**
   服务端统一接管执行、权限、审计、回滚与发布。
4. **结构化治理**
   虽然主输入是文本、主执行体是代码，但系统内部必须沉淀结构化元数据。
5. **图是产物，不是源头**
   若要可视化，应由系统根据代码和 manifest 自动生成流程视图，而不是靠人工维护图。
6. **代码与流程图同源**
   流程图只是对编译结果的图形化投影，不能脱离代码单独编辑和保存。

## 3. 产品定位

该能力不是“聊天提示词编辑器”，而是一个：

**面向业务人员与运营人员的 Agent 规格编辑器（Agent Spec Editor）**

用户通过文本配置：

- Agent 的业务角色
- 适用场景
- 流程目标
- 工具权限边界
- 知识库边界
- 人工兜底规则

系统通过 AI 编译生成：

- workflow source code
- workflow manifest
- workflow preview graph
- 运行策略

## 4. 产品形态

## 4.1 前端主工作台

在当前助手工作台中保留“会话 / Agent 构建”双入口。

`Agent 构建` 不再强调“拖节点”，而应逐步演进为以下布局：

1. **Agent 基本信息区**
   - 名称
   - 场景说明
   - 发布渠道
   - 默认模型
2. **自然语言流程描述区**
   - 业务目标描述
   - 流程描述
   - 异常与转人工规则
3. **资源授权区**
   - 可使用知识库
   - 可使用工具 / MCP
   - 风险等级
4. **编译结果区**
   - 生成的流程代码
   - 只读流程图预览
   - 生成摘要
   - 风险与依赖提示
5. **版本 / 发布区**
   - 草稿版本
   - 测试版本
   - 发布版本

## 4.2 核心交互流程

### Step 1：用户编写 Agent Spec

用户填写或编辑：

- `角色描述`
- `目标描述`
- `流程自然语言描述`
- `可访问知识库`
- `允许调用工具`
- `人工兜底规则`

### Step 2：AI 编译

用户点击“生成流程代码”后，系统执行：

1. 对 Spec 做结构化抽取
2. 生成 workflow code
3. 生成 workflow manifest
4. 生成 workflow preview graph
5. 进行静态检查
6. 给出风险提示与可读摘要

### Step 3：预览 / 调试

用户可以：

- 查看流程代码
- 查看流程图预览
- 查看系统推断的执行步骤
- 用测试输入运行一次
- 观察每一步的执行结果

### Step 4：发布

发布时并不是直接把一段自由代码上线，而是生成一个：

- 已签名版本
- 已校验依赖
- 已绑定权限边界
- 可回滚的 workflow version

## 5. 系统架构

## 5.1 逻辑分层

建议新增五层核心能力：

1. **Spec Layer**
   存储用户写的自然语言规格说明。
2. **Compiler Layer**
   用 AI 把 Spec 编译为代码与元数据。
3. **Runtime Layer**
   负责托管执行 workflow code。
4. **Governance Layer**
   负责权限、安全、审计、版本与发布。
5. **Observation Layer**
   负责调试、日志、执行轨迹和自动生成的流程图预览。

## 5.2 执行闭环

完整闭环如下：

1. 用户编辑 Agent Spec
2. 前端提交到后端 `compile`
3. 编译服务调用大模型生成 workflow code
4. 编译服务解析并产出 manifest
5. 编译服务生成只读流程图预览
6. 服务端做静态校验
7. 用户调试运行
8. 通过后发布成某个版本
9. 线上请求按版本加载并执行

## 6. 数据模型设计

这里不建议把“节点图”当作主配置对象，而建议使用三个核心对象。

## 6.1 AgentDefinition

表示一个 Agent 产品对象。

建议字段：

- `id`
- `orgId`
- `name`
- `description`
- `status`
- `defaultModel`
- `publishedVersionId`
- `createdBy`
- `createdAt`
- `updatedAt`

## 6.2 AgentSpec

表示用户维护的自然语言规格。

建议字段：

- `id`
- `agentId`
- `rolePrompt`
- `goalDescription`
- `workflowDescription`
- `handoffRules`
- `knowledgeScope`
- `toolScope`
- `safetyLevel`
- `sourceText`
- `versionLabel`
- `createdAt`

说明：

- `sourceText` 是编译输入的主 source of truth。
- 其他字段用于前端表单拆分和结构化辅助，不替代 `sourceText`。

## 6.3 AgentWorkflowVersion

表示 AI 编译后的可运行版本。

建议字段：

- `id`
- `agentId`
- `specId`
- `versionNo`
- `runtimeLang`
- `workflowCode`
- `workflowManifest`
- `workflowPreview`
- `compileSummary`
- `compileStatus`
- `publishStatus`
- `createdAt`
- `createdBy`

其中：

- `workflowCode`：AI 生成的流程代码
- `workflowManifest`：系统提取的结构化元数据
- `workflowPreview`：系统根据代码 / manifest 生成的只读流程图预览缓存

## 6.4 WorkflowManifest 结构建议

`workflowManifest` 不是用户维护的内容，而是系统自动生成的治理元数据。

建议字段：

```json
{
  "entry": "runAgent",
  "dependencies": {
    "tools": ["rag-search", "crm-customer", "quote-generator"],
    "knowledgeBases": [12, 15],
    "models": ["gpt-4.1"]
  },
  "policies": {
    "allowWriteTools": false,
    "requireHumanHandoffOnLowConfidence": true,
    "maxToolCalls": 3
  },
  "riskLevel": "medium",
  "estimatedSteps": [
    "intent_detect",
    "kb_search",
    "crm_lookup",
    "llm_generate"
  ]
}
```

## 6.5 WorkflowPreview 结构建议

`workflowPreview` 不是独立的主配置，而是编译产物里的只读展示对象。

建议字段：

```json
{
  "format": "mermaid",
  "diagramDsl": "flowchart TD\n  A[接收输入] --> B{识别意图}\n  B -->|产品问答| C[知识检索]\n  B -->|报价请求| D[客户查询]\n  C --> E[生成回复]\n  D --> F[生成报价说明]\n  E --> G[输出结果]\n  F --> G",
  "nodes": [
    { "id": "A", "type": "start", "label": "接收输入" },
    { "id": "B", "type": "decision", "label": "识别意图" }
  ],
  "edges": [
    { "from": "A", "to": "B" }
  ],
  "sourceVersion": "workflow-code-hash"
}
```

说明：

- `diagramDsl` 可优先使用 Mermaid，便于快速渲染和落地。
- `nodes / edges` 便于后续接更强的只读图形组件，但不是人工编辑入口。
- `sourceVersion` 用于标识流程图和代码是否同源。

## 7. 编译机制设计

## 7.1 编译输入

编译器接收：

- Agent 基本信息
- 自然语言 Spec
- 授权范围（KB / Tools）
- 默认模型
- 运行模式

## 7.2 编译输出

编译器至少产出：

1. `workflowCode`
2. `workflowManifest`
3. `workflowPreview`
4. `compileSummary`
5. `warnings`

其中：

- `workflowCode` 是运行时执行体
- `workflowManifest` 是治理元数据
- `workflowPreview` 是只读图形化预览

## 7.3 编译提示词原则

给大模型的编译任务，不是“请随便写一段代码”，而是：

1. 必须使用平台提供的运行时 SDK
2. 不允许直接访问未授权资源
3. 所有外部调用必须经过平台暴露的 runtime context
4. 入口函数签名固定
5. 输出必须符合既定模板

例如生成目标不应是任意 TypeScript，而应是：

```ts
export async function runAgent(ctx: WorkflowContext): Promise<WorkflowResult> {
  // platform-managed code only
}
```

对应的流程图生成也不应让模型自由发挥成“随便一张图”，而应要求：

1. 节点必须映射到代码中的主要阶段
2. 分支必须映射到明确条件
3. 节点顺序必须与 manifest 中的步骤一致
4. 输出必须符合平台支持的只读图形格式

## 7.4 为什么必须限定运行时 SDK

如果完全放开代码生成，后果是：

- 权限边界不清
- 调试困难
- 风险过高
- 难以回放和治理

因此建议采用：

**受限 DSL / 受限 SDK 代码生成**

不是让模型生成任意系统级代码，而是生成在平台沙箱内可运行的工作流代码。

## 8. 运行时设计

## 8.1 统一执行器

虽然我们不认同“手工节点图编排”，但仍然需要一个统一执行器。

这个执行器不是执行 JSON 节点，而是执行：

**AI 生成且经过校验的 workflow code**

统一执行器负责：

- 加载 workflow version
- 构造 runtime context
- 注入工具白名单
- 注入知识库白名单
- 执行流程代码
- 捕获异常
- 记录审计日志

说明：

- 执行器真正运行的是 `workflowCode`
- `workflowPreview` 只用于预览、调试和说明，不参与执行

## 8.2 RuntimeContext 建议

运行时建议向代码暴露一个受控上下文：

```ts
type WorkflowContext = {
  input: string;
  orgId: string;
  userId: string;
  sessionId: string;
  model: ModelInvoker;
  tools: ToolInvoker;
  knowledge: KnowledgeRetriever;
  handoff: HandoffService;
  logger: WorkflowLogger;
};
```

这样 workflow code 可以完成“智能推理 + 工具调用”，但始终在平台边界内运行。

## 8.3 推荐执行模式

最合理的模式是：

**程序骨架执行 + LLM 节点参与**

也就是：

- 流程顺序由代码控制
- 智能理解与生成由模型完成

例如：

1. 先做意图识别
2. 再执行知识检索
3. 再决定是否调用工具
4. 最后生成输出

## 9. 治理与安全设计

## 9.1 权限边界

发布前必须校验：

- 使用的工具是否在授权白名单内
- 使用的知识库是否在授权范围内
- 是否使用了高风险动作
- 是否涉及写操作

## 9.2 审计

每次运行应记录：

- 使用哪个 workflow version
- 输入是什么
- 调用了哪些工具
- 检索了哪些知识库
- 是否触发人工兜底
- 执行是否成功

## 9.3 发布策略

建议支持：

- 草稿
- 测试
- 已发布
- 已回滚

同时要支持：

- 灰度发布
- 指定渠道发布
- 指定组织角色发布

## 9.4 沙箱策略

workflow code 不应直接拥有系统执行能力。

推荐策略：

1. 只允许调用平台 runtime SDK
2. 不允许自由文件系统访问
3. 不允许自由网络访问
4. 不允许访问未授权 Java 服务 Bean
5. 超时、内存、步数受限

## 10. 对当前项目的落地建议

## 10.1 前端演进路径

当前已完成第一阶段框架，下一阶段建议把 `Agent 构建` 页面重心从“字段分区”逐步切向“Spec + 编译”模式。

建议前端新增三个主要区域：

1. **Spec Editor**
   用户写自然语言工作流描述。
2. **Compile Output**
   展示生成代码、只读流程图、摘要、依赖和风险提示。
3. **Run Debug Panel**
   输入测试样例，查看执行轨迹。

界面建议：

- 流程图预览默认放在代码面板旁边，形成“代码 / 图双栏预览”
- 初期可直接渲染 Mermaid
- 后续若需要更强交互，可升级成只读节点图，但仍不提供拖拽编辑

## 10.2 后端新增模块建议

在 `backend/` 中新增：

- `agent/domain`
  - `AgentDefinitionEntity`
  - `AgentSpecEntity`
  - `AgentWorkflowVersionEntity`
- `agent/api`
  - `AgentController`
  - `AgentCompileController`
  - `AgentPublishController`
- `agent/service`
  - `AgentCompilerService`
  - `WorkflowManifestService`
  - `WorkflowRuntimeService`

## 10.3 API 建议

建议新增接口：

- `POST /agents`
  创建 Agent
- `GET /agents`
  查询 Agent 列表
- `GET /agents/{id}`
  查询 Agent 详情
- `POST /agents/{id}/specs`
  保存自然语言 Spec
- `POST /agents/{id}/compile`
  编译生成 workflow code + preview graph
- `POST /agents/{id}/debug`
  使用测试输入进行调试运行
- `POST /agents/{id}/publish`
  发布指定 workflow version
- `GET /agents/{id}/versions`
  查询版本历史

## 10.4 编译生成语言建议

结合当前项目技术栈，推荐优先选择：

### 方案 A：生成受限 TypeScript

优点：

- 前端与后端思维统一
- 更适合用 AI 生成
- 后续若引入 JS 沙箱，扩展性较好

缺点：

- 当前后端是 Java，需要额外的脚本执行或沙箱机制

### 方案 B：生成 Java 风格 DSL / JSON IR，再由 Java Runtime 执行

优点：

- 更容易和 Spring Boot 现有体系融合
- 安全可控

缺点：

- 更像“编译成中间表示”，不是纯粹“代码直接执行”

### 当前项目建议

短期建议：

**先生成“受限 workflow DSL + manifest + 可读伪代码”**

中期建议：

**再演进到“受限 TypeScript / JS workflow code + 沙箱执行”**

原因：

- 当前项目已有 Java Orchestrator、RAG、Tool、MCP 边界，先让编译、版本、治理闭环跑通更重要。
- 一上来就做完全自由代码执行，风险和实现复杂度都偏高。

## 11. 与图形化方案的关系

我们不把图形化作为主编辑方式，但图形化仍然有价值。

正确定位是：

**图形化 = 自动生成的观察视图 / 调试视图**

而不是：

**图形化 = 用户主配置入口**

因此如果后续要展示流程图，应由系统根据 `workflowCode`、`workflowManifest` 和执行轨迹自动渲染。

更进一步，建议在“编译完成”这一刻就同步产出流程图预览：

- 编译结果页展示“静态流程图预览”
- 调试运行页叠加“执行轨迹高亮”
- 两者都不是人工维护的图形配置

## 12. 分阶段实施建议

### Phase 1：Spec 驱动框架

- 前端提供文本 Spec 编辑区
- 后端保存 Agent / Spec
- 可展示编译结果占位

### Phase 2：AI 编译闭环

- 调用大模型生成 workflow code
- 生成 manifest
- 生成只读流程图预览
- 支持编译结果回显

### Phase 3：调试运行

- 输入测试样例
- 执行 workflow version
- 查看步骤日志和工具调用
- 在流程图上叠加当前执行路径

### Phase 4：发布治理

- 版本管理
- 灰度发布
- 回滚
- 审计

### Phase 5：执行态可视化增强

- 根据 manifest 和日志增强流程图预览
- 支持步骤高亮、失败节点标记、耗时提示
- 不引入人工拖拽维护

## 13. 最终结论

对当前项目来说，最合理的 Agent 构建方向不是“图形化节点编排”，而是：

**自然语言 Spec -> AI 编译为流程代码 + 流程图预览 -> 服务端托管执行**

它的优势在于：

1. 更符合业务人员表达方式
2. 更符合 AI Coding 的发展方向
3. 比手工图形化配置效率更高
4. 仍然保留企业级需要的治理、权限、审计和发布能力

最终产品形态应是：

**文本驱动、代码落地、结构化治理、自动生成流程图预览**

这套框架既符合你对未来产品形态的判断，也能和当前 `cc-cici-assistant` 项目的技术路线自然衔接。
