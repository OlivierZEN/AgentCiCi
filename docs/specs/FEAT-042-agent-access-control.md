---
kind: feature-spec
feature_id: FEAT-042
title: Agent access control and user authorization
status: draft
owner_role: product-platform
task_ids: TASK-119
related_decisions: docs/agent-skill-tool-permission-model.md
related_issues: none
updated_at: 2026-05-20T12:43:54Z
updated_by: ai
---

# FEAT-042 - Agent Access Control And User Authorization

## 背景与目标

AgentCiCi 当前已经具备多组织、组织成员、`ORG_ADMIN` / `ORG_USER`、Agent Builder、Skill 绑定、Tool 运行时治理、Open API Key run-as 用户和调用日志等基础能力。随着智能体数量和业务场景增加，系统需要明确“哪些用户可以使用哪些智能体”，避免所有组织成员天然拥有全部智能体使用权，也避免智能体、Skill 或 Tool 成为绕过组织权限的扩权通道。

本功能目标是新增一套智能体访问控制模型：

- 控制组织内用户、管理员和后续用户组对智能体的可见、运行、编辑、发布、管理、调试、Open API 和日志查看权限。
- 第一阶段不强依赖完整角色、部门和用户组管理，先支持全组织、指定用户、现有系统角色和创建者/管理员管理语义。
- 数据模型预留 `GROUP`、`CUSTOM_ROLE`、`DEPARTMENT` 授权主体，保证后续组织架构与用户组能力接入时无需重写智能体权限框架。
- 运行时坚持最小权限：用户决定谁可以触发智能体，智能体发布版本决定它被允许做什么，Runtime Policy 决定本次具体工具/技能调用能不能执行。
- 所有授权变更和运行时拒绝都可审计、可追踪。

## 范围

### In Scope

- 新增智能体访问授权模型 `agent_access_grant`。
- 新增智能体授权审计 `agent_permission_audit`。
- 定义第一阶段授权主体：
  - `ORG`: 当前组织全员。
  - `USER`: 指定组织成员。
  - `SYSTEM_ROLE`: 现有 `ORG_ADMIN` / `ORG_USER`。
  - `OWNER`: 智能体创建者或当前 owner 的隐式管理权限，第一阶段可不作为持久 principal 写入。
- 定义权限动作：
  - `VIEW`: 可看到智能体。
  - `RUN`: 可在助手工作台或会话入口调用智能体。
  - `DEBUG`: 可查看调试信息和运行链路摘要。
  - `EDIT`: 可编辑草稿。
  - `PUBLISH`: 可发布版本。
  - `MANAGE`: 可分配权限、停用、删除或转移 owner。
  - `OPENAPI`: 可创建、轮换、停用或删除该智能体 API Key。
  - `LOG_VIEW`: 可查看该智能体调用日志。
- 在智能体列表、详情、运行入口、Agent Builder、Open API Key 管理和调用日志读取处接入权限校验。
- 第一阶段提供组织管理员可用的智能体授权管理入口。
- 运行时工具/Skill 权限继续遵循 `docs/agent-skill-tool-permission-model.md`，不因用户拥有 `RUN` 权限而扩大 Tool 权限。

### Out Of Scope

- 完整自定义角色管理。
- 部门组织架构管理。
- 用户组管理 UI 和成员维护。
- 跨组织授权。
- 复杂审批流引擎。
- 行级业务数据权限重构，例如 CRM 客户归属规则、知识库文档级 ACL 的完整重建。

这些能力不是第一阶段强依赖，但数据模型和接口必须为它们保留扩展点。

## 用户场景

- 组织管理员希望“售后客服智能体”只开放给指定客服成员使用，不向全员展示。
- 组织管理员希望通用问答智能体面向全组织成员开放，但只有管理员和创建者能编辑发布。
- 组织管理员希望财务或账单类智能体只允许指定用户运行，并禁止普通成员查看调用日志。
- Agent 创建者希望把某个智能体共享给另一位同事调试，但不允许其发布正式版本。
- 组织管理员希望外部系统调用某个已发布智能体时，API Key 必须绑定 run-as 用户，并且该 run-as 用户仍需要有 `RUN` 权限。
- 平台管理员希望高风险 Tool 被平台禁用后，即使用户有智能体 `RUN` 权限，本次调用也会被运行时策略拒绝。

## 现状与约束

