---
kind: feature-spec
feature_id: FEAT-008
title: Knowledge base lifecycle completion
status: in_progress
owner_role: fullstack-knowledge-platform
task_ids: TASK-020
related_decisions: none
related_issues: ISSUE-2026-04-29-kb-delete-leaves-vector-points
updated_at: 2026-04-29T13:53:11Z
updated_by: ai
---

# FEAT-008 - Knowledge base lifecycle completion

## 背景与目标

- 管理端知识库已经具备基础 CRUD、文档上传、发布索引、RAG 检索和 Qdrant/memory 向量库适配。
- 当前实现还不是完整生命周期闭环。用户已发现“知识文档删除后没有同步删除向量库对应内容”，代码检查确认该问题存在，且知识库删除、重复发布、索引中删除等路径也存在同类一致性风险。
- 对标 Dify Knowledge 后，本特性不应只补删除一致性，还需要补齐数据源、切片、索引、检索、元数据、测试与 API 运营等知识库基础能力。
- 本特性目标是把知识库从“能上传并检索”的 MVP 补齐为“可运营、可删除、可重建、可调优、可验证、不会召回过期内容”的管理端能力。

## 已检查范围

- 后端 API: `backend/src/main/java/com/codehouse/ciciassistant/kb/api/KnowledgeBaseController.java`
- 后端服务: `KnowledgeBaseService`, `KbIndexWorker`, `VectorStoreClient`, `QdrantVectorStoreClient`, `MemoryVectorStoreClient`
- RAG 检索: `backend/src/main/java/com/codehouse/ciciassistant/ai/service/RagService.java`
- 数据模型: `knowledge_base`, `kb_document`, `kb_chunk`, `agent_kb_binding`
- 管理端 UI: `frontend/src/admin/pages/AdminKnowledgePage.tsx`
- 助手端选知识库参与 RAG: `frontend/src/assistant/AssistantApp.tsx`
- 现有文档: `AI助手实现设计方案.md`, `README.md`, `.claw/test-report.md`
- Dify 官方知识库文档:
  - `https://docs.dify.ai/en/use-dify/knowledge/readme`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/introduction`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/import-text-data/readme`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/import-text-data/sync-from-notion`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/import-text-data/sync-from-website`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/chunking-and-cleaning-text`
  - `https://docs.dify.ai/en/use-dify/knowledge/create-knowledge/setting-indexing-methods`
  - `https://docs.dify.ai/en/use-dify/knowledge/manage-knowledge/maintain-knowledge-documents`
  - `https://docs.dify.ai/en/use-dify/knowledge/metadata`
  - `https://docs.dify.ai/en/use-dify/knowledge/manage-knowledge/introduction`
  - `https://docs.dify.ai/en/use-dify/knowledge/test-retrieval`
  - `https://docs.dify.ai/en/use-dify/knowledge/integrate-knowledge-within-application`
  - `https://docs.dify.ai/en/use-dify/knowledge/manage-knowledge/maintain-dataset-via-api`

## Dify 官方能力基线

- 创建入口：Dify 支持快速创建知识库，数据来源包括本地文件、Notion 同步、网页导入和空知识库；也支持通过 Knowledge Pipeline 编排复杂处理流程，以及通过外部知识库 API 接入既有 RAG 系统。
- 数据导入：本地文件上传有批量数量、文件大小和图片附件提取限制；网页导入可配置是否抓取子页面、页数上限、深度、排除/包含路径和抽取范围；Notion 支持授权、导入页面、同步更新。
- 切片配置：支持 General 与 Parent-child 两种 chunk 模式；可配置 delimiter、最大长度、overlap、父/子 chunk 参数；支持清洗连续空白、删除 URL/邮箱、Summary Auto-Gen 和切片预览。
- 索引与检索：支持 High Quality / Economical 索引方法；High Quality 下有向量检索、全文检索、混合检索；检索可配置 TopK、Score Threshold、语义/关键词权重和 Rerank 模型。
- 内容管理：文档层支持新增、修改切片设置、删除、启停、归档/取消归档、编辑 chunk、重命名、生成摘要；chunk 层支持新增、批量新增、删除、启停、编辑、关键词、图片附件和摘要。
- 元数据：支持内置元数据和自定义元数据字段，字段类型包括 string/number/time；支持批量编辑、单文档编辑，并在应用检索时使用 metadata filtering。
- 测试与记录：支持在知识库内模拟检索、临时调整检索参数，并记录测试页和应用生产调用产生的检索事件。
- 应用集成：应用可挂载多个知识库，配置多路召回、Weighted Score 或 Rerank，启用元数据过滤、Citation and Attribution，并查看知识库关联应用。
- API 运营：知识库提供 Service API endpoint/key，可程序化管理知识库、文档和 chunk；并可按知识库关闭 API Access。

## Dify 对标必须缺口

