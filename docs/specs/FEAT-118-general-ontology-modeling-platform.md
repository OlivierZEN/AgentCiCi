---
kind: feature-spec
feature_id: FEAT-118
title: 通用本体建模与语义查询平台 V1
status: implemented
owner_role: project-manager
task_ids: TASK-213
related_decisions: FEAT-067, FEAT-075, FEAT-081, FEAT-103, FEAT-111
updated_at: 2026-07-17T08:21:42Z
updated_by: MANAGER-001
---

# FEAT-118 - 通用本体建模与语义查询平台 V1

## 1. 背景与用户决策

用户希望把 AgentCiCi 的“认知与记忆引擎”补齐为通用本体平台，而不是把知识图谱能力固化在 CloudCC CRM 领域。首期允许用 CloudCC CRM 做真实落地案例，但平台内核、数据结构、接口和界面不得出现 CRM 专属假设。

本规格直接承接用户已确认的决策：

- 产品面向业务人员，以可视化建模为主，不要求用户手写 Schema、GraphQL、SQL 或数据源 API。
- 默认入口是“先描述业务领域，再由 AI 生成本体草稿并寻找物理数据映射”；“先连接数据源再反向生成”作为快捷入口。
- AI 可以生成和持续修改草稿，但不能直接发布。发布必须由有权限的业务人员人工触发。
- V1 以查询与只读推理为边界；业务动作只定义名称、用途和参数，不执行外部系统写回。
- 用户授权直接采用本规格中的推荐设计并进入实现、验证与生产发布，不再设置额外书面确认门。

## 2. 产品目标

V1 要交付一个领域无关的“业务语义设计器”，使组织管理员能够：

1. 用自然语言描述一个业务领域。
2. 让 AI 生成概念、属性、关系、指标与动作草稿。
3. 在可视化画布中调整本体，不接触底层 Schema。
4. 连接或导入物理数据源元数据，把业务语义映射到真实对象和字段。
5. 校验并发布不可变版本。
6. 自动获得 JSON Schema、GraphQL SDL 和受限语义查询契约。
7. 用结构化语义查询读取映射后的数据，并得到来源、映射和版本证据。

V1 的通用性必须由两套领域证明：

- 真实参考领域：CloudCC CRM，覆盖客户、联系人、商机、产品、互动和经营动作。
- 非 CRM 轻量领域：项目交付，覆盖项目、任务和负责人，使用内置示例数据源；创建该领域不得修改后端代码。

## 3. 非目标

- 不实现任意 SQL、任意脚本、任意系统调用或外部写回。
- 不在 V1 引入独立图数据库；本体定义、版本和关系先落 PostgreSQL，保留图存储适配边界。
- 不实现 OWL 全集、RDF 推理机、SPARQL 全语法或复杂知识图谱算法。
- 不做自动发布、无人审批的破坏性变更或跨版本静默迁移。
- 不把文档 RAG、用户偏好或模型推断直接当作已验证企业事实。
- 不新增移动端布局、截图或自动化验收。

## 4. 方案比较与选择

### 4.1 方案 A：领域无关内核 + 可安装领域包（采用）

内核只理解本体、概念、属性、关系、指标、动作、数据源、物理对象、映射、版本和查询计划；CRM 通过领域包、数据源适配器和映射模板接入。

优点是领域边界清晰、可测试、可演进，既能快速用 CRM 验证，也能在不改内核代码的情况下创建其他业务领域。缺点是首版必须同时设计通用元模型和适配器契约。

### 4.2 方案 B：先做 CRM 语义层，再抽象

落地更快，但实体、字段和查询逻辑容易沉淀为 CRM 专用模型，后续抽象成本高，与用户的通用平台目标冲突，不采用。

### 4.3 方案 C：直接采用完整 RDF/OWL 技术栈

标准能力强，但对业务用户、现有 Spring/PostgreSQL 架构和 V1 交付范围过重。V1 只保留可导出、可扩展的语义边界，不采用完整标准栈。

## 5. 总体架构

