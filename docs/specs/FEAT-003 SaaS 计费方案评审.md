FEAT-003 SaaS 计费方案评审

  整体评价

  方案在产品设计层面是合理的——四层混合计费模型（平台订阅 + 席位 + 资源用量 +
  增值模块）与当前系统的企业 AI
  平台定位匹配。计费项覆盖面、套餐分层、超额策略的设计思路都没有方向性问题。

  但经过逐项与代码对照后，有以下需要完善的地方：

  ---
  一、计量基础设施严重缺失（最大风险）

  现状：OpsController.cost() 只是用 auditService.latest(orgId).size() * 0.02
  做了一个极度简化的估算（OpsController.java:33-34）。整个系统没有任何 token 级计量能力：

  - AliyunBailianClient 的 parseCompletionResponse 完全忽略了阿里云百炼 API 返回的 usage
  字段（prompt_tokens / completion_tokens / total_tokens），这些数据被直接丢弃了
  - ChatCompletionResult record 没有 token usage 字段
  - 流式调用路径同样没有从 SSE 最后一帧提取 usage

  建议：spec 应当明确将"从 AliyunBailianClient 采集真实 token
  usage"列为第一阶段的前置工作，否则后续所有 AI 用量计费项都是无根之木。具体改动：
  1. ChatCompletionResult 增加 promptTokens / completionTokens / totalTokens
  2. parseCompletionResponse 从 response.usage 中读取
  3. 流式路径从 [DONE] 前最后一帧或 final chunk 的 usage 字段提取

  ---
  二、Meter Event 协议设计缺少关键字段

  usage_meter_event 的字段设计（spec 236-249 行）缺少几个在实际结算中必需的维度：

  ┌─────────────────┬──────────────────────────────────────────────────────────────────────────┐
  │    缺失字段     │                                   原因                                   │
  ├─────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ user_id         │ 用于用户维度的用量归因和成本分摊报表；当前系统聊天、工具调用等链路都已携 │
  │                 │ 带 userId                                                                │
  ├─────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ agent_id        │ 用于按智能体维度统计成本；当前 ChatOrchestratorService 每次调用都已有    │
  │                 │ skillContext.agentId()                                                   │
  ├─────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ model_name /    │ spec 提到"按模型档位区分价格"，但 event 中没有模型标识字段               │
  │ model_tier      │                                                                          │
  ├─────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ is_platform_pai │ spec 提到区分"客户自带密钥"和"平台代付"，但 event 结构中未体现           │
  │ d               │                                                                          │
  ├─────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ session_id      │ 关联回原始会话，便于争议查账                                             │
  └─────────────────┴──────────────────────────────────────────────────────────────────────────┘

  建议：补充这些字段到 event shape 中，并说明哪些是必填、哪些是可选。

  ---
  三、席位计费的判定逻辑未与现有权限模型对齐

  Spec 建议区分"协作席位"和"构建席位"，并指出应按"是否使用构建能力"判断，而不是仅按角色编码。但当前
  系统的权限模型只有两种角色：ORG_ADMIN 和 ORG_USER（见 OrgEntity 和鉴权体系）。

  需要完善：
  1. 明确"构建能力"的操作清单——哪些 API 路径属于构建行为（例如
  /admin/agents/*、/admin/knowledge-bases/*、/admin/tools/*）
  2. 说明是基于 API 调用审计来判定席位类型，还是需要新增显式的席位分配机制
  3. "月内峰值"统计需要一个定时快照任务，spec 应提到这个实现需求

  ---
  四、Agent/Workflow 计量口径与现有执行模型不完全匹配

  Spec 提到计量"Agent 运行次数"和"节点执行数"。当前代码中：
  - AgentWorkflowExecutionLogEntity 已记录 orgId/agentId/source/status/durationMs，可以作为 Agent
  执行计量的原始来源
  - 但 AgentWorkflowRuntimeService.executeWorkflow() 目前是模拟执行（解析 workflow code 生成
  trace，不是真正的节点调度），nodeMetrics 是推算值而非实际运行结果

  建议：spec 应标注当前 runtime
  是"最小执行器"阶段，节点级计费需要等真正的执行引擎落地后才可启用。第一阶段只做 Agent/Workflow
  实例级（execution log）计量。

  ---
  五、知识库计量缺少具体采集点

  Spec 列了 7
  项知识库计费项（知识库数量、文档数、存储容量、切片数、向量容量、检索次数、重建次数），但：
  - 当前 KB 代码（KnowledgeBaseService、KbIndexWorker、EmbeddingService）没有任何用量事件发射机制
  - 向量存储使用了 QdrantVectorStoreClient，但没有暴露存储量查询接口
  - 检索发生在 RagService.retrieveContext() 中，当前无计数

  建议：spec 应列出每个计量项的具体采集点：
  - 检索次数 → RagService.retrieveContext() 入口
  - 文档数/切片数 → KbDocumentRepository / KbChunkRepository count
  - 存储容量 → 需要 Qdrant 或文件存储的容量查询 API
  - 重建次数 → KbIndexWorker 任务完成回调

  ---
  六、工具调用区分不够精确

  Spec 提到要区分"平台内置工具 / 平台代付第三方 / 客户自带密钥"三类。当前 ToolOrchestratorService
  的分发逻辑已经有清晰的分类（CloudCC 内置 → Memory → Email → Tavily → MCP），但：
  - 没有元数据标记哪个工具属于哪种计费类型
  - McpServerEntity 没有 billing_type 字段来区分客户自部署和平台托管

  建议：在 MCP Server 或 Tool Definition 实体上增加计费分类字段，或在 spec
  中说明如何通过配置来映射。

  ---
  七、缺少数据一致性与幂等性设计

  对于计量事件的可靠采集，spec 缺少以下关键设计：
  1. 幂等性：同一次 LLM 调用/工具执行重试时如何避免重复计量？建议 event 携带 idempotency_key（如
  sessionId + round + toolCallId）
  2. 异步 vs 同步：计量事件应异步写入还是同步？当前聊天链路已有 try-catch
  忽略审计写入失败的模式（ChatOrchestratorService.java:133-135），计量事件应采用类似策略
  3. 补偿机制：如果 meter event 丢失怎么办？是否需要定时对账任务？

  ---
  八、套餐配额的运行时拦截机制未设计

  Spec 定义了三种超额策略（auto_charge / soft_limit / hard_limit），但没有说明拦截点在哪里：
  - hard_limit 需要在请求入口做同步配额检查——这意味着每次聊天请求、Agent 执行前都要查询余额
  - 这会引入性能开销，需要设计本地缓存（如组织级 quota 缓存 + 异步扣减）
  - 需要明确 quota 检查的粒度：是每次 LLM 调用前检查，还是每次用户请求入口检查？

  建议：补充一节"Quota Enforcement Architecture"，说明拦截点、缓存策略和降级行为。

  ---
  九、其他小项

  1. 结算币种和精度：spec 没有提及币种字段和金额精度（分 vs 元 vs 厘），对于国际化和财务对账很重要
  2. 免费额度/试用：套餐设计没有提到试用期或免费层（Free tier），对于 SaaS 获客很常见
  3. 账单生成时序：spec
  提到"自然月结算"和"年付"，但没有说明出账任务的触发机制（定时任务？事件驱动？）
  4. 数据保留策略：usage_meter_event 会高频写入，应说明保留期和归档策略
  5. Existing Capability Mapping 小错误：spec 引用了
  ChatController，但从代码看聊天入口应该是通过某个 Controller 调用
  ChatOrchestratorService，建议核实实际类名

  ---
  建议的实施优先级调整

  Spec 的 Handoff Notes 中建议第一阶段是"建立 usage_meter_event + 升级 ops/metrics/cost +
  管理端账单总览"。我建议在这之前插入一个 Phase 0：

  ▎ Phase 0（计量数据采集）：
  ▎ 1. AliyunBailianClient 返回值增加 token usage
  ▎ 2. ChatOrchestratorService 每次聊天结束后写入 usage event（含 token、model、agent）
  ▎ 3. 为 RagService、ToolOrchestratorService、AgentWorkflowExecutionLogService 增加 event 发射钩子
  ▎ 4. 新建 usage_meter_event 表和写入 service

  没有 Phase 0 的数据采集，Phase 1 的账单和套餐页面就没有真实数据可展示。

  ---
  总结：方案的产品设计方向正确，计费分层合理，但距离可实施还缺少计量基础设施设计（token
  采集、事件幂等性、quota 拦截架构）和与现有代码的精确接驳方案。建议把以上 9 项反馈补充到 spec
  中，使后续实现者能直接拆出可执行的开发任务。