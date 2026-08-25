---
kind: feature-spec
feature_id: FEAT-014
title: Admin Skill Versioning Import Export
status: implemented
owner_role: product-admin-skill-governance
task_ids: TASK-035,TASK-329
related_decisions: FEAT-009
related_issues: none
updated_at: 2026-08-25T09:08:47Z
updated_by: codex
---

# FEAT-014 - 管理端技能版本控制与导入导出

## 背景与目标

管理端已经具备技能列表、新建、编辑、自然语言生成、平台标准 Skill 派生、运行时版本 pin 等基础能力。下一阶段需要补齐组织管理员维护自定义技能时最常用的治理动作：

- 每次保存自定义技能时形成可读的版本历史，保留最近三个可恢复版本。
- 管理员可以查看版本变更日志，并从最近三个版本中的任意一个恢复。
- 管理员可以把系统内自定义技能导出为通用技能 zip 包。
- 管理员可以导入外部通用技能 zip 包，并由大模型拆解映射为系统支持的字段。
- 管理员可以删除不再使用的自定义技能，但不能删除平台标准技能。
- 新建/编辑页需要明确区分“保存草稿”和“正式发布”：草稿用于编辑和预览，发布后的版本才进入 Agent 绑定、运行时 pin 和导出默认版本。
- 技能列表页和新建/编辑页要承载这些能力，但继续保持当前 `鎏金账房` 的紧凑管理端风格。

本设计是管理端功能增强的实施规格，不直接改变 `DESIGN.md` / `DESIGN.json` 的视觉语言。

## 范围

### In Scope

- 租户自定义技能 `TENANT_CUSTOM` 的最近三版恢复历史。
- 版本变更日志：手写 changelog、系统差异摘要、创建人、创建时间、来源动作。
- 版本恢复：从最近三个可恢复版本中恢复到当前草稿/当前技能。
- 自定义技能导出 zip：仅允许 `sourceType=TENANT_CUSTOM` 且 `editPolicy=EDITABLE`。
- 导出前通过大模型把系统内技能字段整理为通用标准技能格式，再打包为 zip，而不是直接把内部字段原样导出。
- 外部标准技能 zip 导入：解析通用格式，使用大模型映射为系统字段，导入后落为 `TENANT_CUSTOM`。
- 自定义技能删除：仅允许删除 `TENANT_CUSTOM`，删除前做 Agent 绑定、发布版本和运行时 pin 保护检查。
- 标准技能只读：`PLATFORM_STANDARD` 在租户管理端禁止编辑正文、工具/知识边界、升级规则、输出契约、发布和删除。
- 草稿/发布流程：保存草稿不影响运行时；正式发布生成 `PUBLISHED` 版本并更新当前发布版本引用。
- 导入前预览、字段映射确认、冲突处理、编译预览和审计记录。
- 管理端技能列表页、新建页、编辑页的页面结构调整原型。

### Out Of Scope

- 平台标准技能 `PLATFORM_STANDARD` 的导出。
- 租户派生技能 `TENANT_DERIVED` 的新建、派生入口、直接导出和专门治理。第一版隐藏派生功能入口，暂不鼓励创建新的派生技能。
- 平台标准技能模板的灰度发布、平台回滚和 rollout group。该部分仍归 FEAT-009。
- 多技能批量导入导出。第一版每个 zip 包只承载一个技能。
- 跨组织自动同步技能包。
- 导入后自动绑定到 Agent。导入只创建技能，绑定仍在 Agent Builder 完成。
- 平台标准技能的租户侧正文编辑、租户侧发布和租户侧删除。

### 补充需求落地矩阵

| 补充需求 | 设计落点 | 实现硬约束 |
|---|---|---|
| 增加自定义技能删除功能 | 列表行更多菜单、编辑页更多菜单、`DELETE /skills/{id}`、`GET /skills/{id}/delete-impact` | 仅 `TENANT_CUSTOM + EDITABLE` 可删除；删除前必须做 Agent 绑定和 runtime pin 影响分析；第一版软删除 |
| 标准技能禁止编辑 | 标准技能详情页进入只读查看态 | `PLATFORM_STANDARD` 在租户管理端不显示保存、发布、删除、导出、继续优化正文；后端 `PUT/PUBLISH/DELETE/EXPORT` 也必须拒绝 |
| 导出的技能也要依赖大模型整理成通用标准技能格式 | 导出 job 先进入模型标准化整理，再生成 zip | 模型不可用、schema 校验失败或安全扫描失败时导出失败；不能退化为内部字段直出 |
| 隐藏派生技能功能入口 | 列表、新建页、标准技能详情页不展示派生或另存派生 | 前端不暴露 `/skills/{id}/derive` 入口；已有历史派生仅兼容查看，不纳入本次导出/删除/发布主流程 |
| 新建编辑只有保存草稿，没有正式发布 | 新建页和编辑页同时提供“保存草稿”和“发布” | 保存草稿只影响 `DRAFT/latestDraftVersionId`；发布才生成 `PUBLISHED`、更新 `currentPublishedVersionId`，并进入 Agent 绑定和导出默认基准 |

## 用户场景

- 组织管理员持续迭代一个销售质检技能，保存后发现提示片段效果变差，需要从上一版恢复。
- 组织管理员在编辑技能时需要记录“为什么改了工具白名单或升级规则”，后续审计可追踪。
- 组织管理员把某个自定义技能导出，交给另一个环境或客户项目导入复用。
- 组织管理员点击导出时，系统先由大模型把内部 prompt/spec/边界规则整理成通用标准技能包，管理员下载的是标准化后的 zip。
- 组织管理员拿到外部标准技能包，上传后系统解析 manifest/spec/prompt，由模型映射成名称、编码、提示片段、规格正文、工具白名单、知识库白名单、升级规则、输出契约和风险等级。
- 导入技能包包含系统不存在的工具或知识库时，管理员需要看到未匹配项，并选择跳过、手动匹配或保留为待配置占位。
- 组织管理员保存一个新技能后，先得到草稿；确认编译预览无阻断问题后，再点击“发布”形成可绑定/可运行版本。
- 组织管理员需要清理测试用自定义技能时，可以删除；如果技能已绑定 Agent 或有运行中发布依赖，系统阻止删除并提示先解绑或停用。

