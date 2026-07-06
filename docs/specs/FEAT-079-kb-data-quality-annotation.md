---
kind: feature-spec
feature_id: FEAT-079
title: 知识库数据清洗与智能标注平台能力
status: draft
owner_role: fullstack-agent
task_ids: TASK-169
related_decisions: FEAT-008, FEAT-067
related_issues: none
updated_at: 2026-07-06T15:38:52+08:00
updated_by: MANAGER-001
---

# FEAT-079 - 知识库数据清洗与智能标注平台能力

## 背景与目标

客户项目需求中明确要求平台具备“数据清洗”和“智能标注工具”能力：

- 数据清洗：识别重复/无效数据，支持正则表达式过滤与人工复核。
- 智能标注工具：支持对数据进行标注。

现有 AgentCiCi 已具备知识库上传、解析、切片、向量化、文档/片段权限、metadata、检索评测、连接器同步和 drift audit。当前缺口不是重新建设独立数据平台，而是在知识库生产链路中补齐数据质量治理与标注复核工作台，让向量化前后的知识数据可被扫描、清洗、复核、标注、审计和重新索引。

本特性目标：

- 为知识库管理员提供生产可用的数据质量扫描、清洗规则、人工复核和智能标注能力。
- 把重复、无效、噪声、正则命中、低质量片段转成可追踪的问题队列。
- 清洗动作必须支持预览、人工确认、审计记录和向量重建，不允许静默破坏知识库内容。
- 标注建议必须可审核，接受后写入现有 metadata 体系，并可用于检索过滤、评测和运营管理。

## 范围

### In Scope

- 新增知识库质量治理后端模型、API 和管理端入口。
- 支持质量扫描 run：
  - 重复 chunk 检测：基于 `contentHash` 的精确重复，预留近似重复扩展位。
  - 无效/低质量数据检测：空内容、过短、过长、过度空白、纯链接/纯符号、疑似乱码、格式噪声。
  - 正则规则检测：管理员可配置规则，扫描命中项进入复核。
- 支持清洗规则：
  - `REGEX_REMOVE`、`REGEX_REPLACE`、`TRIM`、`COLLAPSE_WHITESPACE`、`REMOVE_EMPTY_LINES`。
  - 规则必须先 preview，再由管理员 apply。
  - apply 时校验内容 hash，防止基于过期预览覆盖新内容。
  - apply 后更新 chunk 内容、刷新 hash、重建向量，并记录审计。
- 支持人工复核队列：
  - 问题状态：`OPEN`、`APPLIED`、`IGNORED`、`RESOLVED`。
  - 记录问题类型、严重级别、证据、命中规则、处理人、处理时间。
- 支持智能标注：
  - 生成 document/chunk 标签或 metadata 建议。
  - 首版以规则/启发式建议为稳定兜底，并预留模型辅助建议入口。
  - 支持接受、拒绝、修改后接受和批量应用。
  - 接受后写入现有文档 metadata；chunk 级标注写入新增标注表。
- 后台 `/admin/kb` 增加“质量治理/标注”工作区，覆盖扫描概览、问题队列、清洗规则、智能标注。
- 覆盖组织隔离、管理员权限、操作审计、租户清理和错误处理。

### Out Of Scope

- 不做独立于知识库的新数据平台、项目空间或通用数据湖。
- 不做商业级 OCR、语音/视频标注、图像框选标注。
- 不做多人标注一致性统计、众包质检或复杂标注任务分配。
- 不新增移动端兼容实现、移动端布局适配或移动端自动化验收。
- 不把模型建议作为不可审核的自动写入结果。

## 用户场景

- 知识库管理员在上传或同步数据后发起质量扫描，快速看到重复、空内容、格式噪声和规则命中。
- 管理员新增“删除页脚免责声明”“过滤广告行”等正则清洗规则，先预览影响，再批量应用。
- 管理员逐条处理问题队列：忽略误报、确认清洗、标记已解决。
- 管理员让平台给文档或片段生成标签建议，例如产品线、部署方式、客户阶段、风险类型，审核后写入 metadata。
- 后续 RAG 检索、metadata filter 和知识库评测可以使用这些标注结果。

