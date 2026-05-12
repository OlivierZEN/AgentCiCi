---
kind: feature-spec
feature_id: FEAT-028
title: File Backed Builtin Skills
status: implemented
owner_role: backend-agent-runtime
task_ids: TASK-079
related_decisions: FEAT-009, FEAT-014, FEAT-015
related_issues: none
updated_at: 2026-05-11T09:32:50Z
updated_by: ai
---

# FEAT-028 - 文件型内置标准技能

## 背景与目标

Cici 需要把 `cloudcc-customization-expert-common` 这类 CloudCC CRM 二次开发专家技能做成系统内置标准技能，供所有组织共用。

该技能包含大量官方模块文档，例如对象、字段、权限、触发器、类、脚本、菜单、应用、视图、自定义设置、定时作业与定时类等模块的 `introduction.md`、`devguide.md`、`api.md`。这些内容属于平台官方能力说明和接口契约，不适合按组织复制到数据库，也不适合混入租户知识库向量化存储。

本功能目标是新增一套 **文件型内置标准技能** 机制：

- 内置标准技能包以文件目录形式随应用代码发布。
- 多组织共享同一份只读文件资源。
- 数据库只存轻量治理索引、版本/checksum、组织启用状态和 Agent 绑定关系。
- 运行时按用户意图和模块需要加载相关文档片段，而不是一次性把全部文档塞进 prompt。
- 保持与现有 `PLATFORM_STANDARD`、`platform_skill_template`、`skill_definition`、`skill_version` 和 `agent_skill_binding` 兼容。
- 允许后续把标准技能派生为组织自定义技能，但派生只复制必要的 prompt/spec 配置，不复制全部官方 reference 文档。

## 范围

### In Scope

- 新增应用内文件型内置技能目录约定。
- 支持 `cloudcc-customization-expert-common` 作为首个文件型内置标准技能。
- 应用启动时扫描 classpath 中的内置技能包并同步平台模板索引。
- 数据库记录技能元数据、版本号、checksum、目录 URI 和治理状态，不存储大文档正文。
- 租户侧可查看、启用/停用和绑定该平台标准技能。
- 运行时根据模块意图加载该技能相关 `SKILL.md` 与模块文档。
- 支持文档模块导航：优先 `introduction.md`，需要实现/联调时加载 `devguide.md` 和 `api.md`。
- 支持文件资源 checksum 检查，确保数据库索引与代码包版本可追踪。
- 支持本地开发、Docker 镜像、ACR 发布和 ECS 部署环境读取同一份 classpath 资源。
- 新增最小后端测试覆盖：启动扫描、模板同步、模块文档读取、租户运行时解析。

### Out Of Scope

- 不把 CloudCC 官方文档正文保存到 PostgreSQL。
- 不把该技能文档导入租户知识库或 Qdrant。
- 不允许租户直接编辑文件型标准技能正文。
- 不支持租户上传任意文件目录成为平台内置技能。
- 不在第一版实现可视化文档编辑器。
- 不在第一版实现完整灰度发布和逐组织模板升级差异合并。
- 不替代 FEAT-015 的 Skill 内嵌声明式 API 运行时。
- 不把文件型技能变成 Codex 本地 skill 的自动安装机制；这是 Cici 应用自己的内置标准技能机制。

## 用户场景

- 平台维护者把 CloudCC 官方二次开发文档随 Cici 应用代码发布，所有组织自动获得同一份标准能力。
- 组织管理员在 Agent Builder 或技能库中看到“CloudCC 二次开发专家”标准技能，将其绑定到售前、实施或平台运维 Agent。
- 用户询问“如何创建 CloudCC 自定义对象”“触发器接口怎么写”“字段接口有哪些必填参数”时，运行时自动加载对应模块的官方文档片段。
- 用户要求生成接口设计或代码时，运行时先加载模块 `introduction.md`，再按需要加载 `devguide.md` 与 `api.md`，避免只输出概述。
- 平台升级 Cici 镜像后，内置技能文件版本随代码更新。数据库记录新的 checksum 和版本索引，历史运行 trace 仍能追踪当时使用的模板版本。

## 现状与约束

### Verified Facts

