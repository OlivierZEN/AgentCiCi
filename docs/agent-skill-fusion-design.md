# Agent Spec 与 Skill 融合设计文档

更新时间：2026-04-18
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文用于统一当前两条已经成形的产品与架构路线：

- `自然语言 Agent Spec -> AI 编译 workflow code -> 服务端托管执行`
- `skill registry -> skill 解析 -> 运行时约束工具 / 知识 / 输出`

目标不是让其中一条替代另一条，而是把它们收敛成一套可以长期演进的融合方案，满足：

1. 保持项目已确定的文本优先 Agent Builder 主线。
2. 让 `skill` 成为真正可复用、可治理、可多租户隔离的能力模块。
3. 让 `Agent` 与 `skill` 共用一套自然语言编写与编译骨架，而不是维护两套割裂系统。
4. 为后续版本、调试、发布、灰度、审计保留稳定边界。

本文件是以下两份文档的融合落地版：

- `docs/natural-language-agent-workflow-framework.md`
- `docs/skill-system-design.md`

## 2. 核心判断

### 2.1 Agent Spec 不被 skill 直接替代

`skill` 可以吸收一部分原本写在 Agent Spec 中的自然语言能力描述，但不能直接取代 Agent Spec。

原因：

- `skill` 解决的是 **可复用能力沉淀**。
- `Agent Spec` 解决的是 **顶层工作流编排与发布**。
- `Agent` 是面向用户和渠道发布的产品对象。
- `skill` 是被 `Agent` 复用的能力模块，不应天然承担顶层产品身份。

因此，项目不应演进成“只有 skill、没有 Agent Spec”的体系。

### 2.2 二者应共用同一种作者心智

虽然 `Agent` 与 `skill` 的运行时职责不同，但对用户而言，二者都应支持：

- 用自然语言描述目标和流程
- 配置工具 / 知识边界
- 配置风险与人工兜底
- 编译预览
- 版本与发布治理

也就是说：

**同一种自然语言 Spec 写法，不同的编译目标与运行时承载。**

### 2.3 二者应共用编译骨架，不共用产品身份

推荐采用：

- **共享 Spec Layer / Compiler Layer**
- **分离 Skill Runtime 与 Agent Workflow Runtime**

这样既避免双系统重复建设，又不混淆 `skill` 与 `Agent` 的职责。

## 3. 统一对象模型

### 3.1 Tool

`Tool` 是最小执行单元，例如：

- CRM 查询
- 审批待办拉取
- MCP 工具调用
- 知识检索适配器

它负责真正的执行，不负责业务复用语义。

### 3.2 Skill

`skill` 是位于 `Agent` 和 `Tool` 之间的可复用业务能力模块。

它负责描述：

- 适用场景
- 行为约束
- 提示词片段
- 知识边界
- 工具白名单
- 风险等级
- 兜底规则
- 输出契约

在融合设计下，`skill` 进一步分成两类：

1. **Policy Skill**
   - 主要产出 prompt / policy / scope
   - 适合当前已落地的聊天运行时
   - 示例：`conversation-core`、`knowledge-first`

2. **Workflow Skill**
   - 主要产出可复用子流程或受限 subflow
   - 可被 `Agent workflow` 显式引用
   - 示例：`quote-workflow`、`crm-query`

### 3.3 Agent Spec

`Agent Spec` 是顶层 Agent 的自然语言规格说明，是 Agent Builder 的主输入。

它负责描述：

- Agent 的角色与业务目标
- 顶层流程和关键分支
- 对 skill 的引用与编排
- 对工具、知识、模型、权限的整体约束
- 发布前的版本与治理要求

### 3.4 Agent

`Agent` 是面向用户发布的顶层产品对象，承载：

- persona
- 渠道
- 发布状态
- 默认模型
- 已发布版本
- 可绑定的 skill 集合

### 3.5 Workflow Version

`Workflow Version` 是 Agent Spec 编译后的受控执行体，负责：

- 作为线上运行时入口
- 固定依赖版本
- 固定权限边界
- 支持发布、回滚、调试、审计

## 4. 统一关系图

推荐关系如下：

