# Skill Creator 内置化与自然语言建技能实施设计

更新时间：2026-04-23  
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文用于设计一套适配当前项目的“自然语言创建技能”能力，目标是把 `skill-creator` 从外部技能方法论，内置为平台底层能力的一部分，使管理后台的技能管理中心支持以下主流程：

**用户用自然语言描述想要的技能 -> 系统调用内置 `skill-creator` 编译链路 -> 生成标准化、结构化、可治理的 Skill 草稿 -> 用户预览、修订、发布。**

本文重点不是讨论“如何手工写一个 SKILL.md”，而是给出一套可以接在当前项目现有 `skill_definition / skill_version / skill preview / spec compiler / admin skill center` 之上的实现方案。

## 2. 需求总结

结合你的目标，本次需求可归纳为五点：

1. `skill-creator` 不再只是一个外部可选技能，而要成为系统底层内置能力。
2. 技能管理中心新增“自然语言创建技能”入口，面向管理员而不是 prompt 工程师。
3. 用户输入的是自然语言业务描述，系统输出的是标准结构化 Skill 定义，而不是一段松散 prompt。
4. 生成结果必须兼容当前项目已落地的 Skill 体系，包括：
   - `skill_definition`
   - `skill_version`
   - `draft_spec_text`
   - `previewCompile`
   - `SkillResolverService`
   - `SkillPromptAssembler`
5. 吸收到平台底层的 `skill-creator` 必须是**隐藏不可见能力**：
   - 不出现在前端技能列表
   - 不允许用户手动绑定到 Agent
   - 不作为普通内置 Skill 暴露
   - 仅在需要生成 / 精修结构化 Skill 时由系统自动调用

一句话说：

**不是把 `skill-creator` 原封不动搬进来，而是把它改造成当前系统的“Skill Spec 编译器 + Skill 生成教练”。**

## 3. 当前基础与约束

## 3.1 已有基础

从当前仓库看，系统已经具备实现这件事的关键底座：

1. **Skill 已是一等对象**
   - 已有 `skill_definition`
   - 已有 `skill_version`
   - 已有 `agent_skill_binding`

2. **Skill 已支持自然语言草稿字段**
   - `skill_definition.draft_spec_text`
   - `skill_version.spec_text`

3. **已有 Skill 预编译入口**
   - `POST /skills/preview`
   - `SkillDefinitionService.previewCompile(...)`

4. **已有通用 Spec 编译骨架**
   - `SpecCompilerService`
   - 当前已可产出 `compileSummary / warnings / specIr`

5. **已有 Skill 运行时**
   - `SkillResolverService`
   - `SkillPromptAssembler`
   - 运行时消费的是结构化字段，而不是文件系统中的原始 `SKILL.md`

这意味着：

**当前系统并不适合直接照搬 Anthropic 风格的“目录 + SKILL.md + references + scripts”作为运行时事实源，而更适合把它吸收成“技能生成规范”和“编译模板来源”。**

## 3.2 关键约束

本方案需要遵守以下现实约束：

1. 当前产品的主事实源在数据库，不在本地技能目录。
2. 当前运行时消费的是：
   - `promptFragment`
   - `toolWhitelist`
   - `kbWhitelist`
   - `handoffRule`
   - `outputContract`
   - `riskLevel`
3. 当前 `SpecCompilerService` 更接近启发式编译器，尚不是强约束的 LLM 结构化编译器。
4. 管理后台的目标用户是管理员/运营，不应要求其理解 `SKILL.md`、frontmatter、引用层级等底层细节。

因此，`skill-creator` 内置化后要承担的是：

- 把用户自然语言需求补全为 Skill 结构化意图
- 按平台标准字段生成 Skill 草稿
- 必要时额外生成“可导出的标准 SKILL.md 工件”

而不是要求用户自己维护技能文件树。

## 4. 设计结论

本次设计采用以下结论：

1. 将 `skill-creator` 定义为**平台内置编译能力**，而不是普通租户可编辑 Skill。
2. `skill-creator` 采用**隐藏内置、自动调用**模式：
   - 不进入 `/skills` 可见目录
   - 不展示在前端技能管理列表
   - 不允许在 Agent Builder 中被绑定
   - 不作为用户可运营的 skill asset 出现