- 当前组织角色只有 `ORG_ADMIN` / `ORG_USER`，没有独立角色、部门和用户组管理。
- 当前系统已有 Agent、Skill、Tool、Knowledge Base、Open API Key 和平台治理能力，权限改造必须避免打断已发布 Agent 的运行。
- `docs/agent-skill-tool-permission-model.md` 已定义 Agent / Skill / Tool 权限边界：Skill 声明的 Tool 只在 Skill 执行期间有效，不提升为 Agent 全局 Tool 权限。
- FEAT-009 已实现 Skill 分层治理和发布版本 pin，智能体权限不能绕过已发布版本快照。
- FEAT-021 已实现 Agent Open API 和 API Key run-as 用户语义，本功能需补充 run-as 用户对目标 Agent 的 `RUN` 权限校验。
- FEAT-010 平台控制面已区分平台角色与组织角色，本功能第一阶段只处理组织内智能体访问控制；平台治理智能体或平台模板仍由平台权限控制。

## 设计原则

1. 授权主体先小后大
   第一阶段只依赖现有用户、组织和系统角色，不等待完整角色、部门、用户组模块。

2. 授权模型一次打稳
   即使第一阶段不实现用户组和部门，也要在 schema 和服务层支持可扩展 `principal_type`。

3. 使用权和运行能力分离
   `RUN` 只代表用户可以触发智能体，不代表用户获得智能体绑定 Tool 的直接权限。

4. 显式授权优先，默认安全
   新建智能体默认仅 owner 和 `ORG_ADMIN` 可管理；是否全组织可运行由创建/发布流程显式选择。

5. 管理权限高于分享权限
   `MANAGE` 可维护授权；`EDIT` 不自动包含 `MANAGE`；`RUN` 不自动包含 `VIEW` 以外的敏感能力。

6. 高风险动作二次校验
   `PUBLISH`、`OPENAPI`、高风险 Tool 调用、外部写操作和权限授予必须写审计。

## 权限语义

### Permission Set

| Permission | 语义 | 典型入口 |
| --- | --- | --- |
| `VIEW` | 在列表、选择器、详情摘要中可见 | 智能体列表、会话智能体选择 |
| `RUN` | 可发起会话、继续会话或通过 Open API run-as 触发 | 助手工作台、Open API runtime |
| `DEBUG` | 可查看调试摘要、trace、runtime governance notes | Agent Builder debug |
| `EDIT` | 可编辑草稿配置、提示词、技能绑定、知识库绑定 | Agent Builder |
| `PUBLISH` | 可发布新版本、下线发布版本 | Agent Builder publish |
| `MANAGE` | 可授权、撤权、停用、删除、转移 owner | 权限管理、危险操作 |
| `OPENAPI` | 可创建、轮换、停用、删除 API Key | API Key 管理弹窗 |
| `LOG_VIEW` | 可查看调用日志和失败原因 | API Key 日志、Agent 日志 |

### Permission Inheritance

- `ORG_ADMIN` 默认拥有本组织所有 Agent 的 `VIEW`、`RUN`、`DEBUG`、`EDIT`、`PUBLISH`、`MANAGE`、`OPENAPI`、`LOG_VIEW`。
- Agent owner 默认拥有该 Agent 的全部权限。
- `MANAGE` 不自动授予平台级治理能力，只在当前组织和当前 Agent 内生效。
- `RUN` 隐含运行入口可见，但列表筛选仍建议按 `VIEW OR RUN` 返回。
- `EDIT` 不隐含 `PUBLISH`。
- `OPENAPI` 不隐含 `RUN`；创建 Key 时所选 run-as 用户必须拥有 `RUN`。
- `LOG_VIEW` 不隐含 `DEBUG`；普通业务日志和调试 trace 可以分层展示。

### Default Policy

新建 Agent 的默认策略：

- owner: all permissions。
- `ORG_ADMIN`: all permissions。
- `ORG_USER`: no grant by default，除非创建流程选择“全组织可用”。
- 若创建流程选择“全组织可用”，写入 `principal_type=ORG` 的 `VIEW` + `RUN` 授权。
- 若创建流程选择“仅管理员和我可用”，不写入全组织授权。

## 数据模型

### `agent_access_grant`