- 一个 `Agent` 可以绑定多个 `Skill`
- 一个 `Skill` 可以被多个 `Agent` 复用
- 一个 `Skill` 可以引用多个 `Tool`
- 一个 `Agent Spec` 在编译时可以显式引用多个 `Skill`
- 一个 `Agent Workflow Version` 在发布时必须 pin 住具体 `SkillVersion`

这里要特别区分两种关系：

1. **绑定关系**
   - `Agent` 平时默认挂哪些 skill
   - 用于会话运行时解析和构建器默认能力库

2. **编译依赖关系**
   - 某个 Agent Workflow Version 在编译时依赖了哪些 `SkillVersion`
   - 用于版本可复现和发布回滚

不能只保留“逻辑绑定”而不 pin 版本，否则 Agent 发布后会被后续 skill 修改漂移。

## 5. 统一作者模型

### 5.1 作者写的是统一 Spec，不是两套完全不同语言

无论是编写 `Agent` 还是 `skill`，建议都使用统一的文本输入框架：

- 角色 / 场景
- 目标
- 输入与触发条件
- 处理步骤
- 关键分支
- 知识与工具边界
- 风险与转人工规则
- 输出要求

差别只在“编译目标”：

- 目标是顶层 Agent：编译成 `workflow code`
- 目标是 skill：编译成 `policy skill` 或 `workflow skill`

### 5.2 Skill Builder 与 Agent Builder 共享同一套编辑器骨架

前端建议演进为：

1. `Agent Builder`
   - 用于创建和发布完整 Agent
   - 支持引用现有 skill
   - 支持从局部流程抽取 skill

2. `Skill Builder`
   - 用于维护组织能力库
   - 支持从模板创建
   - 支持从 Agent 中抽取
   - 支持独立编译、预览、发布

两者在 UI 上不应是两套完全不同的心智模型，而应复用：

- Spec 编辑区
- 资源授权区
- 编译结果区
- 版本 / 发布区

### 5.3 推荐的产品入口

建议保留两条入口：

1. **Agent Builder 内引用 / 抽取 skill**
2. **管理端独立维护 skill 库**

这样既满足业务用户从 Agent 出发，也满足组织管理员维护标准能力库。

## 6. 共享编译架构

### 6.1 统一的编译流水线

推荐收敛为一套共享编译管线：

1. 接收自然语言 Spec 与授权边界
2. 抽取结构化中间表示 `SpecIR`
3. 进行静态校验与风险分析
4. 按目标类型走不同 emitter
5. 生成预览、摘要、警告与版本元数据

### 6.2 统一中间表示 `SpecIR`

编译器内部建议统一沉淀如下 IR：

```json
{
  "role": "售前跟进",
  "goal": "完成客户答复与后续动作建议",
  "triggers": ["用户提问", "报价请求"],
  "steps": [
    "识别意图",
    "检索知识",
    "必要时调用 CRM",
    "生成答复"
  ],
  "decisionRules": [
    "命中不足时追问或转人工",
    "涉及价格承诺时必须确认"
  ],
  "toolPolicy": {
    "allowedTools": ["crm-query", "quote-generator"]
  },
  "knowledgePolicy": {
    "allowedKnowledgeBases": ["sales-kb"]
  },
  "handoffPolicy": {
    "requireHumanOn": ["pricing_commitment", "unclear_permission"]
  },
  "outputContract": "结论、依据、下一步建议",
  "references": {
    "skills": ["sales-copilot"]
  }
}
```

### 6.3 Skill 编译目标

`compileSkill(spec)` 推荐输出：

- `compiledPromptFragment`
- `compiledPolicyJson`
- `effectiveToolWhitelist`
- `effectiveKbWhitelist`
- `riskLevel`
- `warnings`
- `compileSummary`

若 skill 属于 `workflow skill`，还可以额外产出：

- `compiledSubflowCode`
- `subflowManifest`
- `subflowPreview`

### 6.4 Agent 编译目标

`compileAgent(spec)` 推荐输出：

- `workflowCode`
- `workflowManifest`
- `workflowPreview`
- `compileSummary`
- `warnings`
- `resolvedSkillRefs`

其中 `resolvedSkillRefs` 必须带具体版本号或 `skillVersionId`，保证发布可复现。