| 编号 | Dify 基线能力 | 当前项目状态 | 必须补齐内容 | 优先级 |
|---|---|---|---|---|
| D1 | 多数据源：本地文件、空知识库、Notion、网页、外部知识库 API | 仅有本地文件上传和手工 chunk 接口 | 抽象 `data_source_type/source_config/sync_status`，保留本地文件为 P0，网页/Notion/外部 KB API 作为 P1 扩展 | P1 |
| D2 | 文件解析与导入限制：批量数量、大小限制、图片附件、多格式解析 | 当前按 UTF-8 `Files.readString`，UI 暗示 PDF/CSV 但后端无解析策略 | 明确支持文件类型、大小/批量限制、解析失败原因；P0 支持 txt/md/csv/docx/pdf 的可预测解析或显式拒绝 | P0 |
| D3 | 可配置切片：delimiter、max length、overlap、预处理、预览 | 固定 280 字符硬切，无预览、无 overlap、无清洗策略 | 新增 KB/document 级 chunk settings、预处理配置和 preview API；默认值兼容现有 280 字符 | P0 |
| D4 | Parent-child chunk 模式 | 当前只有单层 chunk | 数据模型预留 `parent_chunk_id/chunk_role`，P1 实现子 chunk 命中、父 chunk 返回 | P1 |
| D5 | 索引方法与检索参数：High Quality/Economical、TopK、Score Threshold、embedding model | 当前固定 embedding 维度与 topK=5，检索策略写死 | 新增 `index_method/embedding_model/retrieval_strategy/top_k/score_threshold`，RAG 按配置执行 | P0 |
| D6 | 向量/全文/混合检索、语义/关键词权重、Rerank | 当前是向量优先 + DB fallback，无全文索引、无混合权重、无 rerank | P1 引入全文索引与 hybrid 策略；预留 rerank provider/model 配置与调用链路 | P1 |
| D7 | 文档启停、归档、重命名、编辑 chunk、chunk 启停/删除 | 当前只有上传、发布、删除文档；手工 chunk 只增不管生命周期 | 文档与 chunk 都要有 `enabled/archived/status`；所有启停/删除必须同步影响检索和向量库 | P0 |
| D8 | chunk 关键词、摘要、图片附件 | 当前没有关键词、摘要、附件模型 | P1 增加 `keywords/summary/attachments`，摘要和关键词可参与索引；图片附件先做数据模型预留 | P1 |
| D9 | 元数据字段、批量标注、metadata filtering | 当前无元数据 | 增加元数据字段定义、文档元数据值、批量编辑 API；RAG 支持手工 filter，自动 filter 留 P2 | P1 |
| D10 | 检索测试页、临时参数、检索记录 | 当前只能通过聊天间接验证 RAG | 增加 `/kb/{id}/retrieval/test` 与 `kb_retrieval_log`，管理端展示命中 chunk、score、来源和参数 | P1 |
| D11 | 应用集成可见性、多知识库召回、引用归因 | 当前助手端只传 `knowledgeBaseIds`，没有引用归因和关联应用视图 | 返回 chunk/document/kb 引用元数据，管理端展示 linked agents/apps；引用归因 UI 作为 P1/P2 | P1 |
| D12 | Knowledge Service API 和 API Access 开关 | 当前只有平台内管理 API，无独立 KB API key/access 控制 | 增加服务端 API key、权限范围、按 KB 启停 API access，支持外部系统同步文档和 chunk | P1 |

## 已验证现状与缺口

