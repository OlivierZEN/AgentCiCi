---
kind: feature-spec
feature_id: FEAT-167
title: DevAutopilot 机器主体治理负责人和应用调用者分离
status: verified
owner_role: fullstack-agent
task_ids: TASK-279
related_decisions: "FEAT-145 SERVICE 统一身份；FEAT-152 确认式写入；FEAT-164 DevAutopilot 租户初始化"
related_issues: none
updated_at: 2026-08-11T03:53:08Z
updated_by: codex
---

# FEAT-167 - DevAutopilot 机器主体治理负责人和应用调用者分离

## 背景与目标

DevAutopilot 产品经理 Agent 已绑定健康的 SERVICE Principal，但运行时把 `PRIMARY_OWNER` 同时解释为治理负责人和唯一委托人。结果是租户 `ORG_ADMIN` 可以看到并运行 Agent，却不能让 Agent 调用 Semattice。人类负责人应继续承担机器主体所有权、密钥、生命周期与问责，不应成为唯一业务调用者。

本功能将机器主体健康、应用调用授权和具体工具风险分层判断，并在 OACT 与审计中同时保留负责人和本次人类发起人。

## 范围

### In Scope

- DevAutopilot 产品经理 Agent 使用 `TENANT_APP_ROLE` 委托策略；既有 `PRIMARY_OWNER` 绑定在初始化补偿时幂等升级。
- `OWNER`、`ORG_ADMIN` 自动拥有 DevAutopilot `APP_ADMIN` 调用能力，但不自动成为机器主体负责人。
- 机器主体 `PRIMARY` 负责人保留 `APP_ADMIN` 调用能力和治理责任。
- 其他激活租户成员通过明确应用角色授权：`VIEWER`、`CONTRIBUTOR`、`REVIEWER`、`APP_ADMIN`。
- 查询要求 Agent `RUN` 且具备上述任一应用角色；创建/修改要求 `CONTRIBUTOR` 以上；评审要求 `REVIEWER` 或 `APP_ADMIN`；删除仅允许 `APP_ADMIN`。
- DevAutopilot 租户管理端可查看和替换成员应用角色；只允许同租户激活 HUMAN 成员。
- SERVICE OACT 的 `owner_principal_id` 保持治理负责人，`delegated_by_principal_id` 使用本次登录 HUMAN account principal。
- Agent 列表返回当前用户的机器执行权限摘要；工作台无调用权时保留 Agent 可见性但禁用输入，并显示可操作原因。
- SSE 运行错误使用 `error` 事件后正常结束响应，不再把已提交的 `text/event-stream` 交给 JSON 全局异常处理器。

### Out Of Scope

- 不改变 Semattice 授权契约、数据库或策略引擎。
- 不允许平台账号替代租户成员执行，不直接修改生产/UAT 数据。
- 不扩大 SERVICE Principal 自身 scopes；应用角色只能在现有机器 scopes 内进一步收紧。
- 不取消人类明确确认、幂等键、父子关系校验或 Tool 字段白名单。

## 权限模型

| 人类上下文 | 查询 | 创建/修改 | 评审 | 删除 |
|---|---:|---:|---:|---:|
| 租户 OWNER / ORG_ADMIN | 是 | 是 | 是 | 是 |
| 机器主体 PRIMARY 负责人 | 是 | 是 | 是 | 是 |
| VIEWER | 是 | 否 | 否 | 否 |
| CONTRIBUTOR | 是 | 是 | 否 | 否 |
| REVIEWER | 是 | 是 | 是 | 否 |
| APP_ADMIN | 是 | 是 | 是 | 是 |
| 无应用角色的普通成员 | 否 | 否 | 否 | 否 |

所有允许者还必须具备当前 Agent 的 `RUN` 权限、激活成员身份和同租户上下文。OWNER/ORG_ADMIN 与负责人是服务端派生的有效角色，不写入应用角色表。

## 数据与接口

- 新增 `tenant_application_member_role`：`activation_id`、`company_member_id`、`role_code`、`status`、`granted_by_member_id`、时间戳；同一应用成员唯一。
- `GET /api/admin/devautopilot/team/access-members`：返回激活租户成员及显式/有效应用角色。
- `PUT /api/admin/devautopilot/team/access-members`：组织管理员完整替换显式角色，空角色表示移除；不能写入非激活、跨租户成员。
- `GET /agents` 每项增加 `executionAccess`：`bound`、`canInvoke`、`maxRole`、`reasonCode`、`message`。

## 审计

- OACT：`owner_principal_id=<机器负责人 account principal>`，`delegated_by_principal_id=<当前调用者 account principal>`，`delegation_policy=TENANT_APP_ROLE`。
- 平台审计 actor 使用当前 HUMAN account principal，详情同时记录 Agent、SERVICE、owner、member role、purpose，不记录 token/secret。
- 应用角色替换记录 before/after 的成员公共技术标识与角色，不记录手机号、邮箱和凭据。

## 验收标准

- 当前 `Platform Admin` 类型的租户 ORG_ADMIN 可通过产品经理 Agent 确认创建项目，且 OACT/审计区分负责人和发起人。
- 普通成员无角色时前端输入禁用，直接显示“需要租户管理员授予 DevAutopilot 应用角色”，不会先发送再失败。
- VIEWER 只能查询；CONTRIBUTOR 可创建/修改但不能评审/删除；REVIEWER 可评审；APP_ADMIN 可删除。
- 暂停机器主体、关闭绑定、缺少 scope、租户未开通 Semattice、成员停用均继续失败关闭并返回准确 reason。
- SSE 错误只产生结构化 `error` 事件，不出现 `HttpMessageNotWritableException`。
- Flyway V1→最新、后端定向测试、前端定向测试、生产构建、UAT 真实 ORG_ADMIN 对话与 Semattice 回读通过。

## 回滚

- 应用代码可回滚至上一 UAT 候选；V109 表为向后兼容新增，可保留。
- 回滚后旧代码仍按 `PRIMARY_OWNER` 运行；若绑定已升级为 `TENANT_APP_ROLE`，回滚前必须通过受管初始化把绑定恢复为旧兼容值，禁止直接写库。
- 已由正式确认链路创建的 Semattice 记录不随应用回滚删除。

## 实现与 UAT 证据

- 实现提交为 `c66d9448c95b`，UAT 首发为 `2.8.61-beta.2`；后续 `2.8.61-beta.3 / 47affe4086e5` 沿主线包含本功能，未回退 V109 或委托授权实现。
- V109 增加应用成员角色表并把既有 DevAutopilot 产品经理绑定幂等升级为 `TENANT_APP_ROLE`；新开通流程先注册资源再配置执行绑定，补偿流程可修复既有绑定。
- Demo Company 的产品经理机器主体负责人和实际 `ORG_ADMIN` 调用者不是同一 HUMAN。实际查询成功，平台审计分别记录 actor、`ownerPrincipalId`、`appRole=APP_ADMIN` 和 `delegationPolicy=TENANT_APP_ROLE`，证明治理负责人不再等于唯一调用者。
- 管理端独立弹窗只读验收显示负责人和租户 ORG_ADMIN 自动 APP_ADMIN，普通 ORG_USER 默认无权；应用角色不扩大机器主体 Semattice scopes。
- 浏览器查询与管理页 error/warning 为 0；SSE 定向测试证明错误事件后正常结束，不再触发 JSON 全局异常写入。
