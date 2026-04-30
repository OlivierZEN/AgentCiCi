# Agent、Skill 与 Tool 权限模型设计文档

## 1. 背景

在 Agent 系统中，Agent 可以直接调用 Tool，也可以调用 Skill。Skill 本身为了完成特定任务，可能需要依赖若干 Tool。

为了避免 Skill 成为绕过 Agent Tool 白名单的间接扩权通道，需要明确以下问题：

- Agent 允许哪些 Tool 被直接调用。
- Agent 允许哪些 Skill 被触发。
- Skill 内部依赖的 Tool 在什么条件下可以被使用。
- Skill 依赖的 Tool 是否会提升为 Agent 的全局 Tool 权限。

本设计采用以下原则：

> Skill 依赖的 Tool 只在该 Skill 被触发执行期间可用，并且仅与该 Skill 的执行上下文关联，不会提升为 Agent 的全局 Tool 权限。

## 2. 核心概念

### 2.1 Agent

Agent 是任务执行主体，代表某个角色、用户、应用或自动化流程。

Agent 可以配置：

```yaml
agent:
  id: sales_assistant
  allowed_tools:
    - crm.read
    - knowledge.search
  allowed_skills:
    - customer_followup
    - lead_summary
```

其中：

- `allowed_tools` 表示 Agent 可直接调用的 Tool。
- `allowed_skills` 表示 Agent 可触发执行的 Skill。

### 2.2 Tool

Tool 是系统中的原子能力，例如：

```text
crm.read
crm.update
email.send
calendar.create
browser.search
shell.exec
database.query
```

Tool 通常具有明确的输入、输出、副作用和安全等级。

### 2.3 Skill

Skill 是面向任务的能力封装。它可以包含提示词、工作流、代码逻辑、规则约束和 Tool 编排。

Skill 需要声明自身依赖的 Tool：

```yaml
skill:
  id: customer_followup
  required_tools:
    - crm.read
    - email.draft
  optional_tools:
    - email.send
    - calendar.create
```

Skill 本身不是安全主体，不直接拥有权限。它只是声明：执行该 Skill 时需要哪些 Tool。

## 3. 设计目标

本权限模型需要满足以下目标：

1. 防止隐式扩权  
   Agent 被允许调用某个 Skill，不代表 Agent 自动获得该 Skill 依赖 Tool 的全局直接调用权。

2. 支持 Skill 封装高阶能力  
   Skill 可以在受控上下文中使用比 Agent 直接 Tool 白名单更丰富的 Tool。

3. 保持权限边界清晰  
   Agent 的直接 Tool 权限、Skill 执行期 Tool 权限、用户审批权限需要明确区分。

4. 支持运行时审计  
   所有 Tool 调用都需要能追溯到 Agent、Skill、用户、会话和审批链路。

5. 支持降级和审批  
   当 Skill 所需 Tool 不可用时，系统可以拒绝执行、降级执行，或请求临时授权。

## 4. 权限模型

系统中存在两类 Tool 权限：

- Agent 直接 Tool 权限
- Skill 作用域 Tool 权限

### 4.1 Agent 直接 Tool 权限

Agent 可以直接调用 `allowed_tools` 中列出的 Tool。

```yaml
agent:
  id: sales_assistant
  allowed_tools:
    - crm.read
    - knowledge.search
```

此时 Agent 可以直接调用：

```text
crm.read
knowledge.search
```

但不能直接调用：

```text
email.send
crm.delete
shell.exec
```

### 4.2 Skill 作用域 Tool 权限

当 Agent 被允许调用某个 Skill，并且该 Skill 正在执行时，Skill 声明的依赖 Tool 可以在该 Skill 的执行上下文中被调用。

```yaml
agent:
  id: sales_assistant
  allowed_tools:
    - crm.read
  allowed_skills:
    - customer_followup

skill:
  id: customer_followup
  required_tools:
    - crm.read
    - email.draft
  optional_tools:
    - email.send
```

在这个例子中：

