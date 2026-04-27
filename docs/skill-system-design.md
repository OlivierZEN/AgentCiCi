# Skill 体系设计文档

更新时间：2026-04-17  
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文给出一套适配当前项目的 `skill` 体系设计，目标是在现有：

- 聊天编排链路
- 工具编排链路
- RAG 检索链路
- Agent Builder

之上，补出一层真正可运行、可治理、可复用的 `skill` 能力。

本文同时覆盖两类能力：

- **内置标准 skill**：系统出厂自带、由平台维护、可作为默认能力模块复用。
- **用户自定义 skill**：组织内管理员或运营根据业务需要创建、调优、发布的能力模块。

## 2. 当前现状

结合当前仓库，系统已经具备一些“像 skill 的素材”，但还没有真正独立的 skill 运行时：

1. 聊天链路当前仍以固定 system prompt + RAG + 全量工具为主。
2. 工具层已经有内置工具和 MCP 工具编排，但缺少按场景过滤与组合。
3. Agent Builder 已支持 `systemPrompt`、`specText`、`toolIds`、`knowledgeBaseIds`，但当前更多是编译预览和 UI 预设，尚未接入真实会话运行时。
4. 前端已经存在“系统内置智能体”和“已发布智能体”的产品形态，但它们还不是由统一 skill registry 驱动。

因此，当前最合适的方向不是再叠一层前端 preset，而是把 `skill` 做成后端一等公民。

## 3. 核心定义

### 3.1 Skill 是什么

`skill` 是一个**可复用的业务能力模块**，位于 `Agent` 和 `Tool` 之间。

它不是：

- 单个工具函数
- 纯提示词片段
- 独立发布的完整智能体

它应该同时描述：

- 适用场景
- 行为约束
- 提示词片段
- 知识边界
- 工具白名单
- 风险等级
- 人工兜底规则
- 输出契约

### 3.2 Agent、Skill、Tool 的关系

- **Tool**：最小执行单元，例如查询客户、拉审批、调用 MCP。
- **Skill**：可复用的业务能力模块，决定“何时查知识、何时用哪些工具、输出格式与兜底规则”。
- **Agent**：面向用户的顶层产品对象，负责承载 persona、渠道、发布状态和一组 skill 组合。

推荐关系：

- 一个 `Agent` 可以绑定多个 `Skill`
- 一个 `Skill` 可以被多个 `Agent` 复用
- 一个 `Skill` 可以引用多个 `Tool`

## 4. 设计目标

### 4.1 目标

1. 让系统具备一套可内置、可扩展、可组合的标准能力库。
2. 让用户可以创建组织级自定义 skill，而不是每次都从头堆 prompt。
3. 让 skill 真正进入聊天运行时，而不是只停留在构建器预览层。
4. 保持当前“自然语言 Spec -> 编译 -> 托管执行”的路线不变。
5. 为后续版本治理、灰度、调试和审计保留稳定边界。

### 4.2 非目标

第一阶段不追求：

- 图形化 skill 编排器
- 任意代码执行型 skill 脚本市场
- 跨组织共享公共 skill 商店

## 5. Skill 分类

建议将 skill 分为三层。

### 5.1 平台基础 skill

由系统强制挂载，不向普通用户暴露删除。

示例：

- `conversation-core`：回答风格、语言跟随、Markdown 输出、禁止暴露思维链。
- `knowledge-first`：优先知识检索，命中不足时追问或降级。
- `safe-handoff`：高风险、权限不清、价格承诺等场景必须转人工。

### 5.2 内置标准 skill

由平台预置、组织可启停、可作为模板复用。

建议首批内置：

- `general-assistant`
  - 通用问答、知识检索、基础协作分流
- `sales-copilot`
  - 售前问答、客户档案查询、报价前置判断
- `approval-assistant`
  - 审批待办拉取、流程规则检索、催办建议
- `crm-query`
  - CRM 查询导向、信息汇总、下一步建议
- `quote-workflow`
  - 报价生成、折扣风险提示、人工确认

### 5.3 用户自定义 skill

由组织管理员在 Agent Builder 或管理端创建。

典型来源：

- 复用内置标准 skill 后做组织化改写
- 从零编写业务自然语言 Spec
- 把某个成熟 Agent 中的局部流程抽取为 skill

## 6. 总体架构

### 6.1 运行时分层

建议在当前架构中新增一层 `Skill Runtime Layer`：

1. **Base System Policy**
   - 平台不可变安全与输出规范
2. **Agent Identity Layer**
   - Agent 的角色、人设、渠道信息
3. **Skill Resolution Layer**
   - 解析当前会话生效的 skill 集合
4. **RAG / Tool Scope Layer**
   - 根据 skill 过滤知识库与工具