3. 在技能管理中心新增“自然语言创建”模式，作为 `Skill Studio` 的主入口之一。
4. 引入一层新的中间对象：`SkillSpecIR`，作为自然语言技能描述到数据库 Skill 定义之间的结构化桥梁。
5. `skill-creator` 的职责不是直接生成最终 `promptFragment`，而是优先生成：
   - 标准字段
   - 触发场景
   - 工具/知识边界
   - 风险与兜底规则
   - 输出契约
   - 可选的扩展资源建议
6. 生成后的 Skill 草稿仍然走现有：
   - 预览编译
   - 保存草稿
   - 版本生成
   - 发布
7. 第一阶段不要求完整复刻外部 `skill-creator` 的 eval 工作流，只吸收其“技能生成规范”和“渐进式结构化”能力。

## 5. 总体架构

## 5.1 新增能力分层

建议在当前 Skill 体系上新增一层：

1. **Skill Authoring UX Layer**
   - 管理后台技能中心自然语言创建页
   - 表单编辑 / 结构化预览 / diff 对比

2. **Builtin Skill Creator Layer**
   - 内置 `skill-creator` 提示模板
   - 技能描述补全
   - 结构化输出校验

3. **Skill Spec Compile Layer**
   - `SkillSpecIR` 生成
   - `promptFragment / policy / scope` 归一化
   - warnings / risk / summary 输出

4. **Skill Persistence Layer**
   - `skill_definition`
   - `skill_version`
   - 可选 `skill_artifact`

5. **Existing Skill Runtime Layer**
   - `SkillResolverService`
   - `SkillPromptAssembler`

## 5.2 可见性与调用原则

`skill-creator` 吸收到平台底层后，产品形态上应明确为“内部编译器能力”，而不是“可见内置 Skill”。

必须满足：

1. 前端技能列表、技能详情、Agent Builder 绑定面板中都不展示 `skill-creator`。
2. `/skills` 相关目录接口不返回该能力。
3. 该能力不占用租户 skill 名额，也不参与组织级启停。
4. 仅在以下场景由系统自动调用：
   - 用户在技能管理中心点击“自然语言创建”
   - 用户对已生成的 Skill 草稿发起“继续优化/改写”
   - 后续系统需要把自然语言需求补全为结构化 SkillSpec 的后台编译场景
5. 调用方式应由 `SkillAuthoringService` 在后端编排，前端只感知“正在生成技能草稿”，不感知底层 `skill-creator` 实体。

## 5.3 核心定位变化

内置后的 `skill-creator` 不再是“教模型怎么写 skill 的说明书”，而是平台内部的：

**Skill Authoring Copilot**

它做三件事：

1. 把自然语言需求整理成结构化 SkillSpec。
2. 把结构化 SkillSpec 映射成当前系统可运行的 SkillDefinition。
3. 给出缺失信息、风险提示和建议字段补全。

## 6. 目标对象模型

## 6.1 新增 `SkillSpecIR`

建议新增一个内部中间表示 `SkillSpecIR`，用于承接 `skill-creator` 的结构化输出。

示例：

```json
{
  "skillCode": "contract-risk-guard",
  "name": "合同风险识别",
  "description": "识别合同条款中的审批、价格、法务和交付风险，并给出处理建议。",
  "skillKind": "policy-skill",
  "triggerHints": [
    "合同评审",
    "条款风险",
    "审批前检查",
    "报价承诺"
  ],
  "userIntentExamples": [
    "帮我看看这个合同有没有风险",
    "这份报价条款需要走审批吗",
    "客户要求写进 SLA，是否有问题"
  ],
  "promptFragment": "When the user asks to review commercial terms, contracts, pricing commitments, or delivery clauses, identify risk items first, explain why they matter, and recommend the next approval or handoff step.",
  "toolWhitelist": ["contract_search", "approval_policy_lookup"],
  "kbWhitelist": ["legal-kb", "approval-kb"],
  "handoffRule": "涉及法律承诺、价格折让、交付 SLA 或非标准合同条款时，必须提示人工法务或审批人确认。",
  "outputContract": "输出包含风险等级、风险点、判定依据、建议动作。",
  "riskLevel": "HIGH",
  "clarificationQuestions": [
    "是否只处理标准合同模板，还是也支持自由文本条款？"
  ],
  "warnings": [
    "未提供明确工具，建议先仅基于知识库模式创建。"
  ],
  "resourcePlan": {
    "suggestedReferences": [],
    "suggestedScripts": [],
    "exportableSkillMd": true
  }
}
```