| 编号 | 现状 | 影响 | 证据 |
|---|---|---|---|
| G1 | 删除文档只删源文件和 `kb_document`，不删 `kb_chunk` 和向量点 | 已删除文档仍可能通过 DB fallback 或 Qdrant 召回 | `KnowledgeBaseService.deleteDocument(...)` 仅 `Files.deleteIfExists` + `documentRepository.deleteByIdAndOrgId` |
| G2 | 删除知识库只删 `knowledge_base` 主表 | 文档、chunk、文件、向量点、Agent 绑定均可能成为孤儿数据 | `KnowledgeBaseService.deleteKnowledgeBase(...)` 仅 `kbRepository.deleteByIdAndOrgId` |
| G3 | `VectorStoreClient` 只有 `upsert/search`，没有删除契约 | 服务层无法可靠清理 Qdrant/memory 向量内容 | `VectorStoreClient` 接口没有 delete 方法 |
| G4 | `kb_chunk` 缺少 `document_id` 字段，当前只把 `doc-{id}` 写进 tags | 删除、重建、追踪某文档的 chunk 只能靠字符串约定，无法做可靠约束 | `V2__orchestrator...` 的 `kb_chunk` 只有 org/kb/content/tags，`V4` 仅补 `vector_id` |
| G5 | 文档重复发布会追加新 chunk 和新向量点 | 同一内容重复召回，旧向量无法按版本替换 | `indexDocument(...)` 每次 split 后直接 upsert + save，没有先清理旧索引 |
| G6 | 索引中删除没有任务状态防护 | MQ worker 可能遇到已删除文档后抛错或重试；也可能在删除竞态中写入新 chunk | `KbIndexWorker.consume(...)` 直接调用 `indexDocument(...)` |
| G7 | Qdrant upsert 异常被吞掉后仍返回 UUID | 文档可能被标记为 `PUBLISHED`，但实际没有可用向量点 | `QdrantVectorStoreClient.upsert(...)` catch 后 log debug 并返回 id |
| G8 | RAG 直接使用向量库 payload content，未按 DB 文档状态二次过滤 | 删除、下线、权限收敛难以在检索阶段强制生效 | `RagService.retrieveContext(...)` 直接返回 `vectorStoreClient.search(...)` 的字符串 |
| G9 | 管理端 UI 暂无取消发布、重新索引、清理失败重试、索引错误详情 | 管理员无法自助修复坏索引或确认删除是否真正完成 | `AdminKnowledgePage.toggleDocStatus(...)` 对已发布文档仅提示暂不支持停用 |
| G10 | 知识库列表接口未返回文档数、chunk 数、最近更新时间、索引状态汇总 | 管理端卡片已有字段但后端未提供，展示容易失真 | `listKnowledgeBases(...)` 只返回 id/org/name/description/status |
| G11 | 手工 chunk 接口只写 DB chunk，不写向量库，也无文档归属 | 测试与真实文档索引路径分叉，长期会污染生命周期模型 | `POST /kb/{kbId}/chunks` 直接 `chunkRepository.save(...)` |
| G12 | 上传解析只按 UTF-8 文本读取，UI 暗示支持 PDF/CSV 等类型 | 非纯文本文件可能索引失败或内容不可控，缺少明确文件类型策略 | `indexDocument(...)` 使用 `Files.readString(...)` |
| G13 | 没有切片参数、切片预览和文档级切片设置 | 管理员无法在入库前判断 chunk 质量，后续只能重新上传 | 对标 Dify chunk settings/preview 后确认 |
| G14 | 没有检索参数、检索测试页和检索日志 | 无法在不发起真实聊天的情况下验证召回质量 | 对标 Dify retrieval testing 后确认 |
| G15 | 没有元数据字段和 metadata filtering | 无法按部门、版本、产品线、有效期等业务条件约束检索 | 对标 Dify metadata/filtering 后确认 |

## 范围

### In Scope

- 补齐文档删除、知识库删除、取消发布、重新索引的向量库与 DB 同步清理。
- 补齐基础数据源模型、文件解析策略、导入限制和同步状态。
- 补齐 KB/document 级切片设置、预处理、切片预览和可重建索引。
- 补齐索引方法、embedding model、TopK、Score Threshold 等检索设置。
- 调整 `VectorStoreClient` 契约，让 Qdrant 和 memory 实现都支持按 vectorId、documentId、knowledgeBaseId 删除。
- 为 `kb_chunk` 建立可靠的文档归属、排序、索引状态和软删除字段。
- 让 RAG 检索只返回处于可用状态的 KB、文档和 chunk。
- 管理端补齐文档状态、启停、归档、重命名、重试、取消发布、清理失败提示、批量操作和知识库统计。
- 补齐 chunk 管理基础能力：查看、启停、编辑、删除、新增，并确保向量索引同步。
- 补齐元数据字段、文档元数据值、批量编辑和检索过滤基础能力。
- 补齐检索测试 API/页面与检索记录，展示命中 chunk、score、文档来源和检索参数。
- 添加后端集成测试和最小前端构建验证，覆盖删除后不再召回。

### Out Of Scope

- 不在本阶段引入复杂权限模型，如角色/用户组级可见范围；只保留字段和设计扩展点。
- 不接入商业级对象存储或文档解析 SaaS；文件仍可先保留本地存储。
- 不强制实现 Notion/网页/外部知识库 API 的完整生产连接器；先完成数据源抽象，具体连接器按后续任务扩展。
- 不强制实现 Rerank、多模态图片向量、Summary Auto-Gen、Parent-child chunk 的完整体验；先完成数据模型和策略扩展点。
- 不实现高级引用高亮和答案溯源 UI；本阶段只保证检索一致性、基础引用数据和基础可观测。
- 不改造 Agent Builder 的知识库选择交互，只做删除后的绑定清理和运行时防护。

## 用户场景

- 管理员上传制度文档，系统自动索引，助手端勾选知识库后能召回新内容。
- 管理员删除某文档后，助手端后续提问不再召回该文档的任何 chunk 或向量内容。
- 管理员删除整个知识库后，所有文档、chunk、向量点、源文件和 Agent 绑定都被清理或标记失效。
- 文档索引失败时，管理员能看到失败原因并点击重试。
- 文档已发布后，管理员可以取消发布，保留源文件与文档记录，但 RAG 不再召回对应内容。
- 管理员在上传前可以预览切片效果，调整 delimiter、长度和 overlap，避免重要语义被硬切断。
- 管理员可以用测试问题直接验证知识库召回效果，查看每个命中 chunk 的 score、文档、元数据和检索策略。
- 管理员可以给文档打上部门、产品线、版本、有效期等元数据，并在应用或工作流里限制检索范围。
- 运维或开发人员可以根据 DB 状态和向量清理日志确认是否存在清理失败，需要时触发补偿任务。