5. **Execution Layer**
   - 调用模型、执行工具、写入会话与审计

### 6.2 技术位置

建议新增以下服务：

- `SkillDefinitionService`
- `SkillResolverService`
- `SkillPromptAssembler`
- `SkillBindingService`
- `SkillCompileService`（第二阶段）

其中：

- `SkillResolverService` 接到 `ChatOrchestratorService` 前面
- `SkillPromptAssembler` 负责把 Agent + Skill + 平台策略组装成最终 system prompt
- `ToolOrchestratorService` 改为支持按 skill 白名单过滤工具

## 7. 数据模型

### 7.1 SkillTemplate

平台级模板，主要承载内置标准 skill。

建议字段：

- `id`
- `skillCode`
- `name`
- `description`
- `builtin`
- `category`
- `status`
- `defaultPromptFragment`
- `defaultToolWhitelist`
- `defaultKbStrategy`
- `defaultHandoffRule`
- `defaultOutputContract`
- `riskLevel`
- `createdAt`
- `updatedAt`

### 7.2 SkillDefinition

组织级实际 skill 定义，既可来源于内置模板，也可纯自定义。

建议字段：

- `id`
- `orgId`
- `skillCode`
- `name`
- `description`
- `sourceType`
  - `builtin-derived` / `custom`
- `templateCode`
- `enabled`
- `draftSpecText`
- `systemPromptFragment`
- `toolWhitelist`
- `kbWhitelist`
- `handoffRule`
- `outputContract`
- `priority`
- `version`
- `createdBy`
- `createdAt`
- `updatedAt`

### 7.3 SkillVersion

用于管理编译结果、审核状态和可回滚版本。

建议字段：

- `id`
- `skillId`
- `versionNo`
- `specText`
- `compiledPromptFragment`
- `compiledPolicyJson`
- `compileSummary`
- `publishStatus`
- `createdBy`
- `createdAt`

### 7.4 AgentSkillBinding

Agent 与 skill 的绑定关系。

建议字段：

- `id`
- `orgId`
- `agentId`
- `skillId`
- `activationMode`
  - `always-on` / `intent-route` / `manual`
- `activationCondition`
- `priority`
- `enabled`
- `createdAt`

## 8. 配置与合并规则

### 8.1 Prompt 合并顺序

建议从低到高按下面顺序拼装：

1. 平台基础 system policy
2. Agent 基础 persona
3. 技能 prompt 片段
4. 会话态补充规则
5. 当前用户输入

### 8.2 冲突处理

不同 skill 同时生效时，规则不要简单字符串拼接，建议结构化合并：

- `toolWhitelist`：取并集，再经过组织权限裁剪
- `kbWhitelist`：取并集，再经过用户显式选择裁剪
- `handoffRule`：取并集
- `riskLevel`：取最高等级
- `outputContract`：只允许一个主输出契约，按 `priority` 选主
- `denyRules`：硬拒绝优先级最高

### 8.3 兜底原则

当 skill 解析失败或绑定为空时：

- 保留平台基础 policy
- 退回默认通用 skill
- 继续允许最小安全问答链路

## 9. 运行时流程

### 9.1 会话入口

会话请求进入后，推荐处理顺序：

1. 识别当前 `orgId / userId / sessionId / agentId`
2. 解析当前 Agent 绑定的 skill
3. 按会话上下文补充动态 skill
4. 计算最终知识范围和工具范围
5. 组装最终 system prompt
6. 执行模型调用与工具循环
7. 记录命中的 skill、工具调用和兜底事件

### 9.2 对现有代码的改造点

#### ChatOrchestratorService

当前职责是：

- 路由模型
- 拉 RAG
- 拉工具定义
- 组装 messages
- 执行 tool loop

建议新增：

- `resolveSkills(...)`
- `resolveEffectiveKnowledgeBases(...)`
- `resolveEffectiveTools(...)`
- `buildSystemPrompt(...)`

#### ToolOrchestratorService

当前返回的是“组织可用工具总表”。  
建议升级为：

- `getToolDefinitions(orgId, allowedToolNames)`
- `executeTool(...)` 保持不变，但执行前要校验是否在当前 skill allowlist 中

#### AgentCompileService

当前已经具备：

- `specText`
- `toolIds`
- `knowledgeBaseIds`
- `handoffRule`
- `workflowManifest`
- `workflowPreview`

这意味着它天然适合作为用户自定义 skill 的编译入口基础。  
建议第二阶段将它从“只编译 Agent”扩展到“可编译 SkillSpec”。

## 10. 内置标准 skill 建议清单

### 10.1 conversation-core

- 类型：平台基础 skill
- 作用：
  - 语言跟随
  - Markdown 输出
  - 不输出思维链
  - 保持简洁和专业