## 7. 统一运行时

### 7.1 会话运行时：适合当前已落地聊天链路

当前项目已实现的 skill 运行时，适合作为 **Conversation Runtime**：

- 解析当前 Agent 绑定的 skill
- 合并 prompt 片段
- 过滤工具白名单
- 过滤知识范围
- 组装最终 system prompt

这条链路适合：

- 通用聊天助手
- 工具受限问答
- 轻量业务协作
- 不需要完整工作流执行的场景

### 7.2 工作流运行时：适合已发布 Agent

对于复杂 Agent，推荐使用 **Workflow Runtime**：

- 加载 `AgentWorkflowVersion`
- 注入 runtime context
- 注入已授权工具 / 知识
- 执行 workflow code
- 在需要时调用 `invokeSkill(...)`
- 记录 trace、异常、审计

### 7.3 `invokeSkill(...)` 作为两者的桥梁

融合运行时建议提供平台级能力：

```ts
await ctx.invokeSkill("sales-copilot", {
  input,
  mode: "policy" | "subflow"
});
```

其语义为：

- 若目标是 `policy skill`，则把 skill 的策略合并进当前阶段上下文
- 若目标是 `workflow skill`，则执行其受限 subflow

这样可以让 Agent workflow 显式复用 skill，而不是只靠 prompt 拼接。

## 8. 数据模型

## 8.1 Skill 侧

### SkillTemplate

平台级模板，用于承载内置标准 skill。

关键字段：

- `id`
- `templateCode`
- `name`
- `category`
- `builtin`
- `defaultSpecText`
- `defaultPolicyJson`
- `status`

### SkillDefinition

组织级 skill 逻辑对象。

关键字段：

- `id`
- `orgId`
- `skillCode`
- `name`
- `description`
- `sourceType`：`builtin-derived` / `custom`
- `templateCode`
- `latestDraftVersionId`
- `currentPublishedVersionId`
- `enabled`
- `createdBy`
- `createdAt`
- `updatedAt`

### SkillVersion

skill 的不可变编译版本。

关键字段：

- `id`
- `skillId`
- `versionNo`
- `specText`
- `skillKind`：`policy` / `workflow`
- `compiledPromptFragment`
- `compiledPolicyJson`
- `compiledSubflowCode`
- `subflowManifest`
- `subflowPreview`
- `compileSummary`
- `warnings`
- `publishStatus`
- `createdBy`
- `createdAt`

### SkillAsset

可选扩展，用于承载 references / templates / example / bundle 附件。

关键字段：

- `id`
- `skillId`
- `skillVersionId`
- `assetType`
- `storageUri`
- `checksum`
- `createdAt`

## 8.2 Agent 侧

### AgentDefinition

顶层 Agent 产品对象。

关键字段：

- `id`
- `orgId`
- `name`
- `description`
- `status`
- `defaultModel`
- `publishedVersionId`
- `createdBy`
- `createdAt`
- `updatedAt`

### AgentSpec

用户维护的自然语言规格输入。

关键字段：

- `id`
- `agentId`
- `sourceText`
- `rolePrompt`
- `goalDescription`
- `workflowDescription`
- `handoffRules`
- `knowledgeScope`
- `toolScope`
- `skillRefs`
- `versionLabel`
- `createdAt`

### AgentWorkflowVersion

Agent 编译后的受控运行版本。

关键字段：

- `id`
- `agentId`
- `specId`
- `versionNo`
- `workflowCode`
- `workflowManifest`
- `workflowPreview`
- `compileSummary`
- `warnings`
- `publishStatus`
- `createdBy`
- `createdAt`

### AgentSkillBinding

Agent 默认绑定 skill 的关系表。

关键字段：

- `id`
- `orgId`
- `agentId`
- `skillId`
- `activationMode`
- `activationCondition`
- `priority`
- `enabled`
- `createdAt`

### AgentWorkflowSkillRef

推荐新增，用于固定某个 WorkflowVersion 依赖的 skill 版本。

关键字段：

- `id`
- `workflowVersionId`
- `skillId`
- `skillVersionId`
- `referenceMode`：`always-on` / `invoke` / `fallback`
- `createdAt`

