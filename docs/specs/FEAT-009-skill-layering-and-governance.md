---
updated_at: 2026-04-30T02:17:21Z
status: active
feature_id: FEAT-009
owner_role: product-platform
---

# FEAT-009 Skill Layering And Governance

## Goal

- 将当前混合在 `builtin` 与 `custom` 里的 Skill 体系，升级为可长期运营的分层模型。
- 明确平台核心兜底策略、平台标准 Skill、租户派生 Skill、租户自定义 Skill 的边界。
- 让平台维护更新、租户可见配置、Agent 发布版本可复现三件事同时成立。
- 为后续平台运营后台、标准能力灰度发布、计费和审计提供稳定数据模型。

## Problem

当前系统已经具备 `skill_definition`、`skill_version`、`agent_skill_binding`、内置 Skill 懒加载、Skill Authoring、Agent Builder 绑定等基础能力，但产品语义仍主要依赖一个 `builtin` 布尔值。

这会带来几个问题：

- 平台底层兜底策略与可见标准 Skill 混在同一层，租户侧很难理解哪些能力可见、可配、可编辑。
- 内置标准 Skill 由平台维护，但当前 `ensureBuiltinSkills` 更多是补缺，不负责平台版本升级、灰度和影响分析。
- 已发布 Agent 如果只引用逻辑 Skill，而不 pin 具体 SkillVersion，会在平台更新或租户编辑后发生运行时漂移。
- 隐藏平台能力，例如 `BuiltinSkillCreatorService`，已经存在但没有统一归类和治理入口。
- 后续计费、审计、运营后台需要知道一次运行命中了哪些平台策略、标准能力和租户能力，当前模型表达不足。

## Current Verified Baseline

- `skill_definition` 当前包含 `builtin`、`enabled`、`prompt_fragment`、`draft_spec_text`、`tool_whitelist`、`kb_whitelist`、`handoff_rule`、`output_contract`、`risk_level`。
- `skill_version` 当前已存在，可记录 `spec_text`、`spec_ir_json`、编译后的 prompt/policy、有效工具/知识边界、风险和发布状态。
- `SkillDefinitionService.BUILTIN_SKILLS` 当前包含 `conversation-core`、`knowledge-first`、`safe-handoff`、`general-assistant`、CRM 系列、`approval-assistant`、`web-search`。
- `BuiltinSkillCreatorService` 已是隐藏平台能力，代码注释明确“不作为可见内置 skill asset 暴露”。
- `AgentSkillBindingService` 与 `/agents/{agentId}/skills` 已将 Agent-Skill 绑定主入口移动到 Agent 侧。

## Design Principles

1. 不再只用 `builtin/custom` 判断能力性质，而是同时表达来源、可见性、编辑策略、绑定策略和版本策略。
2. 平台核心兜底策略不应作为租户可删除 Skill 展示；普通租户最多看到调试摘要。
3. 平台标准 Skill 可见、可启停、可绑定、可派生，但正文由平台维护，租户不能直接编辑。
4. 租户业务差异通过派生 Skill 或自定义 Skill 承载，不通过修改平台模板正文承载。
5. 线上 Agent / Workflow 运行必须引用已发布版本，不能直接跑草稿，也不能隐式漂移到最新版。
6. 平台更新必须支持影响分析、灰度、回滚和审计。
7. 第一阶段保留模块化单体，不引入独立 Skill 服务；数据和代码边界按未来可拆分设计。

## Next Phase Implementation Scope

本阶段目标是 **完整实现 Skill 分层治理闭环**，不是只做字段标记。

交付范围：

1. `skill_definition` 分层语义字段与兼容迁移。
2. 租户 Skill 列表、详情、编辑、删除、派生的权限语义调整。
3. 平台标准 Skill 模板与模板版本管理。
4. 平台核心策略包建模与运行时注入。
5. Agent 发布时 pin 具体 SkillVersion。
6. 标准 Skill / 核心策略的影响分析、发布、灰度、回滚最小闭环。
7. Debug / trace 展示 policy bundle 与 skill version 摘要。

实现顺序必须保持从“兼容字段”到“运行时 pin”再到“平台发布治理”，避免先改 UI 导致运行时语义不稳定。

## Target Classification

| Layer | Product Name | Visibility | Editable | Deletable | Binding | Maintainer |
|---|---|---:|---:|---:|---|---|
| L0 | Platform Core Policy | hidden | no | no | mandatory | platform |
| L1 | Platform Standard Skill | visible | no | no | optional/default | platform |
| L2 | Tenant Derived Skill | visible | yes | yes | optional | tenant |
| L3 | Tenant Custom Skill | visible | yes | yes | optional | tenant |
| L4 | Hidden Platform Service Capability | hidden | no | no | internal only | platform |