```text
业务语义设计器
  ├─ 领域描述向导
  ├─ 可视化本体画布
  ├─ 属性 / 关系 / 指标 / 动作检查器
  ├─ 数据映射工作台
  └─ 校验、差异、发布与版本浏览
            │
            ▼
AI 建模副驾驶 ──只产生草稿提案──▶ 本体草稿服务
            │                         │
            │                         ├─ 元模型校验器
            │                         ├─ 影响分析器
            │                         ├─ JSON Schema 编译器
            │                         └─ GraphQL SDL 编译器
            │
            ▼
物理元数据目录 ◀── 数据源适配器 ── CloudCC / 内置示例 / 后续连接器
            │
            ▼
只读语义查询路由器 ──▶ 受限查询计划 ──▶ 数据源适配器 ──▶ 证据化结果
```

### 5.1 模块边界

- `ontology/model`：领域无关元模型、草稿和发布版本。
- `ontology/catalog`：数据源、物理对象和字段目录。
- `ontology/mapping`：概念/属性/关系到物理对象/字段的映射。
- `ontology/ai`：AI 提案生成、提案预览和草稿应用；不能发布。
- `ontology/compiler`：确定性生成 JSON Schema、GraphQL SDL 和查询契约。
- `ontology/query`：校验结构化语义查询、生成只读执行计划、调用适配器并返回证据。
- `ontology/adapter`：数据源发现和只读查询接口；具体供应商逻辑不得进入内核。
- `frontend/admin/ontology`：业务人员使用的领域向导、画布、映射、校验和发布界面。

每个模块通过显式 DTO 或接口通信。AI、CloudCC 和图形画布都不是本体元模型的依赖。

## 6. 通用元模型

### 6.1 本体工作区

`OntologyWorkspace` 表示一个组织内独立的业务领域：

- `id`、`orgId`、`key`、`name`、`description`
- `status`: `DRAFT | PUBLISHED | ARCHIVED`
- `draftRevision`
- `publishedVersion`
- `createdBy`、`updatedBy`、时间戳

同一组织内 `key` 唯一；所有读取和写入必须以当前 `orgId` 过滤。

### 6.2 概念

`OntologyConcept` 表示业务概念，不等同于数据库表：

- `key`、业务名称、复数名称、说明
- `conceptType`: `ENTITY | EVENT`
- `displayPropertyKey`
- 画布坐标 `positionX/positionY`
- `queryable`、`enabled`

V1 不引入继承；可通过关系和标签扩展。

### 6.3 属性

`OntologyProperty` 归属于概念：

- `key`、业务名称、说明
- `dataType`: `TEXT | LONG_TEXT | INTEGER | DECIMAL | BOOLEAN | DATE | DATETIME | ENUM | REFERENCE`
- `required`、`multiple`、`sensitive`、`queryable`
- 枚举值、格式提示和默认展示策略

### 6.4 关系

`OntologyRelation` 连接两个概念：

- `key`、业务名称、说明
- `sourceConceptKey`、`targetConceptKey`
- `cardinality`: `ONE_TO_ONE | ONE_TO_MANY | MANY_TO_ONE | MANY_TO_MANY`
- 正向和反向业务读法
- `queryable`、`enabled`

### 6.5 指标与动作

- `OntologyMetric`：名称、适用概念、聚合类型、度量属性、分组属性、默认时间属性和过滤条件。V1 支持 `COUNT | SUM | AVG | MIN | MAX`。
- `OntologyAction`：名称、适用概念、说明和参数定义。V1 只编译动作契约，不提供执行器。

### 6.6 发布版本

发布时把完整草稿序列化成不可变 `OntologyVersionSnapshot`，包含：

- 版本号和内容哈希
- 完整元模型、映射与契约
- 校验结果摘要
- 发布人和发布时间

运行时只读取已发布快照；草稿修改不能影响线上查询。

## 7. 数据目录与映射

### 7.1 数据源适配器契约

```text
OntologyDataSourceAdapter
  supports(type)
  discoverObjects(context, config)
  discoverFields(context, config, objectKey)
  validateMapping(mapping)
  executeRead(context, physicalQuery)
```

适配器必须接收当前组织和用户上下文，不得自行绕过现有身份、令牌或记录权限。

### 7.2 V1 数据源

- `CLOUDCC`：复用现有组织集成配置和当前用户 CloudCC 会话，发现标准/自定义对象及字段，查询继续服从该用户在 CloudCC 的记录权限。
- `INLINE_SAMPLE`：组织内保存的小型 JSON 示例数据，用于项目交付领域和通用性验收；限制体积和只读操作。

### 7.3 映射模型

V1 支持：

- 概念 → 物理对象
- 属性 → 物理字段
- 关系 → 来源字段与目标字段的等值关联
- 指标 → 已映射数值字段的安全聚合

