---
kind: feature-spec
feature_id: FEAT-067
title: Enterprise knowledge platform readiness
status: in_implementation
owner_role: fullstack-agent
task_ids: TASK-157
related_decisions: FEAT-008, FEAT-018, FEAT-031, FEAT-042
updated_at: 2026-06-20T16:37:00Z
updated_by: MANAGER-001
---

# FEAT-067 - 企业知识平台生产就绪

## 背景与目标

FEAT-008 已经把知识库从 MVP 推进到维护期：生命周期清理、向量删除契约、切片设置、检索测试、metadata、文档/chunk 运营、运行时 metadata filter、结构化来源和上传准入均已落地。现在用户要求继续补齐到企业知识平台程度。

本特性目标：

- 多格式稳定解析：txt/md/csv/json/docx 保持稳定，补 PDF 解析策略和解析质量错误分类。
- 连接器同步：建立 WEB/NOTION/EXTERNAL_API 数据源同步骨架，支持增量、失败恢复和同步日志。
- 权限到文档/片段级：检索阶段按文档、chunk、用户、角色或后续用户组过滤，不能只靠 KB 级授权。
- 召回评测：知识库内置评测集、query、期望命中、评分和趋势。
- 引用可信度：RAG source 返回引用置信、来源状态、版本、metadata 和可信解释。
- 重建索引与漂移检查：支持全量/增量重建、向量审计、孤儿点清理、embedding 配置漂移和内容 hash 漂移。

## 当前已具备

- 文档/知识库删除、取消发布、重建索引会清理 DB chunk 和向量，RAG 用 DB 状态作为最终闸门。
- `VectorStoreClient` 已支持结构化 upsert/search/delete/audit。
- KB 级 chunk、retrieval、embedding 设置已落地。
- 检索测试、metadata field、文档 metadata、runtime metadata filter 和结构化来源已落地。
- 文档和 chunk 的启停、归档、编辑、删除、批量操作已落地。
- 上传策略明确支持 `txt/md/csv/json/docx`，PDF 当前显式拒绝。

## 生产缺口

| 缺口 | 目标结果 | 优先级 |
|---|---|---|
| PDF 和解析质量 | PDF 明确支持文本型解析或给出可诊断失败；所有 parser 统一错误分类 | P0 |
| Qdrant 真实 profile 验证 | upsert、delete by ids/document/KB、audit、drift smoke 有真实证据 | P0 |
| 文档/片段级权限 | RAG 检索按文档/chunk ACL 过滤，运行 trace 记录过滤摘要 | P0 |
| 重建索引与漂移检查 | 管理端可触发 KB/document rebuild，查看 orphan、stale、embedding mismatch | P0 |
| 知识召回评测 | 管理员维护 query set，运行评测，查看 expected hit / precision / missing source | P1 |
| 连接器同步骨架 | WEB/NOTION/EXTERNAL_API 有 source、sync job、cursor、日志和失败恢复 | P1 |
| 引用可信度 | source 返回 confidence、trustLevel、freshness、permissionFiltered、citation reason | P1 |
| Hybrid/rerank | 全文 + 向量 + rerank 扩展点可配置 | P2 |

## 设计方案

### 1. 多格式解析

新增统一 parser contract：

```text
ParsedDocument {
  title,
  text,
  metadata,
  warnings,
  parserName,
  parserVersion,
  contentSha256
}
```

解析失败必须分类：

- `UNSUPPORTED_TYPE`
- `FILE_TOO_LARGE`
- `EMPTY_TEXT`
- `ENCRYPTED_OR_SCANNED`
- `PARSER_ERROR`
- `MALFORMED_FILE`

P0 PDF 策略：优先支持文本型 PDF 抽取；扫描件或加密 PDF 明确失败，不假装索引成功。

### 2. 数据源与连接器同步

新增数据源和同步模型：

- `kb_data_source`: `LOCAL_FILE/EMPTY/WEB/NOTION/EXTERNAL_API`、配置、状态、最近同步、错误。
- `kb_sync_job`: 手动/定时同步 job、cursor、统计、失败原因。
- `kb_source_document_map`: 外部 source id 到内部 document id 映射。

P1 先实现同步骨架和 EXTERNAL_API/WEB 的最小可用路径，Notion 可保留 provider contract。

### 3. 文档/片段级权限

新增 ACL 模型：

- `kb_acl_policy`: 绑定 KB、document 或 chunk。
- principal: `ORG`、`SYSTEM_ROLE`、`USER`、预留 `GROUP/DEPARTMENT/CUSTOM_ROLE`。
- permission: `VIEW` / `RETRIEVE` / `MANAGE`。

RAG 检索流程必须：

1. 读取候选 chunk。
2. 校验 KB、document、chunk 状态。
3. 校验当前用户或 run-as 用户的 ACL。
4. 应用 metadata filter。
5. 返回 source，并在 trace 中记录 permission filtered count。