## 现状与约束

### Verified Facts

- `skill_definition` 已包含分层治理字段：`sourceType`、`visibility`、`editPolicy`、`bindingPolicy`、`updatePolicy`、`templateCode`、`baseTemplateVersion`、`currentPublishedVersionId`、`latestDraftVersionId`。
- `skill_version` 已存在，且已被 Agent 发布版本 pin 使用。已发布 Agent 依赖 `agent_workflow_skill_ref.skill_version_id` 保持运行时不漂移。
- 管理端当前主入口为 `/admin/skills` 和 `/admin/skills/new`、`/admin/skills/:id/edit`。
- 技能创建/编辑 API 当前是 `/skills`、`/skills/{id}`、`/skills/preview`、`/skills/authoring/*`。
- 当前页面已采用暖象牙、墨色、香槟金线条的 `鎏金账房` 产品页基线。

### Key Constraint

“最近三个可恢复版本”不能破坏已发布 Agent 的运行时 pin。设计上需要区分：

- **可恢复版本历史**：面向管理员编辑恢复，只展示最近三个版本。
- **运行时发布快照**：面向 Agent 已发布版本稳定性，若被 `agent_workflow_skill_ref` 引用，即使超过最近三版也不能物理删除。

因此第一版建议复用 `skill_version` 承载快照，但新增 retention/来源字段；恢复面只展示最近三个可恢复快照。被已发布 Agent 引用的老快照可标记为 `PROTECTED_RUNTIME`，不出现在“最近三版恢复”主列表中，但可在审计或 debug 中保留。

另一个关键约束是“草稿”和“发布”必须分离。当前管理端只有“保存草稿”，这会让管理员误以为保存后技能已经正式进入运行时。第一版需要把生命周期明确为：

- `DRAFT`: 可编辑、可预览、可恢复，但不进入正式 Agent 运行时。
- `PUBLISHED`: 编译通过后发布形成的稳定版本，可被 Agent 绑定、发布版本 pin、导出默认使用。
- `DISABLED`: 技能停用，不允许新绑定或运行时主动选择，但历史 pinned snapshot 仍按已有发布版本保护。
- `DELETED`: 自定义技能软删除，不在普通列表出现；若存在历史 pinned snapshot，快照保留但不允许新绑定。

标准技能在租户管理端只能查看和启停配置，不允许编辑正文、不允许租户侧发布、不允许删除；派生技能入口第一版隐藏，避免引导租户进入暂不支持的模板差异治理流程。

## 方案设计

### 1. 技能生命周期、草稿与发布

#### 状态定义

| 状态 | 说明 | 管理端动作 |
|---|---|---|
| `DRAFT` | 草稿版本，保存后可继续编辑和预览 | 保存草稿、预览编译、删除草稿、自定义技能发布 |
| `PUBLISHED` | 正式发布版本，进入 Agent 绑定和运行时 pin 候选 | 查看、导出、生成新草稿、回滚/恢复后再发布 |
| `DISABLED` | 停用状态，保留定义和版本历史 | 重新启用、查看历史 |
| `DELETED` | 自定义技能软删除 | 默认列表隐藏，审计可查 |

#### 保存草稿

- “保存草稿”只写入 `skill_definition` 当前可编辑字段，并创建/更新一个 `DRAFT` 版本。
- 草稿不会更新 `currentPublishedVersionId`。
- 草稿不会影响已发布 Agent 的 `agent_workflow_skill_ref`。
- 保存草稿后页面状态显示“草稿已保存，尚未发布”。

#### 正式发布

发布按钮只对 `TENANT_CUSTOM + EDITABLE` 技能显示。发布前必须：

1. 技能代码、名称、规格正文或提示片段至少满足最小可运行要求。
2. 编译预览通过，无阻断级 warning。
3. 工具/知识库白名单均已校验为当前组织可用资源。
4. 管理员填写或确认发布 changelog。

发布成功后：

- 新增一条 `skill_version.publish_status=PUBLISHED` 的版本快照。
- 更新 `skill_definition.current_published_version_id`。
- 若存在 `latest_draft_version_id`，保留为下一次编辑入口，但运行时以当前发布版本为准。
- Agent Builder 新绑定技能时默认选择当前发布版本。
- 已发布 Agent 不自动漂移，仍需要重新发布 Agent 才会 pin 到新的 Skill 版本。

#### 回滚与恢复关系

- “恢复到草稿”只是把旧版本内容拷回当前编辑表单，并创建新的 `DRAFT`。
- “发布恢复版本”需要管理员再点击发布，生成新的 `PUBLISHED` 版本。
- 不提供直接把旧版本一键覆盖当前发布版本的入口，避免绕过编译预览和 changelog。

### 2. 技能版本控制

#### 版本生成时机

对 `TENANT_CUSTOM` 技能：

- 新建成功后创建 `v1`，来源为 `CREATE`。
- 每次保存编辑后创建新版本，来源为 `SAVE`。
- 每次正式发布后创建 `PUBLISHED` 版本，来源为 `PUBLISH`。
- 自然语言生成后“按草稿创建”创建 `v1`，来源为 `AI_CREATE`。
- 继续优化并保存后创建新版本，来源为 `AI_REFINE_SAVE`。
- 从历史版本恢复后创建新版本，来源为 `RESTORE`，并记录 `restoredFromVersionId`。
- 导入技能创建成功后创建 `v1`，来源为 `IMPORT`。

对 `PLATFORM_STANDARD`：

- 不在租户管理端提供可恢复版本。
- 平台模板版本仍走 `/platform/skills` 与 FEAT-009 机制。

对 `TENANT_DERIVED`：