映射表达式只允许白名单转换：字符串裁剪、大小写、日期解析、数值转换、空值回退和枚举映射。禁止任意代码、SQL 片段和远端 URL。

映射记录必须保存来源、目标、转换、置信度、AI/人工来源、验证状态和最近验证时间。

## 8. AI 建模副驾驶

### 8.1 主流程：领域优先

1. 业务人员填写领域名称、用途、主要对象、常见问题和可选约束。
2. 后端组合严格 JSON 输出提示词，调用组织当前可用模型。
3. AI 返回 `OntologyDraftProposal`，只包含通用元模型字段。
4. 服务端执行结构、命名、引用和安全校验。
5. 页面展示新增、修改、删除差异；用户点击“应用到草稿”后才写入草稿。
6. AI 可以继续根据用户自然语言修改草稿，但每次都产生可审阅提案。

### 8.2 快捷流程：数据源优先

用户可先选择数据源和物理对象，由 AI 根据对象/字段元数据提出业务名称、概念边界、关系和映射。该流程与主流程最终都生成相同的 `OntologyDraftProposal`。

### 8.3 AI 权限边界

- AI 不能发布、归档或删除已发布版本。
- AI 不能创建任意执行表达式、凭证或写回动作。
- AI 响应解析失败、引用不存在或超出限制时，提案整体不应用，并返回可诊断错误。
- 模型不可用时仍允许手工建模、元数据导入和确定性契约编译。

## 9. 可视化业务建模体验

### 9.1 信息架构

管理端新增 `/admin/ontology`：

- 本体列表：名称、说明、草稿修订、线上版本、状态、最近更新。
- 新建领域向导：领域描述优先；数据源反向生成作为次入口。
- 本体工作台：顶部版本/校验/发布，左侧工具栏，中间关系画布，右侧业务属性检查器，底部或抽屉显示 AI 提案、映射与问题。
- 版本浏览：草稿与已发布版本差异、生成契约和发布时间线。

### 9.2 业务化语言

默认界面使用“业务对象、业务属性、关系、业务指标、可执行动作、数据映射”，不向普通用户暴露表、JOIN、SDL、AST 等技术术语。Schema/API 只在“技术预览”中只读展示。

### 9.3 画布行为

- 新增、选择、拖动和删除概念节点。
- 从源概念拖出关系到目标概念，随后在右侧填写业务读法和基数。
- 右侧检查器编辑概念及其属性；所有修改只影响草稿。
- 画布自动保存采用修订号乐观锁；冲突时停止覆盖并提示刷新或复制草稿。
- 键盘可达：节点可聚焦，新增/编辑/删除均有非拖拽替代入口。

界面继承认证后 `鎏金账房` 设计事实：暖象牙底、墨色文字、紧凑密度、香槟金结构线和克制企业工作台语气。

### 9.4 草稿一致性、导航与无障碍门禁