- Agent 可以直接调用 `crm.read`。
- Agent 可以触发 `customer_followup`。
- `customer_followup` 执行期间可以使用 `email.draft`。
- `email.send` 只能在该 Skill 执行期间，且通过策略或审批后使用。
- Agent 不能在 Skill 外部直接调用 `email.draft` 或 `email.send`。

## 5. 核心规则

### 5.1 Skill Tool 不提升为 Agent 全局权限

```text
agent.allowed_skills contains skill_a
skill_a.required_tools contains email.send

does not imply:

agent.allowed_tools contains email.send
```

Skill 依赖的 Tool 只在 Skill 执行上下文中生效。

### 5.2 Tool 调用必须绑定调用上下文

每次 Tool 调用都必须携带上下文。

Agent 直接调用：

```json
{
  "agent_id": "sales_assistant",
  "skill_id": null,
  "session_id": "sess_001",
  "user_id": "user_123",
  "tool": "crm.read",
  "invocation_type": "agent_direct"
}
```

Skill 作用域调用：

```json
{
  "agent_id": "sales_assistant",
  "skill_id": "customer_followup",
  "session_id": "sess_001",
  "user_id": "user_123",
  "tool": "email.send",
  "invocation_type": "skill_scoped"
}
```

### 5.3 Skill 只能调用自己声明过的 Tool

即使运行时上下文中有其他 Tool 可用，Skill 也只能调用自己 manifest 中声明的 Tool。

```text
tool in skill.required_tools
or tool in skill.optional_tools
```

### 5.4 高风险 Tool 需要额外策略控制

以下 Tool 即使被 Skill 声明，也建议要求审批或策略校验：

```text
email.send
payment.charge
crm.delete
database.write
shell.exec
file.write
external_api.call
permission.grant
```

### 5.5 审计归属 Agent，但来源标记 Skill

Tool 调用的责任主体是 Agent，但必须记录该调用是否来自 Skill。

```text
责任主体：Agent
执行来源：Skill
底层能力：Tool
最终授权：Runtime Policy
```

## 6. 权限判定逻辑

推荐判定函数：

```python
def can_call_tool(agent, tool, context):
    if tool in agent.allowed_tools:
        return allow("agent_direct_tool")

    if context.current_skill is not None:
        skill = context.current_skill

        if skill.id not in agent.allowed_skills:
            return deny("skill_not_allowed_for_agent")

        if tool not in skill.required_tools and tool not in skill.optional_tools:
            return deny("tool_not_declared_by_skill")

        if not runtime_policy_allows(agent, skill, tool, context):
            return deny("runtime_policy_denied")

        return allow("skill_scoped_tool")

    return deny("tool_not_allowed")
```

简化公式：

```text
Agent 可直接调用的 Tool =
  agent.allowed_tools

Agent 通过 Skill 可间接调用的 Tool =
  skill.declared_tools
  where skill in agent.allowed_skills
  and context.current_skill == skill
  and runtime_policy_allows == true

Agent 实际可用 Tool =
  agent.allowed_tools
  union current_skill_scoped_tools
```

这里的并集是运行时上下文并集，不是静态全局并集。

## 7. Skill Manifest 设计

建议 Skill 显式声明依赖、风险等级和降级策略。

```yaml
id: customer_followup
name: Customer Follow-up
version: 1.0.0

description: Generate and optionally send customer follow-up emails.

required_tools:
  - name: crm.read
    reason: Read customer profile and recent activity.

  - name: email.draft
    reason: Generate an email draft.

optional_tools:
  - name: email.send
    reason: Send the follow-up email after approval.
    requires_approval: true

fallbacks:
  email.send:
    strategy: draft_only
    message: If sending is not allowed, create a draft instead.

risk_level: medium

side_effects:
  - create_email_draft
  - optionally_send_email
```

## 8. 执行流程

```text
1. Agent 接收到任务
2. Agent 判断是否需要触发 Skill
3. 系统检查 Agent 是否在 allowed_skills 中拥有该 Skill
4. 创建 Skill Execution Context
5. Skill 请求调用 Tool
6. 权限系统判断：
   - Tool 是否在 Agent 直接 allowed_tools 中
   - 或 Tool 是否在当前 Skill 声明依赖中
   - 当前 Skill 是否属于 Agent allowed_skills
   - Runtime Policy 是否允许
   - 是否需要用户审批
7. Tool 执行
8. 写入审计日志
9. Skill 返回结果
10. 销毁 Skill Execution Context
```