### 4. 召回评测

新增知识库评测模型：

- `kb_eval_suite`
- `kb_eval_case`
- `kb_eval_run`
- `kb_eval_case_result`

断言首版包含：

- expected document id / title keyword。
- expected chunk keyword。
- min score。
- forbidden document。
- metadata filter expectation。

指标：

- hit rate。
- expected source recall。
- forbidden source violations。
- average top score。
- stale source rate。

### 5. 引用可信度

RAG `RetrievedSource` 需要扩展：

- `confidence`: score + retrieval method + rerank reason 的归一化结果。
- `trustLevel`: `high/medium/low/stale/permission_filtered`。
- `freshness`: 文档更新时间、索引时间、source sync time。
- `citationReason`: 为什么引用该片段。
- `sourceVersion`: document index version / content hash。

Chat/Open API/trace 需透出这些字段，前端可先展示基础引用卡片。

### 6. 重建索引和漂移检查

新增能力：

- KB 全量 rebuild。
- 文档 rebuild。
- drift audit：
  - DB active chunk 无 vector。
  - Qdrant vector 无 DB chunk。
  - embedding model/dimension 不匹配。
  - source content hash 与 document hash 不一致。
  - document published 但 source sync stale。
- drift repair：
  - 删除 orphan vector。
  - 重建 missing vector。
  - 标记 stale document。

## 任务拆分

- `TASK-157A`：parser contract、PDF 文本解析、解析错误分类和回归测试。
- `TASK-157B`：文档/chunk ACL 数据模型、RAG 权限过滤、trace 摘要。
- `TASK-157C`：rebuild / drift audit / repair 后端和管理端入口。
- `TASK-157D`：KB retrieval evaluation 数据模型、API、后端断言和管理端结果。
- `TASK-157E`：数据源同步骨架、sync job、EXTERNAL_API/WEB 最小同步路径。
- `TASK-157F`：引用可信度字段、Open API/Chat source 透出和前端展示。

## 验收标准

- txt/md/csv/json/docx/pdf 的 parser 成功、拒绝、空文本、损坏文件均有 focused tests。
- RAG 在文档级或 chunk 级无权限时不把内容放入模型上下文，trace 能看到过滤计数。
- 管理端可触发文档/KB rebuild 和 drift audit，能看到 orphan/missing/stale/mismatch 结果。
- 知识库评测可创建 query 用例，运行后展示 expected source recall、失败项和引用证据。
- RAG source 包含 score、confidence、trustLevel、document/chunk/source version、metadata、freshness。
- 真实 Qdrant smoke 覆盖 upsert、delete、audit、drift；无环境时必须记录阻塞，不得宣称通过。
- 验证至少包含 `KnowledgeBaseLifecycleIntegrationTest` 扩展、Qdrant smoke、`frontend npm run build`、`git diff --check` 和 `/admin/kb` 桌面截图。

## 非目标

- 不在本阶段做商业级 OCR。
- 不做完整 Notion 双向编辑。
- 不做复杂组织架构和用户组管理 UI；ACL 预留用户组/部门但首版可只接用户和系统角色。
- 不新增移动端兼容实现或移动端验收。

## 风险与回滚

- 风险：PDF 解析依赖复杂。缓解：P0 只支持文本型 PDF，扫描件明确失败。
- 风险：ACL 过滤影响召回。缓解：trace 记录 permission filtered count，并提供管理端诊断。
- 风险：连接器同步扩大范围。缓解：先做 provider contract、job、cursor 和 external API/web 最小路径。
- 回滚：ACL 可默认兼容已有 KB 全组织可见策略；drift repair 可先 dry-run。

## 实现进展

- 2026-06-20T16:13:20Z：
  - 引入 PDFBox，上传策略默认允许 `pdf` / `application/pdf`。
  - `KnowledgeBaseService` 对文本型 PDF 执行 PDFBox 文本抽取；加密、扫描/空文本、损坏 PDF 会明确失败。
  - `uploadPolicy` 说明更新为文本型 PDF 已启用，PDF 不再列为 unsupported parser。
  - `KnowledgeBaseLifecycleIntegrationTest` 更新 PDF 用例，使用 PDFBox 生成文本 PDF 并验证发布后 RAG 可召回。
  - 当前本地真实集成测试仍受 Docker/PostgreSQL 未启动阻塞；`mvn -DskipTests test` 已验证主代码和测试代码编译。