## 6.2 `SkillSpecIR` 与现有表的映射

| `SkillSpecIR` | `skill_definition` / `skill_version` |
|---|---|
| `skillCode` | `skill_code` |
| `name` | `name` |
| `description` | `description` |
| `promptFragment` | `prompt_fragment` / `compiled_prompt_fragment` |
| `toolWhitelist` | `tool_whitelist` / `effective_tool_whitelist` |
| `kbWhitelist` | `kb_whitelist` / `effective_kb_whitelist` |
| `handoffRule` | `handoff_rule` |
| `outputContract` | `output_contract` |
| `riskLevel` | `risk_level` |
| `user natural language source` | `draft_spec_text` / `spec_text` |
| `warnings` | `warnings` |
| `compile summary` | `compile_summary` |

## 6.3 可选新增 `skill_artifact`

如果后续希望支持“导出标准技能包”或与外部 skill 生态互通，建议二阶段新增：

- `skill_artifact`
  - `id`
  - `org_id`
  - `skill_id`
  - `version_no`
  - `artifact_type`
    - `skill-md`
    - `openai-yaml`
    - `export-bundle`
  - `content`
  - `created_at`

这样可以做到：

- 运行时仍以数据库结构化字段为事实源
- 外部共享/导出时再生成 `SKILL.md`

## 7. 系统能力改造

## 7.1 后端新增服务

建议新增以下服务：

- `BuiltinSkillCreatorService`
- `SkillSpecSchemaValidator`
- `SkillAuthoringService`

职责建议如下：

### `BuiltinSkillCreatorService`

负责：

- 持有平台内置的 `skill-creator` 系统提示模板
- 调用模型，把用户自然语言描述编译为 `SkillSpecIR`
- 输出结构化 JSON，而不是自由文本
- 作为隐藏底层能力存在，不暴露为 `SkillDefinitionEntity`

### `SkillSpecSchemaValidator`

负责：

- 校验 `SkillSpecIR` 字段完整性
- 校验 `skillCode` 格式
- 校验 `riskLevel / skillKind / toolWhitelist` 枚举与格式
- 对空字段给出 warnings 而不是直接失败

### `SkillAuthoringService`

负责串联全链路：

1. 接收用户自然语言描述
2. 调用 `BuiltinSkillCreatorService`
3. 得到 `SkillSpecIR`
4. 调用现有 `previewCompile`
5. 生成 authoring preview
6. 落草稿或创建新版本
7. 控制 `skill-creator` 的自动调用时机与兜底重试

## 7.2 对现有 `SpecCompilerService` 的建议

不建议把所有逻辑都堆进当前 `SpecCompilerService`，建议采用“两段式编译”：

### 第一段：意图结构化

`natural language -> SkillSpecIR`

由 `BuiltinSkillCreatorService` 负责，重点是：

- 理解业务意图
- 归纳触发场景
- 提炼标准字段
- 发现缺失信息

### 第二段：运行时归一化

`SkillSpecIR -> compiled skill policy`

由现有 `SkillDefinitionService.previewCompile(...)` + `SpecCompilerService` 继续负责，重点是：

- 归一化工具/知识边界
- 生成 `compileSummary`
- 生成 `warnings`
- 生成 `policyJson`
- 拼装 prompt preview

这样能最大化复用现有代码，并避免一次性重写现有编译器。

## 8. 提示词与结构化输出协议

## 8.1 内置 `skill-creator` 的系统职责

建议把平台内置的 `skill-creator` 提示模板设计成以下角色：