### L0 Platform Core Policy

平台核心策略是底层必备兜底能力，不建议在产品上称为 Skill。它可以继续复用 Skill Runtime 的部分组装能力，但在领域模型上应表达为 `PolicyBundle`。

典型内容：

- 基础安全与越权防护。
- 不暴露推理链。
- 高风险动作确认。
- 工具调用上限与敏感数据外发约束。
- 通用输出规范。
- 默认人工兜底原则。

当前候选迁移：

- `conversation-core`
- `knowledge-first`
- `safe-handoff`

### L1 Platform Standard Skill

平台标准 Skill 是租户可见的标准能力库，由平台统一维护。

典型内容：

- `general-assistant`
- `web-search`
- `sales-copilot`
- `approval-assistant`
- CRM 线索、商机、续约等标准业务能力。

租户允许：

- 查看详情、版本和变更说明。
- 启用或停用。
- 绑定到 Agent。
- 选择激活方式和优先级。
- 派生为租户自己的 Skill。

租户不允许：

- 直接编辑标准 Skill 正文。
- 删除标准 Skill。
- 修改平台维护的模板版本。

### L2 Tenant Derived Skill

租户派生 Skill 是从平台标准 Skill 某个版本复制而来的组织级能力。

它需要保留：

- `baseTemplateCode`
- `baseTemplateVersion`
- `derivedAt`
- `diffSummary`

租户可以编辑、发布、回滚、删除。平台模板后续升级时，不自动覆盖派生 Skill，只提示可合并或重新派生。

### L3 Tenant Custom Skill

租户自定义 Skill 由组织管理员通过 Skill Studio、自然语言 Authoring 或从 Agent 片段抽取创建。

它没有平台模板依赖，完整生命周期由租户维护。

### L4 Hidden Platform Service Capability

隐藏平台服务能力不是面向 Agent 的业务能力模块，而是平台实现能力。

当前例子：

- `BuiltinSkillCreatorService`
- 后续可能的 Skill 质量分析器、风险扫描器、版本迁移助手。

它不进入租户 Skill 列表，不参与 Agent 绑定，不按 Skill 计费，但其调用可以进入平台审计与成本统计。

## Target Data Model

### SkillDefinition Extension

建议在 `skill_definition` 上补充以下字段，或通过新表渐进承载：

| Field | Type | Meaning |
|---|---|---|
| `source_type` | enum | `PLATFORM_STANDARD` / `TENANT_DERIVED` / `TENANT_CUSTOM` |
| `visibility` | enum | `VISIBLE` / `HIDDEN` |
| `edit_policy` | enum | `LOCKED` / `CONFIGURABLE` / `EDITABLE` |
| `binding_policy` | enum | `MANDATORY` / `DEFAULT_ON` / `OPTIONAL` / `INTERNAL_ONLY` |
| `update_policy` | enum | `AUTO` / `MANUAL` / `PINNED` |
| `template_code` | varchar | 平台模板 code，派生 Skill 可引用 |
| `base_template_version` | integer | 派生来源版本 |
| `current_published_version_id` | bigint | 当前发布版本 |
| `latest_draft_version_id` | bigint | 最新草稿版本 |

保留 `builtin` 作为兼容字段，但不再作为产品判断主依据。

### PlatformPolicyBundle

新增平台核心策略版本表：

```text
platform_policy_bundle
- id
- bundle_code
- name
- description
- version_no
- policy_json
- prompt_fragment
- tool_policy_json
- data_egress_policy_json
- publish_status
- rollout_status
- created_by
- created_at
- published_at
```

运行时需要记录：

```text
policy_bundle_runtime_ref
- org_id
- agent_id
- session_id
- policy_bundle_code
- version_no
- rollout_group
- created_at
```

第一阶段可不单独建 `policy_bundle_runtime_ref`，先在 chat / workflow trace 中写入 `policyBundleRefs`。

### PlatformSkillTemplate

新增平台标准 Skill 模板版本表：

```text
platform_skill_template
- id
- template_code
- name
- category
- description
- status
- current_version_no
- created_at
- updated_at

platform_skill_template_version
- id
- template_code
- version_no
- spec_text
- prompt_fragment
- policy_json
- default_tool_whitelist
- default_kb_strategy
- handoff_rule
- output_contract
- risk_level
- changelog
- publish_status
- rollout_status
- created_by
- created_at
- published_at
```

