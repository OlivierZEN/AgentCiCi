---
kind: feature-spec
feature_id: FEAT-156
title: DEV Autopilot 研发身份花名与新增开发者
status: verified
owner_role: integration-agent
task_ids: TASK-264
related_decisions: "AgentCiCi 是全局 Principal 与凭据权威；Semattice 只保存租户投影和授权事实"
related_issues: none
updated_at: 2026-08-05T15:15:00Z
updated_by: MANAGER-001
---

# FEAT-156 - DEV Autopilot 研发身份花名与新增开发者

## 背景与目标

租户 `org5nszpgj99jaysxv6y` 已有产品总监 HUMAN、产品经理 SERVICE 和开发者 SERVICE 三类研发主体。用户要求把治理页面中的研发身份改成易识别的花名，并新增第二个开发者机器账号。

## 目标花名

- 产品总监 HUMAN：`Oliver`，继续绑定全局用户 `18611892001`。
- 产品经理 SERVICE：`大乔`，沿用 client `dev-autopilot-product-manager`。
- 现有开发者 SERVICE：`悟空`，Client ID 规范化为 `dev-autopilot-developer-wukong`。
- 新增开发者 SERVICE：`后羿`，client 使用 `dev-autopilot-developer-houyi`。
- 三个 SERVICE 的 PRIMARY 人类负责人均为产品总监 Oliver。

用户原文第二次写了“研发交付产品经理，改名成悟空”。结合截图只有一个产品经理和一个开发者，本次按“现有研发交付开发者改名为悟空”执行；不创建第二个产品经理。

## 范围

### In Scope

- 更新 AgentCiCi 权威 HUMAN/SERVICE 显示名；悟空 Client ID 的规范化改名必须保留主体 ID、Keycloak service-account subject、负责人、Secret 与生命周期状态。
- 通过现有受管 API 创建后羿 SERVICE，secret 只返回一次并写入服务器 `0600` 受管文件，不进入仓库、日志或审计。
- 使用各主体的短时 OACT 自同步 Semattice 显示名与新 Principal 投影。
- 为后羿分配现有“开发者”角色和“研发交付部”primary membership，复用现有角色权限与数据范围，不复制权限定义。
- 验证 Semattice 管理中心精确展示四名研发主体、角色、组织与 active 状态；验证后羿只能执行开发者允许的任务/工时能力。

### Out Of Scope

- 不新增人类手机号账号，不修改其他同租户 AgentCiCi 成员。
- 不轮换或撤销现有大乔、悟空的凭据。
- 不新增角色、Permission Set、字段权限、数据范围或对象策略。
- 不改变 DEV Autopilot 项目业务对象和元数据。

## 安全与一致性

- AgentCiCi 继续作为显示名、全局身份、Keycloak client 和 owner 关系权威。
- Client ID 改名必须由组织管理员调用受保护的机器主体治理 API 完成；先更新 Keycloak client，再在同一事务中同步 AgentCiCi 权威记录和 identity mirror。持久化失败时必须尝试回滚 Keycloak 名称。
- Semattice 只保存同租户 Principal、角色和组织投影；新开发者必须先用可信 SERVICE OACT 自同步，再赋权。
- 后羿只获得与悟空相同的开发者角色，不获得产品经理或身份治理权限。
- 所有一次性 secret 仅在生产服务器内存与 root-only 文件中处理，任何验证输出必须脱敏。

## 验收标准

- AgentCiCi 权威数据精确返回 Oliver、大乔、悟空、后羿四个目标花名。
- 后羿 SERVICE 为 ACTIVE，owner 为 Oliver，Keycloak client credentials 可交换短时 Semattice OACT。
- Semattice members/overview 返回 4 members、3 roles、1 organization；四行角色分别为产品总监、产品经理、开发者、开发者。
- 后羿能读取/领取/更新开发任务并登记工时，不能创建项目或读取身份治理目录。
- 悟空以新 Client ID 可通过 Keycloak client-credentials、AgentCiCi OACT 和 DevAutopilot 任务读取；旧 Client ID 不再为有效开发者准入。
- 三个仓库工作树保持 clean；所有触发的项目状态文档完成校验和提交。