- 第一版隐藏新建派生入口和列表操作入口。
- 若系统中已有历史派生技能，只允许查看和按现有兼容规则处理，不在本次页面中强调“派生/另存为派生”。
- 第一版不纳入导出，版本恢复可在后续扩展。

#### 保留策略

- 对每个 `TENANT_CUSTOM` skill，按 `createdAt desc` 保留最近三个 `restoreVisible=true` 版本。
- 新建第 4 个可恢复版本后，把第 4 个之后的旧版本标记为 `restoreVisible=false`。
- 如果旧版本被 `agent_workflow_skill_ref` 引用，不删除，只标记 `retentionState=PROTECTED_RUNTIME`。
- 如果旧版本未被引用，可软删除或标记 `retentionState=PRUNED`。第一版建议软标记，便于审计。

#### 变更日志结构

每个版本记录两类 changelog：

- `changeLog`: 管理员手写，保存弹层/侧栏输入，例如“补充合同条款升级规则”。
- `diffSummary`: 系统生成，基于上一版本字段差异，例如“工具白名单新增 2 项；风险等级 MEDIUM -> HIGH；规格正文变更 4 行”。

版本行还需要显示：

- 版本号：`v1`、`v2`、`v3`。
- 来源：新建、保存、AI 生成、AI 优化、导入、恢复。
- 创建人：当前管理员用户。
- 创建时间。
- 发布状态：草稿、已发布、被 Agent pin。
- 影响提示：若恢复会改变工具/知识边界，需要在确认前提示。

#### 恢复流程

1. 管理员在编辑页打开“版本”侧栏。
2. 选择最近三个版本中的任意一个。
3. 页面展示差异摘要：基础信息、提示片段、规格正文、边界规则、风险等级。
4. 管理员点击“恢复为当前草稿”。
5. 系统把历史版本字段写回当前表单，并提示需要保存。
6. 管理员保存后生成新版本，来源为 `RESTORE`。

恢复不直接覆盖已发布 Agent 的 pinned snapshot。已发布 Agent 只有重新发布后才会命中新技能版本。

### 3. 自定义技能删除

#### 删除权限

允许删除：

- `sourceType=TENANT_CUSTOM`
- `editPolicy=EDITABLE`
- 当前用户是组织管理员

禁止删除：

- `PLATFORM_STANDARD`
- `TENANT_DERIVED`（第一版入口隐藏，已有历史数据不提供删除入口）
- `visibility=HIDDEN`
- `editPolicy=LOCKED` 或 `CONFIGURABLE`
- 当前仍被任一 Agent 绑定、当前发布版本仍被可运行 Agent 引用，或仍存在未处理的运行时发布依赖

#### 删除策略

第一版使用软删除：

- `skill_definition.enabled=false`
- `visibility=HIDDEN` 或新增 `lifecycle_status=DELETED`
- 写入 `deleted_at`、`deleted_by`、`delete_reason`
- 最近三版恢复列表不再进入普通编辑入口
- 若存在历史 `PROTECTED_RUNTIME` 版本，继续保留 `skill_version`，用于历史 Agent runtime/debug 解析

删除前置检查：

1. 是否为自定义技能。
2. 是否存在启用中的 `agent_skill_binding`。
3. 是否存在当前发布 Agent pin 到该技能版本。
4. 是否存在未完成的导入/发布 job。

交互建议：

- 列表行更多菜单显示“删除”，仅自定义技能启用。
- 编辑页顶部更多菜单显示“删除技能”。
- 删除确认不作为第一交互模式，但危险动作需要确认框。确认文案必须包含技能名称和影响摘要，例如“该技能未绑定 Agent，删除后普通列表不可见，历史审计仍保留”。

### 4. 标准技能只读与隐藏派生入口

标准技能在租户管理端的行为：

- 列表操作显示“查看”，不显示“编辑”。
- 详情/编辑页所有正文、提示片段、规格正文、边界规则、风险等级字段只读。
- 不显示“保存草稿”“发布”“删除”“导出”“继续优化正文”。
- 可以保留租户级“启用/停用”配置入口，前提是该标准技能的 `editPolicy=CONFIGURABLE`。
- 页面顶部状态明确显示“平台标准 · 平台维护 · 租户只读”。

派生技能入口处理：

- 列表不显示“派生”“另存为派生”。
- 标准技能详情页不显示“派生”按钮。
- 新建页不提供“从标准技能派生”的创建方式。
- 若后端现有 `/skills/{id}/derive` API 暂时保留兼容，前端不暴露入口；后续如恢复派生能力，需要单独补充差异比较、模板升级和派生治理设计。

### 5. 导出功能

#### 导出权限

允许导出：

- `sourceType=TENANT_CUSTOM`
- `editPolicy=EDITABLE`
- 当前用户是组织管理员

禁止导出：

- `PLATFORM_STANDARD`
- `TENANT_DERIVED`
- `visibility=HIDDEN`
- `editPolicy=LOCKED` 或 `CONFIGURABLE`

禁止导出时在 UI 中给出明确原因：

- 平台标准技能由平台维护，不能导出。
- 派生技能带有平台模板依赖，第一版不提供入口。

#### 导出模型整理流程

导出不是把内部字段直接写入 zip。系统必须先调用大模型，把当前系统内的技能定义整理成通用标准技能格式。

输入给大模型：

- 当前导出基准：默认当前 `PUBLISHED` 版本；若没有发布版本，要求管理员选择“导出草稿”并显示水印/警告。
- `skill_definition` 当前字段：名称、描述、风险、提示片段、规格正文、升级规则、输出契约。
- `skill_version` 编译摘要、有效工具和知识库边界。
- 工具元数据：工具名、展示名、描述、风险等级，只包含引用描述，不包含凭据。
- 知识库元数据：知识库名称和描述，只包含引用描述，不包含知识库正文。
- 导出目标格式版本，例如 `universal-skill-package@1.0`。

大模型输出：

- 标准化 `manifest.json`
- 面向外部系统阅读的 `skill.md`
- 去内部实现细节后的 `prompt.md`
- `contract.json`
- `resources.json`
- `README.md`
- `exportNotes` 和 `warnings`

