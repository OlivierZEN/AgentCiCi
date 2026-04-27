# Agent 与 Skill 组合关系改造实施设计

更新时间：2026-04-21  
适用项目：`cc-cici-assistant`

## 1. 文档目标

本文用于把当前项目中关于 `Agent` 与 `Skill` 的职责边界、配置入口和运行时关系，收敛成一套可以直接实施的改造方案。

本次改造重点不是重做 `skill` 体系，也不是推翻已有 `Agent Builder` 主线，而是解决下面这个实际产品问题：

**`skill` 的选择与约束，应主要发生在 `Agent` 设置页 / Agent Builder 中，而不是由 admin 技能中心承担 Agent-Skill 关联主入口。**

本文目标：

1. 明确 `Agent`、`Skill`、`Tool` 的职责边界。
2. 明确 admin 页面与 Agent Builder 的页面分工。
3. 在尽量少改动现有库表的前提下，给出可分阶段实施的改造方案。
4. 收敛当前编译链路与运行时链路对 `skill` 的使用差异。

## 2. 设计结论

本次改造采用以下结论作为基线：

1. `Agent` 仍然是顶层产品对象，负责对外身份、渠道、发布与顶层编排。
2. `Skill` 仍然是组织级可复用能力资产，负责复用的 prompt/policy/tool/kb/handoff/output 约束。
3. `Admin / Skill Studio` 负责 `skill` 资产治理，不再承担 Agent-Skill 关联的主操作入口。
4. `Agent 设置页 / Agent Builder` 负责选择“当前 Agent 允许使用的 skill 范围”，并配置激活方式、优先级、启停状态。
5. 后端关系仍以 `agent_skill_binding` 为事实源，不新造第二套关联模型。
6. 改造第一阶段不做大规模 schema 变更，优先调整入口、接口归属与前后端调用链。

一句话概括：

**admin 管 `skill` 是什么，agent 页面管这个 agent 能用哪些 `skill`。**

## 3. 当前现状与主要问题

结合当前仓库，系统已经具备 `skill` 运行时与 `agent_skill_binding` 数据模型，但产品入口与实现链路仍然存在割裂。

### 3.1 已有基础

当前已经具备：

- `SkillDefinitionEntity / SkillVersionEntity / AgentSkillBindingEntity`
- `SkillResolverService`，运行时会按 `agentId` 解析当前生效 skill
- `SkillPromptAssembler`，会把 skill prompt 片段与 handoff 规则拼进最终 system prompt
- `AgentBuilderShell`，已是助手侧的顶层 Agent 构建入口
- `AgentCompileService`，已支持 `skillRefs`，并在未显式传参时回退到 `agent_skill_binding`

因此，系统其实已经天然接近“Agent 选择 Skill，运行时按 Agent 加载 Skill”的模型。

### 3.2 当前问题

#### 问题一：配置入口和运行时语义不一致

运行时已经按 `agent -> bindings -> skills` 工作，但前端主构建入口 `AgentBuilderShell` 仍未把 `skill` 纳入 Agent 编辑主流程。

结果是：

- 用户在 Agent Builder 里配置的是 `toolIds / knowledgeBaseIds / channels`
- 真正的 `skill` 绑定却在 `AdminSkillsPage` 中维护
- 产品心智被拆成两处，后续很容易重复配置

#### 问题二：admin 技能中心混入了 agent 侧职责

`AdminSkillsPage` 当前既承担：

- skill 列表与编辑
- skill 模板创建
- skill 预览编译
- agent-skill 绑定

这会导致：

- 页面职责过重
- skill 资产治理和 agent 装配被混在一起
- 一旦 agent 数量增长，admin 端维护绑定会越来越不适合

#### 问题三：编译链路与运行时链路存在边界不对齐

当前存在以下不一致：

1. `Chat` 运行时主要依赖 skill 解析出的工具、知识库、handoff 规则。
2. `AgentBuilder` 保存与编译主流程主要依赖 `toolIds / knowledgeBaseIds`。
3. `AgentBuilder` 当前编译请求未显式传入 `skillRefs`，更多依赖后端按已绑定 skill 回退解析。