- 当前系统已经有 `skill_definition`、`skill_version`、`agent_skill_binding`、`platform_skill_template` 和 `platform_skill_template_version`。
- FEAT-009 已定义 `PLATFORM_STANDARD`、`TENANT_DERIVED`、`TENANT_CUSTOM`、平台模板和运行时 pin 的治理模型。
- FEAT-014 已把租户自定义技能的草稿/发布/版本恢复作为治理方向。
- FEAT-015 已把 Skill 内嵌声明式 API 运行时定义为独立能力，不应与文件型文档资源混淆。
- 当前 `SkillDefinitionService.BUILTIN_SKILLS` 仍以 Java 常量形式定义一批小型内置技能，适合 prompt 片段，不适合承载大量模块文档。
- `cloudcc-customization-expert-common` 当前目录形态已经接近标准 skill 包：根部 `SKILL.md`，模块目录下有 `introduction.md`、`devguide.md` 和 `api.md`。

### Key Constraint

文件型内置技能必须区分 **运行策略** 和 **参考文档**：

| 层级 | 存放位置 | 是否进数据库 | 是否进入 prompt | 说明 |
|---|---|---:|---:|---|
| 技能元数据 | `manifest.json` / `SKILL.md` | 轻量索引 | 部分 | 名称、描述、触发、版本、模块清单 |
| 核心运行策略 | `SKILL.md` / compiled prompt | 是 | 是 | 触发规则、执行规则、质量标准 |
| 模块参考文档 | `<module>/introduction.md` 等文件 | 否 | 按需 | 官方模块说明和接口契约 |
| 组织配置 | 数据库 | 是 | 部分 | 启用状态、绑定关系、派生关系 |
| 组织凭证/工具 | 数据库或密钥服务 | 是 | 否 | CloudCC token、OpenAPI base URL、工具授权 |

第一版必须避免两个方向的误用：

- 不能为了多组织共享而把所有官方文档复制到每个组织的数据库。
- 不能为了检索方便而把平台官方文档混进租户知识库，导致权限、版本和来源边界不清。

## 方案设计

### 0. CloudCC 二次开发运行配置

`cloudcc-customization-expert-common` 需要把 CloudCC Setup API 服务根地址与当前用户 CloudCC 鉴权绑定成一组运行配置：

- `setupSvc` 来源于当前组织 `CloudCC CRM` 集成应用的 `orgapi_switch_address` 解析结果，即 CloudCC 组织 API 网关。
- 若组织 API 网关路径包含 `lightningapi`，运行时把该 path segment 替换为 `setup`，例如 `https://ap10.apis.cloudcc.cn/lightningapi` -> `https://ap10.apis.cloudcc.cn/setup`。
- 若组织 API 网关不包含 `lightningapi` path，运行时在尾部追加 `/setup`，例如 `https://ap10.apis.cloudcc.cn` -> `https://ap10.apis.cloudcc.cn/setup`。
- 当前用户绑定的 CloudCC 账号通过 `cc_username` + `cc_safetymark` 换取 `accessToken`；token 只进入服务端 CloudCC 工具调用的 `accessToken` 请求头，不以明文写入模型 prompt、trace 或前端 payload。
- 当 CloudCC 二次开发专家技能被激活，或其文件型参考文档在本轮命中时，system prompt 会注入 `setupSvc` 与 “accessToken 可由服务端凭证绑定注入” 的运行说明，指导模型使用已批准的 CloudCC 工具而不是向用户索要 token。
- 若组织集成应用、用户 CloudCC 绑定或 token 换取失败，运行时配置区会明确标记不可用，技能应提示用户先完成集成应用与账号绑定。

### 1. 文件目录约定

应用内置技能放在后端 classpath 资源目录：

```text
backend/src/main/resources/builtin-skills/
└── cloudcc-customization-expert-common/
    ├── manifest.json
    ├── SKILL.md
    ├── README.md
    ├── object/
    │   ├── introduction.md
    │   └── api.md
    ├── fields/
    │   ├── introduction.md
    │   └── api.md
    ├── classes/
    │   ├── introduction.md
    │   ├── devguide.md
    │   └── api.md
    └── triggers/
        ├── introduction.md
        ├── devguide.md
        └── api.md
```

资源路径规范：

- 技能目录名必须等于 `skillCode`。
- `SKILL.md` 必须存在。
- `manifest.json` 必须存在。
- 模块目录名使用小写、数字、连字符或当前 CloudCC 既有驼峰模块名；后端读取时保持文件名原样，路由匹配时可做 alias。
- 模块文档只允许 `introduction.md`、`devguide.md`、`api.md`、`examples.md` 四类；第一版只要求前三类。
- Docker 镜像构建必须把 `backend/src/main/resources/builtin-skills` 打入 Spring Boot artifact。