服务端校验：

- 输出必须符合 schema。
- 不允许出现组织密钥、token、用户凭据、内部 URL、知识库正文。
- `resources.json` 只能保留资源名称、描述、required、matchStrategy，不保留内部数据库 ID 作为强依赖。
- 如果模型不可用或 schema 校验失败，导出失败并提示“标准化整理失败”，不得退化为直接导出内部字段。

#### 导出基准版本

- 默认导出当前 `PUBLISHED` 版本。
- 如果技能只有草稿未发布，导出按钮默认禁用；管理员可在更多菜单选择“导出草稿包”，下载包需在 `manifest.json.skill.publishStatus=DRAFT` 中标注。
- 如果当前表单有未保存修改，导出提示“导出基于最近保存/发布版本”。

#### 通用技能包格式

zip 根目录建议固定为：

```text
skill-package.zip
├── manifest.json
├── skill.md
├── prompt.md
├── contract.json
├── resources.json
└── README.md
```

`manifest.json`：

```json
{
  "format": "universal-skill-package",
  "formatVersion": "1.0",
  "packageId": "sales-lead-triage",
  "name": "销售线索分诊",
  "description": "识别线索质量并给出跟进建议。",
  "language": "zh-CN",
  "sourceSystem": "cc-cici-assistant",
  "exportedAt": "2026-05-01T06:53:05Z",
  "exportedBy": "admin@example.com",
  "skill": {
    "code": "sales-lead-triage",
    "riskLevel": "MEDIUM",
    "versionNo": 3,
    "changeLog": "补充合同条款升级规则"
  }
}
```

`skill.md`：

```markdown
# 销售线索分诊

## Capability

识别客户背景、预算、需求紧迫度并给出线索分层。

## Instructions

1. 提取客户公司、角色、行业、地域与对接渠道。
2. 识别预算信号、紧急程度和采购决策链条。
3. 按 A/B/C 输出线索等级，并标注判定依据。

## Escalation

涉及价格承诺、合同条款或跨部门资源调度时，转人工销售确认。
```

`prompt.md`：导出 `promptFragment`。

`contract.json`：

```json
{
  "outputContract": "输出包含线索等级、证据字段、推荐动作、建议负责人。",
  "riskLevel": "MEDIUM",
  "triggerHints": ["线索分级", "客户跟进建议"],
  "userIntentExamples": ["帮我判断这个客户是不是高质量线索"]
}
```

`resources.json`：

```json
{
  "tools": [
    {
      "name": "cloudcc_pageQuery",
      "displayName": "CloudCC 分页查询",
      "required": true,
      "matchStrategy": "byName"
    }
  ],
  "knowledgeBases": [
    {
      "name": "销售SOP",
      "required": false,
      "matchStrategy": "byName"
    }
  ]
}
```

导出包不包含：

- 组织密钥、模型密钥、CloudCC token、用户凭据。
- 知识库原文内容。
- 工具实现代码。
- 平台标准模板元数据。
- Agent 绑定关系。

### 6. 导入功能

#### 导入结果定位

外部技能包导入后统一创建为：

- `sourceType=TENANT_CUSTOM`
- `visibility=VISIBLE`
- `editPolicy=EDITABLE`
- `bindingPolicy=OPTIONAL`
- `updatePolicy=MANUAL`
- `builtin=false`

即使外部包声称自己是“标准技能”，在本系统租户管理端也只作为自定义技能导入，不提升为平台标准技能。

#### 导入流程

1. 管理员点击“导入技能”。
2. 上传 zip，前端只负责选择文件与展示状态，后端解析。
3. 后端校验 zip：
   - 文件大小限制，建议第一版 5 MB。
   - 禁止路径穿越。
   - 只读取白名单文件名。
   - manifest 格式和版本可识别。
4. 后端抽取文本内容，调用大模型进行字段映射。
5. 大模型输出结构化 `ImportedSkillDraft`。
6. 系统做 schema sanitize、工具/知识库候选匹配、风险扫描。
7. 前端展示导入预览：
   - 识别到的技能字段。
   - 原包内容摘要。
   - 工具/知识库匹配结果。
   - 模型映射置信度与警告。
8. 管理员确认或手动调整。
9. 点击“创建自定义技能”，落库并生成 `v1`，来源为 `IMPORT`。

#### 大模型映射要求

输入：

- `manifest.json`
- `skill.md`
- `prompt.md`
- `contract.json`
- `resources.json`
- 当前系统可用工具列表：`toolName`、`displayName`、`description`、MCP server 信息。
- 当前系统可用知识库列表：`id`、`name`、`description`。

输出字段：

```json
{
  "skillCode": "sales-lead-triage",
  "name": "销售线索分诊",
  "description": "识别线索质量并给出跟进建议。",
  "promptFragment": "...",
  "draftSpecText": "...",
  "toolWhitelist": ["cloudcc_pageQuery"],
  "kbWhitelist": ["12"],
  "handoffRule": "...",
  "outputContract": "...",
  "riskLevel": "MEDIUM",
  "mappingNotes": ["resources.json 中 sales_query 映射为 cloudcc_pageQuery"],
  "warnings": ["知识库 销售SOP 未唯一匹配，需人工确认"]
}
```

约束：

- 模型只能映射到系统存在的工具和知识库 ID。
- 无法匹配的资源进入 `unmatchedResources`，不得捏造工具名或知识库 ID。
- 风险等级不能低于导入包声明的风险等级；若包声明缺失，由模型建议，系统规则可上调。
- 技能编码冲突时默认生成后缀，例如 `sales-lead-triage-2`，也允许管理员手动修改。
- 映射提示必须要求保留原始业务语义，不要把外部包套成 CRM 样例。

#### 导入失败与降级

- zip 格式错误：停在上传阶段，展示错误。
- manifest 缺失但有 `skill.md`：允许进入“弱格式导入”，模型从 Markdown 推断字段，但提示置信度较低。
- 模型不可用：允许生成基础草稿，只填 `name/description/draftSpecText/promptFragment`，资源匹配为空，要求管理员人工补齐。
- 工具/知识库无法匹配：仍可创建技能，但相关白名单为空或仅保留已确认匹配。

