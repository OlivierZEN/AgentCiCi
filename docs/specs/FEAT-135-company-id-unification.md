---
kind: feature-spec
feature_id: FEAT-135
title: AgentCiCi company identity unification
status: verified
updated_at: 2026-07-24T06:12:00Z
updated_by: MANAGER-001
owner_role: integration-agent
---

# FEAT-135 - 顶层租户 `company_id` 统一

## 决策与目标

AgentCiCi 当前称为 `org_id` 的值实际是顶层租户、跨应用企业身份，不是组织架构节点。即日起统一命名为 `company_id`。`organization_id` 仅保留给未来公司内部部门、事业部、团队等组织架构节点，必须归属一个 `company_id`。

编号值本身不重键：现有形如 `org...` 的 20 位值继续作为同一不可变企业编号。此次改的是数据库、代码、接口和 JWT 的字段/领域名称，而不是值的内容。

## 统一模型

```text
company_id                    跨系统顶层租户/企业身份
├─ AgentCiCi company_member   公司成员与公司级资源
├─ Semattice company_id       同一企业在 Semattice 的开户绑定
└─ organization_id            未来内部组织架构节点（尚未实现）
```

Semattice 已采用 `company_id` 作为受控开户契约；AgentCiCi 完成切换后不再需要将外部 `company_id` 翻译为内部 `org_id`。

## 破坏性契约

- 所有 AgentCiCi JSON、查询参数、事件字段、平台运营 DTO 和 JWT claim 统一使用 `companyId` / `company_id`。
- 旧 `orgId` / `org_id` 输入、JWT claim 和前端本地会话不兼容，不提供 alias、双读或双写；旧会话必须重新登录。
- 保留 `/platform/tenants/*` 作为运营界面的资源路由；路径参数的领域含义改为 `companyId`，不再称 `orgId`。
- Semattice 入站和回调继续只接受 `company_id`；其已完成的 company-identity migration 不回退。

## 数据库迁移

新增 AgentCiCi 正向 Flyway migrations `V94__company_identity_unification.sql` 与 `V95__company_profile_vocabulary.sql`，不得修改 V1–V93。

1. 将根表 `org` 重命名为 `company`，`organization_member` 重命名为 `company_member`；所有公司生命周期表从 `organization_*` 改为 `company_*`。
2. 对生产 catalog 中全部顶层租户 `org_id` 列（当前已核验为 131 张表的 131 列）无损重命名为 `company_id`；主键、外键、唯一约束和索引继续约束相同的值。
3. 统一重命名受影响的约束和索引标识，删除遗留的 `org` 命名；迁移应在 fresh V1–V93 库和现有 V93 数据库均验证。
4. `agent_access_grant` 先将遗留 `ORG` CHECK 约束替换为 `COMPANY` 版本，再转换既有授权记录；不执行 `UPDATE` 重写 ID 值，不删除数据，不修改历史 migration checksum。
5. V95 将 legacy `organization_size` 物理列改为 `company_size`，与 company profile 的 JPA/API 契约一致。

## 实现范围

- 后端：`OrgEntity` / Repository、成员关系、鉴权上下文、JWT、Redis key 命名、服务参数、JPA mappings、repository derived method、审计、生命周期、Agent/知识库/记忆等全部公司级隔离字段。
- 前端：认证持久化、登录请求/响应、运营端租户应用与生命周期页面、管理员页面、类型和测试统一为 `companyId`；文案使用“公司”或“租户”，不显示“组织 ID”。
- 跨产品：受控开户 reservation/binding 的物理列、实体和审计统一为 `company_id`；更新 AgentCiCi 与 Semattice 规格，确认 Semattice 不接受旧 claim/input。

## 上线与回滚

此迁移与新代码不兼容，执行顺序为：备份 → 停止旧 backend 写入 → 发布包含 V94/V95 的新 backend → 迁移完成后启动 backend/frontend → 重新登录与受控开户 smoke。旧二进制不得在 V94 后启动；回滚仅限于切回包含兼容 schema 的数据库备份，不能把新库直接配给旧二进制。2026-07-24 经用户明确授权即时发布后，生产 `2.8.9 / 0194706` 已完成该流程并健康运行。

## 验收

- fresh V1–V94 与生产备份恢复库均不存在顶层 `org_id` 列，`company_id` 引用和值数量保持一致。
- 认证、管理员、平台运营、Agent 运行、生命周期与 Semattice reserve/complete 全部只使用 `company_id`。
- 旧 JWT `org_id`、旧 JSON `orgId` 和旧开户参数被 fail closed；新会话和新 `company_id` 请求正常工作。
- AgentCiCi 全量相关测试、前端构建、真实本机 AgentCiCi→Semattice 开户、迁移后生产 smoke 和状态校验通过后方可发布。
