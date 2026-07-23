---
kind: feature-spec
feature_id: FEAT-131
title: 通用外部应用智能体记忆平台
status: in_progress
owner_role: shared
task_ids: TASK-226
related_decisions: FEAT-023,FEAT-024,FEAT-031,FEAT-103,FEAT-130
related_issues: none
updated_at: 2026-07-22T03:40:00Z
updated_by: MANAGER-001
---

# FEAT-131 - 通用外部应用智能体记忆平台

## 1. 背景与目标

Agent CC 是可被外部应用接入的智能体平台。外部应用可以是客服、CRM、门户、业务工作台、协同系统或行业应用；它们负责自身的渠道、会话、业务对象和业务事实。Agent CC 不应内置任一外部应用的领域对象、页面、路由或业务流程。

外部应用中的终端用户可能跨多次、多个入口持续与智能体交互。平台需要在权限范围内提供上下文连续性，又不能把全部历史原文无限注入模型，或让模型将未经验证的信息永久化。

本功能目标是在 Agent CC 中建设可治理、可复用的主体记忆服务，并以标准集成契约向任何外部应用提供同等能力：

1. 让智能体在跨会话、跨入口、跨业务 Agent 切换时保持必要的连续性；
2. 让外部应用的领域系统继续作为其实时业务事实源；
3. 用结构化会话摘要、主体记忆和按需语义检索控制上下文预算；
4. 支持来源、置信度、时效、敏感级别、人工纠正、撤销、保留与清理；
5. 在 Trace、审计和评测中完整说明记忆的读取、截断、写入、拒绝和移交。

## 2. 已确认产品原则

### 2.1 系统职责

| 系统 | 权威职责 | 非职责 |
|---|---|---|
| 外部应用 | 渠道/界面、原始交互、业务对象、领域状态、人工流程和业务动作 | 不复制 Agent CC 的模型、技能、评测与智能体治理 |
| 外部领域系统 | 订单、案例、合同、设备、项目或其他领域对象的实时业务事实 | 不承担通用上下文压缩与智能体记忆召回 |
| Agent CC | 记忆提炼、检索、上下文组装、Agent/Skill/Tool 治理、Trace、评测、审计与生命周期策略 | 不替代外部应用或领域系统的业务事实源 |

### 2.2 记忆原则

- 原始会话、会话摘要、可复用记忆、实时业务事实必须分层保存，不能用一个“聊天摘要”替代全部数据。
- 关系数据库是主体记忆的权威存储；向量数据库仅保存适合语义检索的脱敏片段索引，不能成为业务状态或权限的事实源。
- 所有记忆均以组织为硬边界；主体、会话、领域引用和 Agent scope 进一步限制可见性。
- 模型可以提出候选记忆，但不得无约束地决定长期写入、覆盖、删除或发布。
- 已确认主体信息不得重复追问；影响用户权益或外部业务状态的关键结论必须实时调用领域工具确认。
- 多个业务 Agent 可共享最小必要的主体与当前会话上下文，但不默认共享全部 Agent 私有工作记忆。
- 平台通用模型、接口、数据库表、工具和 Skills 不使用任何外部应用名称或领域对象作为实现标识；领域语义仅存在于外部应用适配配置与租户业务资产中。
- 外部应用的终端主体能够在适用的产品和权限范围内请求查看、纠正、禁用或删除非强制保留的记忆。

### 2.3 智能体职责边界

- `route-agent` 只做路由与风险判断，不直接输出领域结论，也不执行业务写操作。
- `domain-agent` 按自身绑定的知识、工具和 scope 完成领域任务。
- 人工业务人员可按组织角色和数据范围查看原文、摘要、记忆及证据，并可修正/撤销记忆。

## 3. 范围

### In Scope

- 外部应用主体、会话和领域对象的通用记忆主体模型；
- 会话滚动摘要、待办、承诺、路由归属与人工接管状态；
- 主体共享、Agent 私有、领域命名空间和会话四类记忆 scope；
- 关系型权威存储、向量索引、混合检索和 token 预算上下文组装；
- 候选记忆提炼、去重、冲突、时效、人工审核和纠正；
- 外部应用到 Agent CC 的可信服务端上下文契约；
- 多 Agent 的记忆读取规则与路由移交包；
- 记忆 Trace、审计、评测、保留、导出和清理接入。