### AgentWorkflowSkillRef

新增发布依赖固定表，避免 Agent 发布后因 Skill 更新漂移：

```text
agent_workflow_skill_ref
- id
- org_id
- workflow_version_id
- skill_id
- skill_version_id
- template_code
- template_version_no
- reference_mode
- created_at
```

其中 `reference_mode` 可取：

- `always-on`
- `intent-route`
- `manual`
- `fallback`
- `invoke`

## Runtime Resolution

最终运行时上下文按以下顺序组装：

1. 平台核心策略 `PlatformPolicyBundleVersion`
2. Agent 已发布版本中的 runtime policy
3. Agent 自身系统提示、模型、工具/知识边界
4. 已发布 AgentWorkflowVersion pin 住的 SkillVersion
5. 会话请求中允许的临时知识库和工具范围
6. 用户记忆和 session state

核心规则：

- L0 平台核心策略永远注入，不受租户禁用影响。
- L1 平台标准 Skill 只有被 Agent 绑定或发布版本引用后才生效。
- L2/L3 租户 Skill 必须使用已发布版本进入线上运行。
- 草稿只能用于 preview / debug，不能进入正式 chat runtime。
- Debug / trace 必须展示命中的策略和 Skill 版本摘要，但不暴露隐藏策略全文。

## Platform Update Flow

### Platform Core Policy Update

1. 平台创建新的 `PlatformPolicyBundleVersion`。
2. 通过静态校验和回归测试。
3. 选择 rollout group，例如 internal / 5% tenants / selected tenants / all tenants。
4. 运行时按 org / agent / session 解析当前灰度版本。
5. 观测错误率、工具失败率、转人工率、成本变化。
6. 全量发布或回滚。

### Platform Standard Skill Update

1. 平台创建新的 `platform_skill_template_version`。
2. 执行编译、风险扫描和样例回归。
3. 生成影响分析：
   - 多少租户启用了该标准 Skill。
   - 多少 Agent 绑定该标准 Skill。
   - 多少已发布 Workflow pin 住旧版本。
   - 多少派生 Skill 来源于旧模板。
4. 灰度更新标准 Skill 的租户实例。
5. 对已发布 Agent 保持旧版本引用，除非租户或平台执行升级动作。
6. 对草稿和新建绑定默认使用新版。

### Tenant Derived Skill Update

派生 Skill 不自动跟随平台模板。平台模板有新版本时，租户侧看到：

- 新版本摘要。
- 差异比较。
- 一键重新派生。
- 手动合并建议。

## Admin UX

### Tenant Skill Studio

列表过滤增加：

- 平台标准
- 派生自平台
- 组织自定义
- 风险等级
- 已发布 / 草稿 / 已停用

标准 Skill 详情页：

- 显示“平台维护，不可编辑”。
- 允许启停、绑定、派生。
- 显示版本、变更说明和默认工具/知识边界。

派生 / 自定义 Skill 详情页：

- 允许编辑 Spec、工具、知识、兜底和输出契约。
- 支持 preview、publish、rollback。
- 显示来源模板和差异摘要。

### Agent Builder

Agent Builder 只负责选择某个 Agent 允许使用哪些 Skill：

- 可选目录默认展示 L1/L2/L3 可见 Skill。
- 不展示 L0 和 L4。
- 编译时显式传 `skillRefs`。
- 发布前展示即将 pin 的 SkillVersion。
- 如果引用的是平台标准 Skill，需要展示版本策略：固定当前版本 / 跟随租户默认版本。

第一阶段建议所有发布版本都固定当前版本。

## API Design

### Tenant Skill APIs

保留：

- `GET /skills`
- `POST /skills`
- `GET /skills/{id}`
- `PUT /skills/{id}`
- `DELETE /skills/{id}`
- `POST /skills/preview`

扩展返回字段：

```json
{
  "sourceType": "PLATFORM_STANDARD",
  "visibility": "VISIBLE",
  "editPolicy": "CONFIGURABLE",
  "bindingPolicy": "OPTIONAL",
  "updatePolicy": "PINNED",
  "templateCode": "web-search",
  "baseTemplateVersion": 3,
  "currentPublishedVersionId": 42
}
```

新增：

- `POST /skills/{id}/derive`
- `GET /skills/{id}/versions`
- `POST /skills/{id}/versions/{versionId}/publish`
- `POST /skills/{id}/versions/{versionId}/rollback`

### Platform Skill APIs

平台运营后台使用：