## 接口与数据影响

### 数据模型扩展

建议在 `skill_version` 上补充字段：

| Field | Type | Meaning |
|---|---|---|
| `change_log` | text | 管理员手写 changelog |
| `diff_summary` | text | 系统生成差异摘要 JSON 或 Markdown |
| `version_source` | varchar | `CREATE` / `SAVE` / `AI_CREATE` / `AI_REFINE_SAVE` / `IMPORT` / `RESTORE` |
| `created_by` | varchar | 创建版本的管理员用户 ID 或用户名 |
| `restore_visible` | boolean | 是否进入最近三版恢复列表 |
| `retention_state` | varchar | `ACTIVE_RECENT` / `PROTECTED_RUNTIME` / `PRUNED` |
| `restored_from_version_id` | bigint | 恢复来源版本 ID |
| `package_manifest_json` | text | 导入来源 manifest 摘要，可选 |

建议在 `skill_definition` 上补充或确认以下字段：

| Field | Type | Meaning |
|---|---|---|
| `lifecycle_status` | varchar | `DRAFT` / `PUBLISHED` / `DISABLED` / `DELETED` |
| `deleted_at` | timestamp | 自定义技能软删除时间 |
| `deleted_by` | varchar | 删除人 |
| `delete_reason` | text | 删除原因 |
| `last_published_at` | timestamp | 最近正式发布时间 |
| `last_published_by` | varchar | 最近发布人 |

也可新增 `skill_import_job`：

| Field | Type | Meaning |
|---|---|---|
| `id` | bigint | 导入任务 ID |
| `org_id` | varchar | 组织 |
| `status` | varchar | `UPLOADED` / `MAPPING` / `READY` / `FAILED` / `CREATED` |
| `original_filename` | varchar | 原始文件名 |
| `manifest_json` | text | manifest |
| `raw_summary` | text | zip 内容摘要 |
| `mapped_draft_json` | text | 模型映射结果 |
| `warnings_json` | text | 解析和映射警告 |
| `created_skill_id` | bigint | 确认创建后的技能 ID |
| `created_by` | varchar | 上传人 |
| `created_at` | timestamp | 上传时间 |
| `updated_at` | timestamp | 更新时间 |

建议新增 `skill_export_job`：

| Field | Type | Meaning |
|---|---|---|
| `id` | bigint | 导出任务 ID |
| `org_id` | varchar | 组织 |
| `skill_id` | bigint | 导出的技能 |
| `skill_version_id` | bigint | 导出基准版本 |
| `status` | varchar | `MAPPING` / `READY` / `FAILED` |
| `standard_manifest_json` | text | 模型整理后的 manifest |
| `package_summary_json` | text | zip 文件清单、warnings、exportNotes |
| `failure_reason` | text | 失败原因 |
| `created_by` | varchar | 导出人 |
| `created_at` | timestamp | 创建时间 |
| `updated_at` | timestamp | 更新时间 |

### API Design

版本：

- `GET /skills/{id}/versions?limit=3&restoreVisible=true`
- `POST /skills/{id}/versions/{versionId}/restore`
- `POST /skills/{id}/versions`
- `POST /skills/{id}/publish`
- `POST /skills/{id}/unpublish` 或 `POST /skills/{id}/disable`

导出：

- `POST /skills/{id}/exports`
- `GET /skills/exports/{exportId}`
- `GET /skills/exports/{exportId}/download`

导入：

- `POST /skills/imports`
- `GET /skills/imports/{importId}`
- `PUT /skills/imports/{importId}/draft`
- `POST /skills/imports/{importId}/create`

删除：

- `DELETE /skills/{id}`
- `GET /skills/{id}/delete-impact`

保存扩展：

```json
{
  "skillCode": "sales-lead-triage",
  "name": "销售线索分诊",
  "description": "...",
  "changeLog": "补充合同条款升级规则"
}
```

为了兼容当前 `PUT /skills/{id}`，`changeLog` 可以作为可选字段加入 `UpsertSkillRequest`。为空时后端生成默认 changelog，例如“保存技能配置”。

发布请求：

```json
{
  "changeLog": "补充合同条款升级规则并发布",
  "confirmWarnings": ["KB_SALES_SOP_UNMATCHED"]
}
```

删除请求：

```json
{
  "reason": "测试技能已废弃"
}
```

### 服务端权限守卫

前端隐藏入口不是权限边界。第一版必须在服务端集中校验以下规则：

- `PUT /skills/{id}`：`PLATFORM_STANDARD` 拒绝编辑正文、提示片段、规格正文、工具/知识边界、升级规则、输出契约和风险等级；只允许符合 `editPolicy=CONFIGURABLE` 的租户级启停配置。
- `POST /skills/{id}/publish`：仅允许 `TENANT_CUSTOM + EDITABLE`，并要求编译预览与资源校验通过。
- `DELETE /skills/{id}`：仅允许 `TENANT_CUSTOM + EDITABLE`，且删除影响分析无阻断项。
- `POST /skills/{id}/exports`：仅允许 `TENANT_CUSTOM + EDITABLE`；必须完成模型标准化整理、schema 校验和安全扫描后才能下载。
- `POST /skills/{id}/derive`：若 API 因兼容保留，第一版不作为管理端公开能力；如被直接调用，需要按现有权限校验返回明确错误或兼容只读结果，不能创建新的可编辑派生入口。

这些校验需要与审计事件一起落地，避免用户通过旧前端、脚本或浏览器请求绕过页面级限制。

### 审计

需要记录：

- `skill.version.create`
- `skill.version.publish`
- `skill.version.restore`
- `skill.export`
- `skill.export.map`
- `skill.export.fail`
- `skill.import.upload`
- `skill.import.map`
- `skill.import.create`
- `skill.import.fail`
- `skill.delete`
- `skill.delete.reject`