这会导致：

- 用户在 Builder 中看见的能力边界，和线上运行时边界不完全一致
- skill 的可见存在感不够强
- 后续做调试、审计、版本 pin 时理解成本上升

## 4. 改造范围与非目标

### 4.1 改造范围

本次改造覆盖：

- 页面入口职责调整
- Agent Builder 增加 skill 选择能力
- 后端接口归属调整
- skill 与 agent 的有效能力合并规则
- 编译 / 运行时的 skill 输入收敛

### 4.2 非目标

本次改造不覆盖：

- 重做 `skill` 库表
- 引入图形化 skill 编排器
- 建立跨组织 skill 商店
- 删除 Agent Builder 中现有 `toolIds / knowledgeBaseIds`
- 立刻把 skill 版本 pin 落成独立依赖表

## 5. 目标职责模型

## 5.1 对象职责

| 对象 | 职责 | 事实源 | 主编辑入口 |
|---|---|---|---|
| `Tool` | 最小执行单元 | `tool_definition` + 内置工具目录 | admin 工具中心 |
| `Skill` | 可复用能力资产 | `skill_definition` / `skill_version` | admin 技能中心 |
| `Agent` | 顶层产品对象、渠道与发布单元 | `agent_definition` 等 agent 域表 | Agent Builder |
| `AgentSkillBinding` | 某个 Agent 允许使用哪些 skill | `agent_skill_binding` | Agent Builder |

## 5.2 页面职责

### Admin / Skill Studio

只负责：

- 创建 skill
- 编辑 skill
- 预览编译 skill
- 启停 skill
- 查看 skill 风险等级、默认工具/知识边界、输出契约

不再作为主入口负责：

- 某个 Agent 绑定哪些 skill

### Agent Builder / Agent 设置页

新增并负责：

- 为当前 Agent 选择可用 skill
- 设置每个 skill 的激活方式
- 设置 priority / enabled
- 在编译前查看该 Agent 的 skill 组合摘要

继续负责：

- definition / spec / publish config / channel / tool / kb 配置
- 编译、调试、发布

## 6. 目标运行时关系

推荐将能力收敛为三层：

1. **平台层**
   - 平台基础 system prompt
   - 全局安全规则

2. **Agent 层**
   - `agent.systemPrompt`
   - `agent.model`
   - `agent.handoffRule`
   - `agent.toolIds`
   - `agent.knowledgeBaseIds`

3. **Skill 层**
   - `promptFragment`
   - `toolWhitelist`
   - `kbWhitelist`
   - `handoffRule`
   - `outputContract`
   - `riskLevel`

## 6.1 Prompt 合并规则

最终 system prompt 的组装顺序：

1. 平台基础 prompt
2. Agent 自身 `systemPrompt`
3. 已启用 skill 的 `promptFragment`
4. 已启用 skill 的 handoff / output contract

原则：

- Agent 负责顶层 persona
- Skill 负责复用能力片段
- 冲突时优先更严格的安全规则

## 6.2 工具边界合并规则

定义：

- `agentToolBoundary = Agent.toolIds`
- `skillToolUnion = union(selectedSkill.toolWhitelist)`

第一阶段目标规则：

- 若 `agentToolBoundary` 和 `skillToolUnion` 都非空，则 `effectiveTools = intersection(agentToolBoundary, skillToolUnion)`
- 若只有 `agentToolBoundary` 非空，则 `effectiveTools = agentToolBoundary`
- 若只有 `skillToolUnion` 非空，则 `effectiveTools = skillToolUnion`
- 若两者都为空，则 `effectiveTools = []`

说明：

- `Agent.toolIds` 是顶层外边界
- `Skill.toolWhitelist` 是复用能力内边界
- 二者同时存在时取交集，避免 agent 选了 skill 之后把边界放宽

## 6.3 知识库边界合并规则

定义：