### Out Of Scope

- 用记忆取代任何外部领域系统的实时业务事实；
- 将所有历史会话原文永久保存或全部送入模型；
- 让模型自行执行退款、赔付、改地址、关单、发券等高风险写操作；
- 为任一外部应用新增专属渠道、页面、CRM/工单或领域功能；
- 将现有 Agent CC `user_memory` 直接扩展为外部主体记忆的唯一实现；
- 新增移动端专属页面、移动端适配或移动端验收。

## 4. 用户场景

### 4.1 跨会话领域查询

外部应用用户一周后询问“上次申请现在怎么样了”。系统读取当前主体的未关闭事项、上次承诺和相关会话摘要，再调用外部领域工具。回答以实时状态为准，并引用可解释的上下文。

### 4.2 业务 Agent 移交

用户先咨询方案，后续输入需要处理已发生的业务问题。外部应用保存路由变化，路由 Agent 输出目标业务 Agent，再传入结构化移交包。目标 Agent 不重复询问已确认的身份、偏好和领域线索。

### 4.3 人工纠正

人工业务人员发现“主体偏好电话通知”已过期或错误，将该记忆禁用并给出原因。后续智能体不得再将该记忆注入上下文，Trace 仍保留修正审计。

### 4.4 重复低价值输入

用户连续输入“你好、你好呀、你好吗”。外部应用适配层或 Agent CC 通用入口在轻量语义分类层识别为连续 `GREETING`，进入限频模式；不调用模型、RAG、工具，不写入长期记忆，也不污染会话摘要。用户输入新的业务意图或人工请求后立即恢复正常流程。

## 5. 当前能力与演进边界

| 当前能力 | 可复用点 | 本功能需要补齐 |
|---|---|---|
| `user_memory` | 组织/用户/Agent 隔离、人工或提取写入、prompt 注入 | 外部主体、证据链、scope、时效、敏感级别、冲突和领域权限 |
| `chat_session_state` | 租户隔离、会话状态、乐观锁 | 通用结构化摘要、事项/承诺/路由状态、跨 Agent 移交 |
| Agent Builder | Agent、KB、工具、渠道、技能、版本、发布和评测 | 动态 Agent 移交、主体记忆绑定与可见范围配置 |
| RAG/Qdrant | 组织知识检索、metadata filter、Trace | 主体记忆的受控语义索引、按主体/领域引用/敏感度的检索过滤 |
| Trace/评测 | 模型、RAG、工具、技能、版本、运行记录 | 注入记忆、截断、记忆写入候选和路由移交证据 |
| 组织生命周期 | 保留、导出、冻结、清理和审计摘要 | 主体记忆、向量片段和证据引用的同批处理 |

现有 `user_memory` 的 `orgId + userId + agentId` 语义应继续服务 Agent CC 内部用户。所有外部应用主体使用新的 `memory_subject` 模型，不能将外部主体伪造为内部 `app_user`。

## 6. 总体架构

```text
外部应用渠道 / 界面 / 事件入口
  → 外部应用适配层（身份、会话、限频、人工流程）
  → Agent CC 主体记忆上下文服务
      → 会话快照 + 主体记忆 + 相关历史 + 授权过滤
      → Agent CC 路由 / 领域 Agent
      → RAG、受控工具、模型路由、Trace
  → 外部应用保存消息、会话归属、领域关联和发送/动作结果
  → 异步候选记忆提炼、审核、索引和审计
```

平台不保存或解释外部应用的领域字段；适配层只将标准主体、会话、授权、领域引用和最小必要上下文传入平台。一个外部入口仅进入其适配层或路由入口，多个领域 Agent 不应同时直接消费同一入口，避免重复回复和不可解释的竞争路由。

## 7. 数据设计

### 7.1 记忆主体

```text
memory_subject
- subject_id
- org_id
- subject_type: EXTERNAL_USER / EXTERNAL_PRINCIPAL / CONVERSATION / DOMAIN_REFERENCE
- application_code
- external_ref: 外部应用主体、会话或领域对象的稳定标识
- lifecycle_status
- created_at / updated_at
```

`org_id + application_code + subject_type + external_ref` 必须唯一。匿名主体、已验证主体和领域对象需有明确身份绑定状态；未验证身份不得扩大到已验证主体级别的历史记忆。