## 方案设计

### 1. 生命周期状态

建议统一状态枚举：

- `knowledge_base.status`: `DRAFT`, `ACTIVE`, `DELETING`, `DELETED`, `CLEANUP_FAILED`
- `kb_document.status`: `UPLOADED`, `INDEXING`, `PUBLISHED`, `FAILED`, `UNPUBLISHED`, `DELETING`, `DELETED`, `CLEANUP_FAILED`
- `kb_chunk.status`: `ACTIVE`, `DISABLED`, `DELETING`, `DELETED`, `INDEX_STALE`

检索只允许：

- KB: `ACTIVE`
- Document: `PUBLISHED`
- Chunk: `ACTIVE`

上传后自动发布可以继续保留，但服务端必须允许手工 `publish/reindex/unpublish/delete`。

### 1.1 数据源与导入模型

新增数据源字段：

- `knowledge_base.source_type`: `LOCAL_FILE`, `EMPTY`, `WEB`, `NOTION`, `EXTERNAL_API`
- `knowledge_base.source_config_json`: 数据源配置，敏感凭证只保存引用，不直接明文入库。
- `kb_document.source_uri`: 文件路径、网页 URL、Notion page id 或外部文档 id。
- `kb_document.source_etag/source_updated_at`: 支持后续增量同步。
- `kb_document.file_size/content_sha256`: 支持去重、重复上传检测和审计。

P0 只要求本地文件与空知识库稳定；P1 增加网页、Notion、外部知识库 API。

文件导入 P0 规则：

- 配置化限制：`maxFileSize`, `maxFilesPerUpload`, `allowedContentTypes`。
- 后端按 content type 选择 parser；不支持的类型必须明确失败，不允许误标 `PUBLISHED`。
- 解析输出统一为 `ParsedDocument { text, title, metadata, attachments }`。

### 2. 数据模型调整

新增迁移建议 `V25__kb_lifecycle_completion.sql`：

- `knowledge_base`
  - `source_type VARCHAR(32)`
  - `source_config_json TEXT`
  - `index_method VARCHAR(32)`
  - `embedding_model VARCHAR(128)`
  - `retrieval_strategy VARCHAR(32)`
  - `top_k INT`
  - `score_threshold NUMERIC(5,4)`
  - `rerank_model VARCHAR(128)`
  - `chunk_mode VARCHAR(32)`
  - `chunk_settings_json TEXT`
  - `preprocess_settings_json TEXT`
  - `api_access_enabled BOOLEAN NOT NULL DEFAULT TRUE`
  - `updated_at TIMESTAMP`
  - `deleted_at TIMESTAMP`
  - 可选统计缓存: `document_count`, `published_document_count`, `chunk_count`
- `kb_document`
  - `file_size BIGINT`
  - `content_sha256 VARCHAR(128)`
  - `source_uri VARCHAR(1000)`
  - `source_etag VARCHAR(256)`
  - `source_updated_at TIMESTAMP`
  - `enabled BOOLEAN NOT NULL DEFAULT TRUE`
  - `archived BOOLEAN NOT NULL DEFAULT FALSE`
  - `chunk_settings_json TEXT`
  - `indexed_at TIMESTAMP`
  - `updated_at TIMESTAMP`
  - `deleted_at TIMESTAMP`
  - `error_message VARCHAR(1000)`
  - `index_version INT NOT NULL DEFAULT 1`
- `kb_chunk`
  - `document_id BIGINT`
  - `chunk_index INT`
  - `content_hash VARCHAR(128)`
  - `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'`
  - `created_at TIMESTAMP`
  - `deleted_at TIMESTAMP`
  - `metadata_json TEXT`
  - `enabled BOOLEAN NOT NULL DEFAULT TRUE`
  - `parent_chunk_id BIGINT`
  - `chunk_role VARCHAR(16)`
  - `keywords TEXT`
  - `summary TEXT`
  - index: `(org_id, knowledge_base_id, document_id)`
  - index: `(org_id, status, knowledge_base_id)`
- `kb_metadata_field`
  - `id`, `org_id`, `knowledge_base_id`, `field_key`, `field_name`, `value_type`, `created_at`
  - unique: `(org_id, knowledge_base_id, field_key)`
- `kb_document_metadata`
  - `id`, `org_id`, `knowledge_base_id`, `document_id`, `field_id`, `string_value`, `number_value`, `time_value`
- `kb_retrieval_log`
  - `id`, `org_id`, `knowledge_base_id`, `app_id`, `session_id`, `query`, `retrieval_strategy`, `top_k`, `score_threshold`, `hit_count`, `hit_summary_json`, `created_at`

现存数据迁移：

- 从 `kb_chunk.tags` 中的 `doc-{id}` 尝试回填 `document_id`。
- 无法回填的历史 chunk 标记为 `INDEX_STALE`，保留但不参与检索；管理员可重建索引。
- 对已有 `kb_document` 回填 `updated_at = created_at`。
- 对已有 KB 回填 `source_type=LOCAL_FILE`、`index_method=HIGH_QUALITY`、`retrieval_strategy=VECTOR`、`top_k=5`。