> 你是企业智能体平台的 Skill Authoring Compiler。  
> 你的任务不是写散文式 prompt，而是把管理员提供的自然语言需求转换为标准 SkillSpec JSON。  
> 输出必须严格遵守 schema。  
> 优先生成当前平台运行时需要的字段：description、promptFragment、toolWhitelist、kbWhitelist、handoffRule、outputContract、riskLevel。  
> 若信息不足，可保留空数组/空字段，并在 warnings 与 clarificationQuestions 中说明。  
> 不生成虚构工具名或知识库 ID；无法确认时明确标记为待补充。  

## 8.2 输出协议

建议新增严格 JSON schema，至少包含：

- `skillCode`
- `name`
- `description`
- `skillKind`
- `triggerHints`
- `userIntentExamples`
- `promptFragment`
- `toolWhitelist`
- `kbWhitelist`
- `handoffRule`
- `outputContract`
- `riskLevel`
- `clarificationQuestions`
- `warnings`
- `resourcePlan`

关键规则：

1. 不允许输出 Markdown 包裹的 JSON。
2. 不允许生成 schema 外字段。
3. 不允许臆造当前系统不存在的工具 ID。
4. 若工具未知，输出空列表并写 warning。
5. `description` 要覆盖“做什么 + 什么时候用”，吸收 `skill-creator` 中对触发描述“稍微 pushy”的经验。

## 8.3 内置提示模板如何吸收外部 `skill-creator`

外部 `skill-creator` 的价值主要有三块，应选择性吸收：

1. **Skill 写作原则**
   - concise
   - progressive disclosure
   - description 决定触发
   - 区分输出契约与实现细节

2. **作者访谈思路**
   - 技能解决什么问题
   - 何时触发
   - 输出长什么样
   - 需要哪些边界和兜底

3. **验证意识**
   - 生成后需要案例预览
   - 需要 warnings
   - 需要后续 eval 能力

但以下部分不建议在第一阶段直接产品化：

- 基于文件目录的 skill workspace
- 大量 baseline/with-skill 对比跑分
- 浏览器 benchmark viewer

这些更适合作为二阶段“技能调优实验室”能力。

## 9. 技能管理中心交互设计

## 9.1 新入口

建议在技能管理中心新增两个创建入口：

1. `自然语言创建`
2. `高级手动创建`

默认主推 `自然语言创建`。

## 9.2 自然语言创建流程

### Step 1：输入自然语言描述

用户输入例如：

> 我想做一个审批前风险检查技能。  
> 当销售提交特殊折扣、非标准合同条款、实施交付承诺时，先帮我判断有没有风险。  
> 如果命中高风险要提醒转人工审批。  
> 输出里要包含风险等级、原因和建议动作。

### Step 2：系统生成 Skill 草稿

界面返回三块内容：

1. **结构化技能卡**
   - 名称
   - 描述
   - 风险等级
   - 工具/知识边界

2. **Prompt / Policy 预览**
   - prompt fragment
   - handoff rule
   - output contract

3. **待确认项**
   - 缺失工具
   - 缺失知识库
   - 歧义字段

### Step 3：用户确认与微调

用户可编辑：

- 名称 / 编码
- 描述
- promptFragment
- 工具白名单
- 知识库白名单
- handoffRule
- outputContract
- riskLevel

### Step 4：预览编译

复用现有 `POST /skills/preview` 返回：

- `promptPreview`
- `effectiveTools`
- `effectiveKbs`
- `warnings`
- `compileSummary`
- `policyJson`

### Step 5：保存草稿 / 创建版本

确认后创建：

- `skill_definition`
- `skill_version`

## 9.3 建议的页面布局

建议使用三栏或上下双层布局：

1. 左侧：自然语言输入与推荐示例
2. 中间：结构化字段编辑器
3. 右侧：编译预览 / warnings / prompt 预览

这样能体现：

- 左边是“用户语言”
- 中间是“系统结构化定义”
- 右边是“实际运行效果”

## 10. API 设计

## 10.1 新增 Authoring API

建议新增：

### `POST /skills/authoring/generate`

请求：