- `GET /platform/skill-templates`
- `POST /platform/skill-templates`
- `GET /platform/skill-templates/{templateCode}/versions`
- `POST /platform/skill-templates/{templateCode}/versions`
- `POST /platform/skill-templates/{templateCode}/versions/{versionNo}/publish`
- `POST /platform/skill-templates/{templateCode}/versions/{versionNo}/rollout`
- `POST /platform/skill-templates/{templateCode}/versions/{versionNo}/rollback`
- `GET /platform/skill-templates/{templateCode}/impact`

### Platform Policy APIs

- `GET /platform/policy-bundles`
- `POST /platform/policy-bundles`
- `GET /platform/policy-bundles/{bundleCode}/versions`
- `POST /platform/policy-bundles/{bundleCode}/versions`
- `POST /platform/policy-bundles/{bundleCode}/versions/{versionNo}/rollout`
- `POST /platform/policy-bundles/{bundleCode}/versions/{versionNo}/rollback`

## Migration Plan

### Phase 1: Semantic Fields And Compatibility

- Add `source_type`、`visibility`、`edit_policy`、`binding_policy`、`update_policy` to `skill_definition`.
- Backfill existing rows:
  - `conversation-core`、`knowledge-first`、`safe-handoff` -> hidden platform core candidate or temporary `PLATFORM_STANDARD + MANDATORY`.
  - Other `builtin=true` rows -> `PLATFORM_STANDARD + VISIBLE + CONFIGURABLE`.
  - `builtin=false` rows -> `TENANT_CUSTOM + VISIBLE + EDITABLE`.
- Keep old `builtin` behavior for frontend compatibility.
- Update `/skills` payload.

### Phase 2: Platform Template Versioning

- Add `platform_skill_template` and `platform_skill_template_version`.
- Move `BUILTIN_SKILLS` seed content into platform template seed catalog.
- Make `ensureBuiltinSkills` synchronize from platform template versions, with safe rules:
  - create missing tenant standard Skill rows;
  - update platform-maintained fields for standard Skill;
  - preserve tenant-level enabled/binding choices;
  - never overwrite derived/custom Skill content.

### Phase 3: Core Policy Bundle

- Add `platform_policy_bundle`.
- Move mandatory hidden policy out of visible Skill list.
- Update `SkillPromptAssembler` or a new `RuntimePolicyAssembler` to inject policy bundle first.
- Add trace fields for policy bundle refs.

### Phase 4: Version Pinning

- Add `agent_workflow_skill_ref`.
- On Agent compile/publish, resolve Skill refs to concrete SkillVersion.
- Runtime loads pinned SkillVersion from the published workflow.
- Existing fallback path remains for unpublished Agents.

### Phase 5: Platform Rollout And Impact Analysis

- Add platform rollout tables and APIs.
- Add impact analysis for standard Skill and policy updates.
- Add UI in platform operations console.

## Future Service Split

第一阶段仍在当前 Spring Boot 模块化单体内实现。

建议包边界：

```text
skill/
platform/skilltemplate/
platform/policy/
agent/
metering/
audit/
```

未来拆分顺序：

1. `PlatformSkillTemplateService` 可独立为平台配置服务。
2. `PolicyBundleService` 可独立为运行时策略配置服务。
3. `SkillRuntimeResolver` 仍留在 Agent Runtime 服务，按版本引用读取配置快照。
4. `usage_meter_event` 和 trace 可进入独立计量 / 观测服务。

拆分前必须保证：

- Agent runtime 不依赖跨服务强事务。
- 发布版本持有足够配置快照或稳定版本引用。
- 平台配置服务故障时，运行时可使用最近一次可用配置。

## Implementation Progress

- 2026-04-30 已完成 Phase 1 / Phase 2 的第一批可用实现：
  - `V30__skill_governance_and_platform_audit.sql` 已落地分层语义字段。
  - 租户 `/skills` 已按 `VISIBLE/HIDDEN`、`CONFIGURABLE/EDITABLE` 收口，并支持从平台标准 Skill 派生租户 Skill。
  - `V31__platform_skill_template_and_tool_governance.sql` 已落地 `platform_skill_template` 与 `platform_skill_template_version`。
  - `/platform/skills` 已支持平台模板版本列表、草稿创建、发布、回滚，并把平台标准 Skill 同步为模板事实源。
  - 平台模板发布会同步写入 `skill_version` 发布快照与治理字段，为后续 S5 Agent 发布 pin 预留发布态事实源。
  - `V32__platform_policy_bundle_runtime_controls.sql` 已落地 `platform_policy_bundle`，并把 `conversation-core/knowledge-first/safe-handoff` 迁移为 `core-default@v1` 运行时策略包。
  - `SkillResolverService` / `SkillPromptAssembler` 已改为先注入 `PolicyBundle`，聊天 `runtimePolicy` 与 debug/chat trace 现在会回显命中的 bundle 摘要。