### 3. 向量库契约

把 `VectorStoreClient` 扩展为：

```java
String upsert(VectorUpsertCommand command);
List<VectorSearchHit> search(VectorSearchQuery query);
VectorDeleteResult deleteByVectorIds(String orgId, List<String> vectorIds);
VectorDeleteResult deleteByDocument(String orgId, String knowledgeBaseId, Long documentId);
VectorDeleteResult deleteByKnowledgeBase(String orgId, String knowledgeBaseId);
```

向量 payload 必须包含：

- `org_id`
- `knowledge_base_id`
- `document_id`
- `chunk_id`
- `chunk_index`
- `content_hash`

Qdrant 删除策略：

- 优先按 `vector_id` 列表删除，适合文档和重建索引。
- 缺少本地 vectorId 时，按 payload filter 删除 `org_id + knowledge_base_id + document_id`。
- 删除调用使用 `wait=true`，失败时返回明确状态，不吞异常。

Memory 删除策略：

- 内存条目保留同样 payload，支持按 vectorId/document/kb 删除，作为集成测试的确定性后备。

### 4. 索引与重建流程

发布或重新索引文档：

1. 校验 KB 属于当前 `org_id` 且未删除。
2. 校验文件类型、大小、批量限制和 parser 可用性。
3. 将文档置为 `INDEXING`，生成新的 `index_version`。
4. 先清理旧 chunk 和旧向量点，或将旧 chunk 标记为 `INDEX_STALE`。
5. 解析文档，生成标准文本、标题、系统元数据和附件。
6. 应用 preprocess settings，按 chunk settings 生成 chunk；如为 Parent-child 模式，生成父/子结构。
7. 保存 chunk 草稿，拿到 chunk id。
8. 写入向量库，payload 带 `document_id/chunk_id`。
9. 回写 `vector_id`，chunk 置为 `ACTIVE`。
10. 文档置为 `PUBLISHED`，更新 `indexed_at/updated_at`。

切片预览：

- `POST /kb/chunking/preview`：输入文件或文本、chunk settings、preprocess settings，返回前 N 个 chunk 预览。
- 预览只做解析和切片，不入库，不写向量。

Chunk 管理：

- 编辑 chunk 后必须重新生成 embedding 并 upsert 同一个或新的 vector point。
- 禁用 chunk 只改 DB 状态并从检索中过滤；P1 可选择同步删除向量以降低成本。
- 删除 chunk 必须删除对应向量点。
- 新增 chunk 必须走与文档索引一致的 embedding/upsert 路径，不再只写 DB。

失败处理：

- 任一 chunk 写向量失败，文档置为 `FAILED`，记录 `error_message`。
- 已写入的新向量点通过补偿清理。
- 旧版本是否保留由实现选择：P0 可以先清旧再写新，P1 可做双版本切换以降低重建失败影响。

### 5. 删除与取消发布流程

删除文档：

1. 文档置为 `DELETING`，检索立即不可见。
2. 查询该文档下所有 chunk 和 vectorId。
3. 删除向量点。
4. chunk 置为 `DELETED` 或物理删除。
5. 删除源文件。
6. 文档置为 `DELETED` 或物理删除。
7. 返回 `cleanupStatus=COMPLETED`。

删除知识库：

1. KB 置为 `DELETING`，检索立即不可见。
2. 遍历所有未删除文档执行同样清理。
3. 清理 `agent_kb_binding` 中该 KB 的引用。
4. 对 Skill 的 `kbWhitelist` 暂不自动改写 JSON，但运行时必须忽略不存在或已删除 KB；管理端后续可提示失效引用。
5. KB 置为 `DELETED` 或物理删除。

取消发布：

1. 文档置为 `UNPUBLISHED`。
2. 删除该文档向量点。
3. chunk 置为 `INDEX_STALE` 或 `DELETED`。
4. 保留源文件与文档记录，允许后续重新发布。

清理失败：

- 如果向量库删除失败，状态置为 `CLEANUP_FAILED`，记录错误。
- RAG 仍必须通过 DB 状态屏蔽该文档或 KB。
- 管理端提供“重试清理”动作。

### 6. RAG 检索收口

当前 `VectorStoreClient.search(...)` 返回字符串，后续应返回 `VectorSearchHit`：

- `vectorId`
- `chunkId`
- `documentId`
- `knowledgeBaseId`
- `score`
- `contentPreview`

`RagService` 处理方式：

1. 校验请求中的 KB 都属于当前 org 且 `ACTIVE`。
2. 读取 KB 检索配置：`retrieval_strategy`, `top_k`, `score_threshold`, `rerank_model`。
3. 应用 metadata filter，先收敛候选文档或在 DB 二次过滤阶段过滤。
4. 按策略执行 vector/full-text/hybrid 检索；P0 先实现 vector + DB 状态过滤，P1 实现 full-text/hybrid/rerank。
5. 向量召回拿到 hit 后，用 `chunkId/documentId/kbId/orgId` 回 DB 二次过滤状态。
6. 只拼接 DB 中 `ACTIVE + PUBLISHED + ACTIVE KB` 的 chunk content。
7. DB fallback 也必须带同样状态过滤。
8. 返回结果保留 `score`, `documentId`, `documentName`, `chunkId`, `knowledgeBaseId`, `metadata`, `sourceUri`，供引用归因和检索测试使用。