- 业务文档草稿和数据映射草稿都由工作台统一持有；切换“模型 / 数据映射 / AI 建议”等页签不得丢失未保存内容。映射存在未保存修改时，内部目录刷新、数据源发现、AI 提案生成/应用、校验、发布、离开工作台和关闭浏览器都必须被阻止；只有用户明确确认的“重新载入映射”可以丢弃本地行。技术预览只读到权威映射不能把“目录 + 映射工作台”标记为完整加载，映射保存请求只提交服务端允许的白名单字段。
- 管理端使用 React Router 数据路由的正式导航阻断能力，统一覆盖侧栏、退出登录、浏览器后退与前进；页面刷新和关闭窗口使用 `beforeunload` 兜底，不直接篡改浏览器历史栈。
- 前端鉴权作用域由 `orgId + token` 共同标识；任一部分变化时立即清空工作区、向导、映射、AI 提案、版本详情和技术预览等敏感状态。管理壳的用户/组织资料请求和本体工作台异步回写都必须核验鉴权作用域与请求序号；页面卸载时立即使鉴权代次、工作区、数据代次和后续异步步骤失效，过期响应不得回写身份、恢复旧会话或继续发起下一跳变更。
- 新建领域向导在工作区创建成功后立即进入并回读服务端草稿；数据源创建成功后立即以服务端草稿为检查点，再继续元数据发现和 AI 提案。工作区创建响应不确定或返回 `ONTOLOGY_KEY_CONFLICT` 时，前端必须读取当前组织的权威工作区列表，且仅在 `key + name + description + createdBy` 与当前管理员及原请求全部精确相同时恢复；同 key 但请求内容或创建者不同不得接管。无法确认时锁定创建入口并提示先返回列表刷新核对，禁止盲目重试。参考包摘要必须从实际 classpath 包原始字节返回确定性的 `workspaceIdentity(key, name, description)` 与小写 SHA-256 `fingerprint`；参考包安装遇到相同两类错误时也必须读取当前组织权威列表，且只允许恢复元数据身份、当前管理员 `createdBy`、`creationSource=REFERENCE_PACKAGE`、`referencePackageId` 与 `referencePackageFingerprint` 全部精确匹配的工作区。展示标题不能参与身份判断；即使同一管理员手工创建了完全相同的 key/name/description，也不得接管。无法确认时锁定安装入口直到工作区列表成功刷新，其他错误保持原样。数据源创建仍按稳定数据源标识核对。
- 技术预览必须绑定生成时的草稿修订、线上版本和权威映射签名；请求携带 `expectedRevision`，服务端在工作区行锁内校验并在响应中返回 `sourceDraftRevision`，不一致返回 `ONTOLOGY_REVISION_CONFLICT`。前端还必须校验候选版本号等于绑定线上版本的下一版本，避免同修订被他人发布后接受错误候选。业务文档、数据映射、AI 应用、数据源或版本发生变化后旧预览立即失效，任一草稿未保存时禁止生成或复制技术契约。
- 面向业务人员的默认界面只展示连接器名称、中文状态、中文统计、业务名称化的 AI 差异与可操作的问题说明，不暴露适配器键、稳定元素键、内部错误码、对象路径或供应商英文诊断；技术信息只允许出现在受控技术预览中。
- 页签使用标准 `tablist / tab / tabpanel` 语义并支持方向键、Home、End 与焦点跟随，所有页签引用的面板节点始终存在；确认弹窗默认聚焦“取消”。正文和控件字号不低于 11px，关键文本、警告状态与按钮在全部八套主题（含 Galaxy 暗色画布/面板）上的对比度达到 WCAG AA（普通文本至少 4.5:1）。

## 10. 确定性契约编译

### 10.1 JSON Schema

每个概念生成一个 Draft 2020-12 兼容的对象 Schema；关系使用引用或标识字段表达。编译结果带本体 key、版本和内容哈希。

### 10.2 GraphQL SDL

V1 生成只读 SDL 预览：概念类型、关系字段、过滤输入、排序输入和查询根。SDL 不直接启用任意 GraphQL 执行端点；实际执行仍通过受限语义查询 API，避免绕过权限和查询预算。

### 10.3 语义查询契约

```json
{
  "ontologyKey": "customer-operations",
  "version": 1,
  "concept": "customer",
  "select": ["name", "industry"],
  "filters": [{"property": "status", "operator": "EQ", "value": "ACTIVE"}],
  "orderBy": [{"property": "name", "direction": "ASC"}],
  "limit": 50
}
```

V1 运算符限定为 `EQ | NE | IN | CONTAINS | GT | GTE | LT | LTE | BETWEEN | IS_NULL`，关系最多一跳，默认 50 条、硬上限 200 条，不允许全表无界扫描。

## 11. 查询执行与证据

执行顺序：

1. 解析当前组织和用户。
2. 定位明确的已发布本体版本。
3. 校验概念、属性、操作符、查询预算和敏感字段权限。
4. 从发布快照解析物理映射。
5. 生成数据源无关 `PhysicalQueryPlan`。
6. 由匹配的适配器编译和执行只读请求。
7. 将物理字段归一化为业务属性。
8. 返回结果、版本、数据源、物理对象、映射和耗时证据。
9. 写入查询审计；不记录凭证，敏感过滤值按策略脱敏。

若一个查询跨越多个数据源，V1 明确返回 `CROSS_SOURCE_QUERY_NOT_SUPPORTED`；跨源联邦执行留到后续版本。

## 12. 持久化模型

Flyway V82 新增以下 13 张表：

- `ontology_workspace`
- `ontology_concept`
- `ontology_property`
- `ontology_relation`
- `ontology_metric`
- `ontology_action`
- `ontology_data_source`
- `ontology_physical_object`
- `ontology_physical_field`
- `ontology_mapping`
- `ontology_ai_proposal`
- `ontology_version`
- `ontology_query_audit`

