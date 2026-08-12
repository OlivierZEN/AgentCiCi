---
kind: task-status
task_id: TASK-290
status: review
updated_at: 2026-08-12T07:36:57Z
updated_by: codex
assignee: codex
owner_role: backend-agent
spec_path: docs/specs/FEAT-174-admin-member-public-id-refresh.md
related_issues: ISSUE-2026-08-12-admin-member-public-id-stale
---

# TASK-290 - 管理端新增成员公共编号回读修复

## 问题

管理端添加全新手机号成员时，数据库触发器已生成 `public_id`，但同事务 Repository 查询命中 JPA 一级缓存中的旧实体，Keycloak provisioning 因公共编号为空失败。

## 范围

- 在新增成员账号持久化后显式 refresh。
- 增加故障同构回归测试。
- 完成后端定向测试、package、本地 main 归并及本地全栈 backend 更新验证。

## 完成条件

- 定向测试证明 provisioning 前已回读公共编号。
- 后端 package 成功。
- 修复提交进入本地 `main`，本地 `cc-local-stack` 只重建 backend，并回读健康、重启次数和版本指纹。
- 未直接写 UAT 数据；UAT 发布与真实成员邀请另行授权和验收。

## 当前证据

- UAT 实际运行 `2.8.61-beta.16 / aef334205280`，health=`UP`，公共 smoke 全部通过。
- 故障目标手机号、邮箱对应的 account/identifier/member/identity 均为 0，失败事务无残留。
- `AdminUserServiceTest, CompanyProvisioningServiceTest, KeycloakIdentityProvisioningServiceTest` 共 21 项通过。
- 后端 `mvn -DskipTests package` 通过。
- 修复提交 `ab1b02c` 已快进归并到本地 `main`，未触碰被占用功能分支的未提交内容。
- 本地 backend 从 `main@ab1b02c` 构建为 `2.8.62-dev.ab1b02c`，运行镜像 `sha256:3bd08dcfdc2eaf847d72e5b5228b668fdad17ee3660c9d9d2bb1c11f8de32009`；health=`UP`、healthy/restart=0、edge 200、匿名用户 API 401、启动错误 0。
- 本次只重建本地 backend，frontend、PostgreSQL、Redis、RabbitMQ、Qdrant、Keycloak、Semattice 与 DevAutopilot 容器 ID 保持不变；UAT/生产未修改。