## 现状与约束

- FEAT-067 已提供文档/chunk、metadata field、文档 metadata、ACL、eval、connector、drift audit、Qdrant smoke 和引用可信度。
- `kb_chunk.content_hash` 可用于精确重复识别，`KbChunkEntity.updateContent(...)` 可刷新内容与 hash。
- 现有 document metadata 适合承载文档级标注；chunk 级标注需要新增表，避免污染 chunk 主表。
- 迁移目录最新版本为 `V69__chat_session_state_tenant_primary_key.sql`，本特性新增迁移使用 `V70__kb_data_quality_annotation.sql`。
- 后台设计必须遵循 `PRODUCT.md` / `DESIGN.md` / `DESIGN.json` 的产品工作台风格：暖象牙底、墨色文字、紧凑密度、香槟金结构线、克制企业语气。

## 方案设计

### 1. 数据模型

新增表：

- `kb_quality_rule`
  - 规则名称、类型、pattern/replacement、作用域、启用状态、创建人、更新时间。
- `kb_quality_run`
  - 扫描任务、状态、统计、开始/结束时间、触发人、错误信息。
- `kb_quality_issue`
  - 扫描问题、目标类型、目标 id、问题类型、严重级别、证据、内容 hash、状态、处理信息。
- `kb_annotation_suggestion`
  - 标注建议、目标类型、目标 id、字段 key、建议值、置信度、建议来源、状态、处理信息。
- `kb_chunk_annotation`
  - chunk 级已接受标注，字段 key、值、来源、处理人、更新时间。

现有表复用：

- 文档级已接受标注写入 `kb_document_metadata`。
- 清洗后复用现有向量重建能力刷新 Qdrant。
- 操作审计复用平台 audit log。

### 2. API

在现有知识库管理 API 下新增 `/kb/{knowledgeBaseId}/quality/*`：

- `POST /quality/runs`：发起扫描。
- `GET /quality/runs`：查看扫描历史和统计。
- `GET /quality/issues`：分页查看复核队列。
- `POST /quality/issues/{issueId}/ignore`：忽略误报。
- `POST /quality/issues/{issueId}/resolve`：手动标记解决。
- `POST /quality/rules`、`GET /quality/rules`、`PATCH /quality/rules/{ruleId}`：管理清洗/扫描规则。
- `POST /quality/rules/{ruleId}/preview`：预览规则影响。
- `POST /quality/rules/{ruleId}/apply`：应用规则，执行 hash 校验与向量重建。
- `POST /quality/annotations/suggest`：生成标注建议。
- `GET /quality/annotations/suggestions`：查看建议队列。
- `POST /quality/annotations/suggestions/{id}/accept`：接受或修改后接受。
- `POST /quality/annotations/suggestions/{id}/reject`：拒绝建议。
- `GET /quality/annotations/chunks`：查看 chunk 级已接受标注。

### 3. 扫描与清洗流程

1. 管理员发起扫描。
2. 服务读取当前 KB 的 active documents/chunks。
3. 生成质量问题：
   - 精确重复：同一 KB 内相同 `content_hash` 的多个 active chunk。
   - 无效内容：按长度、空白、噪声、链接、符号比例检测。
   - 正则命中：按启用规则检测目标文本。
4. 问题进入 `OPEN` 队列。
5. 管理员 preview 清洗规则，确认 before/after。
6. apply 时校验 issue 记录的 `content_hash` 与当前 chunk 一致。
7. 更新 chunk 内容、重新计算 hash、重新 upsert vector。
8. issue 标记为 `APPLIED` 或 `RESOLVED`，写入审计。

### 4. 智能标注流程

1. 管理员选择文档、chunk 或当前筛选结果生成建议。
2. 首版建议来源：
   - 已有 metadata 字段名与内容关键词匹配。
   - 文档名、source metadata、标题结构、常见产品/部署/风险词汇。
   - 预留模型辅助策略，若模型服务不可用则降级为规则建议。
3. 建议以队列形式展示，管理员可批量接受、拒绝或编辑后接受。
4. 文档级接受写入 `kb_document_metadata`；chunk 级接受写入 `kb_chunk_annotation`。
5. 标注变更进入审计，并可在检索测试和 eval 中用于过滤。