### 2. manifest.json

每个文件型内置技能必须提供 manifest：

```json
{
  "schemaVersion": 1,
  "skillCode": "cloudcc-customization-expert-common",
  "name": "CloudCC 二次开发专家",
  "description": "CloudCC CRM 二次开发设计与实施。适用于对象、字段、权限、触发器、类、脚本、菜单、应用、视图、自定义设置、定时作业与定时类等模块。",
  "category": "cloudcc-development",
  "sourceType": "PLATFORM_STANDARD",
  "visibility": "VISIBLE",
  "editPolicy": "CONFIGURABLE",
  "bindingPolicy": "OPTIONAL",
  "updatePolicy": "AUTO",
  "riskLevel": "MEDIUM",
  "version": 1,
  "documentRoot": ".",
  "entrypoint": "SKILL.md",
  "defaultActivationMode": "intent-route",
  "modules": [
    {
      "code": "object",
      "name": "对象",
      "files": ["introduction.md", "api.md"],
      "triggerHints": ["对象", "自定义对象", "object"]
    },
    {
      "code": "fields",
      "name": "字段",
      "files": ["introduction.md", "api.md"],
      "triggerHints": ["字段", "自定义字段", "fields"]
    }
  ]
}
```

字段规则：

| 字段 | 说明 |
|---|---|
| `schemaVersion` | manifest schema 版本，第一版为 `1` |
| `skillCode` | 技能编码，必须等于目录名 |
| `name` / `description` | 平台技能库和运行时摘要展示 |
| `sourceType` | 第一版固定 `PLATFORM_STANDARD` |
| `editPolicy` | 第一版建议 `CONFIGURABLE`，租户可启停但不可编辑正文 |
| `bindingPolicy` | `OPTIONAL` 或后续 `DEFAULT_ON` |
| `updatePolicy` | 文件随应用升级，第一版固定 `AUTO` |
| `version` | 文件型技能包版本号，由平台维护者递增 |
| `entrypoint` | 核心技能说明文件，通常为 `SKILL.md` |
| `modules` | 可按需加载的文档模块清单 |

checksum 不由维护者手写。启动扫描时后端按文件内容生成：

- `bundleChecksum`: 整个技能目录内容 hash。
- `entrypointChecksum`: `SKILL.md` hash。
- `moduleChecksums`: 每个模块文件 hash。

### 3. 启动扫描与索引同步

新增 `FileBackedBuiltinSkillCatalog`：

职责：

1. 扫描 `classpath:/builtin-skills/*/manifest.json`。
2. 校验目录名、manifest、`SKILL.md` 和模块文件存在性。
3. 计算 bundle checksum。
4. 暴露按 `skillCode`、模块和文件类型读取内容的 API。
5. 返回只读 `FileBackedBuiltinSkillBundle` 模型。

新增 `FileBackedBuiltinSkillSyncService`：

职责：

1. 应用启动后同步文件型内置技能索引。
2. 为每个 bundle 创建或更新 `platform_skill_template`。
3. 为每个新版本或 checksum 变化创建 `platform_skill_template_version`。
4. 为已存在组织补齐 `skill_definition` 中对应 `PLATFORM_STANDARD` 逻辑对象。
5. 不把模块文档正文写入数据库。

同步策略：

- 如果 `template_code + version` 不存在，创建新模板版本。
- 如果 version 相同但 checksum 不同，启动时记录 error 并拒绝覆盖，要求维护者递增 manifest version。
- 如果 version 更高，创建新模板版本并更新 `current_version_no`。
- 第一版可以只对已存在组织在 `ensurePhaseOneDefaults(orgId)` 时懒同步该组织的 `skill_definition`，避免启动时扫描全部组织。

### 4. 数据模型影响

优先复用现有表，新增最小字段。

#### platform_skill_template

建议新增字段：

```text
resource_type VARCHAR(32) DEFAULT 'INLINE'
resource_uri VARCHAR(512)
bundle_checksum VARCHAR(128)
```

含义：

- `resource_type=INLINE`：现有数据库内联标准技能。
- `resource_type=FILE_BACKED`：文件型内置标准技能。
- `resource_uri=classpath:/builtin-skills/cloudcc-customization-expert-common`
- `bundle_checksum`：当前模板版本对应的文件包 hash。