### 7.2 记忆项

```text
customer_memory_item
- memory_id, org_id, subject_id
- scope: CONVERSATION / SUBJECT_SHARED / AGENT_PRIVATE / DOMAIN_NAMESPACE
- scope_key: 可选的目标 Agent 或外部应用领域命名空间
- memory_type: PREFERENCE / VERIFIED_FACT / DOMAIN_STATE / COMMITMENT /
               OPEN_ITEM / ROUTING / RESTRICTION / SUMMARY
- content_json
- confidence
- status: CANDIDATE / VERIFIED / ACTIVE / RESOLVED / EXPIRED / SUPERSEDED / REVOKED
- sensitivity: NORMAL / INTERNAL / SENSITIVE
- valid_from / valid_to
- source_type: EXTERNAL_MESSAGE / AGENT_REPLY / TOOL_RESULT / DOMAIN_SYSTEM / HUMAN
- source_refs_json
- created_by: SYSTEM / AGENT / HUMAN
- version, created_at, updated_at
```

`content_json` 需按类型定义最小 schema。例如 `COMMITMENT` 必须包括 `owner`、`action`、`due_at`、`fulfillment_status`；`ROUTING` 必须包括 `route_type`、`confidence`、`reason`、`route_version`。平台只校验通用 schema，不定义外部应用的领域字段语义。

### 7.3 会话上下文快照

```text
conversation_context_snapshot
- org_id, conversation_id
- active_agent_id
- route_type / route_confidence / route_version
- structured_summary_json
- current_case_refs_json
- next_action
- human_handoff_required
- interaction_mode: NORMAL / REPETITIVE_GREETING / WAITING_USER / WAITING_SYSTEM
- version, latest_message_at, updated_at
```

摘要为结构化内容，不是无限追加的自然语言。最小字段包括：当前诉求、已确认事实、待补字段、已查询系统、未完成事项、承诺与截止时间、当前 Agent、转人工状态。

### 7.4 证据、候选与索引

```text
memory_evidence
- evidence_id, org_id, memory_id
- source_type, source_ref, excerpt_redacted, captured_at

memory_candidate
- candidate_id, org_id, subject_id, proposed_memory_json
- extraction_trace_id, validation_result, review_status, rejected_reason

memory_vector_fragment
- fragment_id, org_id, memory_id
- redacted_text, embedding_ref, metadata_json, indexed_at, deleted_at
```

关系数据库保存权威状态、权限和证据关系。向量库只保存已脱敏、可语义检索的文本及最小 metadata；向量命中后必须回读关系库确认状态、权限和有效期。

## 8. 读路径与上下文预算

### 8.1 每轮请求流程

1. 外部应用适配层解析组织、应用、外部主体、会话、授权和当前业务归属；
2. 轻量规则先处理重复输入、限频、渠道窗口、人工要求和确定性高风险条件；
3. 若会话已有稳定 `active_agent_id`，优先进入当前 Agent；否则调用路由 Agent；
4. Agent CC 读取会话快照、未关闭领域事项、未履约承诺和授权范围内的主体记忆；
5. 按当前问题从向量索引检索少量相关历史片段，并按权威库状态二次过滤；
6. 根据 Agent scope 装配上下文、RAG 和受控业务工具；
7. 调用目标 Agent，返回答复、结构化操作建议、Trace ID 和移交/人工标记；
8. 外部应用在原入口发送或展示结果，保存消息与业务关联；异步提交候选记忆事件。

### 8.2 注入优先级

1. 平台安全策略、渠道规则、Agent/Skill 输出契约；
2. 当前用户消息与最近必要原文；
3. 当前会话摘要、待补字段、未完成承诺、人工接管状态；
4. 实时领域工具结果和当前关联业务对象摘要；
5. 高置信、未过期、当前 Agent 有权读取的主体共享/专属记忆；
6. 最多若干条强相关历史片段；
7. 组织知识库 RAG 片段。

每一类必须配置独立 token 预算。预算不足时按“未完成承诺、当前案例、最新、高置信、高相关”的顺序保留；不得因为历史原文过多挤掉平台安全规则或实时业务事实。

### 8.3 检索排序与过滤

检索先执行强过滤：`org_id`、主体归属、Agent scope、状态、敏感级别、有效期和当前角色权限。再结合相关性、来源权威度、新鲜度、置信度与未闭环权重排序。