- `agentKbBoundary = Agent.knowledgeBaseIds`
- `skillKbUnion = union(selectedSkill.kbWhitelist)`

第一阶段目标规则：

- 若二者都非空，则 `effectiveDefaultKbs = intersection(agentKbBoundary, skillKbUnion)`
- 若只有一侧非空，则取非空一侧
- 若二者都为空，则默认为空

在 chat 请求里继续保留：

- 若用户请求未指定 kb，则默认用 `effectiveDefaultKbs`
- 若用户请求指定 kb，则对用户请求和 `effectiveDefaultKbs` 再取交集

## 6.4 Handoff 与输出契约规则

- `agent.handoffRule` 作为顶层规则，优先放在最前
- 各 skill 的 `handoffRule` 按 priority 顺序合并
- `outputContract` 第一阶段继续采用“首个非空 skill outputContract”策略
- 后续如需要，可再引入 agent 级显式 `outputContract`

## 7. 目标接口设计

## 7.1 接口归属原则

虽然底层数据仍来自 `skill` 域，但“某个 agent 绑定什么 skill”在语义上属于 `agent` 自身配置，因此接口应收口到 `/agents` 下面。

### 保留的 skill 资产接口

- `GET /skills`
- `POST /skills`
- `GET /skills/{id}`
- `PUT /skills/{id}`
- `DELETE /skills/{id}`
- `POST /skills/preview`

### 新增的 agent 侧 skill 绑定接口

- `GET /agents/{agentId}/skills`
- `PUT /agents/{agentId}/skills`

推荐返回结构：

```json
{
  "bindings": [
    {
      "skillId": 12,
      "skillCode": "sales-copilot",
      "skillName": "售前协同",
      "riskLevel": "MEDIUM",
      "activationMode": "always-on",
      "activationCondition": "",
      "priority": 10,
      "enabled": true,
      "toolWhitelist": ["cloudcc_pageQuery"],
      "kbWhitelist": [],
      "handoffRule": "涉及报价承诺时转人工"
    }
  ]
}
```

### 兼容策略

为避免一次性改太大：

1. 第一阶段保留 `/skills/agents/{agentId}/bindings` 作为兼容层，不再作为新入口
2. Agent Builder 切到调用 `/agents/{agentId}/skills`
3. 老接口标记为 deprecated 兼容接口；admin 页面移除绑定区后，仅在确有历史调用方时短期保留

## 7.2 编译接口收敛

`POST /agents/{agentId}/compile` 在第一阶段保持现有结构，但要求前端显式传入 `skillRefs`。

即：

- `AgentBuilderShell` 选中的 skill 绑定，编译时同步生成 `skillRefs`
- 后端继续保留“未传 skillRefs 时回退到 agent 已绑定 skill”的兼容逻辑

这样有两个好处：

1. Builder 看到的 skill 组合能直接反映到 compile 结果里
2. 编译产物对“本次构建依赖了哪些 skill”更透明

## 8. 前端改造设计

## 8.1 AgentBuilderShell 改造目标

当前 `AgentDraft` 建议扩展一个 skill 绑定草稿结构：

```ts
type AgentSkillBindingDraft = {
  skillId: number;
  skillCode: string;
  skillName: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  activationMode: "always-on" | "intent-route" | "manual";
  activationCondition: string;
  priority: number;
  enabled: boolean;
};
```

并在 `AgentDraft` 中增加：

```ts
skillBindings: AgentSkillBindingDraft[];
```

## 8.2 Agent Builder 页面结构调整

建议把定义页调整为以下顺序：

1. Agent 基本信息
2. 自然语言 Spec
3. Skill 范围
4. 知识边界
5. 工具边界
6. 渠道与发布
7. 编译结果

其中“Skill 范围”是新增主区块，而不是附属弹窗。

## 8.3 Skill 范围区块交互

### 左侧：已选 skill 列表

每个 skill 卡片展示：

- `name / skillCode`
- 风险等级
- 激活方式
- priority
- 启停状态
- 默认工具数 / 知识库数