- 2026-04-30 已完成 S5 Agent 发布版本 pin：
  - `V33__agent_workflow_skill_ref.sql` 已落地 workflow 级 pinned skill refs。
  - `AgentDefinitionService.publishVersion(...)` 发布时会把引用的 Skill 固定到具体 `skill_version_id` / `template_version_no`。
  - `AgentWorkflowSkillRefService`、`SkillResolverService` 与 debug/chat runtime 已优先解析 pinned refs，`/ai/chat` 新增 `resolvedSkillVersions` 摘要。
  - `OrchestratorIntegrationTest.shouldKeepPublishedAgentPinnedToSkillVersionAfterSkillEdits` 已验证：skill 后续编辑不会影响旧发布版本的 tool scope / pinned version summary。
- 2026-04-30 已完成 S6 第一批治理产品化收口：
  - `/platform/policies/core` 已暴露当前 `core-default@v1` 的 code/version、来源 core skills、handoff rule 数、prompt 行数和 live published agent 数，用于平台侧核对当前全局生效策略。
  - `/platform/skills` 版本历史已补 `impact` 摘要：会展示工具/KB/risk/handoff/output 变化、命中的 pinned workflow / Agent 数，以及“后续重新发布才命中新版本”的灰度/回滚提示。
  - `/agents/{agentId}/debug` 新增 `runtimeGovernanceNotes/policyBundle/resolvedSkillVersions`；`/ai/chat` 新增 `runtimeGovernance` 与 `runtimeExecution.policyBundle/resolvedSkillVersions`，用于排障时核对命中的平台策略包与 skill snapshot。
  - `PlatformGovernanceIntegrationTest` 与 `OrchestratorIntegrationTest` 已补上述治理摘要断言，`frontend npm run build` 已通过。
- 2026-04-30 已完成 S6 第二批治理产品化收口：
  - `/platform/policies/core/versions` 已支持 `core-default` 的草稿创建、独立发布与回滚，不再需要借道平台 Skill 模板治理。
  - 平台治理页已新增 PolicyBundle 编辑器、版本历史和立即影响摘要，可直接展示 live published agent 数、样例 Agent 与 rollout / rollback 提示。
  - `PlatformGovernanceIntegrationTest` 已覆盖核心策略包 draft/publish/rollback 与 `platform.policy.version.create/publish/rollback` 审计事件；`frontend npm run build` 已通过。
- 当前仍未完成：
  - PolicyBundle 仍是全局即时生效，`rollout group` 编排表、租户分组与观测面仍未落地。
  - 更细粒度的 rollout / 灰度仍主要依赖“已发布 Agent 继续命中旧 pinned snapshot，后续重新发布命中新版本”的天然分层。

## Acceptance Criteria

- 租户 Skill 列表能区分平台标准、派生、自定义，隐藏核心策略不出现在普通列表中。
- 平台标准 Skill 可见但不可直接编辑或删除。
- 租户可从平台标准 Skill 派生自己的可编辑 Skill。
- 已发布 Agent 固定 SkillVersion，平台或租户后续编辑不会影响旧发布版本。
- Debug / trace 能展示命中的 policy bundle 和 skill version 摘要。
- 平台可发布标准 Skill 新版本，并看到影响租户 / Agent / Workflow 的分析。
- 平台核心策略可灰度和回滚。

## Risks

- 若第一阶段直接把 `conversation-core` 等从普通 Skill 列表删除，可能影响现有默认绑定和运行时解析；应先做兼容字段，再迁移运行时。
- 若平台标准 Skill 自动覆盖租户实例，可能破坏租户已有启停和绑定选择；同步策略必须只覆盖平台维护字段。
- 若 Agent 发布版本没有 pin 住 SkillVersion，后续所有版本治理都会失效。
- 若隐藏策略全文在调试接口泄露，可能增加提示词和安全策略暴露风险。

## Open Questions

- 平台标准 Skill 的租户启停是否按套餐控制，还是所有租户默认可见？
- 平台核心策略是否需要支持租户级加强版，例如金融/医疗行业包？
- 派生 Skill 的模板差异比较第一阶段做文本 diff，还是做 SpecIR diff？
- 平台标准 Skill 升级是否允许租户选择“自动跟随小版本”？
