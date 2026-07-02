# FEAT-074 - Qdrant 向量维度漂移修复

## 背景

2026-07-02 线上知识库上传本地 Markdown 文档失败，页面报错：

`Qdrant upsert failed: 400 Bad Request ... Vector dimension error: expected dim: 16, got 1024`

用户上传文件：

`/Volumes/AISpace/AI/y-skills/cc-customer-success/knowledge/import-ready/01-cloudcc-company-overview.md`

线上排查结果：

- 生产版本：`2.1.7`。
- 生产后端环境启用 `APP_KB_VECTOR_STORE=qdrant`，但没有显式 `APP_KB_EMBEDDING_DIMENSION`。
- 生产 Qdrant collection `cici_kb_chunk` 当前配置为 `vectors.size=16`。
- 数据库中 `knowledge_base.embedding_dimension` 与 `kb_chunk.embedding_dimension` 均为 `1024`。
- `QdrantVectorStoreClient` 的 `app.kb.embedding.dimension` 默认值仍是 `16`，与 `EmbeddingService`、`KnowledgeBaseService`、数据库 migration 默认值 `1024` 不一致。

## 目标

- 修复代码默认值不一致：Qdrant collection 默认维度应与本地 embedding 默认维度一致，为 `1024`。
- 启动时检测已有 Qdrant collection 维度；如果与配置维度不一致，要输出清晰错误日志，避免后续上传才暴露 obscure 400。
- 线上恢复：备份 Qdrant 数据后，将 `cici_kb_chunk` 重建为 1024 维，并重建现有 KB 向量索引。
- 上传同一个 Markdown 文档应成功发布索引，不再出现 `expected dim: 16, got 1024`。

## 范围

- 后端：`QdrantVectorStoreClient` 默认维度与 collection 维度检测。
- 测试：补充 Qdrant collection 维度解析/检测的 focused unit test。
- 运维：生产 Qdrant 备份、collection 重建、索引修复、上传 smoke。
- 文档：任务状态、测试报告、发布/运维记录。

## 非目标

- 不更换 embedding 模型。
- 不改变知识库上传 UI 视觉。
- 不改变多租户访问控制和 RAG 检索策略。
- 不删除业务源文档；Qdrant collection 重建前必须先备份。

## 验收标准

- 后端测试覆盖 Qdrant 默认维度/维度读取或 mismatch 诊断。
- 生产 `cici_kb_chunk` collection 的 `vectors.size` 为 `1024`。
- 生产已有可检索 chunk 完成向量重建，Qdrant points 数与已登记 active chunk 规模一致或有明确 drift 说明。
- 用户提供的 Markdown 文档可以重新上传并进入 `PUBLISHED` 或可恢复的成功索引状态。