这样即使 Qdrant 中仍有历史点，也不会进入模型上下文。

### 7. 管理端补齐

知识库列表：

- 展示 `documentCount`, `publishedDocumentCount`, `failedDocumentCount`, `chunkCount`, `updatedAt`。
- 卡片状态区分 `草稿/可用/清理中/清理失败`。

文档表：

- 展示大小、chunk 数、索引时间、错误摘要。
- 操作：发布、重新索引、取消发布、启用/停用、归档/取消归档、重命名、删除、清理失败重试。
- 支持批量删除和批量重建索引。
- `INDEXING/DELETING` 继续轮询刷新。

切片与解析：

- 创建/上传时配置 chunk mode、delimiter、max length、overlap、preprocess settings。
- 提供切片预览面板，展示前 N 个 chunk、字数、是否过短/过长。
- 文档详情允许修改文档级 chunk settings 并触发重新索引。

Chunk 详情：

- 查看 chunk 列表、score 测试命中记录、状态、关键词、摘要、metadata。
- 支持新增、编辑、启停、删除 chunk。
- 编辑后显示 `Edited` 标记并重建向量。

元数据：

- 管理 metadata fields：新增、重命名、删除字段。
- 文档列表支持批量编辑 metadata。
- 检索测试和应用配置支持手工 metadata filter。

检索测试：

- 输入 query，临时调整 topK、scoreThreshold、retrievalStrategy。
- 展示命中 chunk、score、文档名、metadata、sourceUri、是否通过状态过滤。
- 记录测试和应用调用的检索日志。

删除交互：

- 删除文档确认文案明确“会同步删除向量索引”。
- 删除 KB 二次确认展示文档数量和绑定影响。
- 清理失败时保留红色状态和重试入口，不只 toast。

### 8. API 变更建议

- `GET /kb`
  - 增加统计字段和 `updatedAt`。
- `GET /kb/{kbId}/documents`
  - 增加 `fileSize`, `chunkCount`, `indexedAt`, `updatedAt`, `errorMessage`, `enabled`, `archived`, `sourceType`, `metadata`。
- `PUT /kb/{id}/settings`
  - 更新 index/retrieval/chunk/preprocess/API access 等设置；变更 embedding model 时触发全量重建提示。
- `POST /kb/chunking/preview`
  - 返回切片预览，不落库。
- `POST /kb/documents/{id}/publish`
  - 保持兼容，内部变成幂等索引任务。
- `POST /kb/documents/{id}/reindex`
  - 明确重建索引。
- `POST /kb/documents/{id}/unpublish`
  - 下线文档并清向量。
- `POST /kb/documents/{id}/enable` / `POST /kb/documents/{id}/disable`
  - 临时纳入/排除检索。
- `POST /kb/documents/{id}/archive` / `POST /kb/documents/{id}/unarchive`
  - 归档保留但排除检索。
- `PATCH /kb/documents/{id}`
  - 重命名、更新文档级切片设置、更新 metadata。
- `POST /kb/documents/{id}/cleanup/retry`
  - 重试删除或取消发布失败后的向量清理。
- `DELETE /kb/documents/{id}`
  - 返回 `cleanupStatus`。
- `DELETE /kb/{id}`
  - 返回 `deletedDocuments`, `deletedChunks`, `deletedVectors`, `cleanupStatus`。
- `GET /kb/documents/{id}/chunks`
  - 查看 chunk 列表。
- `POST /kb/documents/{id}/chunks`
  - 新增 chunk 并写向量。
- `PATCH /kb/chunks/{chunkId}`
  - 编辑内容、关键词、摘要、启停状态，并按需重建向量。
- `DELETE /kb/chunks/{chunkId}`
  - 删除 chunk 和向量点。
- `POST /kb/{kbId}/retrieval/test`
  - 返回命中结果和过滤/重排信息。
- `GET /kb/{kbId}/retrieval/logs`
  - 查询检索记录。
- `POST/GET/PATCH/DELETE /kb/{kbId}/metadata-fields`
  - 管理元数据字段。
- `PUT /kb/{kbId}/documents/metadata`
  - 批量编辑文档元数据。

## 任务拆分

- `TASK-020A`: 数据模型和 repository
  - 添加迁移、实体字段、chunk/document/kb 查询与删除方法；补 source/chunk/retrieval/metadata/log 数据模型。
- `TASK-020B`: 向量库删除契约
  - 扩展 `VectorStoreClient`，实现 Qdrant 和 memory 删除，删除失败不再静默吞掉。
- `TASK-020C`: 生命周期服务
  - 文档删除、KB 删除、取消发布、重建索引、索引中删除防竞态。