### 10.2 knowledge-first

- 类型：平台基础 skill
- 作用：
  - 优先走知识库
  - 命中不足时追问
  - 禁止脱离知识边界编造制度性答案

### 10.3 general-assistant

- 类型：内置标准 skill
- 作用：
  - 通用问答
  - 基础协作分流
  - 默认兜底

### 10.4 sales-copilot

- 类型：内置标准 skill
- 作用：
  - 销售问题识别
  - CRM 查询
  - 报价前置判断
  - 涉及承诺时转人工

### 10.5 approval-assistant

- 类型：内置标准 skill
- 作用：
  - 待审批项拉取
  - 流程规则对照
  - 催办建议与升级建议

## 11. 用户自定义 skill 产品设计

### 11.1 创建入口

建议提供两条入口：

1. **Agent Builder 内抽取为 skill**
   - 用户把当前 Agent 中一段成熟能力沉淀为 skill
2. **管理端直接新建 skill**
   - 管理员独立维护组织能力库

### 11.2 创建流程

1. 填写基本信息
2. 编写自然语言 Spec
3. 选择知识范围
4. 选择工具白名单
5. 设置兜底与风险级别
6. 点击编译
7. 查看摘要、风险、预览
8. 保存草稿或发布

### 11.3 用户可见的能力

用户自定义 skill 应支持：

- 草稿
- 版本
- 启停
- 复制
- 基于模板创建
- 绑定到多个 Agent

## 12. API 设计建议

### 12.1 Skill 管理接口

- `GET /skills`
- `POST /skills`
- `GET /skills/{id}`
- `PUT /skills/{id}`
- `POST /skills/{id}/compile`
- `POST /skills/{id}/publish`
- `POST /skills/{id}/disable`

### 12.2 Agent 绑定接口

- `GET /agents/{agentId}/skills`
- `PUT /agents/{agentId}/skills`
- `POST /agents/{agentId}/skills/reorder`

### 12.3 运行时调试接口

- `POST /skills/{id}/debug`
- `POST /agents/{agentId}/debug`

调试返回建议包含：

- 命中的 skill 列表
- 最终 prompt 摘要
- 有效知识范围
- 有效工具范围
- 风险提示

## 13. 治理与安全

### 13.1 权限

- 平台基础 skill：系统维护，不可删
- 内置标准 skill：平台维护，组织可启停或派生
- 用户自定义 skill：`ORG_ADMIN` 可维护，`ORG_USER` 只可使用

### 13.2 安全边界

即使 skill 允许某个工具，也仍必须经过：

- 组织权限校验
- 用户权限校验
- 风险级别校验
- 审批/确认策略

### 13.3 审计

建议记录：

- 本次会话命中的 skill
- 触发原因
- 生效工具范围
- 触发的 handoff
- 运行版本号

## 14. 分阶段落地

### Phase 1：运行时最小闭环

目标：先让“内置标准 skill 真正生效”。

范围：

- 新增 `skill_definition` / `agent_skill_binding`
- 内置种子 skill
- `ChatOrchestratorService` 接 skill resolver
- `ToolOrchestratorService` 支持白名单过滤
- 默认 Agent 绑定通用 skill

### Phase 2：组织级自定义 skill

目标：让管理员能创建和绑定 skill。

范围：

- skill CRUD
- skill 编译预览
- skill 绑定管理
- 管理端或 Builder 创建入口

### Phase 3：版本、调试与发布治理

目标：让 skill 成为可治理资产。

范围：

- version / publish / rollback
- debug trace
- 审计报表
- 模板派生

## 15. 为什么这条方案适合当前项目

这套方案贴合当前仓库的原因有三点：

1. 当前项目已经有聊天编排、工具编排、RAG 和 Agent Builder，skill 只是在中间补出可复用能力层，不需要推翻既有架构。
2. 当前 Agent Builder 已经沉淀了 `specText + toolIds + knowledgeBaseIds + handoffRule` 这些关键输入，天然适合演进成 skill 编辑与编译入口。
3. 当前前端已经出现“系统内置智能体”和“已发布智能体”的产品形态，引入 skill registry 后，内置能力终于能从 UI 预设变成真正的运行时资产。

## 16. 推荐结论

建议将 `skill` 定义为：

**可被 Agent 复用、可约束知识与工具边界、可进入运行时编排的业务能力模块。**

推荐先做 **Phase 1 最小闭环**：

1. 系统内置标准 skill 种子化
2. 聊天运行时接 skill 解析
3. 工具按 skill 白名单过滤
4. 默认 CiCi Agent 绑定 `conversation-core + knowledge-first + general-assistant`

这样改动最小，但能最快把“内置标准 skill + 用户自定义 skill”从概念推进到当前项目真实可演进的骨架。