所有业务表包含 `org_id`；工作区子资源通过工作区归属二次校验组织。组织导出、删除和保留策略必须纳入新表，防止租户销户后残留。

V82 一经审查不得回改。正向 V83 只扩展 `ontology_workspace`，不增加第 14 张表：

- `creation_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL'`
- `reference_package_id VARCHAR(128)`
- `reference_package_fingerprint CHAR(64)`
- CHECK 约束强制 `MANUAL` 对应两个引用字段都为 NULL；`REFERENCE_PACKAGE` 对应包 ID 非空，且 SHA-256 必须是 64 位小写十六进制字符串。

普通工作区创建必须持久化 `MANUAL / NULL / NULL`；参考包安装必须持久化 `REFERENCE_PACKAGE / 实际包 ID / 实际包原始字节 SHA-256`。这三项是不可由工作区名称或说明推断的创建 provenance。

## 13. 后端 API

管理 API 使用 `/admin/ontologies`：

- 工作区：列表、创建、读取、更新、归档；仅组织管理员可见的工作区 DTO 返回 `createdBy`、`creationSource`、`referencePackageId`、`referencePackageFingerprint`，用于结果未知时核对创建身份与来源，不作为普通查询接口字段。
- 草稿：读取/批量保存、乐观锁、校验、差异。
- AI：创建提案、读取提案、应用提案。
- 数据目录：创建数据源、发现对象、发现字段、保存示例数据。
- 映射：读取、替换、验证。
- 编译：JSON Schema、GraphQL SDL、语义查询契约预览；`POST /{workspaceId}/compile-preview` 必须提交 `expectedRevision`，响应返回 `sourceDraftRevision` 与候选 `version`，服务端在同一事务的工作区行锁内校验后编译；客户端只接受 `sourceDraftRevision` 精确匹配且候选版本等于绑定线上版本下一版本的响应。
- 发布：发布草稿、版本列表、版本详情。

运行 API 使用 `/semantic-query`：

- `POST /semantic-query/execute` 执行结构化只读查询。
- `POST /semantic-query/explain` 只生成和返回执行计划，不访问数据源。

所有写管理 API 要求组织管理员权限；查询 API 要求组织成员身份并继续服从数据源侧用户权限。

## 14. 错误处理

稳定错误码至少包括：

- `ONTOLOGY_NOT_FOUND`
- `ONTOLOGY_REVISION_CONFLICT`
- `ONTOLOGY_KEY_CONFLICT`
- `ONTOLOGY_VALIDATION_FAILED`
- `ONTOLOGY_PUBLISH_REQUIRES_HUMAN`
- `AI_PROPOSAL_INVALID`
- `AI_MODEL_UNAVAILABLE`
- `DATA_SOURCE_UNAVAILABLE`
- `MAPPING_INVALID`
- `QUERY_CONTRACT_INVALID`
- `QUERY_BUDGET_EXCEEDED`
- `SENSITIVE_PROPERTY_FORBIDDEN`
- `CROSS_SOURCE_QUERY_NOT_SUPPORTED`

外部数据源或 AI 失败不能损坏草稿；发布使用事务，在快照、版本号和工作区状态全部成功后提交。

工作区创建与参考包安装以 V82 的 `uq_ontology_workspace_org_key` 为最终一致性约束。服务端在保存后显式 flush，并且只把该约束的并发唯一键异常翻译为 HTTP 409 / `ONTOLOGY_KEY_CONFLICT`；其他完整性错误保持原异常语义，禁止统一伪装成 key 冲突。

参考包列表响应必须暴露由实际包文档生成的只读 `workspaceIdentity`，以及由实际 classpath JSON 原始字节生成的稳定 SHA-256 `fingerprint`。安装结果未知时必须把二者与当前组织权威工作区的管理员、创建来源、包 ID 和包指纹全部核对；这些字段不是请求幂等键，也不授权接管由其他管理员或手工流程创建的同 key 工作区。

## 15. 安全与治理