```text
score = semantic_relevance × source_authority × freshness × confidence × open_item_weight
```

实时领域系统结果不由该分数决定；它们必须使用受控工具实时读取。

## 9. 写路径、冲突与人工审核

### 9.1 异步候选提炼

对话完成后，以“外部用户消息 + Agent 回复 + 工具结果 + 路由/人工状态”为输入产生严格 JSON 候选。写入前按顺序执行：

1. 敏感信息识别、脱敏和数据最小化；
2. 类型 schema 校验、来源证据校验、重复检测；
3. 与现有记忆和实时业务事实冲突检测；
4. 分配置信度、scope、有效期和审核策略；
5. 自动发布、进入人工审核或拒绝；
6. 对可检索项异步生成向量片段并记录索引结果。

模型不得直接覆盖或删除长期记忆。工具结果、人工确认和外部主体明确表述可产生候选，但仍必须经过策略验证。

### 9.2 事实优先级

```text
实时业务系统 / 领域工具结果
> 人工确认
> 外部主体明确表述
> 已验证历史记忆
> AI 推断
```

冲突时保留历史版本和证据，不静默覆盖。旧项标记 `SUPERSEDED`、`EXPIRED` 或 `REVOKED`，由新项明确引用替代关系。

### 9.3 自动写入与强制审核

可自动进入 `ACTIVE` 的示例：明确语言偏好、当前会话诉求、已关联外部领域对象引用、已确认的待补字段、会话摘要。

必须人工审核或依赖业务系统确认的示例：涉及权益的承诺、补偿、报价、合同、账户权限、风险标签、争议定性和敏感个人信息。

## 10. 路由与跨 Agent 移交

外部应用以确定性规则优先：已有会话归属、未关闭领域事项、明确人工要求、风险关键词或自身业务规则。仅在无法确定时调用通用 `route-agent`。

路由 Agent 只返回结构化结果。`DOMAIN_ROUTE_KEY` 是外部应用注册的领域路由键，平台只校验其格式、绑定关系和授权，不内置任何领域枚举：

```json
{
  "route": "DOMAIN_ROUTE_KEY | HUMAN | GENERAL | KEEP_CURRENT",
  "confidence": 0.0,
  "skillCode": "optional-bound-skill-code",
  "handoffSummary": "最小必要移交摘要",
  "reason": "可审计的简短原因",
  "humanHandoffRequired": false
}
```

外部应用解析结果后调用目标 `agentId`。当前平台没有可在构建页直接配置的 Agent-to-Agent 自动转调，因此一期由任一外部应用的服务端适配层执行切换；后续可将其下沉为 Agent CC 的受控 `agent_handoff` 能力。

移交包必须包括：会话 ID、来源/目标 Agent、当前诉求、已确认事实、未完成事项、案例引用、承诺、权限/身份状态、来源 Trace ID。不得复制不相关的 Agent 私有记忆。

## 11. 对话生命周期与反循环策略

### 11.1 任务预算

会话不是无限上下文。每个任务类型配置最大澄清轮数、工具调用次数、低置信回答次数、成本/token 与等待时间。达到预算后必须输出明确下一步：建单、预约、转人工、等待异步结果或收口。

### 11.2 语义重复与限频

外部应用适配层或 Agent CC 通用入口在调用模型前对短消息做归一化、轻量意图识别和短窗口语义聚类。例如“你好、你好呀、你好吗、哈喽”连续出现时识别为 `GREETING`。

- 前两次可短答并引导业务诉求；
- 第三次提示可描述问题或输入“人工客服”；
- 后续进入 `REPETITIVE_GREETING`，使用固定短答、限频且不调用模型/RAG/工具；
- 出现新业务意图、人工请求或经过冷却时间后立即解除。

重复问候不写入长期记忆、会话学习或知识候选。

### 11.3 会话状态

支持 `NORMAL`、`WAITING_USER`、`WAITING_SYSTEM`、`HANDOFF`、`RESOLVED`、`CLOSED`。静默超时、入口回复窗口和异步领域状态由外部应用管理；会话关闭后可重开，但只注入必要摘要与未关闭事项。

## 12. Agent CC 与外部应用的接口契约

