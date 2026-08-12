---
kind: feature-spec
feature_id: FEAT-174
title: 管理端新增成员公共编号回读修复
status: implemented
owner_role: backend-agent
task_ids: TASK-290
related_decisions: "FEAT-145 HUMAN 统一身份"
related_issues: ISSUE-2026-08-12-admin-member-public-id-stale
updated_at: 2026-08-12T07:36:57Z
updated_by: codex
---

# FEAT-174 - 管理端新增成员公共编号回读修复

## 背景与目标

组织管理员在用户管理页添加一个尚不存在的手机号时，后端先插入全局账号，再创建 Keycloak HUMAN 身份。`user_account.public_id` 由 PostgreSQL `BEFORE INSERT` 触发器生成，但新增成员链路在同一事务内通过 Repository 再查询，JPA 一级缓存仍返回未包含 `public_id` 的旧实体，最终以 `Global account public ID is not available` 失败。

目标是在进入 Keycloak provisioning 前强制回读数据库生成的公共编号，使新成员创建、统一身份邀请和成员状态在同一事务内成功或完整回滚。

## 范围

- `AdminUserService` 在账号 `saveAndFlush` 后使用当前持久化上下文 `refresh` 回读触发器字段。
- 新增回归测试，断言 Keycloak provisioning 被调用前 `public_id` 已存在，并且不再依赖同事务 `findById`。
- 保留现有手机号、邮箱、角色、停用成员和统一身份安全规则。

不修改数据库触发器、Keycloak Realm、前端表单或 API 结构；不直接修补 UAT 数据。

## 验收标准

- 新手机号添加成员时，Keycloak provisioning 收到非空且不可变的全局公共编号。
- 已存在账号、停用成员、身份修复及激活同步行为不回归。
- 失败事务不留下全局账号、登录标识、成员或外部身份半成品。
- 身份相关定向测试和后端 package 通过；本地开发环境更新后 backend healthy、restart=0，版本可追溯至本地 `main` 提交。

## 回滚

- 回滚 `AdminUserService` 与测试提交即可；无数据库迁移或数据格式变更。
- UAT/生产发布必须使用不可变版本，发布前按各环境 Runbook 备份并只重建 AgentCiCi 应用服务。

## 实现进展

- 已确认 UAT `2.8.61-beta.16` 仍可触发故障；目标手机号与邮箱对应的四类记录均为 0，事务已完整回滚。
- 代码修复、21 项身份链路定向测试与后端 package 已通过；提交 `ab1b02c` 已进入本地 main，本地 backend `2.8.62-dev.ab1b02c` 的版本、健康、重启、edge 与匿名鉴权边界验证通过。
- UAT 与生产未修改；下一步是受权 UAT 发布后创建专用测试成员，回读全局账号、登录标识、成员、Keycloak 身份和激活邮件状态。