审计内容包括 skillId、skillCode、versionNo、sourceType、文件名、导入警告数量、导出是否被拒绝及原因。

## 页面调整原型

### 设计原则

- 管理端页面仍按 product register：高密度、表格优先、操作贴近对象。
- 继续使用暖象牙底、墨色文字、细香槟金结构线；金色只用于 active、focus、边界、主要动作。
- 版本和导入导出是治理动作，不做营销式大卡片，不做首屏 hero。
- 导入流程避免 modal 作为第一反应：优先使用页面内抽屉/工作区。只有危险恢复确认可以使用轻量确认框。
- 长说明收进问号 tooltip 或导入结果警告列表，不让列表页堆满解释文案。

### 技能列表页 `/admin/skills`

目标：列表页成为“找技能、看来源、看草稿/发布状态、发起导入/导出/删除”的入口。

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ 技能管理                                           [导入技能] [新建技能]       │
│ 面包屑 / 管理端 / 技能                                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│ [搜索名称/编码/描述____] [来源: 全部 v] [风险: 全部 v] [状态: 全部 v] [更多] │
├──────────────────────────────────────────────────────────────────────────────┤
│ 名称                来源        版本          风险   状态      更新时间   操作 │
│──────────────────────────────────────────────────────────────────────────────│
│ 销售线索分诊        自定义      v3 · 已发布   中     启用      05-01    编辑 │
│ sales-lead-triage              草稿 v4 待发布                       导出 删除│
│                                                                            │
│ Web 搜索            平台标准    v5 · 平台维护 低     启用      04-30    查看 │
│ web-search                     只读 · 不可导出                       启停  │
│                                                                            │
│ 合同审查扩展        派生        v2 · 历史数据 高     停用      04-29    查看 │
│ contract-review-plus           派生入口已隐藏                         —    │
└──────────────────────────────────────────────────────────────────────────────┘

右侧可选导入工作区（点击“导入技能”后在列表页内展开）：
┌──────────────────────────────────────────────┐
│ 导入技能                                      │
│ [上传 zip________________________________]    │
│ 解析状态：等待上传                            │
│                                              │
│ 映射预览                                      │
│ 名称 / 编码 / 风险 / 工具匹配 / 知识库匹配      │
│                                              │
│ [取消] [创建自定义技能]                       │
└──────────────────────────────────────────────┘
```

列表字段变化：

- 新增“版本”列：展示 `vN`、最新状态、最近三版入口。
- 新增“发布状态”表达：已发布、仅草稿、草稿待发布、停用、已删除默认隐藏。
- 操作列按来源差异：
  - 自定义：编辑、发布/查看发布状态、导出、删除、更多。
  - 平台标准：查看、启停配置，不显示编辑、导出、发布、删除、派生。
  - 派生：第一版不显示创建或派生入口；已有历史派生技能只显示查看或按兼容规则展示。
- 筛选新增“来源”：平台标准、派生、自定义。
- 顶部主操作新增“导入技能”。
- “新建技能”只创建自定义技能，不提供派生入口。

空态：

- 没有自定义技能时，列表不显示大面积卡片，只在表格空行给出“新建技能 / 导入技能”两个紧凑动作。

### 技能新建页 `/admin/skills/new`

目标：新建页保留当前自然语言生成工作台，同时让导入成为同级入口。

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ 管理端 / 技能 / 新建技能             [启用开关] [导入zip] [保存草稿] [发布] │
├──────────────────────────────────────────────────────────────────────────────┤
│ 创建方式： [自然语言生成] [手动填写] [导入zip]                               │
├──────────────────────────────────────────────────────────────────────────────┤
│ 自然语言生成                                                                │
│ ┌──────────────────────────────┐ ┌─────────────────────────────────────────┐ │
│ │ 需求描述                      │ │ 摘要预览                                │ │
│ │                              │ │ 目标 / 触发 / 输出 / 风险                 │ │
│ └──────────────────────────────┘ └─────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────────┤
│ 基础信息 | 提示片段 | 规格正文 | 边界规则 | 编译预览                         │
│                                                                              │
│ 基础信息区新增：                                                             │
│ 变更日志 [新建技能初始版本________________________________________]          │
└──────────────────────────────────────────────────────────────────────────────┘
```

当选择“导入zip”：

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ 导入zip                                                                      │
│ [选择文件 skill-package.zip] [重新解析]                                       │
├─────────────────────────────────────┬────────────────────────────────────────┤
│ 外部包内容                           │ 映射为系统技能                         │
│ manifest / skill.md / resources      │ 名称、编码、描述、风险                  │
│                                      │ 工具白名单匹配                          │
│                                      │ 知识库白名单匹配                        │
│                                      │ 警告与待确认项                          │
├─────────────────────────────────────┴────────────────────────────────────────┤
│ [返回] [套用到表单] [创建自定义技能]                                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

行为：

- “套用到表单”把映射结果填入现有基础信息/提示片段/规格正文/边界规则 tab。
- “保存草稿”创建或更新 `DRAFT`，不进入运行时。
- “发布”先保存草稿，再执行编译预览和发布校验；成功后生成 `PUBLISHED` 版本。
- “创建自定义技能”应改名为“保存草稿”或“保存并发布”，避免误解保存即上线。
- 新建成功后自动生成 `v1`，版本日志来自导入摘要或用户填写的变更日志。
- 标准技能不进入新建页；派生创建方式不展示。

### 技能编辑页 `/admin/skills/:id/edit`

目标：编辑页增加版本侧栏、导出、删除和发布入口，不打乱当前 tab 编辑结构。

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ 管理端 / 技能 / 销售线索分诊                                                 │
│ 自定义 · v3 已发布 · 草稿 v4 待发布     [启用] [导出] [版本] [保存草稿] [发布] │
├──────────────────────────────────────────────────────────────────────────────┤
│ 自然语言生成 / 继续优化                                                      │
│ ┌──────────────────────────────┐ ┌─────────────────────────────────────────┐ │
│ │ 增量需求                      │ │ 摘要预览                                │ │
│ └──────────────────────────────┘ └─────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────────┤
│ 基础信息 | 提示片段 | 规格正文 | 边界规则 | 编译预览                         │
│                                                                              │
│ 变更日志 [说明这次保存改了什么_____________________________]                 │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

