---
kind: feature-spec
feature_id: FEAT-129
title: 生产发布构造器注入启动热修
status: in_progress
owner_role: fullstack-agent
task_ids: TASK-224
updated_at: 2026-07-22T09:55:00+08:00
updated_by: MANAGER-001
---

# FEAT-129 - 生产发布构造器注入启动热修

## 背景与目标

- `2.8.2` 发布后，后端在 Spring 初始化 `AuditService` 时因存在两个未标注构造器而无法选择注入构造器，容器持续重启。
- 已立即回滚至健康的 `2.8.1`；Flyway V84 已正向成功执行，不能回退历史迁移。
- 修复必须明确选择依赖 `SecurityRedactionService` 的运行时构造器，并保持单参构造器仅服务于无 Spring 容器的单元测试。

## 范围

- 对 `AuditService` 与 `PlatformAuditService` 的双参构造器标注 Spring 注入入口。
- 新增 Spring 上下文启动回归，覆盖两个审计服务可被容器实例化。
- 用新的不可变版本发布；不得覆盖失败的 `2.8.2` tag 或镜像。

## 验收标准

- 后端 Spring 上下文能启动，`AuditService` 与 `PlatformAuditService` 正常注入 `SecurityRedactionService`。
- 后端定向测试、后端打包、前端生产构建和 Compose 校验通过。
- 生产新版本健康，`/system/version` 与 Git tag、镜像 tag 一致；V84 保持成功。

## 风险与回滚

- 风险：发布启动失败会影响 API 可用性。
- 回滚：只将 backend/frontend 镜像与 `acr.env` 切回已验证的 `2.8.1`；V84 表结构保持，不做反向迁移。