### 右侧：可选 skill 目录

支持：

- 搜索
- 按 `builtin / custom / riskLevel` 过滤
- 查看 skill 详情摘要
- 一键加入当前 Agent

### 卡片内操作

支持：

- 修改 `activationMode`
- 编辑 `activationCondition`
- 调整 `priority`
- 启用/停用
- 移除 skill

## 8.4 保存、编译、发布行为

### 保存草稿

`saveFramework()` 需要新增一步：

- `PUT /agents/{agentId}/skills`

并把提示文案从：

- `definition/spec/bindings/publish-configs`

扩成：

- `definition/spec/bindings/skills/publish-configs`

### 编译

`compileWorkflow()` 需要把：

- `skillBindings.filter(enabled).map(skill => skill.skillCode)`

转成 `skillRefs`

### 发布

发布逻辑不需要改入口，但编译摘要与调试摘要中应展示：

- 本次编译生效的 skill
- skill 派生出的有效工具与知识边界

## 8.5 AdminSkillsPage 改造目标

`AdminSkillsPage` 应收口为纯 skill 资产中心。

移除：

- `selectedAgentId`
- `bindings`
- 绑定保存按钮
- agent 绑定区块

保留：

- 技能列表
- 模板创建
- 技能编辑
- 预览编译
- 启停

这样 admin 页面更像“能力资产库”，而不是“所有 agent 的拼装后台”。

## 9. 后端改造设计

## 9.1 服务职责调整

建议把当前“skill 定义服务”和“agent-skill 绑定服务”解耦。

### SkillDefinitionService

继续负责：

- skill CRUD
- skill preview compile
- 默认内置 skill 初始化
- skill version 管理

不再继续膨胀承担：

- agent skill binding 的主对外语义

### 新增 AgentSkillBindingService

负责：

- listBindings(orgId, agentId)
- replaceBindings(orgId, agentId, bindings)
- 校验 skill 是否存在、是否启用
- 组装 agent 侧技能视图

这样领域边界更清晰：

- `skill` 域管定义
- `agent` 域管装配

## 9.2 AgentDefinitionController 增补接口

推荐在 `AgentDefinitionController` 或新的 `AgentSkillBindingController` 中新增：

- `GET /agents/{agentId}/skills`
- `PUT /agents/{agentId}/skills`

并把 `GET /agents/{agentId}` 的详情返回扩展为：

- `skillBindings`

这样 Agent Builder 首屏加载 Agent 详情时就能一次拿齐 Agent 相关配置。

## 9.3 有效能力解析统一

建议新增一个共享服务，例如：

- `AgentCapabilityResolverService`

负责统一计算：

- `effectiveSkillCodes`
- `effectiveToolNames`
- `effectiveKnowledgeBaseIds`
- `effectiveHandoffRules`

由它同时服务于：

- `ChatOrchestratorService`
- `AgentCompileService`
- 后续 debug / trace API

避免：

- 运行时一套规则
- 编译时另一套规则

## 9.4 编译链路改造

`AgentCompileService` 第一阶段建议保持现有兼容逻辑，但补齐以下行为：

1. 显式优先使用请求体 `skillRefs`
2. 若未传，再回退到 `agent_skill_binding`
3. 编译摘要中展示 `selected skills`
4. manifest 中保留 `resolvedSkillRefs`
5. compile warnings 中提示 skill 与 agent 工具边界冲突

例如：

- Agent 选择了 `sales-copilot`
- 但 Agent 顶层工具边界没包含 CRM 工具

则给出 warning，而不是静默放过

## 9.5 运行时链路改造

`SkillResolverService` 当前只聚合 skill 自身 whitelist，后续应升级为：

- 先拿 agent 顶层 definition / bindings
- 再拿 selected skill definitions
- 最后计算有效 tools / kbs / handoff

也就是说，运行时不再只理解“技能自带边界”，而是理解“agent 外边界 + skill 内边界”的组合关系。

## 10. 数据与迁移方案