点击 [版本] 后右侧展开：
┌──────────────────────────────────────────────┐
│ 版本历史                                      │
│ 最近三个可恢复版本                            │
│                                              │
│ ● v3  当前版本                                │
│   保存 · 05-01 14:30 · 张三                   │
│   工具 +1，升级规则变更                       │
│                                              │
│ ○ v2                                         │
│   AI 优化保存 · 05-01 11:10 · 张三            │
│   规格正文变更 4 行                           │
│   [查看差异] [恢复为当前草稿]                 │
│                                              │
│ ○ v1                                         │
│   导入 · 04-30 18:42 · 李四                   │
│   初始导入版本                                │
│   [查看差异] [恢复为当前草稿]                 │
│                                              │
│ 运行时保护快照：2 个（被已发布 Agent 引用）     │
└──────────────────────────────────────────────┘
```

差异查看：

```text
┌──────────────────────────────────────────────┐
│ v2 与当前表单差异                             │
│ 基础信息                                      │
│ - 风险等级：MEDIUM -> HIGH                    │
│ 边界规则                                      │
│ - 工具白名单新增：cloudcc_pageQuery           │
│ 规格正文                                      │
│ - 变更 4 行                                   │
│                                              │
│ [关闭] [恢复为当前草稿]                       │
└──────────────────────────────────────────────┘
```

保存行为：

- 保存按钮旁不再额外解释版本规则。
- 如果 changelog 为空，保存仍允许，但后端生成默认 changelog。
- 保存成功后右侧版本历史刷新，最多显示三个可恢复版本。
- 保存草稿不更新运行时发布版本，页面提示“草稿已保存，尚未发布”。

发布行为：

- “发布”是自定义技能的高价值主动作，靠近“保存草稿”但视觉上明确区分。
- 发布前自动运行编译预览；阻断级 warning 必须处理。
- 发布成功后状态 chip 更新为“已发布 vN”，并更新 `currentPublishedVersionId`。
- 平台标准技能和历史派生技能不显示发布按钮。

导出行为：

- 自定义技能：点击导出后先创建导出任务，由大模型整理为通用标准技能格式；任务完成后下载 zip。
- 若只有草稿未发布，默认导出禁用；可在更多菜单选择“导出草稿包”，并在 manifest 标注 `DRAFT`。
- 若最新表单未保存，先提示“导出基于最近保存/发布版本”。
- 平台标准/派生技能：按钮禁用或不显示；在更多菜单里展示不可导出原因。

删除行为：

- 自定义技能：更多菜单显示“删除技能”。
- 若存在 Agent 绑定或当前发布引用，删除入口禁用并显示影响摘要。
- 删除成功后回到列表，普通列表不再显示该技能。
- 平台标准技能和派生技能不显示删除入口。

标准技能查看页：

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│ 管理端 / 技能 / Web 搜索                                                     │
│ 平台标准 · 平台维护 · 租户只读                         [启用开关] [返回列表] │
├──────────────────────────────────────────────────────────────────────────────┤
│ 基础信息 | 提示片段 | 规格正文 | 边界规则 | 编译预览                         │
│                                                                              │
│ 所有正文和边界字段只读；不显示保存、发布、删除、导出、派生。                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

## 任务拆分

- `TASK-035A`: 后端版本字段、最近三版 retention、恢复 API、changelog/diff summary。
- `TASK-035B`: 后端草稿/发布状态机、发布 API、发布前编译校验、`currentPublishedVersionId` 更新。
- `TASK-035C`: 后端自定义技能删除、删除影响分析、软删除与 runtime pin 保护。
- `TASK-035D`: 后端导出 job、导出大模型标准化整理、zip 打包、导出下载。
- `TASK-035E`: 后端导入 job、zip 安全校验、外部包格式解析。
- `TASK-035F`: 大模型导入映射 prompt、schema sanitize、资源匹配、fallback 导入。
- `TASK-035G`: 管理端技能列表页导入/导出/删除/发布状态 UI，隐藏派生入口。
- `TASK-035H`: 管理端技能新建与编辑页版本侧栏、变更日志、导入 zip 工作区、保存草稿/发布入口、标准技能只读态。
- `TASK-035I`: 服务端权限守卫与审计验证，覆盖标准技能只读、自定义技能删除、导出标准化失败拦截、派生入口隐藏后的直接调用防护。
- `TASK-035J`: 集成测试、前端构建、导入导出回归、草稿/发布状态机和权限矩阵验证。

## 验收标准

- 自定义技能连续保存 4 次后，编辑页只显示最近 3 个可恢复版本。
- 管理员可以从最近三个可恢复版本任意选择一个，恢复到当前表单，保存后生成新的版本记录。
- 被已发布 Agent pin 的旧 `skill_version` 不因三版保留策略而被物理删除，运行时仍能解析。
- 版本记录包含 changelog、diff summary、创建人、创建时间、来源动作。
- 新建/编辑页必须同时提供“保存草稿”和“发布”两个清晰动作；保存草稿不影响运行时，发布成功后才更新 `currentPublishedVersionId`。
- 发布前必须执行编译预览和资源校验；阻断级 warning 不允许发布。
- 标准技能在租户管理端只读，不允许编辑正文、提示片段、规格正文、边界规则、风险等级、发布或删除。
- 管理端不展示派生技能入口：列表、标准技能详情页、新建页都不出现“派生”动作。
- 自定义技能可删除；删除前必须做 Agent 绑定与运行时 pin 影响分析，存在当前运行依赖时阻止删除。
- 删除自定义技能使用软删除，普通列表隐藏，历史审计和受保护运行时快照保留。
- 平台标准技能不能导出，接口和 UI 都必须拦截。
- 派生技能第一版不能直接导出，且不展示新的派生/另存派生入口。
- 自定义技能导出必须先经过大模型标准化整理，生成通用标准技能格式；模型不可用或 schema 校验失败时不得直接导出内部字段。
- 自定义技能导出的 zip 包当前包含 `manifest.json`、`SKILL.md`、`cici-skill.md`、`prompt.md`、`contract.json`、`resources.json`、`PACKAGE_SPEC.md`、`README.md`。`SKILL.md` 是外部智能体通用入口，`cici-skill.md` 是 AgentCiCi 导入规格正文。
- 外部 zip 可导入为租户自定义技能；导入过程经过模型映射、资源匹配、预览确认。
- 已软删除的自定义技能不得继续占用原 `skillCode` 导致同 code 技能包无法再次导入；删除后重新导入同 code 应创建新的租户自定义草稿。
- 模型无法匹配的工具/知识库不会被伪造进入白名单。
- 导入创建后的技能可继续编辑、预览编译、保存版本、导出。
- 所有新增管理端页面保持 `鎏金账房` 风格：紧凑表格、浅色暖底、细金线边界、无营销 hero、无装饰渐变、无玻璃拟态。

## 风险与回滚

- 风险：把最近三版保留误实现为物理删除所有旧 `skill_version`，会破坏已发布 Agent 的 pinned runtime。
  - 缓解：先实现 `retentionState`，删除前检查 `agent_workflow_skill_ref` 引用；第一版只软标记。
- 风险：外部 zip 带恶意路径或超大文件。
  - 缓解：后端 zip entry 白名单、大小限制、路径标准化检查。
- 风险：模型映射误把不存在的工具名写入白名单。
  - 缓解：模型输出后做服务端资源白名单校验，未命中资源只能进 warning。
- 风险：导出泄露组织凭据或知识库原文。
  - 缓解：导出文件只包含技能定义与资源引用名称，不包含密钥、token、知识库内容。
- 风险：保存草稿被误认为正式上线，导致管理员以为 Agent 已命中新版本。
  - 缓解：状态机明确区分 `DRAFT` / `PUBLISHED`，运行时只使用发布版本，页面使用不同按钮和状态 chip。
- 风险：删除自定义技能破坏正在运行的 Agent。
  - 缓解：删除前检查绑定和 `agent_workflow_skill_ref`，对当前运行依赖阻止删除，对历史 pinned snapshot 做 `PROTECTED_RUNTIME` 保留。
- 风险：大模型导出整理遗漏关键约束或引入不存在资源。
  - 缓解：导出结果必须 schema 校验和资源反查，失败不允许下载；导出包保留 `exportNotes` 与 warnings。
- 回滚：版本/导入导出 API 可独立关闭入口；已创建的自定义技能仍按普通技能保留。

## 实现进展

- 2026-05-01: 已完成设计文档初稿，覆盖版本控制、导入导出、数据/API 影响与管理端页面原型。
- 2026-05-01: 根据补充需求更新设计：增加自定义技能删除、标准技能租户侧只读、导出前大模型标准化整理、隐藏派生入口、保存草稿/正式发布状态机。
- 2026-05-01: 继续补齐补充需求落地矩阵、服务端权限守卫、任务拆分和验收映射，明确前端隐藏入口不能替代后端权限拦截。
- 2026-05-01: 完成第一轮代码实现：`V35` 迁移、`skill_version` retention/changelog/source 字段、`skill_definition` lifecycle/delete/publish 字段、版本列表与恢复 API、发布 API、删除影响分析与软删除、导出 zip 下载、导入 zip 解析与创建、派生入口服务端拒绝、列表页和编辑页发布/导入/导出/删除/版本入口。
- 2026-05-01: 完成验证：`backend mvn -q -Dmaven.repo.local=.m2 -Dtest=SkillGovernanceIntegrationTest,SkillAuthoringIntegrationTest test` 通过；`frontend npm run build` 通过，保留既有 Vite chunk-size warning。
- 2026-05-01: 完成第二轮硬化实现：导出链路增加“模型标准化优先 + 确定性回退”，并在下载前执行 manifest/schema 与敏感信息扫描；导入预览新增 `resourceMapping`（工具/知识库匹配和未匹配项）与可编辑 `draftOverride` 创建接口；管理端新建页导入改为先载入可编辑草稿；新增 runtime pin retention 回归，验证被 pin 旧版本 prune 后保留为 `PROTECTED_RUNTIME`。
- 2026-05-01: 完成导入预览工作区收口：新建页导入预览支持“升级处理规则/输出约定”字段编辑，并在“直接创建草稿”前增加技能代码/显示名称必填校验；复跑 FEAT-014 后端集成测试与前端构建通过。
- 2026-05-03: 修复软删除 Skill code 占用导入创建：删除时归档旧 `skill_code`，创建时遇到历史 `DELETED` 同 code 会先归档旧记录并 flush；新增 `V36__archive_deleted_skill_codes.sql` 处理既有软删除数据；`SkillGovernanceIntegrationTest` 覆盖删除同 code 后导入创建成功，复跑 `SkillGovernanceIntegrationTest` 与 `SkillAuthoringIntegrationTest` 通过。
- 2026-08-25: TASK-329 修复模型输出 manifest 固定格式字段漂移导致的导出 400。模型继续整理技能正文与可移植契约，但 `format`、`formatVersion`、`packageId`、版本、发布状态和导出身份由服务端按当前发布版本覆盖；最终八文件包仍统一执行 JSON、格式与敏感信息校验。技能列表同步补齐导出进行中状态、非 JSON 与下载失败反馈。

## 交接说明

- 先读本 spec，再读 `docs/specs/FEAT-009-skill-layering-and-governance.md` 中 skill 分层和 runtime pin 的约束。
- 实现前重点确认 `skill_version` 的 retention 方案，不能影响 `agent_workflow_skill_ref`。
- UI 实现前继续按 `AGENTS.md` 读取 `PRODUCT.md` / `DESIGN.md`，保持管理端 product register。