- 2026-06-20T16:28:40Z：
  - 新增 `kb_access_grant`，支持文档级和 chunk 级 `READ` 授权，首版 principal 覆盖 `ORG`、`USER`、`SYSTEM_ROLE`。
  - 新增 `KbAccessControlService`，chunk 授权优先于文档授权；未配置 ACL 时兼容历史全员可读，配置 ACL 后按 principal 严格过滤。
  - `RagService` 的向量命中和 fallback 检索均接入 ACL 过滤，并在 `RetrievalResult`、Chat trace、SSE RAG 阶段透出 `permissionFilteredCount`。
  - Chat 同步和流式路径传入当前 `userId` 与 `TenantContext` roles，避免模型上下文接收未授权知识片段。
  - 新增 `/kb/documents/{id}/acl`、`/kb/chunks/{id}/acl` 管理接口，组织管理员可查看和替换授权列表。
  - `KnowledgeBaseLifecycleIntegrationTest` 增加文档 ACL 检索过滤用例；当前真实集成测试仍受 Docker/PostgreSQL 未启动阻塞，`mvn -DskipTests test` 已验证主代码和测试代码编译。
- 2026-06-20T16:42:00Z：
  - 新增 `/kb/drift/audit`，默认 dry-run，传 `repair=true` 时执行可安全修复。
  - drift audit 覆盖已注册向量数、Qdrant/内存向量 orphan、active chunk 缺 vector、已发布文档无 active chunk、FAILED/CLEANUP_FAILED 文档。
  - repair 会删除已采样 orphan vector、重建受影响文档，并对无文档归属的手工 chunk 缺失 vector 进行 upsert 修复。
  - 当前 chunk 表未持久化 embedding provider/model/dimension，embedding drift 暂返回 `NOT_AVAILABLE_UNTIL_CHUNK_EMBEDDING_METADATA_IS_PERSISTED`，后续需补元数据后做真实比较。
  - `KnowledgeBaseLifecycleIntegrationTest` 增加健康知识库 drift audit 用例；当前真实集成测试仍受 Docker/PostgreSQL 未启动阻塞，`mvn -DskipTests test` 已验证主代码和测试代码编译。
- 2026-06-20T16:50:20Z：
  - `RagService.RetrievedSource` 增加 `confidence`、`trustLevel`、`freshnessStatus`、`documentIndexVersion`、`documentIndexedAt`、`chunkContentHash`。
  - Chat trace 和 SSE RAG 阶段复用 `source.toPayload()`，因此引用可信度字段会随 sources 透出。
  - trust 首版规则：向量分数高于 0.82 为 `HIGH`，高于 0.55 为 `MEDIUM`，fallback 为 `MEDIUM`，未发布或异常文档为 `LOW`；freshness 按 indexedAt 分为 `FRESH`、`AGING`、`STALE`、`UNKNOWN`。
  - `KnowledgeBaseLifecycleIntegrationTest` 增加 source payload 字段断言；当前真实集成测试仍受 Docker/PostgreSQL 未启动阻塞，`mvn -DskipTests test` 已验证主代码和测试代码编译。
- 2026-06-20T16:31:34Z：
  - 新增 `kb_eval_suite`、`kb_eval_case`、`kb_eval_run`、`kb_eval_case_result`，建立知识库召回评测数据模型。
  - 新增管理端后端 API：创建/列出 suite、添加/列出 case、运行 suite、列出 runs、查看 run results。
  - 评测运行复用真实 `RagService.retrieveDetailed`，按 expected document id、document keyword、chunk keyword、min score、forbidden document、metadata filter 计算 case 结果。
  - run 聚合输出 `hitRate`、`expectedSourceRecall`、`forbiddenSourceViolations`、`averageTopScore`、`staleSourceRate`，case result 保存 source 证据摘要。
  - 租户导出/清理清单纳入四张 eval 表，避免组织删除后残留评测数据。
  - `KnowledgeBaseLifecycleIntegrationTest` 增加召回评测 suite/case/run/results 用例；当前真实集成测试仍受 Docker/PostgreSQL 未启动阻塞，`mvn -DskipTests test` 和 `git diff --check` 已通过。
- 2026-06-20T16:37:00Z：
  - 新增 `kb_data_source`、`kb_sync_job`、`kb_source_document_map`，建立连接器同步骨架。
  - 新增管理端后端 API：创建/列出数据源、手动同步数据源、查看同步 job。
  - `WEB` / `EXTERNAL_API` 首版支持 `config.content` inline 文本或 `config.url` HTTP(S) GET；WEB 响应会轻量去 HTML 标签，NOTION 暂为 `contract_only` 并返回明确失败。
  - 同步内容写入内部 `kb_document` 并复用 `publishDocument` 索引路径，外部 `externalId` 通过 `kb_source_document_map` 关联内部文档，重复同步会更新并重建同一文档。
  - 租户导出/清理清单纳入三张 connector 表；upload policy sourceTypes 更新为 WEB/EXTERNAL_API available、NOTION contract_only。
  - `KnowledgeBaseLifecycleIntegrationTest` 增加 EXTERNAL_API inline content 同步后 RAG 可召回用例；当前真实集成测试仍受 Docker/PostgreSQL 未启动阻塞，`mvn -DskipTests test` 和 `git diff --check` 已通过。