## 10.1 Phase 1 不改库表

第一阶段直接复用：

- `skill_definition`
- `skill_version`
- `agent_skill_binding`

无需新增 Flyway。

## 10.2 Phase 2 可选增强

当需要更强的版本可复现性时，再考虑新增：

- `agent_workflow_skill_dependency`

用于把某个 `agent_workflow_version` 具体 pin 到哪些 `skillVersion`

但这一项不是本次入口改造的阻塞项，因为当前 manifest 中已经能够承载 `resolvedSkillRefs`

## 11. 分阶段实施计划

## Phase 1：入口归位

目标：

- 不改库
- 不重做运行时
- 先把 skill 选择收口到 Agent Builder

任务：

1. `AgentBuilderShell` 增加 `skillBindings` 状态
2. 新增 agent 侧 skill 绑定接口
3. 保存草稿时一起保存 skill bindings
4. 编译时显式传 `skillRefs`
5. `AdminSkillsPage` 去掉 agent 绑定区

完成标志：

- 一个 admin 可以只在 Agent Builder 内完成“选 skill -> 保存 -> 编译”
- admin 技能中心只剩 skill 资产治理

## Phase 2：编译与运行时统一

目标：

- 让 compile 与 chat 对 skill 的理解一致

任务：

1. 新增 `AgentCapabilityResolverService`
2. `ChatOrchestratorService` 改为使用统一 effective scope
3. `AgentCompileService` 改为复用同一套 effective scope 规则
4. compile/debug 结果输出 effective skills/tools/kbs

完成标志：

- Agent Builder 看到的边界与线上运行时边界一致

## Phase 3：治理增强

目标：

- 提升可审计性和调试体验

任务：

1. debug / trace 显示 active skills
2. compile warning 显示 skill-agent boundary 冲突
3. 视需要引入 `agent_workflow_skill_dependency`
4. 增加前端只读态和权限提示

完成标志：

- skill 组合关系可调试、可审计、可回溯

## 12. 验收标准

达到以下条件，视为本改造一期完成：

1. Agent Builder 页面可查看和编辑当前 Agent 的 skill 范围。
2. 保存 Agent 草稿时会同步保存 skill bindings。
3. 编译请求会显式带上当前选中的 `skillRefs`。
4. 编译摘要可见本次生效的 skills。
5. admin 技能中心不再包含 agent 绑定区块。
6. 数据层不新增重复事实源，`agent_skill_binding` 仍是唯一绑定事实源。

## 13. 风险与注意事项

### 风险一：页面入口变了，但后端语义没变

如果只改前端入口，不整理后端归属，后续还是容易回到“到底谁管 bindings”的混乱状态。

应对：

- 先新增 `/agents/{agentId}/skills`
- 再逐步下线 `/skills/agents/{agentId}/bindings` 这类 skill 域兼容绑定接口

### 风险二：Agent 直接工具边界与 Skill 工具边界冲突

如果不定义清楚合并规则，就会出现：

- 用户以为选了 skill 就能用对应工具
- 但 Agent 顶层白名单把工具截断了

应对：

- 第一阶段明确取交集
- 编译时给 warning

### 风险三：Assistant 侧入口与 admin 权限不一致

Agent Builder 位于助手侧，但编辑行为本质仍属于管理操作。

应对：

- 编辑接口继续保留 admin 权限
- 前端补充只读态与无权限提示

## 14. 最终建议

本项目最稳妥的演进方式不是“取消 admin 的 skill 中心”，也不是“把 skill 完全做成 agent 局部配置”。

推荐坚持下面这套长期模型：

- `Skill` 是组织级能力资产，在 admin 中治理
- `Agent` 是顶层产品对象，在 Agent Builder 中装配 skill
- `AgentSkillBinding` 是唯一关联事实源
- `compile` 与 `runtime` 共用同一套 effective capability 解析规则

这样既保持了当前项目已经落下来的 `Agent Builder` 主线，也不会破坏 `skill` 作为复用能力库的长期价值。