```json
{
  "sourceText": "用户自然语言描述",
  "preferredName": "可选",
  "preferredSkillCode": "可选",
  "toolCandidates": ["可选，来自当前组织工具目录"],
  "kbCandidates": ["可选，来自当前组织知识库目录"]
}
```

响应：

```json
{
  "sourceText": "原始描述",
  "skillSpec": {
    "skillCode": "approval-risk-guard",
    "name": "审批前风险检查",
    "description": "...",
    "promptFragment": "...",
    "toolWhitelist": [],
    "kbWhitelist": [],
    "handoffRule": "...",
    "outputContract": "...",
    "riskLevel": "HIGH",
    "warnings": ["未找到明确工具，请补充。"],
    "clarificationQuestions": ["是否需要接入审批规则知识库？"]
  },
  "preview": {
    "promptPreview": "...",
    "compileSummary": ["..."],
    "policyJson": {}
  }
}
```

### `POST /skills/authoring/refine`

用于在已有草稿基础上继续优化。

请求：

```json
{
  "sourceText": "请把输出改成包含处理建议和负责人",
  "currentSkillSpec": { }
}
```

用途：

- 增量修改 Skill 草稿
- 支持类似“继续编辑”的自然语言体验

### `POST /skills/authoring/create`

用于一步保存为 Skill。

请求：

```json
{
  "sourceText": "原始自然语言",
  "skillSpec": { }
}
```

行为：

- 调 `SkillSpecSchemaValidator`
- 调 `SkillDefinitionService.createSkill(...)`
- 自动创建首个 `skill_version`

## 10.2 可见性约束

新增 authoring API 后，仍需显式约束：

1. `skill-creator` 不通过 `/skills`、`/agents/{agentId}/skills` 等业务接口对外暴露。
2. 前端页面不能出现名为 `skill-creator` 的技能卡、技能详情或绑定项。
3. 前端只展示“自然语言创建 / AI 生成技能草稿”这样的产品文案，不展示底层能力名称。
4. 若后端需要审计，可在内部日志或 trace 中记录 `builtin-skill-creator` 调用，但不回传为用户可操作对象。

## 10.3 兼容现有接口

本方案不替换现有：

- `POST /skills`
- `PUT /skills/{id}`
- `POST /skills/preview`

而是在其前面增加 authoring 层。

这样：

- 手动创建路径继续可用
- 自然语言创建路径走新入口
- 底层落库与预览逻辑复用现有实现

## 11. 数据库改造建议

## 11.1 第一阶段

第一阶段可只新增极少字段：

### `skill_version`

建议增加：

- `source_type`
  - `manual`
  - `builtin-skill-creator`
- `spec_ir_json`
  - 保存 `SkillSpecIR`
- `authoring_notes`
  - 保存 warnings / clarificationQuestions 的快照

目的：

- 可追踪这版 skill 是否由自然语言生成
- 可复盘生成时的结构化中间结果

## 11.2 第二阶段

如需完整技能包导出与实验能力，再考虑新增：

- `skill_artifact`
- `skill_eval_run`
- `skill_eval_case`
- `skill_eval_report`

## 12. 与现有运行时的衔接

## 12.1 不改运行时事实源

本设计明确建议：

**线上运行时继续以 `skill_definition / skill_version` 中的结构化字段为事实源。**

不要在第一阶段让聊天运行时直接读取导出的 `SKILL.md`。

原因：

1. 当前运行时已经稳定围绕数据库字段实现。
2. `SKILL.md` 更适合作为导出工件和可读资产。
3. 如果让运行时再解析 Markdown 技能文件，会引入第二套事实源。

## 12.2 可选导出能力

如果产品上需要“导出为标准 Skill 包”，建议在详情页提供：

- 导出 `SKILL.md`
- 导出 `agents/openai.yaml`
- 导出 bundle zip

但这属于治理/互通能力，不影响运行时主链路。

## 13. 风险与防错设计

## 13.1 风险一：生成内容过于泛化

现象：

- 生成的描述像“通用助手”
- 无明显触发边界

对策：

- 在内置提示模板中强制要求写明“何时触发”
- 对 description 过短或过泛给 warning
- 如果触发边界不清，则不允许直接发布为高风险技能

