---
kind: feature-spec
feature_id: FEAT-146
title: 计费用量公司成员查询修复
status: active
owner_role: backend-agent
task_ids: TASK-253
related_decisions: FEAT-037, FEAT-135 company_id 统一
related_issues: 组织管理端计费用量页 JPQL UnknownPathException
updated_at: 2026-07-29T12:00:09Z
updated_by: MANAGER-001
---

# FEAT-146 - 计费用量公司成员查询修复

## 背景与目标

- 组织管理端 `/admin/billing` 在加载计费订阅时返回 `org.hibernate.query.sqm.UnknownPathException`。
- 错误 JPQL 使用已迁移前的 `member.org.id`，而 `UserEntity` 的当前关联字段是 `member.company`。
- 修复活跃构建者席位统计，使组织管理员能正常查看计费用量，不改变计费口径或数据。

## 范围

### In Scope

- 将 `BillingUsageMeteringService` 中构建者席位统计的 JPQL 改为当前 `company` 关联。
- 增加 `/admin/billing/overview` 的回归断言，确保新注册组织首次加载可完成席位统计。

### Out Of Scope

- 不改计费版本、Credits 计算、角色规则、数据库迁移、前端或生产环境。

## 方案设计

- 保留现有查询条件：同一 `companyId`、`ACTIVE` 成员、角色为 `OWNER` 或 `ORG_ADMIN`。
- 仅将实体路径从 `member.org.id` 更正为 `member.company.id`；因为该路径由 JPA 实体属性决定，不依赖数据库列的历史命名。

## 验收标准

- `/admin/billing/overview` 不再产生 `UnknownPathException`，并返回构建者席位数。
- 定向计费集成测试、后端编译和 diff 检查通过。

## 风险与回滚

- 改动只影响查询路径，不写入或迁移数据；回退单一代码提交即可恢复。

## 实现进展

- 当前状态：已确认根因，待实现。