- 严格按 `org_id` 隔离工作区、目录、提案、版本和审计。
- CloudCC 访问复用当前用户会话，不降级到组织级超级令牌。
- 数据源配置只保存引用或受现有集成密钥机制保护的配置；API 不回显秘密。
- 发布动作记录操作者、来源草稿修订、校验摘要和内容哈希。
- 查询只允许已发布版本、白名单运算符、限制字段和限制条数。
- `sensitive=true` 的属性默认不可查询，只有显式授权后开放；审计结果脱敏。
- AI 提示只传业务描述和允许使用的元数据，不传数据源凭证和未授权业务记录。

## 16. 首期领域包

### 16.1 CloudCC CRM 参考包

领域包以普通数据定义和映射模板存在，内核中不出现 CRM 类型分支。初始概念：

- 客户 `customer`
- 联系人 `contact`
- 商机 `opportunity`
- 产品 `product`
- 互动 `interaction`
- 经营动作 `business_action`

实际映射以目标组织发现到的 CloudCC 对象和字段为准；模板只提供候选映射，发布前必须验证。

### 16.2 项目交付示例包

内置 `project / task / owner` 示例和少量 JSON 数据。验收必须证明：

- 可由相同 API 和画布创建。
- 可生成同样的 Schema、SDL 和查询契约。
- 可执行按状态查询任务、按项目查询任务等只读语义查询。
- 不新增领域专属 Java 或 TypeScript 分支。

## 17. 实施切片

V1 作为一个产品里程碑，但按可验证切片交付：

详细的 TDD 实施步骤、文件职责、接口和验证命令见 `docs/specs/FEAT-118-general-ontology-modeling-platform-plan.md`，它是本规格的实施计划附件。

1. 通用元模型、V82、租户生命周期与版本快照。
2. 工作区、草稿、校验、发布和编译 API。
3. 数据目录、映射和 `INLINE_SAMPLE` 适配器。
4. CloudCC 元数据与只读查询适配器。
5. AI 提案生成、校验、差异和应用。
6. 管理端列表、向导、可视化画布、检查器、映射和发布。
7. 两套领域包、查询审计、端到端与生产验收。

## 18. 验收标准

### 18.1 通用性

- 本体核心代码没有 CloudCC、客户、商机等领域标识符。
- CRM 与项目交付两套领域可通过相同元模型、API、画布和发布流程运行。
- 新增第三个领域无需修改后端元模型和前端画布代码。

### 18.2 业务建模

- 业务人员可以从领域描述创建 AI 草稿，并在画布上增删改概念、属性和关系。
- AI 修改只形成提案；未点击应用不改变草稿，未人工发布不改变线上版本。
- 草稿自动保存具有修订冲突保护。
- 校验问题能定位到具体概念、属性、关系或映射。

### 18.3 编译与运行

- 已发布版本可生成确定性 JSON Schema、GraphQL SDL 和语义查询契约。
- `INLINE_SAMPLE` 的项目任务查询真实返回映射后的业务字段与证据。
- CloudCC 参考领域至少完成对象/字段发现、映射验证和一条当前用户权限下的只读查询。
- 未发布草稿、敏感属性、越界 limit、未知字段和跨源查询均被拒绝。

### 18.4 安全与隔离

- 集成测试证明组织 A 不能读取、修改、发布、查询组织 B 的本体或目录。
- 并发创建同组织同 key 时仅一条工作区落库，另一请求稳定返回 409 / `ONTOLOGY_KEY_CONFLICT`；结果未知后的客户端核对不得接管其他管理员创建的工作区。
- AI 提案不能调用发布 API。
- 所有查询均有本体版本、数据源、映射和操作者审计，不保存明文秘密。

### 18.5 产品质量

- 后端 focused 测试、真实 PostgreSQL/Flyway 集成测试通过。
- 前端单元测试与生产构建通过。
- `/admin/ontology` 在桌面端完成列表、向导、画布、检查器、映射、AI 提案、校验和发布交互检查。
- 桌面截图无外层横向溢出，浏览器控制台无 error/warning。
- `git diff --check`、状态文件验证、发布 dry-run、生产备份、部署和生产 smoke 全部有真实证据。

## 19. 发布与回滚

- 生产发布严格遵循 `docs/production-release-runbook.md` 与 `scripts/release-acr.sh`。
- 发布前运行 `./scripts/release-acr.sh --dry-run`，版本号、镜像 tag、Git tag 和前后端版本保持一致。
- 生产数据库从当前 V81 依次应用 V82 与 V83：V82 新增 13 张表，V83 只为 `ontology_workspace` 增加 provenance 列和 CHECK 约束。应用回滚时这些表和列保留但不被旧版本读取。
- 上线后 smoke 覆盖健康检查、管理端路由、创建/发布示例本体、示例查询、组织隔离和 CloudCC 发现诊断。
- 若 AI 模型或 CloudCC 暂不可用，手工建模、编译和 `INLINE_SAMPLE` 查询仍必须可用；对应适配器明确显示降级状态。