#### platform_skill_template_version

建议新增字段：

```text
resource_uri VARCHAR(512)
bundle_checksum VARCHAR(128)
entrypoint_checksum VARCHAR(128)
module_manifest_json TEXT
```

`module_manifest_json` 只存模块清单、文件名、checksum 和触发 hint，不存正文。

#### skill_definition

复用：

- `source_type=PLATFORM_STANDARD`
- `template_code=cloudcc-customization-expert-common`
- `base_template_version=<manifest.version>`
- `prompt_fragment` 存 `SKILL.md` 中核心执行规则的压缩版或编译版。
- `draft_spec_text` 可存只读摘要，不存所有模块文档。

第一版不需要新增 `skill_asset` 表。若后续需要外部对象存储或可上传资产，再引入 `SkillAsset`。

### 5. 运行时解析与按需加载

新增 `BuiltinSkillDocumentService`：

核心方法：

```java
ResolvedBuiltinSkillDocs resolveDocs(
    String skillCode,
    String userMessage,
    String activeSkillCode,
    List<String> explicitModuleHints
)
```

第一版模块识别策略：

1. 如果前端或 Agent workflow 显式传入模块 hint，优先使用。
2. 否则根据 manifest `triggerHints` 对用户消息做关键词匹配。
3. 如果无法识别模块，只加载 `SKILL.md` 和模块列表摘要。
4. 如果识别到模块，先加载该模块 `introduction.md`。
5. 当用户意图包含“接口、参数、API、请求体、返回、联调、实现、代码、创建、更新、删除”等词时，再加载 `api.md`。
6. 当模块存在 `devguide.md` 且用户意图包含“开发、规范、限制、最佳实践、触发器、类、代码”等词时，加载 `devguide.md`。
7. 每轮最多加载 3 个模块，避免 prompt 爆炸。

文档注入位置：

- `SkillPromptAssembler` 继续负责装配已绑定技能 prompt。
- 对 `FILE_BACKED` 技能，额外追加一个 `Reference documents for active builtin skill` 区块。
- 区块必须标明来源：`cloudcc-customization-expert-common/object/api.md@v1`。
- trace 中记录 `fileBackedSkillRefs`，包括 skillCode、version、module、file、checksum。

示例 prompt 片段：

```text
Reference documents for active builtin skill:
- Source: cloudcc-customization-expert-common/object/introduction.md@v1
  ...
- Source: cloudcc-customization-expert-common/object/api.md@v1
  ...
```

### 6. 与知识库和 RAG 的边界

文件型内置技能不是租户知识库。

边界规则：

- 不进入 `knowledge_base`。
- 不进入 `kb_document`。
- 不进入 Qdrant。
- 不受组织知识库启停影响。
- 不暴露为用户上传文档。
- 不参与普通知识库召回评分。
- 运行 trace 中以 `builtin_reference` 类型展示，而不是 `kb_document`。

原因：

- 这些文档是平台官方能力，不是租户私有知识。
- 文件版本由应用发版控制，需要可审计和可回滚。
- CloudCC API 参数等内容需要确定性读取，不能依赖向量召回误差。
- 多组织共享时复制到每个组织会带来存储、升级和一致性负担。

### 7. 租户治理与 UI 语义

租户管理端技能库中，该技能显示为：

- 类型：平台标准
- 来源：系统内置文件
- 状态：可启用/停用
- 编辑：只读
- 绑定：可绑定到 Agent
- 更新：随平台升级

详情页展示：

- 名称、描述、版本、分类、风险等级。
- 模块列表和模块说明摘要。
- 最近更新时间、bundle checksum 短码。
- 只读提示：`该技能由平台随应用代码维护，组织不能直接编辑官方文档。`

不展示：

- 大段全部模块文档正文。
- 保存草稿、发布、删除、导出、编辑正文。
- 派生入口第一版隐藏，保持 FEAT-014 口径。

平台端后续可增加：

- 文件型内置技能列表。
- manifest 校验状态。
- 已安装版本和 checksum。
- 哪些组织启用了该技能。
- 哪些 Agent 绑定了该技能。

第一版可先只做后端能力和最小列表展示。

### 8. CloudCC 技能包首版落地

首个目标包：