```sql
create table agent_access_grant (
  id uuid primary key,
  org_id varchar(64) not null,
  agent_id uuid not null,
  principal_type varchar(32) not null,
  principal_id varchar(128),
  permission varchar(32) not null,
  source varchar(32) not null default 'MANUAL',
  granted_by uuid,
  expires_at timestamp null,
  status varchar(32) not null default 'ACTIVE',
  created_at timestamp not null,
  updated_at timestamp not null
);
```

Recommended constraints:

- `principal_type in ('ORG', 'USER', 'SYSTEM_ROLE', 'GROUP', 'CUSTOM_ROLE', 'DEPARTMENT')`
- `permission in ('VIEW', 'RUN', 'DEBUG', 'EDIT', 'PUBLISH', 'MANAGE', 'OPENAPI', 'LOG_VIEW')`
- Unique active grant: `(org_id, agent_id, principal_type, principal_id, permission, status)`，或通过 partial unique index 约束 `status='ACTIVE'`。
- `principal_id`:
  - `ORG`: null 或当前 `org_id`。
  - `USER`: user id。
  - `SYSTEM_ROLE`: `ORG_ADMIN` / `ORG_USER`。
  - `GROUP` / `CUSTOM_ROLE` / `DEPARTMENT`: 后续模块对应 id。

### `agent_permission_audit`

```sql
create table agent_permission_audit (
  id uuid primary key,
  org_id varchar(64) not null,
  agent_id uuid not null,
  actor_user_id uuid,
  action varchar(32) not null,
  target_principal_type varchar(32),
  target_principal_id varchar(128),
  permission varchar(32),
  before_json text,
  after_json text,
  reason varchar(512),
  trace_id varchar(128),
  created_at timestamp not null
);
```

审计动作建议：

- `GRANT`
- `REVOKE`
- `UPDATE_EXPIRES_AT`
- `BULK_REPLACE`
- `DEFAULT_POLICY_CREATED`
- `RUNTIME_DENIED`
- `OPENAPI_RUN_AS_DENIED`

## 服务层设计

### `AgentAccessControlService`

建议新增统一服务，所有入口都通过它判断：

```text
boolean can(user, orgId, agentId, permission)
void require(user, orgId, agentId, permission)
List<AgentPermission> effectivePermissions(user, orgId, agentId)
List<AgentGrant> listGrants(orgId, agentId)
void replaceGrants(orgId, agentId, request, actor)
```

判定顺序：

1. 用户必须是当前组织 active member。
2. 平台级禁用、组织冻结、Agent disabled 先拦截。
3. `ORG_ADMIN` 默认通过组织内 Agent 权限。
4. Agent owner 默认通过该 Agent 全部权限。
5. 查 `USER` grant。
6. 查 `SYSTEM_ROLE` grant。
7. 查 `ORG` grant。
8. 后续接入 `GROUP`、`CUSTOM_ROLE`、`DEPARTMENT` grant。
9. grant 过期或非 active 则忽略。

### Runtime Gate

每次会话或 Open API 调用：

```text
require(user, orgId, agentId, RUN)
load published agent version
resolve skill/tool/kb/model scope
run ChatOrchestratorService
for each tool call:
  apply Agent / Skill / Tool runtime policy
  apply high-risk confirmation or approval policy
  write trace
```

这意味着 `RUN` 是进入智能体的门票，不是 Tool 的通行证。

### Open API Run-As

API Key 管理规则：

- 创建或轮换 Key 的 actor 需要 `OPENAPI`。
- Key 绑定的 run-as 用户必须是当前组织 active member。
- run-as 用户必须对该 Agent 拥有 `RUN`。
- Open API 调用时同时校验 Key 状态、Agent 状态、run-as 用户状态和 run-as `RUN` 权限。
- 如果 run-as 权限被撤销，旧 Key 不需要立即删除，但下一次调用应返回权限拒绝，并写 `OPENAPI_RUN_AS_DENIED` 审计或调用日志。

## API 设计

### List Agent Grants

```http
GET /agents/{agentId}/access-grants
```

Required permission: `MANAGE` or `ORG_ADMIN`。

Response:

```json
{
  "agentId": "agent-001",
  "effectiveDefault": "OWNER_AND_ADMIN",
  "grants": [
    {
      "principalType": "ORG",
      "principalId": null,
      "principalName": "全组织成员",
      "permissions": ["VIEW", "RUN"],
      "expiresAt": null,
      "status": "ACTIVE"
    },
    {
      "principalType": "USER",
      "principalId": "user-001",
      "principalName": "张三",
      "permissions": ["VIEW", "RUN", "DEBUG"],
      "expiresAt": null,
      "status": "ACTIVE"
    }
  ]
}
```

