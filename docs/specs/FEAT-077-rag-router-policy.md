# FEAT-077 - RAG 检索路由策略化改造

## 背景

2026-07-03 连续两次线上反馈暴露了同一类问题：

- `CloudCC私有云部署注意事项有哪些` 在 `2.1.8` 前没有触发已绑定知识库。
- `CloudCC 产品都有什么功能` 在 `2.1.9` 中仍没有触发知识库，并输出了 `<search_knowledge ... />` 伪工具标签。

这些问题不是向量库、文档索引或 ACL 本身失效，而是“是否应该进入知识库检索”的路由判断过于依赖零散关键词。当前补丁能止血，但不能作为长期产品能力。

## 目标

- 将知识库触发判断从散落关键词升级为独立 RAG Router。
- 路由结果必须包含可观测原因，例如显式选择知识库、Agent 默认知识库命中、领域策略命中、业务工具意图跳过、闲聊跳过。
- 产品知识、公司介绍、部署实施、规则流程、服务政策等企业私有知识问法应稳定触发 RAG。
- 邮件、客户、订单、审批等实时业务操作问法默认不触发 RAG，除非是企业微信客服等知识库优先渠道。
- 保留多租户与 KB ACL 边界：路由只决定是否检索已授权 KB，不扩大知识库范围。

## 范围

- 后端：新增或抽取 `KnowledgeRetrievalRouter`，封装 RAG 触发策略和原因。
- 后端：`ChatOrchestratorService` 使用路由结果执行预检索，并把触发原因写入 trace metadata。
- 测试：补充 focused 单元测试覆盖正向知识意图、负向业务工具意图、企业微信客服强制知识策略、无 KB 时跳过、路由原因可观测。
- 文档：更新任务状态、规格和测试报告。

## 非目标

- 不新增数据库字段或管理 UI。
- 不改 Qdrant、embedding、chunk、KB ACL、租户隔离。
- 不新增 `search_knowledge` XML 工具执行器。
- 不做线上发布，除非用户后续明确要求。

## 设计

RAG Router 输入：

- 用户问题。
- Agent/Skill 解析后的有效知识库 ID。
- 用户显式选择的知识库 ID。
- 会话 ID/渠道。

RAG Router 输出：

- `shouldRetrieve`：是否进入 RAG。
- `reason`：稳定枚举，供 trace、测试和后续运营分析使用。
- `matchedCategory`：命中的知识意图类别，例如 `product_knowledge`、`deployment_implementation`、`policy_process`。
- `matchedTerm`：命中的规则词，用于调试，不作为用户可见答案依据。

默认策略：

- 有显式 KB 选择时，只要有效 KB 不为空即检索。
- 没有有效 KB 时永不检索。
- 闲聊/创作类请求跳过。
- 企业知识类请求检索，覆盖产品、公司、部署、流程、政策、FAQ、手册、配置、依据、操作指南等类别。
- 实时业务工具类请求跳过，避免把“查客户、看订单、发邮件、拉审批”误当知识问答。
- 企业微信客服会话在存在有效 KB 且不是闲聊时优先检索，用知识库回答原则性问题，不查询实时业务系统。

## 验收标准

- `CloudCC 产品都有什么功能`、`介绍一下CloudCC这家公司`、`私有云部署注意事项有哪些`、`根据产品文档说明触发器怎么配置` 都返回检索，并带有对应路由原因/类别。
- `你好，上才艺` 跳过，原因是闲聊。
- `看下今天的潜在客户和订阅台账明细` 在普通 Web 会话中跳过，原因是业务工具意图。
- `我的订单 SO-001 怎么还没有发货` 在企业微信客服会话中检索，原因是客服渠道知识优先。
- 无有效 KB 时，即使用户显式选择 KB，也跳过并给出无有效 KB 原因。
- RAG trace metadata 包含 `ragTriggerReason`、`ragMatchedCategory`、`ragMatchedTerm` 和 `ragPolicyVersion`。

## 交付记录

- 2026-07-03：已在 `codex/TASK-167-rag-router-policy` 实现。新增 `KnowledgeRetrievalRouter`，将 RAG 触发判断输出为 `shouldRetrieve`、`reason`、`matchedCategory`、`matchedTerm`、`policyVersion`；`ChatOrchestratorService` 已使用该决策执行预检索并写入 trace metadata。focused RED/GREEN 测试、backend compile、`git diff --check` 均通过。本任务未发布生产。
