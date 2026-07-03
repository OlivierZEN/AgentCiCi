# FEAT-076 - 产品功能类知识库触发与伪工具标签防护

## 背景

2026-07-03 用户复测线上 `2.1.9` 后反馈：客户成功智能体回答 `CloudCC 产品都有什么功能` 时没有命中知识库，并直接输出：

`<search_knowledge base="神州数码云基地" query="CloudCC 产品功能" limit="10" />`

生产只读 trace 核验：

- `trace_id=3405538b-7215-42ca-ade9-1315f45c0aab`
- `agent_id=after-sales-agent`
- `title=CloudCC 产品都有什么功能`
- `rag_context_count=0`
- `knowledge_base_names_json=[]`
- `tool_call_count=0`
- `summary=<search_knowledge ... />`

对比同一生产版本 `2.1.9` 的 `私有云部署注意事项有哪些`，已能命中 `CloudCC客户成功知识库` 5 个片段。

## 目标

- Agent 已绑定知识库时，产品功能、产品能力、模块、公司介绍等知识问法应触发预检索。
- 系统提示明确禁止把 `<search_knowledge ... />`、`<rag-search ... />` 等伪工具标签作为最终回答输出。
- 保留闲聊不检索、普通实时业务工具查询不检索的保护。

## 范围

- 后端：调整 `ChatOrchestratorService.shouldUseKnowledgeRetrieval(...)` 的知识类意图覆盖。
- 后端：更新工具边界提示，明确知识库由服务端预检索/正式 function calling 执行，不能输出伪 XML 工具标签。
- 测试：补充 focused unit test 覆盖产品功能和伪工具标签防护提示。
- 文档：更新任务状态、测试报告和发布事实源。

## 非目标

- 不新增 `search_knowledge` 工具执行器。
- 不改变知识库 ACL、Qdrant 检索、向量维度、Agent 绑定模型。
- 不改变前端 UI。

## 验收标准

- `CloudCC 产品都有什么功能` 在存在有效默认知识库且未显式选择知识库时返回 `shouldUseKnowledgeRetrieval=true`。
- `介绍一下CloudCC这家公司` 在存在有效默认知识库时返回 `shouldUseKnowledgeRetrieval=true`。
- `你好，上才艺` 仍跳过知识库检索。
- `看下今天的潜在客户和订阅台账明细` 在普通 Web 会话中仍跳过知识库检索。
- 工具边界 prompt 包含禁止输出 `search_knowledge` / XML 伪工具标签的约束。