## 13.2 风险二：臆造工具和知识库

现象：

- 模型编出不存在的工具名

对策：

- `generate` 接口把当前组织工具/知识库候选传入模型
- 服务端二次校验，不存在就移除并给 warning
- UI 高亮“待绑定资源”

## 13.3 风险三：用户以为自然语言生成后就一定可用

现象：

- 生成的 Skill 还没验证就被启用

对策：

- 所有自然语言新建 Skill 默认 `draft`
- 强制经过一次 preview compile
- 高风险技能发布前建议增加 smoke case

## 13.4 风险四：形成第二套 Skill 写法

现象：

- 一部分人写数据库字段
- 一部分人写 SKILL.md

对策：

- 明确数据库结构化 Skill 是系统事实源
- `SKILL.md` 是导出物，不是主编辑对象
- 管理后台只暴露结构化字段和自然语言创建入口

## 13.5 风险五：误把隐藏编译器做成可见 Skill

现象：

- 前端把 `skill-creator` 渲染为内置 Skill 卡片
- Agent Builder 允许绑定 `skill-creator`
- 用户误以为它是业务能力 Skill，而不是平台编译器

对策：

- 后端不为其创建可枚举的 `SkillDefinitionEntity`
- `/skills` 列表接口层面直接排除
- 前端以“AI 生成技能草稿”表达产品能力，不展示底层实现名
- 审计、trace 和监控可以记录 `builtin-skill-creator`，但仅在内部可见

## 14. 分阶段实施建议

## Phase 1：自然语言建技能最小闭环

目标：

- 技能中心可通过自然语言生成 Skill 草稿
- 可预览、编辑、保存

范围：

1. 新增 `BuiltinSkillCreatorService`
2. 新增 `POST /skills/authoring/generate`
3. 前端新增“自然语言创建”入口
4. 生成 `SkillSpecIR`
5. 复用现有 `previewCompile`
6. 保存到 `skill_definition / skill_version`

不做：

- 自动评测
- 导出 skill bundle
- 多轮复杂追问 agent

## Phase 2：自然语言精修与结构化追问

目标：

- 支持“继续优化这个技能”
- 支持针对缺失信息自动追问

范围：

1. 新增 `refine` 接口
2. 支持 authoring session
3. 支持 clarification questions 的交互闭环

## Phase 3：技能调优实验室

目标：

- 吸收外部 `skill-creator` 的 eval 方法论

范围：

1. 测试样例
2. baseline / generated skill 对比
3. benchmark viewer
4. 版本质量评分

## Phase 4：技能导出与生态互通

目标：

- 与外部 skill 格式互通

范围：

1. 导出 `SKILL.md`
2. 导出 `openai.yaml`
3. 导出标准 bundle

## 15. 推荐实施顺序

建议按下面顺序落地：

1. 先实现 `SkillSpecIR` 和 `BuiltinSkillCreatorService`
2. 再加 `POST /skills/authoring/generate`
3. 接着在技能中心加“自然语言创建”页面
4. 页面先做“生成 -> 人工确认 -> preview -> 保存”的单轮闭环
5. 最后再考虑多轮 refine、评测和导出

## 16. 最终结论

结合当前系统现状，最合理的做法不是把外部 `skill-creator` 当成普通 Skill 接进来，而是：

**把 `skill-creator` 产品化为平台底层的 Skill Authoring Compiler。**

它在架构上的正确位置是：

- 前面接住管理员的自然语言意图
- 中间生成 `SkillSpecIR`
- 后面复用现有 Skill 编译、预览、落库、运行时链路

这样做的收益是：

1. 与当前 `skill_definition / skill_version / previewCompile / runtime resolver` 完全兼容。
2. 管理员不用理解底层 `SKILL.md` 结构，也能创建标准化技能。
3. 后续仍可把结构化 Skill 导出为标准技能包，对接外部 skill 生态。
4. 第一阶段实现成本可控，不需要重做整个 Skill 系统。

如果用一句产品语言来定义这次改造：

**让“创建技能”从写 prompt，升级为写需求；让系统把需求编译成可治理的 Skill 资产。**