终端客户端不得直接访问 Agent CC 内部运行接口。外部应用服务端通过可信集成身份调用，并传递最小上下文：

```json
{
  "orgId": "由可信身份解析，客户端不可伪造",
  "applicationCode": "external-application-code",
  "conversationId": "external-conversation-id",
  "externalSubject": {"id": "external-subject-id", "identityLevel": "ANONYMOUS|VERIFIED"},
  "entryType": "external-entry-type",
  "requestedAgentId": "target-domain-agent",
  "activeSkillCode": "optional-bound-skill-code",
  "domainReferences": ["external-domain-ref"],
  "message": "用户本轮输入",
  "metadataFilters": {"applicationCode": "...", "conversationId": "..."}
}
```

Agent CC 返回：终端用户可见答复、Trace ID、实际 Agent/Skill、引用、工具结果摘要、路由/人工移交状态、可异步提炼的记忆事件引用。外部应用仍负责其入口发送/展示、原始消息持久化、业务工作台状态和领域关联。

## 13. 权限、安全、保留与删除

- 所有记忆读取、写入、向量检索和证据回读必须按 `org_id` 硬隔离；不得信任客户端组织 ID。
- Widget 匿名会话按组织、匿名标识、会话归属三重隔离；未验证身份不能读取联系人级别记忆。
- 敏感记忆默认不向量化；若确有检索需求，必须脱敏并限定角色与 scope。
- 原始会话默认不作为模型长期上下文。保留期限按会话类型、敏感级别、关联业务、争议状态和组织策略决定。
- 普通闲聊、业务争议、法务保留、模型评测样本应有不同保留类别；任何具体期限由产品、合同与合规要求最终确认。
- 记忆项、向量片段、证据、摘要和候选必须接入既有组织级导出、冻结、清理和审计流程；清理失败必须保留 `PARTIAL_FAILED` 摘要，不得假报删除成功。

## 14. Trace、审计与评测

每次 Agent 运行 Trace 新增或标准化记录：

- `memoryContextIds`、`memoryScopes`、`memorySelectionReason`；
- 记忆 token 占用、截断原因和未采用冲突项摘要；
- `memoryWriteCandidates`、审核状态、拒绝原因和写入后的记忆版本；
- 路由来源、目标 Agent、移交摘要与关联 Trace；
- 业务工具事实引用、脱敏状态、保留类别。

评测集至少覆盖：

1. 跨会话召回未履约承诺；
2. 业务 Agent 移交时不重复询问已确认信息；
3. 关键领域结论必须调用实时工具；
4. 过期、撤销和冲突记忆不会进入回答；
5. 跨主体、跨组织、跨 Agent 私有 scope 均不可泄露；
6. 重复问候不会触发模型、RAG、工具、长期记忆或知识学习；
7. 人工修正、用户删除和组织清理后不再被召回。

## 15. 平台治理与外部应用呈现

Agent CC 提供通用的记忆治理与 Trace 查询能力：记忆 scope、候选审核、纠正、禁用、删除、保留状态和证据链接。外部应用可以按其产品形态消费这些接口并呈现“主体上下文/记忆”区域；平台不要求或实现特定外部应用页面。

组织管理员提供记忆治理入口：查询、筛选、人工审核、纠正、禁用、删除、保留状态和 Trace 链接。所有操作需记录操作者、理由、前后版本和时间。

Agent Builder 中每个 Agent 应可查看其允许读取的 memory scopes、记忆写入策略、相关 Skills、工具白名单和评测门禁；具体模型选择继续由平台模型治理统一处理。`applicationCode` 仅作为隔离、审计和适配标识，不改变平台通用对象模型。

## 16. 分期实施

### Phase 1：通用会话连续性与可信上下文

- 定义外部应用可信接入契约和主体/会话映射；
- 建立通用会话快照、任务状态、承诺与移交包；
- 使用最小必要会话上下文和现有 Trace；
- 只允许人工或确定性规则写入会话状态，不做自动长期记忆。

`TASK-226` 负责本阶段的通用后端核心：外部应用/主体/会话的最小可信上下文契约、关系型主体记忆权威模型、会话快照和权限隔离定向测试。不得引入任何外部应用领域对象、页面或专属业务工具。

### Phase 2：主体记忆 V2 与混合检索