### 5. 前端设计

在知识库详情内新增“质量治理”区域，采用四个紧凑子视图：

- 扫描概览：最近扫描、问题总数、重复/无效/规则命中统计、发起扫描按钮。
- 问题队列：表格、筛选、证据摘要、状态操作、清洗预览入口。
- 清洗规则：规则列表、创建/编辑、启停、预览、应用。
- 智能标注：建议队列、批量接受/拒绝、字段选择、chunk 已标注列表。

UI 原则：

- 以表格、筛选、侧栏详情和确认弹窗为主，不做营销式 hero。
- 文案保持企业后台语气，避免把“AI”做成不可解释黑箱。
- 所有危险操作必须有预览、影响数量和二次确认。

## 接口与数据影响

- 新增 Flyway 迁移 `V70__kb_data_quality_annotation.sql`。
- 新增 JPA entity/repository/service/controller，位于 `kb` 模块内。
- `PlatformTenantLifecycleService` 需要纳入新表的租户清理。
- 复用现有 KB 管理权限，所有接口要求组织管理员。
- 新增 API 向后兼容，不改变现有上传、检索和 eval 行为。
- 清洗 apply 是有状态写操作，必须审计并保留 issue/run 证据。

## 任务拆分

- `TASK-169A`：规格、任务、授权和数据模型迁移。
- `TASK-169B`：后端质量扫描、问题队列、规则 preview/apply、审计与租户清理。
- `TASK-169C`：后端智能标注建议、接受/拒绝、文档 metadata 和 chunk annotation 落库。
- `TASK-169D`：管理端“质量治理”工作区、表格、筛选、预览和批量操作。
- `TASK-169E`：集成测试、前端构建、桌面端截图、Qdrant 清洗后重建验证。

## 验收标准

- 后端集成测试覆盖：
  - 重复 chunk 被扫描为质量问题。
  - 无效/过短/噪声内容被扫描为问题。
  - 正则规则 preview 返回 before/after，不直接写库。
  - 正则规则 apply 校验 hash、更新 chunk、刷新向量、更新 issue 状态。
  - 标注建议可接受到文档 metadata 与 chunk annotation。
  - 租户删除/清理覆盖新增质量治理表。
- API 权限：
  - 非组织管理员不能访问质量治理写接口。
  - 跨组织 KB、run、issue、suggestion 不可读写。
- 前端验收：
  - `/admin/kb` 知识库详情能进入质量治理区域。
  - 扫描、问题队列、规则预览、标注建议核心流程可完成。
  - 桌面端无横向溢出、无控制台错误、符合现有设计语言。
- 验证命令至少包含：
  - `mvn -q -Dmaven.repo.local=.m2 -Dtest=KnowledgeBaseLifecycleIntegrationTest test`
  - `npm run build` in `frontend/`
  - `git diff --check`
  - 真实后端 `/admin/kb` 桌面 Playwright 截图。

## 风险与回滚

- 风险：自动清洗误删业务内容。缓解：首版必须 preview、hash 校验、人工确认和审计。
- 风险：标注建议不准确。缓解：建议默认进入待审核，不自动写入正式 metadata。
- 风险：清洗后向量与 DB 内容不一致。缓解：apply 后同步 upsert vector，并通过 drift audit/检索测试复核。
- 风险：管理页继续膨胀。缓解：将质量治理 UI 拆成独立组件或子视图，避免把所有逻辑堆进单个页面。
- 回滚：新增表和 API 可通过隐藏前端入口降级；已应用清洗可基于审计记录和文档重传/重建恢复。

## 实现进展

- 2026-07-06T15:38:52+08:00：创建规格草案，推荐采用“知识库内嵌质量治理 + 智能标注”路径。

## 交接说明

- 接手者先读 FEAT-067，确认已有 KB metadata、chunk、ACL、eval、drift 和 Qdrant 重建能力。
- 实现前必须通过 TASK-169 的 task-scoped `dev-login.py` 与 `check-assignment.py`。
- 不要在没有预览和人工确认的情况下实现自动清洗写入。