### Replace Agent Grants

```http
PUT /agents/{agentId}/access-grants
```

Required permission: `MANAGE`。

Request:

```json
{
  "grants": [
    {
      "principalType": "ORG",
      "principalId": null,
      "permissions": ["VIEW", "RUN"]
    },
    {
      "principalType": "SYSTEM_ROLE",
      "principalId": "ORG_ADMIN",
      "permissions": ["VIEW", "RUN", "DEBUG", "EDIT", "PUBLISH", "MANAGE", "OPENAPI", "LOG_VIEW"]
    }
  ],
  "reason": "Open shared assistant to all organization members"
}
```

Server behavior:

- Validate actor has `MANAGE`。
- Validate principal exists when `principal_type=USER`。
- Reject unsupported `GROUP` / `CUSTOM_ROLE` / `DEPARTMENT` until corresponding module is enabled, while keeping enum compatibility in code.
- Preserve owner and `ORG_ADMIN` implicit permissions even if omitted.
- Write `agent_permission_audit` with before/after.

### Check My Agent Permission

```http
GET /agents/{agentId}/my-access
```

Response:

```json
{
  "agentId": "agent-001",
  "permissions": ["VIEW", "RUN"],
  "sourceSummary": ["ORG", "SYSTEM_ROLE:ORG_USER"]
}
```

This API is useful for frontend route guards and disabled action states.

## 前端设计

### Agent List

- 默认只展示当前用户有 `VIEW` 或 `RUN` 的 Agent。
- `ORG_ADMIN` 可以通过管理筛选查看全部 Agent。
- 被隐藏的 Agent 不应出现在普通成员选择器中。

### Agent Builder

- 无 `EDIT` 时不可进入编辑页或进入只读摘要。
- 无 `PUBLISH` 时发布按钮不可见或禁用，并显示简短权限原因。
- 无 `MANAGE` 时不展示权限管理入口。
- 无 `DEBUG` 时不展示 trace/debug 明细。

### Access Management Modal

第一阶段建议以 modal 形式放在 Agent Builder 或 Agent 详情页：

- 共享范围：
  - 仅我和管理员。
  - 全组织成员可运行。
  - 指定用户可运行。
- 高级权限：
  - 对指定用户授予 `DEBUG`、`EDIT`、`PUBLISH`、`OPENAPI`、`LOG_VIEW`、`MANAGE`。
- 明确展示 owner 和管理员拥有隐式管理权限，不允许在普通授权列表中移除。

产品页 UI 必须遵守 `DESIGN.md` 的 `鎏金账房` product register：紧凑密度、文本层级、必要 1px 分隔线；授权行不要做卡片化大块背景。

## 与角色、部门、用户组的关系

本功能不强依赖完整角色、部门和用户组管理。

第一阶段最小授权主体：

```text
ORG
USER
SYSTEM_ROLE
OWNER implicit
```

后续推荐接入顺序：

1. `USER`: 指定用户授权。
2. `SYSTEM_ROLE`: 现有 `ORG_ADMIN` / `ORG_USER`。
3. `GROUP`: 用户组授权，优先级高于部门。
4. `CUSTOM_ROLE`: 自定义业务角色。
5. `DEPARTMENT`: 部门授权。

用户组优先于部门，因为用户组表达权限集合，部门表达组织结构。智能体使用权直接绑定部门会在组织架构调整时产生权限漂移。

## 与 Agent / Skill / Tool 权限模型的关系

本功能只解决“谁能触发或管理 Agent”，不替代 Skill 和 Tool 的运行时治理。

最终允许关系：

```text
用户组织成员状态
and 用户对 Agent 的权限
and Agent 发布版本可用
and Agent / Skill / Tool runtime policy
and 高风险操作确认或审批
= 本次调用允许
```

Example:

- 用户拥有 `RUN`，可以触发客户跟进 Agent。
- 该 Agent 绑定 `customer_followup` Skill。
- Skill 声明 `email.send` 为 optional tool。
- 本次调用需要发送邮件时，运行时仍要检查 Tool 风险策略、用户确认和平台禁用状态。
- 若策略拒绝，Agent 可以降级为生成邮件草稿或返回需要管理员授权的提示。