## 9. 多租户治理原则

### 9.1 技术隔离

所有 skill / Agent / tool / KB / trace 都必须显式带：

- `orgId`
- `userId`

运行时任何一次调用都不能脱离租户上下文。

### 9.2 平台模板与组织派生分离

推荐采用：

- 平台维护 `SkillTemplate`
- 组织内生成 `SkillDefinition`
- 组织发布 `SkillVersion`

组织不应直接修改平台模板正文，而应通过“派生”形成自己的 skill 定义。

### 9.3 不允许租户直接运行任意脚本

融合后即使 skill 具备 `workflow skill` 能力，也不能演进成“组织上传任意脚本直接执行”。

推荐约束：

- 只能生成平台受限 SDK / 受限 DSL 代码
- 只能调用平台暴露的 runtime context
- 只能访问已授权 tool / KB / model
- 所有执行都带审计、超时、重试和权限校验

### 9.4 发布与回滚

所有线上运行必须引用：

- `published AgentWorkflowVersion`
- `published SkillVersion`

不能直接跑草稿内容，也不能在已发布 Agent 中隐式漂移到 skill 最新版本。

## 10. 产品与交互建议

### 10.1 Agent Builder

Agent Builder 应继续作为当前主线产品入口。

新增能力：

- 引用 skill
- 查看 skill 版本
- 从当前流程片段抽取 skill
- 编译时展示依赖的 skill 列表与版本

### 10.2 Skill Builder

Skill Builder 可以放在管理端，也可以在 Agent Builder 内以弹层 / 抽屉方式提供轻入口。

建议支持：

- 从模板创建
- 从零创建
- 从 Agent 流程抽取
- 预览编译
- 发布
- 回滚
- 绑定到多个 Agent

### 10.3 调试体验

调试界面应统一返回：

- 当前命中的 skill
- 最终 prompt 摘要
- 有效工具范围
- 有效知识范围
- 风险提示
- 工作流执行轨迹
- `invokeSkill(...)` 调用链

## 11. 与当前实现的衔接

当前仓库已经具备融合方案的部分骨架：

1. `skill_definition + agent_skill_binding` 已落地
2. `SkillResolverService + SkillPromptAssembler` 已接入聊天运行时
3. Agent Builder 已有自然语言 Spec、编译预览和 workflow preview
4. 管理端已支持 skill CRUD / preview / bindings

但当前还存在两个明显缺口：

1. `skill` 侧尚未形成真正的 `SkillVersion / publish / rollback`
2. `Agent workflow` 与 `skill runtime` 仍是并行骨架，尚未由共享编译与版本引用打通

## 12. 推荐落地阶段

### Phase A：共享建模

目标：先把概念和数据模型对齐。

范围：

- 为 skill 补 `draftSpecText`
- 新增 `SkillVersion`
- 为 Agent 编译结果增加 `resolvedSkillRefs`

### Phase B：共享编译器

目标：让 Agent 与 skill 共用 `SpecIR`。

范围：

- 抽取统一 `SpecCompiler`
- 拆分 `compileSkill(...)` 与 `compileAgent(...)`
- 统一 warnings / compileSummary / risk analysis

### Phase C：版本与运行时融合

目标：让 workflow 与 skill 在运行时真正可组合。

范围：

- 支持 `invokeSkill(...)`
- 新增 `AgentWorkflowSkillRef`
- 发布时 pin `SkillVersion`

### Phase D：统一治理与调试

目标：让融合体系真正可上线运营。

范围：

- publish / rollback / debug trace
- 审计报表
- skill 调用链可视化
- Agent 与 skill 的依赖影响分析

## 13. 推荐结论

推荐将当前项目的统一方向定义为：

**Agent Spec 负责顶层编排，skill 负责可复用能力沉淀，两者共用自然语言 Spec 与编译骨架，但保留各自独立的运行时职责与发布治理边界。**

这条路线的好处是：

1. 不推翻已经确定的 Agent Builder 主线。
2. 不让 skill 退化成纯 prompt 标签。
3. 不让系统陷入两套自然语言编译器长期分叉。
4. 能同时满足多租户、版本治理、运行时安全和后续复用扩展。