- 新增主体、记忆项、证据、候选与向量片段模型；
- 建立受控候选提炼、审核、冲突和 TTL 策略；
- 落地关系库权威读取 + 向量索引语义召回 + token 预算组装；
- 支持主体共享、Agent 私有和领域命名空间 scope，以及人工纠正。

`TASK-227` 先实现本阶段的关系型治理前置条件：候选、证据、审核、撤销和 TTL。任何候选都必须由人工或确定性规则审核通过后才能生成有效记忆；向量索引和自动提炼在此边界稳定后再独立授权实现。

`TASK-228` 仅落地已审核、非敏感记忆的向量片段登记和语义候选召回；每一个命中都必须回读关系库完成组织、主体、scope、状态与有效期复核。向量库不保存权限事实，也不承载外部应用领域状态。

### Phase 3：治理与运营闭环

- Trace 记忆证据、记忆质量指标和评测发布门禁；
- 接入导出、保留、删除、legal hold 与组织 purge；
- 从人工纠正、投诉和 Trace 失败回流回归集；
- 将外部应用适配层的路由能力下沉为 Agent CC 受控的 `agent_handoff` 编排能力。

## 17. 验收标准

- 外部主体在授权范围内跨会话提问时，智能体能利用当前摘要和有效记忆，且不重复询问已确认信息。
- 业务 Agent 切换时，目标 Agent 获得最小必要移交包，外部入口不会重复回复。
- 影响用户权益或领域状态的关键回答由实时工具结果支持；旧记忆不能替代实时业务事实。
- 所有记忆读取均经过组织、主体、scope、角色、敏感级别和有效期过滤。
- 重复低价值输入被限频处理，不进入大模型、RAG、工具、长期记忆或知识学习。
- 人工业务人员可在授权范围内查看来源、纠正、禁用和删除记忆；更改可审计。
- Trace 能说明被注入/截断/写入/拒绝的记忆与路由移交。
- 记忆、向量和证据纳入组织生命周期清理；清理后不再召回。
- 通过后端数据/权限/检索/生命周期测试、Agent 评测，以及至少两个不同外部应用适配方的集成验收。

## 18. 风险与降级

| 风险 | 控制与降级 |
|---|---|
| 记忆错误或过期 | 以实时工具为准；记录证据、TTL、冲突关系和人工纠正；故障时只使用会话摘要 |
| 跨租户或跨主体泄露 | 检索前强过滤、向量命中后二次回读授权、跨组织安全回归 |
| 上下文过长或成本失控 | 分层 token 预算、摘要、相关性召回、限频和工具预算 |
| 模型滥写记忆 | 异步候选管道、schema/策略校验、人工审核和审计；不直接授予长期写入权 |
| 向量索引失败或延迟 | 关系库仍可读取当前摘要、未闭环项和结构化记忆；索引异步重试，不阻断外部应用主链路 |
| Agent 路由抖动 | 会话归属锁定、确定性规则优先、`KEEP_CURRENT`、人工覆盖和 Trace 追溯 |
| 删除与法律保留冲突 | 使用组织保留策略和 legal hold；删除请求记录原因、范围、例外与执行结果 |

## 19. 交接说明

继续实现前，先阅读：

- `docs/specs/FEAT-023-ai-native-after-sales-agent.md`：一个售后外部应用接入场景，仅作领域适配参考；
- `docs/specs/FEAT-024-account-tenant-lifecycle-and-data-retention.md`：组织保留、导出与 purge；
- `docs/specs/FEAT-031-agent-evaluation-regression-system.md`：Trace 回流和评测门禁；
- `docs/specs/FEAT-103-customer-interaction-archive-memory-retrieval.md`：现有客户互动记忆与按需检索实现；
- `docs/specs/FEAT-130-forced-skill-execution-context.md`：技能选择、上下文和 Trace 语义；
- `/Volumes/AISpace/workbench/mydoc/0-解决方案资料/PRODUCT-002-followup-current-functional-requirements.md`：FollowUp 当前能力与未完成边界；它是首个参考接入方，不是 Agent CC 平台功能定义。

本规格为平台设计基线，尚未创建实现任务、数据迁移或接口。进入实现前必须将本规格拆分为任务卡和授权范围，并对通用保留期限、人工审核责任、外部应用集成认证与至少两个独立适配方的验收完成产品确认。