```text
/Volumes/workspace/AI/skills/cloudcc-customization-expert-common
```

复制到：

```text
backend/src/main/resources/builtin-skills/cloudcc-customization-expert-common
```

落地前需要修正：

- `SKILL.md` 中 `文档根目录：cloudcc-skills/<module>/` 改为 `文档根目录：本技能目录下的 <module>/`。
- `SKILL.md` 模块列表中 `project`、`config` 若无对应目录，应移除或在 manifest 标记为 `planned`，避免运行时误读。
- 补 `manifest.json`。
- 保留 `README.md` 作为维护者说明，但运行时默认不加载。

首版建议模块清单以实际存在目录为准：

- `application`
- `brief`
- `button`
- `classes`
- `customPage`
- `customSetting`
- `fields`
- `menu`
- `object`
- `pagelayout`
- `permission`
- `profile`
- `recordType`
- `role`
- `scheduleJob`
- `script`
- `timer`
- `triggers`
- `user`
- `validationRule`
- `view`

## 接口与数据影响

### 后端服务

新增服务：

- `FileBackedBuiltinSkillCatalog`
- `FileBackedBuiltinSkillSyncService`
- `BuiltinSkillDocumentService`

调整服务：

- `SkillDefinitionService.ensurePhaseOneDefaults(orgId)`：补齐文件型标准技能逻辑对象。
- `SkillResolverService`：在 resolved skill refs 中识别 `FILE_BACKED` 模板。
- `SkillPromptAssembler`：支持追加按需文档片段。
- `PlatformGovernanceService`：模板版本管理支持 `resource_type=FILE_BACKED`。

### API

第一版可不新增公网 API。若管理端需要查看模块摘要，可扩展：

```text
GET /skills/{id}/builtin-docs
GET /skills/{id}/builtin-docs/{moduleCode}
```

限制：

- 仅组织管理员可查看。
- 只返回模块清单、文件名、摘要和 checksum。
- 不默认返回全部正文，避免前端一次拉取过大内容。

平台端后续可扩展：

```text
GET /api/platform/file-backed-skills
GET /api/platform/file-backed-skills/{skillCode}/validation
```

### 数据库迁移

新增迁移建议：

```sql
ALTER TABLE platform_skill_template
    ADD COLUMN IF NOT EXISTS resource_type VARCHAR(32) DEFAULT 'INLINE',
    ADD COLUMN IF NOT EXISTS resource_uri VARCHAR(512),
    ADD COLUMN IF NOT EXISTS bundle_checksum VARCHAR(128);

ALTER TABLE platform_skill_template_version
    ADD COLUMN IF NOT EXISTS resource_uri VARCHAR(512),
    ADD COLUMN IF NOT EXISTS bundle_checksum VARCHAR(128),
    ADD COLUMN IF NOT EXISTS entrypoint_checksum VARCHAR(128),
    ADD COLUMN IF NOT EXISTS module_manifest_json TEXT;
```

索引：

```sql
CREATE INDEX IF NOT EXISTS idx_platform_skill_template_resource_type
    ON platform_skill_template(org_id, resource_type);
```

## 任务拆分

### TASK-079 File-backed builtin skills specification

- status: draft
- owner_role: backend-agent-runtime
- scope: 本文档沉淀文件型内置标准技能架构、数据边界、运行时加载策略和 CloudCC 首包落地范围。

### 后续实现任务建议

- `TASK-080`: 新增 classpath 文件型技能目录、复制 CloudCC 技能包、补 manifest。
- `TASK-081`: 实现 `FileBackedBuiltinSkillCatalog` 和 manifest/checksum 校验。
- `TASK-082`: 实现数据库模板索引同步和组织级 `skill_definition` 懒初始化。
- `TASK-083`: 实现 `BuiltinSkillDocumentService` 与运行时按需文档注入。
- `TASK-084`: 管理端标准技能只读详情补文件型技能模块摘要。
- `TASK-085`: 增加后端集成测试和 Docker artifact 资源校验。

## 验收标准

功能验收：

- `cloudcc-customization-expert-common` 能以文件目录形式打入后端应用。
- 应用启动后能扫描到该技能，并创建/更新平台模板索引。
- 任一组织首次访问技能列表时能看到该平台标准技能。
- 组织管理员可以将该技能绑定到 Agent。
- 用户询问 CloudCC 对象/字段/触发器/类相关问题时，运行时只加载相关模块文档。
- 数据库中不保存 CloudCC 模块文档正文。
- trace 能看到本轮加载的文件型内置技能来源、模块、文件和版本。