## 迁移与兼容

第一阶段迁移建议：

1. 为现有 Agent 创建 owner/admin 隐式规则，不必为每个 Agent 写入管理员 grant。
2. 对历史上默认全员可用的 Agent，按兼容策略批量写入 `ORG` 的 `VIEW` + `RUN` grant。
3. 对未发布、草稿或测试 Agent，默认仅 owner 和管理员可见。
4. 对已有 Open API Key，补一次 run-as `RUN` 权限巡检报告；不自动删除 Key，但运行时严格校验。
5. 发布前提供 dry-run SQL 或管理脚本，列出将变为不可见/不可运行的 Agent。

Rollback:

- 可通过配置开关临时退回旧行为：组织成员默认可运行本组织 Agent。
- 即使打开兼容开关，`OPENAPI`、`PUBLISH`、`MANAGE` 和高风险 Tool 仍应保持新权限校验。

## 任务拆分

### TASK-119 Agent access control design and implementation

Recommended phases:

1. Schema and service foundation
   - Add `agent_access_grant` and `agent_permission_audit` migration.
   - Add `AgentAccessControlService` and unit/integration tests.
   - Add migration helper for existing agents.

2. Runtime and API gates
   - Gate Agent list/detail/run/debug/publish/Open API/log APIs.
   - Enforce Open API run-as `RUN` permission.
   - Add audit records for grant changes and denied Open API run-as.

3. Admin UI
   - Add access management modal/page under Agent Builder or Agent detail.
   - Add list filtering and disabled states.
   - Verify desktop and mobile screenshots under `鎏金账房` product rules.

4. Future expansion hooks
   - Keep `GROUP` / `CUSTOM_ROLE` / `DEPARTMENT` enum support in domain model.
   - Reject unsupported principal types with clear API error until those modules exist.

## 验收标准

- 普通组织成员只能看到拥有 `VIEW` 或 `RUN` 的 Agent。
- 用户没有 `RUN` 时，无法从助手工作台或 Open API 触发该 Agent。
- `ORG_ADMIN` 和 Agent owner 保持默认管理能力。
- 指定用户授权后，该用户可运行目标 Agent；撤权后无法继续运行。
- `EDIT`、`PUBLISH`、`MANAGE`、`OPENAPI`、`LOG_VIEW` 分别控制对应入口，不被 `RUN` 自动包含。
- API Key run-as 用户没有 `RUN` 时，Open API 调用被拒绝并记录可审计原因。
- Skill 和 Tool 运行时权限不因 Agent `RUN` 授权扩大。
- 授权变更写入 `agent_permission_audit`。
- 迁移后现有关键 Agent 的可用性符合兼容策略。
- 前端权限管理 UI 在桌面和 390px 移动端无横向溢出，符合 `DESIGN.md` product UI rules。

## 风险与回滚

- 风险：迁移策略过严导致历史 Agent 对普通用户突然不可见。
  缓解：先生成 dry-run 报告，对历史默认可用 Agent 批量写入 `ORG VIEW/RUN`。

- 风险：只在前端隐藏入口，后端 API 未拦截。
  缓解：所有 Agent API 必须后端 `require(...)`，前端只做体验优化。

- 风险：`RUN` 被误解为 Tool 权限。
  缓解：服务命名和审计字段区分 `agent_access` 与 `runtime_tool_policy`。

- 风险：未来角色/部门上线后授权来源冲突。
  缓解：`effectivePermissions` 返回 source summary，并为每个 principal type 保持独立 grant。

- 回滚：保留兼容开关恢复“组织成员默认可运行本组织 Agent”；授权表和审计表可继续保留，不影响旧路径。

## 实现进展

- 2026-05-20: 已完成产品权限模型讨论并形成设计文档。
- 未完成：schema、服务、API、UI、迁移脚本和测试实现。

## 交接说明

- 接手实现前先阅读本文件、`docs/agent-skill-tool-permission-model.md`、`docs/specs/FEAT-009-skill-layering-and-governance.md`、`docs/specs/FEAT-021-agent-open-api.md`。
- 第一阶段不要先实现完整角色、部门和用户组模块；先落 `ORG` / `USER` / `SYSTEM_ROLE` / owner implicit。
- 所有运行入口必须以后端权限校验为准，前端隐藏和禁用只是辅助体验。