## 9. 拒绝与降级策略

当 Skill 依赖的 Tool 不可用时，系统可以采用三种策略。

### 9.1 拒绝执行

适用于必需 Tool 缺失：

```text
customer_followup requires crm.read, but crm.read is unavailable.
```

### 9.2 降级执行

适用于可选 Tool 缺失：

```text
email.send unavailable, fallback to email.draft.
```

### 9.3 请求授权

适用于高风险或临时授权场景：

```text
customer_followup requests permission to use email.send for this execution.
```

## 10. 安全与审计要求

每次 Tool 调用至少应记录以下字段：

```json
{
  "timestamp": "2026-04-29T10:00:00Z",
  "agent_id": "sales_assistant",
  "skill_id": "customer_followup",
  "tool": "email.send",
  "invocation_type": "skill_scoped",
  "user_id": "user_123",
  "session_id": "sess_001",
  "approval_id": "approval_456",
  "decision": "allowed",
  "policy": "runtime_policy_v1"
}
```

审计系统需要能回答以下问题：

- 是哪个 Agent 发起了调用？
- 是否通过 Skill 间接调用？
- 该 Skill 是否在 Agent 的 Skill 白名单中？
- 该 Tool 是否在 Skill manifest 中声明？
- 是否经过运行时策略校验？
- 是否经过用户或管理员审批？
- 调用是否产生外部副作用？

## 11. 推荐结论

最终建议采用如下权限语义：

```text
Agent Tool 白名单：
  定义 Agent 可直接调用的裸 Tool 权限。

Agent Skill 白名单：
  定义 Agent 可触发的高阶能力。

Skill Tool 依赖：
  定义 Skill 执行期间可请求使用的 Tool。

Runtime Policy：
  决定某次具体 Tool 调用是否最终允许。
```

最重要的边界是：

> Skill 依赖的 Tool 只在该 Skill 被触发执行期间有效，且只能服务于该 Skill 的执行流程。它不会变成 Agent 的全局 Tool 权限，也不能被 Agent 在 Skill 外部直接调用。

## 12. 仓库实现状态（与本后端对齐）

以下为截至实现时的行为摘要，便于与设计 §4–§6 对照；细节以代码为准。

| 能力 | 状态 |
|------|------|
| Agent 直接绑定的工具 vs Skill `toolWhitelist` 声明分层展示 | **已实现**：`AgentCapabilityResolution` / `ResolvedSkillContext` 字段；`/ai/chat`、`/agents/{agentId}/debug` JSON |
| 运行时可用工具面 | **已实现**：基线 = Agent 直接绑定 ∪ 已发布 manifest 依赖 ∪ CiCi 内置扩充（CloudCC 发现工具 + `tavily_search` / `tavily_extract`）；Skill 声明中**不在**基线的工具仅在绑定为 **ALWAYS**（ambient）或 **当前激活 Skill** 与会话态一致时并入 |
| Skill 独有工具不写入 Agent 静态配置 | **已实现**：通过 `skillScopedToolNames` 与告警文案区分 |
| 会话态「当前 Skill」 | **已实现**：`chat_session_state.state_json.active_skill_code`；`/ai/chat`、流式接口可选请求体 `activeSkillCode`（空字符串清除）；与绑定 Skill 列表校验 |
| 工具调用审计 | **已实现**：`AuditService` 事件 `TOOL_INVOCATION`，载荷含 skill 上下文分类（agent_direct / skill_scoped / memory_builtin 等）；§5.4 高风险事前审批仍 **未实现** |
| 高风险工具审批 / Runtime Policy 额外拒绝路径 | **未实现**（刻意延后）：仍以允许列表 + 业务策略为主；与设计 §5.4 对齐时再补 |

变更参考：`TASK-019`（`.claw/task-board.md`）。