技术验收：

- 后端测试覆盖 manifest 读取、checksum 生成、模板同步、模块文档加载。
- `mvn -q -Dmaven.repo.local=.m2 -Dtest=FileBackedBuiltinSkill* test` 成功。
- `mvn -q -Dmaven.repo.local=.m2 -DskipTests compile` 成功。
- Docker 构建后 jar 内包含 `builtin-skills/cloudcc-customization-expert-common/manifest.json`。
- 目标迁移在空库和已有库上均可重复执行。

安全与隔离验收：

- 租户无法通过 API 修改文件型内置技能正文。
- 租户无法读取文件系统任意路径，只能读取 manifest 声明的 classpath 资源。
- 文档加载不使用用户传入的原始路径拼接，必须通过 manifest module code 映射。
- CloudCC 凭证仍来自组织集成配置或工具鉴权，不写入技能文件。

## 风险与回滚

### 风险

- 文档包过大导致 prompt 超长。
- manifest 版本未递增但文件内容变化，造成线上版本追踪混乱。
- 模块关键词匹配不准，导致加载错误模块。
- Docker/Jar 打包遗漏 `builtin-skills` 资源。
- 将平台文档误当租户知识库处理，破坏版本和权限边界。

### 缓解

- 每轮限制最多加载 3 个模块，每个文件做字符数上限和摘要裁剪。
- checksum 变化但 version 未变时启动报错或至少输出高优先级告警。
- 模块识别第一版使用 manifest hint + 明确关键词，后续可加入轻量分类器。
- 增加 artifact 资源存在性测试。
- 数据模型明确 `resource_type=FILE_BACKED`，运行 trace 使用 `builtin_reference`。

### 回滚

- 如果文件型技能扫描异常，可通过配置关闭该技能包同步：
  - `cici.skills.file-backed.enabled=false`
  - 或 `cici.skills.file-backed.disabled-codes=cloudcc-customization-expert-common`
- 已同步的 `skill_definition` 可保留但设为 disabled。
- 回滚应用镜像即可回到上一版本文件资源。
- 数据库新增字段保持向后兼容，不影响现有 inline 内置技能。

## 实现进展

- 2026-05-11: 已完成架构规格草稿。
- 2026-05-11: 已完成第一版实现与验证。
  - 已复制 CloudCC 技能包到 `backend/src/main/resources/builtin-skills/cloudcc-customization-expert-common/`。
  - 已修正 `SKILL.md` 文档根目录描述并移除无实际目录的 `project` / `config` 模块。
  - 已新增 `manifest.json`，模块清单以实际存在目录和文件为准。
  - 已新增 `V48__file_backed_builtin_skills.sql`，模板和模板版本保存 FILE_BACKED resource URI、checksum 和模块 manifest，不保存模块正文。
  - 已实现 `FileBackedBuiltinSkillCatalog`、`FileBackedBuiltinSkillSyncService` 和 `BuiltinSkillDocumentService`。
  - 已接入组织默认技能初始化、技能列表/只读摘要 API、运行时 prompt 装配和 trace metadata。
  - 已新增 `FileBackedBuiltinSkillIntegrationTest` 并通过既有 `SkillGovernanceIntegrationTest` 回归。

## 交接说明

下一位接手者先看：

1. 本文档的 `方案设计` 与 `CloudCC 技能包首版落地`。
2. [FEAT-009 Skill Layering And Governance](./FEAT-009-skill-layering-and-governance.md) 的平台标准 Skill 分层语义。
3. [FEAT-014 管理端技能版本控制与导入导出](./FEAT-014-skill-versioning-import-export.md) 的标准技能只读与派生入口隐藏口径。
4. [FEAT-015 Skill 内嵌声明式 API 运行时](./FEAT-015-skill-declarative-api-runtime.md) 的 runtime API 边界，避免把文件型文档资源误实现成 API 工具。

继续推进前需要确认：

- `cloudcc-customization-expert-common` 的正式展示名称是否为“CloudCC 二次开发专家”。
- 是否第一版默认绑定到 `cici-system`，还是仅在技能库中可选绑定。
- 文件型技能模块文档是否允许平台端查看全文，还是只允许运行时按需读取。