- `TASK-020D`: RAG 状态过滤
  - 向量 hit DB 二次过滤，DB fallback 加状态过滤。
- `TASK-020E`: 管理端 UI
  - 状态、统计、重试、取消发布、批量动作、清理失败提示。
- `TASK-020F`: 文件解析、切片设置和切片预览
  - 支持可配置 delimiter/max length/overlap/preprocess settings，上传前预览 chunk。
- `TASK-020G`: 检索设置与测试记录
  - 支持 topK/scoreThreshold/retrievalStrategy，新增 retrieval test API 和日志。
- `TASK-020H`: 文档/chunk 运营
  - 文档启停/归档/重命名；chunk 查看/新增/编辑/启停/删除，并同步向量。
- `TASK-020I`: 元数据与过滤
  - metadata field、批量标注、RAG metadata filter。
- `TASK-020J`: 回归测试与 smoke
  - 删除文档后不再 RAG 命中，删除 KB 后无孤儿数据，重复发布不重复召回。

## 验收标准

- 删除已发布文档后：
  - `kb_document` 不再处于可检索状态。
  - 该文档的 `kb_chunk` 不再参与检索。
  - Qdrant/memory 中对应向量点被删除或被 DB 二次过滤彻底屏蔽。
  - 同一问题再次调用 `/ai/chat` 不再返回该文档内容。
- 删除知识库后：
  - 该 KB 不出现在 `GET /kb` 可用列表中。
  - 其文档、chunk、向量点、源文件被清理或标记删除。
  - Agent 绑定中的该 KB 引用被清理或运行时忽略。
- 重复发布同一文档不会产生重复可检索 chunk。
- 索引失败或清理失败时，管理端能展示失败原因并重试。
- 管理员可以配置 delimiter、max length、overlap 和预处理选项，并在入库前预览 chunk。
- 管理员可以调整 topK、score threshold 等检索参数，并用测试页看到命中 chunk、score、来源文档和过滤原因。
- 文档和 chunk 的启停、归档、编辑、删除都能立即影响检索结果。
- 文档元数据可创建、批量编辑，并可在检索时作为过滤条件。
- 后端至少补充集成测试：
  - `shouldDeleteDocumentChunksAndVectors`
  - `shouldDeleteKnowledgeBaseCascadeDataAndRuntimeBindings`
  - `shouldNotRetrieveDeletedOrUnpublishedDocument`
  - `shouldReindexDocumentIdempotently`
  - `shouldPreviewChunksWithConfiguredDelimiterAndOverlap`
  - `shouldFilterRetrievalByDocumentMetadata`
  - `shouldDisableDocumentAndChunkFromRetrieval`
- 前端至少完成 `npm run build`。

## 风险与回滚

- 风险：历史 chunk 没有 `document_id`，无法精确归属。缓解：先从 tags 回填，无法回填的标记为 `INDEX_STALE`，必要时要求重建索引。
- 风险：Qdrant 删除失败导致向量残留。缓解：DB 状态过滤是最终闸门，向量清理失败只影响存储成本，不应影响召回正确性。
- 风险：删除 KB 自动清理 Agent 绑定可能影响已发布 Agent。缓解：删除前管理端展示绑定影响；运行时忽略失效 KB，并在 Agent 详情显示失效引用。
- 风险：本地 memory 和 Qdrant 行为不一致。缓解：统一接口测试覆盖两类实现，核心服务测试以 memory 确定性验证。
- 风险：一次性追平 Dify 全量能力会拉大范围。缓解：P0 先交付删除一致性、文件解析边界、切片设置、检索参数、启停状态和测试页；Notion/网页/外部 KB、Parent-child、Rerank、多模态和 Service API 作为 P1/P2。
- 回滚方式：保留新增字段不删，恢复旧 API 行为时仍让 RAG 状态过滤生效；如果向量删除接口有问题，可暂时只做 DB 屏蔽并排队后台清理。

## 实现进展