## 20. 设计自检结论

- 无待定占位：V1 范围、运行边界、错误、权限、验收和回滚均已明确。
- 无领域耦合：CloudCC 只存在于适配器与领域包，不进入通用元模型。
- 无发布歧义：AI 永远只写提案/草稿，人工发布是唯一线上变更入口。
- 无执行歧义：V1 仅支持已发布版本上的受限只读语义查询，动作只有契约。
- 范围已拆片：七个实施切片可以独立测试，但共同构成一个可上线的 V1。

## 21. V1 生产交付事实

### 21.1 版本与运行态

- PR #13 合并提交 `f922b86f1884ec5f7b7e1d97d3d0558202d0180f` 已发布为不可变生产版本 `2.7.10`。
- 生产从 V81 顺序应用 V82/V83，均 `success=true`；V82 新增 13 张 ontology 表，V83 只增加工作区 provenance 列与 CHECK 约束，V82 checksum 保持不变。
- 发布前备份为 `/opt/cici/backups/20260717-154253-before-2.7.10-task213-ontology`；部署只强制重建 backend/frontend，四个状态服务容器 ID 完全保持不变。
- 480 秒内 17 次采样始终为六服务 healthy、重启 0、OOM 0、health `UP`、版本 `2.7.10 / f922b86f1884 / 2.7.10`，backend `ERROR|Exception` 为 0。应用即时回滚点为 `2.7.9 / c04e992b3840`，V82/V83 可保留。

### 21.2 通用领域闭环

- 生产 `project-delivery` 由同一参考包/元模型/API/画布完成对象和字段发现、15/15 映射验证、候选编译、人工发布及重复发布幂等校验；线上不可变版本为 v1，来源草稿修订为 6。
- `semantic-query` explain 生成 `projects` 与 `contains-task` 查询计划；execute 返回 1 个项目和 2 个关联任务，并携带本体版本 1、总数 1 的证据。另一组织执行同一查询返回 404；审计只保存 `REDACTED`，不保存过滤明文。
- 生产 1600×1000 浏览器验证列表、3 节点/2 关系画布、映射、JSON Schema、GraphQL SDL、查询契约和不可变版本历史；全部工作区/技术页签 IDREF 有效，console error/warning 与 document/body 横向溢出均为 0。
- 生成态 `frontend/vite.config.js` 已与 TypeScript 事实源同步；全新检出后直接 `npm run dev` 会代理 `/admin/ontologies/**` 和 `/semantic-query/**`，不会误返回 SPA HTML。

### 21.3 CloudCC 首期落地边界

- `customer-operations` 参考包已在两个演示组织以 `REFERENCE_PACKAGE + packageId + 原始包 bytes SHA-256` 安装为可编辑草稿，证明 CRM 领域仍通过普通领域包进入通用内核。
- 两个当前可用密码登录用户均不能取得有效的 CloudCC 当前用户会话，对象发现因此明确返回 `502 DATA_SOURCE_UNAVAILABLE`。两次失败均未修改 CloudCC、未损坏草稿、未执行映射校验或发布；Nginx 除这两次受控诊断外没有其他 5xx。
- CloudCC 适配器的发现、字段解析、映射验证、当前用户只读查询与失败封装已由聚焦/集成测试覆盖。恢复有效的用户绑定后，应直接续跑生产真实目录、映射和查询验收；禁止降级为组织级超级令牌或绕过当前用户权限。

### 21.4 质量边界

- 全新 PostgreSQL 相关后端回归 127/127、前端 26 个文件 / 177 项、前端生产构建和后端 package 通过；独立安全与规格终审均为 Approved，Critical 0 / Important 0。
- 后续保留两项非阻塞 Minor：补充 RouterProvider + deferred Promise 挂载级异步测试；扩大修改、目录和发布 API 的参数化跨租户 404 覆盖。
- V1 已按本规格上线并关闭 TASK-213。OWL/RDF 全集、复杂推理、跨源联邦、动作写回、移动端和其他 V2 能力不在本次完成范围内。