- 当前状态：P0 第一阶段已实现并验证；第二阶段已完成“切片/检索参数 + preview/retrieval test + metadata filtering + 文档/chunk 细粒度运营 + 批量操作”三批落地，剩余人工回归与 UX 收尾。
- 已完成项：
  - 已检查知识库管理端、后端知识库服务、向量库适配、RAG 检索和现有测试覆盖。
  - 已对照 Dify Knowledge 官方文档补齐能力差距分析。
  - 已确认文档删除不同步向量库和 chunk 清理的问题。
  - 已整理生命周期、切片、检索、元数据、测试和内容运营补齐方案与任务拆分。
  - 已新增迁移 `V27__kb_lifecycle_completion.sql`，补齐 `kb_chunk.document_id/status/enabled/deleted_at/chunk_index/content_hash`，并为 KB/document/chunk 增加生命周期与统计所需字段。
  - 已扩展 `VectorStoreClient` 为结构化 `VectorUpsertCommand/VectorSearchQuery/VectorSearchHit`，并增加 `deleteByVectorIds/deleteByDocument/deleteByKnowledgeBase`；`MemoryVectorStoreClient` 与 `QdrantVectorStoreClient` 均已实现。
  - 已改造 `KnowledgeBaseService`：发布/重建索引前清旧 chunk 和向量；删除文档、删除知识库、取消发布会同步清理向量和 DB chunk，删除 KB 会清理 Agent KB 绑定。
  - 已改造 `RagService`：向量命中必须用 `chunkId` 回 DB 校验，DB fallback 同样过滤 KB/document/chunk 状态；删除或下线文档不会再被召回。
  - 管理端已补最小操作入口：文档重建、下线、删除确认文案、chunk 数和清理状态展示。
  - 已新增 `KnowledgeBaseLifecycleIntegrationTest`，覆盖删除文档、取消发布、重建索引幂等和删除 KB 级联。
  - 已新增迁移 `V28__kb_chunking_and_retrieval_settings.sql`，落地 `knowledge_base` 的 `chunk_size/chunk_overlap/chunk_delimiter/retrieval_strategy/top_k/score_threshold` 与 `kb_retrieval_log`。
  - 已新增后端 API：`GET/PUT /kb/{id}/settings`、`POST /kb/{id}/chunking/preview`、`POST /kb/{id}/retrieval/test`、`GET /kb/{id}/retrieval/logs`。
  - `KnowledgeBaseService` 已按 KB 参数执行分片（索引链路）和检索测试（topK/scoreThreshold），并写入 retrieval logs。
  - `RagService` 已按 KB 配置的 `topK/scoreThreshold` 生效召回过滤。
  - 管理端知识库设置页已接入切片参数编辑、chunk preview、retrieval test 和最近检索日志。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkPreviewAndRetrievalTestWithKbSettings`。
  - 已新增迁移 `V29__kb_metadata_and_chunk_ops.sql`，落地 `kb_metadata_field` 与 `kb_document_metadata`。
  - 已新增文档/切片细粒度运营 API：文档 `rename/enable/disable/archive/unarchive`，切片 `list/update/enable/disable/delete`。
  - retrieval test 已支持 `metadataFilters` 并按文档 metadata 过滤结果。
  - 管理端文档列表已接入文档重命名、文档启停/归档、切片管理弹层（编辑/启停/删除）和文档 metadata 编辑入口；设置页新增 metadata 字段管理。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportChunkToggleAndMetadataFilteringInRetrievalTest`。
  - 已新增文档/切片批量操作 API，并在管理端接入批量勾选与批量动作。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldSupportBatchDocumentOperations`。
  - retrieval test 已新增 metadata filter 字段合法性校验，未知字段会返回明确错误。
  - 管理端检索测试区已展示可用 metadata 字段，批量操作按钮已补“已选数量 + 空选禁用态”。
  - 已补并通过 `KnowledgeBaseLifecycleIntegrationTest.shouldRejectUnknownMetadataFilterField`。
  - 管理端知识库页面视觉已完成第一批收口：按钮、表格、输入、侧栏和弹层统一为简洁紧凑风格，减少高饱和和装饰性效果，强化后台操作密度与可读性。
  - 已完成管理端 UX 收口第二轮：metadata/doc metadata 输入增加格式校验提示；检索测试与切片预览补空输入和空结果提示；文档/切片批量条新增“清空选择”；失败态错误文案统一样式；设置区改为响应式网格。
  - 已完成前端验证：`frontend npm run build` 通过（含第二轮 UX 调整）。
  - 已完成真实后端接口回归：建库、metadata 字段创建、unknown metadata 字段报错、文档上传发布、metadata 过滤检索、检索日志、文档批量启停均通过。
  - 已完成管理端 UX 收口第三轮：检索 metadata 字段与文档 metadata 字段新增前端字段存在性校验并提示可用字段；输入变化会重置空结果提示，避免误导。
  - 已完成前端验证：`frontend npm run build` 通过（含第三轮 UX 调整）。
  - 已完成管理端 UX 收口第四轮：批量操作结果新增内联反馈块（成功/部分失败 + 失败样本）并支持关闭；切片预览与检索测试按钮在空输入时禁用。
  - 已完成回归复核：重启本地后端后再次执行 `/tmp/feat008_reg.sh`，建库/metadata 字段/unknown field 报错/文档上传发布/metadata 检索/检索日志/批量启停均通过。
  - 已完成前端验证：`frontend npm run build` 通过（含第四轮 UX 调整）。
- 未完成项：
  - 管理端仍需补一轮页面级人工回归（文档/设置/切片弹层），确认第四轮“批量反馈块 + 按钮禁用态”无回归。

## 交接说明

- 下一位接手者可从 `TASK-020F/TASK-020G` 继续：先落切片设置/预览，再落检索参数与 retrieval test。
- 当前 P0 删除一致性已具备 DB 状态最终闸门；即使 Qdrant 中存在历史残留点，没有有效 `chunk_id` 或 DB 状态不可检索的 hit 不会进入 RAG 上下文。
- 手工 chunk 入口已改为同步写向量并返回 chunk/vector 信息，但仍缺少 chunk 编辑/删除 API；后续按 `TASK-020H` 补齐。
